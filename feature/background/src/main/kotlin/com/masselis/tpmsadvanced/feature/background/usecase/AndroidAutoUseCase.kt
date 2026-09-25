package com.masselis.tpmsadvanced.feature.background.usecase

import androidx.car.app.connection.CarConnection
import androidx.car.app.connection.CarConnection.CONNECTION_TYPE_PROJECTION
import androidx.lifecycle.Observer
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach

internal class AndroidAutoUseCase {

    private val logger = Logger.withTag("AndroidAutoUseCase")

    /**
     * Whether the phone projects to a car (Android Auto). A car with Android Automotive built in
     * is not a projection: the phone does not run the app there.
     */
    val connected: Flow<Boolean> = callbackFlow {
        val liveData = CarConnection(appContext).type
        val observer = Observer<Int> { trySend(it == CONNECTION_TYPE_PROJECTION) }
        liveData.observeForever(observer)
        awaitClose { liveData.removeObserver(observer) }
    }
        // LiveData can only be observed from the main thread
        .flowOn(Dispatchers.Main.immediate)
        .distinctUntilChanged()
        .onEach { logger.d { "Android Auto projection connected: $it" } }
}
