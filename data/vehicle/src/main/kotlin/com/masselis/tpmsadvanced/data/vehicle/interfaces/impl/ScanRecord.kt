package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.bluetooth.le.ScanRecord

/**
 * The advertisement in hexadecimal, without the zeros Android pads it with. Its AD structures are
 * followed length byte by length byte, rather than the trailing zeros dropped: a zero can be the
 * last byte of real data, such as a CRC.
 */
@OptIn(ExperimentalStdlibApi::class)
@Suppress("MagicNumber")
internal val ScanRecord.advertisementHex: String
    get() {
        var end = 0
        // A length byte of 0 ends the significant part
        while (end < bytes.size && bytes[end] != 0.toByte()) end += 1 + (bytes[end].toInt() and 0xFF)
        return bytes.copyOf(end.coerceAtMost(bytes.size)).toHexString()
    }
