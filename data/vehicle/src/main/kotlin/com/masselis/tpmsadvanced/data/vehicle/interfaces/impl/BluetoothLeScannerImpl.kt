package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanSettings.MATCH_MODE_AGGRESSIVE
import android.bluetooth.le.ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.dematerializeCompletion
import com.masselis.tpmsadvanced.core.common.materializeCompletion
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Advertisement
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.DeviceMatch
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Failure
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

@SuppressLint("MissingPermission")
@Suppress("MaxLineLength")
internal class BluetoothLeScannerImpl(
    private val context: Context
) : BluetoothLeScanner {

    private val logger = Logger.withTag("BluetoothLeScannerImpl")

    private var lastStartScan = Duration.ZERO

    private val bluetoothAdapter get() = context.getSystemService<BluetoothManager>()?.adapter

    /** Every result of a scan with [filters] and [settings], debug builds adding the mock ones */
    @SuppressLint("InlinedApi")
    @RequiresPermission("android.permission.BLUETOOTH_SCAN")
    private fun rawScan(filters: List<ScanFilter>, settings: ScanSettings) = callbackFlow {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                launch { send(result) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                launch { results.forEach { send(it) } }
            }

            override fun onScanFailed(errorCode: Int) {
                close(Failure.Scan(errorCode))
            }
        }

        // Anti-spam mechanism to avoid an exception when requesting 6 scans within a 30s frame.
        // Every scan of the app counts, the tyre ones and the others alike.
        delay(5.seconds - (System.currentTimeMillis().milliseconds - lastStartScan))
        // Delay elapsed, set lastStartScan to the current timestamp
        lastStartScan = System.currentTimeMillis().milliseconds

        val leScanner = bluetoothAdapter?.bluetoothLeScanner
        if (leScanner == null) {
            close(Failure.ScannerIsNull(bluetoothAdapter?.state))
            awaitCancellation()
        }
        leScanner.startScan(filters, settings, callback)
        awaitClose {
            if (bluetoothAdapter?.isEnabled == true) {
                leScanner.flushPendingScanResults(callback)
                leScanner.stopScan(callback)
            }
        }
    }.flowOn(Dispatchers.Main) // System's BluetoothLeScanner class as issues if called on a background thread
        .withMockAdvertisements(context, filters)

    @RequiresPermission("android.permission.BLUETOOTH_SCAN")
    private fun scan(mode: Int) = rawScan(
        FILTERS,
        ScanSettings
            .Builder()
            .setScanMode(mode)
            .setMatchMode(MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(MATCH_NUM_ONE_ADVERTISEMENT)
            .build(),
    )
        .mapNotNull { result ->
            logger.v { "Sensor found during scan. Address: ${result.device.address}, scan bytes: ${result.scanRecord?.bytes?.toHexString()}" }
            (
                RawPecham(result)
                    ?: RawBekubeeKy(result)
                    ?: RawWicarlink(result)
                    ?: RawBekubeeTpms(result)
                    ?: RawSysgration(result)
                    ?: run {
                        logger.d { "Sensor not parsed. Scan bytes: ${result.scanRecord?.bytes?.toHexString()}" }
                        null
                    }
            )?.let { it to result.scanRecord?.advertisementHex }
        }
        // A real sensor emits the same value up to 10 times in a short time, to avoid to emit the
        // same value 10 times, only a change of the decoded packet goes through.
        .distinctUntilChangedBy { (raw, _) -> raw }
        .map { (raw, advertisement) ->
            // Kept whole, for what the decoders don't read yet
            when (val tyre = raw.asTyre()) {
                is Tyre.Unlocated -> tyre.copy(raw = advertisement)
                is Tyre.SensorLocated -> tyre.copy(raw = advertisement)
            }
        }
        .onEach { logger.d("Sensor content: $it") }

    private val lowLatencyScanFlow = scan(ScanSettings.SCAN_MODE_LOW_LATENCY).shared()

    override fun highDutyScan(): Flow<Tyre.SensorInput> = lowLatencyScanFlow

    @SuppressLint("MissingPermission")
    private val balancedScanFlow = scan(ScanSettings.SCAN_MODE_BALANCED).shared()

    override fun normalScan(): Flow<Tyre.SensorInput> = balancedScanFlow

    @SuppressLint("MissingPermission")
    override fun advertisements(mode: ScanMode, devices: List<DeviceMatch>?): Flow<Advertisement> =
        if (devices?.isEmpty() == true) emptyFlow()
        else rawScan(
            devices.orEmpty().flatMap { it.asFilters() },
            // Every packet, the broadcast period is read from them
            ScanSettings.Builder().setScanMode(mode.value).build(),
        ).map { result ->
            Advertisement(
                address = result.device.address,
                name = result.scanRecord?.deviceName,
                rssi = result.rssi,
                timestamp = result.timestampNanos.nanoseconds,
                isTyreSensor = result.scanRecord?.serviceUuids.orEmpty().any { it in SERVICE_UUIDS },
            )
        }

    private val ScanMode.value
        get() = when (this) {
            ScanMode.OPPORTUNISTIC -> ScanSettings.SCAN_MODE_OPPORTUNISTIC
            ScanMode.LOW_POWER -> ScanSettings.SCAN_MODE_LOW_POWER
            ScanMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
            ScanMode.LOW_LATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
        }

    /**
     * Either one matches, the system delivering what matches any of the filters. The public API
     * only filters on a public address, and some phones then miss a random one (most beacons): the
     * name makes up for it.
     */
    private fun DeviceMatch.asFilters(): List<ScanFilter> = buildList {
        add(ScanFilter.Builder().setDeviceAddress(address).build())
        name?.let { add(ScanFilter.Builder().setDeviceName(it).build()) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun Flow<Tyre.SensorInput>.shared() = this
        .materializeCompletion()
        .shareIn(GlobalScope + Dispatchers.Default, WhileSubscribed())
        .dematerializeCompletion()

    @SuppressLint("InlinedApi")
    @Suppress("MagicNumber")
    override fun missingPermission(): List<String> = when (Build.VERSION.SDK_INT) {
        in Int.MIN_VALUE..28 -> listOf(ACCESS_COARSE_LOCATION)
        in 29..30 -> listOf(ACCESS_FINE_LOCATION)
        in 31..Int.MAX_VALUE -> listOf(BLUETOOTH_CONNECT, BLUETOOTH_SCAN)
        else -> error("Unreachable condition")
    }.filter { checkSelfPermission(context, it) != PERMISSION_GRANTED }

    override val isBluetoothRequired = true

    @OptIn(ExperimentalUnsignedTypes::class)
    companion object {
        private val SERVICE_UUIDS = listOf(
            RawSysgration.SERVICE_UUID,
            RawPecham.SERVICE_UUID,
            RawWicarlink.SERVICE_UUID,
            RawBekubeeKy.SERVICE_UUID,
            RawBekubeeTpms.SERVICE_UUID
        )
        private val FILTERS = SERVICE_UUIDS.map { ScanFilter.Builder().setServiceUuid(it).build() }
    }
}
