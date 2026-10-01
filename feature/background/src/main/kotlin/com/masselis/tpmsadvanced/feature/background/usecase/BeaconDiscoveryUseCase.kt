package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Advertisement
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.LOW_LATENCY
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Lists every device advertising around, to pick beacons from */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BeaconDiscoveryUseCase(
    private val scanner: BluetoothLeScanner,
    private val bluetoothOn: Flow<Boolean>,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    data class Device(
        val address: String,
        val name: String?,
        /** The signal of its last packet, in dBm */
        val rssi: Int,
        /** The typical time between two of its packets, null until a few were heard */
        val period: Duration?,
        val isTyreSensor: Boolean,
    ) {
        /**
         * A resolvable private address, the kind phones, watches and earbuds use, changes every few
         * minutes: such a device can't be recognized later. Told by its two highest bits being 01,
         * which a public address can have too, hence "may".
         */
        @Suppress("MagicNumber")
        val mayChangeAddress: Boolean
            get() = address
                .take(2)
                .toIntOrNull(16)
                ?.let { it and 0xC0 == 0x40 }
                ?: false
    }

    /**
     * The devices heard within the last [WINDOW], the loudest first, refreshed every [REFRESH]
     * rather than on every packet: dozens of devices each sending several per second would redraw
     * the list all the time. Empty while Bluetooth is off.
     */
    fun devices(): Flow<List<Device>> = bluetoothOn
        .distinctUntilChanged()
        .flatMapLatest { on ->
            if (on.not()) return@flatMapLatest flowOf(emptyList())
            merge(
                scanner
                    .advertisements(LOW_LATENCY, devices = null)
                    .map<Advertisement, Advertisement?> { it },
                flow {
                    while (true) {
                        emit(null)
                        delay(REFRESH)
                    }
                },
            )
                .runningFold(Heard(emptyMap(), isRefresh = false)) { heard, advertisement ->
                    advertisement
                        ?.let {
                            Heard(heard.devices + (it.address to heard.devices[it.address].plus(it)), isRefresh = false)
                        }
                        ?: heard.copy(
                            devices = heard.devices.filterValues { it.lastHeard.elapsedNow() < WINDOW },
                            isRefresh = true,
                        )
                }
                .filter { it.isRefresh }
                .map { heard ->
                    heard.devices
                        .entries
                        .sortedWith(compareBy(LOUDEST_FIRST) { it.value })
                        .map { (address, device) -> device.asDevice(address) }
                }
        }
        .distinctUntilChanged()

    private data class Heard(val devices: Map<String, Accumulated>, val isRefresh: Boolean)

    private class Accumulated(
        val firstHeard: Duration,
        val lastHeard: TimeMark,
        val name: String?,
        val rssi: Int,
        val timestamps: List<Duration>,
        val isTyreSensor: Boolean,
    ) {
        fun asDevice(address: String) = Device(
            address = address,
            name = name,
            rssi = rssi,
            period = timestamps
                .zipWithNext { a, b -> b - a }
                // The same packet received twice, or on another advertising channel
                .filter { it.isPositive() }
                .takeIf { it.size >= MIN_GAPS }
                // Missed packets make some gaps a multiple of the period: the median ignores them
                ?.sorted()
                ?.let { it[it.size / 2] },
            isTyreSensor = isTyreSensor,
        )
    }

    private fun Accumulated?.plus(advertisement: Advertisement) = Accumulated(
        firstHeard = this?.firstHeard ?: advertisement.timestamp,
        lastHeard = timeSource.markNow(),
        // Some devices only send their name in some packets (the scan response)
        name = advertisement.name ?: this?.name,
        rssi = advertisement.rssi,
        timestamps = (this?.timestamps.orEmpty() + advertisement.timestamp).takeLast(MAX_TIMESTAMPS),
        isTyreSensor = advertisement.isTyreSensor || this?.isTyreSensor == true,
    )

    private companion object {
        val WINDOW = 30.seconds
        val REFRESH = 1.seconds
        const val MAX_TIMESTAMPS = 11
        const val MIN_GAPS = 3

        /**
         * By signal, so that the device held next to the phone comes first. Rounded to 10 dB, and
         * then in the order they were found, so that rows don't swap places under the finger with
         * every small change of signal.
         */
        @Suppress("MagicNumber")
        val LOUDEST_FIRST = compareByDescending<Accumulated> { Math.floorDiv(it.rssi, 10) }
            .thenBy { it.firstHeard }
    }
}
