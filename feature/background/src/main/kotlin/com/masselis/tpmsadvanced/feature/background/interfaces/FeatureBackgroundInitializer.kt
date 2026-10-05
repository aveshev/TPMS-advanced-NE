package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.Context
import com.masselis.tpmsadvanced.core.common.AppGraphReadyInitializer
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings

/** Alerts whatever scans, the main screen or the monitor service, from the app's start */
public class FeatureBackgroundInitializer : AppGraphReadyInitializer<Unit> {
    override fun create(context: Context) {
        Bindings.featureBackgroundInternal.alertNotifier()
    }
}
