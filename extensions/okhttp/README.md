# ExoPlayer OkHttp Extension #

## Description ##

The OkHttp Extension is an [HttpDataSource][] implementation using Square's
[OkHttp][].

## Using the extension ##

The easiest way to use the extension is to add it as a gradle dependency. You
need to make sure you have the jcenter repository included in the `build.gradle`
file in the root of your project:

```gradle
repositories {
    jcenter()
}
```

Next, include the following in your module's `build.gradle` file:

```gradle
compile 'com.google.android.exoplayer:extension-okhttp:rX.X.X'
```

where `rX.X.X` is the version, which must match the version of the ExoPlayer
library being used.

[HttpDataSource]: https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/upstream/HttpDataSource.html
[OkHttp]: https://square.github.io/okhttp/
