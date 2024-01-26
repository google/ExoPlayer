# ExoPlayer DASH module

Provides support for Dynamic Adaptive Streaming over HTTP (DASH) content in
ExoPlayer.

## Getting the module

The easiest way to get the module is to add it as a gradle dependency:

```gradle
implementation 'androidx.media3:media3-exoplayer-dash:1.X.X'
```

where `1.X.X` is the version, which must match the version of the other media
modules being used.

Alternatively, you can clone this GitHub project and depend on the module
locally. Instructions for doing this can be found in the [top level README][].

[top level README]: ../../README.md

## Using the module

Adding a dependency to this module is all that's required to enable playback of
DASH media items added to `ExoPlayer` in its default configuration. Internally,
`DefaultMediaSourceFactory` will automatically detect the presence of the module
and convert a DASH `MediaItem` into a `DashMediaSource` for playback.

Similarly, a `DownloadManager` in its default configuration will use
`DefaultDownloaderFactory`, which will automatically detect the presence of
the module and build `DashDownloader` instances to download DASH content.

For advanced playback use cases, applications can build `DashMediaSource`
instances and pass them directly to the player. For advanced download use cases,
`DashDownloader` can be used directly.

## Links

*   [Developer Guide][]
*   [Javadoc][]

[Developer Guide]: https://developer.android.com/media/media3/exoplayer/dash
[Javadoc]: https://developer.android.com/reference/androidx/media3/exoplayer/dash/package-summary
