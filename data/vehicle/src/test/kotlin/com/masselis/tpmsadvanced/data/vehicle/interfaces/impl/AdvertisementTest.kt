package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalStdlibApi::class)
internal class AdvertisementTest {

    private fun statusBytes(hex: String) = Advertisement(hex.hexToByteArray()).statusBytes

    private fun bytes(vararg values: Int) = values.map { it.toUByte() }

    @Test
    fun `pecham gives its status byte`() {
        // Real sensors: "A" (alarm) set, then "S" (standing still) set
        assertEquals(bytes(0x80), statusBytes("0303A5270308425208FF801B1B02A584EA"))
        assertEquals(bytes(0x20), statusBytes("0303a5270308425208ff201b1b02c99ec8"))
    }

    @Test
    fun `bekubee ky gives the same byte as pecham`() {
        assertEquals(bytes(0x80), statusBytes("0303A52703084B5908FF801B1B02A584EA"))
    }

    @Test
    fun `wicarlink gives its four candidate bytes`() {
        // The live advertisement of WircarlinkTest: bytes 9, 10, 14 and 15
        assertEquals(bytes(0xAC, 0x00, 0x00, 0x08), statusBytes("0201060303B0FB12FFAC00D043520008DA001D10FF1100AF2206"))
    }

    @Test
    fun `bekubee tpms gives its company id`() {
        // The live advertisements of BekubeeTpmsTest, mounted then unplugged
        assertEquals(bytes(0x02), statusBytes("050854504D530CFF0200AF4500A7002D56A8B4030327A8"))
        assertEquals(bytes(0x06), statusBytes("050854504D530CFF0600B34A0064002FA0057D030327A8"))
    }

    @Test
    fun `sysgration gives its alarm byte`() {
        // Flags, service UUID 0xFBB0, then under company ID 0x0001: location, EA CA, ID, pressure,
        // temperature, battery and the alarm byte
        assertEquals(
            bytes(0x01),
            statusBytes("0201060303B0FB" + "13FF0100" + "80EACA123456" + "E0930400" + "D0070000" + "5A" + "01"),
        )
    }

    @Test
    fun `an unknown advertisement has no status byte`() {
        assertNull(statusBytes("0201060303AAAA"))
    }
}
