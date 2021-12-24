# Proguard rules specific to the DataSource module.

# Constant folding for resource integers may mean that a resource passed to this method appears to be unused. Keep the method to prevent this from happening.
-keepclassmembers class com.google.android.exoplayer2.upstream.RawResourceDataSource {
  public static android.net.Uri buildRawResourceUri(int);
}

# Constructors accessed via reflection in DefaultDataSource
-dontnote com.google.android.exoplayer2.ext.rtmp.RtmpDataSource
-keepclassmembers class com.google.android.exoplayer2.ext.rtmp.RtmpDataSource {
  <init>();
}
