# Dia

A simple Android app for people using a **FreeStyle Libre 2**. It pulls your CGM readings in through
**LibreLinkUp** and gives you a quick way to log insulin, food, activity and other stats, with a clean dashboard.

> Dia is a personal logbook, **not a medical device**, and is not affiliated with Abbott.
> Always confirm with your sensor or meter before making treatment decisions.

## Features

- **Live glucose**: current reading, trend arrow, time since the reading and change since the last one. The number is colored by range.
- **Chart**: last 3/6/12/24 h with your target band, plus insulin, carb and activity markers.
- **Logging in a few taps**: one sheet for
  - **Insulin**: rapid or long-acting (you can rename them), +/- stepper, quick-pick doses
  - **Food**: carbs with quick-pick amounts, meal tag (Breakfast / Lunch / Dinner / Snack / Hypo treat)
  - **Activity**: type, minutes, intensity
  - **Other**: fingerstick BG, weight, ketones, free-text notes
  - "When": now, 15 min, 30 min, 1 h or 2 h ago
- **Today summary**: time in range, average, rapid and long-acting units, carbs, active minutes.
- **Stats (7/14/30/90 days)**: average, GMI (estimated A1c), variability (CV), a time-in-ranges bar,
  a daily pattern chart (median with 25–75% and 10–90% bands), and daily insulin/carb/activity averages.
- **History**: entries grouped by day with daily totals. You can delete an entry and undo it.
- **Background sync** every 15 minutes, or pull down to refresh.
- **Import** a LibreView CSV export to backfill months of data (including insulin and carbs logged in LibreLink).
  **Export** everything to CSV.
- mg/dL or mmol/L, adjustable target range, Material You colors, dark mode.

## Connecting your Libre 2

Dia reads data the same way the official **LibreLinkUp** follower app does:

1. Install **LibreLinkUp** from the Play Store and create an account. Using a *different* email from your
   LibreLink account is the most reliable.
2. In your **FreeStyle LibreLink** app: *Menu → Connected Apps → LibreLinkUp → Add connection* and invite that email.
3. Open LibreLinkUp, accept the invite and any terms it shows.
4. In Dia: **Settings → LibreLinkUp**, then log in with the LibreLinkUp email and password.

Your password is only sent to LibreView to get a session token. Dia stores the token, not the password, and
leaves the token out of Android cloud backups. Readings appear about once a minute on LibreView's side, and Dia
fetches the latest ~12 hours each time it syncs.

If login fails with a version or 403 error, LibreView may have started requiring a newer app version. Enter the
current LibreLinkUp version from the Play Store under **Settings → Advanced**.

### Backfilling history

On [libreview.com](https://www.libreview.com), go to *Glucose History → Download glucose data*, then in Dia use
**Settings → Your data → Import LibreView CSV**. Both US (comma, mg/dL, AM/PM) and European (semicolon, mmol/L,
24 h, decimal comma) exports are supported. Re-importing the same file does not create duplicates.

## Building

Requirements: JDK 17+ and the Android SDK (Android Studio Ladybug or newer is easiest).

```bash
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests for parsing and stats
```

Every push runs the GitHub Actions workflow **Android build**. Download the APK from the run's
**dia-debug-apk** artifact and install it on your phone. You'll need to allow installs from unknown sources.

## Tech

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · WorkManager · OkHttp · kotlinx.serialization.
Min Android 8.0 (API 26).

```
app/src/main/java/app/dia/
├── data/
│   ├── db/            Room entities + DAOs (glucose readings, log entries)
│   ├── libre/         LibreLinkUp API client, LibreView CSV parser
│   ├── settings/      DataStore-backed preferences + session
│   └── DiaRepository.kt
├── domain/            Units, ranges, stats (TIR, GMI, CV, hourly profile)
├── sync/              Background sync worker
└── ui/                Compose screens: home, log sheet, history, stats, settings
```
