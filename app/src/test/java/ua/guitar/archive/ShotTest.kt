package ua.guitar.archive

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ShotTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun shot() {
        rule.runOnUiThread {
            ViewModelProvider(rule.activity)[AppVm::class.java].store.update {
                Library(categories = listOf("Рок", "Блюз", "Джаз", "Поп", "Акустика").mapIndexed { i, n -> Category("c$i", n, null, i.toLong()) })
            }
        }
        rule.waitForIdle()
        rule.runOnUiThread { ViewModelProvider(rule.activity)[AppVm::class.java].dismissUpdate() }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Котик").performClick()
        val out = System.getProperty("shotDir") ?: "/tmp"
        for (t in listOf(250L, 500L)) {
            rule.mainClock.advanceTimeBy(250)
            rule.runOnUiThread {
                val v = rule.activity.window.decorView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                File(out, "shot_$t.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
