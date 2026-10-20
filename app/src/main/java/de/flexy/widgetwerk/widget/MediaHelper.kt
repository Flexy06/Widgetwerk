package de.flexy.widgetwerk.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings

/** Zugriff auf laufende Mediensitzungen (benötigt Benachrichtigungszugriff für [LiveService]). */
object MediaHelper {

    fun hasAccess(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(ctx, LiveService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun controllers(ctx: Context): List<MediaController> = runCatching {
        ctx.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(ctx, LiveService::class.java))
    }.getOrDefault(emptyList())

    /** Die "wichtigste" Sitzung: spielend vor pausiert. */
    fun active(ctx: Context): MediaController? {
        val list = controllers(ctx)
        return list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
    }

    fun isPlaying(c: MediaController?) = c?.playbackState?.state == PlaybackState.STATE_PLAYING ||
        c?.playbackState?.state == PlaybackState.STATE_BUFFERING

    fun art(c: MediaController?): Bitmap? {
        val m = c?.metadata ?: return null
        return m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }

    fun title(c: MediaController?): String? = c?.metadata?.let {
        it.getString(MediaMetadata.METADATA_KEY_TITLE) ?: it.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
    }

    fun artist(c: MediaController?): String? = c?.metadata?.let {
        it.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: it.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: it.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
    }

    fun appLabel(ctx: Context, c: MediaController?): String? = c?.packageName?.let { pkg ->
        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
    }
}
