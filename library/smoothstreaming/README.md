# ExoPlayer SmoothStreaming library module #

Provides support for SmoothStreaming content.

Adding a dependency to this module is all that's required to enable playback of
SmoothStreaming `MediaItem`s added to an `ExoPlayer` or `SimpleExoPlayer` in
their default configurations. Internally, `DefaultMediaSourceFactory` will
automatically detect the presence of the module and convert SmoothStreaming
`MediaItem`s into `SsMediaSource` instances for playback.

Similarly, a `DownloadManager` in its default configuration will use
`DefaultDownloaderFactory`, which will automatically detect the presence of
the module and build `SsDownloader` instances to download SmoothStreaming
content.

For advanced playback use cases, applications can build `SsMediaSource`
instances and pass them directly to the player. For advanced download use cases,
`SsDownloader` can be used directly.

## Links ##

* [Developer Guide][].
* [Javadoc][]: Classes matching
  `com.google.android.exoplayer2.source.smoothstreaming.*` belong to this
  module.

[Developer Guide]: https://exoplayer.dev/smoothstreaming.html
[Javadoc]: https://exoplayer.dev/doc/reference/index.html
