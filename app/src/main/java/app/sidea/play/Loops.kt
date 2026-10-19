package app.sidea.play

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.sidea.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/** A recorded texture, read into memory: stereo 16-bit frames at [rate]. */
class Loop(val samples: ShortArray, val rate: Int) {
    val frames: Int get() = samples.size / 2
}

/**
 * The two real recordings (see docs/SOUNDS.md): vinyl crackle and tape hiss, each a seamless loop.
 * Loaded on first use and kept.
 */
object Loops {
    @Volatile private var vinyl: Loop? = null
    @Volatile private var tape: Loop? = null

    fun get(c: Context, isTape: Boolean): Loop = if (isTape) tape ?: read(c, R.raw.tape_hiss).also { tape = it } else vinyl ?: read(c, R.raw.vinyl_crackle).also { vinyl = it }

    /** Reads a 16-bit stereo WAV: finds the format and data chunks, whatever else is in the file. */
    private fun read(c: Context, res: Int): Loop {
        val bytes = c.resources.openRawResource(res).use { it.readBytes() }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var rate = 32_000
        var at = 12
        while (at + 8 <= bytes.size) {
            val id = String(bytes, at, 4, Charsets.US_ASCII)
            val len = b.getInt(at + 4)
            if (id == "fmt ") rate = b.getInt(at + 12)
            if (id == "data") {
                val n = minOf(len, bytes.size - at - 8) / 2
                val out = ShortArray(n)
                b.position(at + 8)
                b.asShortBuffer().get(out, 0, n)
                return Loop(out, rate)
            }
            at += 8 + len + (len and 1)
        }
        return Loop(ShortArray(2), rate)
    }
}

/**
 * The recording laid over music that plays in another app. The system mixes the two; this doesn't
 * ask for audio focus, so the other player keeps playing.
 */
class Overlay(private val context: Context) {
    @Volatile private var running = false
    @Volatile var gain = 0f
    private var worker: Thread? = null
    private var current: Boolean? = null

    /** Plays the vinyl (or tape) loop until [stop]. */
    fun start(isTape: Boolean) {
        if (running && current == isTape) return
        stop()
        current = isTape
        running = true
        val loop = Loops.get(context, isTape)
        worker = thread(name = "side-a-overlay", isDaemon = true) {
            val size = AudioTrack.getMinBufferSize(loop.rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(8192)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(loop.rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(size)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            val chunk = ShortArray(2048)
            var at = 0
            var g = 0f
            try {
                track.play()
                while (running) {
                    for (i in chunk.indices) {
                        // Ease towards the wanted level so changes never click.
                        g += (gain - g) * 0.0005f
                        chunk[i] = (loop.samples[at] * g).toInt().coerceIn(-32768, 32767).toShort()
                        at = (at + 1) % loop.samples.size
                    }
                    track.write(chunk, 0, chunk.size)
                }
            } finally {
                track.pause()
                track.flush()
                track.release()
            }
        }
    }

    fun stop() {
        running = false
        worker?.join(500)
        worker = null
        current = null
    }
}
