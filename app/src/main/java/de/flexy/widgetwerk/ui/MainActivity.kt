package de.flexy.widgetwerk.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.flexy.widgetwerk.data.ConfigStore
import de.flexy.widgetwerk.data.WallpaperMode
import de.flexy.widgetwerk.data.WidgetConfig
import de.flexy.widgetwerk.data.WidgetKind
import de.flexy.widgetwerk.glass.WallpaperSource
import de.flexy.widgetwerk.ui.components.LabeledSlider
import de.flexy.widgetwerk.ui.components.Section
import de.flexy.widgetwerk.ui.components.WidgetPreview
import de.flexy.widgetwerk.ui.components.pct
import de.flexy.widgetwerk.ui.theme.WidgetwerkTheme
import de.flexy.widgetwerk.widget.LiveService
import de.flexy.widgetwerk.widget.MediaHelper
import de.flexy.widgetwerk.widget.RefreshWorker
import de.flexy.widgetwerk.widget.WidgetRegistry
import de.flexy.widgetwerk.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RefreshWorker.schedule(this)
        setContent { WidgetwerkTheme { App() } }
    }
}

private enum class Screen { HOME, GLASS }

/** Zählt bei jedem ON_RESUME hoch – um Status (Berechtigungen, Widgets) neu zu lesen. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    BackHandler(screen != Screen.HOME) { screen = Screen.HOME }
    AnimatedContent(screen, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) }, label = "screen") { s ->
        when (s) {
            Screen.HOME -> HomeScreen(openGlass = { screen = Screen.GLASS })
            Screen.GLASS -> GlassScreen(back = { screen = Screen.HOME })
        }
    }
}

// ====================================================================== Start

@Composable
private fun HomeScreen(openGlass: () -> Unit) {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    val changes by ConfigStore.changes.collectAsState()
    val placed = remember(tick, changes) { WidgetRegistry.all(ctx) }
    val global = remember(tick, changes) { ConfigStore.global(ctx) }
    val liveOn = remember(tick) { MediaHelper.hasAccess(ctx) }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Hero() }
            item {
                StatusCard(
                    icon = Icons.Rounded.BlurOn,
                    title = "Glass source",
                    text = when (global.wallpaperMode) {
                        WallpaperMode.NONE -> "Not set – glass widgets use milky glass from your colors. Set your wallpaper here for real blur."
                        WallpaperMode.PICKED -> "Using your chosen image. Tap a glass widget once after moving it."
                        WallpaperMode.SYSTEM -> "Following your system wallpaper automatically."
                    },
                    ok = global.wallpaperMode != WallpaperMode.NONE,
                    action = "Set up",
                    onClick = openGlass,
                )
            }
            item {
                StatusCard(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Live updates",
                    text = if (liveOn) "On – now playing, battery and theme changes update instantly."
                    else "Allow notification access for the Now playing widget and instant updates. Notifications themselves are never read.",
                    ok = liveOn,
                    action = if (liveOn) "Settings" else "Allow",
                    onClick = { openListenerSettings(ctx) },
                )
            }
            if (placed.isNotEmpty()) {
                item { Header("On your home screen", "${placed.size} widget${if (placed.size == 1) "" else "s"} – tap to customise") }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        placed.forEach { (id, kind) ->
                            val aligned = ConfigStore.bounds(ctx, id) != null
                            FilledTonalButton(onClick = { ctx.startActivity(ConfigureActivity.intent(ctx, id)) }) {
                                Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(kind.title + if (aligned) "" else " · tap once")
                            }
                        }
                    }
                }
                item {
                    TextButton(onClick = { WidgetUpdater.requestAll(ctx); RefreshWorker.runNow(ctx) }) {
                        Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Refresh all widgets")
                    }
                }
            }
            item { Header("Widget pack", "Live previews with your colors and wallpaper") }
            items(WidgetKind.entries, key = { it.name }) { kind -> GalleryCard(kind) }
            item {
                Text(
                    "Tip: Long-press any widget on your home screen → “Widget settings” to change its look.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Hero() {
    val cs = MaterialTheme.colorScheme
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "a")
    val breathe by spin.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "b")
    Box(Modifier.fillMaxWidth().height(200.dp).statusBarsPadding()) {
        Box(Modifier.size(120.dp).align(Alignment.TopEnd).offset(x = 10.dp, y = 6.dp).rotate(angle)
            .clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.primaryContainer))
        Box(Modifier.size((64 * breathe).dp).align(Alignment.TopEnd).offset(x = (-96).dp, y = 96.dp).rotate(-angle * 1.5f)
            .clip(MaterialShapes.Sunny.toShape()).background(cs.tertiaryContainer))
        Box(Modifier.size(40.dp).align(Alignment.TopEnd).offset(x = (-30).dp, y = 128.dp)
            .clip(MaterialShapes.Clover4Leaf.toShape()).background(cs.secondary))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp)) {
            Text("Widgetwerk", style = MaterialTheme.typography.displayMedium, color = cs.onSurface)
            Text("Expressive widgets on glass", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.padding(start = 8.dp, top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, text: String, ok: Boolean, action: String, onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = if (ok) cs.surfaceContainer else cs.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(if (ok) cs.primaryContainer else cs.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (ok) Icons.Rounded.CheckCircle else icon, null, tint = if (ok) cs.onPrimaryContainer else cs.onPrimary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                Icon(Icons.Rounded.ChevronRight, null, tint = cs.primary)
            }
        }
    }
}

@Composable
private fun GalleryCard(kind: WidgetKind) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val cfg = remember(kind) { WidgetConfig.defaults(kind) }
    Surface(shape = RoundedCornerShape(32.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WidgetPreview(kind, null, cfg, maxHeight = 220.dp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(kind.title, style = MaterialTheme.typography.titleLarge)
                    Text(kind.subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Button(onClick = { pin(ctx, kind) }) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Add")
                }
            }
        }
    }
}

private fun pin(ctx: Context, kind: WidgetKind) {
    val am = AppWidgetManager.getInstance(ctx)
    if (am.isRequestPinAppWidgetSupported) {
        am.requestPinAppWidget(WidgetRegistry.component(ctx, kind), null, null)
    } else {
        Toast.makeText(ctx, "Long-press your home screen → Widgets → Widgetwerk", Toast.LENGTH_LONG).show()
    }
}

fun openListenerSettings(ctx: Context) {
    val cn = ComponentName(ctx, LiveService::class.java)
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString())
    runCatching { ctx.startActivity(detail) }.onFailure {
        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

// ====================================================================== Glas-Quelle

@Composable
private fun GlassScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = rememberResumeTick()
    val changes by ConfigStore.changes.collectAsState()
    val global = remember(changes) { ConfigStore.global(ctx) }
    var zoom by remember { mutableStateOf(global.zoom) }
    var offX by remember { mutableStateOf(global.offsetX) }
    var offY by remember { mutableStateOf(global.offsetY) }
    var busy by remember { mutableStateOf(false) }
    var waitingForAllFiles by remember { mutableStateOf(false) }

    fun afterChange() { WidgetUpdater.requestAll(ctx) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val ok = withContext(Dispatchers.Default) { WallpaperSource.importPicked(ctx, uri) }
            busy = false
            if (ok) afterChange() else Toast.makeText(ctx, "Couldn't read that image", Toast.LENGTH_SHORT).show()
        }
    }

    fun useSystem() = scope.launch {
        busy = true
        val ok = withContext(Dispatchers.Default) { WallpaperSource.useSystem(ctx) }
        busy = false
        if (ok) afterChange()
        else Toast.makeText(ctx, "System wallpaper not readable (live wallpaper?) – pick the image instead", Toast.LENGTH_LONG).show()
    }

    // Zurück aus "Zugriff auf alle Dateien"
    LaunchedEffect(tick) {
        if (waitingForAllFiles && WallpaperSource.canReadSystem(ctx)) { waitingForAllFiles = false; useSystem() }
    }

    // Feinjustierung: entprellt neu berechnen
    LaunchedEffect(zoom, offX, offY) {
        if (zoom == global.zoom && offX == global.offsetX && offY == global.offsetY) return@LaunchedEffect
        delay(350)
        withContext(Dispatchers.Default) {
            ConfigStore.updateGlobal(ctx) { it.copy(zoom = zoom, offsetX = offX, offsetY = offY) }
            if (ConfigStore.global(ctx).wallpaperMode != WallpaperMode.NONE) WallpaperSource.reprocess(ctx)
        }
        afterChange()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Text("Glass source", style = MaterialTheme.typography.headlineMedium)
                }
            }
            item {
                Section(
                    "How the glass works",
                    subtitle = "Android doesn't let widgets blur what's behind them. Widgetwerk cuts the matching piece out of your wallpaper and blurs it. " +
                        "After placing or moving a glass widget, tap it once – the launcher then tells Widgetwerk exactly where it sits.",
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) {
                            Icon(Icons.Rounded.Image, null); Spacer(Modifier.width(8.dp)); Text("Pick wallpaper image")
                        }
                        FilledTonalButton(onClick = {
                            if (WallpaperSource.canReadSystem(ctx)) useSystem()
                            else {
                                waitingForAllFiles = true
                                runCatching {
                                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                                }
                            }
                        }, enabled = !busy) {
                            Icon(Icons.Rounded.Wallpaper, null); Spacer(Modifier.width(8.dp)); Text("Use system wallpaper")
                        }
                        if (global.wallpaperMode != WallpaperMode.NONE) OutlinedButton(onClick = { WallpaperSource.disable(ctx); afterChange() }) {
                            Text("Milky glass only")
                        }
                    }
                    Text(
                        "“Use system wallpaper” needs “All files access” (Android only exposes the wallpaper that way). " +
                            "Picking the same image you use as wallpaper works without it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { PhonePreview(changes) }
            if (global.wallpaperMode != WallpaperMode.NONE) item {
                Section("Fine-tune", subtitle = "If the glass doesn't line up with your wallpaper, nudge it here.") {
                    LabeledSlider("Zoom", zoom, 1f..1.5f, pct(zoom)) { zoom = it }
                    LabeledSlider("Horizontal", offX, -0.5f..0.5f, pct(offX)) { offX = it }
                    LabeledSlider("Vertical", offY, -0.5f..0.5f, pct(offY)) { offY = it }
                    TextButton(onClick = { zoom = 1f; offX = 0f; offY = 0f }) { Text("Reset") }
                }
            }
        }
    }
}

/** Telefon-Silhouette mit Wallpaper und den Positionen der platzierten Widgets. */
@Composable
private fun PhonePreview(changes: Int) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val wall = remember(changes) { WallpaperSource.get(ctx) }
    val (sw, sh) = remember { WallpaperSource.screenSize(ctx) }
    val rects = remember(changes) {
        WidgetRegistry.all(ctx).mapNotNull { (id, kind) -> ConfigStore.bounds(ctx, id)?.let { it to kind } }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.width(220.dp).aspectRatio(sw.toFloat() / sh).clip(RoundedCornerShape(32.dp))
                .background(cs.surfaceContainerHighest).clickable { },
        ) {
            if (wall != null) Image(wall.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = cs.onSurfaceVariant)
            }
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / sw
                rects.forEach { (r, _) ->
                    drawRoundRect(
                        color = cs.primary,
                        topLeft = Offset(r.left * k, r.top * k),
                        size = Size(r.width() * k, r.height() * k),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
        }
    }
}
