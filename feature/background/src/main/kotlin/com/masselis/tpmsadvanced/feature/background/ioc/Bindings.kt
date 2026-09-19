package com.masselis.tpmsadvanced.feature.background.ioc

import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.BackgroundViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides

@Suppress("unused")
@ContributesTo(AppScope::class)
public interface Bindings {

    @Provides
    private fun backgroundViewModel(): BackgroundViewModel = BackgroundViewModel()

    public val featureBackgroundInternal: Internal

    @Inject
    public class Internal internal constructor(
        internal val backgroundViewModel: () -> BackgroundViewModel,
    )

    public companion object : Bindings by appGraph as Bindings
}
