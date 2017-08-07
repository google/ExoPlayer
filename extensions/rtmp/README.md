# ExoPlayer RTMP extension #

## Description ##

The RTMP extension is a [DataSource][] implementation for playing [RTMP][]
streams using [LibRtmp Client for Android][].

[DataSource]: https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/upstream/DataSource.html
[RTMP]: https://en.wikipedia.org/wiki/Real-Time_Messaging_Protocol
[LibRtmp Client for Android]: https://github.com/ant-media/LibRtmp-Client-for-Android

## Using the extension ##

The easiest way to use the extension is to add it as a gradle dependency:

```gradle
compile 'com.google.android.exoplayer:extension-rtmp:rX.X.X'
```

where `rX.X.X` is the version, which must match the version of the ExoPlayer
library being used.

Alternatively, you can clone the ExoPlayer repository and depend on the module
locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
