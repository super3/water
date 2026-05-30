# Watch ↔ Phone Sync Protocol

The Pebble watchapp (`pebble/`) and the Android app (`android-widget/`) exchange
data through the **Core Devices "Pebble" app** using **PebbleKit 2**
(`io.rebble.pebblekit2`). The watch speaks Pebble's **AppMessage** protocol to the
Core app over Bluetooth; the Core app relays those messages to/from our Android app
via a **bound service** (not the old broadcast-based classic PebbleKit — the Core
app does not implement that path).

This file is the single source of truth for the contract — change it here first,
then update both sides.

## UUID

```
6b329be5-682a-4283-892b-d596790bdfb1
```

- Watch: `pebble/package.json` → `pebble.uuid`
- Android: `PebbleProtocol.APP_UUID` in `PebbleSync.kt`

## Authorizing the Android app (required)

The Core app only relays messages to an Android package that the watchapp lists as
its companion. `pebble/package.json` must contain:

```json
"companionApp": { "android": { "url": "...", "apps": [ { "package": "com.watertracker.widget" } ] } }
```

This must be present in the **built bundle's** `appinfo.json` — run `pebble clean`
before `pebble build` if you change it, since an incremental build can ship a stale
`appinfo.json` and silently break relaying.

## Units

All water amounts are in **fluid ounces (oz)**, matching the Android data model
(`WaterModel.kt`, `WT_GOAL_DEFAULT = 64`, `WaterRepository.INCREMENT = 8`).

## Message keys

The Pebble SDK assigns each `messageKeys` entry an integer starting at **10000**, in
the order listed in `pebble/package.json` (see `pebble/build/js/message_keys.json`).
Both sides must use these exact numbers — they are NOT 0-based indices.

| Key           | Number | Type   | Direction      | Meaning                                            |
| ------------- | ------ | ------ | -------------- | -------------------------------------------------- |
| `TodayOz`     | 10000  | int32  | phone → watch  | Today's total intake, in oz.                       |
| `GoalOz`      | 10001  | int32  | phone → watch  | Daily goal, in oz.                                 |
| `LogOz`       | 10002  | int32  | watch → phone  | Log a glass: add this many oz (UP = +8).           |
| `RequestSync` | 10003  | uint8  | watch → phone  | "Send me the current totals." Value is always `1`. |
| `RemoveLast`  | 10004  | uint8  | watch → phone  | Delete the most recent entry logged today (DOWN).  |
| `WatchSeq`    | 10005  | int32  | watch → phone  | Unique, monotonic op id (de-dup key). Sent with `LogOz`/`RemoveLast`. |
| `LogTs`       | 10006  | int32  | watch → phone  | When the op was logged on the watch (unix seconds), sent with `LogOz`. |

On the watch these are `MESSAGE_KEY_TodayOz`, etc. On Android they are the `UInt`
constants in `PebbleProtocol` (`KEY_TODAY_OZ = 10000u`, …). Numbers received from the
watch always arrive as `Int32` or `UInt32` regardless of their size on the watch.

## Flows

### Watch opens
- The Core app calls the Android listener's `onAppOpened`, which pushes
  `TodayOz` + `GoalOz`.
- The watch also sends `RequestSync = 1` on launch as a belt-and-suspenders pull.
- The watch caches the last-known values in persistent storage, so the ring is
  correct immediately on launch even before the phone replies.

### Offline-tolerant logging (the watch's sync layer)

The watch keeps a **persistent queue** of pending "add a glass" ops so logs made
while the phone is locked / out of range are never lost and the ring never
reverts. Each op carries a unique monotonic `WatchSeq` and the `LogTs` it was
logged at.

- **UP** appends an op (`+8`), bumps the displayed total immediately, and tries
  to flush.
- **Flush** sends the front op (`LogOz` + `WatchSeq` + `LogTs`), one in flight at
  a time. The op is removed from the queue only when the phone **ACKs** it
  (PebbleKit 2 turns our `ReceiveResult.Ack` into the AppMessage ACK). Flush is
  retried on launch, on a ~10s timer while open, and whenever a message arrives.
- The watch **ignores incoming `TodayOz` while its queue is non-empty / a send is
  in flight** — otherwise the echo would clobber an optimistic log that hasn't
  reached the phone yet (this was the "ring reverts" bug). Once the queue drains,
  the next echo re-bases the total.
- **DOWN** cancels the most recent *unsynced* op locally if one exists; otherwise
  it sends `RemoveLast` (online only) to delete the phone's most recent entry.

On the phone, `onMessageReceived`:
1. Reads `WatchSeq`. If `seq <= lastWatchSeq` (a resent op after a lost ACK), it
   **skips applying** but still ACKs + echoes, so the watch advances.
2. Otherwise applies the op — `addEntry(amount, LogTs * 1000)` (recorded at the
   watch's timestamp so it lands on the right day) or deletes the latest entry —
   then stores `seq` as the new `lastWatchSeq`.
3. Pushes the authoritative `TodayOz` + `GoalOz` back.

`WatchSeq` is seeded from the watch clock on first run, so it keeps increasing
across reinstalls and the phone's high-water mark stays valid.

### User logs water on the phone (app or home-screen widget)
1. Android writes through `WaterRepository` (existing behavior, unchanged).
2. `pushStateToPebble(...)` sends `TodayOz` + `GoalOz` to the watch — hooked into
   the widget action and the app's `persist()` chokepoint.

## Delivery notes / caveats

- Requires the Core Devices Pebble app installed and the watch paired/connected.
- **Phone → watch push only lands while our watchapp is open** — PebbleKit 2 returns
  `FailedDifferentAppOpen` otherwise. `onAppOpened` + `RequestSync` make "open the
  app and see the right number" reliable.
- Android side: a `BasePebbleListenerService` registered in the manifest with the
  `io.rebble.pebblekit2.RECEIVE_DATA_FROM_WATCH` intent filter; the Core app binds
  it on demand (works even when our app isn't foregrounded).
- Sending uses `DefaultPebbleSender`; the target Core app is auto-selected via
  `DefaultPebbleAndroidAppPicker`.
