//! C ABI shim over `btleplug`.
//!
//! Loaded by the Temperature Dashboard JVM via JNA. Polling design with
//! JSON-encoded events keeps the FFI surface as boring as possible — see
//! `BtleplugNative.kt` on the Kotlin side for the wire format.
//!
//! Cross-platform: btleplug picks the right backend per OS at compile time
//! (WinRT on Windows, BlueZ over D-Bus on Linux, CoreBluetooth on macOS).
//!
//! GATT extension (v0.3.x): on top of the original scan API, the shim now
//! supports connection-oriented operations needed for the ThermoPro
//! history-backfill feature. The added entrypoints (`btleplug_connect`,
//! `btleplug_subscribe`, `btleplug_write_char`, `btleplug_next_notification`,
//! `btleplug_disconnect`) all key off a per-connection id allocated on the
//! Handle. Notifications drain into a dedicated VecDeque so the JVM can poll
//! them independently of the scan event queue.

use btleplug::api::{
    Central, CentralEvent, Characteristic, Manager as _, Peripheral as _, ScanFilter, WriteType,
};
use btleplug::platform::{Adapter, Manager, Peripheral};
use futures::stream::StreamExt;
use once_cell::sync::Lazy;
use parking_lot::Mutex;
use serde::Serialize;
use std::collections::{HashMap, VecDeque};
use std::os::raw::{c_int, c_void};
use std::ptr;
use std::str;
use std::sync::Arc;
use std::time::Duration;
use tokio::runtime::{Builder, Runtime};
use tokio::sync::Notify;
use tokio::task::JoinHandle;
use uuid::Uuid;

const RC_OK: c_int = 0;
const RC_EMPTY: c_int = -1;
const RC_ERR: c_int = -2;
const RC_OVERFLOW: c_int = -3;
const RC_NO_ADAPTER: c_int = -4;
const RC_NOT_CONNECTED: c_int = -5;
const RC_SERVICE_NOT_FOUND: c_int = -6;
const RC_CHARACTERISTIC_NOT_FOUND: c_int = -7;
const RC_INVALID_UTF8: c_int = -8;
const RC_INVALID_UUID: c_int = -9;

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
    next_conn_id: Mutex<i32>,
    connections: Mutex<HashMap<i32, Arc<Connection>>>,
}

struct Connection {
    id: i32,
    peripheral: Peripheral,
    notifications: Mutex<VecDeque<NotificationEvent>>,
    notify_task: Mutex<Option<JoinHandle<()>>>,
    /// characteristic UUID → owning service UUID, built at discover time.
    /// btleplug's ValueNotification only carries the characteristic UUID;
    /// we look the service up from this map so JVM consumers can filter by
    /// (service, char) like the Android side does.
    char_to_service: Mutex<HashMap<Uuid, Uuid>>,
}

#[derive(Clone)]
struct NotificationEvent {
    service_uuid: String,
    characteristic_uuid: String,
    bytes: Vec<u8>,
}

#[derive(Serialize)]
struct NotificationJson<'a> {
    #[serde(rename = "serviceUuid")]
    service_uuid: &'a str,
    #[serde(rename = "characteristicUuid")]
    characteristic_uuid: &'a str,
    /// hex-encoded payload bytes
    bytes: String,
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
        next_conn_id: Mutex::new(1),
        connections: Mutex::new(HashMap::new()),
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
        let connections: Vec<Arc<Connection>> = h.inner.connections.lock().values().cloned().collect();
        for conn in connections {
            if let Some(task) = conn.notify_task.lock().take() {
                task.abort();
            }
            let _ = RUNTIME.block_on(conn.peripheral.disconnect());
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

/// Connect to a peripheral and discover services. Returns the connection id
/// (>0) or a negative error code.
#[no_mangle]
pub extern "C" fn btleplug_connect(
    handle: *mut c_void,
    address: *const u8,
    address_len: usize,
) -> c_int {
    let h = match unsafe { handle.cast::<Handle>().as_ref() } {
        Some(r) => r,
        None => return RC_ERR,
    };
    let address = match read_str(address, address_len) {
        Some(s) => s,
        None => return RC_INVALID_UTF8,
    };

    // Make sure we have an adapter (scan may not have run yet).
    let inner = h.inner.clone();
    let adapter = match ensure_adapter(&inner) {
        Some(a) => a,
        None => return RC_NO_ADAPTER,
    };

    let result: Result<Arc<Connection>, c_int> = RUNTIME.block_on(async move {
        let peripherals = adapter.peripherals().await.map_err(|err| {
            log::warn!("adapter.peripherals() failed: {err:?}");
            RC_ERR
        })?;
        let target = address.to_uppercase();
        let mut found: Option<Peripheral> = None;
        for p in peripherals {
            if p.address().to_string().to_uppercase() == target {
                found = Some(p);
                break;
            }
        }
        let peripheral = match found {
            Some(p) => p,
            None => {
                // Not in cache — kick off a short scan to populate it.
                let _ = adapter.start_scan(ScanFilter::default()).await;
                tokio::time::sleep(Duration::from_millis(2000)).await;
                let mut hit: Option<Peripheral> = None;
                if let Ok(after) = adapter.peripherals().await {
                    for p in after {
                        if p.address().to_string().to_uppercase() == target {
                            hit = Some(p);
                            break;
                        }
                    }
                }
                let _ = adapter.stop_scan().await;
                match hit {
                    Some(p) => p,
                    None => return Err(RC_ERR),
                }
            }
        };
        peripheral.connect().await.map_err(|err| {
            log::warn!("connect failed: {err:?}");
            RC_ERR
        })?;
        peripheral.discover_services().await.map_err(|err| {
            log::warn!("discover_services failed: {err:?}");
            RC_ERR
        })?;
        let id = {
            let mut next = inner.next_conn_id.lock();
            let id = *next;
            *next += 1;
            id
        };
        // Build the characteristic→service map up front so the notification
        // pump doesn't need an extra lookup per event.
        let mut char_to_service = HashMap::new();
        for service in peripheral.services() {
            for ch in &service.characteristics {
                char_to_service.insert(ch.uuid, service.uuid);
            }
        }
        let connection = Arc::new(Connection {
            id,
            peripheral: peripheral.clone(),
            notifications: Mutex::new(VecDeque::with_capacity(128)),
            notify_task: Mutex::new(None),
            char_to_service: Mutex::new(char_to_service),
        });
        // Start a single notification-pump task per connection.
        let conn_clone = connection.clone();
        let join = RUNTIME.spawn(async move {
            run_notification_pump(conn_clone).await;
        });
        *connection.notify_task.lock() = Some(join);
        inner.connections.lock().insert(id, connection.clone());
        Ok(connection)
    });

    match result {
        Ok(conn) => conn.id,
        Err(code) => code,
    }
}

/// Subscribe to notifications on the given characteristic.
#[no_mangle]
pub extern "C" fn btleplug_subscribe(
    handle: *mut c_void,
    conn_id: c_int,
    service_uuid: *const u8,
    service_uuid_len: usize,
    char_uuid: *const u8,
    char_uuid_len: usize,
) -> c_int {
    let h = match unsafe { handle.cast::<Handle>().as_ref() } {
        Some(r) => r,
        None => return RC_ERR,
    };
    let svc = match parse_uuid(service_uuid, service_uuid_len) {
        Ok(u) => u,
        Err(code) => return code,
    };
    let chr = match parse_uuid(char_uuid, char_uuid_len) {
        Ok(u) => u,
        Err(code) => return code,
    };
    let conn = match h.inner.connections.lock().get(&conn_id).cloned() {
        Some(c) => c,
        None => return RC_NOT_CONNECTED,
    };
    let target = match find_characteristic(&conn.peripheral, svc, chr) {
        Ok(c) => c,
        Err(code) => return code,
    };
    let result = RUNTIME.block_on(conn.peripheral.subscribe(&target));
    match result {
        Ok(()) => RC_OK,
        Err(err) => {
            log::warn!("subscribe failed: {err:?}");
            RC_ERR
        }
    }
}

/// Write to a characteristic.
#[no_mangle]
pub extern "C" fn btleplug_write_char(
    handle: *mut c_void,
    conn_id: c_int,
    service_uuid: *const u8,
    service_uuid_len: usize,
    char_uuid: *const u8,
    char_uuid_len: usize,
    payload: *const u8,
    payload_len: usize,
    with_response: c_int,
) -> c_int {
    let h = match unsafe { handle.cast::<Handle>().as_ref() } {
        Some(r) => r,
        None => return RC_ERR,
    };
    let svc = match parse_uuid(service_uuid, service_uuid_len) {
        Ok(u) => u,
        Err(code) => return code,
    };
    let chr = match parse_uuid(char_uuid, char_uuid_len) {
        Ok(u) => u,
        Err(code) => return code,
    };
    let conn = match h.inner.connections.lock().get(&conn_id).cloned() {
        Some(c) => c,
        None => return RC_NOT_CONNECTED,
    };
    let target = match find_characteristic(&conn.peripheral, svc, chr) {
        Ok(c) => c,
        Err(code) => return code,
    };
    let bytes = if payload.is_null() || payload_len == 0 {
        Vec::new()
    } else {
        unsafe { std::slice::from_raw_parts(payload, payload_len).to_vec() }
    };
    let write_type = if with_response != 0 {
        WriteType::WithResponse
    } else {
        WriteType::WithoutResponse
    };
    let result = RUNTIME.block_on(conn.peripheral.write(&target, &bytes, write_type));
    match result {
        Ok(()) => RC_OK,
        Err(err) => {
            log::warn!("write failed: {err:?}");
            RC_ERR
        }
    }
}

/// Pop the next notification for a connection into `buf` (JSON UTF-8).
/// Same return-code conventions as `btleplug_next_event`.
#[no_mangle]
pub extern "C" fn btleplug_next_notification(
    handle: *mut c_void,
    conn_id: c_int,
    buf: *mut u8,
    buf_len: usize,
) -> c_int {
    let h = match unsafe { handle.cast::<Handle>().as_ref() } {
        Some(r) => r,
        None => return RC_ERR,
    };
    let conn = match h.inner.connections.lock().get(&conn_id).cloned() {
        Some(c) => c,
        None => return RC_NOT_CONNECTED,
    };
    let event = {
        let mut queue = conn.notifications.lock();
        queue.pop_front()
    };
    let event = match event {
        Some(e) => e,
        None => return RC_EMPTY,
    };
    let json = NotificationJson {
        service_uuid: &event.service_uuid,
        characteristic_uuid: &event.characteristic_uuid,
        bytes: hex::encode(&event.bytes),
    };
    let serialized = match serde_json::to_string(&json) {
        Ok(s) => s,
        Err(_) => return RC_ERR,
    };
    let bytes = serialized.as_bytes();
    if bytes.len() > buf_len {
        return RC_OVERFLOW;
    }
    unsafe { ptr::copy_nonoverlapping(bytes.as_ptr(), buf, bytes.len()) };
    bytes.len() as c_int
}

/// Disconnect and forget a connection. Safe to call multiple times.
#[no_mangle]
pub extern "C" fn btleplug_disconnect(handle: *mut c_void, conn_id: c_int) -> c_int {
    let h = match unsafe { handle.cast::<Handle>().as_ref() } {
        Some(r) => r,
        None => return RC_ERR,
    };
    let conn = match h.inner.connections.lock().remove(&conn_id) {
        Some(c) => c,
        None => return RC_OK, // idempotent
    };
    if let Some(task) = conn.notify_task.lock().take() {
        task.abort();
    }
    let _ = RUNTIME.block_on(conn.peripheral.disconnect());
    RC_OK
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

// ---------------------------------------------------------------------------- GATT helpers

/// Lazily start the manager + adapter so connect() works without a prior scan.
fn ensure_adapter(inner: &Arc<Inner>) -> Option<Adapter> {
    if let Some(a) = inner.adapter.lock().clone() {
        return Some(a);
    }
    let acquired: Option<Adapter> = RUNTIME.block_on(async {
        let manager = Manager::new().await.ok()?;
        let adapters = manager.adapters().await.ok()?;
        adapters.into_iter().next()
    });
    if let Some(a) = acquired {
        *inner.adapter.lock() = Some(a.clone());
        Some(a)
    } else {
        None
    }
}

fn find_characteristic(
    peripheral: &Peripheral,
    service_uuid: Uuid,
    char_uuid: Uuid,
) -> Result<Characteristic, c_int> {
    let services = peripheral.services();
    let service = services
        .iter()
        .find(|s| s.uuid == service_uuid)
        .ok_or(RC_SERVICE_NOT_FOUND)?;
    let characteristic = service
        .characteristics
        .iter()
        .find(|c| c.uuid == char_uuid)
        .ok_or(RC_CHARACTERISTIC_NOT_FOUND)?
        .clone();
    Ok(characteristic)
}

async fn run_notification_pump(conn: Arc<Connection>) {
    let stream = match conn.peripheral.notifications().await {
        Ok(s) => s,
        Err(err) => {
            log::warn!("notifications stream failed: {err:?}");
            return;
        }
    };
    tokio::pin!(stream);
    const MAX_BACKLOG: usize = 256;
    while let Some(note) = stream.next().await {
        // ValueNotification carries only the characteristic UUID; look up the
        // owning service from the map we built at connect time.
        let service_uuid = conn
            .char_to_service
            .lock()
            .get(&note.uuid)
            .copied()
            .unwrap_or(Uuid::nil());
        let mut queue = conn.notifications.lock();
        if queue.len() >= MAX_BACKLOG {
            queue.pop_front();
        }
        queue.push_back(NotificationEvent {
            service_uuid: service_uuid.to_string().to_lowercase(),
            characteristic_uuid: note.uuid.to_string().to_lowercase(),
            bytes: note.value,
        });
    }
    log::debug!("notification pump ended for conn {}", conn.id);
}

fn read_str(ptr: *const u8, len: usize) -> Option<String> {
    if ptr.is_null() || len == 0 {
        return None;
    }
    let slice = unsafe { std::slice::from_raw_parts(ptr, len) };
    str::from_utf8(slice).ok().map(|s| s.to_string())
}

fn parse_uuid(ptr: *const u8, len: usize) -> Result<Uuid, c_int> {
    let s = read_str(ptr, len).ok_or(RC_INVALID_UTF8)?;
    Uuid::parse_str(&s).map_err(|_| RC_INVALID_UUID)
}

// Silence dead-code warning for RC_NO_ADAPTER; it is exposed via the ABI.
#[allow(dead_code)]
const _UNUSED_CODES: [c_int; 1] = [RC_NO_ADAPTER];
