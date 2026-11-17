# RabBeat desktop release ProGuard rules.
# The plugin's default rules already cover Kotlin, Skiko, coroutines and
# kotlinx.serialization; this file adds the app-specific bits.

# ---- Missing optional/platform classes referenced by third-party libs ----
# okhttp3's platform adapters reference Android-only and optional TLS classes.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn android.**
-dontwarn dalvik.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# jna-platform drags in dbus-java (Linux-only) and junit from audio libs.
-dontwarn org.freedesktop.dbus.**
-dontwarn junit.**
-dontwarn com.ibm.uvm.**
-dontwarn com.sun.jna.**
-dontwarn javazoom.**

# ---- Reflectively-loaded classes the shrinker would otherwise drop ----

# Room: *_Impl (database + DAO impls) is instantiated via Class.forName.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class **_Impl { *; }
-keepclasseswithmembernames class * {
    @androidx.room.* <methods>;
}

# JNA: everything is bound reflectively (FileKit dialogs use jna-platform COM).
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }

# javax.sound SPIs (mp3/flac/vorbis) are only referenced from
# META-INF/services files loaded by AudioSystem's ServiceLoader.
-keep class * implements javax.sound.sampled.spi.AudioFileReader { *; }
-keep class * implements javax.sound.sampled.spi.FormatConversionProvider { *; }
-keep class * implements javax.sound.sampled.spi.MixerProvider { *; }
-keepdirectories META-INF/services

# Coil3 discovers network fetchers via ServiceLoader.
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }

# jaudiotagger instantiates tag readers by class lookup.
-keep class org.jaudiotagger.** { *; }

# sqlite-bundled JNI driver.
-keep class androidx.sqlite.** { *; }

# Annotations read at runtime by Room/kotlinx.serialization.
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, Signature
