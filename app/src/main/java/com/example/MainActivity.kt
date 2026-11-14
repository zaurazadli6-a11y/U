package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.repository.RoomRepository
import com.example.data.repository.UserRepository
import com.example.ui.ArenaScreenState
import com.example.ui.PaperArenaViewModel
import com.example.ui.screens.GameArenaScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.WaitingRoomScreen
import com.example.ui.theme.PaperArenaTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      PaperArenaTheme {
        PaperArenaRoot()
      }
    }
  }
}

@Composable
fun PaperArenaRoot(auth: FirebaseAuth = Firebase.auth) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

  LaunchedEffect(Unit) {
    if (auth.currentUser == null) {
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = scope
      )
    }
  }

  val user = currentUser
  if (user == null) {
    SignInScreen(
      onAuthSuccess = {}
    )
  } else {
    val uid = user.uid
    val displayName = user.displayName ?: "Oyuncu"
    key(uid) {
      AuthenticatedPaperArenaApp(
        currentUserId = uid,
        initialDisplayName = displayName,
        onSignOut = {
          signOut(
            credentialManager = credentialManager,
            scope = scope
          )
        }
      )
    }
  }
}

@Composable
private fun AuthenticatedPaperArenaApp(
  currentUserId: String,
  initialDisplayName: String,
  onSignOut: () -> Unit
) {
  val viewModel: PaperArenaViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        PaperArenaViewModel(
          userRepository = UserRepository(db),
          roomRepository = RoomRepository(db),
          currentUserId = currentUserId,
          initialDisplayName = initialDisplayName
        )
      }
    }
  )

  val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
  val screenState by viewModel.screenState.collectAsStateWithLifecycle()
  val currentRoom by viewModel.currentRoom.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
  val matchResult by viewModel.matchResult.collectAsStateWithLifecycle()

  when (val screen = screenState) {
    is ArenaScreenState.Lobby -> {
      LobbyScreen(
        profileState = profileState,
        isBusy = isBusy,
        errorMessage = errorMessage,
        onClearError = viewModel::clearError,
        onSelectSkin = viewModel::selectSkin,
        onUpdateName = viewModel::updateDisplayName,
        onStartQuickMatch = viewModel::startQuickMatchmaking,
        onCreatePrivateRoom = viewModel::createPrivateRoom,
        onJoinPrivateRoom = viewModel::joinPrivateRoom,
        onStartSolo = viewModel::startSoloMatch,
        onSignOut = onSignOut
      )
    }

    is ArenaScreenState.WaitingRoom -> {
      WaitingRoomScreen(
        roomId = screen.roomId,
        room = currentRoom,
        currentUserId = currentUserId,
        isBusy = isBusy,
        onToggleBots = { fill -> viewModel.toggleRoomBots(screen.roomId, fill) },
        onStartMatch = { viewModel.hostStartRoomMatch(screen.roomId) },
        onLeaveRoom = viewModel::leaveRoomAndReturnToLobby
      )
    }

    is ArenaScreenState.Playing -> {
      GameArenaScreen(
        engine = viewModel.engine,
        isMultiplayer = screen.isMultiplayer,
        roomCode = currentRoom?.roomCode,
        matchResult = matchResult,
        onMatchEvents = viewModel::onMatchEvents,
        onReplay = viewModel::replayAfterMatch,
        onExitToLobby = viewModel::leaveRoomAndReturnToLobby
      )
    }
  }
}
