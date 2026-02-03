# ImageCropper

A powerful, customizable, and smooth image cropping library for Android, built entirely using **Pure Android Compose**.

![Kotlin](https://img.shields.io/badge/kotlin-100%25-blue.svg)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Ready-green.svg)
![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)
![Build Status](https://github.com/vshpyrka/android-image-cropper/actions/workflows/build.yml/badge.svg)

## 🎥 Demo

![test2](https://github.com/user-attachments/assets/68e85b2e-8b2c-4561-8c9d-fab501aee0b2)

## ✨ Features

*   **Pure Compose**: Built from the ground up using Jetpack Compose drawing and gestures.
*   **Smooth Interactions**: Supports pinch-to-zoom, pan-to-move, and handle dragging.
*   **Dynamic Viewport**: Automatically pans and adjusts the viewport when dragging near edges.
*   **Customizable**: Easily style the overlay, border, handles, and grid colors.
*   **Grid Support**: Includes a rule-of-thirds grid for precise composition.

## 📦 Installation
TBD

<!--- Add the library to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.yourusername:ImageCropper:1.0.0") // Replace with actual version/jitpack link
}
```
-->

## 🚀 Usage

Using the `ImageCropper` is simple. Just pass an `ImageBitmap` and hoist the state to get the result.

```kotlin
@Composable
fun MyCroppingScreen(inputBitmap: Bitmap) {
    // 1. Convert various image types to ImageBitmap
    val imageBitmap = inputBitmap.asImageBitmap()

    // 2. Create the state
    val state = rememberImageCropperState(imageBitmap)
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // 3. Render the cropper
        Box(modifier = Modifier.weight(1f)) {
            ImageCropper(
                imageBitmap = imageBitmap,
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 4. Controls
        Button(
            onClick = {
                // Get the cropped result
                val croppedImage: ImageBitmap = state.crop()
                
                // Do something with the result...
            }
        ) {
            Text("Crop Image")
        }
        
        Button(
            onClick = {
                scope.launch { state.reset() }
            }
        ) {
            Text("Reset")
        }
    }
}
```

### Customization

You can customize the look and feel by passing `ImageCropperColors`:

```kotlin
ImageCropper(
    imageBitmap = imageBitmap,
    colors = ImageCropperDefaults.colors(
        overlayColor = Color.Black.copy(alpha = 0.7f),
        borderColor = Color.Cyan,
        handleColor = Color.White,
        gridColor = Color.White.copy(alpha = 0.5f)
    )
)
```

## 📄 License

```text
Copyright 2026 vshpyrka

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
