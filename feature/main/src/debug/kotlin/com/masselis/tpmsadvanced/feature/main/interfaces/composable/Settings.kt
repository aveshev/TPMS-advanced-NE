package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable.ExitToken
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable.Instructions
import com.masselis.tpmsadvanced.core.androidtest.check
import com.masselis.tpmsadvanced.core.androidtest.onEnterAndOnExit
import com.masselis.tpmsadvanced.core.androidtest.process

@OptIn(ExperimentalTestApi::class)
public class Settings(
    private val backButtonTag: String,
    private val containerTag: String,
    composeTestRule: ComposeTestRule,
) :
    ComposeTestRule by composeTestRule,
    EnterExitComposable<Settings> by onEnterAndOnExit(
        { composeTestRule.waitUntilExactlyOneExists(hasTestTag(containerTag)) },
        { composeTestRule.waitUntilDoesNotExist(hasTestTag(containerTag)) }
    ) {
    private val backButton
        get() = onNodeWithTag(backButtonTag)

    private val deleteVehicleButton
        get() = onNodeWithTag(DeleteVehicleButtonTags.Button.tag)

    private val deleteAllSensorsButton
        get() = onNodeWithTag(ClearBoundSensorsButtonTags.root)

    private val manageSensorsButton
        get() = onNodeWithTag(VehicleSettingsTags.manageSensors)

    private val manageSensorsTest = ManageSensors(backButtonTag)

    private val deleteVehicleDialogTest = DeleteVehicleDialog()

    public fun assertVehicleSettingsDisplayed() {
        onNodeWithTag(containerTag).assertIsDisplayed()
    }

    public fun deleteVehicle(instructions: Instructions<DeleteVehicleDialog>): ExitToken<Settings> {
        deleteVehicleButton.performScrollTo()
        deleteVehicleButton.performClick()
        deleteVehicleDialogTest.process(instructions)
        return exitToken
    }

    public fun assertVehicleDeleteIsNotEnabled() {
        deleteVehicleButton.assertIsNotEnabled()
    }

    /** Taps it, then confirms it, or cancels it when not [confirm] */
    public fun deleteAllSensors(confirm: Boolean = true) {
        deleteAllSensorsButton.performScrollTo()
        deleteAllSensorsButton.performClick()
        onNodeWithTag(if (confirm) ClearBoundSensorsButtonTags.confirm else ClearBoundSensorsButtonTags.cancel)
            .performClick()
    }

    public fun waitDeleteAllSensorsEnabled(): Unit =
        waitUntil { deleteAllSensorsButton.check(isEnabled()) }

    public fun waitDeleteAllSensorsDisabled() {
        waitUntil { deleteAllSensorsButton.check(isNotEnabled()) }
    }

    /** Opens the sensors' page, back on these settings once it's left */
    public fun manageSensors(instructions: Instructions<ManageSensors>) {
        manageSensorsButton.performScrollTo()
        manageSensorsButton.performClick()
        manageSensorsTest.process(instructions)
    }

    public fun leave(): ExitToken<Settings> {
        backButton.performClick()
        return exitToken
    }

    public companion object {
        context(rule: ComposeTestRule)
        public operator fun invoke(
            backButtonTag: String,
            containerTag: String
        ): Settings = Settings(backButtonTag, containerTag, rule)
    }
}