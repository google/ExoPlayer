# Proguard rules specific to the core module.

# Constructors accessed via reflection in DefaultRenderersFactory
-dontnote androidx.media3.decoder.vp9.LibvpxVideoRenderer
-keepclassmembers class androidx.media3.decoder.vp9.LibvpxVideoRenderer {
  <init>(long, android.os.Handler, androidx.media3.exoplayer.video.VideoRendererEventListener, int);
}
-dontnote androidx.media3.decoder.av1.Libgav1VideoRenderer
-keepclassmembers class androidx.media3.decoder.av1.Libgav1VideoRenderer {
  <init>(long, android.os.Handler, androidx.media3.exoplayer.video.VideoRendererEventListener, int);
}
-dontnote androidx.media3.decoder.ffmpeg.ExperimentalFfmpegVideoRenderer
-keepclassmembers class androidx.media3.decoder.ffmpeg.ExperimentalFfmpegVideoRenderer {
  <init>(long, android.os.Handler, androidx.media3.exoplayer.video.VideoRendererEventListener, int);
}
-dontnote androidx.media3.decoder.opus.LibopusAudioRenderer
-keepclassmembers class androidx.media3.decoder.opus.LibopusAudioRenderer {
  <init>(android.os.Handler, androidx.media3.exoplayer.audio.AudioRendererEventListener, androidx.media3.exoplayer.audio.AudioSink);
}
-dontnote androidx.media3.decoder.flac.LibflacAudioRenderer
-keepclassmembers class androidx.media3.decoder.flac.LibflacAudioRenderer {
  <init>(android.os.Handler, androidx.media3.exoplayer.audio.AudioRendererEventListener, androidx.media3.exoplayer.audio.AudioSink);
}
-dontnote androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
-keepclassmembers class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer {
  <init>(android.os.Handler, androidx.media3.exoplayer.audio.AudioRendererEventListener, androidx.media3.exoplayer.audio.AudioSink);
}
-dontnote androidx.media3.decoder.midi.MidiRenderer
-keepclassmembers class androidx.media3.decoder.midi.MidiRenderer {
  <init>(android.content.Context);
}

# Constructors accessed via reflection in DefaultDownloaderFactory
-dontnote androidx.media3.exoplayer.dash.offline.DashDownloader
-keepclassmembers class androidx.media3.exoplayer.dash.offline.DashDownloader {
  <init>(androidx.media3.common.MediaItem, androidx.media3.datasource.cache.CacheDataSource$Factory, java.util.concurrent.Executor);
}
-dontnote androidx.media3.exoplayer.hls.offline.HlsDownloader
-keepclassmembers class androidx.media3.exoplayer.hls.offline.HlsDownloader {
  <init>(androidx.media3.common.MediaItem, androidx.media3.datasource.cache.CacheDataSource$Factory, java.util.concurrent.Executor);
}
-dontnote androidx.media3.exoplayer.smoothstreaming.offline.SsDownloader
-keepclassmembers class androidx.media3.exoplayer.smoothstreaming.offline.SsDownloader {
  <init>(androidx.media3.common.MediaItem, androidx.media3.datasource.cache.CacheDataSource$Factory, java.util.concurrent.Executor);
}

# Constructors accessed via reflection in DefaultMediaSourceFactory
-dontnote androidx.media3.exoplayer.dash.DashMediaSource$Factory
-keepclasseswithmembers class androidx.media3.exoplayer.dash.DashMediaSource$Factory {
  <init>(androidx.media3.datasource.DataSource$Factory);
}
-dontnote androidx.media3.exoplayer.hls.HlsMediaSource$Factory
-keepclasseswithmembers class androidx.media3.exoplayer.hls.HlsMediaSource$Factory {
  <init>(androidx.media3.datasource.DataSource$Factory);
}
-dontnote androidx.media3.exoplayer.smoothstreaming.SsMediaSource$Factory
-keepclasseswithmembers class androidx.media3.exoplayer.smoothstreaming.SsMediaSource$Factory {
  <init>(androidx.media3.datasource.DataSource$Factory);
}
-dontnote androidx.media3.exoplayer.rtsp.RtspMediaSource$Factory
-keepclasseswithmembers class androidx.media3.exoplayer.rtsp.RtspMediaSource$Factory {
  <init>();
}

# Constructors and methods accessed via reflection in CompositingVideoSinkProvider
-dontnote androidx.media3.effect.PreviewingSingleInputVideoGraph$Factory
-keepclasseswithmembers class androidx.media3.effect.PreviewingSingleInputVideoGraph$Factory {
  <init>(androidx.media3.common.VideoFrameProcessor$Factory);
}
-dontnote androidx.media3.effect.DefaultVideoFrameProcessor$Factory$Builder
-keepclasseswithmembers class androidx.media3.effect.DefaultVideoFrameProcessor$Factory$Builder {
  androidx.media3.effect.DefaultVideoFrameProcessor$Factory build();
}
