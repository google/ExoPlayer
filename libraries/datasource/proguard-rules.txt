# Proguard rules specific to the DataSource module.

# Constant folding for resource integers may mean that a resource passed to this method appears to be unused. Keep the method to prevent this from happening.
-keepclassmembers class androidx.media3.datasource.RawResourceDataSource {
  public static android.net.Uri buildRawResourceUri(int);
}

# Constructors accessed via reflection in DefaultDataSource
-dontnote androidx.media3.datasource.rtmp.RtmpDataSource
-keepclassmembers class androidx.media3.datasource.rtmp.RtmpDataSource {
  <init>();
}
