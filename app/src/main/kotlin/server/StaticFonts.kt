package io.github.roaxio.ezcammic.server

import java.util.concurrent.ConcurrentHashMap

/**
 * Web fonts used by the dashboard, bundled as Java resources (app/src/main/resources/fonts)
 * and served on /fonts/<file>. Only the exact file names below are served, so there is no
 * path handling (and no path traversal). Licenses: SIL OFL 1.1, see OFL-*.txt next to the files.
 */
object StaticFonts {
    const val ROUTE_PREFIX = "/fonts/"

    private val WHITELIST = setOf(
        "PlusJakartaSans-latin.woff2",
        "JetBrainsMono-latin.woff2"
    )

    private val cache = ConcurrentHashMap<String, ByteArray>()

    /** Returns the font bytes for a whitelisted request path such as "/fonts/JetBrainsMono-latin.woff2", or null. */
    fun load(path: String): ByteArray? {
        if (!path.startsWith(ROUTE_PREFIX)) return null
        val name = path.substring(ROUTE_PREFIX.length)
        if (name !in WHITELIST) return null
        cache[name]?.let { return it }
        val bytes = StaticFonts::class.java.getResourceAsStream("/fonts/$name")?.use { it.readBytes() } ?: return null
        cache[name] = bytes
        return bytes
    }
}
