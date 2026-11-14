package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ArenaSkin
import com.example.data.model.ArenaSkins
import com.example.data.model.UserProfile
import com.example.game.drawSkinPreview
import com.example.ui.UiState
import java.util.Locale

@Composable
fun LobbyScreen(
  profileState: UiState<UserProfile?>,
  isBusy: Boolean,
  errorMessage: String?,
  onClearError: () -> Unit,
  onSelectSkin: (String) -> Unit,
  onUpdateName: (String) -> Unit,
  onStartQuickMatch: () -> Unit,
  onCreatePrivateRoom: (Boolean) -> Unit,
  onJoinPrivateRoom: (String) -> Unit,
  onStartSolo: () -> Unit,
  onSignOut: () -> Unit
) {
  val snackbarHostState = remember { SnackbarHostState() }
  var showNameDialog by remember { mutableStateOf(false) }
  var showPrivateRoomSheet by remember { mutableStateOf(false) }
  var joinCodeInput by remember { mutableStateOf("") }
  var fillPrivateWithBots by remember { mutableStateOf(true) }

  LaunchedEffect(errorMessage) {
    if (errorMessage != null) {
      snackbarHostState.showSnackbar(errorMessage)
      onClearError()
    }
  }

  val profile = (profileState as? UiState.Success)?.data ?: UserProfile()
  val equippedSkin = ArenaSkins.byId(profile.selectedSkinId)

  Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Header: Player Identity & Sign Out
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(18.dp))
              .clickable { showNameDialog = true }
              .padding(vertical = 4.dp, horizontal = 4.dp)
          ) {
            Box(
              modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(equippedSkin.baseColor.copy(alpha = 0.16f))
                .border(2.dp, equippedSkin.baseColor, RoundedCornerShape(16.dp)),
              contentAlignment = Alignment.Center
            ) {
              Canvas(modifier = Modifier.size(38.dp)) {
                drawSkinPreview(equippedSkin)
              }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = profile.displayName.ifBlank { "Oyuncu" },
                  style = MaterialTheme.typography.titleLarge,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                  imageVector = Icons.Rounded.Edit,
                  contentDescription = "İsmi düzenle",
                  modifier = Modifier.size(16.dp),
                  tint = MaterialTheme.colorScheme.primary
                )
              }
              Text(
                text = "Kostüm: ${equippedSkin.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          IconButton(
            onClick = onSignOut,
            modifier = Modifier
              .size(48.dp)
              .testTag("sign_out_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Logout,
              contentDescription = "Çıkış Yap",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Lifetime Stats Strip
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          StatTile(
            modifier = Modifier.weight(1f),
            label = "EN İYİ ALAN",
            value = String.format(Locale.US, "%.1f%%", profile.bestPercentage),
            accent = Color(0xFF00A8E8)
          )
          StatTile(
            modifier = Modifier.weight(1f),
            label = "ELEMELER",
            value = "${profile.totalKills}",
            accent = Color(0xFFFF5E62)
          )
          StatTile(
            modifier = Modifier.weight(1f),
            label = "ZAFER",
            value = "${profile.wins}",
            accent = Color(0xFFFFB800)
          )
        }

        // Primary Action 1: Online Random Matchmaking
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_match_card"),
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = Color.Transparent),
          elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(
                Brush.linearGradient(
                  colors = listOf(Color(0xFF00A8E8), Color(0xFF0077B6))
                )
              )
              .padding(20.dp)
          ) {
            Column {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
              ) {
                Surface(
                  color = Color.White.copy(alpha = 0.22f),
                  shape = RoundedCornerShape(50)
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Public,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "ÇEVRİMİÇİ ARENA",
                      style = MaterialTheme.typography.labelSmall,
                      color = Color.White
                    )
                  }
                }
                Icon(
                  imageVector = Icons.Rounded.FlashOn,
                  contentDescription = null,
                  tint = Color(0xFFFFD600),
                  modifier = Modifier.size(28.dp)
                )
              }
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "Rastgele Eşleşme",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
              )
              Text(
                text = "Çevrimiçi rakiplerle anında eşleş ve dairesel kağıt haritasını fethet!",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
              )
              Spacer(modifier = Modifier.height(16.dp))
              Button(
                onClick = onStartQuickMatch,
                enabled = !isBusy,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(52.dp)
                  .testTag("quick_match_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color.White,
                  contentColor = Color(0xFF0077B6)
                )
              ) {
                if (isBusy) {
                  CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                } else {
                  Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Hemen Eşleş & Oyna",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }

        // Primary Action 2 & 3: Private Rooms + Solo Bots
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          ModeActionCard(
            modifier = Modifier
              .weight(1f)
              .testTag("private_room_button"),
            icon = Icons.Rounded.Lock,
            accent = Color(0xFF7C4DFF),
            title = "Özel Oda",
            subtitle = "Kod kur veya katıl",
            onClick = { showPrivateRoomSheet = true }
          )
          ModeActionCard(
            modifier = Modifier
              .weight(1f)
              .testTag("solo_play_button"),
            icon = Icons.Rounded.SmartToy,
            accent = Color(0xFFFF5E62),
            title = "Tek Kişilik",
            subtitle = "7 akıllı bota karşı",
            onClick = onStartSolo
          )
        }

        // Skin Selector Section
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Küp Kostümleri",
            style = MaterialTheme.typography.titleLarge
          )
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
          ) {
            items(ArenaSkins.all, key = { it.id }) { skin ->
              SkinCardItem(
                skin = skin,
                selected = skin.id == equippedSkin.id,
                onClick = { onSelectSkin(skin.id) }
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }

  // Edit Display Name Dialog
  if (showNameDialog) {
    var tempName by remember { mutableStateOf(profile.displayName) }
    AlertDialog(
      onDismissRequest = { showNameDialog = false },
      title = { Text("Oyuncu İsmi") },
      text = {
        OutlinedTextField(
          value = tempName,
          onValueChange = { if (it.length <= 24) tempName = it },
          label = { Text("Görünen adın") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onUpdateName(tempName)
            showNameDialog = false
          }
        ) {
          Text("Kaydet")
        }
      },
      dismissButton = {
        TextButton(onClick = { showNameDialog = false }) {
          Text("İptal")
        }
      }
    )
  }

  // Private Room Create / Join Modal Dialog
  if (showPrivateRoomSheet) {
    AlertDialog(
      onDismissRequest = { showPrivateRoomSheet = false },
      title = { Text("Özel Oyun Odası") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Text(
            text = "Arkadaşlarınla aynı arenada yarışmak için yeni bir oda kur veya davet kodunu gir.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "Boş yerlere AI Bot ekle",
              style = MaterialTheme.typography.bodyLarge
            )
            Switch(
              checked = fillPrivateWithBots,
              onCheckedChange = { fillPrivateWithBots = it }
            )
          }

          Button(
            onClick = {
              showPrivateRoomSheet = false
              onCreatePrivateRoom(fillPrivateWithBots)
            },
            enabled = !isBusy,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("create_private_room_button"),
            shape = RoundedCornerShape(14.dp)
          ) {
            Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Yeni Özel Oda Kur")
          }

          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              OutlinedTextField(
                value = joinCodeInput,
                onValueChange = { joinCodeInput = it.uppercase().take(8) },
                label = { Text("Oda Kodu (Örn: A7K9P2)") },
                leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("join_room_code_input")
              )
              FilledTonalButton(
                onClick = {
                  showPrivateRoomSheet = false
                  onJoinPrivateRoom(joinCodeInput)
                },
                enabled = !isBusy && joinCodeInput.trim().length >= 4,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("join_private_room_button"),
                shape = RoundedCornerShape(12.dp)
              ) {
                Text("Koda Göre Odaya Katıl")
              }
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        OutlinedButton(onClick = { showPrivateRoomSheet = false }) {
          Text("Kapat")
        }
      }
    )
  }
}

@Composable
private fun StatTile(
  modifier: Modifier = Modifier,
  label: String,
  value: String,
  accent: Color
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 3.dp
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        color = accent
      )
    }
  }
}

@Composable
private fun ModeActionCard(
  modifier: Modifier = Modifier,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accent: Color,
  title: String,
  subtitle: String,
  onClick: () -> Unit
) {
  Card(
    modifier = modifier.clickable(onClick = onClick),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(accent.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = accent,
          modifier = Modifier.size(24.dp)
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun SkinCardItem(
  skin: ArenaSkin,
  selected: Boolean,
  onClick: () -> Unit
) {
  val borderColor = if (selected) skin.baseColor else MaterialTheme.colorScheme.surfaceVariant
  Surface(
    modifier = Modifier
      .width(116.dp)
      .clip(RoundedCornerShape(20.dp))
      .border(
        width = if (selected) 2.5.dp else 1.dp,
        color = borderColor,
        shape = RoundedCornerShape(20.dp)
      )
      .clickable(onClick = onClick)
      .testTag("skin_${skin.id}"),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = if (selected) 6.dp else 2.dp
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(56.dp)) {
          drawSkinPreview(skin)
        }
        if (selected) {
          Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = "Seçili",
            tint = skin.baseColor,
            modifier = Modifier
              .align(Alignment.TopEnd)
              .size(20.dp)
          )
        }
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = skin.name,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}
