package io.github.roaxio.ezcammic

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.ezcammic.data.SettingsPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsPreferencesTest {

  @Test
  fun `SettingsPreferences persists isCameraDisabled`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = SettingsPreferences(context)
    val config = prefs.loadConfig()
    assertFalse(config.isCameraDisabled)

    val updated = config.copy(isCameraDisabled = true)
    prefs.saveConfig(updated)

    val reloaded = prefs.loadConfig()
    assertTrue(reloaded.isCameraDisabled)

    // Restore
    prefs.saveConfig(config.copy(isCameraDisabled = false))
  }

  @Test
  fun `SettingsPreferences migrates old FPS enum value and stores any int`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val raw = context.getSharedPreferences("ezcammic_prefs", Context.MODE_PRIVATE)
    val prefs = SettingsPreferences(context)
    val original = prefs.loadConfig()

    raw.edit().putString("pref_target_fps", "FPS_24").commit()
    assertEquals(24, prefs.loadConfig().targetFps)

    prefs.saveConfig(prefs.loadConfig().copy(targetFps = 60))
    assertEquals("60", raw.getString("pref_target_fps", null))
    assertEquals(60, prefs.loadConfig().targetFps)

    raw.edit().remove("pref_target_fps").commit()
    assertEquals(30, prefs.loadConfig().targetFps)

    prefs.saveConfig(original)
  }

  @Test
  fun `CameraService builds smart notification and dispatches quick actions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val controller = Robolectric.buildService(CameraService::class.java).create()
    val service = controller.get()

    var muteToggled = 0
    var camToggled = 0
    var stopTriggered = 0

    try {
      CameraService.onToggleMuteAction = { muteToggled++ }
      CameraService.onToggleCameraAction = { camToggled++ }
      CameraService.onStopStreamAction = { stopTriggered++ }

      val startIntent = Intent(context, CameraService::class.java).apply {
        action = CameraService.ACTION_START
        putExtra(CameraService.EXTRA_ENDPOINT, "https://192.168.1.42:4747")
        putExtra(CameraService.EXTRA_CLIENTS, 2)
        putExtra(CameraService.EXTRA_AUDIO_MUTED, false)
        putExtra(CameraService.EXTRA_CAMERA_DISABLED, false)
        putExtra(CameraService.EXTRA_RESOLUTION, "1080p")
      }
      service.onStartCommand(startIntent, 0, 1)

      val liveNotification = service.buildSmartNotification()
      val title = liveNotification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
      val text = liveNotification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
      assertEquals("EZ-Cam-Mic • Live (2 clients)", title)
      assertTrue(text.contains("https://192.168.1.42:4747"))
      assertTrue(text.contains("Cam 1080p"))
      assertTrue(text.contains("Mic Active"))
      assertEquals(3, liveNotification.actions.size)
      assertEquals("Mute Mic", liveNotification.actions[0].title.toString())
      assertEquals("Cam Off", liveNotification.actions[1].title.toString())
      assertEquals("Stop", liveNotification.actions[2].title.toString())

      // Update to Audio Only + Muted state
      service.updateNotificationState(
        endpoint = "http://192.168.1.42:4747",
        connectedClients = 1,
        isAudioMuted = true,
        isCameraDisabled = true,
        resolution = "720p"
      )
      val standbyNotification = service.buildSmartNotification()
      val standbyTitle = standbyNotification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
      val standbyText = standbyNotification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
      assertEquals("EZ-Cam-Mic • Audio Only (1 client)", standbyTitle)
      assertTrue(standbyText.contains("Cam Off"))
      assertTrue(standbyText.contains("Mic Muted"))
      assertEquals("Unmute Mic", standbyNotification.actions[0].title.toString())
      assertEquals("Cam On", standbyNotification.actions[1].title.toString())

      // Verify quick-action intents dispatch to callbacks
      service.onStartCommand(Intent(context, CameraService::class.java).apply { action = CameraService.ACTION_TOGGLE_MUTE }, 0, 2)
      service.onStartCommand(Intent(context, CameraService::class.java).apply { action = CameraService.ACTION_TOGGLE_CAMERA }, 0, 3)
      service.onStartCommand(Intent(context, CameraService::class.java).apply { action = CameraService.ACTION_USER_STOP }, 0, 4)

      assertEquals(1, muteToggled)
      assertEquals(1, camToggled)
      assertEquals(1, stopTriggered)
    } finally {
      CameraService.onToggleMuteAction = null
      CameraService.onToggleCameraAction = null
      CameraService.onStopStreamAction = null
      service.onStartCommand(Intent(context, CameraService::class.java).apply { action = CameraService.ACTION_STOP }, 0, 5)
      controller.destroy()
    }
  }
}
