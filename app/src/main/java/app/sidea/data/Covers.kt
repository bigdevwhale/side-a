package app.sidea.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Sleeve pictures, kept as small JPEGs in the app's own folder so they survive the source going away. */
object Covers {
    private const val SIDE = 800
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun dir(c: Context) = File(c.filesDir, "covers").apply { mkdirs() }

    /** Saves [image] square-cropped and scaled down; returns the file name for [Record.cover]. */
    fun save(c: Context, image: Bitmap): String {
        val side = minOf(image.width, image.height)
        val square = Bitmap.createBitmap(image, (image.width - side) / 2, (image.height - side) / 2, side, side)
        val scaled = if (side > SIDE) Bitmap.createScaledBitmap(square, SIDE, SIDE, true) else square
        val name = "${UUID.randomUUID()}.jpg"
        File(dir(c), name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        cache.put(name, scaled)
        return name
    }

    fun file(c: Context, name: String) = File(dir(c), name)

    fun load(c: Context, name: String): Bitmap? = cache.get(name) ?: run {
        val f = file(c, name)
        if (!f.exists()) return null
        BitmapFactory.decodeFile(f.path)?.also { cache.put(name, it) }
    }

    fun delete(c: Context, name: String?) {
        if (name == null) return
        cache.remove(name)
        file(c, name).delete()
    }

    /** Decodes picture bytes no bigger than needed for a sleeve. */
    fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIDE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
