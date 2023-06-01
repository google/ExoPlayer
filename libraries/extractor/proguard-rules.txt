# Proguard rules specific to the extractor module.

# Methods accessed via reflection in DefaultExtractorsFactory
-dontnote androidx.media3.decoder.flac.FlacExtractor
-keepclassmembers class androidx.media3.decoder.flac.FlacExtractor {
  <init>(int);
}
-dontnote androidx.media3.decoder.flac.FlacLibrary
-keepclassmembers class androidx.media3.decoder.flac.FlacLibrary {
  public static boolean isAvailable();
}
-dontnote androidx.media3.decoder.midi.MidiExtractor
-keepclassmembers class androidx.media3.decoder.midi.MidiExtractor {
  <init>();
}

# Don't warn about checkerframework and Kotlin annotations
-dontwarn org.checkerframework.**
-dontwarn kotlin.annotations.jvm.**
-dontwarn javax.annotation.**
