# ExoPlayer IMA extension #

## Description ##

The IMA extension is a [MediaSource][] implementation wrapping the
[Interactive Media Ads SDK for Android][IMA]. You can use it to insert ads
alongside content.

[IMA]: https://developers.google.com/interactive-media-ads/docs/sdks/android/
[MediaSource]: https://github.com/google/ExoPlayer/blob/release-v2/library/core/src/main/java/com/google/android/exoplayer2/source/MediaSource.java

## Getting the extension ##

To use this extension you need to clone the ExoPlayer repository and depend on
its modules locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md

## Using the extension ##

Pass a single-window content `MediaSource` to `ImaAdsMediaSource`'s constructor,
along with a `ViewGroup` that is on top of the player and the ad tag URI to
show. The IMA documentation includes some [sample ad tags][] for testing. Then
pass the `ImaAdsMediaSource` to `ExoPlayer.prepare`.

You can try the IMA extension in the ExoPlayer demo app. To do this you must
select and build one of the `withExtensions` build variants of the demo app in
Android Studio. You can find IMA test content in the "IMA sample ad tags"
section of the app.

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
[sample ad tags]: https://developers.google.com/interactive-media-ads/docs/sdks/android/tags

## Known issues ##

This is a preview version with some known issues:

* Tapping the 'More info' button on an ad in the demo app will pause the
  activity, which destroys the ImaAdsMediaSource. Played ad breaks will be
  shown to the user again if the demo app returns to the foreground.
* Ad loading timeouts are currently propagated as player errors, rather than
  being silently handled by resuming content.
