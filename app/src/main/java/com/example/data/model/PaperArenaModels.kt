package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SkinPattern {
  SOLID,
  CROWN,
  NINJA,
  WATERMELON,
  ALIEN,
  CYBER,
  PIZZA,
  ROCKET
}

data class ArenaSkin(
  val id: String,
  val name: String,
  val baseColor: Color,
  val shadowColor: Color,
  val trailColor: Color,
  val pattern: SkinPattern,
  val subtitle: String
)

object ArenaSkins {
  val all: List<ArenaSkin> = listOf(
    ArenaSkin(
      id = "classic_cyan",
      name = "Buz Küpü",
      baseColor = Color(0xFF00D2FF),
      shadowColor = Color(0xFF0099CC),
      trailColor = Color(0xAA4BE3FF),
      pattern = SkinPattern.SOLID,
      subtitle = "Klasik parlak turkuaz"
    ),
    ArenaSkin(
      id = "coral_flame",
      name = "Mercan Alevi",
      baseColor = Color(0xFFFF5E62),
      shadowColor = Color(0xFFC93B40),
      trailColor = Color(0xAAFF8A8D),
      pattern = SkinPattern.SOLID,
      subtitle = "Canlı mercan kırmızısı"
    ),
    ArenaSkin(
      id = "royal_crown",
      name = "Kraliyet",
      baseColor = Color(0xFFFFB800),
      shadowColor = Color(0xFFCC8E00),
      trailColor = Color(0xAAFFD35C),
      pattern = SkinPattern.CROWN,
      subtitle = "Altın taçlı şampiyon"
    ),
    ArenaSkin(
      id = "ninja_violet",
      name = "Gölge Ninja",
      baseColor = Color(0xFF7C4DFF),
      shadowColor = Color(0xFF5328C7),
      trailColor = Color(0xAAA485FF),
      pattern = SkinPattern.NINJA,
      subtitle = "Kafa bantlı gizli usta"
    ),
    ArenaSkin(
      id = "watermelon",
      name = "Karpuz",
      baseColor = Color(0xFF2ECC71),
      shadowColor = Color(0xFF1E9B53),
      trailColor = Color(0xAAFF5B6E),
      pattern = SkinPattern.WATERMELON,
      subtitle = "Tatlı yaz dilimi"
    ),
    ArenaSkin(
      id = "alien_lime",
      name = "Uzaylı",
      baseColor = Color(0xFF8BE000),
      shadowColor = Color(0xFF62A300),
      trailColor = Color(0xAAB3F24C),
      pattern = SkinPattern.ALIEN,
      subtitle = "Üç gözlü ziyaretçi"
    ),
    ArenaSkin(
      id = "cyber_pink",
      name = "Siber Neon",
      baseColor = Color(0xFFFF2E93),
      shadowColor = Color(0xFFB81465),
      trailColor = Color(0xAAFF75B8),
      pattern = SkinPattern.CYBER,
      subtitle = "Vizörlü fütürist küp"
    ),
    ArenaSkin(
      id = "rocket_cobalt",
      name = "Roket",
      baseColor = Color(0xFF3B82F6),
      shadowColor = Color(0xFF1D58B8),
      trailColor = Color(0xAA78ADFF),
      pattern = SkinPattern.ROCKET,
      subtitle = "Hızlı kobalt pilot"
    )
  )

  fun byId(id: String): ArenaSkin = all.firstOrNull { it.id == id } ?: all.first()

  // Distinct palette for bots & multiplayer slots so territories never clash visually
  val slotPalettes: List<Triple<Color, Color, Color>> = listOf(
    Triple(Color(0xFF00D2FF), Color(0xFF0099CC), Color(0xAA4BE3FF)), // Cyan
    Triple(Color(0xFFFF5E62), Color(0xFFC93B40), Color(0xAAFF8A8D)), // Coral
    Triple(Color(0xFF8BE000), Color(0xFF62A300), Color(0xAAB3F24C)), // Lime
    Triple(Color(0xFF7C4DFF), Color(0xFF5328C7), Color(0xAAA485FF)), // Violet
    Triple(Color(0xFFFFB800), Color(0xFFCC8E00), Color(0xAAFFD35C)), // Gold
    Triple(Color(0xFFFF2E93), Color(0xFFB81465), Color(0xAAFF75B8)), // Magenta
    Triple(Color(0xFF3B82F6), Color(0xFF1D58B8), Color(0xAA78ADFF)), // Cobalt
    Triple(Color(0xFFFF7A00), Color(0xFFCC5800), Color(0xAAFFA64D))  // Tangerine
  )
}

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "Oyuncu",
  val selectedSkinId: String = "classic_cyan",
  val bestPercentage: Double = 0.0,
  val totalKills: Long = 0L,
  val wins: Long = 0L,
  val matchesPlayed: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GameRoom(
  @DocumentId val id: String = "",
  val hostId: String = "",
  val roomCode: String = "",
  val isPrivate: Boolean = false,
  val status: String = "waiting",
  val maxPlayers: Long = 6L,
  val fillWithBots: Boolean = true,
  val playerUids: List<String> = emptyList(),
  val playerProfiles: List<String> = emptyList(),
  val winnerUid: String? = null,
  val winnerName: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  data class ParticipantSummary(
    val uid: String,
    val displayName: String,
    val skinId: String
  )

  fun parsedParticipants(): List<ParticipantSummary> {
    return playerProfiles.mapNotNull { packed ->
      val parts = packed.split("|")
      if (parts.size >= 3) {
        ParticipantSummary(uid = parts[0], displayName = parts[1], skinId = parts[2])
      } else {
        null
      }
    }
  }
}

@IgnoreExtraProperties
data class RoomPlayer(
  @DocumentId val id: String = "",
  val userId: String = "",
  val roomId: String = "",
  val displayName: String = "Oyuncu",
  val skinId: String = "classic_cyan",
  val colorIndex: Long = 0L,
  val memberUids: List<String> = emptyList(),
  val x: Double = 0.0,
  val y: Double = 0.0,
  val angle: Double = 0.0,
  val percentage: Double = 0.0,
  val kills: Long = 0L,
  val alive: Boolean = true,
  val trailPacked: String = "",
  val territoryPacked: String = "",
  val claimedKillUid: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)
