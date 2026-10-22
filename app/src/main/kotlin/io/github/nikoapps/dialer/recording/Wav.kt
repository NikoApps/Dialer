package io.github.nikoapps.dialer.recording

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Plain PCM WAV files, which every phone's ringer can play. Pure, so the header and the silence
 * are tested on the JVM.
 */
object Wav {
    const val HEADER_BYTES = 44
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    private const val BYTES_PER_FRAME = CHANNELS * BITS_PER_SAMPLE / 8

    /** The bytes of [ms] milliseconds of sound at [sampleRate], rounded down to whole samples. */
    fun bytesFor(ms: Int, sampleRate: Int): Int = (sampleRate.toLong() * ms / 1000).toInt() * BYTES_PER_FRAME

    /** How long [pcmBytes] of sound at [sampleRate] lasts, in milliseconds. */
    fun durationMs(pcmBytes: Int, sampleRate: Int): Int = (pcmBytes.toLong() / BYTES_PER_FRAME * 1000 / sampleRate).toInt()

    /** The 44 byte RIFF header for [dataBytes] of mono 16-bit PCM at [sampleRate]. */
    fun header(dataBytes: Int, sampleRate: Int): ByteArray = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt(36 + dataBytes)
        put("WAVE".toByteArray(Charsets.US_ASCII))
        put("fmt ".toByteArray(Charsets.US_ASCII))
        putInt(16) // fmt chunk size for PCM
        putShort(1) // PCM
        putShort(CHANNELS.toShort())
        putInt(sampleRate)
        putInt(sampleRate * BYTES_PER_FRAME) // byte rate
        putShort(BYTES_PER_FRAME.toShort()) // block align
        putShort(BITS_PER_SAMPLE.toShort())
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(dataBytes)
    }.array()

    /**
     * Writes [length] bytes of [pcm] (little-endian 16-bit mono at [sampleRate]) as a WAV file, with
     * [silenceMs] of silence after it, so a looping ringtone reads "Mum is calling ... Mum is calling".
     * An odd last byte is dropped so the file holds whole samples. Returns the bytes written.
     */
    fun write(out: OutputStream, pcm: ByteArray, length: Int = pcm.size, sampleRate: Int, silenceMs: Int): Int {
        val sound = length.coerceIn(0, pcm.size) / BYTES_PER_FRAME * BYTES_PER_FRAME
        val silence = bytesFor(silenceMs.coerceAtLeast(0), sampleRate)
        out.write(header(sound + silence, sampleRate))
        out.write(pcm, 0, sound)
        val zeros = ByteArray(minOf(silence, 8192))
        var left = silence
        while (left > 0) {
            val n = minOf(left, zeros.size)
            out.write(zeros, 0, n)
            left -= n
        }
        return HEADER_BYTES + sound + silence
    }
}
