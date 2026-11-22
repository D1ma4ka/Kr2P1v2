package ru.krapiva.app.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.krapiva.app.designsystem.KraPivaTheme
import ru.krapiva.app.designsystem.ThemeMode

class MenuComparisonLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fourPackagesAndActionsRemainReachableInBothThemesAtLargeFont() {
        val theme = mutableStateOf(ThemeMode.LIGHT)
        val rows = mutableStateOf((1..4).map { index ->
            MenuPosition("p$index", "section", "Пиво", "brewery", "Пивоварня", null, null,
                null, null, null, null, if (index == 1) "can" else "bottle", null,
                null, null, 330 + index, true, false)
        })
        var checked: String? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                KraPivaTheme(theme.value) {
                    Box(Modifier.width(320.dp)) {
                        MenuComparisonScreen("Бар", rows.value, cached = false, old = false, back = {},
                            remove = { id -> rows.value = rows.value.filterNot { it.positionId == id } },
                            clear = { rows.value = emptyList() }, open = {}, checkin = { checked = it.positionId })
                    }
                }
            }
        }
        compose.onNodeWithText("Сравнение · Бар").assertIsDisplayed()
        compose.onAllNodesWithText("Чекин").onFirst().performClick()
        assertEquals("p1", checked)
        compose.runOnIdle { theme.value = ThemeMode.DARK }
        compose.onAllNodesWithText("Убрать").onFirst().performClick()
        compose.runOnIdle { assertEquals(3, rows.value.size) }
        compose.onNodeWithText("К меню").assertHasClickAction()
    }
}
