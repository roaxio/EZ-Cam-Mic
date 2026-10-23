package io.github.roaxio.ezcammic

import io.github.roaxio.ezcammic.camera.YuvUtils
import io.github.roaxio.ezcammic.model.AspectRatioOption
import io.github.roaxio.ezcammic.model.StreamOrientation
import io.github.roaxio.ezcammic.model.StreamResolution
import io.github.roaxio.ezcammic.server.SslUtils
import io.github.roaxio.ezcammic.server.WebcamHttpServer
import org.junit.Assert.*
import org.junit.Test

class CoreLogicTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun sslContext_canBeCreated() {
    val sslContext = SslUtils.getOrCreateSSLContext()
    assertNotNull(sslContext)
    val serverSocket = SslUtils.createSSLServerSocket(0)
    assertNotNull(serverSocket)
    assertTrue(serverSocket.localPort > 0)
    serverSocket.close()
  }

  @Test
  fun testAspectRatioOption_lookupAndDefaults() {
    val ratio169 = AspectRatioOption.findById("16:9")
    assertEquals("16:9", ratio169.id)
    assertEquals(StreamOrientation.LANDSCAPE, ratio169.orientation)
    assertEquals(16f / 9f, ratio169.ratioValue, 0.001f)

    val ratio916 = AspectRatioOption.findById("9:16")
    assertEquals("9:16", ratio916.id)
    assertEquals(StreamOrientation.PORTRAIT, ratio916.orientation)

    // Fallback when unknown
    val fallback = AspectRatioOption.findById("unknown_ratio")
    assertEquals("16:9", fallback.id)
  }

  @Test
  fun testStreamResolution_properties() {
    val res1080p = StreamResolution.RES_1080P
    assertEquals(1920, res1080p.width)
    assertEquals(1080, res1080p.height)
    assertEquals("1080p", res1080p.badge)
    assertEquals("16:9_1920x1080", res1080p.name)

    val res720p = StreamResolution.RES_720P
    assertEquals(1280, res720p.width)
    assertEquals(720, res720p.height)
  }

  @Test
  fun testWebcamHttpServer_admissionControlLimit() {
    assertEquals(128, WebcamHttpServer.MAX_CONCURRENT_SOCKETS)
    assertEquals(16, WebcamHttpServer.MAX_STREAMING_VIDEO_CLIENTS)
    assertEquals(16, WebcamHttpServer.MAX_STREAMING_AUDIO_CLIENTS)
  }

  @Test
  fun testDynamicResourceBudgets_derivesValidLimits() {
    val videoClients = io.github.roaxio.ezcammic.server.DynamicResourceBudgets.computeMaxStreamingVideoClients()
    assertTrue("Video clients should be within clamped bounds [4, 32]", videoClients in 4..32)

    val audioClients = io.github.roaxio.ezcammic.server.DynamicResourceBudgets.computeMaxStreamingAudioClients()
    assertTrue("Audio clients should be within clamped bounds [8, 32]", audioClients in 8..32)

    val sockets = io.github.roaxio.ezcammic.server.DynamicResourceBudgets.computeMaxConcurrentSockets()
    assertTrue("Concurrent sockets should be within clamped bounds [16, 128]", sockets in 16..128)

    val pool = io.github.roaxio.ezcammic.server.DynamicResourceBudgets.createClientThreadPool()
    assertEquals(sockets, pool.maximumPoolSize)
    pool.shutdown()
  }

  @Test
  fun testVideoFrameDistributor_registrationAndCapacity() {
    val distributor = io.github.roaxio.ezcammic.server.VideoFrameDistributor()
    val maxClients = distributor.maxStreamingClients
    assertTrue(maxClients >= 4)

    val streams = mutableListOf<java.io.ByteArrayOutputStream>()
    for (i in 0 until maxClients) {
      val baos = java.io.ByteArrayOutputStream()
      streams.add(baos)
      val registered = distributor.registerClient(baos)
      assertTrue("Client $i should register successfully", registered)
      assertTrue(distributor.isClientActive(baos))
    }
    assertEquals(maxClients, distributor.getConnectedClientsCount())

    // Saturation check: extra registration should be rejected
    val overflowStream = java.io.ByteArrayOutputStream()
    val overflowRegistered = distributor.registerClient(overflowStream)
    assertFalse("Overflow client should be rejected", overflowRegistered)

    // Unregister a client and verify decrement
    distributor.unregisterClient(streams[0])
    assertEquals(maxClients - 1, distributor.getConnectedClientsCount())
    assertFalse(distributor.isClientActive(streams[0]))

    distributor.stop()
    assertTrue(distributor.isStopped)
    assertEquals(0, distributor.getConnectedClientsCount())
  }

  @Test
  fun testYuvUtils_mirrorNv21_isInvolution() {
    val w = 16
    val h = 16
    val size = w * h * 3 / 2
    val original = ByteArray(size) { it.toByte() }
    val mirrored = ByteArray(size)
    val restored = ByteArray(size)

    YuvUtils.mirrorNv21(original, mirrored, w, h)
    YuvUtils.mirrorNv21(mirrored, restored, w, h)

    assertArrayEquals(original, restored)
  }

  @Test
  fun testYuvUtils_rotateNv21_180_isInvolution() {
    val w = 16
    val h = 16
    val size = w * h * 3 / 2
    val original = ByteArray(size) { (it % 128).toByte() }
    val rotated180 = ByteArray(size)
    val restored = ByteArray(size)

    YuvUtils.rotateNv21_180(original, rotated180, w, h)
    YuvUtils.rotateNv21_180(rotated180, restored, w, h)

    assertArrayEquals(original, restored)
  }

  @Test
  fun testWebcamConfig_cameraDisabledProperty() {
    val defaultConfig = io.github.roaxio.ezcammic.model.WebcamConfig()
    assertFalse(defaultConfig.isCameraDisabled)

    val audioOnlyConfig = defaultConfig.copy(isCameraDisabled = true)
    assertTrue(audioOnlyConfig.isCameraDisabled)
  }

  @Test
  fun testServerStats_cameraStreamingProperty() {
    val defaultStats = io.github.roaxio.ezcammic.model.ServerStats()
    assertTrue(defaultStats.isCameraStreaming)

    val pausedStats = defaultStats.copy(isCameraStreaming = false)
    assertFalse(pausedStats.isCameraStreaming)
  }
}
