package io.github.nikoapps.dialer.recording

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavTest {

    private fun write(pcm: ByteArray, length: Int = pcm.size, rate: Int = 16_000, silenceMs: Int = SILENCE_MS): ByteArray =
        ByteArrayOutputStream().also { Wav.write(it, pcm, length, rate, silenceMs) }.toByteArray()

    private fun ByteArray.le() = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    private fun ByteArray.ascii(at: Int) = String(this, at, 4, Charsets.US_ASCII)

    @Test
    fun `the header says mono 16-bit PCM at the recording's rate`() {
        val file = write(ByteArray(3200) { 1 }, rate = 44_100)
        val b = file.le()
        assertEquals("RIFF", file.ascii(0))
        assertEquals("WAVE", file.ascii(8))
        assertEquals("fmt ", file.ascii(12))
        assertEquals(16, b.getInt(16))
        assertEquals(1, b.getShort(20).toInt()) // PCM
        assertEquals(1, b.getShort(22).toInt()) // mono
        assertEquals(44_100, b.getInt(24))
        assertEquals(88_200, b.getInt(28)) // byte rate
        assertEquals(2, b.getShort(32).toInt()) // block align
        assertEquals(16, b.getShort(34).toInt())
        assertEquals("data", file.ascii(36))
    }

    @Test
    fun `sizes in the header match the file`() {
        val file = write(ByteArray(3200) { 1 }, rate = 16_000)
        val b = file.le()
        val silence = 16_000 * 2 * 3 / 2 // 1.5 s of 16-bit mono
        assertEquals(Wav.HEADER_BYTES + 3200 + silence, file.size)
        assertEquals(file.size - 8, b.getInt(4))
        assertEquals(3200 + silence, b.getInt(40))
    }

    @Test
    fun `the sound comes first, then about 1,5 seconds of silence`() {
        val pcm = ByteArray(1000) { (it % 100 + 1).toByte() }
        val file = write(pcm, rate = 16_000)
        assertArrayEquals(pcm, file.copyOfRange(Wav.HEADER_BYTES, Wav.HEADER_BYTES + 1000))
        val tail = file.copyOfRange(Wav.HEADER_BYTES + 1000, file.size)
        assertEquals(Wav.bytesFor(1500, 16_000), tail.size)
        assertEquals(1500, Wav.durationMs(tail.size, 16_000))
        assertTrue(tail.all { it == 0.toByte() })
    }

    @Test
    fun `only the recorded part of the buffer is written, in whole samples`() {
        val pcm = ByteArray(5000) { 7 }
        val file = write(pcm, length = 1001, silenceMs = 0)
        assertEquals(Wav.HEADER_BYTES + 1000, file.size)
        assertEquals(1000, file.le().getInt(40))
    }

    @Test
    fun `write reports the bytes it wrote`() {
        val out = ByteArrayOutputStream()
        val written = Wav.write(out, ByteArray(400), sampleRate = 44_100, silenceMs = 1500)
        assertEquals(out.size(), written)
    }

    @Test
    fun `durations and sizes agree`() {
        assertEquals(88_200, Wav.bytesFor(1000, 44_100))
        assertEquals(1000, Wav.durationMs(88_200, 44_100))
        assertEquals(Wav.bytesFor(MAX_RECORDING_MS, 16_000), 160_000)
    }
}
