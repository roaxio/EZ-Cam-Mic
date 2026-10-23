package io.github.roaxio.ezcammic.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt
import io.github.roaxio.ezcammic.server.DynamicResourceBudgets

class AudioStreamManager {

    val maxStreamingClients: Int
        get() = DynamicResourceBudgets.computeMaxStreamingAudioClients()

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _audioLevelRms = MutableStateFlow(0f)
    val audioLevelRms: StateFlow<Float> = _audioLevelRms.asStateFlow()

    private class ClientSession(
        val outputStream: OutputStream,
        val queue: ArrayBlockingQueue<ByteArray> = ArrayBlockingQueue(64),
        var job: Job? = null
    )

    private val clientSessions = ConcurrentHashMap<OutputStream, ClientSession>()

    var isMuted: Boolean = false
        set(value) {
            val changed = field != value
            field = value
            if (changed) {
                handleMuteStateChanged(value)
            }
        }

    var gain: Float = 1.0f
    var sampleRate: Int = 44100
        private set

    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

    fun setSamplingRate(newRate: Int) {
        if (sampleRate != newRate) {
            sampleRate = newRate
            if (isRecording) {
                stop()
                start(sampleRate)
            }
        }
    }

    @Synchronized
    private fun handleMuteStateChanged(muted: Boolean) {
        if (muted) {
            // Immediately release hardware microphone so Android OS privacy indicator turns off
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {}
            audioRecord = null
            try {
                noiseSuppressor?.release()
                echoCanceler?.release()
            } catch (e: Exception) {}
            noiseSuppressor = null
            echoCanceler = null
            _audioLevelRms.value = 0f
        } else {
            // Re-acquire hardware microphone when unmuted
            if (isRecording && audioRecord == null) {
                initAudioRecord()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initAudioRecord(): Boolean {
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                channelConfig,
                audioEncoding
            )
            val bufferSize = maxOf(minBufferSize * 4, 16384)

            // Primary: High-fidelity standard MIC (avoids CAMCORDER beamforming DSP phase distortion)
            var record: AudioRecord? = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioEncoding,
                    bufferSize
                )
            } catch (e: Exception) {
                null
            }

            // Fallback: VOICE_RECOGNITION (clean acoustic tuning)
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record?.release()
                record = try {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        sampleRate,
                        channelConfig,
                        audioEncoding,
                        bufferSize
                    )
                } catch (e: Exception) {
                    null
                }
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record?.release()
                audioRecord = null
                return false
            }

            audioRecord = record

            try {
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply {
                        enabled = true
                    }
                }
            } catch (e: Exception) {}

            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.apply {
                        enabled = true
                    }
                }
            } catch (e: Exception) {}

            record.startRecording()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(sampleRateInHz: Int = 44100) {
        if (isRecording) return
        this.sampleRate = sampleRateInHz
        isRecording = true

        if (!isMuted) {
            initAudioRecord()
        }

        recordingJob = scope.launch {
            // 1024 samples ~ 23.2ms at 44.1kHz - robust hardware chunking with imperceptible latency
            val buffer = ShortArray(1024)
            val byteBuffer = ByteBuffer.allocate(buffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            var lastVuDispatchTime = 0L

            while (isActive && isRecording) {
                if (isMuted || audioRecord == null) {
                    // Microphone hardware is completely released; stream pure digital silence chunks at exact sample rate timing
                    // so connected clients (OBS, VLC, Web) maintain stable streaming without disconnection
                    kotlinx.coroutines.delay(23L)
                    if (isRecording && isMuted) {
                        val silenceBytes = ByteArray(1024 * 2)
                        broadcastAudio(silenceBytes)
                        _audioLevelRms.value = 0f
                    }
                } else {
                    val record = audioRecord
                    val readSamples = record?.read(buffer, 0, buffer.size) ?: 0
                    if (readSamples > 0) {
                        // Calculate RMS level for VU meter
                        var sum = 0.0
                        for (i in 0 until readSamples) {
                            val sample = buffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / readSamples) / 32768.0
                        val rawRms = (rms.toFloat() * gain).coerceIn(0f, 1f)

                        // Decouple presentation dispatch from high-frequency audio sampling (Rule 22.3)
                        val now = System.currentTimeMillis()
                        if (now - lastVuDispatchTime >= 66L || abs(rawRms - _audioLevelRms.value) >= 0.15f) {
                            lastVuDispatchTime = now
                            _audioLevelRms.value = rawRms
                        }

                        // Process buffer (Gain)
                        byteBuffer.clear()
                        for (i in 0 until readSamples) {
                            var sample = buffer[i].toInt()
                            if (gain != 1.0f) {
                                sample = (sample * gain).toInt().coerceIn(-32768, 32767)
                            }
                            byteBuffer.putShort(sample.toShort())
                        }

                        val bytesLength = readSamples * 2
                        val bytesCopy = byteBuffer.array().copyOf(bytesLength)

                        // Broadcast to live client queues
                        broadcastAudio(bytesCopy)
                    }
                }
            }
        }
    }

    private fun broadcastAudio(data: ByteArray) {
        if (clientSessions.isEmpty()) return
        for (session in clientSessions.values) {
            // Live-edge invariant: if queue has 8 chunks (~185ms), drop oldest so client NEVER experiences buffering lag
            if (!session.queue.offer(data)) {
                session.queue.poll()
                session.queue.offer(data)
            }
        }
    }

    fun registerAudioClient(outputStream: OutputStream): Boolean {
        if (clientSessions.size >= maxStreamingClients) {
            return false
        }
        try {
            if (!isRecording) {
                start(sampleRate)
            }
            // Send initial WAV header
            val header = createWavHeader(sampleRate, 1, 16)
            outputStream.write(header)
            outputStream.flush()

            val session = ClientSession(outputStream)
            session.job = scope.launch {
                try {
                    while (isActive && isRecording && clientSessions.containsKey(outputStream)) {
                        val chunk = session.queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                        outputStream.write(chunk)
                        outputStream.flush()
                    }
                } catch (e: Exception) {
                    // Client disconnected or socket terminated
                } finally {
                    unregisterAudioClient(outputStream)
                }
            }
            clientSessions[outputStream] = session
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun unregisterAudioClient(outputStream: OutputStream) {
        val session = clientSessions.remove(outputStream)
        session?.job?.cancel()
        session?.queue?.clear()
    }

    fun isAudioClientActive(outputStream: OutputStream): Boolean {
        return clientSessions.containsKey(outputStream)
    }

    fun getConnectedClientsCount(): Int {
        return clientSessions.size
    }

    @Synchronized
    fun stop() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        for ((_, session) in clientSessions) {
            session.job?.cancel()
            session.queue.clear()
        }
        clientSessions.clear()

        try {
            noiseSuppressor?.release()
        } catch (e: Exception) {}
        noiseSuppressor = null

        try {
            echoCanceler?.release()
        } catch (e: Exception) {}
        echoCanceler = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
        _audioLevelRms.value = 0f
    }

    fun release() {
        stop()
    }

    /**
     * Builds a standard 44-byte WAV header configured for streaming.
     */
    private fun createWavHeader(sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val totalAudioLen = 0x7FFFFFF0 // Max int size for continuous stream
        val totalDataLen = totalAudioLen + 36

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 16 for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // Audio format 1 = PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = blockAlign.toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        return header
    }
}
