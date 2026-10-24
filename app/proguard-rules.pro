# ProGuard / R8 rules for ADM

# Keep Kotlin standard library internals
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep OkHttp3
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep Hilt / Dagger
-keep class dagger.** { *; }
-keep class com.google.dagger.** { *; }
-dontwarn dagger.**

# Keep youtubedl-android
-keep class com.yausername.youtubedl_android.** { *; }
-dontwarn com.yausername.youtubedl_android.**

# Keep Android components & entry points
-keep public class com.atakan.adm.MainActivity
-keep public class com.atakan.adm.AdmApplication
-keep public class com.atakan.adm.service.** { *; }
