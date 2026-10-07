package com.masselis.tpmsadvanced.data.vehicle.interfaces.demo

import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

@Suppress("MagicNumber")
public class DemoLeScanner : BluetoothLeScanner {

    private val frontLeft = listOf(
        Tyre.SensorLocated(
            now(),
            -20,
            1,
            0.4f.bar,
            15f.celsius,
            SYSGRATION,
            false,
            SensorLocation.FRONT_LEFT,
            batteryPercent = 90,
        ),
        Tyre.Unlocated(
            now(),
            -20,
            2,
            0.4f.bar,
            15f.celsius,
            PECHAM,
            false,
            3.1f.volts,
        )
    )

    private val frontRight = listOf(
        Tyre.SensorLocated(
            now(),
            -20,
            3,
            1.6f.bar,
            20f.celsius,
            SYSGRATION,
            false,
            SensorLocation.FRONT_RIGHT,
            batteryPercent = 90,
        ),
        Tyre.Unlocated(
            now(),
            -20,
            4,
            1.6f.bar,
            20f.celsius,
            PECHAM,
            false,
            3f.volts,
        )
    )

    private val rearLeft = listOf(
        Tyre.SensorLocated(
            now(),
            -20,
            5,
            2.0f.bar,
            35f.celsius,
            SYSGRATION,
            false,
            SensorLocation.REAR_LEFT,
            batteryPercent = 90,
        ),
        Tyre.Unlocated(
            now(),
            -20,
            6,
            2.0f.bar,
            35f.celsius,
            PECHAM,
            false,
            2.9f.volts,
        ),
    )

    private val rearRight = listOf(
        Tyre.SensorLocated(
            now(),
            -20,
            7,
            2.8f.bar,
            95f.celsius,
            SYSGRATION,
            false,
            SensorLocation.REAR_RIGHT,
            batteryPercent = 90,
        ),
        Tyre.Unlocated(
            now(),
            -20,
            8,
            2.8f.bar,
            95f.celsius,
            PECHAM,
            false,
            3f.volts,
        )
    )

    private val source = flow {
        (frontLeft + frontRight + rearLeft + rearRight).forEach {
            emit(it)
        }
        awaitCancellation()
    }

    override fun highDutyScan(): Flow<Tyre.SensorInput> = source

    override fun normalScan(): Flow<Tyre.SensorInput> = source

    // The demo's readings aren't stored, nothing alerts about them
    override val isScanningTyres: Flow<Boolean> = flowOf(false)

    // No device around in the demo
    override fun advertisements(
        mode: BluetoothLeScanner.ScanMode,
        devices: List<BluetoothLeScanner.DeviceMatch>?,
    ): Flow<BluetoothLeScanner.Advertisement> = emptyFlow()

    override fun missingPermission(): List<String> = emptyList()

    override val isBluetoothRequired: Boolean = false
}
