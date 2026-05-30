# Watch ↔ Phone Sync Protocol

The Pebble watchapp (`pebble/`) and the Android app (`android-widget/`) talk over
Bluetooth using Pebble's **AppMessage** protocol, bridged by the Pebble mobile app.
Both sides are tied together by a shared **UUID** and a small set of **message keys**.
This file is the single source of truth for that contract — change it here first,
then update both sides.

## UUID

```
6b329be5-682a-4283-892b-d596790bdfb1
```

- Watch: `pebble/package.json` → `pebble.uuid`
- Android: passed to `PebbleKit.sendDataToPebble(...)` / `PebbleDataReceiver(UUID)`

## Units

All water amounts are in **fluid ounces (oz)**, matching the Android app's data
model (`WaterModel.kt`, `WT_GOAL_DEFAULT = 64`, `WaterRepository.INCREMENT = 8`).

## Message keys

| Key           | Type   | Direction      | Meaning                                              |
| ------------- | ------ | -------------- | ---------------------------------------------------- |
| `TodayOz`     | int32  | phone → watch  | Today's total intake, in oz.                         |
| `GoalOz`      | int32  | phone → watch  | Daily goal, in oz.                                   |
| `LogOz`       | int32  | watch → phone  | User logged this many oz on the watch (e.g. +8).     |
| `RequestSync` | uint8  | watch → phone  | "Send me the current totals." Value is always `1`.   |

On the watch these are referenced as `MESSAGE_KEY_TodayOz`, etc. (generated from
`pebble/package.json` → `pebble.messageKeys`). On Android they are integer indices
in the same order: `TodayOz=0, GoalOz=1, LogOz=2, RequestSync=3`.

## Flows

### Watch opens (pull / handshake)
1. Watch sends `RequestSync = 1`.
2. Phone replies with `TodayOz` + `GoalOz` for today.
3. Watch caches both in persistent storage and renders the ring.

The watch also caches the last-known values, so it shows a correct ring
immediately on launch — even before the phone replies (or if it never does).

### User logs water on the watch
1. Watch optimistically adds `GLASS_OZ` (8) locally and redraws.
2. Watch sends `LogOz = 8`.
3. Phone calls `WaterRepository.addEntry(...)`, then replies with the
   authoritative `TodayOz` (+ `GoalOz`), which the watch adopts.

### User logs water on the phone (app or home-screen widget)
1. Android writes through `WaterRepository` (unchanged existing behavior).
2. Opportunistically pushes `TodayOz` + `GoalOz` to the watch (lands only if the
   watchapp is currently open; otherwise the watch picks it up via the next
   `RequestSync` on launch).

## Delivery notes / caveats

- Requires the Pebble mobile app installed and the watch paired/connected.
- Phone → watch push only arrives while the **watchapp is open**. The
  `RequestSync` handshake on launch is what makes "open the app and see the right
  number" reliable.
- The Android receiver is **manifest-declared**, so the watch can wake it to
  serve a `RequestSync` / `LogOz` even when the Android app is not in the
  foreground.
