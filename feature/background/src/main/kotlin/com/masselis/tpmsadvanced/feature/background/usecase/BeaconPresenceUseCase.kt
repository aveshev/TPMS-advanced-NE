package com.masselis.tpmsadvanced.feature.background.usecase

import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Beacon
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Advertisement
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.DeviceMatch
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.runningFold
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Tells which of the user's beacons are around, from their advertisements */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BeaconPresenceUseCase(
    private val scanner: BluetoothLeScanner,
    private val bluetoothOn: Flow<Boolean>,
    /** The signal (dBm) from which a beacon counts as nearby */
    private val minRssi: StateFlow<Int>,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val logger = Logger.withTag("BeaconPresenceUseCase")

    /**
     * The addresses of the [beacons] heard nearby, with the signal (dBm) of their last packet. A
     * beacon is nearby while one of its packets at least as loud as [minRssi] was heard within
     * [WINDOW]: a beacon far away (the neighbour's) doesn't count, nor does one gone quiet. Scans
     * while Bluetooth is on, and again after a failure.
     */
    fun nearby(beacons: List<Beacon>, mode: ScanMode): Flow<Map<String, Int>> = bluetoothOn
        .distinctUntilChanged()
        .flatMapLatest { on ->
            if (on.not()) return@flatMapLatest flowOf(emptyMap())
            merge(
                scanner
                    .advertisements(mode, beacons.map { DeviceMatch(it.address, it.advertisedName) })
                    // Scanning too often, permission revoked meanwhile, etc: tried again later
                    .retryWhen { cause, _ ->
                        logger.w(cause) { "Beacon scan failed, retrying in $RETRY_DELAY" }
                        delay(RETRY_DELAY)
                        true
                    }
                    .mapNotNull { advertisement ->
                        beacons
                            .firstOrNull { it.isSending(advertisement) }
                            ?.takeIf { advertisement.rssi >= minRssi.value }
                            ?.let { Heard(it.address, advertisement.rssi, timeSource.markNow()) }
                    },
                // Packets stop coming when a beacon goes away: the time passing is what tells it
                flow<Heard?> {
                    while (true) {
                        delay(TICK)
                        emit(null)
                    }
                },
            )
                .runningFold(emptyMap<String, Heard>()) { heard, event ->
                    (event?.let { heard + (it.address to it) } ?: heard)
                        .filterValues { it.at.elapsedNow() < WINDOW }
                        .also { next ->
                            if (next.keys != heard.keys) logger.i {
                                "Nearby beacons with $mode, from ${minRssi.value} dBm: " +
                                    next.values.joinToString { "${it.address} at ${it.rssi} dBm" }.ifEmpty { "none" }
                            }
                        }
                }
                .map { heard -> heard.mapValues { (_, it) -> it.rssi } }
        }
        .distinctUntilChanged()

    private class Heard(val address: String, val rssi: Int, val at: TimeMark)

    private fun Beacon.isSending(advertisement: Advertisement) =
        advertisement.address.equals(address, ignoreCase = true) ||
            (advertisedName != null && advertisement.name == advertisedName)

    companion object {
        /** The modes the background scan for beacons can use, a debug option */
        val BACKGROUND_SCAN_MODES = listOf(ScanMode.LOW_POWER, ScanMode.BALANCED)

        /** The mode of the background scan for beacons when the debug option was never set */
        val DEFAULT_SCAN_MODE = ScanMode.LOW_POWER

        /** The mode a stored name stands for, the default one for an unknown or missing name */
        fun String?.asBeaconScanMode(): ScanMode =
            BACKGROUND_SCAN_MODES.firstOrNull { it.name == this } ?: DEFAULT_SCAN_MODE

        private val WINDOW = 30.seconds
        private val TICK = 5.seconds
        private val RETRY_DELAY = 30.seconds
    }
}
