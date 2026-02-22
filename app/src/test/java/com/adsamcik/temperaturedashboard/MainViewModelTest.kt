package com.adsamcik.temperaturedashboard

import com.adsamcik.temperaturedashboard.storage.Device
import com.adsamcik.temperaturedashboard.storage.DeviceDao
import com.adsamcik.temperaturedashboard.storage.DeviceRepository
import com.adsamcik.temperaturedashboard.ui.models.MainViewModel
import com.adsamcik.temperaturedashboard.ui.state.MainScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading then Empty when no devices`() = runTest(testDispatcher) {
        val fakeDao = FakeDeviceDao()
        val repo = DeviceRepository(fakeDao, testDispatcher)
        val viewModel = MainViewModel(repo)

        assertEquals(MainScreenState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(MainScreenState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `initial state becomes Success when devices exist`() = runTest(testDispatcher) {
        val fakeDao = FakeDeviceDao()
        fakeDao.devices.add(
            Device("AA:BB:CC:DD:EE:FF", "Test Device", null, null, 0L)
        )
        val repo = DeviceRepository(fakeDao, testDispatcher)
        val viewModel = MainViewModel(repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is MainScreenState.Success)
        assertEquals(1, (state as MainScreenState.Success).devices.size)
    }

    @Test
    fun `showAddDeviceDialog toggles correctly`() = runTest(testDispatcher) {
        val fakeDao = FakeDeviceDao()
        val repo = DeviceRepository(fakeDao, testDispatcher)
        val viewModel = MainViewModel(repo)

        assertFalse(viewModel.showAddDeviceDialog.value)

        viewModel.onAddDeviceClicked()
        assertTrue(viewModel.showAddDeviceDialog.value)

        viewModel.dismissAddDeviceDialog()
        assertFalse(viewModel.showAddDeviceDialog.value)
    }

    @Test
    fun `getDeviceByMac returns null when not found`() = runTest(testDispatcher) {
        val fakeDao = FakeDeviceDao()
        val repo = DeviceRepository(fakeDao, testDispatcher)
        val viewModel = MainViewModel(repo)
        advanceUntilIdle()

        assertNull(viewModel.getDeviceByMac("XX:XX:XX:XX:XX:XX"))
    }
}

/**
 * In-memory fake implementation of DeviceDao for testing.
 */
class FakeDeviceDao : DeviceDao {
    val devices = mutableListOf<Device>()

    override suspend fun insertDevice(device: Device) {
        devices.removeAll { it.macAddress == device.macAddress }
        devices.add(device)
    }

    override suspend fun getDeviceByMac(macAddress: String): Device? {
        return devices.find { it.macAddress == macAddress }
    }

    override suspend fun getAllDevices(): List<Device> {
        return devices.toList()
    }

    override suspend fun deleteDevice(device: Device) {
        devices.removeAll { it.macAddress == device.macAddress }
    }

    override suspend fun deleteDeviceByMac(macAddress: String) {
        devices.removeAll { it.macAddress == macAddress }
    }
}
