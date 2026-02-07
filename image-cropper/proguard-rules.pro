# Add project specific ProGuard rules here.
# Keep public API classes and members
-keep class com.vshpyrka.imagecropper.BitmapLoader { *; }
-keep class com.vshpyrka.imagecropper.ImageCropperKt { *; }
-keep class com.vshpyrka.imagecropper.ImageCropperState {
    public <methods>;
    public <fields>;
}
