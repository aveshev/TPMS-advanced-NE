package com.masselis.tpmsadvanced.feature.qrcode.usecase

import androidx.camera.view.CameraController
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.CameraAnalyser
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensor
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors.FourWheel
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors.TwoWheel
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class QrCodeSensorUseCase(
    private val cameraAnalyser: CameraAnalyser,
) {

    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("MagicNumber", "CyclomaticComplexMethod", "LongMethod", "MaxLineLength")
    fun analyse(
        controller: CameraController
    ): Flow<QrCodeSensors> = cameraAnalyser
        .findQrCode(controller)
        .mapNotNull {
            fourSensorRegex.find(it)?.groupValues?.subList(1, 5)
                ?: twoSensorRegex.find(it)?.groupValues?.subList(1, 3)
                ?: run {
                    if (wicarlinkSensorRegex.matches(it)) throw UnsupportedWircarlinkQrCode()
                    else null
                }
        }
        .map { stringHexs ->
            stringHexs
                .map { stringHex ->
                    Pair(
                        // Trying to recognize the location with the id of the sensor
                        when (stringHex.first()) {
                            '1' -> Location.Wheel(FRONT_LEFT)
                            '2' -> Location.Wheel(FRONT_RIGHT)
                            '3' -> Location.Wheel(REAR_LEFT)
                            '4' -> Location.Wheel(REAR_RIGHT)
                            else -> null
                        },
                        // Converts the hexadecimal id to an int
                        stringHex
                            .hexToByteArray()
                            .let {
                                ByteBuffer
                                    .wrap(byteArrayOf(0x00) + it)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .int
                            },
                    )
                }
                .mapIndexed { index, (location, id) ->
                    QrCodeSensor(
                        id,
                        // The sensor id didn't provided the location, let's determine it with the
                        // list index
                        location ?: when (index) {
                            0 -> Location.Wheel(FRONT_LEFT)
                            1 -> Location.Wheel(FRONT_RIGHT)
                            2 -> Location.Wheel(REAR_LEFT)
                            3 -> Location.Wheel(REAR_RIGHT)
                            else -> error("Filled list cannot have more than 4 entries")
                        }
                    )
                }
                .let {
                    when (it.size) {
                        2 -> TwoWheel(it[0], it[1])
                        4 -> FourWheel(it[0], it[1], it[2], it[3])
                        else -> error("Unreachable state, previous regex should only contains 2 or 4 sensors, current sensors: $it")
                    }
                }
        }
        .flowOn(Default)

    class UnsupportedWircarlinkQrCode : IllegalArgumentException()

    companion object {
        // Test available here: https://regex101.com/r/aLJ0o6/1
        private val fourSensorRegex =
            "([0-9a-fA-F]{6})&([0-9a-fA-F]{6})&([0-9a-fA-F]{6})&([0-9a-fA-F]{6})".toRegex()

        private val twoSensorRegex =
            "([0-9a-fA-F]{6})&([0-9a-fA-F]{6})".toRegex()

        // A Wicarlink/LYTPMS QR code only contains a single sensor's 6 hex character id, e.g. AF2206
        private val wicarlinkSensorRegex =
            "^([0-9a-fA-F]{6})$".toRegex()
    }
}
