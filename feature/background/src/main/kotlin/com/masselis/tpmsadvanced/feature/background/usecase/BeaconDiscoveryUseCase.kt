package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Advertisement
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.LOW_LATENCY
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Lists the devices advertising around, to pick beacons from */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BeaconDiscoveryUseCase(
    private val scanner: BluetoothLeScanner,
    private val bluetoothOn: Flow<Boolean>,
    /** The signal (dBm) from which a device is loud enough to count as nearby */
    private val minRssi: StateFlow<Int>,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    data class Device(
        val address: String,
        val name: String,
        /** The median signal of its last packets, in dBm: a single lucky packet doesn't count much */
        val rssi: Int,
        /** The typical time between two of its packets, null until a few were heard */
        val period: Duration?,
        /** Loud enough to count as nearby, see [minRssi] */
        val isStrong: Boolean,
        /** Not heard for a while, gone or out of range */
        val isQuiet: Boolean,
    ) {
        /** Could be a beacon right now */
        val isCandidate: Boolean
            get() = isStrong && isQuiet.not()
    }

    /** What the page adding a beacon shows */
    sealed interface Page {
        /** The first moments, devices are gathered before being listed */
        data class Searching(val remaining: Duration, val found: Int) : Page

        /**
         * [devices] in a fixed order, the loudest first when they were listed, their signal kept up
         * to date. [notShown] counts the devices that became candidates since, listed by the next
         * reload: it only grows until then. [isSorted] tells a reload would change nothing: same
         * devices, same order.
         */
        data class Listed(val devices: List<Device>, val notShown: Int, val isSorted: Boolean = true) : Page
    }

    /**
     * Searches for [SEARCH], then lists the devices that were candidates at some point, the loudest
     * first. Once listed, a device stays for as long as this is collected, greyed out while it isn't
     * a candidate. The list keeps its order, so that rows don't move under the finger as signals
     * change, until [reloads] emits: it is then sorted again and the new devices added. Scans only
     * while [scanning], what was found so far being kept meanwhile.
     */
    fun page(reloads: Flow<Unit>, scanning: Flow<Boolean>): Flow<Page> = channelFlow {
        val latest = MutableStateFlow(emptyList<Device>())
        // Every device ever a candidate, in the order they became one
        val qualified = MutableStateFlow(emptySet<String>())
        launch {
            devices(scanning).collect { devices ->
                latest.value = devices
                qualified.value += devices.filter { it.isCandidate }.map { it.address }
            }
        }
        val start = timeSource.markNow()
        while (start.elapsedNow() < SEARCH) {
            send(Page.Searching(SEARCH - start.elapsedNow(), qualified.value.size))
            delay(minOf(COUNTDOWN_STEP, SEARCH - start.elapsedNow()))
        }
        // Each reload a new one, even when the order comes out the same
        val order = MutableStateFlow(Sorting(latest.value.sorted(qualified.value)))
        launch { reloads.collect { order.value = Sorting(latest.value.sorted(qualified.value)) } }
        combine(order, latest, qualified, ::Triple)
            .runningFold(null as Pair<Sorting, Page.Listed>?) { previous, (sorting, latest, qualified) ->
                val byAddress = latest.associateBy { it.address }
                sorting to Page.Listed(
                    devices = sorting.order.mapNotNull { byAddress[it] },
                    notShown = qualified.count { it !in sorting.order },
                    // Unsorted until the next reload once it was, rather than flickering as signals
                    // hover around each other's
                    isSorted = latest.sorted(qualified) == sorting.order &&
                        previous?.takeIf { (it, _) -> it === sorting }?.second?.isSorted != false,
                )
            }
            .mapNotNull { it?.second }
            .distinctUntilChanged()
            .collect { send(it) }
    }

    /**
     * The [qualified] devices, the candidates first then the greyed out ones, each the loudest
     * first. Stable: equal ones stay in the order they were found.
     */
    private fun List<Device>.sorted(qualified: Set<String>) = this
        .filter { it.address in qualified }
        .sortedWith(compareBy<Device> { it.isCandidate.not() }.thenByDescending { it.rssi })
        .map { it.address }

    /**
     * The named devices heard so far, in the order they were found, refreshed every [REFRESH]
     * rather than on every packet. Scans while [scanning] and Bluetooth is on, the devices heard
     * being kept otherwise. Left out:
     * - An unnamed device, usually one whose address changes all the time, unusable as a beacon.
     * - A tyre sensor: the vehicle's own ones are already bound to it, the others aren't wanted.
     */
    fun devices(scanning: Flow<Boolean>): Flow<List<Device>> = merge(
        combine(scanning, bluetoothOn) { scanning, on -> scanning && on }
            .distinctUntilChanged()
            .flatMapLatest { scan ->
                if (scan) scanner.advertisements(LOW_LATENCY, devices = null) else emptyFlow()
            }
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
                ?: heard.copy(isRefresh = true)
        }
        .filter { it.isRefresh }
        .map { heard ->
            heard.devices.mapNotNull { (address, device) ->
                device.takeIf { it.isTyreSensor.not() }?.asDevice(address)
            }
        }
        .distinctUntilChanged()

    /** An order of the listed devices, by address. Not a data class: told apart by identity. */
    private class Sorting(val order: List<String>)

    private data class Heard(val devices: Map<String, Accumulated>, val isRefresh: Boolean)

    private class Accumulated(
        val lastHeard: TimeMark,
        val name: String?,
        val rssis: List<Int>,
        val isStrong: Boolean,
        val timestamps: List<Duration>,
        val isTyreSensor: Boolean,
    ) {
        fun asDevice(address: String) = name?.let { name ->
            Device(
                address = address,
                name = name,
                rssi = rssis.median(),
                period = timestamps
                    .zipWithNext()
                    // The same packet received twice, or on another advertising channel
                    .filter { (a, b) -> b > a }
                    .let { pairs ->
                        // Some devices send bursts of packets a few ms apart then go quiet, a tyre
                        // sensor for minutes: what matters is the time between two bursts' starts
                        pairs
                            .filter { (a, b) -> b - a >= BURST }
                            .takeIf { it.isNotEmpty() }
                            ?.let { ends -> listOf(timestamps.first()) + ends.map { (_, start) -> start } }
                            ?.zipWithNext { a, b -> b - a }
                            // Never quiet for that long: always sending this fast, not bursting
                            ?: pairs.map { (a, b) -> b - a }
                    }
                    .takeIf { it.size >= MIN_GAPS }
                    // Missed packets make some gaps a multiple of the period: the median ignores them
                    ?.sorted()
                    ?.let { it[it.size / 2] },
                isStrong = isStrong,
                isQuiet = lastHeard.elapsedNow() >= QUIET_AFTER,
            )
        }
    }

    private fun Accumulated?.plus(advertisement: Advertisement): Accumulated {
        val rssis = (this?.rssis.orEmpty() + advertisement.rssi).takeLast(MAX_PACKETS)
        return Accumulated(
            lastHeard = timeSource.markNow(),
            // Some devices only send their name in some packets (the scan response)
            name = advertisement.name ?: this?.name,
            rssis = rssis,
            // A device hovering around the threshold would keep turning on and off otherwise: a strong
            // one only becomes weak a few dB under it
            isStrong = rssis
                .median()
                .let { it >= minRssi.value || (this?.isStrong == true && it >= minRssi.value - HYSTERESIS) },
            timestamps = (this?.timestamps.orEmpty() + advertisement.timestamp).takeLast(MAX_TIMESTAMPS),
            isTyreSensor = advertisement.isTyreSensor || this?.isTyreSensor == true,
        )
    }

    companion object {
        /** How long the page searches before listing anything */
        val SEARCH = 3.seconds

        /** How far under the threshold a strong device's signal falls before it becomes weak, in dB */
        const val HYSTERESIS = 5

        private val COUNTDOWN_STEP = 1.seconds
        private val REFRESH = 1.seconds
        private val QUIET_AFTER = 30.seconds
        private const val MAX_PACKETS = 11
        private const val MIN_GAPS = 3

        /** Packets closer than that belong to the same burst */
        private val BURST = 50.milliseconds

        /** Enough for a few bursts of a few packets each */
        private const val MAX_TIMESTAMPS = 50

        private fun List<Int>.median() = sorted().let { it[it.size / 2] }
    }
}
