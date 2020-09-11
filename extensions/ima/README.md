# ExoPlayer IMA extension #

The IMA extension is an [AdsLoader][] implementation wrapping the
[Interactive Media Ads SDK for Android][IMA]. You can use it to insert ads
alongside content.

[IMA]: https://developers.google.com/interactive-media-ads/docs/sdks/android/
[AdsLoader]: https://exoplayer.dev/doc/reference/index.html?com/google/android/exoplayer2/source/ads/AdsLoader.html

## Getting the extension ##

The easiest way to use the extension is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:extension-ima:2.X.X'
```

where `2.X.X` is the version, which must match the version of the ExoPlayer
library being used.

Alternatively, you can clone the ExoPlayer repository and depend on the module
locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md

## Using the extension ##

To play a media item with an ad tag URI, you need to customize the
`DefaultMediaSourceFactory` as
[documented in the Developer Guide](https://exoplayer.dev/media-sources.html#customizing-media-source-creation).
This way the player will build an `AdsMediaSource` and configure it with the
`ImaAdsLoader` and an `AdViewProvider` automatically.

[Pass an ad tag URI](https://exoplayer.dev/media-items.html#ad-insertion) from
your ad campaign to the `MediaItem.Builder` when building your media item. The
IMA documentation includes some [sample ad tags][] for testing. Note that the
IMA extension only supports players which are accessed on the application's main
thread.

Resuming the player after entering the background requires some special handling
when playing ads. The player and its media source are released on entering the
background, and are recreated when the player returns to the foreground. When
playing ads it is necessary to persist ad playback state while in the background
by keeping a reference to the `ImaAdsLoader`. Reuse this instance when your
callback `AdsLoaderProvider.getAdsLoader(Uri adTagUri)` is called by the player
to get an `ImaAdsLoader` for the same content/ads to be resumed. It is also
important to persist the player position when entering the background by storing
the value of `player.getContentPosition()`. On returning to the foreground, seek
to that position before preparing the new player instance. Finally, it is
important to call `ImaAdsLoader.release()` when playback of the content/ads has
finished and will not be resumed.

You can try the IMA extension in the ExoPlayer demo app, which has test content
in the "IMA sample ad tags" section of the sample chooser. The demo app's
`PlayerActivity` also shows how to persist the `ImaAdsLoader` instance and the
player position when backgrounded during ad playback.

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
[sample ad tags]: https://developers.google.com/interactive-media-ads/docs/sdks/android/tags

## Links ##

* [ExoPlayer documentation on ad insertion][]
* [Javadoc][]: Classes matching `com.google.android.exoplayer2.ext.ima.*`
  belong to this module.

[ExoPlayer documentation on ad insertion]: https://exoplayer.dev/ad-insertion.html
[Javadoc]: https://exoplayer.dev/doc/reference/index.html
