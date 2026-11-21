package ua.guitar.archive

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xxhdpi")
class ReorderTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun vm() = ViewModelProvider(rule.activity)[AppVm::class.java]

    private fun seed() {
        rule.runOnUiThread {
            vm().store.update {
                Library(
                    categories = listOf("Рок", "Блюз", "Джаз", "Поп").mapIndexed { i, n ->
                        Category("c$i", n, null, i.toLong())
                    }
                )
            }
        }
        rule.waitForIdle()
    }

    private fun names() = vm().store.current.categories.map { it.name }

    @Test
    fun longPressAndDragMovesCategory() {
        seed()
        assertEquals(listOf("Рок", "Блюз", "Джаз", "Поп"), names())
        val target = rule.onNodeWithText("Джаз").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithText("Рок").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            // Тягнемо вниз на рядок нижче (до «Джаз»).
            repeat(20) { moveBy(androidx.compose.ui.geometry.Offset(0f, (target.height + 40f) / 20f)) }
            up()
        }
        rule.waitForIdle()
        println("ORDER after drag: ${names()}")
        assertEquals("Рок must no longer be first", false, names().first() == "Рок")
        // Затискання не повинно відкривати категорію.
        assertEquals(Screen.Home, vm().stack.last())
    }

    @Test
    fun tapOpensCategory() {
        seed()
        rule.onNodeWithText("Блюз").performClick()
        rule.waitForIdle()
        assertEquals(Screen.CategoryScr("c1"), vm().stack.last())
    }
}
