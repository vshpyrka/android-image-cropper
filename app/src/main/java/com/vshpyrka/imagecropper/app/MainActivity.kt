package com.vshpyrka.imagecropper.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.vshpyrka.imagecropper.BitmapLoader
import com.vshpyrka.imagecropper.ImageCropper
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
internal class ImageCropperState {
    var imageBitmap by mutableStateOf<Bitmap?>(null)
    var currentUri by mutableStateOf<Uri?>(null)
    var assetToLoad by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)
}

@Composable
internal fun rememberImageCropperState(): ImageCropperState {
    return remember { ImageCropperState() }
}

@Composable
fun ImageCropperApp() {
    val state = rememberImageCropperState()
    val context = LocalContext.current

    // Photo Picker
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            state.currentUri = uri
        }
    }

    // Camera
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            state.currentUri = tempCameraUri
        }
    }

    Scaffold(
        bottomBar = {
            ImageCropperBottomBar(
                onGalleryClick = {
                    try {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error launching picker", e)
                        android.widget.Toast.makeText(
                            context,
                            "Error launching picker: ${e.message}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onCameraClick = {
                    try {
                        val file = createImageFile(context)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                        tempCameraUri = uri
                        takePicture.launch(uri)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error launching camera", e)
                        android.widget.Toast.makeText(
                            context,
                            "Error launching camera: ${e.message}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onAssetsClick = {
                    state.assetToLoad = "nature.png"
                }
            )
        }
    ) { paddingVals ->
        ImageCropperContent(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
        )
    }
}

@Composable
private fun ImageCropperBottomBar(
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onAssetsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = onGalleryClick) {
            Icon(Icons.Default.Image, contentDescription = null)
            Text("Gallery", modifier = Modifier.padding(start = 8.dp))
        }

        Button(onClick = onCameraClick) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Text("Camera", modifier = Modifier.padding(start = 8.dp))
        }

        Button(onClick = onAssetsClick) {
            Icon(Icons.Default.Image, contentDescription = null)
            Text("Assets", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ImageCropperContent(
    state: ImageCropperState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    BoxWithConstraints(modifier = modifier) {
        val constraints = this.constraints

        LaunchedEffect(state.currentUri, state.assetToLoad) {
            if (state.currentUri != null) {
                state.isLoading = true
                android.util.Log.d("MainActivity", "Loading URI: ${state.currentUri}")
                val bitmap = BitmapLoader.loadBitmap(
                    context,
                    state.currentUri!!,
                    constraints.maxWidth,
                    constraints.maxHeight
                )
                state.imageBitmap = bitmap
                state.isLoading = false
            } else if (state.assetToLoad != null) {
                state.isLoading = true
                val bitmap = BitmapLoader.loadBitmapFromAssets(
                    context,
                    state.assetToLoad!!,
                    constraints.maxWidth,
                    constraints.maxHeight
                )
                state.imageBitmap = bitmap
                state.isLoading = false
                state.assetToLoad = null // Reset
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            state.imageBitmap?.let { bmp ->
                ImageCropper(bitmap = bmp)
            } ?: run {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select an image to start cropping")
                }
            }
        }
    }
}

private fun createImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile(
        "JPEG_${timeStamp}_",
        ".jpg",
        storageDir
    )
}
