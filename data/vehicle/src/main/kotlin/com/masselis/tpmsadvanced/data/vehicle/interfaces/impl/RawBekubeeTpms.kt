package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.os.ParcelUuid
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BEKUBEE_TPMS
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import java.util.UUID.fromString

/** This a sensor from the HRTPMS app, see [pull request](https://github.com/VincentMasselis/TPMS-advanced/pull/446) */
@Suppress("MagicNumber")
internal class RawBekubeeTpms private constructor(private val manufacturerData: ByteArray) : Raw {

    override val brand = BEKUBEE_TPMS

    override val sensorId: Int = (manufacturerData[4].toInt() and 0xFF) or
        ((manufacturerData[5].toInt() and 0xFF) shl 8) or
        ((manufacturerData[6].toInt() and 0xFF) shl 16)

    fun pressure() = (
        ((manufacturerData[2].toInt() and 0xFF) shl 8) or
            (manufacturerData[3].toInt() and 0xFF)
        )
        .minus(100)
        .toFloat()
        .kpa

    fun temperature() = (manufacturerData[1].toInt() and 0xFF)
        .minus(50)
        .toFloat()
        .celsius

    // Returns 2.97 for 2.97 volts
    fun voltage() = (manufacturerData[0].toInt() and 0xFF) * 0.01f + 1.22f

    override fun asTyre(timestamp: Double, rssi: Int, sensorId: Int, raw: String): Tyre.SensorInput = Tyre.Unlocated(
        timestamp,
        rssi,
        sensorId,
        pressure(),
        temperature(),
        brand,
        // The low battery is reported through batteryVoltage, these sensors send no alarm
        false,
        voltage().volts,
        raw,
    )

    companion object {
        internal val SERVICE_UUID = ParcelUuid(fromString("0000a827-0000-1000-8000-00805f9b34fb"))

        private const val MIN_MANUFACTURER_DATA_LENGTH = 9

        // This sensor advertises the same payload shape under different company IDs depending on
        // its state (observed 0x0002 when mounted with a valid pressure reading, 0x0006 when
        // unplugged/idle), the company ID is ignored.
        operator fun invoke(packet: AdvertisingPacket): RawBekubeeTpms? = packet
            .takeIf { it.name == "TPMS" && 0xA827 in it.serviceUuids16 }
            ?.manufacturer
            ?.second
            ?.takeIf { it.size >= MIN_MANUFACTURER_DATA_LENGTH }
            ?.let(::RawBekubeeTpms)
    }
}
