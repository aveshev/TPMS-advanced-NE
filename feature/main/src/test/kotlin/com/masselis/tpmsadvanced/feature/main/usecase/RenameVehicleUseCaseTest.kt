package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertFailsWith

internal class RenameVehicleUseCaseTest {

    private lateinit var vehicle: Vehicle
    private lateinit var database: VehicleDatabase
    private lateinit var uuid: UUID

    private fun test() = RenameVehicleUseCase(vehicle, database)

    @Before
    fun setup() {
        val generatedUuid = UUID.randomUUID()
        uuid = generatedUuid
        vehicle = mockk { every { this@mockk.uuid } returns generatedUuid }
        database = mockk { coEvery { updateName(any(), any()) } returns Unit }
    }

    @Test
    fun `the name is saved without its surrounding spaces`() = runTest {
        test().rename("  My van ")
        coVerify { database.updateName("My van", uuid) }
    }

    @Test
    fun `a blank name is refused and nothing is saved`() = runTest {
        assertFailsWith<IllegalArgumentException> { test().rename("   ") }
        coVerify(exactly = 0) { database.updateName(any(), any()) }
    }
}
