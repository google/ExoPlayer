# Proguard rules specific to the extractor module.

# Methods accessed via reflection in DefaultExtractorsFactory
-dontnote com.google.android.exoplayer2.ext.flac.FlacExtractor
-keepclassmembers class com.google.android.exoplayer2.ext.flac.FlacExtractor {
  <init>(int);
}
-dontnote com.google.android.exoplayer2.ext.flac.FlacLibrary
-keepclassmembers class com.google.android.exoplayer2.ext.flac.FlacLibrary {
  public static boolean isAvailable();
}

# Don't warn about checkerframework and Kotlin annotations
-dontwarn org.checkerframework.**
-dontwarn kotlin.annotations.jvm.**
-dontwarn javax.annotation.**
