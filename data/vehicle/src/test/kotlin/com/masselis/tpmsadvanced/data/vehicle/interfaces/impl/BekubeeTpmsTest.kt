package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BEKUBEE_TPMS
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalStdlibApi::class)
internal class BekubeeTpmsTest {

    // 0x08: Shortened Local Name ("TPMS")
    // 0xFF: Manufacturer Specific Data. The values below are the payload *after* the company ID,
    // wrapped by advertisement() into the rest of the packet. The company ID itself varies with
    // sensor state (0x0002 mounted, 0x0006 unplugged) so it must not be filtered on.
    // 0x03: Complete List of 16-bit Service Class UUIDs (0xA827)

    @Suppress("MaxLineLength")
    private val samples = listOf(
        // Full raw advertisement captured live from a physical Bekubee "TPMS" sensor:
        // 050854504D530CFF0200AF4500A7002D56A8B4030327A8
        // (name "TPMS" + manufacturer data, company ID 0002, payload below + service UUID A827,
        // all in the same packet)
        // Unlocated(sensorId=5647616, pressure=Pressure(kpa=67.0), temperature=Temperature(celsius=19.0), battery=30, isAlarm=false)
        "AF4500A7002D56A8B4",

        // From PR #445 (https://github.com/VincentMasselis/TPMS-advanced/pull/445), same
        // physical sensor (printed/QR ID 002D56).
        // Unlocated(sensorId=5647616, pressure=Pressure(kpa=37.0), temperature=Temperature(celsius=18.0), battery=30, isAlarm=false)
        "AF440089002D56087B",
        // Temperature change without a pressure change.
        // Unlocated(sensorId=5647616, pressure=Pressure(kpa=37.0), temperature=Temperature(celsius=27.0), battery=30, isAlarm=false)
        "B14D0089002D563A76",

        // Same physical sensor, unplugged from its valve. Full raw advertisement:
        // 050854504D530CFF0600B34A0064002FA0057D030327A8
        // Company ID is 0x0006 here (not 0x0002 like every mounted sample above) — this is the
        // real-world case that broke ScanRecord.getManufacturerSpecificData(0x0002).
        // Unlocated(sensorId=10497792, pressure=Pressure(kpa=0.0), temperature=Temperature(celsius=24.0), battery=30, isAlarm=false)
        "B34A0064002FA0057D",
    )

    /** The packet of the live captures: name, manufacturer data under [companyId], service UUID */
    private fun advertisement(payload: String, companyId: String = "0200") =
        "050854504D530CFF$companyId${payload}030327A8".hexToByteArray()

    @Test
    fun realValue() {
        samples
            .mapNotNull { advertisement(it).decodedTyre() }
            .onEach(::println)
            .also { assertEquals(samples.size, it.size) }
            .forEach { tyre ->
                assertEquals(BEKUBEE_TPMS, tyre.brand)
                assertFalse(tyre.isAlarm)
                assertNotNull(tyre.batteryVoltage)
            }
    }

    @Test
    fun `the live captures decode whatever their company ID`() {
        assertEquals(5647616, assertNotNull(advertisement(samples[0]).decodedTyre()).sensorId)
        assertEquals(10497792, assertNotNull(advertisement(samples[3], companyId = "0600").decodedTyre()).sensorId)
    }
}
