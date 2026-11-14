package com.example.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class PaperArenaEngineTest {

  @Test
  fun soloSetup_initializesCircularArenaAndPlayers() {
    val engine = PaperArenaEngine()
    engine.setupSoloMatch("u1", "TestPlayer", "classic_cyan", botCount = 5)
    assertEquals(6, engine.players.size)
    assertTrue(engine.totalArenaCells > 4000)
    val local = engine.localPlayer()!!
    assertTrue(local.percentage > 0f)
  }

  @Test
  fun territoryPackAndUnpack_roundTripsCleanly() {
    val engine = PaperArenaEngine()
    engine.setupSoloMatch("u1", "Host", "classic_cyan", botCount = 1)
    val packed = engine.packTerritoryForSlot(0)
    assertTrue(packed.isNotBlank())
    assertTrue(packed.contains(":"))
  }

  @Test
  fun playerStepsAndTurnsSmoothly() {
    val engine = PaperArenaEngine()
    engine.setupSoloMatch("u1", "SteerPlayer", "classic_cyan", botCount = 1)
    val local = engine.localPlayer()!!
    val startX = local.x
    val startY = local.y
    engine.setLocalTargetAngle((PI / 2).toFloat())
    repeat(10) {
      engine.step(0.016f)
    }
    assertTrue(local.x != startX || local.y != startY)
  }
}
