package de.flexy.widgetwerk.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import de.flexy.widgetwerk.data.ProgressStyle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import de.flexy.widgetwerk.data.CalendarRepo
import de.flexy.widgetwerk.data.ClockFont
import de.flexy.widgetwerk.data.ClockStyle
import de.flexy.widgetwerk.data.ColorRole
import de.flexy.widgetwerk.data.ConfigStore
import de.flexy.widgetwerk.data.DateStyle
import de.flexy.widgetwerk.data.DecoStyle
import de.flexy.widgetwerk.data.PhotoStore
import de.flexy.widgetwerk.data.Place
import de.flexy.widgetwerk.data.ShapeKind
import de.flexy.widgetwerk.data.Surface as Material
import de.flexy.widgetwerk.data.Tint
import de.flexy.widgetwerk.data.WeatherRepo
import de.flexy.widgetwerk.data.WidgetConfig
import de.flexy.widgetwerk.data.WidgetKind
import de.flexy.widgetwerk.ui.components.Choices
import de.flexy.widgetwerk.ui.components.LabeledSlider
import de.flexy.widgetwerk.ui.components.Section
import de.flexy.widgetwerk.ui.components.ShapePicker
import de.flexy.widgetwerk.ui.components.SwitchRow
import de.flexy.widgetwerk.ui.components.WidgetPreview
import de.flexy.widgetwerk.ui.components.pct
import de.flexy.widgetwerk.ui.theme.WidgetwerkTheme
import de.flexy.widgetwerk.widget.CalendarWatcher
import de.flexy.widgetwerk.widget.MediaHelper
import de.flexy.widgetwerk.widget.WidgetRegistry
import de.flexy.widgetwerk.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Einstellungen eines Widgets – beim Platzieren und über "Widget-Einstellungen" im Launcher. */
class ConfigureActivity : ComponentActivity() {

    companion object {
        fun intent(ctx: Context, id: Int) = Intent(ctx, ConfigureActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val kind = WidgetRegistry.kindOf(this, id)
        if (kind == null) { finish(); return }
        // Konfiguration ist optional → immer OK melden, damit das Widget bleibt
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        setContent { WidgetwerkTheme { ConfigScreen(id, kind, onDone = { finish() }) } }
    }
}

private val rectShapes = listOf(ShapeKind.ROUNDED, ShapeKind.PILL, ShapeKind.ARCH, ShapeKind.ARCH_DOWN)

private fun shapesFor(kind: WidgetKind) = when (kind) {
    WidgetKind.ANALOG, WidgetKind.DATE, WidgetKind.PHOTO, WidgetKind.GLASS, WidgetKind.DECO, WidgetKind.BATTERY,
    WidgetKind.COUNTDOWN, WidgetKind.PROGRESS -> ShapeKind.entries.toList()
    else -> rectShapes
}

private fun surfacesFor(kind: WidgetKind) = Material.entries.filter { it != Material.ART || kind == WidgetKind.PHOTO || kind == WidgetKind.MEDIA }

@Composable
private fun ConfigScreen(id: Int, kind: WidgetKind, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(ConfigStore.loadOrDefault(ctx, id, kind)) }
    val update: (WidgetConfig) -> Unit = { cfg = it; ConfigStore.save(ctx, id, it) }

    // Homescreen-Widget entprellt mitziehen
    LaunchedEffect(cfg) { delay(300); WidgetUpdater.request(ctx, id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(kind.title) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { Button(onClick = onDone, modifier = Modifier.padding(end = 8.dp)) { Text("Done") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)) {
                WidgetPreview(kind, id, cfg, Modifier.padding(16.dp), maxHeight = 240.dp)
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (kind) {
                    WidgetKind.CLOCK -> ClockOptions(cfg, update)
                    WidgetKind.ANALOG -> Section("Clock") {
                        SwitchRow("Second hand", cfg.showSeconds, "Uses a little more battery") { update(cfg.copy(showSeconds = it)) }
                    }
                    WidgetKind.DATE -> Section("Date") {
                        Choices(DateStyle.entries, cfg.dateStyle, { it.label }) { update(cfg.copy(dateStyle = it)) }
                        LabeledSlider("Text size", cfg.textScale, 0.6f..1.3f, pct(cfg.textScale)) { update(cfg.copy(textScale = it)) }
                    }
                    WidgetKind.WEATHER -> WeatherOptions(id, cfg, update)
                    WidgetKind.PHOTO -> PhotoOptions(cfg, update)
                    WidgetKind.DECO -> Section("Design", subtitle = "Tap the widget on your home screen to shuffle it.") {
                        Choices(DecoStyle.entries, cfg.decoStyle, { it.label }) { update(cfg.copy(decoStyle = it)) }
                        FilledTonalButton(onClick = { update(cfg.copy(seed = cfg.seed + 1)) }) {
                            Icon(Icons.Rounded.Casino, null); Spacer(Modifier.width(8.dp)); Text("Shuffle")
                        }
                    }
                    WidgetKind.CALENDAR -> CalendarOptions(cfg, update)
                    WidgetKind.MEDIA -> MediaOptions(cfg, update)
                    WidgetKind.LAUNCHER -> LauncherOptions(cfg, update)
                    WidgetKind.COUNTDOWN -> CountdownOptions(cfg, update)
                    WidgetKind.NOTE -> NoteOptions(cfg, update)
                    WidgetKind.WORLD -> WorldOptions(cfg, update)
                    WidgetKind.PROGRESS -> Section("Progress") {
                        Choices(ProgressStyle.entries, cfg.progressStyle, { it.label }) { update(cfg.copy(progressStyle = it)) }
                    }
                    WidgetKind.SUN -> WeatherOptions(id, cfg, update, weatherExtras = false)
                    WidgetKind.BATTERY, WidgetKind.GLASS -> Unit
                }
                MaterialOptions(kind, cfg, update)
                ApplyToAll(id, cfg)
                AlignmentInfo(id, cfg, update)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Material

@Composable
private fun MaterialOptions(kind: WidgetKind, cfg: WidgetConfig, update: (WidgetConfig) -> Unit) {
    val decoStandalone = kind == WidgetKind.DECO && (cfg.decoStyle == DecoStyle.SHAPE || cfg.decoStyle == DecoStyle.STACK)
    val photoPlain = kind == WidgetKind.PHOTO && !cfg.photoFrame
    Section("Look", subtitle = "Material, shape and color") {
        if (!decoStandalone && !photoPlain) {
            Text("Material", style = MaterialTheme.typography.labelLarge)
            Choices(surfacesFor(kind), cfg.surface, { it.label }) { update(cfg.copy(surface = it)) }
            val glassy = cfg.surface == Material.GLASS || cfg.surface == Material.FROST || cfg.surface == Material.ART
            if (glassy) {
                Text("Tint", style = MaterialTheme.typography.labelLarge)
                Choices(Tint.entries, cfg.tint, { it.label }) { update(cfg.copy(tint = it)) }
                LabeledSlider("Milkiness", cfg.tintStrength, 0f..1f, pct(cfg.tintStrength)) { update(cfg.copy(tintStrength = it)) }
                LabeledSlider("Blur", cfg.blur, 0f..1f, pct(cfg.blur)) { update(cfg.copy(blur = it)) }
            }
        }
        if (kind != WidgetKind.DECO || cfg.decoStyle != DecoStyle.STACK) {
            Text("Shape", style = MaterialTheme.typography.labelLarge)
            ShapePicker(shapesFor(kind), cfg.shape) { update(cfg.copy(shape = it)) }
            if (cfg.shape == ShapeKind.ROUNDED || cfg.shape == ShapeKind.ARCH || cfg.shape == ShapeKind.ARCH_DOWN) {
                LabeledSlider("Corner radius", cfg.cornerDp, 0f..64f, "${cfg.cornerDp.roundToInt()} dp") { update(cfg.copy(cornerDp = it)) }
            }
        }
        Text("Accent", style = MaterialTheme.typography.labelLarge)
        Choices(ColorRole.entries, cfg.colorRole, { it.label }) { update(cfg.copy(colorRole = it)) }
        SwitchRow("Grain", cfg.grain, "Subtle noise like real frosted glass") { update(cfg.copy(grain = it)) }
        SwitchRow("Light edge", cfg.rim, "Glossy rim along the border") { update(cfg.copy(rim = it)) }
    }
}

/** Material, Form und Farben dieses Widgets auf alle anderen übertragen. */
@Composable
private fun ApplyToAll(id: Int, cfg: WidgetConfig) {
    val ctx = LocalContext.current
    TextButton(onClick = {
        WidgetRegistry.all(ctx).filter { it.first != id }.forEach { (oid, okind) ->
            val o = ConfigStore.loadOrDefault(ctx, oid, okind)
            val shapeOk = cfg.shape in shapesFor(okind)
            ConfigStore.save(ctx, oid, o.copy(
                surface = if (cfg.surface == Material.ART && okind != WidgetKind.PHOTO && okind != WidgetKind.MEDIA) Material.GLASS else cfg.surface,
                tint = cfg.tint, tintStrength = cfg.tintStrength, blur = cfg.blur, cornerDp = cfg.cornerDp,
                shape = if (shapeOk) cfg.shape else o.shape, grain = cfg.grain, rim = cfg.rim, colorRole = cfg.colorRole,
            ))
        }
        WidgetUpdater.requestAll(ctx)
        Toast.makeText(ctx, "Look applied to all widgets", Toast.LENGTH_SHORT).show()
    }) { Text("Apply this look to all my widgets") }
}

@Composable
private fun AlignmentInfo(id: Int, cfg: WidgetConfig, update: (WidgetConfig) -> Unit) {
    val ctx = LocalContext.current
    val changes by ConfigStore.changes.collectAsState()
    val b = remember(changes) { ConfigStore.bounds(ctx, id) }
    val pages = remember(changes) { ConfigStore.global(ctx).pages }
    Section(
        "Glass position",
        subtitle = if (b != null) "Aligned at ${b.left}, ${b.top} (${b.width()}×${b.height()} px). Moved the widget? Just tap it once."
        else "Not aligned yet – tap the widget once on your home screen so the glass matches your wallpaper.",
    ) {
        if (pages > 1) {
            Text("Home screen page", style = MaterialTheme.typography.labelLarge)
            Choices((0 until pages).toList(), cfg.page.coerceIn(0, pages - 1), { "Page ${it + 1}" }) { update(cfg.copy(page = it)) }
        }
        if (b != null) TextButton(onClick = { ConfigStore.clearBounds(ctx, id); WidgetUpdater.request(ctx, id) }) { Text("Forget position") }
    }
}

// ---------------------------------------------------------------- Uhr

@Composable
private fun ClockOptions(cfg: WidgetConfig, update: (WidgetConfig) -> Unit) {
    Section("Clock") {
        Text("Layout", style = MaterialTheme.typography.labelLarge)
        Choices(ClockStyle.entries, cfg.clockStyle, { it.label }) { update(cfg.copy(clockStyle = it)) }
        Text("Font", style = MaterialTheme.typography.labelLarge)
        Choices(ClockFont.entries, cfg.font, { it.label }) { update(cfg.copy(font = it)) }
        Text("Time format", style = MaterialTheme.typography.labelLarge)
        Choices(listOf<Boolean?>(null, false, true), cfg.force24h, { when (it) { null -> "System"; false -> "12 h"; true -> "24 h" } }) {
            update(cfg.copy(force24h = it))
        }
        if (cfg.clockStyle != ClockStyle.MINIMAL) SwitchRow("Show date", cfg.showDate) { update(cfg.copy(showDate = it)) }
        LabeledSlider("Size", cfg.textScale, 0.6f..1.2f, pct(cfg.textScale)) { update(cfg.copy(textScale = it)) }
    }
}

// ---------------------------------------------------------------- Wetter

@Composable
private fun WeatherOptions(id: Int, cfg: WidgetConfig, update: (WidgetConfig) -> Unit, weatherExtras: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    fun choose(p: Place) {
        val n = cfg.copy(lat = p.lat, lon = p.lon, place = p.name)
        update(n)
        results = emptyList(); query = ""
        scope.launch {
            busy = true
            runCatching { WeatherRepo.fetch(p.lat, p.lon) }
                .onSuccess { ConfigStore.saveWeather(ctx, id, it); WidgetUpdater.request(ctx, id) }
                .onFailure { Toast.makeText(ctx, "Weather couldn't be loaded", Toast.LENGTH_SHORT).show() }
            busy = false
        }
    }

    fun locate() = scope.launch {
        busy = true
        val p = WeatherRepo.currentPlace(ctx)
        busy = false
        if (p != null) choose(p) else Toast.makeText(ctx, "Location unavailable – search for your city instead", Toast.LENGTH_LONG).show()
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) locate() }

    Section("Location", subtitle = cfg.place?.let { "Showing weather for $it" } ?: "Choose where the weather is for") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = {
                if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
                else perm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }) {
                Icon(Icons.Rounded.MyLocation, null); Spacer(Modifier.width(8.dp)); Text("Use my location")
            }
            Spacer(Modifier.width(12.dp))
            if (busy) ContainedLoadingIndicator(Modifier.size(40.dp))
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search city") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { scope.launch { results = runCatching { WeatherRepo.search(query) }.getOrDefault(emptyList()) } }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
        )
        LaunchedEffect(query) {
            if (query.length < 2) { results = emptyList(); return@LaunchedEffect }
            delay(400)
            results = runCatching { WeatherRepo.search(query) }.getOrDefault(emptyList())
        }
        results.forEach { p ->
            ListItem(
                headlineContent = { Text(p.name) },
                supportingContent = { Text(p.detail) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { choose(p) },
            )
        }
    }
    if (weatherExtras) Section("Weather") {
        Choices(listOf(false, true), cfg.fahrenheit, { if (it) "°F" else "°C" }) { update(cfg.copy(fahrenheit = it)) }
        SwitchRow("Hourly forecast", cfg.showHourly, "Shown when the widget is tall enough") { update(cfg.copy(showHourly = it)) }
        LabeledSlider("Text size", cfg.textScale, 0.7f..1.2f, pct(cfg.textScale)) { update(cfg.copy(textScale = it)) }
    }
}

// ---------------------------------------------------------------- Foto

@Composable
private fun PhotoOptions(cfg: WidgetConfig, update: (WidgetConfig) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val latest by androidx.compose.runtime.rememberUpdatedState(cfg)

    fun importPhotos(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            busy = true
            val names = withContext(Dispatchers.IO) { uris.mapNotNull { PhotoStore.importPhoto(ctx, it) } }
            busy = false
            if (names.isEmpty()) Toast.makeText(ctx, "Couldn't import those photos", Toast.LENGTH_SHORT).show()
            val c = latest
            update(c.copy(photos = c.photos + names, photoIndex = c.photos.size, photoLastSwitch = System.currentTimeMillis()))
        }
    }

    // System-Fotoauswahl: zeigt auf dem Pixel auch Google-Photos-Alben aus der Cloud
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { importPhotos(it) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importPhotos(it) }

    Section("Photos", subtitle = "Google Photos albums appear in the photo picker. Photos are copied into the app so they also work offline.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Google Photos & gallery")
            }
            FilledTonalButton(onClick = { files.launch(arrayOf("image/*")) }) {
                Icon(Icons.Rounded.Folder, null); Spacer(Modifier.width(8.dp)); Text("Files")
            }
            if (busy) ContainedLoadingIndicator(Modifier.size(40.dp))
        }
        if (cfg.photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cfg.photos, key = { it }) { name ->
                    val thumb = remember(name) { PhotoStore.load(ctx, name, 240) }
                    Box(Modifier.size(84.dp)) {
                        thumb?.let {
                            Image(it.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp)
                                .clip(MaterialShapes.Cookie6Sided.toShape()).background(MaterialTheme.colorScheme.errorContainer)
                                .clickable {
                                    PhotoStore.deleteAll(ctx, listOf(name))
                                    update(cfg.copy(photos = cfg.photos - name, photoIndex = 0))
                                },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, "Remove", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer) }
                    }
                }
            }
            Text("${cfg.photos.size} photo${if (cfg.photos.size == 1) "" else "s"} · tap the widget for the next one",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Section("Slideshow") {
        Choices(listOf(0, 30, 60, 180, 360, 1440), cfg.slideshowMinutes, {
            when (it) { 0 -> "Off"; 30 -> "30 min"; 60 -> "1 h"; 180 -> "3 h"; 360 -> "6 h"; else -> "Daily" }
        }) { update(cfg.copy(slideshowMinutes = it)) }
        SwitchRow("Glass frame", cfg.photoFrame, "Photo sits on a frosted mat") { update(cfg.copy(photoFrame = it)) }
    }
}

// ---------------------------------------------------------------- Kalender / Medien

@Composable
private fun CalendarOptions(cfg: WidgetConfig, update: (WidgetConfig) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(CalendarRepo.hasPermission(ctx)) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) { CalendarWatcher.schedule(ctx); WidgetUpdater.requestKind(ctx, WidgetKind.CALENDAR) }
    }
    Section("Calendar") {
        if (!granted) Button(onClick = { perm.launch(Manifest.permission.READ_CALENDAR) }) {
            Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("Allow calendar access")
        }
        LabeledSlider("Look ahead", cfg.calendarDays.toFloat(), 1f..14f, "${cfg.calendarDays} days") {
            update(cfg.copy(calendarDays = it.roundToInt()))
        }
        LabeledSlider("Header size", cfg.textScale, 0.7f..1.2f, pct(cfg.textScale)) { update(cfg.copy(textScale = it)) }
    }
}

@Composable
private fun MediaOptions(cfg: WidgetConfig, update: (WidgetConfig) -> Unit) {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    val access = remember(tick) { MediaHelper.hasAccess(ctx) }
    Section(
        "Now playing",
        subtitle = if (access) "Notification access is on – the widget follows whatever is playing."
        else "Widgetwerk needs notification access to see what's playing. Notifications themselves are never read.",
    ) {
        if (!access) Button(onClick = { openListenerSettings(ctx) }) {
            Icon(Icons.Rounded.NotificationsActive, null); Spacer(Modifier.width(8.dp)); Text("Allow access")
        }
        SwitchRow("Progress bar", cfg.mediaProgress, "Tap anywhere on the bar to jump there. Needs a 3-row tall widget.") {
            update(cfg.copy(mediaProgress = it))
        }
        SwitchRow("Player buttons", cfg.mediaActions, "Extra buttons the app offers – e.g. Spotify's ♥ (Add to Liked Songs)") {
            update(cfg.copy(mediaActions = it))
        }
    }
}

// ---------------------------------------------------------------- App-Dock

private class AppEntry(val component: String, val label: String, val icon: Bitmap)

private const val MAX_DOCK = 8

@Composable
private fun LauncherOptions(cfg: WidgetConfig, update: (WidgetConfig) -> Unit) {
    val ctx = LocalContext.current
    var sheet by remember { mutableStateOf(false) }
    // Alle startbaren Apps einmal laden (für Liste + Suche)
    val allApps = remember { mutableStateListOf<AppEntry>() }
    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.Default) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .filter { it.activityInfo.packageName != ctx.packageName }
                .map {
                    val cn = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                    AppEntry(cn.flattenToString(), it.loadLabel(pm).toString(), it.loadIcon(pm).toBitmap(96, 96))
                }
                .sortedBy { it.label.lowercase() }
        }
        allApps.addAll(list)
    }
    val byId = allApps.associateBy { it.component }

    Section("Apps", subtitle = "Up to $MAX_DOCK apps. Use the arrows to change the order.") {
        cfg.apps.forEachIndexed { i, comp ->
            val a = byId[comp]
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    a?.let { Image(it.icon.asImageBitmap(), null, Modifier.size(36.dp)) } ?: Box(Modifier.size(36.dp))
                    Text(a?.label ?: comp.substringBefore('/'), Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1)
                    IconButton(onClick = { update(cfg.copy(apps = cfg.apps.move(i, i - 1))) }, enabled = i > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, "Move up")
                    }
                    IconButton(onClick = { update(cfg.copy(apps = cfg.apps.move(i, i + 1))) }, enabled = i < cfg.apps.size - 1) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Move down")
                    }
                    IconButton(onClick = { update(cfg.copy(apps = cfg.apps - comp)) }) { Icon(Icons.Rounded.Close, "Remove") }
                }
            }
        }
        if (cfg.apps.size < MAX_DOCK) Button(onClick = { sheet = true }) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add apps")
        }
        SwitchRow("Themed icons", cfg.themedIcons, "All icons in your colors – apps without a themed icon get one generated") { update(cfg.copy(themedIcons = it)) }
        SwitchRow("App names", cfg.dockLabels, "Small label under each icon") { update(cfg.copy(dockLabels = it)) }
        SwitchRow("Date above icons", cfg.dockDate, "Needs a 2-row tall widget") { update(cfg.copy(dockDate = it)) }
    }
    if (sheet) {
        var query by remember { mutableStateOf("") }
        var chosen by remember { mutableStateOf(cfg.apps) }
        val shown = allApps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
        ModalBottomSheet(onDismissRequest = { update(cfg.copy(apps = chosen)); sheet = false }) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${chosen.size}/$MAX_DOCK in dock", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button(onClick = { update(cfg.copy(apps = chosen)); sheet = false }) { Text("Done") }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search apps") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                )
            }
            if (allApps.isEmpty()) Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { ContainedLoadingIndicator() }
            LazyColumn(Modifier.fillMaxWidth().height(480.dp)) {
                items(shown, key = { it.component }) { a ->
                    val sel = a.component in chosen
                    ListItem(
                        leadingContent = { Image(a.icon.asImageBitmap(), null, Modifier.size(40.dp)) },
                        headlineContent = { Text(a.label) },
                        trailingContent = { Checkbox(checked = sel, onCheckedChange = null) },
                        modifier = Modifier.clickable {
                            chosen = if (sel) chosen - a.component else if (chosen.size < MAX_DOCK) chosen + a.component else chosen
                        },
                    )
                }
            }
        }
    }
}

private fun <T> List<T>.move(from: Int, to: Int): List<T> {
    if (to !in indices) return this
    val m = toMutableList(); val x = m.removeAt(from); m.add(to, x); return m
}

// ---------------------------------------------------------------- Neue Widgets

@Composable
private fun CountdownOptions(cfg: WidgetConfig, update: (WidgetConfig) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    Section("Countdown") {
        OutlinedTextField(
            value = cfg.countdownTitle, onValueChange = { update(cfg.copy(countdownTitle = it)) },
            label = { Text("What are you counting down to?") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        )
        FilledTonalButton(onClick = { picker = true }) {
            Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(8.dp))
            Text(cfg.countdownEpochDay?.let { java.time.LocalDate.ofEpochDay(it).toString() } ?: "Pick a date")
        }
        LabeledSlider("Text size", cfg.textScale, 0.6f..1.3f, pct(cfg.textScale)) { update(cfg.copy(textScale = it)) }
    }
    if (picker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = cfg.countdownEpochDay?.let { it * 86_400_000L } ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { update(cfg.copy(countdownEpochDay = Math.floorDiv(it, 86_400_000L))) }
                    picker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun NoteOptions(cfg: WidgetConfig, update: (WidgetConfig) -> Unit) {
    Section("Note") {
        OutlinedTextField(
            value = cfg.noteText, onValueChange = { update(cfg.copy(noteText = it)) },
            label = { Text("Your note") }, minLines = 3,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        )
        SwitchRow("Centered", cfg.noteCentered) { update(cfg.copy(noteCentered = it)) }
        LabeledSlider("Text size", cfg.textScale, 0.6f..1.6f, pct(cfg.textScale)) { update(cfg.copy(textScale = it)) }
    }
}

@Composable
private fun WorldOptions(cfg: WidgetConfig, update: (WidgetConfig) -> Unit) {
    var query by remember { mutableStateOf("") }
    val zones = remember { java.util.TimeZone.getAvailableIDs().filter { '/' in it && !it.startsWith("Etc") && !it.startsWith("SystemV") }.sorted() }
    val shown = remember(query) {
        if (query.length < 2) emptyList()
        else zones.filter { it.replace('_', ' ').contains(query.trim(), ignoreCase = true) }.take(12)
    }
    Section("World clock", subtitle = "Showing ${de.flexy.widgetwerk.widget.MoreRenderers.cityOf(cfg.zoneId)} (${cfg.zoneId})") {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Search city / time zone") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        )
        shown.forEach { z ->
            ListItem(
                headlineContent = { Text(de.flexy.widgetwerk.widget.MoreRenderers.cityOf(z)) },
                supportingContent = { Text(z) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { update(cfg.copy(zoneId = z, zoneLabel = null)); query = "" },
            )
        }
        OutlinedTextField(
            value = cfg.zoneLabel ?: "", onValueChange = { update(cfg.copy(zoneLabel = it.ifBlank { null })) },
            label = { Text("Label (optional, e.g. \"Mum\")") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        )
        Choices(listOf<Boolean?>(null, false, true), cfg.force24h, { when (it) { null -> "System"; false -> "12 h"; true -> "24 h" } }) {
            update(cfg.copy(force24h = it))
        }
    }
}
