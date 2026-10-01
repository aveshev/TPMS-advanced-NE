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

    /** How much the radio listens, from not at all by itself to all the time */
    public enum class ScanMode {
        /** Never scans by itself, only gets what the scans of other apps receive */
        OPPORTUNISTIC,
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

    /**
     * Every advertisement of the devices matching [devices], or of every device around when null.
     * Each collection scans by itself, nothing is shared or deduplicated. Completes right away for
     * an empty [devices]: no filter at all would mean every device instead.
     */
    public fun advertisements(mode: ScanMode, devices: List<DeviceMatch>?): Flow<Advertisement>

    public fun missingPermission(): List<String>

    public val isBluetoothRequired: Boolean
}
