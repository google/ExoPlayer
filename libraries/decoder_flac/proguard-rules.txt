# Proguard rules specific to the Flac extension.

# This prevents the names of native methods from being obfuscated.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Some members of these classes are being accessed from native methods. Keep them unobfuscated.
-keep class androidx.media3.decoder.flac.FlacDecoderJni {
    *;
}
-keep class androidx.media3.extractor.FlacStreamMetadata {
    *;
}
-keep class androidx.media3.extractor.metadata.flac.PictureFrame {
    *;
}
