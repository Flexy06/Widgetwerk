# Widgetwerk

Material 3 Expressive widget pack for Android 12+ (built for the Pixel 8 Pro) with real frosted glass.

## Widgets
| Widget | What it does |
|---|---|
| Clock | Big digital clock (stacked / one line / time only), 7 fonts, live via `TextClock` |
| Analog clock | Expressive dial shapes, bold hands, optional second hand (`AnalogClock` + `setIcon`) |
| Date | Day & date in a cookie/sunny/… shape or as headline |
| Weather | Open-Meteo (no API key), current + hourly, own expressive weather icons |
| Photo | Google Photos (system photo picker) or files, slideshow, shapes, glass frame |
| Glass | Just frosted glass |
| Shapes | Pure decoration: shape, orbs, stacked shapes, waves, dots, rings – tap to shuffle |
| Calendar | Next events from the phone's calendars |
| Battery | Wavy M3-Expressive charge ring, time-to-full, temperature |
| Now playing | Artwork, title, controls; artwork-blur background |
| App dock | Up to 6 apps, themed (monochrome) icons, optional date |

## How the glass works
Widgets can't blur what's behind them. Widgetwerk keeps a screen-sized copy of the wallpaper
(picked image, or the system wallpaper with "All files access"), cuts out the area behind each
widget and blurs it. The position comes from `Intent.sourceBounds`, which the launcher adds when
you tap a widget (mutable PendingIntent → `TapActivity`). So: tap a glass widget once after
placing or moving it. Without a wallpaper source the widgets fall back to milky glass made from
the dynamic colors.

## Releases
Every push to `main` (or a manual run of **Build & Release APK** in the Actions tab) builds a signed
release APK and publishes it as a GitHub release `v1.0.<run>`. The signing key lives in the repo
secrets (`KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`), so
every release installs over the previous one. Locally the same key is read from `keystore.properties`.

## Build
Open in Android Studio (or `gradlew assembleDebug`). Same toolchain as VolleyHub:
AGP 8.10.1, Kotlin 2.1.21, Compose BOM 2025.05.00, material3 1.4.0-alpha15. minSdk 31, target 36.
Release builds use the release key when available, otherwise the debug key.
