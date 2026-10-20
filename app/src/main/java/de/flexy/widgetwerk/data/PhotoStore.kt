package de.flexy.widgetwerk.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Fotos werden beim Auswählen verkleinert in den App-Speicher kopiert.
 * So funktionieren auch Google-Photos-Bilder aus der Cloud dauerhaft und offline.
 */
object PhotoStore {
    private const val MAX_SIDE = 1600

    private fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }

    fun file(ctx: Context, name: String) = File(dir(ctx), name)

    /** Kopiert ein Bild; liefert den Dateinamen oder null. */
    fun importPhoto(ctx: Context, uri: Uri): String? = runCatching {
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val f = MAX_SIDE.toFloat() / longest
                dec.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
            }
        }
        val name = UUID.randomUUID().toString() + ".jpg"
        file(ctx, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        name
    }.getOrNull()

    fun load(ctx: Context, name: String, maxSide: Int = 1200): Bitmap? {
        val f = file(ctx, name)
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun deleteAll(ctx: Context, names: List<String>) = names.forEach { file(ctx, it).delete() }
}
