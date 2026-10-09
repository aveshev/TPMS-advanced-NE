package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.masselis.tpmsadvanced.core.androidtest.EnterComposable
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable.ExitToken
import com.masselis.tpmsadvanced.core.androidtest.onEnterAndOnExit
import com.masselis.tpmsadvanced.core.androidtest.process
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle

@OptIn(ExperimentalTestApi::class)
public class ManageSensors private constructor(
    private val backButtonTag: String,
    composeTestRule: ComposeTestRule,
) :
    ComposeTestRule by composeTestRule,
    EnterExitComposable<ManageSensors> by onEnterAndOnExit(
        { composeTestRule.waitUntilExactlyOneExists(hasTestTag(ManageSensorsTags.root)) },
        { composeTestRule.waitUntilDoesNotExist(hasTestTag(ManageSensorsTags.root)) },
    ) {

    public fun wheel(location: Vehicle.Kind.Location, instructions: EnterComposable.Instructions<TyreTapArea>) {
        TyreTapArea(location).process(instructions)
    }

    public fun leave(): ExitToken<ManageSensors> {
        onNodeWithTag(backButtonTag).performClick()
        return exitToken
    }

    public companion object {
        context(rule: ComposeTestRule)
        public operator fun invoke(backButtonTag: String): ManageSensors = ManageSensors(backButtonTag, rule)
    }
}
