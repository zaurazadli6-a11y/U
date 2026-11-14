package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.GameRoom
import com.example.data.model.RoomPlayer
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

class RoomRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth
  )

  private val random = SecureRandom()

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  private fun sanitizeName(name: String): String {
    return name.replace("|", "").trim().ifBlank { "Oyuncu" }.take(24)
  }

  private fun generateCode(length: Int = 6): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return buildString(length) {
      repeat(length) {
        append(chars[random.nextInt(chars.length)])
      }
    }
  }

  suspend fun findOrCreatePublicRoom(displayName: String, skinId: String): Result<String> {
    val uid = requireUserId()
    val cleanName = sanitizeName(displayName)
    val cleanSkin = skinId.replace("|", "").take(24)
    val packedProfile = "$uid|$cleanName|$cleanSkin"
    val roomsRef = db.collection("rooms")

    return try {
      // Query waiting public rooms aligned with firestore.rules
      val querySnapshot = roomsRef
        .whereEqualTo("isPrivate", false)
        .whereEqualTo("status", "waiting")
        .limit(10)
        .get()
        .await()

      val candidate = querySnapshot.documents
        .mapNotNull { doc ->
          doc.toObject(GameRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .firstOrNull { room ->
          room.playerUids.size < room.maxPlayers && !room.playerUids.contains(uid)
        }

      if (candidate != null) {
        val docRef = roomsRef.document(candidate.id)
        db.runTransaction { tx ->
          val snap = tx.get(docRef)
          val current = snap.toObject(
            GameRoom::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ) ?: throw IllegalStateException("Oda bulunamadı.")
          if (current.status != "waiting" || current.playerUids.size >= current.maxPlayers) {
            throw IllegalStateException("Oda dolu veya başladı.")
          }
          if (!current.playerUids.contains(uid)) {
            tx.update(
              docRef,
              mapOf(
                "playerUids" to (current.playerUids + uid),
                "playerProfiles" to (current.playerProfiles + packedProfile),
                "updatedAt" to FieldValue.serverTimestamp()
              )
            )
          }
        }.await()
        return Result.success(candidate.id)
      }

      // No suitable waiting public room found -> create a new matchmaking room
      val roomCode = "PUB_${generateCode(6)}"
      val docRef = roomsRef.document(roomCode)
      val payload = mapOf(
        "hostId" to uid,
        "roomCode" to roomCode,
        "isPrivate" to false,
        "status" to "waiting",
        "maxPlayers" to 6L,
        "fillWithBots" to true,
        "playerUids" to listOf(uid),
        "playerProfiles" to listOf(packedProfile),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.set(payload).await()
      Result.success(roomCode)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "rooms", auth)
      Result.failure(e)
    }
  }

  suspend fun createPrivateRoom(
    displayName: String,
    skinId: String,
    fillWithBots: Boolean = true,
    maxPlayers: Long = 6L
  ): Result<String> {
    val uid = requireUserId()
    val cleanName = sanitizeName(displayName)
    val cleanSkin = skinId.replace("|", "").take(24)
    val packedProfile = "$uid|$cleanName|$cleanSkin"
    val roomCode = generateCode(6)
    val docRef = db.collection("rooms").document(roomCode)

    return try {
      val payload = mapOf(
        "hostId" to uid,
        "roomCode" to roomCode,
        "isPrivate" to true,
        "status" to "waiting",
        "maxPlayers" to maxPlayers.coerceIn(2L, 8L),
        "fillWithBots" to fillWithBots,
        "playerUids" to listOf(uid),
        "playerProfiles" to listOf(packedProfile),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.set(payload).await()
      Result.success(roomCode)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun joinPrivateRoomByCode(
    rawCode: String,
    displayName: String,
    skinId: String
  ): Result<String> {
    val uid = requireUserId()
    val code = rawCode.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "")
    if (code.length < 4) {
      return Result.failure(IllegalArgumentException("Geçerli bir oda kodu girin."))
    }
    val cleanName = sanitizeName(displayName)
    val cleanSkin = skinId.replace("|", "").take(24)
    val packedProfile = "$uid|$cleanName|$cleanSkin"
    val docRef = db.collection("rooms").document(code)

    return try {
      db.runTransaction { tx ->
        val snap = tx.get(docRef)
        if (!snap.exists()) {
          throw IllegalStateException("Bu koda sahip oda bulunamadı.")
        }
        val room = snap.toObject(
          GameRoom::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ) ?: throw IllegalStateException("Oda verisi okunamadı.")

        if (room.playerUids.contains(uid)) {
          return@runTransaction
        }
        if (room.status != "waiting") {
          throw IllegalStateException("Bu oyun odası çoktan başladı.")
        }
        if (room.playerUids.size >= room.maxPlayers) {
          throw IllegalStateException("Oda kapasitesi dolu (${room.maxPlayers}/${room.maxPlayers}).")
        }
        tx.update(
          docRef,
          mapOf(
            "playerUids" to (room.playerUids + uid),
            "playerProfiles" to (room.playerProfiles + packedProfile),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        )
      }.await()
      Result.success(code)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun leaveRoom(roomId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("rooms").document(roomId)
    return try {
      db.runTransaction { tx ->
        val snap = tx.get(docRef)
        if (!snap.exists()) return@runTransaction
        val room = snap.toObject(
          GameRoom::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ) ?: return@runTransaction
        if (room.status == "finished") return@runTransaction
        if (room.playerUids.contains(uid)) {
          val updatedUids = room.playerUids.filterNot { it == uid }
          val updatedProfiles = room.playerProfiles.filterNot { it.startsWith("$uid|") }
          tx.update(
            docRef,
            mapOf(
              "playerUids" to updatedUids,
              "playerProfiles" to updatedProfiles,
              "updatedAt" to FieldValue.serverTimestamp()
            )
          )
        }
      }.await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateFillWithBots(roomId: String, fillWithBots: Boolean): Result<Unit> {
    requireUserId()
    val docRef = db.collection("rooms").document(roomId)
    return try {
      docRef.update(
        mapOf(
          "fillWithBots" to fillWithBots,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun startRoomMatch(roomId: String): Result<Unit> {
    requireUserId()
    val docRef = db.collection("rooms").document(roomId)
    return try {
      docRef.update(
        mapOf(
          "status" to "playing",
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun finishRoomMatch(roomId: String, winnerUid: String?, winnerName: String?): Result<Unit> {
    requireUserId()
    val docRef = db.collection("rooms").document(roomId)
    return try {
      val payload = mutableMapOf<String, Any>(
        "status" to "finished",
        "updatedAt" to FieldValue.serverTimestamp()
      )
      if (!winnerUid.isNullOrBlank()) payload["winnerUid"] = winnerUid.take(128)
      if (!winnerName.isNullOrBlank()) payload["winnerName"] = winnerName.take(40)
      docRef.update(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  fun observeRoom(roomId: String): Flow<GameRoom?> = flow {
    val uid = requireUserId()
    val docRef = db.collection("rooms").document(roomId)
    emitAll(
      docRef.snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(
              GameRoom::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
          } else {
            null
          }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
          }
          throw error
        }
    )
  }

  suspend fun spawnPlayerInRoom(roomId: String, player: RoomPlayer): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("rooms").document(roomId).collection("players").document(uid)
    val payload = mutableMapOf<String, Any>(
      "userId" to uid,
      "roomId" to roomId,
      "displayName" to sanitizeName(player.displayName),
      "skinId" to player.skinId.take(30),
      "colorIndex" to player.colorIndex.coerceIn(0L, 15L),
      "memberUids" to player.memberUids.ifEmpty { listOf(uid) }.take(8),
      "x" to player.x.coerceIn(0.0, 4000.0),
      "y" to player.y.coerceIn(0.0, 4000.0),
      "angle" to player.angle.coerceIn(-10.0, 10.0),
      "percentage" to player.percentage.coerceIn(0.0, 100.0),
      "kills" to player.kills.coerceIn(0L, 999L),
      "alive" to player.alive,
      "trailPacked" to player.trailPacked.take(12000),
      "territoryPacked" to player.territoryPacked.take(60000),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )
    if (!player.claimedKillUid.isNullOrBlank()) {
      payload["claimedKillUid"] = player.claimedKillUid.take(128)
    }

    return try {
      docRef.set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updatePlayerLiveState(
    roomId: String,
    x: Double,
    y: Double,
    angle: Double,
    percentage: Double,
    kills: Long,
    alive: Boolean,
    trailPacked: String,
    territoryPacked: String,
    memberUids: List<String>,
    claimedKillUid: String? = null
  ): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("rooms").document(roomId).collection("players").document(uid)
    val payload = mutableMapOf<String, Any>(
      "x" to x.coerceIn(0.0, 4000.0),
      "y" to y.coerceIn(0.0, 4000.0),
      "angle" to angle.coerceIn(-10.0, 10.0),
      "percentage" to percentage.coerceIn(0.0, 100.0),
      "kills" to kills.coerceIn(0L, 999L),
      "alive" to alive,
      "trailPacked" to trailPacked.take(12000),
      "territoryPacked" to territoryPacked.take(60000),
      "memberUids" to memberUids.ifEmpty { listOf(uid) }.take(8),
      "updatedAt" to FieldValue.serverTimestamp()
    )
    if (!claimedKillUid.isNullOrBlank()) {
      payload["claimedKillUid"] = claimedKillUid.take(128)
    }

    return try {
      docRef.update(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  fun observeRoomPlayers(roomId: String, currentUserId: String): Flow<List<RoomPlayer>> = flow {
    val uid = requireUserId()
    require(uid == currentUserId) { "Authenticated user mismatch." }
    val path = "rooms/$roomId/players"
    emitAll(
      db.collection("rooms").document(roomId).collection("players")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            RoomPlayer::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          )
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
          }
          throw error
        }
    )
  }
}
