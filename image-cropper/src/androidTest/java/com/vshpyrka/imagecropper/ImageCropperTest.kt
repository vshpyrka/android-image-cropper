package com.vshpyrka.imagecropper

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageCropperTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun imageCropper_rendersAndAcceptsInput() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        
        composeTestRule.setContent {
            ImageCropper(
                bitmap = bitmap,
                modifier = Modifier.testTag("ImageCropper")
            )
        }

        composeTestRule.onNodeWithTag("ImageCropper")
            .assertExists()
        
        // Simulate a drag logic
        composeTestRule.onNodeWithTag("ImageCropper")
            .performTouchInput {
                swipeRight()
            }
            
        // If no crash, pass. 
        // Real logic validation requires exposing state which is currently internal.
    }
}
