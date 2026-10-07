package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.os.ParcelUuid
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID.fromString

@OptIn(ExperimentalUnsignedTypes::class)
@Suppress("MagicNumber")
internal class RawSysgration private constructor(private val manufacturerData: ByteArray) : Raw {

    override val brand = SYSGRATION

    fun location() = manufacturerData[0]
        .toUByte()
        .let { raw -> SensorLocation.entries.first { it.byte == raw } }

    override val sensorId: Int = ByteBuffer
        .wrap(byteArrayOf(0x00) + manufacturerData.copyOfRange(3, 6))
        .order(ByteOrder.LITTLE_ENDIAN)
        .int

    fun pressure() = ByteBuffer
        .wrap(manufacturerData.copyOfRange(6, 10))
        .order(ByteOrder.LITTLE_ENDIAN)
        .int
        .div(1000f)
        .kpa

    fun temperature() = ByteBuffer
        .wrap(manufacturerData.copyOfRange(10, 14))
        .order(ByteOrder.LITTLE_ENDIAN)
        .int
        .div(100f)
        .celsius

    // A percentage according to theengs/decoder's TPMS decoder, anything above 100 isn't one
    fun batteryPercent() = (manufacturerData[14].toInt() and 0xFF).takeIf { it <= 100 }

    fun isAlarm() = manufacturerData[15] == PRESSURE_ALARM_BYTE

    override fun asTyre(timestamp: Double, rssi: Int, sensorId: Int, raw: String) = Tyre.SensorLocated(
        timestamp,
        rssi,
        sensorId,
        pressure(),
        temperature(),
        brand,
        isAlarm(),
        location(),
        raw = raw,
        batteryPercent = batteryPercent(),
    )

    companion object {
        internal val SERVICE_UUID = ParcelUuid(fromString("0000fbb0-0000-1000-8000-00805f9b34fb"))
        private const val PRESSURE_ALARM_BYTE = 0x01.toByte()
        private val expectedAddress = ubyteArrayOf(0xEAu, 0xCAu).toByteArray()

        operator fun invoke(packet: AdvertisingPacket): RawSysgration? = packet
            .manufacturer
            ?.second
            ?.takeIf { it.size >= 16 && it.copyOfRange(1, 3).contentEquals(expectedAddress) }
            // An unknown location byte isn't a Sysgration advertisement
            ?.takeIf { data -> SensorLocation.entries.any { it.byte == data[0].toUByte() } }
            ?.let(::RawSysgration)
    }
}
