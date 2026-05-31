package com.adsamcik.temperaturedashboard.ble.android

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.adsamcik.temperaturedashboard.ble.api.BluetoothAdapterMonitor
import com.adsamcik.temperaturedashboard.ble.api.BluetoothAdapterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observes the Bluetooth radio's enabled state via the platform broadcast.
 */
class AndroidBluetoothAdapterMonitor(
    private val context: Context,
) : BluetoothAdapterMonitor {

    private val _state = MutableStateFlow(currentState())
    override val state: StateFlow<BluetoothAdapterState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val raw = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            _state.value = raw.toDomain()
        }
    }

    fun register() {
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        _state.value = currentState()
    }

    fun unregister() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    private fun currentState(): BluetoothAdapterState {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return BluetoothAdapterState.Unavailable
        val adapter: BluetoothAdapter = manager.adapter ?: return BluetoothAdapterState.Unavailable
        return when (adapter.state) {
            BluetoothAdapter.STATE_OFF -> BluetoothAdapterState.Off
            BluetoothAdapter.STATE_ON -> BluetoothAdapterState.On
            BluetoothAdapter.STATE_TURNING_ON -> BluetoothAdapterState.TurningOn
            BluetoothAdapter.STATE_TURNING_OFF -> BluetoothAdapterState.TurningOff
            else -> BluetoothAdapterState.Unknown
        }
    }
}

private fun Int.toDomain(): BluetoothAdapterState = when (this) {
    BluetoothAdapter.STATE_OFF -> BluetoothAdapterState.Off
    BluetoothAdapter.STATE_ON -> BluetoothAdapterState.On
    BluetoothAdapter.STATE_TURNING_ON -> BluetoothAdapterState.TurningOn
    BluetoothAdapter.STATE_TURNING_OFF -> BluetoothAdapterState.TurningOff
    else -> BluetoothAdapterState.Unknown
}
