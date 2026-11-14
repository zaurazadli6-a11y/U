package com.example.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.data.model.ArenaSkin
import com.example.data.model.SkinPattern
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun DrawScope.drawPaperArena(
  engine: PaperArenaEngine,
  camX: Float,
  camY: Float,
  zoom: Float,
  pulsePhase: Float
) {
  val viewW = size.width
  val viewH = size.height
  val halfW = viewW / 2f
  val halfH = viewH / 2f

  fun toScreenX(wx: Float): Float = (wx - camX) * zoom + halfW
  fun toScreenY(wy: Float): Float = (wy - camY) * zoom + halfH

  val centerScreen = Offset(toScreenX(PaperArenaEngine.CENTER), toScreenY(PaperArenaEngine.CENTER))
  val radiusScreen = PaperArenaEngine.ARENA_RADIUS_WORLD * zoom

  // 1. Exterior void background (soft slate outside circular arena)
  drawRect(color = Color(0xFFE8EDF5))

  // 2. Arena hazard ring & crisp paper floor
  drawCircle(
    color = Color(0xFFFF4D5A),
    radius = radiusScreen + 14f * zoom,
    center = centerScreen
  )
  drawCircle(
    color = Color(0xFFFDFEFF),
    radius = radiusScreen,
    center = centerScreen
  )

  // 3. Paper grid lines inside visible viewport
  val cellScreen = PaperArenaEngine.CELL_SIZE * zoom
  val stepWorld = PaperArenaEngine.CELL_SIZE * 2f
  val worldLeft = camX - halfW / zoom
  val worldRight = camX + halfW / zoom
  val worldTop = camY - halfH / zoom
  val worldBottom = camY + halfH / zoom

  val startGx = ((worldLeft / stepWorld).toInt() - 1) * stepWorld
  val startGy = ((worldTop / stepWorld).toInt() - 1) * stepWorld
  var wx = startGx
  while (wx <= worldRight + stepWorld) {
    val sx = toScreenX(wx)
    drawLine(
      color = Color(0xFFEAEFF7),
      start = Offset(sx, 0f),
      end = Offset(sx, viewH),
      strokeWidth = (1.2f * zoom).coerceAtLeast(1f)
    )
    wx += stepWorld
  }
  var wy = startGy
  while (wy <= worldBottom + stepWorld) {
    val sy = toScreenY(wy)
    drawLine(
      color = Color(0xFFEAEFF7),
      start = Offset(0f, sy),
      end = Offset(viewW, sy),
      strokeWidth = (1.2f * zoom).coerceAtLeast(1f)
    )
    wy += stepWorld
  }

  // 4. Re-draw crisp outer arena mask ring so grid lines stay neatly inside the circle
  drawCircle(
    color = Color(0xFFE8EDF5),
    radius = radiusScreen + 240f * zoom,
    center = centerScreen,
    style = Stroke(width = 450f * zoom)
  )
  drawCircle(
    color = Color(0xFFFF4D5A),
    radius = radiusScreen + 6f * zoom,
    center = centerScreen,
    style = Stroke(width = 12f * zoom)
  )

  // 5. Visible grid bounds for fast territory rendering
  val minGx = ((worldLeft / PaperArenaEngine.CELL_SIZE).toInt() - 1).coerceIn(0, PaperArenaEngine.GRID_SIZE - 1)
  val maxGx = ((worldRight / PaperArenaEngine.CELL_SIZE).toInt() + 1).coerceIn(0, PaperArenaEngine.GRID_SIZE - 1)
  val minGy = ((worldTop / PaperArenaEngine.CELL_SIZE).toInt() - 1).coerceIn(0, PaperArenaEngine.GRID_SIZE - 1)
  val maxGy = ((worldBottom / PaperArenaEngine.CELL_SIZE).toInt() + 1).coerceIn(0, PaperArenaEngine.GRID_SIZE - 1)

  val playersBySlot = engine.players.associateBy { it.slot }
  val depthOffset = 6.5f * zoom
  val overlap = 0.8f

  // Pass A: 3D bottom extruded paper shadow lip (Paper.io 2 signature depth!)
  for (gy in minGy..maxGy) {
    var gx = minGx
    while (gx <= maxGx) {
      val owner = engine.grid[gy * PaperArenaEngine.GRID_SIZE + gx]
      if (owner >= 0) {
        val runStart = gx
        while (gx <= maxGx && engine.grid[gy * PaperArenaEngine.GRID_SIZE + gx] == owner) {
          gx++
        }
        val p = playersBySlot[owner]
        if (p != null) {
          val belowIdx = if (gy + 1 < PaperArenaEngine.GRID_SIZE) (gy + 1) * PaperArenaEngine.GRID_SIZE + runStart else -1
          val sx = toScreenX(runStart * PaperArenaEngine.CELL_SIZE) - overlap
          val sy = toScreenY(gy * PaperArenaEngine.CELL_SIZE) + depthOffset
          val rw = (gx - runStart) * cellScreen + overlap * 2f
          val rh = cellScreen + overlap
          drawRect(
            color = p.shadowColor,
            topLeft = Offset(sx, sy),
            size = Size(rw, rh)
          )
        }
      } else {
        gx++
      }
    }
  }

  // Pass B: Top bright territory surface (run-merged horizontally for blazing 60fps performance)
  for (gy in minGy..maxGy) {
    var gx = minGx
    while (gx <= maxGx) {
      val owner = engine.grid[gy * PaperArenaEngine.GRID_SIZE + gx]
      if (owner >= 0) {
        val runStart = gx
        while (gx <= maxGx && engine.grid[gy * PaperArenaEngine.GRID_SIZE + gx] == owner) {
          gx++
        }
        val p = playersBySlot[owner]
        if (p != null) {
          val sx = toScreenX(runStart * PaperArenaEngine.CELL_SIZE) - overlap
          val sy = toScreenY(gy * PaperArenaEngine.CELL_SIZE) - overlap
          val rw = (gx - runStart) * cellScreen + overlap * 2f
          val rh = cellScreen + overlap * 2f
          drawRect(
            color = p.baseColor,
            topLeft = Offset(sx, sy),
            size = Size(rw, rh)
          )
        }
      } else {
        gx++
      }
    }
  }

  // 6. Exposed ribbon trails
  for (p in engine.players) {
    if (!p.alive || p.trailPoints.isEmpty()) continue
    val path = Path()
    val first = p.trailPoints.first()
    path.moveTo(toScreenX(first.x), toScreenY(first.y))
    for (i in 1 until p.trailPoints.size) {
      val pt = p.trailPoints[i]
      path.lineTo(toScreenX(pt.x), toScreenY(pt.y))
    }
    path.lineTo(toScreenX(p.x), toScreenY(p.y))

    drawPath(
      path = path,
      color = p.trailColor,
      style = Stroke(
        width = 18f * zoom,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
      )
    )
  }

  // 7. Particles
  for (pt in engine.particles) {
    val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
    drawCircle(
      color = pt.color.copy(alpha = alpha),
      radius = pt.radius * zoom,
      center = Offset(toScreenX(pt.x), toScreenY(pt.y))
    )
  }

  // 8. Players (3D extruded square cube + custom skin details + eyes + golden leader crown!)
  val leaderSlot = engine.leaderSlot()
  for (p in engine.players) {
    if (!p.alive) continue
    val sx = toScreenX(p.x)
    val sy = toScreenY(p.y)
    val cubeSize = 30f * zoom
    val deg = (p.angle * 180f / PI.toFloat())
    drawPlayerCube(
      center = Offset(sx, sy),
      cubeSize = cubeSize,
      angleDeg = deg,
      baseColor = p.baseColor,
      shadowColor = p.shadowColor,
      skinPattern = com.example.data.model.ArenaSkins.byId(p.skinId).pattern,
      hasCrown = (p.slot == leaderSlot),
      pulsePhase = pulsePhase
    )
  }
}

fun DrawScope.drawPlayerCube(
  center: Offset,
  cubeSize: Float,
  angleDeg: Float,
  baseColor: Color,
  shadowColor: Color,
  skinPattern: SkinPattern,
  hasCrown: Boolean,
  pulsePhase: Float = 0f
) {
  val half = cubeSize / 2f
  val radius = CornerRadius(cubeSize * 0.24f, cubeSize * 0.24f)

  // Drop shadow & 3D extrusion under cube
  drawRoundRect(
    color = Color(0x330F172A),
    topLeft = Offset(center.x - half, center.y - half + cubeSize * 0.22f),
    size = Size(cubeSize, cubeSize),
    cornerRadius = radius
  )

  rotate(degrees = angleDeg, pivot = center) {
    // 3D base
    drawRoundRect(
      color = shadowColor,
      topLeft = Offset(center.x - half, center.y - half + cubeSize * 0.12f),
      size = Size(cubeSize, cubeSize),
      cornerRadius = radius
    )
    // Top glossy cube face
    drawRoundRect(
      color = baseColor,
      topLeft = Offset(center.x - half, center.y - half),
      size = Size(cubeSize, cubeSize),
      cornerRadius = radius
    )
    // Specular highlight
    drawRoundRect(
      color = Color.White.copy(alpha = 0.25f),
      topLeft = Offset(center.x - half * 0.8f, center.y - half * 0.8f),
      size = Size(cubeSize * 0.8f, cubeSize * 0.26f),
      cornerRadius = CornerRadius(cubeSize * 0.12f, cubeSize * 0.12f)
    )

    // Pattern overlays
    when (skinPattern) {
      SkinPattern.NINJA -> {
        drawRect(
          color = Color(0xFF1E1B4B),
          topLeft = Offset(center.x - half, center.y - cubeSize * 0.16f),
          size = Size(cubeSize, cubeSize * 0.32f)
        )
      }
      SkinPattern.WATERMELON -> {
        drawRoundRect(
          color = Color(0xFFFF4D6D),
          topLeft = Offset(center.x - half * 0.72f, center.y - half * 0.72f),
          size = Size(cubeSize * 0.72f, cubeSize * 0.72f),
          cornerRadius = CornerRadius(cubeSize * 0.16f, cubeSize * 0.16f)
        )
      }
      SkinPattern.CYBER -> {
        drawRoundRect(
          color = Color(0xFF0F172A),
          topLeft = Offset(center.x + cubeSize * 0.04f, center.y - cubeSize * 0.28f),
          size = Size(cubeSize * 0.34f, cubeSize * 0.56f),
          cornerRadius = CornerRadius(cubeSize * 0.1f, cubeSize * 0.1f)
        )
      }
      SkinPattern.ROCKET -> {
        drawCircle(
          color = Color(0xFFFEF08A),
          radius = cubeSize * 0.15f,
          center = Offset(center.x - cubeSize * 0.18f, center.y)
        )
      }
      else -> Unit
    }

    // Expressive Paper.io style eyes looking forward (+X in rotated frame)
    if (skinPattern != SkinPattern.CYBER) {
      val eyeX = center.x + cubeSize * 0.2f
      val eyeOffsetY = cubeSize * 0.17f
      drawCircle(color = Color(0xFF0F172A), radius = cubeSize * 0.085f, center = Offset(eyeX, center.y - eyeOffsetY))
      drawCircle(color = Color(0xFF0F172A), radius = cubeSize * 0.085f, center = Offset(eyeX, center.y + eyeOffsetY))
      drawCircle(color = Color.White, radius = cubeSize * 0.03f, center = Offset(eyeX + cubeSize * 0.03f, center.y - eyeOffsetY - cubeSize * 0.02f))
      drawCircle(color = Color.White, radius = cubeSize * 0.03f, center = Offset(eyeX + cubeSize * 0.03f, center.y + eyeOffsetY - cubeSize * 0.02f))
    }

    if (skinPattern == SkinPattern.ALIEN) {
      drawCircle(color = Color(0xFF0F172A), radius = cubeSize * 0.075f, center = Offset(center.x + cubeSize * 0.04f, center.y))
    }
  }

  // Golden Crown floating above the #1 leader (or Royal skin)
  if (hasCrown || skinPattern == SkinPattern.CROWN) {
    val bob = sin(pulsePhase * 4f) * (cubeSize * 0.06f)
    val crownY = center.y - half - cubeSize * 0.22f + bob
    val cw = cubeSize * 0.62f
    val ch = cubeSize * 0.38f
    val crownPath = Path().apply {
      moveTo(center.x - cw / 2f, crownY + ch / 2f)
      lineTo(center.x - cw / 2f, crownY - ch * 0.2f)
      lineTo(center.x - cw * 0.22f, crownY + ch * 0.05f)
      lineTo(center.x, crownY - ch * 0.5f)
      lineTo(center.x + cw * 0.22f, crownY + ch * 0.05f)
      lineTo(center.x + cw / 2f, crownY - ch * 0.2f)
      lineTo(center.x + cw / 2f, crownY + ch / 2f)
      close()
    }
    drawPath(path = crownPath, color = Color(0xFFFFD600))
    drawPath(
      path = crownPath,
      color = Color(0xFFB45309),
      style = Stroke(width = (cubeSize * 0.06f).coerceAtLeast(1.5f), join = StrokeJoin.Round)
    )
  }
}

fun DrawScope.drawSkinPreview(skin: ArenaSkin) {
  val cube = size.minDimension * 0.58f
  drawPlayerCube(
    center = Offset(size.width / 2f, size.height / 2f + size.minDimension * 0.05f),
    cubeSize = cube,
    angleDeg = -20f,
    baseColor = skin.baseColor,
    shadowColor = skin.shadowColor,
    skinPattern = skin.pattern,
    hasCrown = skin.pattern == SkinPattern.CROWN
  )
}
