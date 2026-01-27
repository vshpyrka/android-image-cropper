package com.vshpyrka.imagecropper.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.vshpyrka.imagecropper.BitmapLoader
import com.vshpyrka.imagecropper.ImageCropper
import com.vshpyrka.imagecropper.rememberImageCropperState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ImageCropperApp()
            }
        }
    }
}

@Stable
internal class ImageCropperAppState {
    var imageBitmap by mutableStateOf<Bitmap?>(null)
    var croppedBitmap by mutableStateOf<Bitmap?>(null)
    var currentUri by mutableStateOf<Uri?>(null)
    var assetToLoad by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)

    fun reset() {
        imageBitmap = null
        croppedBitmap = null
        currentUri = null
        assetToLoad = null
        isLoading = false
    }
}

@Composable
internal fun rememberImageCropperAppState(): ImageCropperAppState {
    return remember { ImageCropperAppState() }
}

@Composable
fun ImageCropperApp() {
    val state = rememberImageCropperAppState()
    val context = LocalContext.current

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            state.currentUri = uri
        }
    }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            state.currentUri = tempCameraUri
        }
    }

    Scaffold { paddingVals ->
        Box(modifier = Modifier.padding(paddingVals).fillMaxSize()) {
            when {
                state.croppedBitmap != null -> {
                    ResultScreen(
                        bitmap = state.croppedBitmap!!,
                        onReset = { state.reset() }
                    )
                }
                state.imageBitmap != null -> {
                    CroppingScreen(
                        imageBitmap = state.imageBitmap!!.asImageBitmap(),
                        onCrop = { cropped ->
                            state.croppedBitmap = cropped
                        },
                        onCancel = { state.reset() }
                    )
                }
                else -> {
                    SelectionScreen(
                        isLoading = state.isLoading,
                        onGalleryClick = {
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onCameraClick = {
                            val file = createImageFile(context)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            tempCameraUri = uri
                            takePicture.launch(uri)
                        },
                        onAssetsClick = {
                            state.assetToLoad = "nature.png"
                        }
                    )
                }
            }
        }
    }

    // Effect to load image when URI or Asset changes
    LaunchedEffect(state.currentUri, state.assetToLoad) {
        if (state.currentUri != null) {
            state.isLoading = true
            val bitmap = BitmapLoader.loadBitmap(context, state.currentUri!!, 2048, 2048)
            state.imageBitmap = bitmap
            state.isLoading = false
        } else if (state.assetToLoad != null) {
            state.isLoading = true
            val bitmap = BitmapLoader.loadBitmapFromAssets(context, state.assetToLoad!!, 2048, 2048)
            state.imageBitmap = bitmap
            state.isLoading = false
        }
    }
}

@Composable
private fun SelectionScreen(
    isLoading: Boolean,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onAssetsClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Text("Select an image to start", style = MaterialTheme.typography.headlineMedium)
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGalleryClick) {
                    Icon(Icons.Default.Image, null)
                    Text("Gallery", modifier = Modifier.padding(start = 4.dp))
                }
                Button(onClick = onCameraClick) {
                    Icon(Icons.Default.CameraAlt, null)
                    Text("Camera", modifier = Modifier.padding(start = 4.dp))
                }
                Button(onClick = onAssetsClick) {
                    Icon(Icons.Default.Image, null)
                    Text("Assets", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun CroppingScreen(
    imageBitmap: ImageBitmap,
    onCrop: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val cropperState = rememberImageCropperState(imageBitmap)
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            ImageCropper(imageBitmap = imageBitmap, state = cropperState)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onCancel) {
                Text("Cancel")
            }
            Button(onClick = {
                onCrop(cropperState.crop().asAndroidBitmap())
            }) {
                Icon(Icons.Default.Crop, null)
                Text("Crop", modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun ResultScreen(
    bitmap: Bitmap,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Cropped Result", style = MaterialTheme.typography.headlineMedium)
        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        Button(onClick = onReset) {
            Icon(Icons.Default.Refresh, null)
            Text("Start Over", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

private fun createImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
}
