# CLAUDE.md – Widgetwerk

Private Widget-App (Kotlin, Compose für die App-UI, RemoteViews/XML für die Widgets). minSdk 31.
UI-Texte Englisch, Code-Kommentare Deutsch.

## Architektur
- `data/` – `WidgetConfig` (ein JSON pro Widget-ID in SharedPreferences via `ConfigStore`), Wetter (Open-Meteo),
  Kalender, Fotos (verkleinerte Kopien in `filesDir/photos`).
- `glass/` – Rendering ohne Zusatzbibliotheken: `SurfaceRenderer` (Glas/Milchglas/Tonal/Akzent/Artwork, Tönung, Korn,
  Lichtkante, Masken), `Shapes` (eigene expressive Formen als `Path`), `Blur` (Box-Blur), `WallpaperSource`, `Art`.
- `widget/` – `BaseWidget` + 22 Provider (`MoreRenderers`: Countdown, Notiz, Weltuhr, Fortschritt, Sonne & Mond; `GadgetRenderers`: At a glance, Monat, Taschenlampe, Speicher, Timer, Zähler; `Gadgets`: Wecker/Taschenlampe), `WidgetUpdater` (rendert seriell mit Mutex), `Renderers`,
  `TapActivity` (Trampolin: speichert `sourceBounds` für den Glas-Blur), `LiveService` (NotificationListener:
  Medien, Akku, Wallpaper-/Dark-Mode-Wechsel), `RefreshWorker` (30 min) + `CalendarWatcher` (Content-URI-Trigger).
- `ui/` – `MainActivity` (Galerie mit echten RemoteViews-Vorschauen, Glas-Quelle), `ConfigureActivity`.

## Wichtige Entscheidungen
- Schriften in Widgets nur per XML möglich → je Schrift ein Layout `tc_*.xml`, per `addView` eingehängt.
- Uhren brauchen keine Updates (TextClock/AnalogClock). Zeiger-Bitmaps nutzen Bitmap-Dichte, damit sie in voller Größe gezeichnet werden.
- Hintergrund-Bitmaps sind auf `SurfaceRenderer.MAX_PIXELS` begrenzt (RemoteViews-Speicherlimit).
- Klick auf Widget-Root muss MUTABLE sein, sonst fehlen die `sourceBounds`.
- Wallpaper-Cache hat Bildschirmhöhe, Breite bleibt bei Parallaxe erhalten; Ausschnitt je Seite: `WallpaperSource.visibleLeft` (xOffset = page/(pages-1)).
  "Wallpaper fixieren" setzt das Bild exakt bildschirmgroß (SET_WALLPAPER) → keine Parallaxe.
- Medien-Fortschritt: RemoteViews kennen keine Slider → Wellen-Bitmap + 12 unsichtbare Tippzonen (Seek), Chronometer für die laufende Zeit,
  LiveService zeichnet alle 5 s neu (nur bei Wiedergabe + Bildschirm an).
- PendingIntent-RequestCodes: id*64 + Slot (1–3 Medien, 4–5 Player-Aktionen, 6–17 Seek, 20+ Kalender/Dock).
- Erzwungene Themed Icons: `Art.forcedMonochrome` (Vordergrund-Silhouette bzw. Farbabstand zum Hintergrund als Alpha).
- Nächster Wecker: `AlarmChangedReceiver` (NEXT_ALARM_CLOCK_CHANGED, im Manifest erlaubt) aktualisiert Uhr + At a glance.
- Taschenlampe: CameraManager.setTorchMode ohne Berechtigung; Zustand in Prefs, LiveService verfolgt per TorchCallback.
- Compile-Check ohne Gradle möglich: kotlinc (K2JVMCompiler) gegen android.jar + AAR-classes.jar + generierte R-Klasse.

## Offene Punkte
- Build in Android Studio prüfen (Code wurde ohne Android-SDK geschrieben).
- CI: `.github/workflows/release.yml` baut bei Push auf main eine signierte APK und veröffentlicht sie als Release `v1.0.<run>`. Keystore liegt lokal in `keystore/` (gitignored) und als Secrets im Repo.
