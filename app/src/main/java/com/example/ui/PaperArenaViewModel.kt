package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.GameRoom
import com.example.data.model.RoomPlayer
import com.example.data.model.UserProfile
import com.example.data.repository.RoomRepository
import com.example.data.repository.UserRepository
import com.example.game.MatchEvent
import com.example.game.PaperArenaEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

sealed interface ArenaScreenState {
  data object Lobby : ArenaScreenState
  data class WaitingRoom(val roomId: String) : ArenaScreenState
  data class Playing(
    val isMultiplayer: Boolean,
    val roomId: String? = null
  ) : ArenaScreenState
}

data class MatchResultSummary(
  val finalPercentage: Float,
  val kills: Int,
  val rank: Int,
  val totalPlayers: Int,
  val winnerName: String,
  val isVictory: Boolean,
  val isNewBest: Boolean
)

class PaperArenaViewModel(
  private val userRepository: UserRepository,
  private val roomRepository: RoomRepository,
  val currentUserId: String,
  private val initialDisplayName: String
) : ViewModel() {

  val engine = PaperArenaEngine()

  val userProfileState: StateFlow<UiState<UserProfile?>> = userRepository.observeUserProfile(currentUserId)
    .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, "Error observing user profile", rootError)
      emit(UiState.Error(rootError.message ?: "Profil yüklenemedi"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = UiState.Loading
    )

  private val _screenState = MutableStateFlow<ArenaScreenState>(ArenaScreenState.Lobby)
  val screenState: StateFlow<ArenaScreenState> = _screenState.asStateFlow()

  private val _currentRoom = MutableStateFlow<GameRoom?>(null)
  val currentRoom: StateFlow<GameRoom?> = _currentRoom.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _matchResult = MutableStateFlow<MatchResultSummary?>(null)
  val matchResult: StateFlow<MatchResultSummary?> = _matchResult.asStateFlow()

  private var roomObserverJob: Job? = null
  private var remotePlayersJob: Job? = null
  private var syncLoopJob: Job? = null
  private var matchFinalized = false

  init {
    viewModelScope.launch {
      userRepository.ensureUserProfile(initialDisplayName)
    }
  }

  fun clearError() {
    _errorMessage.value = null
  }

  private fun activeProfile(): UserProfile {
    val state = userProfileState.value
    return if (state is UiState.Success && state.data != null) {
      state.data
    } else {
      UserProfile(
        id = currentUserId,
        userId = currentUserId,
        displayName = initialDisplayName.ifBlank { "Oyuncu" },
        selectedSkinId = "classic_cyan"
      )
    }
  }

  fun selectSkin(skinId: String) {
    viewModelScope.launch {
      userRepository.updateSelectedSkin(skinId).onFailure {
        _errorMessage.value = it.localizedMessage ?: "Kostüm seçilemedi."
      }
    }
  }

  fun updateDisplayName(name: String) {
    val trimmed = name.trim()
    if (trimmed.length < 2) {
      _errorMessage.value = "İsim en az 2 karakter olmalıdır."
      return
    }
    viewModelScope.launch {
      userRepository.updateDisplayName(trimmed).onFailure {
        _errorMessage.value = it.localizedMessage ?: "İsim güncellenemedi."
      }
    }
  }

  // --- Solo Mode ---
  fun startSoloMatch() {
    stopMultiplayerJobs()
    _matchResult.value = null
    matchFinalized = false
    val profile = activeProfile()
    engine.setupSoloMatch(
      localUid = currentUserId,
      localName = profile.displayName,
      localSkinId = profile.selectedSkinId,
      botCount = 7
    )
    _screenState.value = ArenaScreenState.Playing(isMultiplayer = false, roomId = null)
  }

  // --- Random Matchmaking (Public Room) ---
  fun startQuickMatchmaking() {
    if (_isBusy.value) return
    _isBusy.value = true
    _errorMessage.value = null
    val profile = activeProfile()
    viewModelScope.launch {
      val result = roomRepository.findOrCreatePublicRoom(profile.displayName, profile.selectedSkinId)
      _isBusy.value = false
      result.onSuccess { roomId ->
        enterWaitingRoom(roomId)
      }.onFailure { err ->
        _errorMessage.value = err.localizedMessage ?: "Eşleştirme başarısız oldu."
      }
    }
  }

  // --- Private Room ---
  fun createPrivateRoom(fillWithBots: Boolean) {
    if (_isBusy.value) return
    _isBusy.value = true
    _errorMessage.value = null
    val profile = activeProfile()
    viewModelScope.launch {
      val result = roomRepository.createPrivateRoom(
        displayName = profile.displayName,
        skinId = profile.selectedSkinId,
        fillWithBots = fillWithBots
      )
      _isBusy.value = false
      result.onSuccess { roomId ->
        enterWaitingRoom(roomId)
      }.onFailure { err ->
        _errorMessage.value = err.localizedMessage ?: "Özel oda oluşturulamadı."
      }
    }
  }

  fun joinPrivateRoom(code: String) {
    if (_isBusy.value) return
    _isBusy.value = true
    _errorMessage.value = null
    val profile = activeProfile()
    viewModelScope.launch {
      val result = roomRepository.joinPrivateRoomByCode(
        rawCode = code,
        displayName = profile.displayName,
        skinId = profile.selectedSkinId
      )
      _isBusy.value = false
      result.onSuccess { roomId ->
        enterWaitingRoom(roomId)
      }.onFailure { err ->
        _errorMessage.value = err.localizedMessage ?: "Odaya katılınamadı."
      }
    }
  }

  private fun enterWaitingRoom(roomId: String) {
    stopMultiplayerJobs()
    _matchResult.value = null
    matchFinalized = false
    _screenState.value = ArenaScreenState.WaitingRoom(roomId)

    roomObserverJob = viewModelScope.launch {
      roomRepository.observeRoom(roomId)
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val rootError = firestoreError ?: error
          Log.w(TAG, "Error observing room $roomId", rootError)
          _errorMessage.value = rootError.localizedMessage ?: "Oda bağlantısı kesildi."
        }
        .collect { room ->
          _currentRoom.value = room
          if (room != null && room.status == "playing" && _screenState.value is ArenaScreenState.WaitingRoom) {
            beginMultiplayerArena(room)
          } else if (room != null && room.status == "finished" && _screenState.value is ArenaScreenState.Playing && !matchFinalized) {
            finalizeLocalMatch(forcedWinnerName = room.winnerName, forcedVictory = (room.winnerUid == currentUserId))
          }
        }
    }
  }

  fun toggleRoomBots(roomId: String, fillWithBots: Boolean) {
    viewModelScope.launch {
      roomRepository.updateFillWithBots(roomId, fillWithBots).onFailure {
        _errorMessage.value = it.localizedMessage ?: "Bot ayarı güncellenemedi."
      }
    }
  }

  fun hostStartRoomMatch(roomId: String) {
    if (_isBusy.value) return
    _isBusy.value = true
    viewModelScope.launch {
      roomRepository.startRoomMatch(roomId).onFailure {
        _errorMessage.value = it.localizedMessage ?: "Maç başlatılamadı."
      }
      _isBusy.value = false
    }
  }

  private fun beginMultiplayerArena(room: GameRoom) {
    val profile = activeProfile()
    val participants = room.parsedParticipants().map { Triple(it.uid, it.displayName, it.skinId) }
    val isHost = room.hostId == currentUserId
    val localPlayer = engine.setupMultiplayerMatch(
      localUid = currentUserId,
      localName = profile.displayName,
      localSkinId = profile.selectedSkinId,
      participants = participants,
      fillWithBots = room.fillWithBots,
      isHost = isHost
    )
    _screenState.value = ArenaScreenState.Playing(isMultiplayer = true, roomId = room.id)

    val memberUids = room.playerUids.ifEmpty { listOf(currentUserId) }

    // 1. Spawn local player doc in Firestore
    viewModelScope.launch {
      roomRepository.spawnPlayerInRoom(
        room.id,
        RoomPlayer(
          userId = currentUserId,
          roomId = room.id,
          displayName = localPlayer.name,
          skinId = localPlayer.skinId,
          colorIndex = localPlayer.slot.toLong(),
          memberUids = memberUids,
          x = localPlayer.x.toDouble(),
          y = localPlayer.y.toDouble(),
          angle = localPlayer.angle.toDouble(),
          percentage = localPlayer.percentage.toDouble(),
          kills = 0L,
          alive = true,
          trailPacked = "",
          territoryPacked = engine.packTerritoryForSlot(localPlayer.slot)
        )
      )
    }

    // 2. Listen to all room players in real-time
    remotePlayersJob?.cancel()
    remotePlayersJob = viewModelScope.launch {
      roomRepository.observeRoomPlayers(room.id, currentUserId)
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          Log.w(TAG, "Error observing room players", firestoreError ?: error)
        }
        .collect { remotePlayers ->
          engine.applyRemotePlayers(remotePlayers, currentUserId)
        }
    }

    // 3. Push local state at ~6.5 Hz (every 150ms) while alive
    syncLoopJob?.cancel()
    syncLoopJob = viewModelScope.launch {
      while (isActive && _screenState.value is ArenaScreenState.Playing) {
        delay(150L)
        val lp = engine.localPlayer() ?: break
        roomRepository.updatePlayerLiveState(
          roomId = room.id,
          x = lp.x.toDouble(),
          y = lp.y.toDouble(),
          angle = lp.angle.toDouble(),
          percentage = lp.percentage.toDouble(),
          kills = lp.kills.toLong(),
          alive = lp.alive,
          trailPacked = engine.packLocalTrail(lp),
          territoryPacked = engine.packTerritoryForSlot(lp.slot),
          memberUids = memberUids,
          claimedKillUid = engine.pendingKillClaimUid
        )
        if (!lp.alive) break
      }
    }
  }

  fun onMatchEvents(events: List<MatchEvent>) {
    if (matchFinalized) return
    val lp = engine.localPlayer() ?: return

    val localDied = events.any { it is MatchEvent.Elimination && it.victimIsLocal } || !lp.alive
    val localWon = lp.percentage >= 98.5f
    val allOthersEliminated = engine.players.size > 1 && engine.players.none { !it.isLocalPlayer && it.alive }

    if (localDied || localWon || allOthersEliminated) {
      val playing = _screenState.value as? ArenaScreenState.Playing
      if (playing?.isMultiplayer == true && playing.roomId != null && (localWon || allOthersEliminated)) {
        viewModelScope.launch {
          roomRepository.finishRoomMatch(playing.roomId, currentUserId, lp.name)
        }
      }
      finalizeLocalMatch(
        forcedWinnerName = if (localWon || allOthersEliminated) lp.name else null,
        forcedVictory = localWon || allOthersEliminated
      )
    }
  }

  private fun finalizeLocalMatch(forcedWinnerName: String? = null, forcedVictory: Boolean = false) {
    if (matchFinalized) return
    matchFinalized = true
    val lp = engine.localPlayer()
    val pct = lp?.percentage ?: 0f
    val kills = lp?.kills ?: 0
    val sorted = engine.players.sortedByDescending { if (it.alive) it.percentage else -1f }
    val rank = (sorted.indexOfFirst { it.isLocalPlayer } + 1).coerceAtLeast(1)
    val leader = sorted.firstOrNull()
    val isVictory = forcedVictory || (lp?.alive == true && pct >= 98.5f)
    val winnerName = forcedWinnerName ?: leader?.name ?: lp?.name ?: "Oyuncu"

    val prevBest = (userProfileState.value as? UiState.Success)?.data?.bestPercentage ?: 0.0
    val isNewBest = pct > prevBest

    _matchResult.value = MatchResultSummary(
      finalPercentage = pct,
      kills = kills,
      rank = if (isVictory) 1 else rank,
      totalPlayers = engine.players.size,
      winnerName = winnerName,
      isVictory = isVictory,
      isNewBest = isNewBest
    )

    viewModelScope.launch {
      userRepository.recordMatchStats(pct.toDouble(), kills, isVictory)
    }
  }

  fun leaveRoomAndReturnToLobby() {
    val roomId = (_screenState.value as? ArenaScreenState.WaitingRoom)?.roomId
      ?: (_screenState.value as? ArenaScreenState.Playing)?.roomId
    stopMultiplayerJobs()
    _matchResult.value = null
    _currentRoom.value = null
    _screenState.value = ArenaScreenState.Lobby
    if (roomId != null) {
      viewModelScope.launch {
        roomRepository.leaveRoom(roomId)
      }
    }
  }

  fun replayAfterMatch() {
    val current = _screenState.value
    if (current is ArenaScreenState.Playing && !current.isMultiplayer) {
      startSoloMatch()
    } else {
      leaveRoomAndReturnToLobby()
    }
  }

  private fun stopMultiplayerJobs() {
    roomObserverJob?.cancel()
    roomObserverJob = null
    remotePlayersJob?.cancel()
    remotePlayersJob = null
    syncLoopJob?.cancel()
    syncLoopJob = null
  }

  override fun onCleared() {
    super.onCleared()
    stopMultiplayerJobs()
  }

  private companion object {
    const val TAG = "PaperArenaVM"
  }
}
