# Water Tracker

[![Tests](https://img.shields.io/github/actions/workflow/status/super3/water/tests.yml?branch=main&label=tests)](https://github.com/super3/water/actions/workflows/tests.yml)
[![Coverage](https://coveralls.io/repos/github/super3/water/badge.svg?branch=main)](https://coveralls.io/github/super3/water?branch=main)

An Android water-intake tracker — a Jetpack Compose app plus a home-screen widget that share the same data.

## Features

- **Home-screen widget** (Glance) — a circular progress ring showing today's total vs. goal, with a flush **+** to log a quick increment. Tapping the tile opens the app.
- **App** — Today screen with the ring, quick-add chips (+8 / +16 / +32 oz), a custom-amount sheet, a 7-day chart, and the day's log. Step through past days, tap any entry to edit/delete, and set the daily goal in Settings.
- Widget and app stay in sync through a shared DataStore.

## Setup

Requires Android Studio (or the Android SDK + JDK 17).

Open the `android-widget/` folder in Android Studio, or for command-line builds point Gradle at your SDK:

```bash
echo "sdk.dir=/path/to/Android/Sdk" > android-widget/local.properties
```

## Build

```bash
cd android-widget
./gradlew :app:assembleDebug      # build the debug APK
./gradlew :app:installDebug       # install on a connected device/emulator
```

## Testing

```bash
cd android-widget
./gradlew :app:testDebugUnitTest  # local JVM unit tests (no device needed)
```

## Project Layout

| Path | Description |
|------|-------------|
| `android-widget/app/src/main/java/com/watertracker/widget/` | Glance home-screen widget (`WaterTrackerWidget`, `AddWaterAction`) + host `MainActivity` |
| `android-widget/app/src/main/java/com/watertracker/widget/app/` | Compose app — screens, components, icons, data model, DataStore repository, design tokens |
| `android-widget/app/src/test/` | Unit tests for the data/logic layer |
| `water-tracker-compact.jsx` | Original design reference for the widget ring |
