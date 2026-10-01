package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Beacon
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.LOW_POWER
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconDiscoveryUseCase.Device
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class BeaconsViewModelTest {

    private lateinit var activateOnBeacon: MutableStateFlow<Boolean>
    private lateinit var beacons: MutableStateFlow<List<Beacon>>
    private lateinit var beaconScanMode: MutableStateFlow<String?>

    private fun test() = BeaconsViewModel(
        mockk<AppPreferences>(relaxed = true) {
            every { activateOnBeacon } returns this@BeaconsViewModelTest.activateOnBeacon
            every { beacons } returns this@BeaconsViewModelTest.beacons
            every { beaconScanMode } returns this@BeaconsViewModelTest.beaconScanMode
        },
        mockk(relaxed = true),
        mockk(relaxed = true),
    )

    @Before
    fun setup() {
        activateOnBeacon = MutableStateFlow(true)
        beacons = MutableStateFlow(listOf(BIKE))
        beaconScanMode = MutableStateFlow(null)
    }

    @Test
    fun `a device is added as a beacon with its advertised name`() {
        test().add(Device("C4:CD:82:63:55:15", "RE6603100142", -60, null, isTyreSensor = false))
        assertEquals(listOf(BIKE, Beacon("C4:CD:82:63:55:15", "RE6603100142", label = null)), beacons.value)
    }

    @Test
    fun `a device already added is not added twice`() {
        test().add(Device(BIKE.address, BIKE.advertisedName, -60, null, isTyreSensor = false))
        assertEquals(listOf(BIKE), beacons.value)
    }

    @Test
    fun `renaming keeps the advertised name to match on`() {
        test().rename(BIKE.address, "  My bike ")
        assertEquals(listOf(BIKE.copy(label = "My bike")), beacons.value)
    }

    @Test
    fun `a blank name brings the advertised one back`() {
        test().rename(BIKE.address, " ")
        assertEquals(listOf(BIKE.copy(label = null)), beacons.value)
        assertEquals("CFMOTOR_ee64a312381a", beacons.value.single().displayName)
    }

    @Test
    fun `removing leaves the other beacons`() {
        val other = Beacon("C4:CD:82:63:55:15", advertisedName = null, label = null)
        beacons.value = listOf(BIKE, other)
        test().remove(setOf(BIKE.address))
        assertEquals(listOf(other), beacons.value)
    }

    @Test
    fun `the condition stays on with a beacon`() {
        test().disableIfNoBeacon()
        assertTrue(activateOnBeacon.value)
    }

    @Test
    fun `the condition turns off without any beacon`() {
        beacons.value = emptyList()
        test().disableIfNoBeacon()
        assertFalse(activateOnBeacon.value)
    }

    @Test
    fun `the scan mode is stored by name`() {
        test().setScanMode(LOW_POWER)
        assertEquals("LOW_POWER", beaconScanMode.value)
    }

    private companion object {
        val BIKE = Beacon("EE:64:A3:12:38:1A", "CFMOTOR_ee64a312381a", label = "Bike")
    }
}
