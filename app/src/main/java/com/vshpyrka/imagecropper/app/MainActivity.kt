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

@Composable
fun ImageCropperApp() {
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var assetToLoad by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    
    // Photo Picker
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            currentUri = uri
        }
    }
    
    // Camera
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            currentUri = tempCameraUri
        }
    }

    fun launchCamera() {
        val file = createImageFile(context)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        tempCameraUri = uri
        takePicture.launch(uri)
    }

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { 
                    try {
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error launching picker", e)
                        android.widget.Toast.makeText(context, "Error launching picker: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Text("Gallery", modifier = Modifier.padding(start = 8.dp))
                }
                
                Button(onClick = { 
                    try {
                        launchCamera()
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error launching camera", e)
                        android.widget.Toast.makeText(context, "Error launching camera: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Text("Camera", modifier = Modifier.padding(start = 8.dp))
                }

                Button(onClick = { 
                     currentUri = null // Reset Uri to trigger LaunchedEffect if needed, or we use a separate state
                     // We need a trigger. Let's use a side-effect or just set a flag.
                     // Simpler: Set a special "asset" state or callback.
                     // Let's us introduce `assetToLoad` state.
                     assetToLoad = "nature.png"
                }) {
                    Icon(Icons.Default.Image, contentDescription = null) // Reusing Image icon or similar
                    Text("Assets", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    ) { paddingVals ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
        ) {
            val constraints = this.constraints
            
            LaunchedEffect(currentUri, assetToLoad) {
                if (currentUri != null) {
                    isLoading = true
                    android.util.Log.d("MainActivity", "Loading URI: $currentUri")
                    val bitmap = BitmapLoader.loadBitmap(
                        context,
                        currentUri!!,
                        constraints.maxWidth,
                        constraints.maxHeight
                    )
                    imageBitmap = bitmap
                    isLoading = false
                    
                    if (bitmap == null) {
                       // Error handling
                    } 
                } else if (assetToLoad != null) {
                     isLoading = true
                     val bitmap = BitmapLoader.loadBitmapFromAssets(
                         context,
                         assetToLoad!!,
                         constraints.maxWidth,
                         constraints.maxHeight
                     )
                     imageBitmap = bitmap
                     isLoading = false
                     assetToLoad = null // Reset
                }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                imageBitmap?.let { bmp ->
                    ImageCropper(bitmap = bmp)
                } ?: run {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select an image to start cropping")
                    }
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
