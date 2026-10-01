package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Advertisement
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.LOW_LATENCY
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Lists the devices advertising around, to pick beacons from */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BeaconDiscoveryUseCase(
    private val scanner: BluetoothLeScanner,
    private val bluetoothOn: Flow<Boolean>,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    data class Device(
        val address: String,
        val name: String,
        /** The median signal of its last packets, in dBm: a single lucky packet doesn't count much */
        val rssi: Int,
        /** The typical time between two of its packets, null until a few were heard */
        val period: Duration?,
        val isTyreSensor: Boolean,
        /** Not heard for a while, gone or out of range */
        val isQuiet: Boolean,
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

        /** Could be a beacon right now: heard lately, loud enough to count as nearby */
        val isCandidate: Boolean
            get() = isQuiet.not() && rssi >= BeaconPresenceUseCase.MIN_RSSI
    }

    /** What the page adding a beacon shows */
    sealed interface Page {
        /** The first moments, devices are gathered before being listed */
        data class Searching(val remaining: Duration, val found: Int) : Page

        /**
         * [devices] in a fixed order, the loudest first when they were listed, their signal kept up
         * to date. [notShown] counts the devices that became candidates since, listed by the next
         * reload: it only grows until then.
         */
        data class Listed(val devices: List<Device>, val notShown: Int) : Page
    }

    /**
     * Searches for [SEARCH], then lists the devices that were candidates at some point, the loudest
     * first. Once listed, a device stays until the page is closed, greyed out while it isn't a
     * candidate. The list keeps its order, so that rows don't move under the finger as signals
     * change, until [reloads] emits: it is then sorted again and the new devices added.
     */
    fun page(reloads: Flow<Unit>): Flow<Page> = channelFlow {
        val latest = MutableStateFlow(emptyList<Device>())
        // Every device ever a candidate, in the order they became one
        val qualified = MutableStateFlow(emptySet<String>())
        launch {
            devices().collect { devices ->
                latest.value = devices
                qualified.value += devices.filter { it.isCandidate }.map { it.address }
            }
        }
        val start = timeSource.markNow()
        while (start.elapsedNow() < SEARCH) {
            send(Page.Searching(SEARCH - start.elapsedNow(), qualified.value.size))
            delay(minOf(COUNTDOWN_STEP, SEARCH - start.elapsedNow()))
        }
        val order = MutableStateFlow(latest.value.sorted(qualified.value))
        launch { reloads.collect { order.value = latest.value.sorted(qualified.value) } }
        combine(order, latest, qualified) { order, latest, qualified ->
            val byAddress = latest.associateBy { it.address }
            Page.Listed(
                devices = order.mapNotNull { byAddress[it] },
                notShown = qualified.count { it !in order },
            )
        }
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
     * The named devices heard since the scan started, in the order they were found, refreshed every
     * [REFRESH] rather than on every packet. An unnamed device is left out: it is usually one whose
     * address changes all the time, unusable as a beacon. Empty while Bluetooth is off.
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
                        ?: heard.copy(isRefresh = true)
                }
                .filter { it.isRefresh }
                .map { heard ->
                    heard.devices.mapNotNull { (address, device) -> device.asDevice(address) }
                }
        }
        .distinctUntilChanged()

    private data class Heard(val devices: Map<String, Accumulated>, val isRefresh: Boolean)

    private class Accumulated(
        val lastHeard: TimeMark,
        val name: String?,
        val rssis: List<Int>,
        val timestamps: List<Duration>,
        val isTyreSensor: Boolean,
    ) {
        fun asDevice(address: String) = name?.let { name ->
            Device(
                address = address,
                name = name,
                rssi = rssis.sorted().let { it[it.size / 2] },
                period = timestamps
                    .zipWithNext { a, b -> b - a }
                    // The same packet received twice, or on another advertising channel
                    .filter { it.isPositive() }
                    .takeIf { it.size >= MIN_GAPS }
                    // Missed packets make some gaps a multiple of the period: the median ignores them
                    ?.sorted()
                    ?.let { it[it.size / 2] },
                isTyreSensor = isTyreSensor,
                isQuiet = lastHeard.elapsedNow() >= QUIET_AFTER,
            )
        }
    }

    private fun Accumulated?.plus(advertisement: Advertisement) = Accumulated(
        lastHeard = timeSource.markNow(),
        // Some devices only send their name in some packets (the scan response)
        name = advertisement.name ?: this?.name,
        rssis = (this?.rssis.orEmpty() + advertisement.rssi).takeLast(MAX_PACKETS),
        timestamps = (this?.timestamps.orEmpty() + advertisement.timestamp).takeLast(MAX_PACKETS),
        isTyreSensor = advertisement.isTyreSensor || this?.isTyreSensor == true,
    )

    companion object {
        /** How long the page searches before listing anything */
        val SEARCH = 5.seconds
        private val COUNTDOWN_STEP = 1.seconds
        private val REFRESH = 1.seconds
        private val QUIET_AFTER = 30.seconds
        private const val MAX_PACKETS = 11
        private const val MIN_GAPS = 3
    }
}
