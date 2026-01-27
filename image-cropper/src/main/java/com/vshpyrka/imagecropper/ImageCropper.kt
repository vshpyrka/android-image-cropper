package com.vshpyrka.imagecropper

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A composable function that displays an image and allows the user to crop it.
 *
 * This composable provides an interactive UI for cropping a bitmap. It displays the image
 * and updates the view to keep the crop rectangle visible and centered when possible.
 *
 * @param bitmap The [Bitmap] to be cropped.
 * @param modifier The [Modifier] to be applied to the layout.
 */
@Composable
fun ImageCropper(
    bitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val minTouchSize = with(density) { 48.dp.toPx() } // Increased for better touch usability
    val minCropSize = with(density) { 100.dp.toPx() } // "100 points" interpreted as dp

    var parentSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                parentSize = Size(it.width.toFloat(), it.height.toFloat())
            }
    ) {
        if (parentSize != Size.Zero) {
            val screenWidth = parentSize.width
            val screenHeight = parentSize.height

            // State
            var imageSize by remember(bitmap) {
                mutableStateOf(Size(bitmap.width.toFloat(), bitmap.height.toFloat()))
            }

            // View Transform (Screen = Image * Scale + Offset)
            // We use Animatable for smooth transitions
            val scaleAnim = remember { Animatable(1f) }
            val offsetXAnim = remember { Animatable(0f) }
            val offsetYAnim = remember { Animatable(0f) }

            // Additional state to track if we initialized the view to center the image
            var initialized by remember(bitmap) { mutableStateOf(false) }

            // Crop Rect in Image Coordinates
            var cropRect by remember(bitmap) {
                mutableStateOf(
                    Rect(
                        offset = Offset(bitmap.width * 0.1f, bitmap.height * 0.1f),
                        size = Size(bitmap.width * 0.8f, bitmap.height * 0.8f)
                    )
                )
            }

            val scope = rememberCoroutineScope()

            // logic to fit image on screen or crop rect on screen
            /**
             * Fits the entire image within the current screen boundaries.
             */
            fun fitImageToScreen() {
                val scaleX = screenWidth / imageSize.width
                val scaleY = screenHeight / imageSize.height
                val scale = min(scaleX, scaleY)

                val w = imageSize.width * scale
                val h = imageSize.height * scale
                val x = (screenWidth - w) / 2
                val y = (screenHeight - h) / 2

                scope.launch {
                    scaleAnim.snapTo(scale)
                    offsetXAnim.snapTo(x)
                    offsetYAnim.snapTo(y)
                }
            }

            /**
             * Centers the current crop rectangle on the screen, zooming in to make it
             * as large as possible with a small margin.
             */
            fun centerCropRectOnScreen() {
                val margin = with(density) { 20.dp.toPx() }
                val availableW = screenWidth - margin * 2
                val availableH = screenHeight - margin * 2

                val scaleX = availableW / cropRect.width
                val scaleY = availableH / cropRect.height
                val scale = min(scaleX, scaleY) // Fit inside

                val newW = cropRect.width * scale
                val newH = cropRect.height * scale
                val newX = (screenWidth - newW) / 2 - cropRect.left * scale
                val newY = (screenHeight - newH) / 2 - cropRect.top * scale

                scope.launch {
                    launch { scaleAnim.animateTo(scale, animationSpec = tween(300)) }
                    launch { offsetXAnim.animateTo(newX, animationSpec = tween(300)) }
                    launch { offsetYAnim.animateTo(newY, animationSpec = tween(300)) }
                }
            }

            LaunchedEffect(bitmap, screenWidth, screenHeight) {
                if (!initialized && screenWidth > 0 && screenHeight > 0) {
                    fitImageToScreen()
                    initialized = true
                }
            }

            // Interaction logic
            // We need to track which handle is being dragged
            var draggingHandle by remember { mutableStateOf<Handle?>(null) } // null, or Handle enum
            var dragStartOffset by remember { mutableStateOf(Offset.Zero) }
            var initialCropRect by remember { mutableStateOf(Rect.Zero) }

            val scale = scaleAnim.value
            val offset = Offset(offsetXAnim.value, offsetYAnim.value)

            // Calculate screen Rect
            val screenCropRect = cropRect.toScreen(scale, offset)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val currentScale = scaleAnim.value
                                val currentOffset = Offset(offsetXAnim.value, offsetYAnim.value)
                                val sCropRect = cropRect.toScreen(currentScale, currentOffset)

                                // Hit test handles
                                val handleHit = getHitHandle(offset, sCropRect, minTouchSize)
                                if (handleHit != null) {
                                    draggingHandle = handleHit
                                    dragStartOffset = offset
                                    initialCropRect = cropRect
                                } else if (sCropRect.contains(offset)) {
                                    draggingHandle = Handle.Center
                                    dragStartOffset = offset
                                    initialCropRect = cropRect
                                } else {
                                    draggingHandle = null
                                }
                            },
                            onDragEnd = {
                                draggingHandle = null
                                centerCropRectOnScreen()
                            },
                            onDragCancel = {
                                draggingHandle = null
                                centerCropRectOnScreen()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val handle = draggingHandle ?: return@detectDragGestures
                                val currentScale = scaleAnim.value

                                val totalDrag = change.position - dragStartOffset
                                val totalDeltaImage = totalDrag / currentScale

                                val r = initialCropRect

                                when (handle) {
                                    Handle.Center -> {
                                        val newLeft = (r.left + totalDeltaImage.x)
                                            .coerceIn(0f, imageSize.width - r.width)
                                        val newTop = (r.top + totalDeltaImage.y)
                                            .coerceIn(0f, imageSize.height - r.height)
                                        cropRect = Rect(Offset(newLeft, newTop), r.size)
                                    }
                                    else -> {
                                        // Resizing
                                        var left = r.left
                                        var top = r.top
                                        var right = r.right
                                        var bottom = r.bottom

                                        if (handle.isLeft) left += totalDeltaImage.x
                                        if (handle.isRight) right += totalDeltaImage.x
                                        if (handle.isTop) top += totalDeltaImage.y
                                        if (handle.isBottom) bottom += totalDeltaImage.y

                                        if (right - left < minCropSize) {
                                            if (handle.isLeft) left = right - minCropSize
                                            else right = left + minCropSize
                                        }
                                        if (bottom - top < minCropSize) {
                                            if (handle.isTop) top = bottom - minCropSize
                                            else bottom = top + minCropSize
                                        }

                                        left = left.coerceAtLeast(0f)
                                        top = top.coerceAtLeast(0f)
                                        right = right.coerceAtMost(imageSize.width)
                                        bottom = bottom.coerceAtMost(imageSize.height)

                                        cropRect = Rect(left, top, right, bottom)
                                    }
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw Image
                    with(drawContext.canvas.nativeCanvas) {
                        save()
                        translate(offset.x, offset.y)
                        scale(scale, scale)
                        drawBitmap(bitmap, 0f, 0f, null)
                        restore()
                    }

                    // Draw Overlay
                    // Top
                    drawRect(
                        Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, screenCropRect.top)
                    )
                    // Bottom
                    drawRect(
                        Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(0f, screenCropRect.bottom),
                        size = Size(size.width, size.height - screenCropRect.bottom)
                    )
                    // Left (middle)
                    drawRect(
                        Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(0f, screenCropRect.top),
                        size = Size(screenCropRect.left, screenCropRect.height)
                    )
                    // Right (middle)
                    drawRect(
                        Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(screenCropRect.right, screenCropRect.top),
                        size = Size(size.width - screenCropRect.right, screenCropRect.height)
                    )

                    // Draw Border
                    drawRect(
                        color = Color.White,
                        topLeft = screenCropRect.topLeft,
                        size = screenCropRect.size,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Draw Handles Visuals
                    val handleSize = 20.dp.toPx()
                    val handleHalf = handleSize / 2

                    // Corners
                    drawHandle(screenCropRect.topLeft, handleHalf)
                    drawHandle(screenCropRect.topRight, handleHalf)
                    drawHandle(screenCropRect.bottomLeft, handleHalf)
                    drawHandle(screenCropRect.bottomRight, handleHalf)

                    // Sides
                    drawHandle(screenCropRect.topCenter, handleHalf)
                    drawHandle(screenCropRect.bottomCenter, handleHalf)
                    drawHandle(screenCropRect.centerLeft, handleHalf)
                    drawHandle(screenCropRect.centerRight, handleHalf)
                }

                // Invisible Helper Nodes for Testing
                val testHandleSize = 48.dp // Match touch size
                val testHandleHalf = testHandleSize / 2

                // Helper to place test tag box
                @Composable
                fun HandleBox(handle: Handle, position: Offset) {
                    Box(
                        modifier = Modifier
                            .size(testHandleSize)
                            .offset {
                                IntOffset(
                                    (position.x - testHandleHalf.toPx()).roundToInt(),
                                    (position.y - testHandleHalf.toPx()).roundToInt()
                                )
                            }
                            .testTag("Handle${handle.name}")
                    )
                }

                HandleBox(Handle.TopLeft, screenCropRect.topLeft)
                HandleBox(Handle.TopRight, screenCropRect.topRight)
                HandleBox(Handle.BottomLeft, screenCropRect.bottomLeft)
                HandleBox(Handle.BottomRight, screenCropRect.bottomRight)
                HandleBox(Handle.Top, screenCropRect.topCenter)
                HandleBox(Handle.Bottom, screenCropRect.bottomCenter)
                HandleBox(Handle.Left, screenCropRect.centerLeft)
                HandleBox(Handle.Right, screenCropRect.centerRight)

                // Crop Rect Center for dragging
                Box(
                    modifier = Modifier
                        .size(
                            width = with(density) { screenCropRect.width.toDp() },
                            height = with(density) { screenCropRect.height.toDp() }
                        )
                        .offset {
                            IntOffset(
                                screenCropRect.left.roundToInt(),
                                screenCropRect.top.roundToInt()
                            )
                        }
                        .testTag("CropRect")
                )
            }
        }
    }
}

/**
 * Helper function to draw a circular handle on the canvas.
 *
 * @param center The center point of the handle.
 * @param radius The radius of the handle circle.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHandle(center: Offset, radius: Float) {
    drawCircle(
        color = Color.White,
        radius = radius,
        center = center
    )
}

/**
 * Extension function to map a [Rect] from image coordinates to screen coordinates.
 *
 * @param scale The current zoom scale.
 * @param offset The current panning offset.
 * @return A [Rect] in screen coordinates.
 */
private fun Rect.toScreen(scale: Float, offset: Offset): Rect {
    return Rect(
        left = left * scale + offset.x,
        top = top * scale + offset.y,
        right = right * scale + offset.x,
        bottom = bottom * scale + offset.y
    )
}

/**
 * Represents the different parts of the crop rectangle that can be interacted with.
 */
private enum class Handle {
    TopLeft, TopRight, BottomLeft, BottomRight,
    Top, Bottom, Left, Right,
    Center;
    
    /** Returns true if this handle involves the left edge. */
    val isLeft get() = this == TopLeft || this == BottomLeft || this == Left
    /** Returns true if this handle involves the right edge. */
    val isRight get() = this == TopRight || this == BottomRight || this == Right
    /** Returns true if this handle involves the top edge. */
    val isTop get() = this == TopLeft || this == TopRight || this == Top
    /** Returns true if this handle involves the bottom edge. */
    val isBottom get() = this == BottomLeft || this == BottomRight || this == Bottom
}

/**
 * Determines which handle (if any) is located at the given touch position.
 *
 * @param pos The touch position in screen coordinates.
 * @param rect The crop rectangle in screen coordinates.
 * @param touchRadius The radius around each handle to consider a hit.
 * @return The [Handle] hit, or null if none.
 */
private fun getHitHandle(pos: Offset, rect: Rect, touchRadius: Float): Handle? {
    fun hit(target: Offset) = (target - pos).getDistance() <= touchRadius

    if (hit(rect.topLeft)) return Handle.TopLeft
    if (hit(rect.topRight)) return Handle.TopRight
    if (hit(rect.bottomLeft)) return Handle.BottomLeft
    if (hit(rect.bottomRight)) return Handle.BottomRight
    
    if (hit(rect.topCenter)) return Handle.Top
    if (hit(rect.bottomCenter)) return Handle.Bottom
    if (hit(rect.centerLeft)) return Handle.Left
    if (hit(rect.centerRight)) return Handle.Right
    
    return null
}
