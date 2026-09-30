package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.bluetooth.le.ScanRecord
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
internal class ScanRecordTest {

    private fun record(hex: String) = mockk<ScanRecord> { every { bytes } returns hex.hexToByteArray() }

    // The live Wicarlink advertisement of WircarlinkTest
    private val wicarlink = "0201060303b0fb12ffac00d043520008da001d10ff1100af2206"

    @Test
    fun `the zeros Android pads the advertisement with are dropped`() {
        assertEquals(wicarlink, record(wicarlink + "00".repeat(36)).advertisementHex)
    }

    @Test
    fun `a zero ending real data is kept`() {
        // Flags, then manufacturer data (length 5) whose last byte, a CRC in real sensors, is 0
        assertEquals("02010605ff12345600", record("02010605ff12345600" + "000000").advertisementHex)
    }

    @Test
    fun `an advertisement without padding is kept whole`() {
        assertEquals(wicarlink, record(wicarlink).advertisementHex)
    }
}
