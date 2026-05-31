# Temperature Dashboard

A universal Bluetooth temperature & humidity sensor dashboard. Runs on **Android**, **Windows**, **Linux**, and **macOS** from a single Kotlin codebase. Local-only, no cloud, history kept forever.

First-class support for **ThermoPro TP35x**, **BTHome v2** (Shelly, ESPHome, DIY), **BLE Environmental Sensing Service**, and **BLE Health Thermometer** out of the box — one app for every common consumer BLE thermometer/hygrometer.

## Why this app exists

- Sensors broadcast continuously. The app collects everything in the background and keeps full history forever — no cloud, no account, no sync.
- Storage uses **interval-based run-length encoding**: when the value doesn't change, no new row — the existing interval just extends its `valid_until` timestamp. A steady reading overnight is **one row**, not 14,400.
- Charts are accurate at any zoom (hour → year) with no precomputed aggregates needed. Stats are exact — time-weighted averages and per-row min/max.
- Gaps in coverage close cleanly: if a sensor goes silent for longer than the configurable stale window (5 min default), the interval freezes and the chart shows a **real gap**, not a stale held value.

## Stack

- **Kotlin 2.1** · **Kotlin Multiplatform** · **Compose Multiplatform 1.8** · JDK 21
- **Material 3** with dynamic colour on Android 12+, warm-coral fallback elsewhere
- **Room 2.7 KMP** with bundled SQLite — same schema everywhere
- **Koin 4** for DI · **Multiplatform Settings** for preferences
- **Napier** for cross-platform logging
- **AGP 8.7.3** · Gradle 8.13 · KSP 2 · convention plugins under `build-logic/`
- BLE — Android: native `android.bluetooth` with foreground service
- BLE — Desktop: Rust **`btleplug`** via JNA (single binary covers Win/Linux/Mac)

## Architecture

```
:app
  :app:android         Android Application (Compose Activity + foreground scan service)
  :app:desktop         Compose Multiplatform Desktop (Window + tray-icon-mode roadmap)
:shared                KMP glue — navigation, DI graph, integration layer
:core
  :core:model          domain types (Sensor, Reading, IntervalStats, Celsius, …)
  :core:database       Room schema + DAOs + IntervalCoalescer (the RLE engine)
  :core:datastore      preferences via Multiplatform Settings
  :core:designsystem   theme, tokens, color schemes
  :core:ui             reusable composables (SensorCard, Sparkline, TemperatureBigDisplay)
:decoder
  :decoder:api         DeviceProfile, AdvertisementSnapshot, Decoder, …
  :decoder:builtins    ThermoPro · BTHome v2 · ESS · Health Thermometer · 10 more SIG decoders
:ble
  :ble:api             BleScanner, BluetoothAdapterMonitor interfaces (KMP)
  :ble:android         android.bluetooth implementation + ScanForegroundService
  :ble:desktop         JNA bridge to btleplug-jni
  :ble:btleplug-jni    Rust cdylib (cargo) compiled by CI per OS
:feature
  :feature:dashboard   grid of SensorCards
  :feature:scan        discover + add flow
  :feature:detail      per-sensor history, charts, stats
  :feature:settings    units, retention, about
build-logic            Gradle convention plugins (tdash.*)
```

## The interval-RLE storage model

Most dashboards either (a) write every advertisement and let the DB grow without bound, or (b) downsample after the fact and lose precision. This app does neither.

Each row in `reading_interval` describes a *run* of equal-ish values:

```sql
CREATE TABLE reading_interval (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  sensor_id     INTEGER NOT NULL,
  temperature_c REAL,
  humidity_pct  REAL,
  battery_pct   INTEGER,
  rssi_avg      INTEGER,
  valid_from    INTEGER NOT NULL,  -- epoch millis
  valid_until   INTEGER NOT NULL,
  sample_count  INTEGER NOT NULL,
  source        TEXT NOT NULL
);
```

`IntervalCoalescer` extends the latest row's `valid_until` as long as new samples stay within threshold (0.1 °C / 1 % RH by default). When the value drifts, the sensor goes silent past the stale window (5 min), or the interval hits its max duration (6 h), the row freezes and a fresh one starts.

This gives O(*changes*), not O(*samples*) — orders of magnitude smaller than the naïve scheme — while preserving full chart fidelity and exact time-weighted statistics. See `core/database/src/commonMain/.../IntervalCoalescer.kt` for the rules, and the 13 unit tests right next to it for the spec.

## Build

```powershell
# Android debug APK
./gradlew :app:android:assembleDebug
# Install on a connected device
./gradlew :app:android:installDebug

# Desktop — runnable jar (also auto-builds when you do packageDistributionForCurrentOs)
./gradlew :app:desktop:run
./gradlew :app:desktop:packageDistributionForCurrentOs   # produces native .msi/.deb/.dmg

# Unit tests (all green as of v0.1.0)
./gradlew :core:model:desktopTest :core:database:desktopTest
./gradlew :decoder:api:test :decoder:builtins:test

# Native btleplug-JNI library — see ble/btleplug-jni/README.md
cd ble/btleplug-jni && cargo build --release
```

## Status

🟢 **v0.1.0 — end-to-end functional.** Both Android (APK) and Desktop (jar + native installer) build green from a single commit. CI workflows live under `.github/workflows/`.

Done:
- ✅ Phase 0 — scaffold, 19 modules, both targets compile
- ✅ Phase 1 — domain types, Room KMP schema, IntervalCoalescer + 13 tests
- ✅ Phase 2 — decoder pipeline + 4 builtin decoders lifted from BluetoothEvaluator
- ✅ Phase 3 — BLE abstraction · Android impl · btleplug Rust + JNA shim for Desktop
- ✅ Phase 4 — SensorRepository, ReadingRepository, ScanningCoordinator, Koin DI
- ✅ Phase 5 — design system (tokens, theme, M3) + responsive shell (NavBar / NavRail)
- ✅ Phase 6 — Dashboard (responsive grid of SensorCards with sparklines)
- ✅ Phase 7 — Scan & add-sensor flow
- ✅ Phase 8 — SensorDetail with stepped chart, range chips, time-weighted stats
- ✅ Phase 9 — Settings (units, coalescing thresholds, stale window, about)
- ✅ Phase 10 — adaptive launcher icon + polish (still iterating; see roadmap)
- ✅ Phase 11 — CI (PR check) + Release (per-OS native installers + APK + AAB)

Roadmap:
- Desktop tray-icon background mode (the native installer ships now; tray is next)
- M3 Expressive components (FAB Menu, spring physics) once Compose Multiplatform tracks androidx.compose.material3 1.4+
- Per-sensor threshold alerts (notification when temp crosses a user-set line)
- iOS port (KMP-ready already; just needs `iosMain` + a Mac to build)

## License

See [LICENSE](LICENSE).
