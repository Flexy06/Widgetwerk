# Widgetwerk

Material 3 Expressive widget pack for Android 12+ (built for the Pixel 8 Pro) with real frosted glass.

## Widgets
| Widget | What it does |
|---|---|
| Clock | Optional next alarm and short weather line; big digital clock (stacked / one line / time only), 7 fonts, live via `TextClock` |
| Analog clock | Expressive dial shapes, bold hands, optional second hand (`AnalogClock` + `setIcon`) |
| Date | Day & date in a cookie/sunny/… shape or as headline |
| Weather | Open-Meteo (no API key), current + hourly, own expressive weather icons |
| Photo | Google Photos (system photo picker) or files, slideshow, shapes, glass frame |
| Glass | Just frosted glass |
| Shapes | Pure decoration: shape, orbs, stacked shapes, waves, dots, rings – tap to shuffle |
| Calendar | Next events from the phone's calendars |
| Battery | Wavy M3-Expressive charge ring, time-to-full, temperature |
| Now playing | Artwork, title, controls, wavy progress bar (tap to seek), live elapsed time, player buttons like Spotify's ♥ |
| App dock | Up to 8 apps, search & reorder, forced themed icons (generated for apps without one), labels, date |
| Countdown | Days until / since a date |
| Note | Sticky note on glass |
| World clock | Live time in another time zone with offset |
| Time progress | Day, week, month and year as rings or wavy bars |
| Sun & moon | Daylight arc with sun position, sunrise/sunset, moon phase |
| At a glance | Pixel-style: date, weather, next event or alarm |
| Month | Month grid, today in a cookie shape, dots on days with events |
| Flashlight | One-tap torch, glows when on |
| Storage | Wavy ring with free / total storage |
| Timer | Up to 5 preset buttons, starts the timer in the clock app |
| Counter | Count anything (+/−), daily goal ring, optional daily reset |

## How the glass works
Widgets can't blur what's behind them. Widgetwerk keeps a screen-sized copy of the wallpaper
(picked image, or the system wallpaper with "All files access"), cuts out the area behind each
widget and blurs it. The position comes from `Intent.sourceBounds`, which the launcher adds when
you tap a widget (mutable PendingIntent → `TapActivity`). So: tap a glass widget once after
placing or moving it. Without a wallpaper source the widgets fall back to milky glass made from
the dynamic colors.

If the launcher slides the wallpaper between home screen pages (parallax), the glass can only match one
page. Fix: **Lock wallpaper** in the app (sets the image exactly screen-sized → no sliding, no wobble), or
set the number of pages and choose each widget's page.

## Releases
Every push to `main` (or a manual run of **Build & Release APK** in the Actions tab) builds a signed
release APK and publishes it as a GitHub release `v1.0.<run>`. The signing key lives in the repo
secrets (`KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`), so
every release installs over the previous one. Locally the same key is read from `keystore.properties`.

## Build
Open in Android Studio (or `gradlew assembleDebug`). Same toolchain as VolleyHub:
AGP 8.10.1, Kotlin 2.1.21, Compose BOM 2025.05.00, material3 1.4.0-alpha15. minSdk 31, target 36.
Release builds use the release key when available, otherwise the debug key.
