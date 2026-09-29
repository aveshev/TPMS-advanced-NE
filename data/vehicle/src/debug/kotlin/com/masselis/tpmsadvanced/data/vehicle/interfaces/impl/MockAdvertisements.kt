package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.Manifest.permission.DUMP
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.PHY_LE_1M
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanResult.PERIODIC_INTERVAL_NOT_PRESENT
import android.bluetooth.le.ScanResult.PHY_UNUSED
import android.bluetooth.le.ScanResult.SID_NOT_PRESENT
import android.bluetooth.le.ScanResult.TX_POWER_NOT_PRESENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcel
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_EXPORTED
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge

private const val ACTION = "com.masselis.tpmsadvanced.MOCK_ADVERTISEMENT"
private const val EXTRA_BYTES = "bytes"
private const val EXTRA_ADDRESS = "address"
private const val EXTRA_RSSI = "rssi"
private const val DEFAULT_ADDRESS = "00:11:22:33:44:55"
private const val DEFAULT_RSSI = -60

// What the legacy `ScanResult` constructor sets: complete data, legacy PDU, connectable
private const val LEGACY_EVENT_TYPE = 0x11

private val logger = Logger.withTag("MockAdvertisements")

/**
 * Debug builds only: merges BLE advertisements sent over adb into the real scan, so they go through
 * the same scan filters and decoders as packets received over the air.
 *
 * ```
 * adb shell am broadcast -a com.masselis.tpmsadvanced.MOCK_ADVERTISEMENT -p com.masselis.tpmsadvanced \
 *     --es bytes 0201060303B0FB12FFAC00D043520008DA001D10FF1100AF2206 \
 *     --es address 00:11:22:33:44:55 \
 *     --ei rssi -60
 * ```
 *
 * `bytes` is the raw advertisement in hex, as `ScanRecord.getBytes()` returns it (the scan logs
 * print it for real sensors). `address` and `rssi` are optional. Packets are only received while a
 * scan is running, and only from a sender holding `android.permission.DUMP`, which the adb shell
 * does and regular apps can't.
 */
@Suppress("MaxLineLength")
internal fun Flow<ScanResult>.withMockAdvertisements(
    context: Context,
    filters: List<ScanFilter>,
): Flow<ScanResult> = merge(
    this,
    callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching {
                    requireNotNull(intent.getStringExtra(EXTRA_BYTES)) { "Missing the \"$EXTRA_BYTES\" extra" }
                        .hexToByteArray()
                        .asScanResult(
                            device = context.getSystemService<BluetoothManager>()
                                ?.adapter
                                ?.getRemoteDevice(intent.getStringExtra(EXTRA_ADDRESS)?.uppercase() ?: DEFAULT_ADDRESS)
                                ?: error("No Bluetooth adapter"),
                            rssi = intent.getIntExtra(EXTRA_RSSI, DEFAULT_RSSI),
                        )
                        .also { result -> check(filters.any { it.matches(result) }) { "Matches none of the scan filters" } }
                }
                    .onSuccess { logger.d { "Mock advertisement received from ${it.device.address}" } }
                    .onSuccess { trySend(it) }
                    // Only the reason, the stack trace is noise for whoever reads it through adb
                    .onFailure { logger.w { "Mock advertisement dropped: ${it.message}" } }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION), DUMP, null, RECEIVER_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }
)

/**
 * `ScanRecord` has no public constructor and its parser, `ScanRecord.parseFromBytes`, is hidden.
 * `ScanResult` is parcelable though, and reading it from a parcel runs that parser, so this writes
 * the parcel the system would send over Binder and reads it back. The fields and their order mirror
 * `ScanResult.writeToParcel`, which hasn't changed since API 26.
 */
private fun ByteArray.asScanResult(device: BluetoothDevice, rssi: Int): ScanResult = Parcel
    .obtain()
    .run {
        try {
            writeInt(1) // Device is present
            device.writeToParcel(this, 0)
            writeInt(1) // Scan record is present
            writeByteArray(this@asScanResult)
            writeInt(rssi)
            writeLong(SystemClock.elapsedRealtimeNanos())
            writeInt(LEGACY_EVENT_TYPE)
            writeInt(PHY_LE_1M) // Primary PHY
            writeInt(PHY_UNUSED) // Secondary PHY
            writeInt(SID_NOT_PRESENT)
            writeInt(TX_POWER_NOT_PRESENT)
            writeInt(PERIODIC_INTERVAL_NOT_PRESENT)
            setDataPosition(0)
            ScanResult.CREATOR.createFromParcel(this)
        } finally {
            recycle()
        }
    }
