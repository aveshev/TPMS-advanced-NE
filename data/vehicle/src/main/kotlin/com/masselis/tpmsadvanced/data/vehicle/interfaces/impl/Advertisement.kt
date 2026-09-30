package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

/**
 * A stored advertisement (`Tyre.raw`), read back without the Android scan objects the decoders
 * need, to find its status bytes. The brand is recognised the way each decoder recognises it, and
 * the positions are the ones the decoders read (the scan record's bytes, or its manufacturer data).
 *
 * Only what is known or suspected to be a status byte is returned, so what counts as one can
 * change here and apply to every stored reading.
 */
@Suppress("MagicNumber")
internal class Advertisement(private val bytes: ByteArray) {

    /** The AD structures, by type: the length-prefixed blocks an advertisement is made of */
    private val structures: Map<Int, ByteArray> = buildMap {
        var index = 0
        while (index < bytes.size && bytes[index] != 0.toByte()) {
            val length = bytes[index].toInt() and 0xFF
            if (index + length >= bytes.size) break
            putIfAbsent(bytes[index + 1].toInt() and 0xFF, bytes.copyOfRange(index + 2, index + 1 + length))
            index += 1 + length
        }
    }

    private val name = (structures[SHORT_NAME] ?: structures[COMPLETE_NAME])?.decodeToString()

    // The company ID leads the manufacturer data, in little endian
    private val manufacturer = structures[MANUFACTURER_DATA]
        ?.takeIf { it.size >= 2 }
        ?.let { (it[0].toInt() and 0xFF) or ((it[1].toInt() and 0xFF) shl 8) to it.copyOfRange(2, it.size) }

    private val serviceUuids16 = listOfNotNull(structures[INCOMPLETE_UUIDS_16], structures[COMPLETE_UUIDS_16])
        .flatMap { it.toList().chunked(2) { (low, high) -> (low.toInt() and 0xFF) or ((high.toInt() and 0xFF) shl 8) } }

    /** null when the advertisement isn't one of the known sensors, or has no status byte */
    val statusBytes: List<UByte>? = when {
        // Status, battery, temperature, pressure then CRC: "ARSB2H1y", most significant bit first
        // (andi38/TPMS). Bekubee KY is a copy of Pecham's format, with the same byte assumed.
        name == "BR" || name == "KY" -> listOf(10)
            .takeIf { bytes.size > 10 }
            ?.map { bytes[it].toUByte() }

        // The company ID changes with the state: 0x02 mounted, 0x06 unplugged
        name == "TPMS" -> manufacturer?.let { (companyId) -> listOf((companyId and 0xFF).toUByte()) }

        // The alarm byte, 0x01 being the only known value
        manufacturer?.second?.let { it.size > 15 && it[1] == 0xEA.toByte() && it[2] == 0xCA.toByte() } == true ->
            listOf(manufacturer.second[15].toUByte())

        // Not decoded by anything yet: 9-10 sit where a company ID usually is, 14-15 after the
        // pressure and the temperature, where a status byte usually is
        SERVICE_FBB0 in serviceUuids16 -> listOf(9, 10, 14, 15)
            .takeIf { bytes.size > 15 }
            ?.map { bytes[it].toUByte() }

        else -> null
    }

    companion object {
        private const val INCOMPLETE_UUIDS_16 = 0x02
        private const val COMPLETE_UUIDS_16 = 0x03
        private const val SHORT_NAME = 0x08
        private const val COMPLETE_NAME = 0x09
        private const val MANUFACTURER_DATA = 0xFF
        private const val SERVICE_FBB0 = 0xFBB0
    }
}
