# Keep Hilt-generated components
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Keep model classes used for serialization/state
-keep class com.jupiter.filemanager.domain.model.** { *; }

# Networking / remote-protocol libraries (optional transitive deps R8 warns about)
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn com.hierynomus.**
-dontwarn net.engio.mbassy.**
-dontwarn org.apache.commons.net.**
-dontwarn com.jcraft.jsch.**
-dontwarn fi.iki.elonen.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn javax.naming.**
-dontwarn java.beans.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.junrar.**
-dontwarn org.apache.commons.logging.**
-keep class com.hierynomus.** { *; }
-keep class com.jcraft.jsch.** { *; }
