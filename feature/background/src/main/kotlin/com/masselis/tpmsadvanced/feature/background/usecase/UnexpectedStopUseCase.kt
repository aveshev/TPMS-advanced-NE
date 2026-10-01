package com.masselis.tpmsadvanced.feature.background.usecase

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.ApplicationExitInfo.REASON_ANR
import android.app.ApplicationExitInfo.REASON_CRASH
import android.app.ApplicationExitInfo.REASON_CRASH_NATIVE
import android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
import android.app.ApplicationExitInfo.REASON_LOW_MEMORY
import android.app.ApplicationExitInfo.REASON_PERMISSION_CHANGE
import android.app.ApplicationExitInfo.REASON_UNKNOWN
import android.app.ApplicationExitInfo.REASON_USER_REQUESTED
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.R
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.Crash
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.ForceStop
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.Killed
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.LowMemory
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.PermissionRevoked
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.Unknown
import kotlinx.coroutines.flow.StateFlow

/**
 * Finds out that the persistent scanning service died without being stopped, which power
 * management does silently on some phones.
 *
 * While the service runs in persistent mode, a marker is kept on disk and only [stopped] removes
 * it. A process killed without notice never removes it, so the next app opening finds the marker
 * while the service does not run. A service the system restarts on its own (it is sticky) writes
 * the marker again, and is not reported since monitoring went on.
 */
internal class UnexpectedStopUseCase(
    private val isRunning: StateFlow<Boolean>,
) {
    private val preferences = appContext.getSharedPreferences("MONITOR_SERVICE", MODE_PRIVATE)

    /**
     * Called by every start of the service, [since] being when this instance of the service was
     * created. Written synchronously: the process can be killed right after.
     */
    fun started(persistent: Boolean, since: Long) {
        if (persistent) preferences.edit(commit = true) { putLong(RUNNING_SINCE, since) }
        else stopped()
    }

    /** The service stopped on purpose (the user, the settings...), nothing to report */
    fun stopped() {
        preferences.edit(commit = true) { remove(RUNNING_SINCE) }
    }

    /**
     * Reports the service's death once: the marker is removed, unless the service runs in this
     * process, in which case it did not die.
     */
    fun consume(): UnexpectedStop? = preferences
        .getLong(RUNNING_SINCE, NOT_RUNNING)
        .takeIf { it != NOT_RUNNING }
        ?.takeIf { isRunning.value.not() }
        ?.also { stopped() }
        // A service started before the last update or boot was killed by it. The boot receiver
        // removes the marker too, but the app can be opened before it runs, and a build without
        // this class (another branch, an older version) leaves the marker behind.
        ?.takeIf { since -> since >= lastUpdateTime() && since >= bootTime() }
        ?.let { since ->
            if (SDK_INT >= R) exitAfter(since)
                ?.let { UnexpectedStop(it.timestamp, Cause.of(it.reason)) }
                ?: UnexpectedStop(null, Unknown)
            else UnexpectedStop(null, Unknown)
        }

    /**
     * The first death of the app's process once the service started. Later ones are not about the
     * service: the process can be started again (a broadcast, a widget...) and killed again as a
     * cached process before the app is opened.
     */
    private fun lastUpdateTime(): Long = appContext
        .packageManager
        .run {
            if (SDK_INT >= TIRAMISU) {
                getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                getPackageInfo(appContext.packageName, 0)
            }
        }
        .lastUpdateTime

    private fun bootTime(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    @RequiresApi(R)
    private fun exitAfter(since: Long): ApplicationExitInfo? = appContext
        .getSystemService<ActivityManager>()
        ?.getHistoricalProcessExitReasons(null, 0, 0)
        ?.filter { it.timestamp >= since }
        ?.minByOrNull { it.timestamp }

    /** @property stoppedAt when the process died, unknown before API 30 */
    data class UnexpectedStop(val stoppedAt: Long?, val cause: Cause)

    enum class Cause {
        /** Force-stopped from its settings, removed from the recent apps, or an OEM killer doing the same */
        ForceStop,
        LowMemory,
        Crash,
        PermissionRevoked,

        /** Any other kill by the system, power management included */
        Killed,

        /** Before API 30, or when the system kept no record of it */
        Unknown;

        companion object {
            fun of(reason: Int): Cause = when (reason) {
                REASON_USER_REQUESTED -> ForceStop
                REASON_LOW_MEMORY -> LowMemory
                REASON_CRASH, REASON_CRASH_NATIVE, REASON_ANR, REASON_INITIALIZATION_FAILURE -> Crash
                REASON_PERMISSION_CHANGE -> PermissionRevoked
                REASON_UNKNOWN -> Unknown
                else -> Killed
            }
        }
    }

    private companion object {
        const val RUNNING_SINCE = "RUNNING_SINCE"
        const val NOT_RUNNING = Long.MIN_VALUE
    }
}
