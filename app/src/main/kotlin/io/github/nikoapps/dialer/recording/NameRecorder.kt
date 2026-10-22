package io.github.nikoapps.dialer.recording

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/** How a recording ended. */
sealed interface RecordResult {
    /** Saved as a WAV file at the path asked for. */
    data class Done(val durationMs: Int) : RecordResult

    /** Stopped before anything was said. Nothing was saved. */
    data object TooShort : RecordResult

    /** The microphone couldn't be used, such as when another app holds it. Nothing was saved. */
    data object Failed : RecordResult
}

/**
 * Records one name from the microphone as mono 16-bit PCM, then writes it as a WAV file with
 * silence after it. [stop] ends it and keeps what was said; it also ends by itself after
 * [MAX_RECORDING_MS]. Cancelling the coroutine ends it and keeps nothing. The microphone is
 * always released when it ends.
 */
class NameRecorder {
    private val stopRequested = AtomicBoolean(false)

    /**
     * Clears an earlier Stop. Call it right before launching [record], not inside the coroutine,
     * so a Stop tapped straight after Record, before the recording is under way, still counts.
     */
    fun reset() {
        stopRequested.set(false)
    }

    /** Ends the recording in progress, or the one about to start, and keeps what was said. */
    fun stop() {
        stopRequested.set(true)
    }

    // Callers check the microphone permission first; a missing one ends as Failed.
    @SuppressLint("MissingPermission")
    suspend fun record(target: File): RecordResult = withContext(Dispatchers.IO) {
        // 44.1 kHz works on every Android phone; 16 kHz is plenty for a voice if it doesn't.
        val rate = SAMPLE_RATES.firstOrNull { minBuffer(it) > 0 } ?: return@withContext RecordResult.Failed
        // About a tenth of a second per read, so Stop is quick.
        val chunk = max(minBuffer(rate), Wav.bytesFor(100, rate))
        val recorder = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, chunk * 2)
        } catch (_: IllegalArgumentException) {
            return@withContext RecordResult.Failed
        } catch (_: SecurityException) {
            return@withContext RecordResult.Failed
        }
        val pcm = ByteArray(Wav.bytesFor(MAX_RECORDING_MS, rate))
        var size = 0
        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) return@withContext RecordResult.Failed
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) return@withContext RecordResult.Failed
            while (size < pcm.size && !stopRequested.get()) {
                ensureActive()
                val read = recorder.read(pcm, size, min(chunk, pcm.size - size))
                if (read < 0) return@withContext RecordResult.Failed
                size += read
            }
        } catch (_: IllegalStateException) {
            return@withContext RecordResult.Failed
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        ensureActive()
        if (Wav.durationMs(size, rate) < MIN_RECORDING_MS) return@withContext RecordResult.TooShort
        if (!writeWav(target, pcm, size, rate)) return@withContext RecordResult.Failed
        RecordResult.Done(Wav.durationMs(size, rate))
    }

    private fun minBuffer(rate: Int): Int =
        AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

    /** Written to a temporary file first, so a half-written recording is never played. */
    private fun writeWav(target: File, pcm: ByteArray, size: Int, rate: Int): Boolean = runCatching {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.outputStream().buffered().use { Wav.write(it, pcm, size, rate, SILENCE_MS) }
        if (!temp.renameTo(target)) {
            temp.delete()
            return@runCatching false
        }
        true
    }.getOrDefault(false)

    private companion object {
        val SAMPLE_RATES = listOf(44_100, 16_000)
    }
}
