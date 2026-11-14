const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const nowTs = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-paperare-3e1a7f9b-d507-4f94-a66b-c9d2e644ef35";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function dbFor(context) {
  return context.firestore({ databaseId: DATABASE_ID });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write users or rooms", async () => {
  const unauthDb = dbFor(testEnv.unauthenticatedContext());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rooms").where("isPrivate", "==", false).where("status", "==", "waiting").get());
  await assertFails(unauthDb.collection("rooms").doc("PUB123").set({ hostId: ALICE_UID }));
});

test("User profile: owner can create and update, cross-user access denied, ghost field rejected", async () => {
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = dbFor(testEnv.authenticatedContext(BOB_UID));

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      selectedSkinId: "classic_cyan",
      bestPercentage: 42.5,
      totalKills: 7,
      wins: 1,
      matchesPlayed: 3,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Cross-user read & write fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" }));

  // Shadow update with ghost field fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: nowTs(),
    })
  );

  // Valid update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      selectedSkinId: "royal_crown",
      bestPercentage: 68.0,
      updatedAt: nowTs(),
    })
  );
});

test("Rooms: public matchmaking list query succeeds, private rooms hidden from public list, join/start/finish flow works", async () => {
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = dbFor(testEnv.authenticatedContext(BOB_UID));
  const charlieDb = dbFor(testEnv.authenticatedContext(CHARLIE_UID));

  // Alice creates a public matchmaking room
  await assertSucceeds(
    aliceDb.collection("rooms").doc("PUB001").set({
      hostId: ALICE_UID,
      roomCode: "PUB001",
      isPrivate: false,
      status: "waiting",
      maxPlayers: 6,
      fillWithBots: true,
      playerUids: [ALICE_UID],
      playerProfiles: [`${ALICE_UID}|Alice|classic_cyan`],
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Alice creates a private room
  await assertSucceeds(
    aliceDb.collection("rooms").doc("PRIV99").set({
      hostId: ALICE_UID,
      roomCode: "PRIV99",
      isPrivate: true,
      status: "waiting",
      maxPlayers: 6,
      fillWithBots: true,
      playerUids: [ALICE_UID],
      playerProfiles: [`${ALICE_UID}|Alice|classic_cyan`],
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Unfiltered rooms list fails
  await assertFails(bobDb.collection("rooms").get());

  // Filtered public matchmaking list succeeds
  await assertSucceeds(
    bobDb.collection("rooms").where("isPrivate", "==", false).where("status", "==", "waiting").get()
  );

  // Bob can get private room by exact code while waiting and join it
  await assertSucceeds(bobDb.collection("rooms").doc("PRIV99").get());
  await assertSucceeds(
    bobDb.collection("rooms").doc("PRIV99").update({
      playerUids: [ALICE_UID, BOB_UID],
      playerProfiles: [`${ALICE_UID}|Alice|classic_cyan`, `${BOB_UID}|Bob|ninja_violet`],
      updatedAt: nowTs(),
    })
  );

  // Non-host Bob cannot start the room
  await assertFails(
    bobDb.collection("rooms").doc("PRIV99").update({
      status: "playing",
      updatedAt: nowTs(),
    })
  );

  // Host Alice starts the room
  await assertSucceeds(
    aliceDb.collection("rooms").doc("PRIV99").update({
      status: "playing",
      updatedAt: nowTs(),
    })
  );

  // Non-participant Charlie cannot read active private room
  await assertFails(charlieDb.collection("rooms").doc("PRIV99").get());

  // Subcollection /rooms/PRIV99/players: Alice & Bob create their own player docs
  await assertSucceeds(
    aliceDb.collection("rooms").doc("PRIV99").collection("players").doc(ALICE_UID).set({
      userId: ALICE_UID,
      roomId: "PRIV99",
      displayName: "Alice",
      skinId: "classic_cyan",
      colorIndex: 0,
      memberUids: [ALICE_UID, BOB_UID],
      x: 500.0,
      y: 500.0,
      angle: 0.0,
      percentage: 1.5,
      kills: 0,
      alive: true,
      trailPacked: "",
      territoryPacked: "10:1",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Charlie cannot create player doc in PRIV99 because he is not in room.playerUids
  await assertFails(
    charlieDb.collection("rooms").doc("PRIV99").collection("players").doc(CHARLIE_UID).set({
      userId: CHARLIE_UID,
      roomId: "PRIV99",
      displayName: "Charlie",
      skinId: "alien_lime",
      colorIndex: 2,
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      x: 300.0,
      y: 300.0,
      angle: 0.0,
      percentage: 1.5,
      kills: 0,
      alive: true,
      trailPacked: "",
      territoryPacked: "10:1",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Bob can query room players using memberUids filter; Charlie fails
  await assertSucceeds(
    bobDb.collection("rooms").doc("PRIV99").collection("players").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertFails(
    charlieDb.collection("rooms").doc("PRIV99").collection("players").doc(ALICE_UID).get()
  );
  await assertFails(
    charlieDb.collection("rooms").doc("PRIV99").collection("players").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Finish room locks further updates (terminal state locking)
  await assertSucceeds(
    aliceDb.collection("rooms").doc("PRIV99").update({
      status: "finished",
      winnerUid: ALICE_UID,
      winnerName: "Alice",
      updatedAt: nowTs(),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc("PRIV99").update({
      status: "playing",
      updatedAt: nowTs(),
    })
  );
});
