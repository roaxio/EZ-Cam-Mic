package io.github.roaxio.ezcammic

import io.github.roaxio.ezcammic.camera.FpsSupport
import io.github.roaxio.ezcammic.server.StaticFonts
import org.junit.Assert.*
import org.junit.Test

class FpsSupportTest {
  // A typical rear camera, including a 60 fps range.
  private val typical = listOf(15..15, 7..30, 15..30, 20..20, 24..24, 30..30, 60..60)

  @Test
  fun options_areDistinctUpperBounds_cappedByResolution() {
    assertEquals(listOf(15, 20, 24, 30, 60), FpsSupport.optionsFor(typical, 60))
    assertEquals(listOf(15, 20, 24, 30), FpsSupport.optionsFor(typical, 30))
    assertEquals(listOf(15, 20, 24), FpsSupport.optionsFor(typical, 25))
    assertEquals(listOf(15, 20, 24, 30, 60), FpsSupport.optionsFor(typical, null))
    assertEquals(listOf(15, 30), FpsSupport.optionsFor(listOf(30..30, 15..15, 7..30), 30))
  }

  @Test
  fun options_noRanges_fallback_andUnreachable() {
    assertEquals(listOf(15, 24, 30), FpsSupport.optionsFor(emptyList(), 30))
    assertEquals(listOf(15, 24, 30), FpsSupport.optionsFor(listOf(0..0, 30..20), 30))
    // Old HALs that report x1000 values are ignored.
    assertEquals(listOf(15, 24, 30), FpsSupport.optionsFor(listOf(15000..30000), 30))
    // Nothing reachable at this size: keep the lowest upper bound.
    assertEquals(listOf(15), FpsSupport.optionsFor(typical, 10))
  }

  @Test
  fun range_isUpperBoundWithLowestLowerBound() {
    assertEquals(7..30, FpsSupport.rangeFor(typical, 30))
    assertEquals(15..30, FpsSupport.rangeFor(listOf(30..30, 15..30), 30))
    assertEquals(30..30, FpsSupport.rangeFor(listOf(30..30), 30))
    assertEquals(60..60, FpsSupport.rangeFor(typical, 60))
    assertNull(FpsSupport.rangeFor(typical, 25))
    assertNull(FpsSupport.rangeFor(emptyList(), 30))
  }

  @Test
  fun nearest_clamps_tiesGoLower() {
    val opts = listOf(15, 30)
    assertEquals(30, FpsSupport.nearest(24, opts))
    assertEquals(15, FpsSupport.nearest(20, opts))
    assertEquals(15, FpsSupport.nearest(22, listOf(30, 15)))
    assertEquals(30, FpsSupport.nearest(1000, listOf(15, 24, 30)))
    assertEquals(60, FpsSupport.nearest(60, listOf(15, 30, 60)))
    assertEquals(15, FpsSupport.nearest(1, listOf(15, 24, 30)))
    assertEquals(30, FpsSupport.nearest(30, emptyList()))
  }

  @Test
  fun lookup_perSensorAndResolution_orientationIndependent() {
    val map = mapOf(
      "0" to mapOf("1920x1080" to listOf(15, 24, 30, 60), "3840x2160" to listOf(15, 30), FpsSupport.ANY_SIZE to listOf(15, 24, 30, 60)),
      "1" to mapOf("1920x1080" to listOf(15, 30), FpsSupport.ANY_SIZE to listOf(30))
    )
    assertEquals(listOf(15, 30), FpsSupport.lookup(map, "0", 2160, 3840))
    assertEquals(listOf(15, 30), FpsSupport.lookup(map, "0", 3840, 2160))
    assertEquals(listOf(15, 24, 30, 60), FpsSupport.lookup(map, "0", 1080, 1920))
    assertEquals(listOf(15, 30), FpsSupport.lookup(map, "1", 1080, 1920))
    assertEquals(listOf(30), FpsSupport.lookup(map, "1", 640, 480))
    assertEquals(listOf(15, 24, 30), FpsSupport.lookup(map, "9", 1920, 1080))
  }

  @Test
  fun parseStored_currentAndPre120Formats() {
    assertEquals(60, FpsSupport.parseStored("60"))
    assertEquals(15, FpsSupport.parseStored("FPS_15"))
    assertEquals(24, FpsSupport.parseStored("FPS_24"))
    assertEquals(30, FpsSupport.parseStored("FPS_30"))
    assertEquals(30, FpsSupport.parseStored(null))
    assertEquals(30, FpsSupport.parseStored("garbage"))
    assertEquals(30, FpsSupport.parseStored("0"))
    assertEquals(30, FpsSupport.parseStored("-5"))
  }

  @Test
  fun staticFonts_whitelistOnly() {
    val font = StaticFonts.load("/fonts/PlusJakartaSans-latin.woff2")
    assertNotNull(font)
    assertEquals("wOF2", String(font!!, 0, 4, Charsets.US_ASCII))
    assertNotNull(StaticFonts.load("/fonts/JetBrainsMono-latin.woff2"))
    assertNull(StaticFonts.load("/fonts/OFL-JetBrainsMono.txt"))
    assertNull(StaticFonts.load("/fonts/../fonts/JetBrainsMono-latin.woff2"))
    assertNull(StaticFonts.load("/fonts/"))
    assertNull(StaticFonts.load("/JetBrainsMono-latin.woff2"))
  }
}
