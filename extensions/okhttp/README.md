# ExoPlayer OkHttp Extension #

## Description ##

The OkHttp Extension is an [HttpDataSource][] implementation using Square's
[OkHttp][].

[HttpDataSource]: https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/upstream/HttpDataSource.html
[OkHttp]: https://square.github.io/okhttp/

## Getting the extension ##

The easiest way to use the extension is to add it as a gradle dependency:

```gradle
compile 'com.google.android.exoplayer:extension-okhttp:rX.X.X'
```

where `rX.X.X` is the version, which must match the version of the ExoPlayer
library being used.

Alternatively, you can clone the ExoPlayer repository and depend on the module
locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
