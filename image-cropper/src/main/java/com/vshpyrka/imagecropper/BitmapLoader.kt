package com.vshpyrka.imagecropper

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility object for loading and resizing bitmaps efficiently to avoid OOM errors.
 * Uses [BitmapFactory.Options.inSampleSize] to downsample images during decoding.
 */
object BitmapLoader {

    /**
     * Loads a bitmap from the given [Uri] and resizes it to fit within the specified dimensions.
     *
     * @param context The context used to access the [ContentResolver].
     * @param uri The [Uri] of the image to load.
     * @param maxWidth The maximum width of the resulting bitmap.
     * @param maxHeight The maximum height of the resulting bitmap.
     * @param dispatcher The [CoroutineDispatcher] to perform the IO operations on. Defaults to [Dispatchers.IO].
     * @return The loaded [Bitmap], or null if loading failed.
     */
    suspend fun loadBitmap(
        context: Context,
        uri: Uri,
        maxWidth: Int,
        maxHeight: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Bitmap? = withContext(dispatcher) {
        try {
            val contentResolver = context.contentResolver

            // 1. Decode bounds
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
                Unit // Return Unit to ensure use returns Unit (non-null) if stream opened
            } ?: run {
                android.util.Log.e("BitmapLoader", "Failed to open input stream for bounds")
                return@withContext null
            }

            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            android.util.Log.d("BitmapLoader", "Image stats: $srcWidth x $srcHeight")
            
            if (srcWidth <= 0 || srcHeight <= 0) {
                 android.util.Log.e("BitmapLoader", "Invalid image dimensions")
                 return@withContext null
            }

            // 2. Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(srcWidth, srcHeight, maxWidth, maxHeight)
            options.inJustDecodeBounds = false
            android.util.Log.d("BitmapLoader", "inSampleSize: ${options.inSampleSize}")

            // 3. Decode bitmap
            val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            
            if (bitmap == null) {
                android.util.Log.e("BitmapLoader", "Failed to decode bitmap")
            }
            
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("BitmapLoader", "Error loading bitmap", e)
            null
        }
    }


    /**
     * Loads a bitmap from the application assets and resizes it.
     *
     * @param context The context used to access assets.
     * @param fileName The name of the file in the assets folder.
     * @param maxWidth The maximum width of the resulting bitmap.
     * @param maxHeight The maximum height of the resulting bitmap.
     * @param dispatcher The [CoroutineDispatcher] to perform the IO operations on. Defaults to [Dispatchers.IO].
     * @return The loaded [Bitmap], or null if loading failed.
     */
    suspend fun loadBitmapFromAssets(
        context: Context,
        fileName: String,
        maxWidth: Int,
        maxHeight: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Bitmap? = withContext(dispatcher) {
        try {
            // 1. Decode bounds
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.assets.open(fileName).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
                Unit
            }

            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            android.util.Log.d("BitmapLoader", "Asset stats: $srcWidth x $srcHeight")
            
            if (srcWidth <= 0 || srcHeight <= 0) {
                 android.util.Log.e("BitmapLoader", "Invalid asset dimensions")
                 return@withContext null
            }

            // 2. Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(srcWidth, srcHeight, maxWidth, maxHeight)
            options.inJustDecodeBounds = false
            android.util.Log.d("BitmapLoader", "inSampleSize: ${options.inSampleSize}")

            // 3. Decode bitmap
            val bitmap = context.assets.open(fileName).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            
            if (bitmap == null) {
                android.util.Log.e("BitmapLoader", "Failed to decode asset bitmap")
            }
            
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("BitmapLoader", "Error loading asset bitmap", e)
            null
        }
    }

    /**
     * Calculates the largest inSampleSize value that is a power of 2 and keeps both
     * height and width larger than the requested height and width.
     *
     * @param srcWidth The original width of the image.
     * @param srcHeight The original height of the image.
     * @param reqWidth The required width of the image.
     * @param reqHeight The required height of the image.
     * @return The calculated inSampleSize.
     */
    private fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight: Int = srcHeight / 2
            val halfWidth: Int = srcWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
