package io.github.roaxio.ezcammic.camera

import kotlin.math.abs

/**
 * Which frame rates a camera sensor can deliver natively, per resolution. Pure logic so it can be
 * unit tested; the camera data comes from [CameraSensorDiscovery].
 *
 * The options for a sensor + resolution are the distinct upper bounds of the sensor's AE target FPS
 * ranges (CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) that the resolution can reach
 * (<= 1e9 / StreamConfigurationMap min frame duration, when known). The chosen value is the maximum
 * frame rate requested from the camera; auto-exposure may go lower in low light.
 */
object FpsSupport {

    /** Default setting, and the options shown when a sensor reports no ranges. */
    const val DEFAULT_FPS = 30
    val FALLBACK_OPTIONS = listOf(15, 24, 30)

    /** Map key for a resolution, independent of portrait/landscape orientation. */
    fun sizeKey(width: Int, height: Int): String = "${maxOf(width, height)}x${minOf(width, height)}"

    /** Key used for a sensor's options when the resolution is not in its size list. */
    const val ANY_SIZE = "*"

    // Ranges must be positive and ordered; values above 1000 are not real frame rates (some old HALs report x1000).
    private fun validRanges(aeRanges: List<IntRange>) = aeRanges.filter { it.first > 0 && it.first <= it.last && it.last <= 1000 }

    /**
     * Supported options (ascending) for one sensor + resolution. No ranges reported -> [FALLBACK_OPTIONS].
     * If no upper bound is reachable at this resolution, the lowest upper bound is kept so there is always a value.
     */
    fun optionsFor(aeRanges: List<IntRange>, maxFpsForSize: Int?): List<Int> {
        val uppers = validRanges(aeRanges).map { it.last }.distinct().sorted()
        if (uppers.isEmpty()) return FALLBACK_OPTIONS
        val reachable = uppers.filter { maxFpsForSize == null || it <= maxFpsForSize }
        return reachable.ifEmpty { listOf(uppers.first()) }
    }

    /** The AE range to request for [fps]: upper bound == fps with the lowest lower bound (e.g. [7,30] over [30,30]). */
    fun rangeFor(aeRanges: List<IntRange>, fps: Int): IntRange? =
        validRanges(aeRanges).filter { it.last == fps }.minByOrNull { it.first }

    /** The option nearest to [fps] (ties go to the lower value). */
    fun nearest(fps: Int, options: List<Int>): Int {
        val list = options.ifEmpty { FALLBACK_OPTIONS }
        return list.minWithOrNull(compareBy<Int> { abs(it - fps) }.thenBy { it })!!
    }

    /** Options for sensor + resolution from a sensorId -> sizeKey -> options map; fallback options if unknown. */
    fun lookup(map: Map<String, Map<String, List<Int>>>, sensorId: String, width: Int, height: Int): List<Int> {
        val bySize = map[sensorId] ?: return FALLBACK_OPTIONS
        return bySize[sizeKey(width, height)] ?: bySize[ANY_SIZE] ?: FALLBACK_OPTIONS
    }

    /**
     * Reads the saved FPS setting. Accepts the current format ("60") and the pre-1.2.0 enum names
     * ("FPS_15", "FPS_24", "FPS_30"); anything else gives [DEFAULT_FPS].
     */
    fun parseStored(value: String?): Int {
        val digits = value?.trim()?.removePrefix("FPS_") ?: return DEFAULT_FPS
        return digits.toIntOrNull()?.takeIf { it in 1..1000 } ?: DEFAULT_FPS
    }
}
