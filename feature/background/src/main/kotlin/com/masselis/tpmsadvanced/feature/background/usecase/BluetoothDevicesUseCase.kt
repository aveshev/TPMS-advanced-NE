package com.masselis.tpmsadvanced.feature.background.usecase

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED
import android.bluetooth.BluetoothAdapter.EXTRA_STATE
import android.bluetooth.BluetoothAdapter.STATE_OFF
import android.bluetooth.BluetoothAdapter.STATE_ON
import android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED
import android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED
import android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED
import android.bluetooth.BluetoothDevice.ACTION_NAME_CHANGED
import android.bluetooth.BluetoothDevice.ACTION_UUID
import android.bluetooth.BluetoothDevice.EXTRA_DEVICE
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothProfile.A2DP
import android.bluetooth.BluetoothProfile.GATT
import android.bluetooth.BluetoothProfile.HEADSET
import android.bluetooth.BluetoothProfile.LE_AUDIO
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.R
import android.os.Build.VERSION_CODES.S
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_EXPORTED
import androidx.core.content.IntentCompat
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.asFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The paired Bluetooth devices, and which of them are connected. Devices are identified by their
 * address: for a paired device it is its identity address, which stays the same even when an LE
 * device randomizes the address it advertises with, and when it is paired again later.
 */
// BLUETOOTH_CONNECT is part of the monitoring permissions, still checked here since it can be
// revoked from the system settings at any time
@SuppressLint("MissingPermission")
internal class BluetoothDevicesUseCase(scope: CoroutineScope) {

    private val logger = Logger.withTag("BluetoothDevicesUseCase")

    data class PairedDevice(val address: String, val name: String, val isAudio: Boolean) {
        companion object {
            /**
             * Either tells it apart: the device class is always known but the LE Audio devices don't
             * necessarily declare an audio one, and the services an LE-only device offers aren't
             * always known to the system. When in doubt, the device counts as an audio one.
             */
            fun isAudio(majorDeviceClass: Int?, serviceUuids: List<UUID>) =
                majorDeviceClass == AUDIO_VIDEO || serviceUuids.any { it in audioServices }

            // A2DP sink, headset (both roles), hands-free, LE Audio (ASCS, PACS), hearing aid (ASHA)
            private val audioServices = listOf(0x110B, 0x1108, 0x1131, 0x111E, 0x184E, 0x1850, 0xFDF0)
                .map { UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(it)) }
        }
    }

    private val manager
        get() = appContext.getSystemService<BluetoothManager>()

    /** Every paired device, null while Bluetooth is off: the system doesn't tell them then */
    val paired: Flow<List<PairedDevice>?> = IntentFilter()
        .apply {
            addAction(ACTION_STATE_CHANGED)
            addAction(ACTION_BOND_STATE_CHANGED)
            addAction(ACTION_NAME_CHANGED)
            // The services of a newly paired device are read right after it is paired
            addAction(ACTION_UUID)
        }
        // Protected system broadcasts, see asFlow()
        .asFlow(RECEIVER_EXPORTED)
        .map { }
        .onStart { emit(Unit) }
        .map {
            manager
                ?.adapter
                ?.takeIf { it.isEnabled }
                ?.let { adapter -> adapter.takeIf { hasPermission() }?.bondedDevices.orEmpty() }
                ?.map { it.asPairedDevice() }
        }
        .distinctUntilChanged()

    /**
     * The paired devices connected right now, whatever connects them: audio, LE, etc. An unpaired
     * device can connect too (an LE device an app connects to without pairing), it is never
     * reported. Shared, so that a single set of system listeners is registered.
     */
    val connected: Flow<Set<PairedDevice>> = callbackFlow {
        val adapter = manager?.adapter
        if (adapter == null || hasPermission().not()) {
            send(emptySet())
            awaitClose()
            return@callbackFlow
        }
        // Every connected address, paired or not: a device paired while connected counts right away
        val addresses = ConcurrentHashMap.newKeySet<String>()
        val proxies = ConcurrentHashMap<Int, BluetoothProfile>()
        val closed = AtomicBoolean(false)
        fun publish() {
            adapter
                .bondedDevices
                .orEmpty()
                .filter { it.address in addresses }
                .map { it.asPairedDevice() }
                .toSet()
                .also { trySend(it) }
        }

        fun readConnected() {
            addresses += manager?.getConnectedDevices(GATT).orEmpty().map { it.address }
            addresses += proxies.values.flatMap { it.connectedDevices }.map { it.address }
            publish()
        }
        // Connections and disconnections are told by the broadcasts. The ones that already exist
        // when starting are read from the audio profiles and from GATT, which covers what matters
        // here (car stereos, intercoms, headsets): another kind of connection made before is only
        // known on its next change.
        launch {
            IntentFilter()
                .apply {
                    addAction(ACTION_ACL_CONNECTED)
                    addAction(ACTION_ACL_DISCONNECTED)
                    addAction(ACTION_STATE_CHANGED)
                    addAction(ACTION_BOND_STATE_CHANGED)
                }
                // Protected system broadcasts, see asFlow()
                .asFlow(RECEIVER_EXPORTED)
                .collect { intent ->
                    when (intent.action) {
                        ACTION_ACL_CONNECTED -> intent.device?.let { addresses += it.address }
                        ACTION_ACL_DISCONNECTED -> intent.device?.let { addresses -= it.address }
                        ACTION_STATE_CHANGED -> when (intent.getIntExtra(EXTRA_STATE, STATE_OFF)) {
                            STATE_ON -> readConnected()
                            // Every connection ends with it, not necessarily with a broadcast each
                            else -> addresses.clear()
                        }
                    }
                    publish()
                }
        }
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                // Connected after the flow ended: nobody would close it
                if (closed.get()) return adapter.closeProfileProxy(profile, proxy)
                proxies[profile] = proxy
                readConnected()
            }

            override fun onServiceDisconnected(profile: Int) {
                proxies -= profile
            }
        }
        listOfNotNull(A2DP, HEADSET, LE_AUDIO.takeIf { SDK_INT >= TIRAMISU })
            .forEach { adapter.getProfileProxy(appContext, listener, it) }
        readConnected()
        awaitClose {
            closed.set(true)
            proxies.forEach { (profile, proxy) -> adapter.closeProfileProxy(profile, proxy) }
        }
    }
        .distinctUntilChanged()
        .onEach { logger.d { "Paired Bluetooth devices connected: ${it.size}" } }
        .shareIn(scope, WhileSubscribed(), replay = 1)

    private fun hasPermission() = SDK_INT < S ||
        ContextCompat.checkSelfPermission(appContext, BLUETOOTH_CONNECT) == PERMISSION_GRANTED

    private fun BluetoothDevice.asPairedDevice() = PairedDevice(
        address = address,
        name = displayName,
        isAudio = PairedDevice.isAudio(bluetoothClass?.majorDeviceClass, uuids.orEmpty().map { it.uuid }),
    )

    private val Intent.device: BluetoothDevice?
        get() = IntentCompat.getParcelableExtra(this, EXTRA_DEVICE, BluetoothDevice::class.java)

    // The name the user gave it in the system settings first, like the system shows it
    private val BluetoothDevice.displayName: String
        get() = (if (SDK_INT >= R) alias else null) ?: name ?: address
}
