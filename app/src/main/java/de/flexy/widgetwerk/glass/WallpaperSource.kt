package de.flexy.widgetwerk.glass

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Environment
import android.view.WindowManager
import androidx.core.graphics.drawable.toBitmap
import de.flexy.widgetwerk.data.ConfigStore
import de.flexy.widgetwerk.data.GlobalSettings
import de.flexy.widgetwerk.data.WallpaperMode
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Liefert das Hintergrundbild so, wie es auf dem Homescreen liegt (Bildschirmformat, 1/3 Auflösung).
 * Widgets schneiden daraus ihren Bereich aus und blurren ihn → "echtes" Glas.
 */
object WallpaperSource {
    private const val SCALE = 3                // Bildschirmpixel pro Cache-Pixel
    private const val SRC = "wall_src.jpg"
    private const val PROC = "wall_proc.jpg"

    @Volatile private var cache: Pair<Long, Bitmap>? = null

    /** Bildschirmgröße in Pixeln (Hochformat). */
    fun screenSize(ctx: Context): Pair<Int, Int> {
        val b = ctx.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        return Pair(min(b.width(), b.height()), max(b.width(), b.height()))
    }

    /** Verarbeitetes Wallpaper (Bildschirmformat / SCALE) oder null. */
    fun get(ctx: Context): Bitmap? {
        val g = ConfigStore.global(ctx)
        if (g.wallpaperMode == WallpaperMode.NONE) return null
        cache?.let { if (it.first == g.wallpaperStamp) return it.second }
        val f = File(ctx.filesDir, PROC)
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.path) ?: return null
        cache = Pair(g.wallpaperStamp, bmp)
        return bmp
    }

    /** Pixel des Cache-Bilds pro Bildschirmpixel. */
    fun cacheScale(ctx: Context, cacheBmp: Bitmap): Float = cacheBmp.width.toFloat() / screenSize(ctx).first

    /** Ein vom Nutzer gewähltes Bild als Wallpaper-Quelle übernehmen. */
    fun importPicked(ctx: Context, uri: Uri): Boolean {
        val bmp = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = info.size
                val longest = max(s.width, s.height)
                if (longest > 2400) dec.setTargetSampleSize(longest / 2400 + 1)
            }
        }.getOrNull() ?: return false
        File(ctx.filesDir, SRC).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        ConfigStore.updateGlobal(ctx) { it.copy(wallpaperMode = WallpaperMode.PICKED) }
        return reprocess(ctx)
    }

    /** Kann die App das System-Wallpaper lesen? (ab Android 13 nur mit "Zugriff auf alle Dateien") */
    fun canReadSystem(ctx: Context): Boolean =
        Environment.isExternalStorageManager() ||
            ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** System-Wallpaper als Quelle verwenden. */
    fun useSystem(ctx: Context): Boolean {
        val bmp = readSystem(ctx) ?: return false
        File(ctx.filesDir, SRC).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val id = runCatching { WallpaperManager.getInstance(ctx).getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrDefault(-1)
        ConfigStore.updateGlobal(ctx) { it.copy(wallpaperMode = WallpaperMode.SYSTEM, systemWallpaperId = id) }
        return reprocess(ctx)
    }

    private fun readSystem(ctx: Context): Bitmap? = runCatching {
        val wm = WallpaperManager.getInstance(ctx)
        if (wm.wallpaperInfo != null) return@runCatching null // Live-Wallpaper: kein Bild verfügbar
        @Suppress("MissingPermission")
        val d = wm.drawable ?: return@runCatching null
        val longest = max(d.intrinsicWidth, d.intrinsicHeight).coerceAtLeast(1)
        val f = min(1f, 2400f / longest)
        d.toBitmap((d.intrinsicWidth * f).toInt().coerceAtLeast(1), (d.intrinsicHeight * f).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    }.getOrNull()

    /** Prüft im Hintergrund, ob sich das System-Wallpaper geändert hat. true = neu verarbeitet. */
    fun refreshSystemIfChanged(ctx: Context): Boolean {
        val g = ConfigStore.global(ctx)
        if (g.wallpaperMode != WallpaperMode.SYSTEM) return false
        val id = runCatching { WallpaperManager.getInstance(ctx).getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrDefault(-1)
        if (id == g.systemWallpaperId && id != -1) return false
        return useSystem(ctx)
    }

    fun disable(ctx: Context) {
        ConfigStore.updateGlobal(ctx) { it.copy(wallpaperMode = WallpaperMode.NONE, wallpaperStamp = System.currentTimeMillis()) }
        cache = null
    }

    /** Rohbild → Bildschirmformat (Center-Crop + Zoom/Versatz). */
    fun reprocess(ctx: Context, g: GlobalSettings = ConfigStore.global(ctx)): Boolean {
        val src = BitmapFactory.decodeFile(File(ctx.filesDir, SRC).path) ?: return false
        val (sw, sh) = screenSize(ctx)
        val cw = sw / SCALE; val ch = sh / SCALE
        val out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val scale = max(cw / src.width.toFloat(), ch / src.height.toFloat()) * g.zoom.coerceIn(1f, 2f)
        val dw = src.width * scale; val dh = src.height * scale
        val exX = dw - cw; val exY = dh - ch
        val left = -exX / 2 - g.offsetX * exX.coerceAtLeast(cw * 0.2f)
        val top = -exY / 2 - g.offsetY * exY.coerceAtLeast(ch * 0.2f)
        Canvas(out).drawBitmap(src, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        File(ctx.filesDir, PROC).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val stamp = System.currentTimeMillis()
        ConfigStore.updateGlobal(ctx) { it.copy(wallpaperStamp = stamp) }
        cache = Pair(stamp, out)
        return true
    }
}
