package com.vshpyrka.imagecropper

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/** The duration of the centering and fitting animations in milliseconds. */
private const val ANIMATION_DURATION_MS = 300

/**
 * Object to hold defaults used by [ImageCropper]
 */
@Stable
public object ImageCropperDefaults {

    /**
     * Default size for crop rect border width
     */
    public val BorderWidth: Dp = ImageCropperTokens.BorderWidth

    /**
     * Default size for crop rect guideline width
     */
    public val GuidelineWidth: Dp = ImageCropperTokens.GridStrokeWidth

    /**
     * Default size for crop handle radius
     */
    public val HandleRadius: Dp = ImageCropperTokens.HandleRadius

    /**
     * Default minimal crop side dimension
     */
    public const val BaseMinCropSize: Float = 100f

    /**
     * Creates a [ImageCropperColors] that represents the different colors used in parts of the [ImageCropper].
     */
    @Composable
    public fun colors(
        overlayColor: Color = Color.Unspecified,
        borderColor: Color = Color.Unspecified,
        handleColor: Color = Color.Unspecified,
        gridColor: Color = Color.Unspecified,
    ): ImageCropperColors = defaultColors.copy(
        overlayColor = overlayColor,
        borderColor = borderColor,
        handleColor = handleColor,
        gridColor = gridColor,
    )

    private val defaultColors: ImageCropperColors = ImageCropperColors(
        overlayColor = ImageCropperTokens.OverlayColor,
        borderColor = ImageCropperTokens.BorderColor,
        handleColor = ImageCropperTokens.HandleColor,
        gridColor = ImageCropperTokens.GridColor,
    )
}

/**
 * Represents the color used by a Crop.
 */
@Immutable
public class ImageCropperColors(
    public val overlayColor: Color,
    public val borderColor: Color,
    public val handleColor: Color,
    public val gridColor: Color,
) {
    public fun copy(
        overlayColor: Color = this.overlayColor,
        borderColor: Color = this.borderColor,
        handleColor: Color = this.handleColor,
        gridColor: Color = this.gridColor,
    ): ImageCropperColors = ImageCropperColors(
        overlayColor.takeOrElse { this.overlayColor },
        borderColor.takeOrElse { this.borderColor },
        handleColor.takeOrElse { this.handleColor },
        gridColor.takeOrElse { this.gridColor },
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageCropperColors

        if (overlayColor != other.overlayColor) return false
        if (borderColor != other.borderColor) return false
        if (handleColor != other.handleColor) return false
        if (gridColor != other.gridColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = overlayColor.hashCode()
        result = 31 * result + borderColor.hashCode()
        result = 31 * result + handleColor.hashCode()
        result = 31 * result + gridColor.hashCode()
        return result
    }
}

/**
 * A state object that can be hoisted to control and observe the image cropping state.
 *
 * @param imageBitmap The [ImageBitmap] that will be used for cropping calculations.
 */
@Stable
public class ImageCropperState(public val imageBitmap: ImageBitmap) {

    /** The size of the original image being cropped. */
    internal var imageSize: Size by mutableStateOf(
        Size(imageBitmap.width.toFloat(), imageBitmap.height.toFloat())
    )

    /** The current crop rectangle in image coordinates. */
    internal var cropRect: Rect by mutableStateOf(Rect.Zero)

    init {
        // Initialize crop rect to 80% of image size, centered, but enforce minimum size
        val imageWidth = imageBitmap.width.toFloat()
        val imageHeight = imageBitmap.height.toFloat()

        // Calculate desired crop size (80% of image)
        val desiredWidth = imageWidth * ImageCropperTokens.DefaultCropRectPercentage
        val desiredHeight = imageHeight * ImageCropperTokens.DefaultCropRectPercentage

        // Enforce minimum crop size (but don't exceed image dimensions)
        val minCropSize = ImageCropperDefaults.BaseMinCropSize // 100 pixels minimum
        val rectWidth = desiredWidth.coerceAtLeast(minCropSize)
            .coerceAtMost(imageWidth)
        val rectHeight = desiredHeight.coerceAtLeast(minCropSize)
            .coerceAtMost(imageHeight)

        cropRect = Rect(
            offset = Offset((imageWidth - rectWidth) / 2f, (imageHeight - rectHeight) / 2f),
            size = Size(rectWidth, rectHeight)
        )
    }

    // Animation states
    internal val scaleAnim = Animatable(1f)
    internal val offsetXAnim = Animatable(0f)
    internal val offsetYAnim = Animatable(0f)

    /** Whether the initial view (fitting image to screen) has been performed. */
    internal var initialized: Boolean by mutableStateOf(false)

    /** The size of the parent container in pixels. */
    internal var parentSize: Size by mutableStateOf(Size.Zero)

    /** The current handle being dragged, or null if no interaction is occurring. */
    private var draggingHandle by mutableStateOf<Handle?>(null)

    /** The initial touch position when a drag started. */
    private var dragStartOffset by mutableStateOf(Offset.Zero)

    /** The crop rectangle's value when a drag/resize operation started. */
    private var initialCropRect by mutableStateOf(Rect.Zero)

    /** Whether the user is currently interacting with the crop rectangle. */
    internal var isInteracting: Boolean by mutableStateOf(false)

    /**
     * Checks if a grid should be drawn.
     */
    internal val showGrid: Boolean
        get() = isInteracting || draggingHandle != null

    /**
     * Crops the original [imageBitmap] using the current [cropRect].
     *
     * @return The cropped region as a new [ImageBitmap].
     */
    public fun crop(): ImageBitmap {
        val androidBitmap = imageBitmap.asAndroidBitmap()
        val currentRect = cropRect
        val left = currentRect.left.toInt().coerceIn(0, androidBitmap.width - 1)
        val top = currentRect.top.toInt().coerceIn(0, androidBitmap.height - 1)
        val width = currentRect.width.toInt().coerceIn(1, androidBitmap.width - left)
        val height = currentRect.height.toInt().coerceIn(1, androidBitmap.height - top)

        return Bitmap.createBitmap(androidBitmap, left, top, width, height)
            .asImageBitmap()
    }

    /**
     * Resets the crop rectangle and viewport animations to their initial states.
     */
    public suspend fun reset() {
        if (parentSize == Size.Zero) return
        val imageWidth = imageBitmap.width.toFloat()
        val imageHeight = imageBitmap.height.toFloat()

        // Calculate desired crop size (80% of image)
        val desiredWidth = imageWidth * ImageCropperTokens.DefaultCropRectPercentage
        val desiredHeight = imageHeight * ImageCropperTokens.DefaultCropRectPercentage

        // Enforce minimum crop size (but don't exceed image dimensions)
        val minCropSize = ImageCropperDefaults.BaseMinCropSize // 100 pixels minimum
        val rectWidth = desiredWidth.coerceAtLeast(minCropSize)
            .coerceAtMost(imageWidth)
        val rectHeight = desiredHeight.coerceAtLeast(minCropSize)
            .coerceAtMost(imageHeight)

        val targetRect = Rect(
            offset = Offset((imageWidth - rectWidth) / 2f, (imageHeight - rectHeight) / 2f),
            size = Size(rectWidth, rectHeight)
        )

        coroutineScope {
            launch {
                animateCropRectTo(targetRect)
            }
            launch {
                fitImageToScreen(animate = true)
            }
        }
    }

    /**
     * Initializes the image viewport (scale and offset) if it hasn't been done yet
     * and the parent dimensions are available.
     */
    internal suspend fun initializeViewportIfNeeded() {
        if (!initialized && parentSize.width > 0f && parentSize.height > 0f) {
            fitImageToScreen()
            initialized = true
        }
    }

    /**
     * Fits the entire image within the current screen boundaries by updating
     * the scale and offset.
     *
     * @param animate Whether to animate the transition or snap immediately.
     */
    internal suspend fun fitImageToScreen(animate: Boolean = false) {
        if (parentSize == Size.Zero) return
        val screenWidth = parentSize.width
        val screenHeight = parentSize.height

        val scaleX = screenWidth / imageSize.width
        val scaleY = screenHeight / imageSize.height
        val scale = min(scaleX, scaleY)
        val scaledWidth = imageSize.width * scale
        val scaledHeight = imageSize.height * scale
        val offsetX = (screenWidth - scaledWidth) / 2
        val offsetY = (screenHeight - scaledHeight) / 2
        coroutineScope {
            if (animate) {
                launch {
                    scaleAnim.animateTo(
                        scale,
                        animationSpec = tween(ANIMATION_DURATION_MS)
                    )
                }
                launch {
                    offsetXAnim.animateTo(
                        offsetX,
                        animationSpec = tween(ANIMATION_DURATION_MS)
                    )
                }
                launch {
                    offsetYAnim.animateTo(
                        offsetY,
                        animationSpec = tween(ANIMATION_DURATION_MS)
                    )
                }
            } else {
                scaleAnim.snapTo(scale)
                offsetXAnim.snapTo(offsetX)
                offsetYAnim.snapTo(offsetY)
            }
        }
    }

    /**
     * Animates the crop rectangle to the given [targetRect].
     */
    private suspend fun animateCropRectTo(targetRect: Rect) {
        val startRect = cropRect
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(ANIMATION_DURATION_MS)
        ) { value, _ ->
            cropRect = Rect(
                left = startRect.left + (targetRect.left - startRect.left) * value,
                top = startRect.top + (targetRect.top - startRect.top) * value,
                right = startRect.right + (targetRect.right - startRect.right) * value,
                bottom = startRect.bottom + (targetRect.bottom - startRect.bottom) * value
            )
        }
    }

    /**
     * Centers the current crop rectangle on the screen, zooming in to make it
     * as large as possible with a small margin.
     */
    internal suspend fun centerCropRectOnScreen(marginPx: Float) {
        if (parentSize == Size.Zero) return
        val screenWidth = parentSize.width
        val screenHeight = parentSize.height

        val availableW = screenWidth - marginPx * 2
        val availableH = screenHeight - marginPx * 2
        val scaleX = availableW / cropRect.width
        val scaleY = availableH / cropRect.height
        val scale = min(scaleX, scaleY)
        val newW = cropRect.width * scale
        val newH = cropRect.height * scale
        val newX = (screenWidth - newW) / 2 - cropRect.left * scale
        val newY = (screenHeight - newH) / 2 - cropRect.top * scale
        coroutineScope {
            launch {
                scaleAnim.animateTo(
                    scale,
                    animationSpec = tween(ANIMATION_DURATION_MS)
                )
            }
            launch {
                offsetXAnim.animateTo(
                    newX,
                    animationSpec = tween(ANIMATION_DURATION_MS)
                )
            }
            launch {
                offsetYAnim.animateTo(
                    newY,
                    animationSpec = tween(ANIMATION_DURATION_MS)
                )
            }
        }
    }

    /**
     * Handles the start of a touch interaction.
     */
    internal fun onInteractionStart(position: Offset, minTouchSize: Float) {
        val screenCropRect =
            cropRect.toScreen(scaleAnim.value, Offset(offsetXAnim.value, offsetYAnim.value))
        if (getHitHandle(
                touchPosition = position,
                cropRect = screenCropRect,
                touchRadius = minTouchSize,
            ) != null || screenCropRect.contains(position)
        ) {
            isInteracting = true
        }
    }

    /**
     * Handles the end of a touch interaction.
     */
    internal fun onInteractionEnd() {
        isInteracting = false
    }

    /**
     * Handles the start of a drag or resize operation.
     */
    internal fun onDragStart(position: Offset, minTouchSize: Float) {
        val screenCropRect =
            cropRect.toScreen(scaleAnim.value, Offset(offsetXAnim.value, offsetYAnim.value))
        val handleHit = getHitHandle(
            touchPosition = position,
            cropRect = screenCropRect,
            touchRadius = minTouchSize,
        )
        if (handleHit != null) {
            draggingHandle = handleHit
            dragStartOffset = position
            initialCropRect = cropRect
        } else if (screenCropRect.contains(position)) {
            draggingHandle = Handle.Center
            dragStartOffset = position
            initialCropRect = cropRect
        } else {
            draggingHandle = null
        }
    }

    /**
     * Determines which handle (if any) is located at the given touch position.
     *
     * @param touchPosition The touch position in screen coordinates.
     * @param cropRect The crop rectangle in screen coordinates.
     * @param touchRadius The radius around each handle to consider a hit.
     * @return The [Handle] hit, or null if none.
     */
    private fun getHitHandle(touchPosition: Offset, cropRect: Rect, touchRadius: Float): Handle? {
        fun hit(target: Offset) = (target - touchPosition).getDistance() <= touchRadius

        if (hit(cropRect.topLeft)) return Handle.TopLeft
        if (hit(cropRect.topRight)) return Handle.TopRight
        if (hit(cropRect.bottomLeft)) return Handle.BottomLeft
        if (hit(cropRect.bottomRight)) return Handle.BottomRight

        if (hit(cropRect.topCenter)) return Handle.Top
        if (hit(cropRect.bottomCenter)) return Handle.Bottom
        if (hit(cropRect.centerLeft)) return Handle.Left
        if (hit(cropRect.centerRight)) return Handle.Right

        return null
    }

    /**
     * Extension function to map a [Rect] from image coordinates to screen coordinates.
     *
     * @param scale The current zoom scale.
     * @param offset The current panning offset.
     * @return A [Rect] in screen coordinates.
     */
    internal fun Rect.toScreen(scale: Float, offset: Offset): Rect {
        return Rect(
            left = left * scale + offset.x,
            top = top * scale + offset.y,
            right = right * scale + offset.x,
            bottom = bottom * scale + offset.y
        )
    }

    /**
     * Handles the drag or resize operation.
     *
     * @param changePosition The current touch position on screen.
     * @param baseMinCropPx The base minimum crop size in pixels.
     */
    internal suspend fun onDrag(
        changePosition: Offset,
        baseMinCropPx: Float,
    ) {
        val handle = draggingHandle ?: return
        val scale = scaleAnim.value
        val totalDeltaImage = (changePosition - dragStartOffset) / scale
        val rectBeforeDrag = initialCropRect

        // Minimum crop size should not exceed image dimensions
        val minWidth = min(imageSize.width, baseMinCropPx)
        val minHeight = min(imageSize.height, baseMinCropPx)

        when (handle) {
            Handle.Center -> {
                val newLeft = (rectBeforeDrag.left + totalDeltaImage.x)
                    .coerceIn(0f, imageSize.width - rectBeforeDrag.width)
                val newTop = (rectBeforeDrag.top + totalDeltaImage.y)
                    .coerceIn(0f, imageSize.height - rectBeforeDrag.height)
                cropRect = Rect(Offset(newLeft, newTop), rectBeforeDrag.size)
            }

            else -> {
                var left = rectBeforeDrag.left
                var top = rectBeforeDrag.top
                var right = rectBeforeDrag.right
                var bottom = rectBeforeDrag.bottom
                if (handle.isLeft) left += totalDeltaImage.x
                if (handle.isRight) right += totalDeltaImage.x
                if (handle.isTop) top += totalDeltaImage.y
                if (handle.isBottom) bottom += totalDeltaImage.y

                if (right - left < minWidth) {
                    if (handle.isLeft) left = right - minWidth
                    else right = left + minWidth
                }
                if (bottom - top < minHeight) {
                    if (handle.isTop) top = bottom - minHeight
                    else bottom = top + minHeight
                }

                left = left.coerceAtLeast(0f)
                top = top.coerceAtLeast(0f)
                right = right.coerceAtMost(imageSize.width)
                bottom = bottom.coerceAtMost(imageSize.height)
                cropRect = Rect(left, top, right, bottom)
            }
        }
        applyAutoPanning(scale)
    }

    /**
     * Shifts the viewport if the crop rectangle moves outside the screen bounds,
     * ensuring the crop rectangle remains visible while respecting image boundaries.
     *
     * @param scale The current zoom scale.
     */
    private suspend fun applyAutoPanning(scale: Float) {
        if (parentSize == Size.Zero) return

        val currentOffset = Offset(offsetXAnim.value, offsetYAnim.value)
        val screenCropRect = cropRect.toScreen(scale, currentOffset)

        var panX = 0f
        var panY = 0f

        // Pan left if rect exceeds screen right edge
        if (screenCropRect.right > parentSize.width) {
            panX = parentSize.width - screenCropRect.right
            // Don't pan past image right edge
            val imageScreenRight = currentOffset.x + (imageSize.width * scale)
            panX = panX.coerceAtLeast(parentSize.width - imageScreenRight)
        }
        // Pan right if rect exceeds screen left edge
        else if (screenCropRect.left < 0f) {
            panX = -screenCropRect.left
            // Don't pan past image left edge
            panX = panX.coerceAtMost(-currentOffset.x)
        }

        // Pan up if rect exceeds screen bottom edge
        if (screenCropRect.bottom > parentSize.height) {
            panY = parentSize.height - screenCropRect.bottom
            // Don't pan past image bottom edge
            val imageScreenBottom = currentOffset.y + (imageSize.height * scale)
            panY = panY.coerceAtLeast(parentSize.height - imageScreenBottom)
        }
        // Pan down if rect exceeds screen top edge
        else if (screenCropRect.top < 0f) {
            panY = -screenCropRect.top
            // Don't pan past image top edge
            panY = panY.coerceAtMost(-currentOffset.y)
        }

        if (panX != 0f || panY != 0f) {
            // Adjust dragStartOffset so that future onDrag calls maintain the same image coordinates
            // while the viewport moves under the touch point.
            dragStartOffset += Offset(panX, panY)
            coroutineScope {
                offsetXAnim.snapTo(offsetXAnim.value + panX)
                offsetYAnim.snapTo(offsetYAnim.value + panY)
            }
        }
    }

    /**
     * Handles the end or cancellation of a drag operation.
     */
    internal suspend fun onDragFinished(marginPx: Float) {
        draggingHandle = null
        centerCropRectOnScreen(marginPx)
    }
}

/**
 * Creates and remembers an [ImageCropperState] for the given [imageBitmap].
 *
 * @param imageBitmap The [ImageBitmap] to initialize the state with.
 * @return A remembered [ImageCropperState].
 */
@Composable
public fun rememberImageCropperState(imageBitmap: ImageBitmap): ImageCropperState {
    return remember(imageBitmap) { ImageCropperState(imageBitmap) }
}

/**
 * A composable function that displays an image and allows the user to crop it.
 *
 * This composable provides an interactive UI for cropping a bitmap. It displays the image
 * and updates the view to keep the crop rectangle visible and centered when possible.
 *
 * @param imageBitmap The [ImageBitmap] to be cropped.
 * @param modifier The [Modifier] to be applied to the layout.
 * @param state The [ImageCropperState] to control and observe the cropping process.
 */
@Composable
public fun ImageCropper(
    imageBitmap: ImageBitmap,
    modifier: Modifier = Modifier,
    state: ImageCropperState = rememberImageCropperState(imageBitmap),
    colors: ImageCropperColors = ImageCropperDefaults.colors(),
) {
    val density = LocalDensity.current
    val minTouchSizePx = with(density) { ImageCropperTokens.MinTouchTargetSize.toPx() }
    val centerMarginPx = with(density) { ImageCropperTokens.CenterMargin.toPx() }
    val baseMinCropSizePx = ImageCropperDefaults.BaseMinCropSize

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                state.parentSize = Size(it.width.toFloat(), it.height.toFloat())
            }
    ) {
        if (state.parentSize != Size.Zero) {
            val scope = rememberCoroutineScope()

            LaunchedEffect(imageBitmap, state.parentSize) {
                state.initializeViewportIfNeeded()
            }

            val scale = state.scaleAnim.value
            val offset = Offset(state.offsetXAnim.value, state.offsetYAnim.value)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            state.onInteractionStart(down.position, minTouchSizePx)
                            waitForUpOrCancellation()
                            state.onInteractionEnd()
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                state.onDragStart(pos, minTouchSizePx)
                            },
                            onDragEnd = {
                                scope.launch {
                                    state.onDragFinished(centerMarginPx)
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    state.onDragFinished(centerMarginPx)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                scope.launch {
                                    state.onDrag(
                                        changePosition = change.position,
                                        baseMinCropPx = baseMinCropSizePx,
                                    )
                                }
                            }
                        )
                    }
            ) {
                val screenCropRect = with(state) { state.cropRect.toScreen(scale, offset) }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    withTransform(
                        transformBlock = {
                            translate(offset.x, offset.y)
                            scale(scale, scale, pivot = Offset.Zero)
                        },
                        drawBlock = {
                            drawImage(state.imageBitmap)
                        }
                    )

                    // Overlay
                    drawOverlay(screenCropRect, colors.overlayColor)

                    // Border
                    drawBorder(screenCropRect, colors.borderColor)

                    // Grid
                    if (state.showGrid) {
                        drawGrid(screenCropRect, colors.gridColor)
                    }

                    // Handles
                    drawHandles(screenCropRect, colors.handleColor)
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
    Box(
        modifier = Modifier
            .size(ImageCropperTokens.HandleInteractionSize)
            .offset {
                IntOffset(
                    (position.x - (ImageCropperTokens.HandleInteractionSize / 2).toPx()).roundToInt(),
                    (position.y - (ImageCropperTokens.HandleInteractionSize / 2).toPx()).roundToInt()
                )
            }
            .then(
                if (drawDebug) Modifier.background(Color.Yellow) else Modifier
            )
            .testTag("Handle${handle.name}")
    )
}

/**
 * Draws the dark overlay around the crop rectangle.
 */
private fun DrawScope.drawOverlay(rect: Rect, color: Color) {
    drawRect(
        color = color,
        topLeft = Offset.Zero,
        size = Size(size.width, rect.top)
    )
    drawRect(
        color = color,
        topLeft = Offset(0f, rect.bottom),
        size = Size(size.width, size.height - rect.bottom)
    )
    drawRect(
        color = color,
        topLeft = Offset(0f, rect.top),
        size = Size(rect.left, rect.height)
    )
    drawRect(
        color = color,
        topLeft = Offset(rect.right, rect.top),
        size = Size(size.width - rect.right, rect.height)
    )
}

/**
 * Draws the border of the crop rectangle.
 */
private fun DrawScope.drawBorder(rect: Rect, color: Color) {
    drawRect(
        color = color,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = ImageCropperDefaults.BorderWidth.toPx())
    )
}

/**
 * Draws the 3x3 grid inside the crop rectangle.
 */
private fun DrawScope.drawGrid(rect: Rect, color: Color) {
    val gridStrokeWidthPx = ImageCropperDefaults.GuidelineWidth.toPx()
    for (i in 1..2) {
        val gridX = rect.left + rect.width * i / 3f
        drawLine(
            color = color,
            start = Offset(gridX, rect.top),
            end = Offset(gridX, rect.bottom),
            strokeWidth = gridStrokeWidthPx
        )
        val gridY = rect.top + rect.height * i / 3f
        drawLine(
            color = color,
            start = Offset(rect.left, gridY),
            end = Offset(rect.right, gridY),
            strokeWidth = gridStrokeWidthPx
        )
    }
}

/**
 * Draws all corner and side handles for the crop rectangle.
 */
private fun DrawScope.drawHandles(rect: Rect, color: Color) {
    val handleRadiusPx = ImageCropperDefaults.HandleRadius.toPx()
    drawHandle(rect.topLeft, handleRadiusPx, color)
    drawHandle(rect.topRight, handleRadiusPx, color)
    drawHandle(rect.bottomLeft, handleRadiusPx, color)
    drawHandle(rect.bottomRight, handleRadiusPx, color)
    drawHandle(rect.topCenter, handleRadiusPx, color)
    drawHandle(rect.bottomCenter, handleRadiusPx, color)
    drawHandle(rect.centerLeft, handleRadiusPx, color)
    drawHandle(rect.centerRight, handleRadiusPx, color)
}

/**
 * Helper function to draw a circular handle on the canvas.
 *
 * @param center The center point of the handle.
 * @param radius The radius of the handle circle.
 */
private fun DrawScope.drawHandle(
    center: Offset,
    radius: Float,
    color: Color,
) {
    drawCircle(
        color = color,
        radius = radius,
        center = center
    )
}
