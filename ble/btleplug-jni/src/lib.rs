//! C ABI shim over `btleplug`.
//!
//! Loaded by the Temperature Dashboard JVM via JNA. Polling design with
//! JSON-encoded events keeps the FFI surface as boring as possible — see
//! `BtleplugNative.kt` on the Kotlin side for the wire format.
//!
//! Cross-platform: btleplug picks the right backend per OS at compile time
//! (WinRT on Windows, BlueZ over D-Bus on Linux, CoreBluetooth on macOS).

use btleplug::api::{Central, CentralEvent, Manager as _, Peripheral as _, ScanFilter};
use btleplug::platform::{Adapter, Manager, Peripheral};
use futures::stream::StreamExt;
use once_cell::sync::Lazy;
use parking_lot::Mutex;
use serde::Serialize;
use std::collections::VecDeque;
use std::os::raw::{c_int, c_void};
use std::ptr;
use std::sync::Arc;
use std::time::Duration;
use tokio::runtime::{Builder, Runtime};
use tokio::sync::Notify;
use tokio::task::JoinHandle;

const RC_OK: c_int = 0;
const RC_EMPTY: c_int = -1;
const RC_ERR: c_int = -2;
const RC_OVERFLOW: c_int = -3;
const RC_NO_ADAPTER: c_int = -4;

static RUNTIME: Lazy<Runtime> = Lazy::new(|| {
    Builder::new_multi_thread()
        .enable_all()
        .worker_threads(2)
        .thread_name("btleplug-jni")
        .build()
        .expect("create tokio runtime")
});

/// Opaque handle handed back to the JVM. All FFI calls take a `*mut Handle`.
struct Handle {
    inner: Arc<Inner>,
}

struct Inner {
    events: Mutex<VecDeque<String>>,
    notify: Notify,
    scan_task: Mutex<Option<JoinHandle<()>>>,
    adapter: Mutex<Option<Adapter>>,
}

#[derive(Serialize)]
struct AdvertJson {
    address: String,
    name: Option<String>,
    rssi: i32,
    #[serde(rename = "timestampMs")]
    timestamp_ms: i64,
    #[serde(rename = "serviceUuids")]
    service_uuids: Vec<String>,
    /// company-id (hex, no 0x) → bytes (hex)
    #[serde(rename = "manufacturerData")]
    manufacturer_data: std::collections::HashMap<String, String>,
    /// service-uuid (lowercase) → bytes (hex)
    #[serde(rename = "serviceData")]
    service_data: std::collections::HashMap<String, String>,
}

// ---------------------------------------------------------------------------- C ABI

/// Allocate a fresh handle.
#[no_mangle]
pub extern "C" fn btleplug_open() -> *mut c_void {
    let inner = Arc::new(Inner {
        events: Mutex::new(VecDeque::with_capacity(256)),
        notify: Notify::new(),
        scan_task: Mutex::new(None),
        adapter: Mutex::new(None),
    });
    let h = Box::new(Handle { inner });
    Box::into_raw(h) as *mut c_void
}

/// Drop the handle. After this the pointer must not be used.
#[no_mangle]
pub extern "C" fn btleplug_close(handle: *mut c_void) {
    if handle.is_null() {
        return;
    }
    unsafe {
        let h = Box::from_raw(handle as *mut Handle);
        if let Some(task) = h.inner.scan_task.lock().take() {
            task.abort();
        }
        drop(h);
    }
}

/// Begin scanning. Returns 0 on success.
#[no_mangle]
pub extern "C" fn btleplug_start_scan(handle: *mut c_void) -> c_int {
    let h = match unsafe { handle.cast::<Handle>().as_ref() } {
        Some(r) => r,
        None => return RC_ERR,
    };
    let inner = h.inner.clone();
    let mut task_slot = inner.scan_task.lock();
    if task_slot.is_some() {
        return RC_OK; // already running, idempotent
    }
    let inner_clone = inner.clone();
    let join = RUNTIME.spawn(async move {
        if let Err(err) = run_scan(inner_clone).await {
            log::warn!("btleplug scan loop ended: {err:?}");
        }
    });
    *task_slot = Some(join);
    RC_OK
}

/// Stop scanning. Safe to call multiple times.
#[no_mangle]
pub extern "C" fn btleplug_stop_scan(handle: *mut c_void) -> c_int {
    let h = match unsafe { handle.cast::<Handle>().as_ref() } {
        Some(r) => r,
        None => return RC_ERR,
    };
    if let Some(task) = h.inner.scan_task.lock().take() {
        task.abort();
    }
    if let Some(adapter) = h.inner.adapter.lock().as_ref() {
        let _ = RUNTIME.block_on(adapter.stop_scan());
    }
    RC_OK
}

/// Pop the next event into `buf` (JSON, UTF-8). Returns:
/// - `>0`  bytes written
/// - `-1`  queue empty
/// - `-2`  fatal error
/// - `-3`  event larger than `buf_len` (event dropped)
#[no_mangle]
pub extern "C" fn btleplug_next_event(
    handle: *mut c_void,
    buf: *mut u8,
    buf_len: usize,
) -> c_int {
    let h = match unsafe { handle.cast::<Handle>().as_ref() } {
        Some(r) => r,
        None => return RC_ERR,
    };
    let mut queue = h.inner.events.lock();
    let Some(event) = queue.pop_front() else { return RC_EMPTY };
    let bytes = event.as_bytes();
    if bytes.len() > buf_len {
        return RC_OVERFLOW;
    }
    unsafe { ptr::copy_nonoverlapping(bytes.as_ptr(), buf, bytes.len()) };
    bytes.len() as c_int
}

// ---------------------------------------------------------------------------- scan loop

async fn run_scan(inner: Arc<Inner>) -> btleplug::Result<()> {
    let manager = Manager::new().await?;
    let adapters = manager.adapters().await?;
    let central = match adapters.into_iter().next() {
        Some(a) => a,
        None => {
            log::warn!("no BLE adapter found");
            return Ok(());
        }
    };
    *inner.adapter.lock() = Some(central.clone());

    let mut events = central.events().await?;
    central.start_scan(ScanFilter::default()).await?;

    while let Some(event) = events.next().await {
        match event {
            CentralEvent::DeviceDiscovered(id)
            | CentralEvent::DeviceUpdated(id)
            | CentralEvent::ManufacturerDataAdvertisement { id, .. }
            | CentralEvent::ServiceDataAdvertisement { id, .. }
            | CentralEvent::ServicesAdvertisement { id, .. } => {
                if let Ok(peripheral) = central.peripheral(&id).await {
                    if let Some(json) = encode_event(&peripheral).await {
                        push_event(&inner, json);
                    }
                }
            }
            _ => {}
        }
    }
    Ok(())
}

async fn encode_event(peripheral: &Peripheral) -> Option<String> {
    let properties = peripheral.properties().await.ok().flatten()?;
    let address = peripheral.address().to_string().to_uppercase();
    let timestamp_ms = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_else(|_| Duration::ZERO)
        .as_millis() as i64;

    let manufacturer_data = properties
        .manufacturer_data
        .iter()
        .map(|(k, v)| (format!("{:x}", k), hex::encode(v)))
        .collect();
    let service_data = properties
        .service_data
        .iter()
        .map(|(uuid, v)| (uuid.to_string().to_lowercase(), hex::encode(v)))
        .collect();
    let service_uuids = properties
        .services
        .iter()
        .map(|u| u.to_string().to_uppercase())
        .collect();

    let advert = AdvertJson {
        address,
        name: properties.local_name,
        rssi: properties.rssi.unwrap_or(0) as i32,
        timestamp_ms,
        service_uuids,
        manufacturer_data,
        service_data,
    };
    serde_json::to_string(&advert).ok()
}

fn push_event(inner: &Arc<Inner>, json: String) {
    const MAX_BACKLOG: usize = 1024;
    let mut q = inner.events.lock();
    if q.len() >= MAX_BACKLOG {
        q.pop_front();
    }
    q.push_back(json);
    inner.notify.notify_one();
}
