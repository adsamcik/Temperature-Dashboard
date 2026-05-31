# btleplug-jni

Cross-platform BLE shim that the Temperature Dashboard desktop JVM loads via JNA.

## Why this exists

There is no production-quality, cross-OS BLE library for the JVM. `btleplug`
(Rust, BSD-3) **is** that library — it cleanly covers Windows (WinRT), Linux
(BlueZ over D-Bus), and macOS (CoreBluetooth) from a single API.

`btleplug-jni` exposes a deliberately small C ABI on top of `btleplug` so
the JVM can poll BLE events without any JNI plumbing or callback marshalling.

## Build

```bash
# From this directory:
cargo build --release

# The resulting library lives at:
#   target/release/btleplug_jni.dll       (Windows)
#   target/release/libbtleplug_jni.so     (Linux)
#   target/release/libbtleplug_jni.dylib  (macOS)
```

The CI workflow under `.github/workflows/release.yml` builds it for each OS
and packages it into the corresponding installer (`.msi`, `.deb`, `.dmg`)
so end users never see Rust.

## ABI

```c
void*  btleplug_open(void);
void   btleplug_close(void* handle);
int    btleplug_start_scan(void* handle);
int    btleplug_stop_scan(void* handle);
/// Returns >0 bytes written, -1 empty, -2 error, -3 buffer too small.
int    btleplug_next_event(void* handle, uint8_t* buf, size_t buf_len);
```

Events are JSON-encoded `AdvertJson` structs — see `src/lib.rs` for the
shape, and `BtleplugNative.kt` on the JVM side for the consumer.

## Why polling + JSON instead of callbacks + structs

- No callback marshalling means no thread-of-control questions between Rust
  Tokio tasks and the JVM dispatcher.
- No struct layout to keep in sync — change a field in Rust, change a field
  in Kotlin, done.
- Performance cost is negligible: BLE advertisements arrive at single-digit
  Hz per device, total event rates fit easily in a 50 ms poll loop.
