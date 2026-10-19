package app.sidea.play

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.sidea.domain.Needle
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * The medium itself, between the file and the speaker, and it also reads the levels for the VU meters
 * on the way through.
 *
 * - **Vinyl:** a little surface hiss, the odd crackle that grows with wear, and a click each time round
 *   when the needle crosses a scratch.
 * - **Tape:** a steady hiss, wow and flutter that grow as the tape stretches, and a short dropout where
 *   the tape was creased.
 *
 * With [loop] set, a real recording of crackle or hiss replaces the made-up hiss and pops.
 */
@UnstableApi
class Surface : BaseAudioProcessor() {
    /** How much of the medium to hear, 0 (clean) to 1. */
    @Volatile var amount = 0.5f

    /** Wear, 0 to 1: more crackle, or more wobble, the more it's been loved. */
    @Volatile var wear = 0f

    @Volatile var tape = false

    /** A real recording of the surface to lay under the music instead of the made-up hiss and pops. */
    @Volatile var loop: Loop? = null

    /** Peaks of the last buffer, 0 to 1, left and right. */
    @Volatile var left = 0f
        private set
    @Volatile var right = 0f
        private set

    @Volatile private var asked = 0

    private val rnd = Random(33)
    private var channels = 2
    private var rate = 44_100
    private var hiss = 0f
    private var pop = 0f
    private var sign = 1f
    private var clicks = 0
    private var untilClick = 0

    // Tape: a short delay line whose length sways makes the pitch waver.
    private var line = FloatArray(0)
    private var write = 0
    private var phaseWow = 0.0
    private var phaseFlutter = 0.0
    private var dropout = 0f
    private var loopAt = 0.0

    /** The needle is about to cross a scratch (or the tape a crease): [n] clicks, once a revolution. */
    fun click(n: Int = Needle.CLICKS) {
        asked = n
    }

    fun quiet() {
        left = 0f
        right = 0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun onFlush() {
        pop = 0f
        line = FloatArray(LINE * channels)
        write = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val a = amount
        val w = wear
        val isTape = tape
        if (line.size != LINE * channels) line = FloatArray(LINE * channels)
        if (asked > 0) {
            clicks = asked
            asked = 0
            untilClick = 0
        }
        // Vinyl: hiss and pops, about one a second on a new record and a dozen on a worn one.
        val hissLevel = a * if (isTape) 0.0035f + 0.003f * w else 0.0016f + 0.004f * w
        val popChance = if (isTape) 0f else a * (0.00003f + 0.00026f * w) * 44_100f / rate
        val turn = (Needle.TURN_MS * rate / 1000).toInt()
        // Tape: slow wow (0.6 Hz) and quick flutter (9 Hz), in frames of delay swing.
        val wowDepth = a * (0.00025 + 0.0018 * w) * rate
        val flutterDepth = a * (0.00003 + 0.00012 * w) * rate
        val wowStep = 2 * PI * 0.6 / rate
        val flutterStep = 2 * PI * 9.0 / rate
        val base = wowDepth + flutterDepth + 2
        val rec = loop?.takeIf { a > 0f && it.frames > 1 }
        val recStep = if (rec != null) rec.rate.toDouble() / rate else 0.0
        // The recording is levelled to RMS 0.1; under the music it sits well below that, and rises with wear.
        val recGain = a * (if (isTape) 0.18f + 0.2f * w else 0.14f + 0.26f * w) / 32768f

        var peakL = 0f
        var peakR = 0f
        val frames = size / (2 * channels)
        for (f in 0 until frames) {
            var add = 0f
            var gain = 1f
            var recL = 0f
            var recR = 0f
            if (rec != null) {
                val i = loopAt.toInt() % rec.frames
                recL = rec.samples[2 * i] * recGain
                recR = rec.samples[2 * i + 1] * recGain
                loopAt += recStep
                if (loopAt >= rec.frames) loopAt -= rec.frames
            }
            if (a > 0f) {
                hiss = hiss * 0.55f + (rnd.nextFloat() - 0.5f) * 0.45f
                if (rec == null) add = hiss * hissLevel * 2f
                if (rec == null && !isTape && pop < 0.0005f && rnd.nextFloat() < popChance) {
                    val r = rnd.nextFloat()
                    pop = (0.008f + r * r * r * 0.09f) * (0.5f + w) * a
                    sign = if (rnd.nextBoolean()) 1f else -1f
                }
                if (clicks > 0 && --untilClick <= 0) {
                    clicks--
                    untilClick = turn
                    if (isTape) dropout = 1f else pop = 0.32f * a
                }
                if (pop >= 0.0005f) {
                    add += pop * sign * (0.45f + rnd.nextFloat() * 0.55f)
                    sign = -sign
                    pop *= 0.8f
                }
                if (dropout > 0.001f) {
                    // A crease: the sound sinks and comes back muffled for a moment.
                    gain = 1f - 0.75f * a * sin(dropout * PI.toFloat())
                    dropout -= 1f / (0.18f * rate)
                }
            }
            var delay = 0.0
            if (isTape && a > 0f) {
                phaseWow += wowStep
                phaseFlutter += flutterStep
                if (phaseWow > 2 * PI) phaseWow -= 2 * PI
                if (phaseFlutter > 2 * PI) phaseFlutter -= 2 * PI
                delay = base + wowDepth * sin(phaseWow) + flutterDepth * sin(phaseFlutter)
            }
            for (ch in 0 until channels) {
                var s = inputBuffer.getShort() / 32768f
                if (isTape && a > 0f) {
                    line[write * channels + ch] = s
                    val pos = write - delay
                    val whole = floor(pos)
                    val i0 = Math.floorMod(whole.toInt(), LINE)
                    val frac = (pos - whole).toFloat()
                    val i1 = (i0 + 1) % LINE
                    s = line[i0 * channels + ch] * (1 - frac) + line[i1 * channels + ch] * frac
                }
                s *= gain
                val m = abs(s)
                if (ch == 0) { if (m > peakL) peakL = m } else if (m > peakR) peakR = m
                val r = if (ch == 0) recL else if (ch == 1) recR else 0f
                out.putShort(((s + add + if (channels == 1) (recL + recR) / 2 else r).coerceIn(-1f, 0.99997f) * 32768f).toInt().toShort())
            }
            if (isTape && a > 0f) write = (write + 1) % LINE
        }
        // Whatever doesn't make a whole frame passes through untouched.
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        left = peakL
        right = if (channels == 1) peakL else peakR
        out.flip()
    }

    override fun onReset() {
        pop = 0f
        clicks = 0
        dropout = 0f
        quiet()
    }

    private companion object {
        /** Frames of delay line: far more than the deepest wow needs. */
        const val LINE = 4096
    }
}
