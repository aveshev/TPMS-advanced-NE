package com.masselis.tpmsadvanced.data.vehicle

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.interfaces.DatabaseExport
import com.masselis.tpmsadvanced.data.vehicle.interfaces.TyreDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.flags
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
internal class TyreDatabaseTest {

    @ContributesTo(AppScope::class)
    internal interface Extractor {
        val database: Database
        val databaseExport: DatabaseExport
    }

    private lateinit var database: Database
    private lateinit var databaseExport: DatabaseExport
    private lateinit var tyreDatabase: TyreDatabase
    private lateinit var currentVehicleUuid: UUID

    @Before
    fun setup() {
        appContext.getDatabasePath("car.db").delete()
        database = (appGraph as Extractor).database
        databaseExport = (appGraph as Extractor).databaseExport
        tyreDatabase = TyreDatabase(database)
        currentVehicleUuid = database.vehicleQueries.currentFavourite().executeAsOne().uuid
    }

    private fun tyre(raw: String?) = Tyre.Located(
        1.0, -60, 42, 2f.bar, 20f.celsius, 100u, false, Location.Wheel(FRONT_LEFT), null, raw,
    )

    private fun latest() = tyreDatabase
        .latestByTyreLocationByVehicle(Location.Wheel(FRONT_LEFT), currentVehicleUuid)
        .execute()

    // A real Pecham advertisement, whose status byte is 0x80
    private val pecham = "0303a5270308425208ff801b1b02a584ea"

    @Test
    fun rawIsStoredAndTheFlagsReadFromIt() = runTest {
        tyreDatabase.insert(tyre(pecham), currentVehicleUuid)
        assertEquals(pecham, latest()?.raw)
        assertEquals(listOf<UByte>(0x80u), latest()?.flags)
    }

    @Test
    fun withoutRawThereAreNoFlags() = runTest {
        tyreDatabase.insert(tyre(null), currentVehicleUuid)
        assertNull(latest()?.raw)
        assertNull(latest()?.flags)
    }

    @Test
    fun exportHoldsTheReadings() = runTest {
        tyreDatabase.insert(tyre(pecham), currentVehicleUuid)
        val file = File(appContext.cacheDir, "database_export/test.db")
        // Twice: an existing copy must be replaced, VACUUM INTO alone refuses to overwrite
        databaseExport.exportTo(file)
        databaseExport.exportTo(file)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { copy ->
            copy.rawQuery("SELECT raw FROM Tyre", null).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(pecham, cursor.getString(0))
            }
        }
    }
}
