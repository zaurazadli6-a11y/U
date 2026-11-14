package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ArenaSkins
import com.example.data.model.GameRoom
import com.example.game.drawSkinPreview
import kotlinx.coroutines.launch

@Composable
fun WaitingRoomScreen(
  roomId: String,
  room: GameRoom?,
  currentUserId: String,
  isBusy: Boolean,
  onToggleBots: (Boolean) -> Unit,
  onStartMatch: () -> Unit,
  onLeaveRoom: () -> Unit
) {
  BackHandler { onLeaveRoom() }

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  val isHost = room?.hostId == currentUserId
  val participants = room?.parsedParticipants() ?: emptyList()

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    containerColor = MaterialTheme.colorScheme.background
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .statusBarsPadding()
        .navigationBarsPadding(),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 560.dp)
          .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Top App Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = onLeaveRoom,
            modifier = Modifier
              .size(48.dp)
              .testTag("leave_waiting_room_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = "Odadan ayrıl"
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = if (room?.isPrivate == true) "Özel Oyun Odası" else "Rastgele Eşleşme Odası",
              style = MaterialTheme.typography.titleLarge
            )
            Text(
              text = "Oyuncular bağlanıyor (${participants.size}/${room?.maxPlayers ?: 6})",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Room Code Card
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text(
                text = "ODA KODU",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = room?.roomCode ?: roomId,
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF38BDF8),
                letterSpacing = 3.sp
              )
            }
            Row {
              IconButton(
                onClick = {
                  val code = room?.roomCode ?: roomId
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  clipboard.setPrimaryClip(ClipData.newPlainText("Oda Kodu", code))
                  scope.launch {
                    snackbarHostState.showSnackbar("Oda kodu kopyalandı: $code")
                  }
                },
                modifier = Modifier
                  .size(48.dp)
                  .testTag("copy_room_code_button")
              ) {
                Icon(
                  imageVector = Icons.Rounded.ContentCopy,
                  contentDescription = "Kodu kopyala",
                  tint = Color.White
                )
              }
              IconButton(
                onClick = {
                  val code = room?.roomCode ?: roomId
                  val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                      Intent.EXTRA_TEXT,
                      "Paper Arena'da bana katıl! Oda Kodu: $code"
                    )
                  }
                  context.startActivity(Intent.createChooser(shareIntent, "Oda Kodunu Paylaş"))
                },
                modifier = Modifier.size(48.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.Share,
                  contentDescription = "Oda kodunu paylaş",
                  tint = Color.White
                )
              }
            }
          }
        }

        // Bot Fill Toggle (host only)
        if (room != null) {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Boş slotları AI Botlarla doldur",
                  style = MaterialTheme.typography.titleMedium
                )
                Text(
                  text = if (isHost) "Arenanın canlı kalması için bot ekler" else "Oda kurucusu tarafından belirlenir",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Switch(
                checked = room.fillWithBots,
                onCheckedChange = { if (isHost) onToggleBots(it) },
                enabled = isHost
              )
            }
          }
        }

        // Participant List
        Text(
          text = "Odadaki Oyuncular",
          style = MaterialTheme.typography.titleLarge
        )

        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          itemsIndexed(participants, key = { _, item -> item.uid }) { index, p ->
            val skin = ArenaSkins.byId(p.skinId)
            val isRoomHost = p.uid == room?.hostId
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(18.dp),
              color = MaterialTheme.colorScheme.surface,
              shadowElevation = 2.dp
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(skin.baseColor.copy(alpha = 0.15f)),
                  contentAlignment = Alignment.Center
                ) {
                  Canvas(modifier = Modifier.size(34.dp)) {
                    drawSkinPreview(skin)
                  }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = if (p.uid == currentUserId) "${p.displayName} (Sen)" else p.displayName,
                    style = MaterialTheme.typography.titleMedium
                  )
                  Text(
                    text = skin.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                if (isRoomHost) {
                  Surface(
                    color = Color(0xFFFFB800).copy(alpha = 0.18f),
                    shape = CircleShape
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = "Oda Kurucusu",
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(16.dp)
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = "Kurucu",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD97706)
                      )
                    }
                  }
                }
              }
            }
          }
        }

        // Bottom Start / Waiting Banner
        if (isHost) {
          Button(
            onClick = onStartMatch,
            enabled = !isBusy,
            modifier = Modifier
              .fillMaxWidth()
              .height(58.dp)
              .testTag("start_room_match_button"),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF00A8E8),
              contentColor = Color.White
            )
          ) {
            if (isBusy) {
              CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
              Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Maçı Başlat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
            }
          }
        } else {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = "Kurucunun maçı başlatması bekleniyor...",
                style = MaterialTheme.typography.titleMedium
              )
            }
          }
        }
      }
    }
  }
}
