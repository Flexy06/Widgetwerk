# CLAUDE.md – Widgetwerk

Private Widget-App (Kotlin, Compose für die App-UI, RemoteViews/XML für die Widgets). minSdk 31.
UI-Texte Englisch, Code-Kommentare Deutsch.

## Architektur
- `data/` – `WidgetConfig` (ein JSON pro Widget-ID in SharedPreferences via `ConfigStore`), Wetter (Open-Meteo),
  Kalender, Fotos (verkleinerte Kopien in `filesDir/photos`).
- `glass/` – Rendering ohne Zusatzbibliotheken: `SurfaceRenderer` (Glas/Milchglas/Tonal/Akzent/Artwork, Tönung, Korn,
  Lichtkante, Masken), `Shapes` (eigene expressive Formen als `Path`), `Blur` (Box-Blur), `WallpaperSource`, `Art`.
- `widget/` – `BaseWidget` + 11 Provider, `WidgetUpdater` (rendert seriell mit Mutex), `Renderers`,
  `TapActivity` (Trampolin: speichert `sourceBounds` für den Glas-Blur), `LiveService` (NotificationListener:
  Medien, Akku, Wallpaper-/Dark-Mode-Wechsel), `RefreshWorker` (30 min) + `CalendarWatcher` (Content-URI-Trigger).
- `ui/` – `MainActivity` (Galerie mit echten RemoteViews-Vorschauen, Glas-Quelle), `ConfigureActivity`.

## Wichtige Entscheidungen
- Schriften in Widgets nur per XML möglich → je Schrift ein Layout `tc_*.xml`, per `addView` eingehängt.
- Uhren brauchen keine Updates (TextClock/AnalogClock). Zeiger-Bitmaps nutzen Bitmap-Dichte, damit sie in voller Größe gezeichnet werden.
- Hintergrund-Bitmaps sind auf `SurfaceRenderer.MAX_PIXELS` begrenzt (RemoteViews-Speicherlimit).
- Klick auf Widget-Root muss MUTABLE sein, sonst fehlen die `sourceBounds`.

## Offene Punkte
- Build in Android Studio prüfen (Code wurde ohne Android-SDK geschrieben).
- CI: `.github/workflows/release.yml` baut bei Push auf main eine signierte APK und veröffentlicht sie als Release `v1.0.<run>`. Keystore liegt lokal in `keystore/` (gitignored) und als Secrets im Repo.
