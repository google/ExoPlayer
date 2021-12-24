# ExoPlayer SmoothStreaming module

Provides support for SmoothStreaming content in ExoPlayer.

## Getting the module

The easiest way to get the module is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:exoplayer-smoothstreaming:2.X.X'
```

where `2.X.X` is the version, which must match the version of the other media
modules being used.

Alternatively, you can clone this GitHub project and depend on the module
locally. Instructions for doing this can be found in the [top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md

Adding a dependency to this module is all that's required to enable playback of
SmoothStreaming media items added to `ExoPlayer` in its default configuration.
Internally, `DefaultMediaSourceFactory` will automatically detect the presence
of the module and convert a SmoothStreaming `MediaItem` into a `SsMediaSource`
for playback.

Similarly, a `DownloadManager` in its default configuration will use
`DefaultDownloaderFactory`, which will automatically detect the presence of
the module and build `SsDownloader` instances to download SmoothStreaming
content.

For advanced playback use cases, applications can build `SsMediaSource`
instances and pass them directly to the player. For advanced download use cases,
`SsDownloader` can be used directly.

## Links

* [Developer Guide][]
* [Javadoc][]

[Developer Guide]: https://exoplayer.dev/smoothstreaming.html
[Javadoc]: https://exoplayer.dev/doc/reference/index.html
