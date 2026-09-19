package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle.Event.ON_RESUME
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.masselis.tpmsadvanced.core.common.appContext

/** The journey that gets the location permission the WiFi exception needs, see [rememberWifiExceptionPermission] */
internal class WifiExceptionPermission(
    private val onRequest: () -> Unit,
    private val inProgress: () -> Boolean,
) {
    /**
     * Asks for the permission if it is missing. If the user ends up refusing it, the option is
     * disabled and the user is told so.
     */
    fun request() = onRequest()

    /** Read live, so it stays correct when this instance was captured by a long-lived effect */
    fun isInProgress() = inProgress()
}

/**
 * Also emits the dialogs of the journey, so it must be called from a place that stays in the
 * composition for as long as the journey can run.
 *
 * There is no system permission popup: the permission the exception needs ("Allow all the time")
 * cannot be granted from one, so the journey starts with a rationale that sends the user to the
 * app's Settings. Cancelling that rationale, or coming back from Settings without the permission,
 * ends the journey: [onDenied] must turn the option off, and a dialog tells the user that it was,
 * unless [isSettingsOnScreen]: the user then sees the toggle turn off.
 */
@Composable
internal fun rememberWifiExceptionPermission(
    permissions: List<String>,
    isSettingsOnScreen: () -> Boolean,
    onDenied: () -> Unit,
): WifiExceptionPermission {
    val activity = LocalActivity.current
    val currentOnDenied by rememberUpdatedState(onDenied)
    val currentIsSettingsOnScreen by rememberUpdatedState(isSettingsOnScreen)
    var inProgress by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    var waitingForSettings by remember { mutableStateOf(false) }
    var showDisabledInfo by remember { mutableStateOf(false) }

    // Read directly rather than through a permission state that refreshes on its own schedule
    fun isGranted() = permissions.all { checkSelfPermission(appContext, it) == PERMISSION_GRANTED }

    fun denied() {
        inProgress = false
        waitingForSettings = false
        showRationale = false
        // On the settings screen the toggle turning itself off is explanation enough
        showDisabledInfo = currentIsSettingsOnScreen().not()
        currentOnDenied()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Back from the Settings the rationale sent the user to
            if (event == ON_RESUME && waitingForSettings) {
                waitingForSettings = false
                if (isGranted()) inProgress = false else denied()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showRationale) {
        LocationPermissionAlert(
            onDismissRequest = ::denied,
            onConfirm = {
                showRationale = false
                waitingForSettings = true
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .apply { addCategory(Intent.CATEGORY_DEFAULT) }
                    .apply { data = "package:${appContext.packageName}".toUri() }
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) }
                    .apply { addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) }
                    .also { activity?.startActivity(it) }
            }
        )
    }
    if (showDisabledInfo) {
        WifiExceptionDisabledAlert(onDismissRequest = { showDisabledInfo = false })
    }

    return WifiExceptionPermission(
        onRequest = {
            if (isGranted()) {
                inProgress = false
            } else {
                inProgress = true
                showRationale = true
            }
        },
        inProgress = { inProgress },
    )
}

@Composable
private fun LocationPermissionAlert(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(
                text = "Identifying which WiFi network you're connected to — so it can be" +
                        " excepted from the WiFi suspend setting — needs the location permission." +
                        " In the app's settings, open Permissions → Location, then select" +
                        " \"Allow all the time\" and turn on \"Use precise location\". This is the" +
                        " only way the current WiFi name can be read while the app is in the" +
                        " background."
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Open settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
private fun WifiExceptionDisabledAlert(onDismissRequest: () -> Unit) {
    AlertDialog(
        text = {
            Text(
                text = "The exception for certain WiFis has been turned off, because the location" +
                        " permission it needs (\"Allow all the time\", with \"Use precise location\")" +
                        " was not granted. You can turn it on again in App settings, under" +
                        " Background scanning."
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "OK")
            }
        }
    )
}
