package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.masselis.tpmsadvanced.core.common.appContext

internal class DisableMonitorBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        appContext
            .stopService(Intent(appContext, MonitorService::class.java))
            .takeIf { it }
            ?.also { appContext.flash("Disabled TPMS background monitoring") }
    }

    internal companion object {
        fun intent() = Intent(appContext, DisableMonitorBroadcastReceiver::class.java)
    }
}
