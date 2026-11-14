package com.example.game

import androidx.compose.ui.graphics.Color
import com.example.data.model.ArenaSkins
import com.example.data.model.RoomPlayer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

data class TrailPoint(val x: Float, val y: Float)

data class Particle(
  var x: Float,
  var y: Float,
  var vx: Float,
  var vy: Float,
  var color: Color,
  var radius: Float,
  var life: Float,
  var maxLife: Float
)

data class FloatingPopup(
  var x: Float,
  var y: Float,
  var text: String,
  var color: Color,
  var life: Float,
  var maxLife: Float = 1.1f
)

data class KillFeedEntry(
  val id: Long,
  val message: String,
  val accentColor: Color,
  val timestampMs: Long
)

data class ArenaPlayer(
  val slot: Int,
  val id: String,
  val name: String,
  val skinId: String,
  val baseColor: Color,
  val shadowColor: Color,
  val trailColor: Color,
  val isHuman: Boolean,
  val isLocalPlayer: Boolean,
  var x: Float,
  var y: Float,
  var angle: Float,
  var targetAngle: Float,
  var alive: Boolean = true,
  var kills: Int = 0,
  var percentage: Float = 0f,
  val trailPoints: MutableList<TrailPoint> = mutableListOf(),
  val trailCells: MutableList<Int> = mutableListOf(),
  var isOutside: Boolean = false,
  var remoteTargetX: Float = x,
  var remoteTargetY: Float = y,
  var remoteTargetAngle: Float = angle,
  var botDecisionTimer: Float = 0f,
  var botMaxTrailPoints: Int = 26,
  var botReturningHome: Boolean = false
)

sealed interface MatchEvent {
  data class Capture(val playerSlot: Int, val isLocal: Boolean, val gainedPercent: Float, val x: Float, val y: Float) : MatchEvent
  data class Elimination(val victimName: String, val victimUid: String, val killerName: String?, val killerIsLocal: Boolean, val victimIsLocal: Boolean, val color: Color) : MatchEvent
}

class PaperArenaEngine {
  companion object {
    const val GRID_SIZE = 90
    const val CELL_SIZE = 24f
    const val WORLD_SIZE = GRID_SIZE * CELL_SIZE // 2160f
    const val CENTER = WORLD_SIZE / 2f // 1080f
    const val ARENA_RADIUS_CELLS = 42
    const val ARENA_RADIUS_WORLD = ARENA_RADIUS_CELLS * CELL_SIZE // 1008f
    const val PLAYER_SPEED = 205f
    const val TURN_SPEED = 5.6f
    const val TRAIL_POINT_SPACING = 8f
    const val SELF_COLLISION_GRACE_POINTS = 16
  }

  // -2 = outside circle, -1 = neutral paper, 0..7 = player slot
  val grid = IntArray(GRID_SIZE * GRID_SIZE) { -2 }
  val totalArenaCells: Int

  // Reusable BFS arrays to avoid GC churn during gameplay
  private val visited = BooleanArray(GRID_SIZE * GRID_SIZE)
  private val bfsQueue = IntArray(GRID_SIZE * GRID_SIZE)
  private val edgeCells = mutableListOf<Int>()

  val players = mutableListOf<ArenaPlayer>()
  val particles = mutableListOf<Particle>()
  val popups = mutableListOf<FloatingPopup>()
  val killFeed = mutableListOf<KillFeedEntry>()

  var localSlot: Int = 0
    private set

  var pendingKillClaimUid: String? = null
    private set

  private var nextFeedId = 1L
  private val rng = Random(System.currentTimeMillis())

  private val botNames = listOf(
    "Kağıt Kral", "Gölge", "Turbo", "Piksel", "Fırtına", "KüpUsta", "Zümrüt", "Atmaca", "Buzul", "Kıvılcım"
  )
  private val botSkins = listOf(
    "coral_flame", "ninja_violet", "alien_lime", "royal_crown", "watermelon", "cyber_pink", "rocket_cobalt", "classic_cyan"
  )

  init {
    val cx = GRID_SIZE / 2
    val cy = GRID_SIZE / 2
    val rSq = ARENA_RADIUS_CELLS * ARENA_RADIUS_CELLS
    var count = 0
    for (gy in 0 until GRID_SIZE) {
      for (gx in 0 until GRID_SIZE) {
        val dx = gx - cx
        val dy = gy - cy
        if (dx * dx + dy * dy <= rSq) {
          grid[gy * GRID_SIZE + gx] = -1
          count++
        } else {
          grid[gy * GRID_SIZE + gx] = -2
        }
      }
    }
    totalArenaCells = count

    // Precompute perimeter cells inside circle (adjacent to -2 or grid boundary)
    for (gy in 0 until GRID_SIZE) {
      for (gx in 0 until GRID_SIZE) {
        val idx = gy * GRID_SIZE + gx
        if (grid[idx] == -1) {
          val isEdge = gx == 0 || gy == 0 || gx == GRID_SIZE - 1 || gy == GRID_SIZE - 1 ||
            grid[idx - 1] == -2 || grid[idx + 1] == -2 ||
            grid[idx - GRID_SIZE] == -2 || grid[idx + GRID_SIZE] == -2
          if (isEdge) edgeCells.add(idx)
        }
      }
    }
  }

  fun resetArena() {
    for (i in grid.indices) {
      if (grid[i] != -2) grid[i] = -1
    }
    players.clear()
    particles.clear()
    popups.clear()
    killFeed.clear()
    pendingKillClaimUid = null
  }

  fun setupSoloMatch(localUid: String, localName: String, localSkinId: String, botCount: Int = 7) {
    resetArena()
    val totalSlots = (1 + botCount).coerceIn(2, 8)
    localSlot = 0

    val spawnPoints = computeSpawnPoints(totalSlots)
    val localSkin = ArenaSkins.byId(localSkinId)
    val localSpawn = spawnPoints[0]
    val localPlayer = ArenaPlayer(
      slot = 0,
      id = localUid,
      name = localName.ifBlank { "Sen" },
      skinId = localSkinId,
      baseColor = localSkin.baseColor,
      shadowColor = localSkin.shadowColor,
      trailColor = localSkin.trailColor,
      isHuman = true,
      isLocalPlayer = true,
      x = localSpawn.first,
      y = localSpawn.second,
      angle = localSpawn.third,
      targetAngle = localSpawn.third
    )
    players.add(localPlayer)
    paintStartingBase(0, localSpawn.first, localSpawn.second)

    val shuffledNames = botNames.shuffled(rng)
    val shuffledSkins = botSkins.filter { it != localSkinId }.shuffled(rng)
    for (slot in 1 until totalSlots) {
      val sp = spawnPoints[slot]
      val palette = ArenaSkins.slotPalettes[slot % ArenaSkins.slotPalettes.size]
      val botSkin = shuffledSkins[(slot - 1) % shuffledSkins.size]
      val bot = ArenaPlayer(
        slot = slot,
        id = "bot_$slot",
        name = shuffledNames[(slot - 1) % shuffledNames.size],
        skinId = botSkin,
        baseColor = palette.first,
        shadowColor = palette.second,
        trailColor = palette.third,
        isHuman = false,
        isLocalPlayer = false,
        x = sp.first,
        y = sp.second,
        angle = sp.third,
        targetAngle = sp.third,
        botMaxTrailPoints = rng.nextInt(16, 36)
      )
      players.add(bot)
      paintStartingBase(slot, sp.first, sp.second)
    }

    recalculatePercentages()
  }

  fun setupMultiplayerMatch(
    localUid: String,
    localName: String,
    localSkinId: String,
    participants: List<Triple<String, String, String>>, // uid, name, skinId
    fillWithBots: Boolean,
    isHost: Boolean
  ): ArenaPlayer {
    resetArena()
    val ordered = participants.sortedBy { it.first }
    val humanCount = ordered.size.coerceIn(1, 8)
    val totalSlots = if (fillWithBots) max(humanCount, 6).coerceAtMost(8) else humanCount
    val spawnPoints = computeSpawnPoints(totalSlots)

    var localPlayerRef: ArenaPlayer? = null
    ordered.forEachIndexed { idx, (uid, name, skinId) ->
      val isLocal = uid == localUid
      if (isLocal) localSlot = idx
      val sp = spawnPoints[idx]
      val skin = ArenaSkins.byId(skinId)
      val palette = if (isLocal) {
        Triple(skin.baseColor, skin.shadowColor, skin.trailColor)
      } else {
        // Use distinct palette or skin colors if not colliding with local
        val p = ArenaSkins.slotPalettes[(idx + 1) % ArenaSkins.slotPalettes.size]
        if (skinId != localSkinId) Triple(skin.baseColor, skin.shadowColor, skin.trailColor) else p
      }
      val player = ArenaPlayer(
        slot = idx,
        id = uid,
        name = name.ifBlank { "Oyuncu ${idx + 1}" },
        skinId = skinId,
        baseColor = palette.first,
        shadowColor = palette.second,
        trailColor = palette.third,
        isHuman = true,
        isLocalPlayer = isLocal,
        x = sp.first,
        y = sp.second,
        angle = sp.third,
        targetAngle = sp.third
      )
      players.add(player)
      paintStartingBase(idx, sp.first, sp.second)
      if (isLocal) localPlayerRef = player
    }

    // Host simulates local AI bots if enabled so even 1-2 player private rooms stay exciting
    if (fillWithBots && isHost) {
      val shuffledNames = botNames.shuffled(rng)
      val shuffledSkins = botSkins.shuffled(rng)
      for (slot in humanCount until totalSlots) {
        val sp = spawnPoints[slot]
        val palette = ArenaSkins.slotPalettes[slot % ArenaSkins.slotPalettes.size]
        val bot = ArenaPlayer(
          slot = slot,
          id = "bot_$slot",
          name = shuffledNames[(slot - humanCount) % shuffledNames.size],
          skinId = shuffledSkins[(slot - humanCount) % shuffledSkins.size],
          baseColor = palette.first,
          shadowColor = palette.second,
          trailColor = palette.third,
          isHuman = false,
          isLocalPlayer = false,
          x = sp.first,
          y = sp.second,
          angle = sp.third,
          targetAngle = sp.third,
          botMaxTrailPoints = rng.nextInt(16, 34)
        )
        players.add(bot)
        paintStartingBase(slot, sp.first, sp.second)
      }
    }

    recalculatePercentages()
    return localPlayerRef ?: players.first()
  }

  private fun computeSpawnPoints(count: Int): List<Triple<Float, Float, Float>> {
    val ringRadius = ARENA_RADIUS_WORLD * 0.56f
    val list = mutableListOf<Triple<Float, Float, Float>>()
    for (i in 0 until count) {
      val theta = (2.0 * PI * i / count).toFloat()
      val sx = CENTER + cos(theta) * ringRadius
      val sy = CENTER + sin(theta) * ringRadius
      val facing = theta + PI.toFloat() // face inward toward arena center
      list.add(Triple(sx, sy, facing))
    }
    return list
  }

  private fun paintStartingBase(slot: Int, worldX: Float, worldY: Float) {
    val cx = worldToGrid(worldX)
    val cy = worldToGrid(worldY)
    val baseRadius = 3
    for (dy in -baseRadius..baseRadius) {
      for (dx in -baseRadius..baseRadius) {
        if (dx * dx + dy * dy <= baseRadius * baseRadius + 1) {
          val gx = cx + dx
          val gy = cy + dy
          if (gx in 0 until GRID_SIZE && gy in 0 until GRID_SIZE) {
            val idx = gy * GRID_SIZE + gx
            if (grid[idx] != -2) {
              grid[idx] = slot
            }
          }
        }
      }
    }
  }

  fun setLocalTargetAngle(angle: Float) {
    players.firstOrNull { it.isLocalPlayer }?.let {
      it.targetAngle = angle
    }
  }

  fun localPlayer(): ArenaPlayer? = players.firstOrNull { it.isLocalPlayer }

  fun leaderboard(): List<ArenaPlayer> =
    players.filter { it.alive }.sortedByDescending { it.percentage }

  fun leaderSlot(): Int? = leaderboard().firstOrNull()?.slot

  fun step(dtRaw: Float): List<MatchEvent> {
    val dt = dtRaw.coerceIn(0.001f, 0.05f)
    val events = mutableListOf<MatchEvent>()

    for (p in players) {
      if (!p.alive) continue

      if (!p.isHuman) {
        updateBotSteering(p, dt)
        advancePlayer(p, dt, events)
      } else if (p.isLocalPlayer) {
        advancePlayer(p, dt, events)
      } else {
        // Remote multiplayer player: smoothly interpolate toward latest synced position + heading
        val dx = p.remoteTargetX - p.x
        val dy = p.remoteTargetY - p.y
        val dist = hypot(dx, dy)
        if (dist > 140f) {
          p.x = p.remoteTargetX
          p.y = p.remoteTargetY
        } else {
          val blend = (dt * 12f).coerceIn(0f, 1f)
          p.x += dx * blend
          p.y += dy * blend
        }
        p.angle = lerpAngle(p.angle, p.remoteTargetAngle, (dt * 14f).coerceIn(0f, 1f))
      }
    }

    // Update particles & floating popups
    val pIter = particles.iterator()
    while (pIter.hasNext()) {
      val pt = pIter.next()
      pt.life -= dt
      if (pt.life <= 0f) {
        pIter.remove()
      } else {
        pt.x += pt.vx * dt
        pt.y += pt.vy * dt
        pt.vx *= 0.94f
        pt.vy *= 0.94f
      }
    }

    val popIter = popups.iterator()
    while (popIter.hasNext()) {
      val pop = popIter.next()
      pop.life -= dt
      if (pop.life <= 0f) {
        popIter.remove()
      } else {
        pop.y -= 38f * dt
      }
    }

    return events
  }

  private fun advancePlayer(p: ArenaPlayer, dt: Float, events: MutableList<MatchEvent>) {
    // Smooth angular turn
    val diff = normalizeAngle(p.targetAngle - p.angle)
    val maxTurn = TURN_SPEED * dt
    p.angle = if (kotlin.math.abs(diff) <= maxTurn) {
      p.targetAngle
    } else {
      normalizeAngle(p.angle + kotlin.math.sign(diff) * maxTurn)
    }

    val prevX = p.x
    val prevY = p.y
    val nextX = p.x + cos(p.angle) * PLAYER_SPEED * dt
    val nextY = p.y + sin(p.angle) * PLAYER_SPEED * dt

    // Check circular arena boundary
    val distFromCenter = hypot(nextX - CENTER, nextY - CENTER)
    if (distFromCenter >= ARENA_RADIUS_WORLD - 10f) {
      eliminatePlayer(p, killer = null, events = events)
      return
    }

    p.x = nextX
    p.y = nextY

    // Sub-step along movement to guarantee zero missed grid cells
    val steps = max(1, (hypot(nextX - prevX, nextY - prevY) / (CELL_SIZE * 0.45f)).roundToInt())
    for (s in 1..steps) {
      if (!p.alive) return
      val t = s.toFloat() / steps
      val sx = prevX + (nextX - prevX) * t
      val sy = prevY + (nextY - prevY) * t
      processCellAndCollisions(p, sx, sy, prevX, prevY, events)
    }
  }

  private fun processCellAndCollisions(
    p: ArenaPlayer,
    sx: Float,
    sy: Float,
    prevX: Float,
    prevY: Float,
    events: MutableList<MatchEvent>
  ) {
    val gx = worldToGrid(sx)
    val gy = worldToGrid(sy)
    if (gx !in 0 until GRID_SIZE || gy !in 0 until GRID_SIZE) return
    val cellIdx = gy * GRID_SIZE + gx
    val cellOwner = grid[cellIdx]
    if (cellOwner == -2) {
      eliminatePlayer(p, killer = null, events = events)
      return
    }

    // 1. Check collision with any other player's exposed trail
    for (other in players) {
      if (!other.alive || other.slot == p.slot) continue
      if (other.isOutside) {
        val hitTrail = other.trailPoints.any { tp -> hypot(sx - tp.x, sy - tp.y) < 15f }
        if (hitTrail) {
          if (p.isLocalPlayer && other.isHuman) {
            pendingKillClaimUid = other.id
          }
          eliminatePlayer(other, killer = p, events = events)
        }
      }
      // Head-on collision: if both outside or one inside safe territory
      if (other.alive && hypot(sx - other.x, sy - other.y) < 18f) {
        if (!p.isOutside && other.isOutside) {
          if (p.isLocalPlayer && other.isHuman) {
            pendingKillClaimUid = other.id
          }
          eliminatePlayer(other, killer = p, events = events)
        } else if (p.isOutside && !other.isOutside) {
          eliminatePlayer(p, killer = other, events = events)
          return
        }
      }
    }

    // 2. Self-trail collision (with grace buffer near head so smooth curves work)
    if (p.isOutside && p.trailPoints.size > SELF_COLLISION_GRACE_POINTS) {
      val checkLimit = p.trailPoints.size - SELF_COLLISION_GRACE_POINTS
      for (i in 0 until checkLimit) {
        val tp = p.trailPoints[i]
        if (hypot(sx - tp.x, sy - tp.y) < 10f) {
          eliminatePlayer(p, killer = null, events = events)
          return
        }
      }
    }

    // 3. Territory capture or trail extension
    if (cellOwner == p.slot) {
      if (p.isOutside && p.trailCells.isNotEmpty()) {
        val prevPercent = p.percentage
        captureTerritory(p)
        val gained = (p.percentage - prevPercent).coerceAtLeast(0.1f)
        events.add(MatchEvent.Capture(p.slot, p.isLocalPlayer, gained, p.x, p.y))
        if (p.isLocalPlayer) {
          popups.add(
            FloatingPopup(
              x = p.x,
              y = p.y - 24f,
              text = "+${String.format("%.1f", gained)}%",
              color = p.shadowColor,
              life = 1.1f
            )
          )
        }
      }
    } else {
      if (!p.isOutside) {
        p.isOutside = true
        p.trailPoints.add(TrailPoint(prevX, prevY))
      }
      val lastPt = p.trailPoints.lastOrNull()
      if (lastPt == null || hypot(sx - lastPt.x, sy - lastPt.y) >= TRAIL_POINT_SPACING) {
        p.trailPoints.add(TrailPoint(sx, sy))
      }
      if (p.trailCells.lastOrNull() != cellIdx) {
        p.trailCells.add(cellIdx)
      }
    }
  }

  private fun captureTerritory(p: ArenaPlayer) {
    // 1. Convert player's trail cells into owned territory
    for (idx in p.trailCells) {
      if (grid[idx] != -2) {
        grid[idx] = p.slot
      }
    }
    p.trailCells.clear()
    p.trailPoints.clear()
    p.isOutside = false

    // 2. Flood fill from circular arena perimeter to mark all exterior cells (not enclosed by p.slot)
    visited.fill(false)
    var qHead = 0
    var qTail = 0

    for (edgeIdx in edgeCells) {
      if (grid[edgeIdx] != p.slot && !visited[edgeIdx]) {
        visited[edgeIdx] = true
        bfsQueue[qTail++] = edgeIdx
      }
    }

    while (qHead < qTail) {
      val curr = bfsQueue[qHead++]
      val gx = curr % GRID_SIZE
      val gy = curr / GRID_SIZE

      if (gx > 0) {
        val n = curr - 1
        if (!visited[n] && grid[n] != -2 && grid[n] != p.slot) {
          visited[n] = true
          bfsQueue[qTail++] = n
        }
      }
      if (gx < GRID_SIZE - 1) {
        val n = curr + 1
        if (!visited[n] && grid[n] != -2 && grid[n] != p.slot) {
          visited[n] = true
          bfsQueue[qTail++] = n
        }
      }
      if (gy > 0) {
        val n = curr - GRID_SIZE
        if (!visited[n] && grid[n] != -2 && grid[n] != p.slot) {
          visited[n] = true
          bfsQueue[qTail++] = n
        }
      }
      if (gy < GRID_SIZE - 1) {
        val n = curr + GRID_SIZE
        if (!visited[n] && grid[n] != -2 && grid[n] != p.slot) {
          visited[n] = true
          bfsQueue[qTail++] = n
        }
      }
    }

    // 3. Fill all enclosed cells inside circle
    for (i in grid.indices) {
      if (grid[i] != -2 && !visited[i]) {
        grid[i] = p.slot
      }
    }

    // 4. Recalculate percentages & eliminate anyone whose entire base was swallowed
    recalculatePercentages()
    for (other in players) {
      if (other.alive && other.slot != p.slot && other.percentage <= 0.001f && !other.isHuman) {
        other.alive = false
        other.trailPoints.clear()
        other.trailCells.clear()
        p.kills++
      }
    }
  }

  private fun eliminatePlayer(
    victim: ArenaPlayer,
    killer: ArenaPlayer?,
    events: MutableList<MatchEvent>
  ) {
    if (!victim.alive) return
    victim.alive = false
    victim.isOutside = false
    victim.trailPoints.clear()
    victim.trailCells.clear()
    victim.percentage = 0f

    if (killer != null && killer.slot != victim.slot) {
      killer.kills++
    }

    // Clear victim territory back to neutral paper
    for (i in grid.indices) {
      if (grid[i] == victim.slot) {
        grid[i] = -1
      }
    }
    recalculatePercentages()

    // Spawn burst particles
    repeat(22) {
      val a = rng.nextFloat() * 2f * PI.toFloat()
      val spd = rng.nextFloat() * 220f + 60f
      particles.add(
        Particle(
          x = victim.x,
          y = victim.y,
          vx = cos(a) * spd,
          vy = sin(a) * spd,
          color = victim.baseColor,
          radius = rng.nextFloat() * 6f + 4f,
          life = 0.7f,
          maxLife = 0.7f
        )
      )
    }

    val msg = if (killer != null) {
      "${killer.name} → ${victim.name} elendi!"
    } else {
      "${victim.name} elendi!"
    }
    killFeed.add(
      0,
      KillFeedEntry(
        id = nextFeedId++,
        message = msg,
        accentColor = killer?.baseColor ?: victim.baseColor,
        timestampMs = System.currentTimeMillis()
      )
    )
    if (killFeed.size > 4) killFeed.removeAt(killFeed.lastIndex)

    events.add(
      MatchEvent.Elimination(
        victimName = victim.name,
        victimUid = victim.id,
        killerName = killer?.name,
        killerIsLocal = killer?.isLocalPlayer == true,
        victimIsLocal = victim.isLocalPlayer,
        color = victim.baseColor
      )
    )
  }

  private fun updateBotSteering(bot: ArenaPlayer, dt: Float) {
    bot.botDecisionTimer -= dt
    val distCenter = hypot(bot.x - CENTER, bot.y - CENTER)

    // Emergency wall avoidance
    if (distCenter > ARENA_RADIUS_WORLD - 110f) {
      bot.targetAngle = atan2(CENTER - bot.y, CENTER - bot.x)
      bot.botDecisionTimer = 0.35f
      return
    }

    // Check if enemy is hunting near bot while bot is exposed
    if (bot.isOutside) {
      val nearbyThreat = players.any { other ->
        other.alive && other.slot != bot.slot && hypot(other.x - bot.x, other.y - bot.y) < 175f
      }
      if (nearbyThreat || bot.trailPoints.size >= bot.botMaxTrailPoints) {
        bot.botReturningHome = true
      }
    } else {
      bot.botReturningHome = false
    }

    if (bot.botReturningHome && bot.isOutside) {
      val homePt = findNearestOwnedCellWorld(bot.slot, bot.x, bot.y)
      if (homePt != null) {
        bot.targetAngle = atan2(homePt.second - bot.y, homePt.first - bot.x)
        return
      }
    }

    if (bot.botDecisionTimer <= 0f) {
      bot.botDecisionTimer = rng.nextFloat() * 0.45f + 0.25f

      // Look for an exposed nearby trail to cut!
      val targetEnemy = players
        .filter { it.alive && it.slot != bot.slot && it.isOutside && it.trailPoints.isNotEmpty() }
        .minByOrNull { hypot(it.x - bot.x, it.y - bot.y) }

      if (targetEnemy != null && hypot(targetEnemy.x - bot.x, targetEnemy.y - bot.y) < 260f && rng.nextFloat() < 0.55f) {
        val midTrail = targetEnemy.trailPoints[targetEnemy.trailPoints.size / 2]
        bot.targetAngle = atan2(midTrail.y - bot.y, midTrail.x - bot.x)
      } else {
        // Gentle wandering turn
        val turn = (rng.nextFloat() - 0.5f) * 1.9f
        bot.targetAngle = normalizeAngle(bot.angle + turn)
      }
    }
  }

  private fun findNearestOwnedCellWorld(slot: Int, fromX: Float, fromY: Float): Pair<Float, Float>? {
    val gx0 = worldToGrid(fromX)
    val gy0 = worldToGrid(fromY)
    var bestDist = Float.MAX_VALUE
    var bestX = CENTER
    var bestY = CENTER
    var found = false

    val minY = (gy0 - 24).coerceAtLeast(0)
    val maxY = (gy0 + 24).coerceAtMost(GRID_SIZE - 1)
    val minX = (gx0 - 24).coerceAtLeast(0)
    val maxX = (gx0 + 24).coerceAtMost(GRID_SIZE - 1)

    for (gy in minY..maxY step 2) {
      for (gx in minX..maxX step 2) {
        if (grid[gy * GRID_SIZE + gx] == slot) {
          val wx = gx * CELL_SIZE + CELL_SIZE * 0.5f
          val wy = gy * CELL_SIZE + CELL_SIZE * 0.5f
          val d = hypot(wx - fromX, wy - fromY)
          if (d < bestDist) {
            bestDist = d
            bestX = wx
            bestY = wy
            found = true
          }
        }
      }
    }
    return if (found) Pair(bestX, bestY) else null
  }

  fun recalculatePercentages() {
    val counts = IntArray(8)
    for (v in grid) {
      if (v in 0..7) counts[v]++
    }
    for (p in players) {
      if (p.slot in 0..7) {
        p.percentage = if (p.alive) {
          (counts[p.slot] * 100f / totalArenaCells).coerceIn(0f, 100f)
        } else {
          0f
        }
      }
    }
  }

  // --- Multiplayer Serialization Helpers ---

  fun packLocalTrail(p: ArenaPlayer): String {
    if (p.trailPoints.isEmpty()) return ""
    // Downsample to at most 180 points for fast Firestore sync
    val step = max(1, p.trailPoints.size / 180)
    return buildString {
      for (i in p.trailPoints.indices step step) {
        if (isNotEmpty()) append(';')
        append(p.trailPoints[i].x.roundToInt())
        append(',')
        append(p.trailPoints[i].y.roundToInt())
      }
    }
  }

  fun packTerritoryForSlot(slot: Int): String {
    val sb = StringBuilder()
    var i = 0
    while (i < grid.size) {
      if (grid[i] == slot) {
        val start = i
        while (i < grid.size && grid[i] == slot) i++
        val len = i - start
        if (sb.isNotEmpty()) sb.append(',')
        sb.append(start).append(':').append(len)
      } else {
        i++
      }
    }
    return sb.toString()
  }

  fun applyRemotePlayers(remoteList: List<RoomPlayer>, localUid: String): List<MatchEvent> {
    val events = mutableListOf<MatchEvent>()
    val local = localPlayer()

    for (rp in remoteList) {
      if (rp.userId == localUid) continue
      val target = players.firstOrNull { it.id == rp.userId } ?: continue

      // Check if remote player claims to have cut our trail
      if (rp.claimedKillUid == localUid && local != null && local.alive) {
        eliminatePlayer(local, killer = target, events = events)
      }

      if (!rp.alive) {
        if (target.alive) {
          eliminatePlayer(target, killer = null, events = events)
        }
        continue
      }

      target.remoteTargetX = rp.x.toFloat()
      target.remoteTargetY = rp.y.toFloat()
      target.remoteTargetAngle = rp.angle.toFloat()
      target.kills = rp.kills.toInt()
      target.percentage = rp.percentage.toFloat()

      // Unpack trail
      target.trailPoints.clear()
      if (rp.trailPacked.isNotBlank()) {
        val pts = rp.trailPacked.split(';')
        for (pt in pts) {
          val comma = pt.indexOf(',')
          if (comma > 0) {
            val px = pt.substring(0, comma).toFloatOrNull()
            val py = pt.substring(comma + 1).toFloatOrNull()
            if (px != null && py != null) {
              target.trailPoints.add(TrailPoint(px, py))
            }
          }
        }
        target.isOutside = target.trailPoints.isNotEmpty()
      } else {
        target.isOutside = false
      }

      // Unpack territory RLE onto grid (clear old cells of target.slot, paint new ones)
      if (rp.territoryPacked.isNotBlank()) {
        for (idx in grid.indices) {
          if (grid[idx] == target.slot) grid[idx] = -1
        }
        val runs = rp.territoryPacked.split(',')
        for (run in runs) {
          val colon = run.indexOf(':')
          if (colon > 0) {
            val start = run.substring(0, colon).toIntOrNull() ?: continue
            val len = run.substring(colon + 1).toIntOrNull() ?: continue
            val end = (start + len).coerceAtMost(grid.size)
            for (idx in start.coerceAtLeast(0) until end) {
              if (grid[idx] != -2 && grid[idx] != localSlot) {
                grid[idx] = target.slot
              }
            }
          }
        }
      }
    }

    return events
  }

  private fun worldToGrid(w: Float): Int =
    (w / CELL_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)

  private fun normalizeAngle(a: Float): Float {
    var r = a
    val twoPi = (2.0 * PI).toFloat()
    while (r > PI) r -= twoPi
    while (r < -PI) r += twoPi
    return r
  }

  private fun lerpAngle(a: Float, b: Float, t: Float): Float {
    val diff = normalizeAngle(b - a)
    return normalizeAngle(a + diff * t)
  }
}
