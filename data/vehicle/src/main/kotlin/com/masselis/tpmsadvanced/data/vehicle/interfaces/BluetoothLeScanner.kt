package com.masselis.tpmsadvanced.data.vehicle.interfaces

import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

public interface BluetoothLeScanner {

    public sealed interface Failure {
        public class ScannerIsNull(adapterState: Int?) :
            Exception("Failure.ScannerIsNull(adapterState=$adapterState)")

        public class Scan(reason: Int) : Exception("Failure.Scan(reason=$reason)")
    }

    /** How much the radio listens, from a small part of the time to all the time */
    public enum class ScanMode {
        LOW_POWER,
        BALANCED,
        LOW_LATENCY,
    }

    /** One advertisement, whatever device sent it */
    public data class Advertisement(
        val address: String,
        val name: String?,
        val rssi: Int,
        /** When it was received, on the clock of `SystemClock.elapsedRealtimeNanos()` */
        val timestamp: Duration,
        /** Sent by a tyre sensor of one of the brands this app reads */
        val isTyreSensor: Boolean,
    )

    /** A device to look for, its advertisements match by [address] or by [name] */
    public data class DeviceMatch(val address: String, val name: String?)

    public fun highDutyScan(): Flow<Tyre.SensorInput>
    public fun normalScan(): Flow<Tyre.SensorInput>

    /** Whether a [highDutyScan] or a [normalScan] is running, whoever collects it */
    public val isScanningTyres: Flow<Boolean>

    /**
     * Every advertisement of the devices matching [devices], or of every device around when null.
     * Completes right away for an empty [devices]: no filter at all would mean every device instead.
     */
    public fun advertisements(mode: ScanMode, devices: List<DeviceMatch>?): Flow<Advertisement>

    public fun missingPermission(): List<String>

    public val isBluetoothRequired: Boolean
}
