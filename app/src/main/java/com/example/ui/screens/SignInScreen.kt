package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.onGoogleSignInClicked
import com.example.data.model.ArenaSkins
import com.example.game.drawSkinPreview

@Composable
fun SignInScreen(
  onAuthSuccess: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  val infiniteTransition = rememberInfiniteTransition(label = "crownBob")
  val bobOffset by infiniteTransition.animateFloat(
    initialValue = -4f,
    targetValue = 4f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bob"
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFFF0F6FF),
            Color(0xFFE6F1FF),
            Color(0xFFFFFFFF)
          )
        )
      )
      .statusBarsPadding()
      .navigationBarsPadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp)
        .widthIn(max = 520.dp)
        .align(Alignment.Center),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      // Hero Banner Card
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_arena_banner),
            contentDescription = "Paper Arena oyun görseli",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
          )
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(
                Brush.verticalGradient(
                  colors = listOf(Color.Transparent, Color(0x880F172A))
                )
              )
          )
          Row(
            modifier = Modifier
              .align(Alignment.BottomStart)
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.92f)),
              contentAlignment = Alignment.Center
            ) {
              Canvas(modifier = Modifier.size(40.dp)) {
                drawSkinPreview(ArenaSkins.byId("royal_crown"))
              }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "PAPER ARENA",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
              )
              Text(
                text = "360° Bölge Fethet • Çevrimiçi Rekabet",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      Column(
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "Haritanın %100'ünü Fethet!",
          style = MaterialTheme.typography.headlineMedium,
          color = Color(0xFF0F172A),
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Kendi alanından çık, şerit izini çiz ve döngüyü kapatarak rakip toprakları ele geçir. Arkadaşlarınla özel odalarda veya rastgele eşleşmede yarış!",
          style = MaterialTheme.typography.bodyLarge,
          color = Color(0xFF475569),
          textAlign = TextAlign.Center
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Feature Highlights
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        FeatureHighlightPill(
          icon = Icons.Rounded.Public,
          accent = Color(0xFF00A8E8),
          title = "Rastgele Çevrimiçi Eşleşme",
          subtitle = "Anında gerçek oyuncularla dairesel arenaya bağlan"
        )
        FeatureHighlightPill(
          icon = Icons.Rounded.Groups,
          accent = Color(0xFF7C4DFF),
          title = "Özel Oyun Odaları",
          subtitle = "6 haneli oda kodu oluştur veya arkadaşının odasına katıl"
        )
        FeatureHighlightPill(
          icon = Icons.Rounded.EmojiEvents,
          accent = Color(0xFFFFB800),
          title = "Taç ve Skor Tablosu",
          subtitle = "Lider tacını kap, rekorlarını bulutta güvenle sakla"
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      if (errorMsg != null) {
        Surface(
          color = Color(0xFFFFEBEE),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
        ) {
          Text(
            text = errorMsg!!,
            color = Color(0xFFC62828),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
            textAlign = TextAlign.Center
          )
        }
      }

      Button(
        onClick = {
          errorMsg = null
          isLoading = true
          onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
              isLoading = false
              onAuthSuccess()
            },
            onAuthError = { msg ->
              isLoading = false
              errorMsg = msg
            },
            scope = scope,
            onAuthCancelled = { isLoading = false }
          )
        },
        enabled = !isLoading,
        modifier = Modifier
          .fillMaxWidth()
          .height(58.dp)
          .testTag("google_sign_in_button"),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xFF00A8E8),
          contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
      ) {
        if (isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = Color.White,
            strokeWidth = 2.5.dp
          )
        } else {
          Text(
            text = "Google ile Giriş Yap ve Oyna",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun FeatureHighlightPill(
  icon: ImageVector,
  accent: Color,
  title: String,
  subtitle: String
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = Color.White,
    shadowElevation = 2.dp
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accent,
          modifier = Modifier.size(22.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = Color(0xFF0F172A)
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = Color(0xFF64748B)
        )
      }
    }
  }
}
