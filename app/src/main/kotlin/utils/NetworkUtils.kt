package io.github.roaxio.ezcammic.utils

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

object NetworkUtils {

    data class NetworkIpInfo(
        val ipAddress: String,
        val interfaceName: String,
        val type: NetworkType,
        val displayName: String
    )

    enum class NetworkType {
        WIFI,
        USB_TETHERING,
        HOTSPOT,
        ETHERNET,
        OTHER
    }

    /**
     * Retrieves all available IPv4 addresses grouped by network interface.
     */
    fun getAllIpAddresses(context: Context): List<NetworkIpInfo> {
        val result = mutableListOf<NetworkIpInfo>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        val name = intf.name.lowercase()
                        val type = when {
                            name.startsWith("wlan") -> NetworkType.WIFI
                            name.startsWith("rndis") || name.startsWith("usb") || name.startsWith("ncm") -> NetworkType.USB_TETHERING
                            name.startsWith("ap") || name.startsWith("softap") -> NetworkType.HOTSPOT
                            name.startsWith("eth") -> NetworkType.ETHERNET
                            else -> NetworkType.OTHER
                        }
                        val displayName = when (type) {
                            NetworkType.WIFI -> "Wi-Fi (${intf.name})"
                            NetworkType.USB_TETHERING -> "USB Tethering (${intf.name})"
                            NetworkType.HOTSPOT -> "Hotspot (${intf.name})"
                            NetworkType.ETHERNET -> "Ethernet (${intf.name})"
                            NetworkType.OTHER -> "Network (${intf.name})"
                        }
                        result.add(NetworkIpInfo(host, intf.name, type, displayName))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to WifiManager if empty
        if (result.isEmpty()) {
            val wifiIp = getWifiIpAddress(context)
            if (wifiIp != null && wifiIp != "0.0.0.0") {
                result.add(NetworkIpInfo(wifiIp, "wlan0", NetworkType.WIFI, "Wi-Fi"))
            }
        }

        return result
    }

    fun getPrimaryIpAddress(context: Context): String {
        val list = getAllIpAddresses(context)
        // Prefer USB tethering if available, then WiFi, then any
        val usb = list.firstOrNull { it.type == NetworkType.USB_TETHERING }
        if (usb != null) return usb.ipAddress

        val wifi = list.firstOrNull { it.type == NetworkType.WIFI }
        if (wifi != null) return wifi.ipAddress

        return list.firstOrNull()?.ipAddress ?: "127.0.0.1"
    }
    
    fun isUsbTetheringActive(context: Context): Boolean {
        val list = getAllIpAddresses(context)
        return list.any { it.type == NetworkType.USB_TETHERING }
    }
    
    fun getUsbTetheringIp(context: Context): String? {
        val list = getAllIpAddresses(context)
        return list.firstOrNull { it.type == NetworkType.USB_TETHERING }?.ipAddress
    }

    private fun getWifiIpAddress(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt == 0) null
            else String.format(
                Locale.US,
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        } catch (e: Exception) {
            null
        }
    }

    fun buildWebDashboardUrl(ip: String, port: Int, isHttps: Boolean = false, authPrefix: String = ""): String {
        val proto = if (isHttps) "https" else "http"
        return "$proto://$authPrefix$ip:$port"
    }

    fun buildVideoFeedUrl(ip: String, port: Int, isHttps: Boolean = false, authPrefix: String = ""): String {
        val proto = if (isHttps) "https" else "http"
        return "$proto://$authPrefix$ip:$port/video"
    }

    fun buildAudioFeedUrl(ip: String, port: Int, isHttps: Boolean = false, authPrefix: String = ""): String {
        val proto = if (isHttps) "https" else "http"
        return "$proto://$authPrefix$ip:$port/audio.wav"
    }

    fun buildSnapshotUrl(ip: String, port: Int, isHttps: Boolean = false, authPrefix: String = ""): String {
        val proto = if (isHttps) "https" else "http"
        return "$proto://$authPrefix$ip:$port/shot.jpg"
    }
}

