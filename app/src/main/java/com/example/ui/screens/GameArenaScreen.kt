package com.example.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.MatchEvent
import com.example.game.PaperArenaEngine
import com.example.game.drawPaperArena
import com.example.ui.MatchResultSummary
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun GameArenaScreen(
  engine: PaperArenaEngine,
  isMultiplayer: Boolean,
  roomCode: String?,
  matchResult: MatchResultSummary?,
  onMatchEvents: (List<MatchEvent>) -> Unit,
  onReplay: () -> Unit,
  onExitToLobby: () -> Unit
) {
  BackHandler { onExitToLobby() }

  val context = LocalContext.current
  val textMeasurer = rememberTextMeasurer()

  // Trigger a frame tick state so Compose Canvas redraws at 60fps smoothly
  var frameTick by remember { mutableIntStateOf(0) }
  var camX by remember { mutableFloatStateOf(engine.localPlayer()?.x ?: PaperArenaEngine.CENTER) }
  var camY by remember { mutableFloatStateOf(engine.localPlayer()?.y ?: PaperArenaEngine.CENTER) }
  var camZoom by remember { mutableFloatStateOf(1.15f) }
  var pulsePhase by remember { mutableFloatStateOf(0f) }

  // Floating joystick state (anywhere on phone screen!)
  var joystickOrigin by remember { mutableStateOf<Offset?>(null) }
  var joystickKnob by remember { mutableStateOf<Offset?>(null) }

  LaunchedEffect(matchResult != null) {
    var lastNanos = 0L
    while (isActive && matchResult == null) {
      withFrameNanos { nanos ->
        if (lastNanos == 0L) lastNanos = nanos
        val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastNanos = nanos
        pulsePhase += dt

        val events = engine.step(dt)
        if (events.isNotEmpty()) {
          if (events.any { (it is MatchEvent.Capture && it.isLocal && it.gainedPercent >= 1.5f) || (it is MatchEvent.Elimination && (it.killerIsLocal || it.victimIsLocal)) }) {
            triggerShortHaptic(context)
          }
          onMatchEvents(events)
        }

        // Smooth camera follow + slight zoom-out as player grows bigger (Paper.io 2 feel!)
        val lp = engine.localPlayer()
        if (lp != null) {
          val followBlend = (dt * 7.5f).coerceIn(0f, 1f)
          camX += (lp.x - camX) * followBlend
          camY += (lp.y - camY) * followBlend
          val desiredZoom = (1.16f - (lp.percentage * 0.0045f)).coerceIn(0.75f, 1.16f)
          camZoom += (desiredZoom - camZoom) * (dt * 2.5f).coerceIn(0f, 1f)
        }

        frameTick++
      }
    }
  }

  val localPlayer = engine.localPlayer()
  val leaderboard = remember(frameTick) { engine.leaderboard().take(4) }
  val killFeed = remember(frameTick) { engine.killFeed.take(2) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFFE8EDF5))
  ) {
    // 1. Full-Screen 60FPS Arena Canvas + Floating Touch Joystick
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .testTag("arena_canvas")
        .pointerInput(matchResult == null) {
          if (matchResult != null) return@pointerInput
          detectDragGestures(
            onDragStart = { startOffset ->
              joystickOrigin = startOffset
              joystickKnob = startOffset
            },
            onDrag = { change, _ ->
              change.consume()
              val origin = joystickOrigin ?: change.position
              val dx = change.position.x - origin.x
              val dy = change.position.y - origin.y
              val dist = hypot(dx, dy)
              val maxRadius = 110f
              joystickKnob = if (dist > maxRadius) {
                Offset(origin.x + (dx / dist) * maxRadius, origin.y + (dy / dist) * maxRadius)
              } else {
                change.position
              }
              if (dist > 10f) {
                engine.setLocalTargetAngle(atan2(dy, dx))
              }
            },
            onDragEnd = {
              joystickOrigin = null
              joystickKnob = null
            },
            onDragCancel = {
              joystickOrigin = null
              joystickKnob = null
            }
          )
        }
    ) {
      // Read frameTick to bind draw phase to 60fps loop
      @Suppress("UNUSED_EXPRESSION")
      frameTick

      drawPaperArena(
        engine = engine,
        camX = camX,
        camY = camY,
        zoom = camZoom,
        pulsePhase = pulsePhase
      )

      // Floating +X.X% capture popups
      val halfW = size.width / 2f
      val halfH = size.height / 2f
      for (pop in engine.popups) {
        val sx = (pop.x - camX) * camZoom + halfW
        val sy = (pop.y - camY) * camZoom + halfH
        val alpha = (pop.life / pop.maxLife).coerceIn(0f, 1f)
        drawText(
          textMeasurer = textMeasurer,
          text = pop.text,
          topLeft = Offset(sx - 36f, sy - 16f),
          style = TextStyle(
            color = pop.color.copy(alpha = alpha),
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
          )
        )
      }

      // Off-screen #1 Crown Leader pointer arrow around screen edge
      val leader = engine.players.firstOrNull { it.slot == engine.leaderSlot() && it.alive && !it.isLocalPlayer }
      if (leader != null && localPlayer != null && localPlayer.alive) {
        val ldx = (leader.x - camX) * camZoom
        val ldy = (leader.y - camY) * camZoom
        if (kotlin.math.abs(ldx) > halfW * 0.85f || kotlin.math.abs(ldy) > halfH * 0.85f) {
          val angle = atan2(ldy, ldx)
          val ringDist = minOf(halfW, halfH) * 0.72f
          val ax = halfW + cos(angle) * ringDist
          val ay = halfH + sin(angle) * ringDist
          rotate(degrees = angle * 180f / PI.toFloat(), pivot = Offset(ax, ay)) {
            val arrow = Path().apply {
              moveTo(ax + 16f, ay)
              lineTo(ax - 10f, ay - 11f)
              lineTo(ax - 5f, ay)
              lineTo(ax - 10f, ay + 11f)
              close()
            }
            drawPath(path = arrow, color = Color(0xFFFFB800))
          }
        }
      }

      // Floating translucent virtual joystick indicator
      val jOrigin = joystickOrigin
      val jKnob = joystickKnob
      if (jOrigin != null && jKnob != null) {
        drawCircle(
          color = Color(0x280F172A),
          radius = 96f,
          center = jOrigin
        )
        drawCircle(
          color = Color(0x55FFFFFF),
          radius = 96f,
          center = jOrigin,
          style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
        drawCircle(
          color = Color(0xCC00A8E8),
          radius = 38f,
          center = jKnob
        )
      }
    }

    // 2. Top HUD Overlay (safe area insets for phone notches)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top
    ) {
      // Left: Personal Score % + Kills + Exit button
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = Color.White.copy(alpha = 0.92f),
          shadowElevation = 6.dp
        ) {
          Column(
            modifier = Modifier
              .width(154.dp)
              .padding(horizontal = 12.dp, vertical = 10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = String.format(Locale.US, "%.1f%%", localPlayer?.percentage ?: 0f),
                style = MaterialTheme.typography.titleLarge,
                color = localPlayer?.shadowColor ?: Color(0xFF0077B6)
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.Bolt,
                  contentDescription = "Eleme sayısı",
                  tint = Color(0xFFFF5E62),
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "${localPlayer?.kills ?: 0}",
                  style = MaterialTheme.typography.titleMedium,
                  color = Color(0xFF0F172A)
                )
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
              progress = { ((localPlayer?.percentage ?: 0f) / 100f).coerceIn(0f, 1f) },
              modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape),
              color = localPlayer?.baseColor ?: Color(0xFF00A8E8),
              trackColor = Color(0xFFE2E8F0)
            )
            if (isMultiplayer && roomCode != null) {
              Spacer(modifier = Modifier.height(5.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.Public,
                  contentDescription = null,
                  tint = Color(0xFF00A8E8),
                  modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "CANLI • $roomCode",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFF475569)
                )
              }
            }
          }
        }

        // Exit button (48dp touch target)
        Surface(
          shape = CircleShape,
          color = Color.White.copy(alpha = 0.9f),
          shadowElevation = 4.dp
        ) {
          IconButton(
            onClick = onExitToLobby,
            modifier = Modifier
              .size(48.dp)
              .testTag("exit_arena_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
              contentDescription = "Arenadan çık",
              tint = Color(0xFF334155)
            )
          }
        }
      }

      // Right: Live Leaderboard Card (Paper.io 2 signature top-right board!)
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.90f),
        shadowElevation = 6.dp
      ) {
        Column(
          modifier = Modifier
            .width(166.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          leaderboard.forEachIndexed { idx, p ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
              ) {
                Box(
                  modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(p.baseColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (idx == 0) {
                  Icon(
                    imageVector = Icons.Rounded.WorkspacePremium,
                    contentDescription = "Lider",
                    tint = Color(0xFFFFB800),
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                  text = p.name,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = if (p.isLocalPlayer) FontWeight.ExtraBold else FontWeight.Medium,
                  color = if (p.isLocalPlayer) Color(0xFF0077B6) else Color(0xFF1E293B),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = String.format(Locale.US, "%.1f%%", p.percentage),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF334155)
              )
            }
          }
        }
      }
    }

    // 3. Kill Feed Pill & Touch Hint
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = 18.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      killFeed.forEach { entry ->
        Surface(
          shape = RoundedCornerShape(50),
          color = Color(0xDD0F172A)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(entry.accentColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = entry.message,
              style = MaterialTheme.typography.labelLarge,
              color = Color.White
            )
          }
        }
      }

      if (joystickOrigin == null && matchResult == null && (localPlayer?.percentage ?: 0f) < 1.2f) {
        Surface(
          shape = RoundedCornerShape(50),
          color = Color.White.copy(alpha = 0.85f),
          shadowElevation = 3.dp
        ) {
          Text(
            text = "Yön vermek için parmağını ekranda sürükle",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF334155),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
          )
        }
      }
    }

    // 4. Match Result Overlay (Game Over / Victory)
    AnimatedVisibility(
      visible = matchResult != null,
      enter = fadeIn() + scaleIn(initialScale = 0.9f),
      modifier = Modifier.align(Alignment.Center)
    ) {
      if (matchResult != null) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color(0x880F172A)),
          contentAlignment = Alignment.Center
        ) {
          Card(
            modifier = Modifier
              .padding(24.dp)
              .widthIn(max = 420.dp)
              .fillMaxWidth()
              .testTag("match_result_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(64.dp)
                  .clip(CircleShape)
                  .background(
                    if (matchResult.isVictory) Color(0xFFFFB800).copy(alpha = 0.2f)
                    else Color(0xFFFF5E62).copy(alpha = 0.16f)
                  ),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Rounded.EmojiEvents,
                  contentDescription = null,
                  tint = if (matchResult.isVictory) Color(0xFFD97706) else Color(0xFFFF5E62),
                  modifier = Modifier.size(36.dp)
                )
              }

              Text(
                text = if (matchResult.isVictory) "ARENA ŞAMPİYONU!" else "OYUN BİTTİ",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF0F172A)
              )

              if (matchResult.isNewBest) {
                Surface(
                  color = Color(0xFFDCFCE7),
                  shape = RoundedCornerShape(50)
                ) {
                  Text(
                    text = "YENİ KİŞİSEL REKOR!",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF15803D),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
              ) {
                ResultStatItem("ALAN", String.format(Locale.US, "%.1f%%", matchResult.finalPercentage))
                ResultStatItem("ELEME", "${matchResult.kills}")
                ResultStatItem("SIRA", "#${matchResult.rank}/${matchResult.totalPlayers}")
              }

              if (!matchResult.isVictory) {
                Text(
                  text = "Lider: ${matchResult.winnerName}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = Color(0xFF64748B)
                )
              }

              Spacer(modifier = Modifier.height(4.dp))

              Button(
                onClick = onReplay,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(54.dp)
                  .testTag("replay_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A8E8))
              ) {
                Icon(imageVector = Icons.Rounded.Replay, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = if (isMultiplayer) "Lobiye Dön & Yeni Maç" else "Tekrar Oyna",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
              }

              OutlinedButton(
                onClick = onExitToLobby,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(50.dp)
                  .testTag("back_to_lobby_button"),
                shape = RoundedCornerShape(16.dp)
              ) {
                Text("Ana Menüye Dön")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ResultStatItem(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = Color(0xFF64748B)
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.titleLarge,
      color = Color(0xFF0F172A)
    )
  }
}

@Suppress("DEPRECATION")
private fun triggerShortHaptic(context: Context) {
  runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      manager?.defaultVibrator?.vibrate(
        VibrationEffect.createOneShot(28L, VibrationEffect.DEFAULT_AMPLITUDE)
      )
    } else {
      val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.vibrate(VibrationEffect.createOneShot(28L, VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        vibrator?.vibrate(28L)
      }
    }
  }
}
