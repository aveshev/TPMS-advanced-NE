package com.masselis.tpmsadvanced.interfaces.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable.ExitToken
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable.Instructions
import com.masselis.tpmsadvanced.core.androidtest.onEnterAndOnExit
import com.masselis.tpmsadvanced.core.androidtest.process
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.Settings
import com.masselis.tpmsadvanced.interfaces.composable.HomeTags
import com.masselis.tpmsadvanced.interfaces.composable.SettingsTag

@OptIn(ExperimentalTestApi::class)
internal class OverflowMenu private constructor(
    composeTestRule: ComposeTestRule
) :
    ComposeTestRule by composeTestRule,
    EnterExitComposable<OverflowMenu> by onEnterAndOnExit(
        { composeTestRule.waitUntilExactlyOneExists(hasTestTag(HomeTags.Overflow.root)) },
        { composeTestRule.waitUntilDoesNotExist(hasTestTag(HomeTags.Overflow.root)) }
    ) {

    private val settingsNode
        get() = onNodeWithTag(HomeTags.Overflow.settings)

    private val settingsTest = Settings(HomeTags.backButton, SettingsTag.vehicle)
    private val bindingMethodTest = BindingMethod()

    fun settings(instructions: Instructions<Settings>): ExitToken<OverflowMenu> {
        settingsNode.performClick()
        settingsTest.process(instructions)
        return exitToken
    }

    /** The binding method is reached through the vehicle settings, left afterwards if back on them */
    fun bindingMethod(instructions: Instructions<BindingMethod>): ExitToken<OverflowMenu> {
        settingsNode.performClick()
        settingsTest.process { bindSensors() }
        bindingMethodTest.process(instructions)
        // Going back from the binding method returns to the settings, finishing a binding goes home
        waitForIdle()
        if (onAllNodesWithTag(SettingsTag.vehicle).fetchSemanticsNodes().isNotEmpty())
            settingsTest.process { leave() }
        return exitToken
    }

    companion object {
        context(rule: ComposeTestRule)
        operator fun invoke(): OverflowMenu = OverflowMenu(rule)
    }
}
