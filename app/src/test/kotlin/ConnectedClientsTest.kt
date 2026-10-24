package io.github.roaxio.ezcammic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.ezcammic.audio.AudioStreamManager
import io.github.roaxio.ezcammic.camera.CameraStreamManager
import io.github.roaxio.ezcammic.server.WebcamHttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectedClientsTest {

  @Test
  fun testWebcamHttpServer_disconnectAndSessionBlacklist() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cameraManager = CameraStreamManager(context)
    val audioManager = AudioStreamManager()
    val server = WebcamHttpServer(cameraManager, audioManager) { _, _ -> }

    // Initially no blocked clients or connected clients
    assertTrue(server.getBlockedClients().isEmpty())
    assertTrue(server.getConnectedClients().isEmpty())

    // Disconnect and block an IP for session
    server.disconnectClient("192.168.1.50", blockForSession = true)
    assertTrue(server.getBlockedClients().contains("192.168.1.50"))
    assertEquals(1, server.getBlockedClients().size)

    // Unblock the client IP
    server.unblockClient("192.168.1.50")
    assertFalse(server.getBlockedClients().contains("192.168.1.50"))
    assertTrue(server.getBlockedClients().isEmpty())

    // Disconnect without blocking
    server.disconnectClient("192.168.1.60", blockForSession = false)
    assertFalse(server.getBlockedClients().contains("192.168.1.60"))
    assertTrue(server.getBlockedClients().isEmpty())
  }
}
