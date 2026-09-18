# VisionAid ProGuard rules
# Minification is disabled by default in this project; these rules keep the
# build safe if you enable it.

# ---- TensorFlow Lite ----
-dontwarn org.tensorflow.lite.**
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.nnapi.** { *; }

# ---- Gson DTOs (Gemini REST API models) ----
-keep class com.visionaid.app.data.remote.gemini.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# ---- ML Kit / Play services ----
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ---- OkHttp / Retrofit ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ---- WorkManager + Hilt worker ----
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
