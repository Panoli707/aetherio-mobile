package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class MediaLayoutTest {
    @Test fun phonePostersUseOneCompactWidthAndThreeColumnsWhenSpacePermits() {
        assertEquals(100.dp, posterWidth(360.dp))
        assertEquals(117.dp, posterWidth(411.dp))
        assertEquals(2, posterColumns(320.dp))
        assertEquals(136.dp, posterWidth(320.dp))
        listOf(320.dp, 360.dp, 411.dp, 600.dp, 800.dp, 1200.dp).forEach { width ->
            val columns = posterColumns(width)
            assertEquals(width.value, (posterWidth(width) * columns + 12.dp * (columns - 1) + 36.dp).value, .01f)
            assertTrue(posterWidth(width) in 96.dp..175.dp)
        }
    }

    @Test fun heroLeavesSpaceForContentAndRespectsSmallAndLandscapeWindows() {
        listOf(360.dp to 800.dp, 411.dp to 900.dp, 320.dp to 480.dp).forEach { (width, height) ->
            assertTrue(homeHeroHeight(width, height) <= height * .62f)
            assertTrue(homeHeroHeight(width, height) > 0.dp)
        }
        assertEquals(252.dp, homeHeroHeight(800.dp, 360.dp))
        assertEquals(400.dp, homeHeroHeight(900.dp, 1280.dp))
    }
}
