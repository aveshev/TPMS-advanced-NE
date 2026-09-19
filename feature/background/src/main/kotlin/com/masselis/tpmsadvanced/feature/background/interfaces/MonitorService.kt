package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings
import com.masselis.tpmsadvanced.feature.background.ioc.vehicle.ServiceComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class MonitorService : LifecycleService() {

    private lateinit var component: ServiceComponent

    override fun onCreate() {
        isRunningMutableStateFlow.value = true
        super.onCreate()
    }

    override fun onStartCommand(
        intent: Intent?, flags: Int, startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)
        // The service is started again while it runs (the app being opened...), a second
        // component would monitor everything twice. The intent is null after a sticky restart.
        if (::component.isInitialized.not()) component = ServiceComponent(this, lifecycleScope)
        return if (Bindings.featureBackgroundInternal.appPreferences.persistentScanning.value) {
            START_STICKY
        } else {
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunningMutableStateFlow.value = false
    }

    companion object {
        private val isRunningMutableStateFlow = MutableStateFlow(false)
        val isRunning = isRunningMutableStateFlow.asStateFlow()

        fun intent() = Intent(appContext, MonitorService::class.java)
    }
}
