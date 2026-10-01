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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.runningFold
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Tells which of the user's beacons are around, from their advertisements */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BeaconPresenceUseCase(
    private val scanner: BluetoothLeScanner,
    private val bluetoothOn: Flow<Boolean>,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val logger = Logger.withTag("BeaconPresenceUseCase")

    /** How the beacons are scanned for: how much the radio listens, and how batched the results are */
    enum class Mode(val scanMode: ScanMode, val reportDelay: Duration = Duration.ZERO) {
        OPPORTUNISTIC(ScanMode.OPPORTUNISTIC),
        LOW_POWER(ScanMode.LOW_POWER),
        LOW_POWER_BATCHED(ScanMode.LOW_POWER, reportDelay = 30.seconds),
        BALANCED(ScanMode.BALANCED),
        LOW_LATENCY(ScanMode.LOW_LATENCY),
    }

    /**
     * The addresses of the [beacons] heard nearby, with the signal (dBm) of their last packet. A
     * beacon is nearby while one of its packets louder than [MIN_RSSI] was heard within [WINDOW]:
     * a beacon far away (the neighbour's) doesn't count, nor does one gone quiet. Packets of a
     * batched [mode] come up to its report delay late, all at once: the window is longer by as much,
     * so that the beacon doesn't seem gone between two batches. Scans while Bluetooth is on, and
     * again after a failure.
     */
    fun nearby(beacons: List<Beacon>, mode: Mode): Flow<Map<String, Int>> = bluetoothOn
        .distinctUntilChanged()
        .flatMapLatest { on ->
            if (on.not()) return@flatMapLatest flowOf(emptyMap())
            merge(
                scanner
                    .advertisements(
                        mode.scanMode,
                        beacons.map { DeviceMatch(it.address, it.advertisedName) },
                        mode.reportDelay,
                    )
                    // Scanning too often, permission revoked meanwhile, etc: tried again later
                    .retryWhen { cause, _ ->
                        logger.w(cause) { "Beacon scan failed, retrying in $RETRY_DELAY" }
                        delay(RETRY_DELAY)
                        true
                    }
                    .mapNotNull { advertisement ->
                        beacons
                            .firstOrNull { it.isSending(advertisement) }
                            ?.takeIf { advertisement.rssi >= MIN_RSSI }
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
                        .filterValues { it.at.elapsedNow() < WINDOW + mode.reportDelay }
                }
                .map { heard -> heard.mapValues { (_, it) -> it.rssi } }
        }
        .distinctUntilChanged()

    private class Heard(val address: String, val rssi: Int, val at: TimeMark)

    private fun Beacon.isSending(advertisement: Advertisement) =
        advertisement.address.equals(address, ignoreCase = true) ||
            (advertisedName != null && advertisement.name == advertisedName)

    companion object {
        /** The mode of the background scan for beacons when the debug option was never set */
        val DEFAULT_SCAN_MODE = Mode.OPPORTUNISTIC

        /** The mode a stored name stands for, the default one for an unknown or missing name */
        fun String?.asBeaconScanMode(): Mode =
            Mode.entries.firstOrNull { it.name == this } ?: DEFAULT_SCAN_MODE

        /** Weaker than that, the beacon is likely someone else's or across the street */
        const val MIN_RSSI = -85
        private val WINDOW = 30.seconds
        private val TICK = 5.seconds
        private val RETRY_DELAY = 30.seconds
    }
}
