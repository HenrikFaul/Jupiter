# Keep Hilt-generated components
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Keep model classes used for serialization/state
-keep class com.jupiter.filemanager.domain.model.** { *; }
