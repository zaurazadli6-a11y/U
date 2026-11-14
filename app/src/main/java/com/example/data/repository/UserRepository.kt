package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.UserProfile
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
import kotlin.math.max

class UserRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
    val uid = requireUserId()
    require(uid == userId) { "Cannot observe another user's private profile." }
    val docRef = db.collection("users").document(uid)
    emitAll(
      docRef.snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(
              UserProfile::class.java,
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

  suspend fun ensureUserProfile(defaultName: String): Result<UserProfile> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    return try {
      val snapshot = docRef.get().await()
      if (snapshot.exists()) {
        val existing = snapshot.toObject(
          UserProfile::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
        Result.success(existing ?: UserProfile(id = uid, userId = uid))
      } else {
        val cleanName = defaultName.trim().ifBlank { "Oyuncu" }.take(24)
        val payload = mapOf(
          "userId" to uid,
          "displayName" to cleanName,
          "selectedSkinId" to "classic_cyan",
          "bestPercentage" to 0.0,
          "totalKills" to 0L,
          "wins" to 0L,
          "matchesPlayed" to 0L,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        val created = docRef.get().await().toObject(
          UserProfile::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
        Result.success(created ?: UserProfile(id = uid, userId = uid, displayName = cleanName))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateSelectedSkin(skinId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    return try {
      docRef.update(
        mapOf(
          "selectedSkinId" to skinId.take(30),
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

  suspend fun updateDisplayName(newName: String): Result<Unit> {
    val uid = requireUserId()
    val cleanName = newName.trim().ifBlank { "Oyuncu" }.take(24)
    val docRef = db.collection("users").document(uid)
    return try {
      docRef.update(
        mapOf(
          "displayName" to cleanName,
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

  suspend fun recordMatchStats(percentage: Double, kills: Int, won: Boolean): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    return try {
      val snapshot = docRef.get().await()
      val current = snapshot.toObject(
        UserProfile::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
      )
      if (current == null) {
        ensureUserProfile(auth.currentUser?.displayName ?: "Oyuncu")
      }
      val prevBest = current?.bestPercentage ?: 0.0
      val newBest = max(prevBest, percentage.coerceIn(0.0, 100.0))
      docRef.update(
        mapOf(
          "bestPercentage" to newBest,
          "totalKills" to ((current?.totalKills ?: 0L) + kills.coerceAtLeast(0)),
          "wins" to ((current?.wins ?: 0L) + if (won) 1L else 0L),
          "matchesPlayed" to ((current?.matchesPlayed ?: 0L) + 1L),
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
}
