package io.github.roaxio.ezcammic.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.LifecycleOwner
import io.github.roaxio.ezcammic.model.AspectRatioOption
import io.github.roaxio.ezcammic.model.CameraFacing
import io.github.roaxio.ezcammic.model.StreamResolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import io.github.roaxio.ezcammic.server.VideoFrameDistributor

class CameraStreamManager(private val context: Context) {

    var frameDistributor: VideoFrameDistributor? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var activeBindToken = 0L
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private val _currentFps = MutableStateFlow(0.0)
    val currentFps: StateFlow<Double> = _currentFps.asStateFlow()

    private val _bitrateKbps = MutableStateFlow(0.0)
    val bitrateKbps: StateFlow<Double> = _bitrateKbps.asStateFlow()

    private val _activeResolution = MutableStateFlow("1920x1080")
    val activeResolution: StateFlow<String> = _activeResolution.asStateFlow()

    @Volatile
    var isReconfiguring: Boolean = false
        private set

    @Volatile
    private var latestJpegFrame: ByteArray? = null

    // Stream tuning settings
    var targetFps: Int = 30
    var jpegQuality: Int = 80
    var isMirrored: Boolean = false
    var currentFacing: CameraFacing = CameraFacing.BACK
        private set
    var currentCameraId: String = "0"
        private set
    var isTorchEnabled: Boolean = false
        private set
    var streamRotation: Int = 0
        private set
    var lockHorizontalStream: Boolean = false
    var isLandscape: Boolean = false
        private set
    var currentZoom: Float = 1.0f
        private set

    private var currentPreviewSurfaceProvider: Preview.SurfaceProvider? = null
    private var lastLifecycleOwner: LifecycleOwner? = null
    private var lastAspectRatioOption: AspectRatioOption = AspectRatioOption.RATIO_16_9
    private var lastResolution: StreamResolution = StreamResolution.RES_1080P
    private var lastTargetFpsSetting: Int = 30

    @Volatile
    var isCameraDisabled: Boolean = false
        set(value) {
            val changed = field != value
            field = value
            if (changed) {
                handleCameraDisabledChanged(value)
            }
        }

    private var placeholderJob: Job? = null
    private val placeholderScope = CoroutineScope(Dispatchers.Default)
    private var cachedPlaceholderFrame: ByteArray? = null

    private fun handleCameraDisabledChanged(disabled: Boolean) {
        if (disabled) {
            if (isTorchEnabled) {
                setTorch(false)
            }
            unbindHardwareCamera()
            startPlaceholderStreaming()
        } else {
            stopPlaceholderStreaming()
            val owner = lastLifecycleOwner
            if (owner != null) {
                bindCamera(
                    lifecycleOwner = owner,
                    cameraId = currentCameraId,
                    aspectRatioOption = lastAspectRatioOption,
                    resolution = lastResolution,
                    // targetFps may have been changed since the last bind (setting changed while not streaming).
                    targetFpsSetting = targetFps,
                    facing = currentFacing,
                    previewSurfaceProvider = currentPreviewSurfaceProvider,
                    isLandscape = this.isLandscape
                )
            }
        }
    }

    private fun getOrCreatePlaceholderFrame(): ByteArray {
        val existing = cachedPlaceholderFrame
        if (existing != null) return existing

        val w = 1280
        val h = 720
        val bitmap = createBitmap(w, h, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)

        // SlateDark950 Background (#070B14)
        val bgPaint = Paint().apply {
            color = Color.rgb(7, 11, 20)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Subtle accent boundary ring
        val ringPaint = Paint().apply {
            color = Color.argb(50, 6, 182, 212)
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        canvas.drawCircle(w / 2f, h / 2f - 30f, 70f, ringPaint)

        // Main text: CAMERA PAUSED
        val titlePaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            textSize = 44f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("CAMERA PAUSED", w / 2f, h / 2f + 80f, titlePaint)

        // Subtitle: Audio Streaming Active
        val subPaint = Paint().apply {
            color = Color.rgb(16, 185, 129)
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("• Microphone Audio Broadcasting Active •", w / 2f, h / 2f + 130f, subPaint)

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        bitmap.recycle()

        val bytes = stream.toByteArray()
        cachedPlaceholderFrame = bytes
        return bytes
    }

    private fun startPlaceholderStreaming() {
        stopPlaceholderStreaming()
        val frame = getOrCreatePlaceholderFrame()
        latestJpegFrame = frame
        _activeResolution.value = "Mic Only"
        placeholderJob = placeholderScope.launch {
            while (isActive && isCameraDisabled) {
                frameDistributor?.distributeFrame(frame)
                _currentFps.value = 1.0
                _bitrateKbps.value = (frame.size * 8.0) / 1000.0
                delay(1000L)
            }
        }
    }

    private fun stopPlaceholderStreaming() {
        placeholderJob?.cancel()
        placeholderJob = null
    }

    // Zero-allocation reusable NV21 buffers for hyper-fast CPU rotation and native JPEG compression
    private var rawNv21Buffer: ByteArray? = null
    private var rotatedNv21Buffer: ByteArray? = null
    private var mirroredNv21Buffer: ByteArray? = null
    private val reusableBaos = ByteArrayOutputStream(256 * 1024)

    // Current requested resolution from user configuration
    private var requestedResolution: StreamResolution = StreamResolution.RES_1080P

    // Frame rate and bitrate measurement
    private var frameCount = 0
    private var bytesSentWindow = 0L
    private var lastFpsCalculationTime = System.currentTimeMillis()

    @Synchronized
    private fun ensureBuffers(width: Int, height: Int) {
        val requiredSize = width * height * 3 / 2
        if (rawNv21Buffer == null || rawNv21Buffer!!.size < requiredSize) {
            rawNv21Buffer = ByteArray(requiredSize)
            rotatedNv21Buffer = ByteArray(requiredSize)
            mirroredNv21Buffer = ByteArray(requiredSize)
        }
    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        cameraId: String = "0",
        aspectRatioOption: AspectRatioOption = AspectRatioOption.RATIO_16_9,
        resolution: StreamResolution = StreamResolution.RES_1080P,
        targetFpsSetting: Int = 30,
        facing: CameraFacing = CameraFacing.BACK,
        previewSurfaceProvider: Preview.SurfaceProvider? = null,
        isLandscape: Boolean = false
    ) {
        this.lastLifecycleOwner = lifecycleOwner
        this.lastAspectRatioOption = aspectRatioOption
        this.lastResolution = resolution
        this.lastTargetFpsSetting = targetFpsSetting
        this.targetFps = targetFpsSetting
        this.currentFacing = facing
        this.currentCameraId = cameraId
        this.isLandscape = isLandscape
        this.currentPreviewSurfaceProvider = previewSurfaceProvider

        if (isCameraDisabled) {
            unbindHardwareCamera()
            startPlaceholderStreaming()
            return
        }

        isReconfiguring = true
        val token = ++activeBindToken
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                if (token != activeBindToken) return@addListener
                cameraProvider = cameraProviderFuture.get()
                if (token != activeBindToken) {
                    cameraProvider?.unbindAll()
                    return@addListener
                }
                cameraProvider?.unbindAll()

                val cameraSelector = try {
                    val builder = CameraSelector.Builder()
                    if (cameraId.isNotEmpty()) {
                        builder.addCameraFilter { cameraInfos ->
                            val matched = cameraInfos.filter { info ->
                                try {
                                    val c2 = androidx.camera.camera2.interop.Camera2CameraInfo.from(info)
                                    c2.cameraId == cameraId
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            if (matched.isNotEmpty()) matched else cameraInfos
                        }
                    } else {
                        if (facing == CameraFacing.FRONT) {
                            builder.requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        } else {
                            builder.requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        }
                    }
                    builder.build()
                } catch (e: Exception) {
                    if (facing == CameraFacing.FRONT) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                }

                this.requestedResolution = resolution
                _activeResolution.value = "${resolution.width}x${resolution.height}"

                val targetRotation = if (lockHorizontalStream || isLandscape) {
                    android.view.Surface.ROTATION_90
                } else {
                    android.view.Surface.ROTATION_0
                }

                val isRotated90 = (targetRotation == android.view.Surface.ROTATION_90 ||
                        targetRotation == android.view.Surface.ROTATION_270)

                val targetWidth = if (isRotated90) {
                    maxOf(resolution.width, resolution.height)
                } else {
                    minOf(resolution.width, resolution.height)
                }
                val targetHeight = if (isRotated90) {
                    minOf(resolution.width, resolution.height)
                } else {
                    maxOf(resolution.width, resolution.height)
                }
                val targetBound = Size(targetWidth, targetHeight)

                // 1. Preview resolution selector (optimized for display)
                val previewResSelectorBuilder = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            targetBound,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )

                when (aspectRatioOption.id) {
                    "16:9", "9:16" -> {
                        previewResSelectorBuilder.setAspectRatioStrategy(
                            AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO)
                        )
                    }
                    "4:3", "3:4" -> {
                        previewResSelectorBuilder.setAspectRatioStrategy(
                            AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
                        )
                    }
                }
                val previewResolutionSelector = previewResSelectorBuilder.build()

                // 2. Dedicated ImageAnalysis resolution selector with exact hardware size matching
                val analysisResSelectorBuilder = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            targetBound,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                    .setResolutionFilter { supportedSizes, _ ->
                        val targetW = targetBound.width
                        val targetH = targetBound.height
                        // Prioritize exact hardware match in either orientation
                        val exactMatches = supportedSizes.filter { size ->
                            (size.width == targetW && size.height == targetH) ||
                            (size.width == targetH && size.height == targetW)
                        }
                        if (exactMatches.isNotEmpty()) {
                            exactMatches
                        } else {
                            val targetRatio = targetW.toDouble() / targetH.toDouble()
                            supportedSizes.sortedWith(
                                compareBy<Size> { size ->
                                    val r1 = size.width.toDouble() / size.height.toDouble()
                                    val r2 = size.height.toDouble() / size.width.toDouble()
                                    minOf(kotlin.math.abs(r1 - targetRatio), kotlin.math.abs(r2 - targetRatio))
                                }.thenBy { size ->
                                    kotlin.math.abs(size.width * size.height - targetW * targetH)
                                }
                            )
                        }
                    }

                when (aspectRatioOption.id) {
                    "16:9", "9:16" -> {
                        analysisResSelectorBuilder.setAspectRatioStrategy(
                            AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO)
                        )
                    }
                    "4:3", "3:4" -> {
                        analysisResSelectorBuilder.setAspectRatioStrategy(
                            AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
                        )
                    }
                }
                val analysisResolutionSelector = analysisResSelectorBuilder.build()

                // Native frame rate: request an AE target FPS range this camera supports at this resolution.
                val aeFpsRange = try {
                    val info = cameraProvider?.let { cameraSelector.filter(it.availableCameraInfos).firstOrNull() }
                    info?.let {
                        val boundId = androidx.camera.camera2.interop.Camera2CameraInfo.from(it).cameraId
                        CameraSensorDiscovery.getAeFpsRangeFor(context, boundId, resolution, targetFpsSetting)
                    }
                } catch (e: Exception) {
                    null
                }

                val previewBuilder = Preview.Builder()
                    .setResolutionSelector(previewResolutionSelector)
                    .setTargetRotation(targetRotation)
                if (aeFpsRange != null) {
                    androidx.camera.camera2.interop.Camera2Interop.Extender(previewBuilder)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(aeFpsRange.first, aeFpsRange.last))
                }
                preview = previewBuilder.build()

                val providerToUse = previewSurfaceProvider ?: currentPreviewSurfaceProvider
                currentPreviewSurfaceProvider = providerToUse
                if (providerToUse != null) {
                    preview?.setSurfaceProvider(providerToUse)
                }

                val analysisBuilder = ImageAnalysis.Builder()
                    .setResolutionSelector(analysisResolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setTargetRotation(targetRotation)
                if (aeFpsRange != null) {
                    androidx.camera.camera2.interop.Camera2Interop.Extender(analysisBuilder)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(aeFpsRange.first, aeFpsRange.last))
                }
                imageAnalysis = analysisBuilder.build()

                imageAnalysis?.setAnalyzer(analysisExecutor) { imageProxy ->
                    processImageFrame(imageProxy)
                }

                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                // Restore zoom & torch state
                setZoom(currentZoom)
                setTorch(isTorchEnabled)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (token == activeBindToken) {
                    isReconfiguring = false
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun processImageFrame(imageProxy: ImageProxy) {
        try {
            if (isReconfiguring) {
                return
            }
            val srcW = imageProxy.width
            val srcH = imageProxy.height

            ensureBuffers(srcW, srcH)

            val rawBuffer = rawNv21Buffer ?: return
            val rotatedBuffer = rotatedNv21Buffer ?: return
            val mirroredBuffer = mirroredNv21Buffer ?: return

            // 1. Direct hardware YUV -> NV21 conversion
            YuvUtils.yuv420ToNv21(imageProxy, rawBuffer)

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            var currentBuffer = rawBuffer
            var outW = srcW
            var outH = srcH

            when (rotationDegrees) {
                90 -> {
                    YuvUtils.rotateNv21_90(rawBuffer, rotatedBuffer, srcW, srcH)
                    currentBuffer = rotatedBuffer
                    outW = srcH
                    outH = srcW
                }
                180 -> {
                    YuvUtils.rotateNv21_180(rawBuffer, rotatedBuffer, srcW, srcH)
                    currentBuffer = rotatedBuffer
                    outW = srcW
                    outH = srcH
                }
                270 -> {
                    YuvUtils.rotateNv21_270(rawBuffer, rotatedBuffer, srcW, srcH)
                    currentBuffer = rotatedBuffer
                    outW = srcH
                    outH = srcW
                }
                else -> {
                    currentBuffer = rawBuffer
                    outW = srcW
                    outH = srcH
                }
            }

            if (isMirrored) {
                YuvUtils.mirrorNv21(currentBuffer, mirroredBuffer, outW, outH)
                currentBuffer = mirroredBuffer
            }

            // Stream is physically upright now; pure native sensor output with zero scaling overhead
            streamRotation = 0
            _activeResolution.value = "${outW}x${outH}"

            // 4. Native C++ SIMD JPEG compression via libjpeg
            val yuvImage = YuvImage(currentBuffer, ImageFormat.NV21, outW, outH, null)
            val cropRect = Rect(0, 0, outW, outH)
            val jpegBytes: ByteArray
            synchronized(reusableBaos) {
                reusableBaos.reset()
                yuvImage.compressToJpeg(cropRect, jpegQuality.coerceIn(30, 95), reusableBaos)
                jpegBytes = reusableBaos.toByteArray()
            }

            latestJpegFrame = jpegBytes
            frameCount++
            bytesSentWindow += jpegBytes.size

            // Forward compressed frame to decoupled egress distributor
            frameDistributor?.distributeFrame(jpegBytes)

            // Measure FPS & Bitrate
            val now = System.currentTimeMillis()
            val elapsedMs = now - lastFpsCalculationTime
            if (elapsedMs >= 1000L) {
                val elapsedSec = elapsedMs / 1000.0
                if (elapsedSec > 0) {
                    _currentFps.value = frameCount / elapsedSec
                    _bitrateKbps.value = ((bytesSentWindow * 8.0) / 1000.0) / elapsedSec
                }
                frameCount = 0
                bytesSentWindow = 0L
                lastFpsCalculationTime = now
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    fun getLatestSnapshot(): ByteArray? {
        if (isCameraDisabled) {
            return getOrCreatePlaceholderFrame()
        }
        return latestJpegFrame
    }

    fun getConnectedClientsCount(): Int {
        return frameDistributor?.getConnectedClientsCount() ?: 0
    }

    fun setTorch(enable: Boolean) {
        isTorchEnabled = enable
        camera?.cameraControl?.enableTorch(enable)
    }

    fun setZoom(ratio: Float) {
        currentZoom = ratio.coerceIn(1.0f, 5.0f)
        camera?.cameraControl?.setZoomRatio(currentZoom)
    }

    fun setPreviewSurfaceProvider(provider: Preview.SurfaceProvider?) {
        try {
            currentPreviewSurfaceProvider = provider
            preview?.setSurfaceProvider(provider)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun unbindHardwareCamera() {
        try {
            isReconfiguring = true
            val unbindToken = ++activeBindToken
            val mainExecutor = ContextCompat.getMainExecutor(context)
            mainExecutor.execute {
                try {
                    preview?.setSurfaceProvider(null)
                    camera = null
                    preview = null
                    imageAnalysis = null
                    val provider = cameraProvider
                    if (provider != null) {
                        provider.unbindAll()
                    } else {
                        val future = ProcessCameraProvider.getInstance(context)
                        if (future.isDone) {
                            try {
                                val p = future.get()
                                cameraProvider = p
                                p.unbindAll()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            future.addListener({
                                try {
                                    val p = future.get()
                                    cameraProvider = p
                                    if (unbindToken == activeBindToken) {
                                        p.unbindAll()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, mainExecutor)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            rawNv21Buffer = null
            rotatedNv21Buffer = null
            mirroredNv21Buffer = null
            _currentFps.value = 0.0
            _bitrateKbps.value = 0.0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unbind() {
        stopPlaceholderStreaming()
        lastLifecycleOwner = null
        unbindHardwareCamera()
    }

    fun release() {
        try {
            stopPlaceholderStreaming()
            placeholderScope.cancel()
            unbind()
            analysisExecutor.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
