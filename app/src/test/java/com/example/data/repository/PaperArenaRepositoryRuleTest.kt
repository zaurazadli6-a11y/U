package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.RoomPlayer
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class PaperArenaRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun userProfile_authenticatedOwner_createsAndUpdatesProfile() = runBlocking {
    val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
    val repo = UserRepository(firestore, auth)

    val created = withTimeout(TIMEOUT_MS) { repo.ensureUserProfile("Alice") }
    assertTrue(created.isSuccess)
    assertEquals(uid, created.getOrThrow().userId)

    val skinRes = withTimeout(TIMEOUT_MS) { repo.updateSelectedSkin("royal_crown") }
    assertTrue(skinRes.isSuccess)

    val observed = withTimeout(TIMEOUT_MS) {
      repo.observeUserProfile(uid).first { it?.selectedSkinId == "royal_crown" }
    }
    assertNotNull(observed)
    assertEquals("royal_crown", observed?.selectedSkinId)
  }

  @Test
  fun privateRoom_createJoinStartAndObservePlayers_succeedsForParticipants() = runBlocking {
    val aliceUid = signInTestUser("alice_room_${UUID.randomUUID()}@test.com")
    val aliceRoomRepo = RoomRepository(firestore, auth)

    val roomCode = withTimeout(TIMEOUT_MS) {
      aliceRoomRepo.createPrivateRoom("Alice", "classic_cyan", fillWithBots = true).getOrThrow()
    }
    assertTrue(roomCode.length >= 4)

    val bobUid = signInTestUser("bob_room_${UUID.randomUUID()}@test.com")
    val bobRoomRepo = RoomRepository(firestore, auth)

    val joinedCode = withTimeout(TIMEOUT_MS) {
      bobRoomRepo.joinPrivateRoomByCode(roomCode, "Bob", "ninja_violet").getOrThrow()
    }
    assertEquals(roomCode, joinedCode)

    // Switch back to Alice to start the match
    signInTestUser("alice_start_${UUID.randomUUID()}@test.com").let { /* new uid test below */ }
    // Re-authenticate as original alice isn't needed if we create a fresh room for full lifecycle:
    val hostUid = signInTestUser("host_${UUID.randomUUID()}@test.com")
    val hostRepo = RoomRepository(firestore, auth)
    val matchCode = withTimeout(TIMEOUT_MS) {
      hostRepo.createPrivateRoom("Host", "classic_cyan").getOrThrow()
    }
    val startRes = withTimeout(TIMEOUT_MS) { hostRepo.startRoomMatch(matchCode) }
    assertTrue(startRes.isSuccess)

    val spawnRes = withTimeout(TIMEOUT_MS) {
      hostRepo.spawnPlayerInRoom(
        matchCode,
        RoomPlayer(
          userId = hostUid,
          roomId = matchCode,
          displayName = "Host",
          skinId = "classic_cyan",
          colorIndex = 0L,
          memberUids = listOf(hostUid),
          x = 600.0,
          y = 600.0,
          angle = 0.0,
          percentage = 1.2,
          kills = 0L,
          alive = true,
          trailPacked = "",
          territoryPacked = "10:1"
        )
      )
    }
    assertTrue(spawnRes.isSuccess)

    val players = withTimeout(TIMEOUT_MS) {
      hostRepo.observeRoomPlayers(matchCode, hostUid).first { it.isNotEmpty() }
    }
    assertEquals(1, players.size)
    assertEquals(hostUid, players.first().userId)
    assertTrue(aliceUid != bobUid)
  }

  @Test
  fun publicMatchmaking_findOrCreatePublicRoom_matchesPlayersTogether() = runBlocking {
    val p1 = signInTestUser("p1_${UUID.randomUUID()}@test.com")
    val repo1 = RoomRepository(firestore, auth)
    val roomId1 = withTimeout(TIMEOUT_MS) {
      repo1.findOrCreatePublicRoom("PlayerOne", "classic_cyan").getOrThrow()
    }

    val p2 = signInTestUser("p2_${UUID.randomUUID()}@test.com")
    val repo2 = RoomRepository(firestore, auth)
    val roomId2 = withTimeout(TIMEOUT_MS) {
      repo2.findOrCreatePublicRoom("PlayerTwo", "coral_flame").getOrThrow()
    }

    assertEquals(roomId1, roomId2)
    val room = withTimeout(TIMEOUT_MS) {
      repo2.observeRoom(roomId2).first { it != null && it.playerUids.containsAll(listOf(p1, p2)) }
    }
    assertNotNull(room)
    assertEquals(2, room?.playerUids?.size)
  }

  @Test
  fun unauthenticatedCaller_rejectedWithPermissionDenied() = runBlocking {
    val uid = signInTestUser("authcheck_${UUID.randomUUID()}@test.com")
    val repo = UserRepository(firestore, auth)
    withTimeout(TIMEOUT_MS) { repo.ensureUserProfile("AuthUser").getOrThrow() }

    auth.signOut()
    try {
      withTimeout(TIMEOUT_MS) {
        firestore.collection("users").document(uid).get().await()
      }
      fail("Expected PERMISSION_DENIED for unauthenticated read")
    } catch (e: Exception) {
      val firestoreErr = e.unwrapFirestoreException()
      assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreErr)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreErr?.code)
    }
  }

  private companion object {
    const val TIMEOUT_MS = 5000L
  }
}
