# Temperature Dashboard

A universal Bluetooth temperature & humidity sensor dashboard. Runs on **Android**, **Windows**, **Linux**, and **macOS** (best-effort) from a single Kotlin codebase.

Built for any BLE thermo/hygro sensor — first-class support for **ThermoPro TP35x**, **BTHome v2** (Shelly, ESPHome, DIY), **BLE Environmental Sensing Service**, and **BLE Health Thermometer** out of the box.

## Why this app

- Sensors broadcast continuously; the app collects everything in the background and keeps history **forever** with no cloud.
- Storage uses interval-based run-length encoding: when the value doesn't change, no new row — the existing interval just extends its `valid_until` timestamp. A steady reading overnight is **one row**, not 14,400.
- Chart any zoom level (hour → year) with no precomputed aggregates needed. Stats are exact (time-weighted).
- Gaps in coverage close cleanly: if a sensor goes offline, the interval freezes and the chart shows a real gap, not stale bogus data.

## Stack

- **Kotlin 2.1** · **Kotlin Multiplatform** · **Compose Multiplatform 1.8** · JDK 21
- **Material 3 Expressive** on Android, stock M3 on Desktop (graceful degradation)
- **Room 2.7+ KMP** with bundled SQLite for cross-platform persistence
- **Koin** for DI
- **KoalaPlot** for KMP-native charts
- **AGP 8.7.3** · Gradle 8.13 · KSP 2 · convention plugins
- Android BLE: native `android.bluetooth`
- Desktop BLE: Rust **`btleplug`** via JNI (single binary covers Win/Linux/Mac)

## Module layout

```
:app
  :app:android         — Android Application
  :app:desktop         — Compose Multiplatform Desktop (tray-icon mode)
:shared                — KMP glue: navigation, DI graph, repository wiring
:core
  :core:model          — domain types (KMP)
  :core:database       — Room schema + DAOs (KMP)
  :core:datastore      — preferences via Multiplatform Settings (KMP)
  :core:designsystem   — theme, tokens, color schemes (KMP)
  :core:ui             — shared composables, chart wrappers (KMP)
:decoder
  :decoder:api         — DeviceProfile, AdvertisementSnapshot interfaces (JVM)
  :decoder:builtins    — ThermoPro / BTHome v2 / ESS / Health Thermometer (JVM)
:ble
  :ble:api             — BleScanner, BleConnection interfaces (KMP)
  :ble:android         — android.bluetooth implementation
  :ble:desktop         — btleplug JNI implementation
:feature
  :feature:dashboard   — grid of sensor cards
  :feature:scan        — discover + pair flow
  :feature:detail      — per-sensor history, charts, stats
  :feature:settings    — units, retention, theme

build-logic             — Gradle convention plugins (tdash.*)
```

## Build

```powershell
./gradlew :app:android:assembleDebug                      # Android debug APK
./gradlew :app:android:installDebug                       # install on device
./gradlew :app:desktop:run                                # run desktop app
./gradlew :app:desktop:packageDistributionForCurrentOs    # build native installer
./gradlew :decoder:builtins:test                          # decoder tests
```

## Status

🟢 **Phase 0 — Scaffold complete.** 19 modules compile (Android + Desktop). Hello-world entry points run on both platforms.

🟡 Phases 1-11 in progress — see `plan.md` for the roadmap.

## License

See [LICENSE](LICENSE).
