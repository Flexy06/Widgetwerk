package de.flexy.widgetwerk.data

import kotlinx.serialization.Serializable

/** Alle Widget-Arten der App. Reihenfolge = Reihenfolge in der Galerie. */
@Serializable
enum class WidgetKind(val title: String, val subtitle: String) {
    CLOCK("Clock", "Big expressive digital clock"),
    ANALOG("Analog clock", "Shaped dial with bold hands"),
    DATE("Date", "Day and date in a shape"),
    WEATHER("Weather", "Now, today and the next hours"),
    PHOTO("Photo", "Google Photos or phone storage"),
    GLASS("Glass", "Just frosted glass – nothing else"),
    DECO("Shapes", "Pure design, no purpose"),
    CALENDAR("Calendar", "Your next events"),
    BATTERY("Battery", "Wavy charge ring"),
    MEDIA("Now playing", "Artwork, title and controls"),
    LAUNCHER("App dock", "Your favourite apps on glass"),
    COUNTDOWN("Countdown", "Days until something big"),
    NOTE("Note", "A sticky note on glass"),
    WORLD("World clock", "The time somewhere else"),
    PROGRESS("Time progress", "Day, week, month & year"),
    SUN("Sun & moon", "Daylight arc and moon phase"),
    GLANCE("At a glance", "Date, weather and what's next"),
    MONTH("Month", "Calendar month with event dots"),
    TORCH("Flashlight", "One tap torch"),
    STORAGE("Storage", "How full your phone is"),
    TIMER("Timer", "Start a timer with one tap"),
    COUNTER("Counter", "Count anything – water, coffee, push-ups"),
}

/** Hintergrund-Material des Widgets. */
@Serializable
enum class Surface(val label: String) {
    GLASS("Glass"),     // echter Blur des Hintergrundbilds an der Widget-Position
    FROST("Milky"),     // Milchglas aus den dynamischen Farben (kein Wallpaper nötig)
    TONAL("Tonal"),     // klassisches M3-Surface
    ACCENT("Accent"),   // Primary Container
    ART("Artwork"),     // Blur des eigenen Inhalts (Foto / Albumcover)
    NONE("None"),       // komplett transparent
}

/** Tönung über dem Glas. */
@Serializable
enum class Tint(val label: String) { AUTO("Auto"), LIGHT("Light"), DARK("Dark"), COLOR("Color") }

/** Expressive Formen (eigene Implementierung, angelehnt an M3 MaterialShapes). */
@Serializable
enum class ShapeKind(val label: String, val polar: Boolean) {
    ROUNDED("Rounded", false),
    PILL("Pill", false),
    ARCH("Arch ↑", false),
    ARCH_DOWN("Arch ↓", false),
    SQUIRCLE("Squircle", true),
    CIRCLE("Circle", true),
    COOKIE4("Cookie 4", true),
    COOKIE6("Cookie 6", true),
    COOKIE9("Cookie 9", true),
    COOKIE12("Cookie 12", true),
    CLOVER("Clover", true),
    SUNNY("Sunny", true),
    BURST("Soft burst", true),
    FLOWER("Flower", true),
    GEM("Gem", true),
}

@Serializable
enum class ClockFont(val label: String) {
    GOOGLE_SANS("Google Sans"),
    THIN("Thin"),
    LIGHT("Light"),
    BOLD("Black"),
    CONDENSED("Condensed"),
    SERIF("Serif"),
    MONO("Mono"),
}

@Serializable
enum class ClockStyle(val label: String) { STACKED("Stacked"), LINE("One line"), MINIMAL("Time only") }

@Serializable
enum class DateStyle(val label: String) { SHAPE("In shape"), HEADLINE("Headline") }

@Serializable
enum class DecoStyle(val label: String) {
    SHAPE("Shape"), ORBS("Orbs"), STACK("Stacked shapes"), WAVES("Waves"), DOTS("Dots"), RINGS("Rings")
}

@Serializable
enum class ProgressStyle(val label: String) { RINGS("Rings"), BARS("Bars") }

@Serializable
enum class ColorRole(val label: String) { PRIMARY("Primary"), SECONDARY("Secondary"), TERTIARY("Tertiary"), NEUTRAL("Neutral") }

/**
 * Einstellungen eines platzierten Widgets. Nicht jede Art nutzt jedes Feld –
 * das hält Speichern/Laden simpel (ein JSON pro Widget-ID).
 */
@Serializable
data class WidgetConfig(
    val kind: WidgetKind,
    // --- Material ---
    val surface: Surface = Surface.GLASS,
    val tint: Tint = Tint.AUTO,
    val tintStrength: Float = 0.25f,   // 0..1
    val blur: Float = 0.6f,            // 0..1
    val cornerDp: Float = 28f,
    val shape: ShapeKind = ShapeKind.ROUNDED,
    val grain: Boolean = true,
    val rim: Boolean = true,           // Lichtkante am Glasrand
    val colorRole: ColorRole = ColorRole.PRIMARY,
    // --- Uhr / Datum ---
    val font: ClockFont = ClockFont.GOOGLE_SANS,
    val clockStyle: ClockStyle = ClockStyle.STACKED,
    val showDate: Boolean = true,
    val force24h: Boolean? = null,     // null = Systemeinstellung
    val showSeconds: Boolean = false,
    val dateStyle: DateStyle = DateStyle.SHAPE,
    val clockAlarm: Boolean = false,          // nächsten Wecker unter der Uhr zeigen
    val clockWeather: Boolean = false,        // Kurz-Wetter unter der Uhr zeigen
    val textScale: Float = 1f,
    // --- Wetter ---
    val lat: Double? = null,
    val lon: Double? = null,
    val place: String? = null,
    val fahrenheit: Boolean = false,
    val showHourly: Boolean = true,
    // --- Foto ---
    val photos: List<String> = emptyList(),   // Dateinamen in filesDir/photos
    val photoIndex: Int = 0,
    val slideshowMinutes: Int = 60,           // 0 = aus
    val photoLastSwitch: Long = 0,
    val photoFrame: Boolean = false,          // Glasrahmen um das Foto
    // --- Deko ---
    val decoStyle: DecoStyle = DecoStyle.SHAPE,
    val seed: Int = 1,
    // --- Kalender ---
    val calendarDays: Int = 7,
    // --- Dock ---
    val apps: List<String> = emptyList(),     // ComponentName.flattenToString()
    val themedIcons: Boolean = true,
    val dockDate: Boolean = false,
    val dockLabels: Boolean = false,
    // --- Glas-Position ---
    val page: Int = 0,                        // Homescreen-Seite (für Wallpaper-Parallaxe)
    // --- Medien ---
    val mediaProgress: Boolean = true,
    val mediaActions: Boolean = true,         // Zusatzknöpfe des Players (z. B. Spotify ♥)
    // --- Countdown ---
    val countdownTitle: String = "Holidays",
    val countdownEpochDay: Long? = null,      // LocalDate.toEpochDay()
    // --- Notiz ---
    val noteText: String = "",
    val noteCentered: Boolean = false,
    // --- Weltuhr ---
    val zoneId: String = "America/New_York",
    val zoneLabel: String? = null,
    // --- Fortschritt ---
    val progressStyle: ProgressStyle = ProgressStyle.RINGS,
    // --- Timer ---
    val timerPresets: List<Int> = listOf(5, 10, 25),   // Minuten
    // --- Zähler ---
    val counterLabel: String = "Water",
    val counterValue: Int = 0,
    val counterGoal: Int = 8,
    val counterDaily: Boolean = true,          // täglich zurücksetzen
    val counterDay: Long = 0,                  // Epoch-Tag des Zählerstands
) {
    companion object {
        /** Sinnvolle Startwerte je Widget-Art. */
        fun defaults(kind: WidgetKind): WidgetConfig = when (kind) {
            WidgetKind.CLOCK -> WidgetConfig(kind, surface = Surface.NONE)
            WidgetKind.ANALOG -> WidgetConfig(kind, shape = ShapeKind.COOKIE12, surface = Surface.GLASS)
            WidgetKind.DATE -> WidgetConfig(kind, shape = ShapeKind.COOKIE9, surface = Surface.ACCENT)
            WidgetKind.WEATHER -> WidgetConfig(kind, cornerDp = 32f)
            WidgetKind.PHOTO -> WidgetConfig(kind, shape = ShapeKind.ROUNDED, surface = Surface.ART, cornerDp = 32f)
            WidgetKind.GLASS -> WidgetConfig(kind, blur = 0.8f, tintStrength = 0.18f)
            WidgetKind.DECO -> WidgetConfig(kind, shape = ShapeKind.SUNNY, surface = Surface.ACCENT)
            WidgetKind.CALENDAR -> WidgetConfig(kind, cornerDp = 32f)
            WidgetKind.BATTERY -> WidgetConfig(kind, shape = ShapeKind.ROUNDED, cornerDp = 36f)
            WidgetKind.MEDIA -> WidgetConfig(kind, surface = Surface.ART, cornerDp = 32f)
            WidgetKind.LAUNCHER -> WidgetConfig(kind, shape = ShapeKind.PILL, blur = 0.7f)
            WidgetKind.COUNTDOWN -> WidgetConfig(kind, shape = ShapeKind.COOKIE12, surface = Surface.ACCENT, colorRole = ColorRole.TERTIARY)
            WidgetKind.NOTE -> WidgetConfig(kind, cornerDp = 28f, tint = Tint.LIGHT, tintStrength = 0.45f)
            WidgetKind.WORLD -> WidgetConfig(kind, cornerDp = 32f)
            WidgetKind.PROGRESS -> WidgetConfig(kind, shape = ShapeKind.ROUNDED, cornerDp = 36f)
            WidgetKind.SUN -> WidgetConfig(kind, cornerDp = 32f)
            WidgetKind.GLANCE -> WidgetConfig(kind, surface = Surface.NONE)
            WidgetKind.MONTH -> WidgetConfig(kind, cornerDp = 32f)
            WidgetKind.TORCH -> WidgetConfig(kind, shape = ShapeKind.COOKIE9, surface = Surface.GLASS)
            WidgetKind.STORAGE -> WidgetConfig(kind, cornerDp = 36f)
            WidgetKind.TIMER -> WidgetConfig(kind, shape = ShapeKind.PILL)
            WidgetKind.COUNTER -> WidgetConfig(kind, cornerDp = 36f, colorRole = ColorRole.SECONDARY)
        }
    }
}

/** Globale App-Einstellungen. */
@Serializable
data class GlobalSettings(
    val wallpaperMode: WallpaperMode = WallpaperMode.NONE,
    val wallpaperStamp: Long = 0,     // ändert sich, wenn ein neues Hintergrundbild verarbeitet wurde
    val offsetX: Float = 0f,          // Feinjustierung des Wallpaper-Ausschnitts (-0.5..0.5)
    val offsetY: Float = 0f,
    val zoom: Float = 1f,             // 1..1.5
    val systemWallpaperId: Int = -1,
    val pages: Int = 1,               // Homescreen-Seiten, über die das Wallpaper scrollt (1 = kein Scrollen)
    val paletteHash: Int = 0,
)

@Serializable
enum class WallpaperMode { NONE, PICKED, SYSTEM }
