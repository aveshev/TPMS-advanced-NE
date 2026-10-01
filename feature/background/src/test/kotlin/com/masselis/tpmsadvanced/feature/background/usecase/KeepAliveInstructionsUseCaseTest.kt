package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.feature.background.usecase.KeepAliveInstructionsUseCase.Companion.sections
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class KeepAliveInstructionsUseCaseTest {

    @Test
    fun `a manufacturer is matched whatever its case`() {
        assertEquals("samsung", KeepAliveInstructionsUseCase("SAMSUNG").vendor)
    }

    @Test
    fun `spaces become dashes`() {
        assertEquals("hmd-global", KeepAliveInstructionsUseCase("HMD Global").vendor)
    }

    @Test
    fun `a manufacturer without a page falls back to the general one`() {
        KeepAliveInstructionsUseCase("Fairphone").also {
            assertEquals("general", it.vendor)
            assertEquals("https://dontkillmyapp.com/general", it.pageUrl)
        }
    }

    private val page = "<h2 id=\"intro\">Intro</h2><p>a</p>" +
        "<h2 id=\"android-14\">14</h2><p>b</p>" +
        "<h2 id=\"android-13\">13</h2><p>c</p>" +
        "<h2 id=\"android-11\">11</h2><p>d</p>"

    @Test
    fun `only the requested sections are kept, in the page's order`() {
        assertEquals(
            "<h2 id=\"android-14\">14</h2><p>b</p><h2 id=\"android-13\">13</h2><p>c</p>",
            page.sections(listOf("android-13", "android-14")),
        )
    }

    @Test
    fun `no matching section gives nothing, so the whole page is shown`() {
        assertNull(page.sections(listOf("android-15")))
    }
}
