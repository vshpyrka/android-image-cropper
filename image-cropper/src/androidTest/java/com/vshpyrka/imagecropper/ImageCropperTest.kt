package com.vshpyrka.imagecropper

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.fillMaxSize
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset as ComposeOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageCropperTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun imageCropper_cropsCorrectly() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val imageBitmap = bitmap.asImageBitmap()
        var state: ImageCropperState? = null
        
        composeTestRule.setContent {
            val s = rememberImageCropperState(imageBitmap)
            state = s
            ImageCropper(
                imageBitmap = imageBitmap,
                state = s,
                modifier = Modifier.fillMaxSize().testTag("ImageCropper")
            )
        }

        composeTestRule.onNodeWithTag("ImageCropper").assertExists()

        // Access public crop() function
        val cropped = state!!.crop()
        
        // For a 100x100 image, 80% would be 80px, but the minimum is 100px
        // So the crop rect should be constrained to 100x100 (the full image)
        assertEquals(100, cropped.width)
        assertEquals(100, cropped.height)
    }

    @Test
    fun imageCropper_smallImageCropsToFullSize() {
        // Test with an image smaller than the minimum crop size (100px)
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val imageBitmap = bitmap.asImageBitmap()
        var state: ImageCropperState? = null
        
        composeTestRule.setContent {
            val s = rememberImageCropperState(imageBitmap)
            state = s
            ImageCropper(
                imageBitmap = imageBitmap,
                state = s,
                modifier = Modifier.fillMaxSize().testTag("ImageCropper")
            )
        }

        composeTestRule.onNodeWithTag("ImageCropper").assertExists()

        val cropped = state!!.crop()
        
        // For an 80x80 image, the minimum is 100px but the image size is the max
        // So the crop rect should be constrained to 80x80 (the full image)
        assertEquals(80, cropped.width)
        assertEquals(80, cropped.height)
    }

    @Test
    fun imageCropper_largeImageCropsAtRightEdge() {
        // Test with a large image and crop rect positioned at the right edge
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val imageBitmap = bitmap.asImageBitmap()
        var state: ImageCropperState? = null
        
        composeTestRule.setContent {
            val s = rememberImageCropperState(imageBitmap)
            state = s
            ImageCropper(
                imageBitmap = imageBitmap,
                state = s,
                modifier = Modifier.fillMaxSize().testTag("ImageCropper")
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ImageCropper").assertExists()

        // For 1000x1000 image, default crop is 800x800 (80%)
        // Drag the crop rectangle to the right by dragging the center (using CropRect)
        // We'll drag it 200 pixels to the right to position it at the right edge
        composeTestRule.onNodeWithTag("CropRect")
            .performTouchInput {
                swipe(
                    start = center,
                    end = center + ComposeOffset(200f, 0f),
                    durationMillis = 100
                )
            }

        composeTestRule.waitForIdle()

        val cropped = state!!.crop()
        
        // Verify the crop result is still 800x800 (size doesn't change when dragging center)
        assertEquals(800, cropped.width)
        assertEquals(800, cropped.height)
    }
}
