package com.masselis.tpmsadvanced.data.vehicle.interfaces

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.io.File

/** Copies the whole database out of the app, to look into its readings elsewhere */
public class DatabaseExport internal constructor(private val driver: SqlDriver) {

    /**
     * A consistent copy, safe to take while the scanner writes: unlike copying the file, VACUUM INTO
     * also takes what still sits in the write-ahead log. The app's bundled SQLite supports it on
     * every Android version.
     */
    public suspend fun exportTo(file: File): Unit = withContext(IO) {
        // VACUUM INTO refuses to overwrite a file
        file.parentFile?.mkdirs()
        file.delete()
        driver.execute(null, "VACUUM INTO ?", 1) { bindString(0, file.path) }
    }
}
