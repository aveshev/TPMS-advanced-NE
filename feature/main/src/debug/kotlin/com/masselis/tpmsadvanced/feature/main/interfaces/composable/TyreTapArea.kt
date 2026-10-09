package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.masselis.tpmsadvanced.core.androidtest.EnterComposable
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable.Instructions
import com.masselis.tpmsadvanced.core.androidtest.onEnter
import com.masselis.tpmsadvanced.core.androidtest.process
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle

/** A location of the vehicle shown, on the main screen or the sensors' page */
@OptIn(ExperimentalTestApi::class)
public class TyreTapArea private constructor(
    private val location: Vehicle.Kind.Location,
    composeTestRule: ComposeTestRule,
) :
    ComposeTestRule by composeTestRule,
    EnterComposable<TyreTapArea> by onEnter(
        { composeTestRule.waitUntilExactlyOneExists(hasTestTag(TyreTapAreaTags.root(location))) }
    ) {

    private val area
        get() = onNodeWithTag(TyreTapAreaTags.root(location))

    private val assignSensorDialog = AssignSensorDialog()
    private val manageSensorDialog = ManageSensorDialog()

    public fun waitUntilUnassigned() {
        waitUntilExactlyOneExists(hasTestTag(TyreReadoutTags.tapToAssign(location)))
    }

    public fun waitUntilAssigned() {
        waitUntilDoesNotExist(hasTestTag(TyreReadoutTags.tapToAssign(location)))
    }

    public fun assign(instructions: Instructions<AssignSensorDialog>) {
        area.performClick()
        assignSensorDialog.process(instructions)
        waitForIdle()
    }

    /** Sends the moving sensor here, see `ManageSensors` */
    public fun tapWhileMoving() {
        area.performClick()
        waitForIdle()
    }

    /** Only on the sensors' page, see `ManageSensors` */
    public fun manage(instructions: Instructions<ManageSensorDialog>) {
        area.performClick()
        manageSensorDialog.process(instructions)
        waitForIdle()
    }

    public companion object {
        context(rule: ComposeTestRule)
        public operator fun invoke(location: Vehicle.Kind.Location): TyreTapArea =
            TyreTapArea(location, rule)
    }
}
