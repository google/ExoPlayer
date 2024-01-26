# ExoPlayer RTSP module

Provides support for RTSP playbacks in ExoPlayer.

## Getting the module

The easiest way to get the module is to add it as a gradle dependency:

```gradle
implementation 'androidx.media3:media3-exoplayer-rtsp:1.X.X'
```

where `1.X.X` is the version, which must match the version of the other media
modules being used.

Alternatively, you can clone this GitHub project and depend on the module
locally. Instructions for doing this can be found in the [top level README][].

[top level README]: ../../README.md

## Using the module

Adding a dependency to this module is all that's required to enable playback of
RTSP media items added to `ExoPlayer` in its default configuration. Internally,
`DefaultMediaSourceFactory` will automatically detect the presence of the module
and convert a RTSP `MediaItem` into a `RtspMediaSource` for playback.

For advanced playback use cases, applications can build `RtspMediaSource`
instances and pass them directly to the player.

## Links

*   [Developer Guide][]
*   [Javadoc][]

[Developer Guide]: https://developer.android.com/media/media3/exoplayer/rtsp
[Javadoc]: https://developer.android.com/reference/androidx/media3/exoplayer/rtsp/package-summary
