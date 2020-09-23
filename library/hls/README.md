# ExoPlayer HLS library module #

Provides support for HTTP Live Streaming (HLS) content.

Adding a dependency to this module is all that's required to enable playback of
HLS `MediaItem`s added to an `ExoPlayer` or `SimpleExoPlayer` in their default
configurations. Internally, `DefaultMediaSourceFactory` will automatically
detect the presence of the module and convert HLS `MediaItem`s into
`HlsMediaSource` instances for playback.

Similarly, a `DownloadManager` in its default configuration will use
`DefaultDownloaderFactory`, which will automatically detect the presence of
the module and build `HlsDownloader` instances to download HLS content.

For advanced playback use cases, applications can build `HlsMediaSource`
instances and pass them directly to the player. For advanced download use cases,
`HlsDownloader` can be used directly.

## Links ##

* [Developer Guide][].
* [Javadoc][]: Classes matching `com.google.android.exoplayer2.source.hls.*`
  belong to this module.

[Developer Guide]: https://exoplayer.dev/hls.html
[Javadoc]: https://exoplayer.dev/doc/reference/index.html
