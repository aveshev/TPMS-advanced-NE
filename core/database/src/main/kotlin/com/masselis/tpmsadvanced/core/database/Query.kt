package com.masselis.tpmsadvanced.core.database

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.CoroutineContext
import app.cash.sqldelight.Query as SqlDelightQuery

/**
 * [transform] turns each row into what the query returns, a row it returns null for is skipped:
 * see [asList] for the rows which may not be readable.
 */
public class QueryList<T : Any> private constructor(
    private val source: SqlDelightQuery<out Any>,
    private val transform: (Any) -> T?,
) {
    public fun execute(): List<T> = source.executeAsList().mapNotNull(transform)

    public fun asFlow(context: CoroutineContext = IO): Flow<List<T>> = source
        .asFlow()
        .mapToList(context)
        .map { it.mapNotNull(transform) }

    public fun asChillFlow(context: CoroutineContext = IO): Flow<List<T>> = source
        .asChillFlow()
        .mapToList(context)
        .map { it.mapNotNull(transform) }

    /**
     * Under the hood [asStateFlow] uses [asChillFlow] to retrieve values from the database when its
     * updated. Since it uses [asChillFlow], using [SharingStarted.WhileSubscribed] is not
     * recommended because value could be inserted, removed or updated into the database when the
     * subscription count is set to 0 but [asChillFlow] will no retrieve them when the first
     * subscriber comes. As consequence, the state will host an outdated value and the latest will
     * not being fetch at all.
     */
    public fun asStateFlow(
        scope: CoroutineScope,
        started: SharingStarted
    ): StateFlow<List<T>> = source
        .asChillFlow()
        .map { query -> query.awaitAsList().mapNotNull(transform) }
        .stateIn(scope, started, execute())

    public companion object {
        @Suppress("UNCHECKED_CAST")
        public fun <T : Any> SqlDelightQuery<T>.asList(): QueryList<T> = QueryList(this) { it as T }

        /** For the rows which can't always be read, such as stored data decoded when read back */
        @Suppress("UNCHECKED_CAST")
        public fun <R : Any, T : Any> SqlDelightQuery<R>.asList(transform: (R) -> T?): QueryList<T> =
            QueryList(this) { transform(it as R) }
    }
}

public class QueryOne<out T : Any> private constructor(
    private val source: SqlDelightQuery<T>
) {
    public fun execute(): T = source.executeAsOne()

    public fun asFlow(context: CoroutineContext = IO): Flow<T> = source
        .asFlow()
        .mapToOne(context)

    public fun asChillFlow(context: CoroutineContext = IO): Flow<T> = source
        .asChillFlow()
        .mapToOne(context)

    /**
     * Under the hood [asStateFlow] uses [asChillFlow] to retrieve values from the database when its
     * updated. Since it uses [asChillFlow], using [SharingStarted.WhileSubscribed] is not
     * recommended because value could be inserted, removed or updated into the database when the
     * subscription count is set to 0 but [asChillFlow] will no retrieve them when the first
     * subscriber comes. As consequence, the state will host an outdated value and the latest will
     * not being fetch at all.
     */
    public fun asStateFlow(
        scope: CoroutineScope,
        started: SharingStarted
    ): StateFlow<T> = source
        .asChillFlow()
        .map { it.awaitAsOne() }
        .stateIn(scope, started, execute())

    public companion object {
        public fun <T : Any> SqlDelightQuery<T>.asOne(): QueryOne<T> = QueryOne(this)
    }
}

/** Same as [QueryList] for its [transform] */
public class QueryOneOrNull<out T : Any> private constructor(
    private val source: SqlDelightQuery<out Any>,
    private val transform: (Any) -> T?,
) {
    public fun execute(): T? = source.executeAsOneOrNull()?.let(transform)

    public fun asFlow(context: CoroutineContext = IO): Flow<T?> = source
        .asFlow()
        .mapToOneOrNull(context)
        .map { it?.let(transform) }

    public fun asChillFlow(context: CoroutineContext = IO): Flow<T?> = source
        .asChillFlow()
        .mapToOneOrNull(context)
        .map { it?.let(transform) }

    /**
     * Under the hood [asStateFlow] uses [asChillFlow] to retrieve values from the database when its
     * updated. Since it uses [asChillFlow], using [SharingStarted.WhileSubscribed] is not
     * recommended because value could be inserted, removed or updated into the database when the
     * subscription count is set to 0 but [asChillFlow] will no retrieve them when the first
     * subscriber comes. As consequence, the state will host an outdated value and the latest will
     * not being fetch at all.
     */
    public fun asStateFlow(
        scope: CoroutineScope,
        started: SharingStarted
    ): StateFlow<T?> = source
        .asChillFlow()
        .map { query -> query.awaitAsOneOrNull()?.let(transform) }
        .stateIn(scope, started, execute())

    public companion object {
        @Suppress("UNCHECKED_CAST")
        public fun <T : Any> SqlDelightQuery<T>.asOneOrNull(): QueryOneOrNull<T> =
            QueryOneOrNull(this) { it as T }

        /** See [QueryList.asList] */
        @Suppress("UNCHECKED_CAST")
        public fun <R : Any, T : Any> SqlDelightQuery<R>.asOneOrNull(transform: (R) -> T?): QueryOneOrNull<T> =
            QueryOneOrNull(this) { transform(it as R) }
    }
}

private fun <T : Any> SqlDelightQuery<T>.asChillFlow(): Flow<SqlDelightQuery<T>> = flow {
    val channel = Channel<Unit>(Channel.CONFLATED)

    val listener = SqlDelightQuery.Listener {
        channel.trySend(Unit)
    }

    addListener(listener)
    try {
        for (ignored in channel) {
            emit(this@asChillFlow)
        }
    } finally {
        removeListener(listener)
    }
}
