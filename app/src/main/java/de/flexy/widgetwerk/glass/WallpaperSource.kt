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
import android.graphics.Rect
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

    /** Pixel des Cache-Bilds pro Bildschirmpixel (das Cache-Bild hat genau Bildschirmhöhe). */
    fun cacheScale(ctx: Context, cacheBmp: Bitmap): Float = cacheBmp.height.toFloat() / screenSize(ctx).second

    /**
     * Linker Rand (in Cache-Pixeln) des sichtbaren Wallpaper-Ausschnitts auf Seite [page].
     * Ist das Bild breiter als der Bildschirm, verschiebt der Launcher es beim Seitenwechsel
     * (Parallaxe) – genau wie ImageWallpaper: xOffset = page / (pages - 1).
     */
    fun visibleLeft(ctx: Context, cacheBmp: Bitmap, page: Int, g: GlobalSettings = ConfigStore.global(ctx)): Float {
        val k = cacheScale(ctx, cacheBmp)
        val screenW = screenSize(ctx).first * k
        val excess = (cacheBmp.width - screenW).coerceAtLeast(0f)
        val f = if (g.pages > 1) page.coerceIn(0, g.pages - 1) / (g.pages - 1f) else 0.5f
        return (excess * f + g.offsetX * screenW * 0.25f).coerceIn(0f, excess)
    }

    /** Bereich hinter einem Widget (Bildschirm-Rechteck) im Cache-Bild. */
    fun regionFor(ctx: Context, cacheBmp: Bitmap, screen: Rect, page: Int): RectF {
        val k = cacheScale(ctx, cacheBmp)
        val left = visibleLeft(ctx, cacheBmp, page)
        return RectF(left + screen.left * k, screen.top * k, left + screen.right * k, screen.bottom * k)
    }

    /**
     * Setzt das gewählte Bild exakt bildschirmgroß als Wallpaper. Dann kann der Launcher es
     * beim Seitenwechsel nicht mehr verschieben → Glas passt auf jeder Seite, kein Nachwackeln.
     */
    fun lockAsWallpaper(ctx: Context): Boolean = runCatching {
        val src = BitmapFactory.decodeFile(File(ctx.filesDir, SRC).path) ?: return false
        val g = ConfigStore.global(ctx)
        val (sw, sh) = screenSize(ctx)
        val out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val scale = max(sw / src.width.toFloat(), sh / src.height.toFloat()) * g.zoom.coerceIn(1f, 2f)
        val dw = src.width * scale; val dh = src.height * scale
        val exX = dw - sw; val exY = dh - sh
        val left = -exX / 2 - g.offsetX * exX
        val top = -exY / 2 - g.offsetY * exY
        Canvas(out).drawBitmap(src, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        val wm = WallpaperManager.getInstance(ctx)
        wm.setBitmap(out, null, true, WallpaperManager.FLAG_SYSTEM)
        // Quelle = genau dieses Bild, keine Seiten mehr
        File(ctx.filesDir, SRC).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val id = runCatching { wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrDefault(-1)
        ConfigStore.updateGlobal(ctx) { it.copy(pages = 1, offsetX = 0f, offsetY = 0f, zoom = 1f, systemWallpaperId = id) }
        reprocess(ctx)
    }.getOrDefault(false)

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

    /**
     * Rohbild → Bildschirmhöhe (1/SCALE). Die Breite bleibt erhalten, wenn das Bild breiter als der
     * Bildschirm ist – den passenden Ausschnitt je Homescreen-Seite wählt [visibleLeft].
     */
    fun reprocess(ctx: Context, g: GlobalSettings = ConfigStore.global(ctx)): Boolean {
        val src = BitmapFactory.decodeFile(File(ctx.filesDir, SRC).path) ?: return false
        val (sw, sh) = screenSize(ctx)
        val cw = sw / SCALE; val ch = sh / SCALE
        val scale = max(cw / src.width.toFloat(), ch / src.height.toFloat()) * g.zoom.coerceIn(1f, 2f)
        val dw = src.width * scale; val dh = src.height * scale
        // Breite: Bildschirm, oder breiter wenn das Wallpaper über mehrere Seiten scrollt
        val outW = if (g.pages > 1) dw.coerceIn(cw.toFloat(), cw * 3f).toInt() else cw
        val out = Bitmap.createBitmap(outW, ch, Bitmap.Config.ARGB_8888)
        val exX = dw - outW; val exY = dh - ch
        val left = -exX / 2 - (if (g.pages > 1) 0f else g.offsetX * exX.coerceAtLeast(cw * 0.2f))
        val top = -exY / 2 - g.offsetY * exY.coerceAtLeast(ch * 0.2f)
        Canvas(out).drawBitmap(src, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        File(ctx.filesDir, PROC).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val stamp = System.currentTimeMillis()
        ConfigStore.updateGlobal(ctx) { it.copy(wallpaperStamp = stamp) }
        cache = Pair(stamp, out)
        return true
    }
}
