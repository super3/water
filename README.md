# Water Tracker

An Android water-intake tracker — a Jetpack Compose app plus a home-screen widget that share the same data.

## Features

- **Home-screen widget** (Glance) — a circular progress ring ("Variant D": 300° arc, 7→5 o'clock) showing today's total vs. goal, with a flush **+** button to log a quick increment. Tapping the tile opens the app.
- **App** — a Today screen with the progress ring, quick-add chips (+8 / +16 / +32 oz) and a custom-amount sheet, a 7-day "This week" bar chart, and the day's log. Day-stepper in the header to browse past days, tap any entry to edit/delete, and a Settings sheet to change the daily goal.
- Widget and app stay in sync through a shared DataStore.

## Project layout

- `android-widget/` — the Android Studio project
  - `app/src/main/java/com/watertracker/widget/` — the Glance widget (`WaterTrackerWidget`, `AddWaterAction`) and host `MainActivity`
  - `app/src/main/java/com/watertracker/widget/app/` — the Compose app (screens, components, icons, data model, DataStore repository, design tokens)
- `water-tracker-compact.jsx` — the original design reference for the widget ring

## Build

Open `android-widget/` in Android Studio, or from the command line:

```
cd android-widget
./gradlew :app:assembleDebug
```

Requires the Android SDK (set `sdk.dir` in `android-widget/local.properties`).
