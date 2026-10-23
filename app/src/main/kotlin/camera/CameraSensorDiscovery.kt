package io.github.roaxio.ezcammic.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import io.github.roaxio.ezcammic.model.AspectRatioOption
import io.github.roaxio.ezcammic.model.CameraFacing
import io.github.roaxio.ezcammic.model.CameraSensorInfo
import io.github.roaxio.ezcammic.model.StreamOrientation
import io.github.roaxio.ezcammic.model.StreamResolution
import kotlin.math.abs
import kotlin.math.roundToInt

object CameraSensorDiscovery {

    fun getAvailableSensors(context: Context): List<CameraSensorInfo> {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return getDefaultSensors()

            val idList = cameraManager.cameraIdList
            if (idList.isEmpty()) return getDefaultSensors()

            val backSensors = mutableListOf<Triple<String, Float, CameraCharacteristics>>()
            val frontSensors = mutableListOf<Triple<String, Float, CameraCharacteristics>>()

            for (id in idList) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val focalLength = focalLengths?.firstOrNull() ?: 0f

                    if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                        backSensors.add(Triple(id, focalLength, chars))
                    } else if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                        frontSensors.add(Triple(id, focalLength, chars))
                    }
                } catch (e: Exception) {
                    // Skip unreadable sensor
                }
            }

            val sensors = mutableListOf<CameraSensorInfo>()

            // Classify back sensors by focal length
            backSensors.sortBy { it.second }
            backSensors.forEachIndexed { index, (id, focalLength, chars) ->
                val name = when {
                    backSensors.size == 1 -> "Rear Main"
                    index == 0 && backSensors.size > 1 && (focalLength < 3.0f || focalLength < (backSensors.getOrNull(1)?.second ?: 999f)) -> "Rear Ultra-Wide"
                    index == backSensors.size - 1 && backSensors.size > 2 -> "Rear Telephoto"
                    index == 1 && backSensors.size > 2 -> "Rear Main (Wide)"
                    backSensors.size == 2 && index == 0 -> "Rear Ultra-Wide"
                    backSensors.size == 2 && index == 1 -> "Rear Main (Wide)"
                    else -> "Rear Sensor #$id"
                }
                val maxBadge = getMaxResolutionBadge(chars)
                val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                sensors.add(
                    CameraSensorInfo(
                        cameraId = id,
                        facing = CameraFacing.BACK,
                        name = "$name [ID: $id]",
                        focalLengthMm = focalLength,
                        maxResolutionBadge = maxBadge,
                        sensorOrientation = sensorOrientation
                    )
                )
            }

            // Front sensors
            frontSensors.sortBy { it.second }
            frontSensors.forEachIndexed { index, (id, focalLength, chars) ->
                val name = when {
                    frontSensors.size == 1 -> "Front Selfie"
                    index == 0 && frontSensors.size > 1 -> "Front Wide"
                    else -> "Front Sensor #$id"
                }
                val maxBadge = getMaxResolutionBadge(chars)
                val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
                sensors.add(
                    CameraSensorInfo(
                        cameraId = id,
                        facing = CameraFacing.FRONT,
                        name = "$name [ID: $id]",
                        focalLengthMm = focalLength,
                        maxResolutionBadge = maxBadge,
                        sensorOrientation = sensorOrientation
                    )
                )
            }

            if (sensors.isNotEmpty()) sensors else getDefaultSensors()
        } catch (e: Exception) {
            getDefaultSensors()
        }
    }

    fun getDefaultSensors(): List<CameraSensorInfo> = listOf(
        CameraSensorInfo("0", CameraFacing.BACK, "Rear Main [ID: 0]", 4.5f, "1080p", 90),
        CameraSensorInfo("1", CameraFacing.FRONT, "Front Selfie [ID: 1]", 2.8f, "1080p", 270)
    )

    private fun getMaxResolutionBadge(chars: CameraCharacteristics): String {
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888)?.toList() ?: emptyList()
        val surfaceSizes = map?.getOutputSizes(android.view.SurfaceHolder::class.java)?.toList() ?: emptyList()
        val combined = yuvSizes + surfaceSizes
        val maxDim = combined.maxOfOrNull { maxOf(it.width, it.height) } ?: 1920
        return when {
            maxDim >= 3840 -> "4K"
            maxDim >= 2560 -> "1440p"
            maxDim >= 1920 -> "1080p"
            maxDim >= 1280 -> "720p"
            else -> "480p"
        }
    }

    fun getSupportedAspectRatiosForSensor(
        context: Context,
        cameraId: String
    ): Pair<List<AspectRatioOption>, List<AspectRatioOption>> {
        val supportedSizes = getRawSensorSizesForCameraId(context, cameraId)

        val detectedLandscape = mutableListOf<AspectRatioOption>()
        val detectedPortrait = mutableListOf<AspectRatioOption>()

        val standardLandscape = listOf(
            AspectRatioOption.RATIO_16_9,
            AspectRatioOption.RATIO_4_3,
            AspectRatioOption.RATIO_1_1
        )
        val standardPortrait = listOf(
            AspectRatioOption.RATIO_9_16,
            AspectRatioOption.RATIO_3_4
        )

        if (supportedSizes.isNotEmpty()) {
            standardLandscape.forEach { ratio ->
                if (supportedSizes.any { sizeMatchesRatio(it.width, it.height, ratio.aspectX, ratio.aspectY) }) {
                    detectedLandscape.add(ratio)
                }
            }
            standardPortrait.forEach { ratio ->
                if (supportedSizes.any { sizeMatchesRatio(it.width, it.height, ratio.aspectY, ratio.aspectX) }) {
                    detectedPortrait.add(ratio)
                }
            }
        }

        val landscape = if (detectedLandscape.isNotEmpty()) detectedLandscape else AspectRatioOption.DEFAULT_LANDSCAPE
        val portrait = if (detectedPortrait.isNotEmpty()) detectedPortrait else AspectRatioOption.DEFAULT_PORTRAIT

        return Pair(landscape, portrait)
    }

    fun getResolutionsForSensorAndAspectRatio(
        context: Context,
        cameraId: String,
        aspectRatio: AspectRatioOption
    ): List<StreamResolution> {
        val rawSizes = getRawSensorSizesForCameraId(context, cameraId)
        val isPortrait = aspectRatio.orientation == StreamOrientation.PORTRAIT

        val filteredSizes = if (rawSizes.isNotEmpty()) {
            if (isPortrait) {
                rawSizes.filter { sizeMatchesRatio(it.width, it.height, aspectRatio.aspectY, aspectRatio.aspectX) }
                    .map { Size(minOf(it.width, it.height), maxOf(it.width, it.height)) }
            } else {
                rawSizes.filter { sizeMatchesRatio(it.width, it.height, aspectRatio.aspectX, aspectRatio.aspectY) }
                    .map { Size(maxOf(it.width, it.height), minOf(it.width, it.height)) }
            }
        } else {
            emptyList()
        }

        if (filteredSizes.isNotEmpty()) {
            val distinctSizes = filteredSizes.distinctBy { "${it.width}x${it.height}" }
                .sortedByDescending { it.width * it.height }

            return distinctSizes.map { size ->
                buildStreamResolution(size.width, size.height, aspectRatio.id)
            }
        }

        return StreamResolution.DEFAULT_PRESETS.filter { it.aspectRatioId == aspectRatio.id }
            .sortedByDescending { it.width * it.height }
    }

    fun getSupportedAspectRatios(
        context: Context,
        facing: CameraFacing
    ): Pair<List<AspectRatioOption>, List<AspectRatioOption>> {
        val sensor = getAvailableSensors(context).firstOrNull { it.facing == facing }
        return if (sensor != null) {
            getSupportedAspectRatiosForSensor(context, sensor.cameraId)
        } else {
            Pair(AspectRatioOption.DEFAULT_LANDSCAPE, AspectRatioOption.DEFAULT_PORTRAIT)
        }
    }

    fun getResolutionsForAspectRatio(
        context: Context,
        facing: CameraFacing,
        aspectRatio: AspectRatioOption
    ): List<StreamResolution> {
        val sensor = getAvailableSensors(context).firstOrNull { it.facing == facing }
        return if (sensor != null) {
            getResolutionsForSensorAndAspectRatio(context, sensor.cameraId, aspectRatio)
        } else {
            StreamResolution.DEFAULT_PRESETS.filter { it.aspectRatioId == aspectRatio.id }
        }
    }

    /**
     * Supported FPS options for every camera id and each of its output sizes:
     * sensorId -> FpsSupport.sizeKey(w, h) -> options (plus FpsSupport.ANY_SIZE for sizes not listed).
     */
    fun getSupportedFpsMap(context: Context): Map<String, Map<String, List<Int>>> {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyMap()
            cameraManager.cameraIdList.associateWith { id ->
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val ranges = getAeFpsRanges(chars)
                    val bySize = mutableMapOf(FpsSupport.ANY_SIZE to FpsSupport.optionsFor(ranges, null))
                    for ((size, maxFps) in getMaxFpsBySize(chars)) {
                        bySize[FpsSupport.sizeKey(size.width, size.height)] = FpsSupport.optionsFor(ranges, maxFps)
                    }
                    bySize
                } catch (e: Exception) {
                    emptyMap()
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** The AE target FPS range to request on [cameraId] for [targetFps] at [resolution], or null to leave the camera default. */
    fun getAeFpsRangeFor(context: Context, cameraId: String, resolution: StreamResolution, targetFps: Int): IntRange? {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val ranges = getAeFpsRanges(chars)
            val key = FpsSupport.sizeKey(resolution.width, resolution.height)
            val maxFps = getMaxFpsBySize(chars).entries.firstOrNull { FpsSupport.sizeKey(it.key.width, it.key.height) == key }?.value
            val effective = FpsSupport.nearest(targetFps, FpsSupport.optionsFor(ranges, maxFps))
            FpsSupport.rangeFor(ranges, effective)
        } catch (e: Exception) {
            null
        }
    }

    private fun getAeFpsRanges(chars: CameraCharacteristics): List<IntRange> =
        chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.map { it.lower..it.upper } ?: emptyList()

    /** Max fps per output size (1e9 / min frame duration), for the same format the size list uses. */
    private fun getMaxFpsBySize(chars: CameraCharacteristics): Map<Size, Int?> {
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return emptyMap()
        val yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888)?.toList() ?: emptyList()
        fun toFps(durationNs: Long): Int? = if (durationNs > 0) (1_000_000_000.0 / durationNs).roundToInt() else null
        return if (yuvSizes.isNotEmpty()) {
            yuvSizes.associateWith { toFps(map.getOutputMinFrameDuration(ImageFormat.YUV_420_888, it)) }
        } else {
            val surfaceClass = android.view.SurfaceHolder::class.java
            (map.getOutputSizes(surfaceClass)?.toList() ?: emptyList())
                .associateWith { toFps(map.getOutputMinFrameDuration(surfaceClass, it)) }
        }
    }

    private fun getRawSensorSizesForCameraId(context: Context, cameraId: String): List<Size> {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888)?.toList() ?: emptyList()
            val surfaceSizes = map?.getOutputSizes(android.view.SurfaceHolder::class.java)?.toList() ?: emptyList()
            // Prioritize YUV_420_888 sizes as CameraX ImageAnalysis uses YUV_420_888 buffers
            if (yuvSizes.isNotEmpty()) {
                yuvSizes.distinctBy { "${it.width}x${it.height}" }
            } else {
                surfaceSizes.distinctBy { "${it.width}x${it.height}" }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun sizeMatchesRatio(w: Int, h: Int, aspectX: Int, aspectY: Int): Boolean {
        val targetRatio = aspectX.toDouble() / aspectY.toDouble()
        val actualRatio = maxOf(w, h).toDouble() / minOf(w, h).toDouble()
        return abs(targetRatio - actualRatio) < 0.05
    }

    private fun buildStreamResolution(w: Int, h: Int, aspectRatioId: String): StreamResolution {
        val maxDim = maxOf(w, h)
        val minDim = minOf(w, h)
        val badge = when {
            maxDim >= 3840 -> "4K"
            maxDim >= 2560 -> "1440p"
            maxDim >= 1920 -> "1080p"
            maxDim >= 1280 -> "720p"
            maxDim >= 854 -> "480p"
            maxDim >= 640 -> "480p"
            else -> "${minDim}p"
        }
        val label = when {
            maxDim >= 3840 -> "4K Ultra HD ($w x $h)"
            maxDim >= 2560 -> "2K Quad HD ($w x $h)"
            maxDim >= 1920 -> "Full HD ($w x $h)"
            maxDim >= 1280 -> "HD ($w x $h)"
            maxDim >= 854 || maxDim >= 640 -> "SD ($w x $h)"
            else -> "Custom ($w x $h)"
        }
        return StreamResolution(label, w, h, badge, aspectRatioId)
    }
}
