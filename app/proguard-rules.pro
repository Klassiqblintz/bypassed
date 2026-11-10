# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK keep.pro file.

# Keep all Compose classes
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }

# Keep our app classes
-keep class com.kycbypasser.app.** { *; }
