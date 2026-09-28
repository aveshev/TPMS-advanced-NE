package com.masselis.tpmsadvanced.feature.background.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.BLUETOOTH
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.DOZE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.WIFI
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase.State.Connected
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase.State.Disconnected
import com.masselis.tpmsadvanced.feature.background.usecase.BluetoothDevicesUseCase.PairedDevice
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

internal class ScanSuspensionUseCaseTest {

    private lateinit var appPreferences: AppPreferences
    private lateinit var deviceIdleModeUseCase: DeviceIdleModeUseCase
    private lateinit var wifiConnectionUseCase: WifiConnectionUseCase
    private lateinit var bluetoothDevicesUseCase: BluetoothDevicesUseCase

    private lateinit var suspendConditions: MutableStateFlow<Boolean>
    private lateinit var suspendScanningInDoze: MutableStateFlow<Boolean>
    private lateinit var suspendScanningOnWifi: MutableStateFlow<Boolean>
    private lateinit var wifiExceptionEnabled: MutableStateFlow<Boolean>
    private lateinit var exceptedWifiSsids: MutableStateFlow<Set<String>>
    private lateinit var isDeviceIdle: MutableStateFlow<Boolean>
    private lateinit var wifiState: MutableStateFlow<WifiConnectionUseCase.State>
    private lateinit var suspendScanningOnBluetooth: MutableStateFlow<Boolean>
    private lateinit var suspendBluetoothDevices: MutableStateFlow<Set<String>>
    private lateinit var bluetoothConnected: MutableStateFlow<Set<String>>

    private fun test() = ScanSuspensionUseCase(
        appPreferences,
        deviceIdleModeUseCase,
        wifiConnectionUseCase,
        bluetoothDevicesUseCase,
    )

    @Before
    fun setup() {
        suspendConditions = MutableStateFlow(true)
        suspendScanningInDoze = MutableStateFlow(false)
        suspendScanningOnWifi = MutableStateFlow(false)
        wifiExceptionEnabled = MutableStateFlow(false)
        exceptedWifiSsids = MutableStateFlow(emptySet())
        isDeviceIdle = MutableStateFlow(false)
        wifiState = MutableStateFlow(Disconnected)
        suspendScanningOnBluetooth = MutableStateFlow(false)
        suspendBluetoothDevices = MutableStateFlow(emptySet())
        bluetoothConnected = MutableStateFlow(emptySet())

        appPreferences = mockk {
            every { suspendConditions } returns this@ScanSuspensionUseCaseTest.suspendConditions
            every { suspendScanningInDoze } returns this@ScanSuspensionUseCaseTest.suspendScanningInDoze
            every { suspendScanningOnWifi } returns this@ScanSuspensionUseCaseTest.suspendScanningOnWifi
            every { wifiExceptionEnabled } returns this@ScanSuspensionUseCaseTest.wifiExceptionEnabled
            every { exceptedWifiSsids } returns this@ScanSuspensionUseCaseTest.exceptedWifiSsids
            every { suspendScanningOnBluetooth } returns this@ScanSuspensionUseCaseTest.suspendScanningOnBluetooth
            every { suspendBluetoothDevices } returns this@ScanSuspensionUseCaseTest.suspendBluetoothDevices
        }
        deviceIdleModeUseCase = mockk { every { isDeviceIdle } returns this@ScanSuspensionUseCaseTest.isDeviceIdle }
        wifiConnectionUseCase = mockk { every { state } returns this@ScanSuspensionUseCaseTest.wifiState }
        bluetoothDevicesUseCase = mockk {
            every { connected } returns bluetoothConnected.map { addresses ->
                addresses.map { PairedDevice(it, it, isAudio = true) }.toSet()
            }
        }
    }

    @Test
    fun `no suspension when both toggles are off`() = runTest {
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun `does not suspend for doze when the toggle is off even if the device is idle`() = runTest {
        isDeviceIdle.value = true
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun `suspends when doze is enabled and the device is idle`() = runTest {
        suspendScanningInDoze.value = true
        isDeviceIdle.value = true
        test().suspensionReasons.test {
            assertEquals(setOf(DOZE), awaitItem())
        }
    }

    @Test
    fun `turning the suspend conditions off lifts every suspension`() = runTest {
        suspendScanningInDoze.value = true
        isDeviceIdle.value = true
        suspendScanningOnWifi.value = true
        wifiState.value = Connected("Home")
        test().suspensionReasons.test {
            assertEquals(setOf(DOZE, WIFI), awaitItem())
            suspendConditions.value = false
            assertEquals(emptySet(), awaitItem())
            suspendConditions.value = true
            assertEquals(setOf(DOZE, WIFI), awaitItem())
        }
    }

    @Test
    fun `does not suspend for wifi when disconnected`() = runTest {
        suspendScanningOnWifi.value = true
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun `suspends when connected to wifi and no exception is configured`() = runTest {
        suspendScanningOnWifi.value = true
        wifiState.value = Connected("HomeNetwork")
        test().suspensionReasons.test {
            assertEquals(setOf(WIFI), awaitItem())
        }
    }

    @Test
    fun `does not suspend when connected to the excepted wifi`() = runTest {
        suspendScanningOnWifi.value = true
        wifiExceptionEnabled.value = true
        exceptedWifiSsids.value = setOf("CarWifi")
        wifiState.value = Connected("CarWifi")
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun `suspends when connected to a non-excepted wifi even with exceptions enabled`() = runTest {
        suspendScanningOnWifi.value = true
        wifiExceptionEnabled.value = true
        exceptedWifiSsids.value = setOf("CarWifi")
        wifiState.value = Connected("HomeNetwork")
        test().suspensionReasons.test {
            assertEquals(setOf(WIFI), awaitItem())
        }
    }

    @Test
    fun `combines both reasons when both conditions are met`() = runTest {
        suspendScanningInDoze.value = true
        isDeviceIdle.value = true
        suspendScanningOnWifi.value = true
        wifiState.value = Connected("HomeNetwork")
        test().suspensionReasons.test {
            assertEquals(setOf(DOZE, WIFI), awaitItem())
        }
    }

    @Test
    fun `suspends while a selected Bluetooth device is connected`() = runTest {
        suspendScanningOnBluetooth.value = true
        suspendBluetoothDevices.value = setOf("SPEAKER")
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
            bluetoothConnected.value = setOf("SPEAKER")
            assertEquals(setOf(BLUETOOTH), awaitItem())
            bluetoothConnected.value = emptySet()
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun `does not suspend for a Bluetooth device that is not selected`() = runTest {
        suspendScanningOnBluetooth.value = true
        suspendBluetoothDevices.value = setOf("SPEAKER")
        bluetoothConnected.value = setOf("CAR")
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun `does not suspend for Bluetooth when the toggle is off`() = runTest {
        suspendBluetoothDevices.value = setOf("SPEAKER")
        bluetoothConnected.value = setOf("SPEAKER")
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
        }
    }
}
