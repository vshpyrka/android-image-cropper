package com.vshpyrka.imagecropper

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import androidx.core.graphics.withSave

/**
 * A state object that can be hoisted to control and observe the image cropping state.
 *
 * @param bitmap The [Bitmap] that will be used for cropping calculations.
 */
@Stable
class ImageCropperState(
    bitmap: Bitmap
) {
    /**
     * The size of the original image being cropped.
     */
    var imageSize by mutableStateOf(
        Size(bitmap.width.toFloat(), bitmap.height.toFloat())
    )
        internal set

    /**
     * The current crop rectangle in image coordinates.
     */
    var cropRect by mutableStateOf(
        Rect(
            offset = Offset(bitmap.width * 0.1f, bitmap.height * 0.1f),
            size = Size(bitmap.width * 0.8f, bitmap.height * 0.8f)
        )
    )
        internal set

    /**
     * Crops the original bitmap using the current [cropRect].
     *
     * @param bitmap The original bitmap being cropped.
     * @return The cropped [Bitmap].
     */
    fun crop(bitmap: Bitmap): Bitmap {
        val r = cropRect
        val left = r.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = r.top.toInt().coerceIn(0, bitmap.height - 1)
        val width = r.width.toInt().coerceIn(1, bitmap.width - left)
        val height = r.height.toInt().coerceIn(1, bitmap.height - top)

        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }
}

/**
 * Creates and remembers an [ImageCropperState] for the given [bitmap].
 *
 * @param bitmap The [Bitmap] to initialize the state with.
 * @return A remembered [ImageCropperState].
 */
@Composable
fun rememberImageCropperState(bitmap: Bitmap): ImageCropperState {
    return remember(bitmap) { ImageCropperState(bitmap) }
}

/**
 * A composable function that displays an image and allows the user to crop it.
 *
 * This composable provides an interactive UI for cropping a bitmap. It displays the image
 * and updates the view to keep the crop rectangle visible and centered when possible.
 *
 * @param bitmap The [Bitmap] to be cropped.
 * @param state The [ImageCropperState] to control and observe the cropping process.
 * @param modifier The [Modifier] to be applied to the layout.
 */
@Composable
fun ImageCropper(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    state: ImageCropperState = rememberImageCropperState(bitmap),
) {
    val density = LocalDensity.current
    val minTouchSize = with(density) { 48.dp.toPx() }
    val minCropSize = with(density) { 100.dp.toPx() }

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

            val scaleAnim = remember { Animatable(1f) }
            val offsetXAnim = remember { Animatable(0f) }
            val offsetYAnim = remember { Animatable(0f) }
            var initialized by remember(bitmap) { mutableStateOf(false) }

            val scope = rememberCoroutineScope()

            /**
             * Fits the entire image within the current screen boundaries by updating
             * the scale and offset.
             */
            fun fitImageToScreen() {
                val scaleX = screenWidth / state.imageSize.width
                val scaleY = screenHeight / state.imageSize.height
                val scale = min(scaleX, scaleY)
                val w = state.imageSize.width * scale
                val h = state.imageSize.height * scale
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
                val scaleX = availableW / state.cropRect.width
                val scaleY = availableH / state.cropRect.height
                val scale = min(scaleX, scaleY)
                val newW = state.cropRect.width * scale
                val newH = state.cropRect.height * scale
                val newX = (screenWidth - newW) / 2 - state.cropRect.left * scale
                val newY = (screenHeight - newH) / 2 - state.cropRect.top * scale
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

            var draggingHandle by remember { mutableStateOf<Handle?>(null) }
            var dragStartOffset by remember { mutableStateOf(Offset.Zero) }
            var initialCropRect by remember { mutableStateOf(Rect.Zero) }
            var isInteracting by remember { mutableStateOf(false) }

            val scale = scaleAnim.value
            val offset = Offset(offsetXAnim.value, offsetYAnim.value)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val currentScale = scaleAnim.value
                            val currentOffset = Offset(offsetXAnim.value, offsetYAnim.value)
                            val sCropRect = state.cropRect.toScreen(currentScale, currentOffset)

                            if (getHitHandle(down.position, sCropRect, minTouchSize) != null ||
                                sCropRect.contains(down.position)
                            ) {
                                isInteracting = true
                            }
                            waitForUpOrCancellation()
                            isInteracting = false
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                val currentScale = scaleAnim.value
                                val currentOffset = Offset(offsetXAnim.value, offsetYAnim.value)
                                val sCropRect = state.cropRect.toScreen(currentScale, currentOffset)
                                val handleHit = getHitHandle(pos, sCropRect, minTouchSize)
                                if (handleHit != null) {
                                    draggingHandle = handleHit
                                    dragStartOffset = pos
                                    initialCropRect = state.cropRect
                                } else if (sCropRect.contains(pos)) {
                                    draggingHandle = Handle.Center
                                    dragStartOffset = pos
                                    initialCropRect = state.cropRect
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
                            onDrag = { change, _ ->
                                change.consume()
                                val handle = draggingHandle ?: return@detectDragGestures
                                val currentScale = scaleAnim.value
                                val totalDrag = change.position - dragStartOffset
                                val totalDeltaImage = totalDrag / currentScale
                                val r = initialCropRect

                                when (handle) {
                                    Handle.Center -> {
                                        val newLeft = (r.left + totalDeltaImage.x)
                                            .coerceIn(0f, state.imageSize.width - r.width)
                                        val newTop = (r.top + totalDeltaImage.y)
                                            .coerceIn(0f, state.imageSize.height - r.height)
                                        state.cropRect = Rect(Offset(newLeft, newTop), r.size)
                                    }

                                    else -> {
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
                                        right = right.coerceAtMost(state.imageSize.width)
                                        bottom = bottom.coerceAtMost(state.imageSize.height)
                                        state.cropRect = Rect(left, top, right, bottom)
                                    }
                                }
                            }
                        )
                    }
            ) {
                val screenCropRect = state.cropRect.toScreen(scale, offset)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    with(drawContext.canvas.nativeCanvas) {
                        withSave {
                            translate(offset.x, offset.y)
                            scale(scale, scale)
                            drawBitmap(bitmap, 0f, 0f, null)
                        }
                    }

                    // Overlay
                    val overlayColor = Color.Black.copy(alpha = 0.5f)
                    drawRect(
                        color = overlayColor,
                        topLeft = Offset.Zero,
                        size = Size(size.width, screenCropRect.top)
                    )
                    drawRect(
                        color = overlayColor,
                        topLeft = Offset(0f, screenCropRect.bottom),
                        size = Size(size.width, size.height - screenCropRect.bottom)
                    )
                    drawRect(
                        color = overlayColor,
                        topLeft = Offset(0f, screenCropRect.top),
                        size = Size(screenCropRect.left, screenCropRect.height)
                    )
                    drawRect(
                        color = overlayColor,
                        topLeft = Offset(screenCropRect.right, screenCropRect.top),
                        size = Size(size.width - screenCropRect.right, screenCropRect.height)
                    )

                    // Border
                    drawRect(
                        color = Color.White,
                        topLeft = screenCropRect.topLeft,
                        size = screenCropRect.size,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Grid
                    if (isInteracting || draggingHandle != null) {
                        val gridStrokeWidth = 1.dp.toPx()
                        val gridColor = Color.White.copy(alpha = 0.7f)
                        for (i in 1..2) {
                            val x = screenCropRect.left + screenCropRect.width * i / 3f
                            drawLine(
                                color = gridColor,
                                start = Offset(x, screenCropRect.top),
                                end = Offset(x, screenCropRect.bottom),
                                strokeWidth = gridStrokeWidth
                            )
                            val y = screenCropRect.top + screenCropRect.height * i / 3f
                            drawLine(
                                color = gridColor,
                                start = Offset(screenCropRect.left, y),
                                end = Offset(screenCropRect.right, y),
                                strokeWidth = gridStrokeWidth
                            )
                        }
                    }

                    // Handles
                    val handleHalf = 10.dp.toPx()
                    drawHandle(screenCropRect.topLeft, handleHalf)
                    drawHandle(screenCropRect.topRight, handleHalf)
                    drawHandle(screenCropRect.bottomLeft, handleHalf)
                    drawHandle(screenCropRect.bottomRight, handleHalf)
                    drawHandle(screenCropRect.topCenter, handleHalf)
                    drawHandle(screenCropRect.bottomCenter, handleHalf)
                    drawHandle(screenCropRect.centerLeft, handleHalf)
                    drawHandle(screenCropRect.centerRight, handleHalf)
                }

                HandleBox(Handle.TopLeft, screenCropRect.topLeft)
                HandleBox(Handle.TopRight, screenCropRect.topRight)
                HandleBox(Handle.BottomLeft, screenCropRect.bottomLeft)
                HandleBox(Handle.BottomRight, screenCropRect.bottomRight)
                HandleBox(Handle.Top, screenCropRect.topCenter)
                HandleBox(Handle.Bottom, screenCropRect.bottomCenter)
                HandleBox(Handle.Left, screenCropRect.centerLeft)
                HandleBox(Handle.Right, screenCropRect.centerRight)

                CroppingRect(screenCropRect)
            }
        }
    }
}

/**
 * An invisible box positioned over the entire crop rectangle to capture touch events
 * for dragging and provide a target for automated tests.
 *
 * @param screenCropRect The current crop rectangle in screen coordinates.
 */
@Composable
private fun CroppingRect(screenCropRect: Rect) {
    val density = LocalDensity.current
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

/**
 * An invisible box positioned over a crop handle to capture touch events
 * and provide a target for automated tests.
 *
 * @param handle The [Handle] this box represents.
 * @param position The position of the handle in screen coordinates.
 * @param drawDebug Whether to draw a yellow background for the handle box for debugging.
 */
@Composable
private fun HandleBox(
    handle: Handle,
    position: Offset,
    drawDebug: Boolean = false,
) {
    val testHandleSize = 48.dp
    val testHandleHalf = testHandleSize / 2
    Box(
        modifier = Modifier
            .size(testHandleSize)
            .offset {
                IntOffset(
                    (position.x - testHandleHalf.toPx()).roundToInt(),
                    (position.y - testHandleHalf.toPx()).roundToInt()
                )
            }
            .then(
                if (drawDebug) Modifier.background(Color.Yellow) else Modifier
            )
            .testTag("Handle${handle.name}")
    )
}

/**
 * Helper function to draw a circular handle on the canvas.
 *
 * @param center The center point of the handle.
 * @param radius The radius of the handle circle.
 */
private fun DrawScope.drawHandle(
    center: Offset,
    radius: Float
) {
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
    /** Top-left corner handle. */
    TopLeft,
    /** Top-right corner handle. */
    TopRight,
    /** Bottom-left corner handle. */
    BottomLeft,
    /** Bottom-right corner handle. */
    BottomRight,
    /** Top-side handle. */
    Top,
    /** Bottom-side handle. */
    Bottom,
    /** Left-side handle. */
    Left,
    /** Right-side handle. */
    Right,
    /** Center handle for dragging the entire rectangle. */
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
