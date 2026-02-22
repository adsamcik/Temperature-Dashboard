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
    private lateinit var fakeDao: FakeDeviceDao
    private lateinit var repo: DeviceRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeDeviceDao()
        repo = DeviceRepository(fakeDao, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading then Empty when no devices`() = runTest(testDispatcher) {
        val viewModel = MainViewModel(repo)

        assertEquals(MainScreenState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(MainScreenState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `initial state becomes Success when devices exist`() = runTest(testDispatcher) {
        fakeDao.devices.add(
            Device("AA:BB:CC:DD:EE:FF", "Test Device", null, null, 0L)
        )
        val viewModel = MainViewModel(repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is MainScreenState.Success)
        assertEquals(1, (state as MainScreenState.Success).devices.size)
    }

    @Test
    fun `showAddDeviceDialog toggles correctly`() = runTest(testDispatcher) {
        val viewModel = MainViewModel(repo)

        assertFalse(viewModel.showAddDeviceDialog.value)

        viewModel.onAddDeviceClicked()
        assertTrue(viewModel.showAddDeviceDialog.value)

        viewModel.dismissAddDeviceDialog()
        assertFalse(viewModel.showAddDeviceDialog.value)
    }

    @Test
    fun `getDeviceByMac returns null when not found`() = runTest(testDispatcher) {
        val viewModel = MainViewModel(repo)
        advanceUntilIdle()

        assertNull(viewModel.getDeviceByMac("XX:XX:XX:XX:XX:XX"))
    }

    @Test
    fun `getDeviceByMac returns device when found`() = runTest(testDispatcher) {
        fakeDao.devices.add(Device("AA:BB:CC:DD:EE:FF", "Test Device", null, null, 0L))
        val viewModel = MainViewModel(repo)
        advanceUntilIdle()

        val device = viewModel.getDeviceByMac("AA:BB:CC:DD:EE:FF")
        assertNotNull(device)
        assertEquals("AA:BB:CC:DD:EE:FF", device!!.device.macAddress)
    }

    @Test
    fun `loadDevices error produces Error state`() = runTest(testDispatcher) {
        fakeDao.errorToThrow = RuntimeException("DB locked")
        val viewModel = MainViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is MainScreenState.Error)
        assertEquals("DB locked", (state as MainScreenState.Error).message)
    }
}

/**
 * In-memory fake implementation of DeviceDao for testing.
 */
class FakeDeviceDao : DeviceDao {
    val devices = mutableListOf<Device>()
    var errorToThrow: Exception? = null

    override suspend fun insertDevice(device: Device) {
        devices.removeAll { it.macAddress == device.macAddress }
        devices.add(device)
    }

    override suspend fun getDeviceByMac(macAddress: String): Device? {
        return devices.find { it.macAddress == macAddress }
    }

    override suspend fun getAllDevices(): List<Device> {
        errorToThrow?.let { throw it }
        return devices.toList()
    }

    override suspend fun deleteDevice(device: Device) {
        devices.removeAll { it.macAddress == device.macAddress }
    }

    override suspend fun deleteDeviceByMac(macAddress: String) {
        devices.removeAll { it.macAddress == macAddress }
    }
}
