package io.github.roaxio.ezcammic.server

object HtmlDashboard {

    fun getDashboardHtml(
        serverPort: Int,
        activeConnectedIp: String = "0.0.0.0",
        wifiIp: String? = null,
        usbIp: String? = null,
        isCurrentConnectionUsb: Boolean = false,
        isHttps: Boolean = false,
        isAuthEnabled: Boolean = false,
        authUsername: String = "admin",
        authPassword: String = "admin123"
    ): String {
        val scheme = if (isHttps) "https" else "http"
        val effectiveIp = if (activeConnectedIp.isNotBlank() && activeConnectedIp != "0.0.0.0") {
            activeConnectedIp
        } else {
            (if (isCurrentConnectionUsb) usbIp else wifiIp) ?: "192.168.1.1"
        }
        val defaultBase = "$scheme://$effectiveIp:$serverPort"
        val wifiIpStr = wifiIp ?: ""
        val usbIpStr = usbIp ?: ""
        val safeUser = authUsername.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "")
        val safePass = authPassword.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "")
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>EZ-Cam-Mic &bull; PC Webcam & Audio Feed</title>
    <style>
        /* Fonts are bundled in the app and served by this server (SIL OFL 1.1), latin subset. */
        @font-face {
            font-family: 'Plus Jakarta Sans';
            font-style: normal;
            font-weight: 400 800;
            font-display: swap;
            src: url(/fonts/PlusJakartaSans-latin.woff2) format('woff2');
            unicode-range: U+0000-00FF, U+0131, U+0152-0153, U+02BB-02BC, U+02C6, U+02DA, U+02DC, U+0304, U+0308, U+0329, U+2000-206F, U+20AC, U+2122, U+2191, U+2193, U+2212, U+2215, U+FEFF, U+FFFD;
        }
        @font-face {
            font-family: 'JetBrains Mono';
            font-style: normal;
            font-weight: 400 600;
            font-display: swap;
            src: url(/fonts/JetBrainsMono-latin.woff2) format('woff2');
            unicode-range: U+0000-00FF, U+0131, U+0152-0153, U+02BB-02BC, U+02C6, U+02DA, U+02DC, U+0304, U+0308, U+0329, U+2000-206F, U+20AC, U+2122, U+2191, U+2193, U+2212, U+2215, U+FEFF, U+FFFD;
        }
        :root {
            --bg-base: #0a0e17;
            --bg-card: #111827;
            --bg-card-hover: #1f293d;
            --bg-inset: #0d131f;
            --accent-cyan: #00e5ff;
            --accent-blue: #0284c7;
            --accent-emerald: #10b981;
            --accent-amber: #f59e0b;
            --accent-rose: #f43f5e;
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
            --text-dim: #64748b;
            --border-subtle: #1e293b;
            --border-glow: rgba(0, 229, 255, 0.25);
            --radius-md: 12px;
            --radius-lg: 18px;
            --shadow-card: 0 10px 30px -10px rgba(0,0,0,0.6);
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif;
            background-color: var(--bg-base);
            color: var(--text-main);
            line-height: 1.5;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            overflow-x: hidden;
        }

        header {
            background: rgba(17, 24, 39, 0.85);
            backdrop-filter: blur(12px);
            border-bottom: 1px solid var(--border-subtle);
            padding: 1rem 2rem;
            position: sticky;
            top: 0;
            z-index: 50;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .brand-container {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .brand-logo {
            width: 36px;
            height: 36px;
            border-radius: 10px;
            background: linear-gradient(135deg, var(--accent-cyan), var(--accent-blue));
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 0 15px rgba(0, 229, 255, 0.4);
        }

        .brand-logo svg {
            width: 20px;
            height: 20px;
            fill: #000;
        }

        .brand-title {
            font-weight: 800;
            font-size: 1.25rem;
            letter-spacing: -0.02em;
            background: linear-gradient(90deg, #ffffff, #cbd5e1);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }

        .badge-live {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            background: rgba(16, 185, 129, 0.15);
            color: var(--accent-emerald);
            border: 1px solid rgba(16, 185, 129, 0.3);
            padding: 4px 10px;
            border-radius: 20px;
            font-size: 0.75rem;
            font-weight: 700;
            letter-spacing: 0.05em;
            text-transform: uppercase;
        }

        .live-dot {
            width: 7px;
            height: 7px;
            border-radius: 50%;
            background-color: var(--accent-emerald);
            animation: pulse-emerald 1.5s infinite;
        }

        @keyframes pulse-emerald {
            0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
            70% { transform: scale(1.1); box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); }
            100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
        }

        main {
            max-width: 1400px;
            margin: 0 auto;
            padding: 2rem;
            width: 100%;
            display: grid;
            grid-template-columns: 1fr 380px;
            gap: 2rem;
            flex: 1;
        }

        @media (max-width: 1024px) {
            main {
                grid-template-columns: 1fr;
            }
        }

        .feed-card {
            background: var(--bg-card);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-lg);
            overflow: hidden;
            box-shadow: var(--shadow-card);
            display: flex;
            flex-direction: column;
        }

        .feed-header {
            padding: 1rem 1.5rem;
            background: rgba(255, 255, 255, 0.02);
            border-bottom: 1px solid var(--border-subtle);
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .feed-title {
            font-weight: 600;
            font-size: 0.95rem;
            color: var(--text-muted);
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .feed-viewport {
            background: var(--bg-inset);
            position: relative;
            min-height: 480px;
            display: flex;
            align-items: center;
            justify-content: center;
            overflow: hidden;
        }

        .video-stream {
            width: 100%;
            height: auto;
            max-height: 75vh;
            object-fit: contain;
            display: block;
            background: #000;
        }

        .overlay-stats {
            position: absolute;
            top: 1rem;
            left: 1rem;
            background: rgba(10, 14, 23, 0.75);
            backdrop-filter: blur(8px);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: var(--radius-md);
            padding: 0.5rem 0.85rem;
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.8rem;
            display: flex;
            gap: 12px;
            color: var(--accent-cyan);
            z-index: 10;
        }

        .controls-sidebar {
            display: flex;
            flex-direction: column;
            gap: 1.5rem;
        }

        .control-section {
            background: var(--bg-card);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-lg);
            padding: 1.25rem;
            box-shadow: var(--shadow-card);
        }

        .section-heading {
            font-size: 0.85rem;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--text-dim);
            margin-bottom: 1rem;
            display: flex;
            align-items: center;
            gap: 6px;
        }

        .btn-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 0.75rem;
        }

        .btn-action {
            background: var(--bg-card-hover);
            border: 1px solid var(--border-subtle);
            color: var(--text-main);
            padding: 0.75rem 1rem;
            border-radius: var(--radius-md);
            font-family: inherit;
            font-size: 0.875rem;
            font-weight: 600;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
        }

        .btn-action:hover {
            border-color: var(--accent-cyan);
            box-shadow: 0 0 12px var(--border-glow);
            transform: translateY(-1px);
        }

        .btn-action:active {
            transform: translateY(1px);
        }

        .btn-action.active {
            background: rgba(0, 229, 255, 0.15);
            border-color: var(--accent-cyan);
            color: var(--accent-cyan);
        }

        .slider-group {
            margin-top: 1rem;
        }

        .slider-label {
            display: flex;
            justify-content: space-between;
            font-size: 0.8rem;
            color: var(--text-muted);
            margin-bottom: 0.5rem;
        }

        input[type="range"] {
            -webkit-appearance: none;
            width: 100%;
            height: 6px;
            border-radius: 3px;
            background: #1e293b;
            outline: none;
        }

        input[type="range"]::-webkit-slider-thumb {
            -webkit-appearance: none;
            appearance: none;
            width: 18px;
            height: 18px;
            border-radius: 50%;
            background: var(--accent-cyan);
            cursor: pointer;
            box-shadow: 0 0 10px rgba(0, 229, 255, 0.6);
        }

        .url-box {
            background: var(--bg-inset);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-md);
            padding: 0.75rem 1rem;
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.8rem;
            color: var(--accent-cyan);
            word-break: break-all;
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-top: 0.5rem;
        }

        .btn-copy {
            background: none;
            border: none;
            color: var(--text-muted);
            cursor: pointer;
            padding: 4px;
            border-radius: 4px;
            display: flex;
            align-items: center;
        }

        .btn-copy:hover {
            color: #fff;
        }

        /* Modern Switch Styles */
        .switch {
            position: relative;
            display: inline-block;
            width: 32px;
            height: 18px;
            flex-shrink: 0;
            vertical-align: middle;
        }

        .switch input {
            opacity: 0;
            width: 0;
            height: 0;
            position: absolute;
        }

        .slider {
            position: absolute;
            cursor: pointer;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background-color: #334155;
            transition: .25s ease;
            border-radius: 18px;
        }

        .slider:before {
            position: absolute;
            content: "";
            height: 12px;
            width: 12px;
            left: 3px;
            bottom: 3px;
            background-color: #f8fafc;
            transition: .25s ease;
            border-radius: 50%;
            box-shadow: 0 1px 3px rgba(0,0,0,0.4);
        }

        input:checked + .slider {
            background-color: var(--accent-cyan);
            box-shadow: 0 0 8px rgba(0, 229, 255, 0.4);
        }

        input:checked + .slider:before {
            transform: translateX(14px);
            background-color: #0a0e17;
        }
    </style>
</head>
<body>
    <header>
        <div class="brand-container">
            <div class="brand-logo">
                <svg viewBox="0 0 24 24"><path d="M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z"/></svg>
            </div>
            <div class="brand-title">EZ-Cam-Mic</div>
        </div>
        <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap;">
            ${if (isAuthEnabled) """
            <div id="header-auth-box" style="display: flex; align-items: center; gap: 8px; background: rgba(0, 229, 255, 0.08); border: 1px solid rgba(0, 229, 255, 0.25); padding: 5px 12px; border-radius: 20px;">
                <label class="switch" title="Toggle embedded credentials in stream URLs">
                    <input type="checkbox" id="toggle-embed-auth" onchange="setAuthEmbed(this.checked)">
                    <span class="slider"></span>
                </label>
                <span style="font-size: 0.72rem; color: var(--accent-cyan); font-weight: 700; white-space: nowrap;">🔐 Embed Auth in URLs</span>
            </div>
            """ else ""}
            <div id="conn-badge" class="badge-live">
                <div id="conn-dot" class="live-dot"></div>
                <span id="conn-badge-text">${if (isCurrentConnectionUsb) "⚡ USB TETHERING" else "📶 WI-FI LAN"}</span>
            </div>
        </div>
    </header>

    <div style="background: rgba(6, 182, 212, 0.12); border-bottom: 1px solid rgba(6, 182, 212, 0.25); padding: 8px 16px; display: flex; justify-content: space-between; align-items: center; font-size: 0.85rem; color: #94a3b8; flex-wrap: wrap; gap: 8px;">
        <div>
            <strong style="color: var(--accent-cyan);">Camera Feed Only:</strong> Need just the video stream for OBS, VLC, or browser fullscreen?
            <a id="banner-video-link" href="/video" target="_blank" style="color: var(--accent-emerald); font-family: monospace; font-weight: bold; margin-left: 6px; text-decoration: underline;">Open /video feed directly</a>
        </div>
        <button class="btn-action" style="padding: 4px 10px; font-size: 0.75rem;" onclick="copyBannerVideoUrl()">
            📋 Copy Raw /video URL
        </button>
    </div>

    <main>
        <section class="feed-card">
            <div class="feed-header">
                <div class="feed-title">
                    <span>Active Webcam Feed</span>
                </div>
                <div style="font-size: 0.8rem; display: flex; gap: 8px; align-items: center;">
                    <span id="conn-mode-text" style="color: var(--accent-cyan);">${if (isCurrentConnectionUsb) "⚡ USB Tethering" else "📶 Wi-Fi LAN"}</span>
                    <span style="color: var(--text-dim);">&bull;</span>
                    <button class="btn-action" style="padding: 3px 8px; font-size: 0.75rem;" onclick="reloadStream()">
                        <span>🔁 Reload</span>
                    </button>
                </div>
            </div>
            <div class="feed-viewport">
                <div class="overlay-stats">
                    <span id="stat-fps">-- FPS</span>
                    <span>&bull;</span>
                    <span id="stat-res">1080p</span>
                    <span>&bull;</span>
                    <span id="stat-bitrate">-- Kbps</span>
                </div>
                <img id="stream-img" class="video-stream" src="/video" alt="Live Camera Feed" />
            </div>
        </section>

        <aside class="controls-sidebar">
            <div class="control-section">
                <div class="section-heading">Remote Controls</div>
                <div class="btn-grid">
                    <button id="btn-torch" class="btn-action" onclick="sendCommand('torch', 'toggle')">
                        <span id="torch-label">🔦 Torch</span>
                    </button>
                    <button id="btn-camera" class="btn-action" onclick="sendCommand('camera', 'switch')">
                        <span>🔄 Switch Cam</span>
                    </button>
                    <button id="btn-mute" class="btn-action" onclick="sendCommand('mute', 'toggle')">
                        <span id="mute-label">🎙️ Mic Active</span>
                    </button>
                    <button class="btn-action" onclick="window.open('/snapshot?t=' + Date.now(), '_blank')">
                        <span>📸 Snapshot</span>
                    </button>
                </div>

                <div class="slider-group">
                    <div class="slider-label">
                        <span>Digital Zoom</span>
                        <span id="zoom-val" style="color: var(--accent-cyan);">1.0x</span>
                    </div>
                    <input type="range" id="zoom-slider" min="1" max="5" step="0.1" value="1" oninput="updateZoom(this.value)" />
                </div>
            </div>

            <div class="control-section">
                <details style="font-size: 0.78rem; color: var(--text-muted);" open>
                    <summary style="cursor: pointer; padding: 0.25rem 0; font-weight: 600; color: var(--accent-cyan);">🎧 Test Audio Stream Directly on PC (VLC / FFplay)</summary>
                    <div style="padding-top: 0.5rem; line-height: 1.4;">
                        <p style="margin: 0 0 0.4rem 0; font-size: 0.72rem; color: var(--text-dim);">
                            Verify raw 16-bit 44.1kHz audio directly on PC to isolate from browser processing:
                        </p>

                        <div id="audio-conn-status" style="font-size: 0.72rem; margin-bottom: 0.5rem; padding: 6px 10px; border-radius: 6px; background: rgba(0,229,255,0.08); border: 1px solid rgba(0,229,255,0.2); color: var(--accent-cyan);">
                            <span id="audio-conn-label">${if (isCurrentConnectionUsb) "⚡ USB Tethering Mode ($effectiveIp:$serverPort)" else "📶 Wi-Fi LAN Mode ($effectiveIp:$serverPort)"}</span>
                        </div>

                        <div style="font-size: 0.72rem; color: var(--accent-cyan); font-weight: 600;">1. FFplay (Terminal low-latency one-liner):</div>
                        <div class="url-box" style="margin-bottom: 0.5rem;">
                            <span id="ffplay-cmd">ffplay -nodisp -autoexit -fflags nobuffer -flags low_delay "$defaultBase/audio.wav"</span>
                            <button class="btn-copy" onclick="copyUrl('ffplay-cmd')">📋</button>
                        </div>

                        <div style="font-size: 0.72rem; color: var(--accent-cyan); font-weight: 600;">2. VLC Player (Terminal command):</div>
                        <div class="url-box" style="margin-bottom: 0.5rem;">
                            <span id="vlc-cmd">vlc "$defaultBase/audio.wav" --network-caching=50</span>
                            <button class="btn-copy" onclick="copyUrl('vlc-cmd')">📋</button>
                        </div>

                        <div style="font-size: 0.72rem; color: var(--accent-cyan); font-weight: 600;">3. VLC Player (GUI Stream URL):</div>
                        <p style="margin: 0 0 0.2rem 0; font-size: 0.72rem; color: var(--text-muted);">
                            Open VLC &rarr; <em>Media</em> &rarr; <em>Open Network Stream</em> (Ctrl+N) &rarr; paste URL &rarr; check <em>Show more options</em> &rarr; set Caching to 50ms:
                        </p>
                        <div class="url-box" style="margin-bottom: 0.5rem;">
                            <span id="vlc-url-box">$defaultBase/audio.wav</span>
                            <button class="btn-copy" onclick="copyUrl('vlc-url-box')">📋</button>
                        </div>

                        <div id="alt-mode-hint" style="font-size: 0.7rem; color: var(--text-muted); margin-top: 0.4rem; padding: 6px 8px; border-left: 2px solid var(--accent-cyan); background: rgba(255,255,255,0.02);">
                        </div>
                    </div>
                </details>
            </div>

            <div class="control-section">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem; flex-wrap: wrap; gap: 6px;">
                    <div class="section-heading" style="margin-bottom: 0;">Direct Feeds (OBS / VLC / Zoom)</div>
                    ${if (isAuthEnabled) """
                    <div id="sidebar-auth-box" style="display: flex; align-items: center; gap: 6px; background: rgba(0, 229, 255, 0.08); border: 1px solid rgba(0, 229, 255, 0.25); padding: 4px 8px; border-radius: 6px;">
                        <label class="switch" style="width: 26px; height: 15px;" title="Toggle embedded credentials in stream URLs">
                            <input type="checkbox" id="toggle-embed-auth-sidebar" onchange="setAuthEmbed(this.checked)">
                            <span class="slider" style="border-radius: 15px;"></span>
                        </label>
                        <span style="font-size: 0.7rem; color: var(--accent-cyan); font-weight: 600;">🔐 Embed Auth</span>
                    </div>
                    """ else ""}
                </div>

                <div style="font-size: 0.75rem; color: var(--accent-cyan); font-weight: 600;">📹 Camera Feed Only (No Studio UI):</div>
                <div style="font-size: 0.7rem; color: var(--text-muted); margin-bottom: 4px;">Use this in OBS (Browser/Media Source), VLC, or FFmpeg:</div>
                <div class="url-box">
                    <span id="mjpeg-url">$defaultBase/video</span>
                    <button class="btn-copy" onclick="copyUrl('mjpeg-url')">📋</button>
                </div>
                
                <div style="font-size: 0.75rem; color: var(--accent-cyan); font-weight: 600; margin-top: 0.75rem;">🎙️ Audio Microphone Stream:</div>
                <div class="url-box">
                    <span id="audio-url">$defaultBase/audio.wav</span>
                    <button class="btn-copy" onclick="copyUrl('audio-url')">📋</button>
                </div>

                <div style="font-size: 0.75rem; color: var(--text-dim); margin-top: 0.75rem;">📸 Still Frame Snapshot:</div>
                <div class="url-box">
                    <span id="snapshot-url">$defaultBase/snapshot</span>
                    <button class="btn-copy" onclick="copyUrl('snapshot-url')">📋</button>
                </div>
            </div>
        </aside>
    </main>

    <script>
        const serverPort = $serverPort;
        const serverWifiIp = "$wifiIpStr";
        const serverUsbIp = "$usbIpStr";
        const serverIsUsb = $isCurrentConnectionUsb;
        const serverIsAuth = $isAuthEnabled;
        const serverAuthUser = "$safeUser";
        const serverAuthPass = "$safePass";

        const currentOrigin = window.location.origin;
        const currentHost = window.location.host;
        const currentHostname = window.location.hostname;
        const currentProtocol = window.location.protocol;

        // USB Tethering detection: check if server detected USB socket or if hostname matches USB Tethering IP
        const isUsb = serverIsUsb || (serverUsbIp && currentHostname === serverUsbIp);

        let isAuthEmbedded = serverIsAuth && (localStorage.getItem('ezcammic_embed_auth') === 'true');

        function setAuthEmbed(enabled) {
            isAuthEmbedded = !!enabled;
            try { localStorage.setItem('ezcammic_embed_auth', isAuthEmbedded ? 'true' : 'false'); } catch(e) {}
            
            const tHeader = document.getElementById('toggle-embed-auth');
            if (tHeader) tHeader.checked = isAuthEmbedded;
            const tSidebar = document.getElementById('toggle-embed-auth-sidebar');
            if (tSidebar) tSidebar.checked = isAuthEmbedded;

            updateAllUrls();
        }

        function getEffectiveBaseUrl() {
            if (isAuthEmbedded && serverIsAuth && serverAuthUser) {
                const userinfo = encodeURIComponent(serverAuthUser) + ':' + encodeURIComponent(serverAuthPass) + '@';
                return currentProtocol + '//' + userinfo + currentHost;
            }
            return currentProtocol + '//' + currentHost;
        }

        function getAuthQuery() {
            if (isAuthEmbedded && serverIsAuth && serverAuthUser) {
                return 'user=' + encodeURIComponent(serverAuthUser) + '&pass=' + encodeURIComponent(serverAuthPass);
            }
            const urlParams = new URLSearchParams(window.location.search);
            const user = urlParams.get('user') || urlParams.get('username');
            const pass = urlParams.get('pass') || urlParams.get('password');
            if (user && pass) {
                return 'user=' + encodeURIComponent(user) + '&pass=' + encodeURIComponent(pass);
            }
            return '';
        }

        function updateAllUrls() {
            const base = getEffectiveBaseUrl();
            const liveVideoUrl = base + '/video';
            const liveAudioUrl = base + '/audio.wav';
            const liveSnapshotUrl = base + '/snapshot';

            const mjpegElem = document.getElementById('mjpeg-url');
            if (mjpegElem) mjpegElem.innerText = liveVideoUrl;

            const audioElem = document.getElementById('audio-url');
            if (audioElem) audioElem.innerText = liveAudioUrl;

            const snapElem = document.getElementById('snapshot-url');
            if (snapElem) snapElem.innerText = liveSnapshotUrl;

            const ffplayElem = document.getElementById('ffplay-cmd');
            if (ffplayElem) ffplayElem.innerText = 'ffplay -nodisp -autoexit -fflags nobuffer -flags low_delay "' + liveAudioUrl + '"';

            const vlcCmdElem = document.getElementById('vlc-cmd');
            if (vlcCmdElem) vlcCmdElem.innerText = 'vlc "' + liveAudioUrl + '" --network-caching=50';

            const vlcUrlElem = document.getElementById('vlc-url-box');
            if (vlcUrlElem) vlcUrlElem.innerText = liveAudioUrl;

            const bannerVideoLink = document.getElementById('banner-video-link');
            if (bannerVideoLink) bannerVideoLink.href = liveVideoUrl;

            const audioConnLabel = document.getElementById('audio-conn-label');
            if (audioConnLabel) {
                if (isUsb) {
                    audioConnLabel.innerHTML = '⚡ <strong>USB Tethering Mode:</strong> ' + base;
                } else {
                    audioConnLabel.innerHTML = '📶 <strong>Wi-Fi LAN Mode:</strong> ' + base;
                }
            }

            const altModeHint = document.getElementById('alt-mode-hint');
            if (altModeHint) {
                if (isUsb) {
                    const wifiAuthPrefix = (isAuthEmbedded && serverIsAuth && serverAuthUser) ? (encodeURIComponent(serverAuthUser) + ':' + encodeURIComponent(serverAuthPass) + '@') : '';
                    const wifiTarget = serverWifiIp ? (currentProtocol + '//' + wifiAuthPrefix + serverWifiIp + ':' + serverPort) : 'the Wi-Fi IP on your phone';
                    altModeHint.innerHTML = '📶 <strong>Wi-Fi Alternative:</strong> To open this dashboard wirelessly instead, open <code>' + wifiTarget + '</code> in your PC browser (ensure phone and PC are on the same Wi-Fi network).';
                } else {
                    altModeHint.innerHTML = '⚡ <strong>USB Tethering Alternative:</strong> Connect USB cable, turn on <em>USB Tethering</em> in Android Settings, then open the USB Tethering IP for ultra-low wired latency.';
                }
            }
        }

        // Initialize toggle states
        const tHeaderInit = document.getElementById('toggle-embed-auth');
        if (tHeaderInit) tHeaderInit.checked = isAuthEmbedded;
        const tSidebarInit = document.getElementById('toggle-embed-auth-sidebar');
        if (tSidebarInit) tSidebarInit.checked = isAuthEmbedded;

        updateAllUrls();

        const connBadgeText = document.getElementById('conn-badge-text');
        const connModeText = document.getElementById('conn-mode-text');

        if (isUsb) {
            if (connBadgeText) connBadgeText.innerText = '⚡ USB TETHERING (HIGH SPEED)';
            if (connModeText) connModeText.innerText = '⚡ USB Tethering (' + currentHost + ')';
        } else {
            if (connBadgeText) connBadgeText.innerText = '📶 WI-FI LAN STREAM';
            if (connModeText) connModeText.innerText = '📶 Wi-Fi LAN (' + currentHost + ')';
        }

        function copyBannerVideoUrl() {
            const videoUrl = getEffectiveBaseUrl() + '/video';
            copyTextToClipboard(videoUrl, 'Raw /video URL');
        }

        let reloadTimer = null;
        function reloadStream() {
            if (reloadTimer) return;
            reloadTimer = setTimeout(() => {
                reloadTimer = null;
                const img = document.getElementById('stream-img');
                const authQuery = getAuthQuery();
                const sep = authQuery ? ('?' + authQuery + '&') : '?';
                img.src = '/video' + sep + 't=' + Date.now();
            }, 1000);
        }

        const streamImg = document.getElementById('stream-img');
        streamImg.onerror = reloadStream;

        let zoomTimer = null;
        function updateZoom(val) {
            document.getElementById('zoom-val').innerText = parseFloat(val).toFixed(1) + 'x';
            if (zoomTimer) clearTimeout(zoomTimer);
            zoomTimer = setTimeout(() => {
                sendCommand('zoom', val);
            }, 50);
        }

        function sendCommand(action, val) {
            const authQuery = getAuthQuery();
            let url = '/api/control?action=' + encodeURIComponent(action) + '&val=' + encodeURIComponent(val);
            if (authQuery) url += '&' + authQuery;
            fetch(url, { cache: 'no-store' })
                .then(res => {
                    if (!res.ok) console.warn('Command returned status:', res.status);
                    return res.json().catch(() => null);
                })
                .then(() => {
                    setTimeout(fetchStatus, 150);
                })
                .catch(err => console.error('Command failed:', err));
        }

        function copyUrl(elemId) {
            const elem = document.getElementById(elemId);
            if (!elem) return;
            const text = elem.innerText || elem.textContent;
            copyTextToClipboard(text, 'URL');
        }

        function copyTextToClipboard(text, label) {
            if (navigator.clipboard && window.isSecureContext) {
                navigator.clipboard.writeText(text).then(() => {
                    alert('Copied to clipboard: ' + text);
                }).catch(() => fallbackCopy(text));
            } else {
                fallbackCopy(text);
            }
        }

        function fallbackCopy(text) {
            const textArea = document.createElement("textarea");
            textArea.value = text;
            textArea.style.position = "fixed";
            textArea.style.left = "-999999px";
            document.body.appendChild(textArea);
            textArea.focus();
            textArea.select();
            try {
                document.execCommand('copy');
                alert('Copied to clipboard: ' + text);
            } catch (e) {
                prompt('Copy this URL:', text);
            }
            document.body.removeChild(textArea);
        }

        function fetchStatus() {
            const authQuery = getAuthQuery();
            let url = '/api/status';
            if (authQuery) url += '?' + authQuery;
            fetch(url, { cache: 'no-store' })
                .then(res => {
                    if (!res.ok) return null;
                    return res.json();
                })
                .then(data => {
                    if (!data) return;
                    const fpsVal = data.currentFps !== undefined ? data.currentFps : data.fps;
                    if (fpsVal !== undefined) {
                        document.getElementById('stat-fps').innerText = fpsVal.toFixed(1) + ' FPS';
                    }
                    if (data.bitrateKbps !== undefined) {
                        document.getElementById('stat-bitrate').innerText = data.bitrateKbps.toFixed(0) + ' Kbps';
                    }
                    if (data.activeResolution) {
                        let resText = data.activeResolution;
                        const parts = data.activeResolution.split('x');
                        if (parts.length === 2) {
                            const w = parseInt(parts[0], 10);
                            const h = parseInt(parts[1], 10);
                            if (w > h) {
                                resText += ' (Landscape)';
                            } else if (w < h) {
                                resText += ' (Portrait)';
                            } else {
                                resText += ' (Square)';
                            }
                        }
                        document.getElementById('stat-res').innerText = resText;
                    }

                    // Torch button state
                    const btnTorch = document.getElementById('btn-torch');
                    const torchLabel = document.getElementById('torch-label');
                    if (data.isTorchOn) {
                        btnTorch.classList.add('active');
                        torchLabel.innerText = '🔦 Torch ON';
                    } else {
                        btnTorch.classList.remove('active');
                        torchLabel.innerText = '🔦 Torch OFF';
                    }

                    // Mute button state
                    const btnMute = document.getElementById('btn-mute');
                    const muteLabel = document.getElementById('mute-label');
                    if (data.isMuted) {
                        btnMute.classList.add('active');
                        muteLabel.innerText = '🔇 Mic Muted';
                    } else {
                        btnMute.classList.remove('active');
                        muteLabel.innerText = '🎙️ Mic Active';
                    }

                    const img = document.getElementById('stream-img');
                    if (data.isMirrored) {
                        img.style.transform = 'scaleX(-1)';
                    } else {
                        img.style.transform = 'none';
                    }
                })
                .catch(() => {});
        }

        // Live stats polling
        fetchStatus();
        setInterval(fetchStatus, 1000);
    </script>
</body>
</html>
        """.trimIndent()
    }
}
