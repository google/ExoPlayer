# ExoPlayer RTMP Extension #

## Description ##

The RTMP Extension is an [DataSource][] implementation for playing [RTMP][] streaming using
[Librtmp Client for Android].

## Using the extension ##

When building [MediaSource][], inject `RtmpDataSourceFactory` like this:

```java
private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
  int type = TextUtils.isEmpty(overrideExtension) ? Util.inferContentType(uri)
      : Util.inferContentType("." + overrideExtension);
  switch (type) {

    // ... other types cases

    case C.TYPE_OTHER:
      DataSource.Factory factory = uri.getScheme().equals("rtmp") ? new RtmpDataSourceFactory() : mediaDataSourceFactory;
      return new ExtractorMediaSource(uri, factory, new DefaultExtractorsFactory(), mainHandler, eventLogger);
    default: {
      throw new IllegalStateException("Unsupported type: " + type);
    }
  }
}
```


[DataSource]: https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/upstream/DataSource.html
[RTMP]: https://en.wikipedia.org/wiki/Real-Time_Messaging_Protocol
[Librtmp Client for Android]: https://github.com/ant-media/LibRtmp-Client-for-Android
[MediaSource]: https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/source/MediaSource.html
