package com.masselis.tpmsadvanced.feature.background.usecase

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.NEARBY_WIFI_DEVICES
import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager.UNKNOWN_SSID
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.content.getSystemService
import com.masselis.tpmsadvanced.core.common.appContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@SuppressLint("MissingPermission")
internal class WifiConnectionUseCase {

    sealed interface State {
        data object Disconnected : State

        data class Connected(val ssid: String?) : State
    }

    val state: Flow<State> = callbackFlow {
        val connectivityManager = appContext.getSystemService<ConnectivityManager>()!!
        val callback = networkCallback(
            onCapabilitiesChanged = { trySend(State.Connected(it.wifiSsid())) },
            onLost = { trySend(State.Disconnected) },
        )
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(TRANSPORT_WIFI).build(),
            callback,
        )
        send(State.Disconnected)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    // Since API 31, NetworkCapabilities redacts location-sensitive transport info (including the
    // WiFi SSID) by default, even when the caller holds the permission and location is enabled —
    // FLAG_INCLUDE_LOCATION_INFO on the callback itself is required to opt back in. Below API 31
    // no such redaction exists (and the flags constructor doesn't exist at all), so the plain
    // no-arg constructor already returns the real SSID. The two constructors are called from two
    // distinct anonymous classes on purpose: NetworkCallback(Int) doesn't exist pre-31, so a
    // single call site guarded only by a runtime SDK_INT check would still fail to resolve on
    // older devices.
    private fun networkCallback(
        onCapabilitiesChanged: (NetworkCapabilities) -> Unit,
        onLost: () -> Unit,
    ): NetworkCallback = if (SDK_INT >= S) {
        object : NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = onCapabilitiesChanged(networkCapabilities)

            override fun onLost(network: Network) = onLost()
        }
    } else {
        object : NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = onCapabilitiesChanged(networkCapabilities)

            override fun onLost(network: Network) = onLost()
        }
    }

    private fun NetworkCapabilities.wifiSsid(): String? = (transportInfo as? WifiInfo)
        ?.ssid
        ?.removeSurrounding("\"")
        ?.takeUnless { it == UNKNOWN_SSID }

    // ACCESS_FINE_LOCATION is what actually unredacts the SSID on every API level (confirmed
    // on-device: NEARBY_WIFI_DEVICES alone was NOT sufficient on API 33+, contrary to what its
    // introduction suggested it would replace). NEARBY_WIFI_DEVICES is requested alongside it on
    // API 33+ since that's still the officially documented permission for this capability there.
    fun missingPermission(): List<String> = when {
        SDK_INT >= TIRAMISU -> listOf(ACCESS_FINE_LOCATION, NEARBY_WIFI_DEVICES)
        else -> listOf(ACCESS_FINE_LOCATION)
    }.filter { checkSelfPermission(appContext, it) != PERMISSION_GRANTED }
}
