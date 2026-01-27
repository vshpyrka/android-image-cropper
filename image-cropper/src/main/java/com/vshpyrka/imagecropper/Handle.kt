package com.vshpyrka.imagecropper

/**
 * Represents the different parts of the crop rectangle that can be interacted with.
 */
internal enum class Handle {
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
    val isLeft: Boolean get() = this == TopLeft || this == BottomLeft || this == Left

    /** Returns true if this handle involves the right edge. */
    val isRight: Boolean get() = this == TopRight || this == BottomRight || this == Right

    /** Returns true if this handle involves the top edge. */
    val isTop: Boolean get() = this == TopLeft || this == TopRight || this == Top

    /** Returns true if this handle involves the bottom edge. */
    val isBottom: Boolean get() = this == BottomLeft || this == BottomRight || this == Bottom
}
