# ExoPlayer DASH library module #

Provides support for Dynamic Adaptive Streaming over HTTP (DASH) content.

Adding a dependency to this module is all that's required to enable playback of
DASH `MediaItem`s added to an `ExoPlayer` or `SimpleExoPlayer` in their default
configurations. Internally, `DefaultMediaSourceFactory` will automatically
detect the presence of the module and convert DASH `MediaItem`s into
`DashMediaSource` instances for playback.

Similarly, a `DownloadManager` in its default configuration will use
`DefaultDownloaderFactory`, which will automatically detect the presence of
the module and build `DashDownloader` instances to download DASH content.

For advanced playback use cases, applications can build `DashMediaSource`
instances and pass them directly to the player. For advanced download use cases,
`DashDownloader` can be used directly.

## Links ##

* [Developer Guide][].
* [Javadoc][]: Classes matching `com.google.android.exoplayer2.source.dash.*`
  belong to this module.

[Developer Guide]: https://exoplayer.dev/dash.html
[Javadoc]: https://exoplayer.dev/doc/reference/index.html
