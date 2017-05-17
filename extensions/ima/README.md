# ExoPlayer IMA extension #

## Description ##

The IMA extension is a [MediaSource][] implementation wrapping the
[Interactive Media Ads SDK for Android][IMA]. You can use it to insert ads
alongside content.

[IMA]: https://developers.google.com/interactive-media-ads/docs/sdks/android/
[MediaSource]: https://github.com/google/ExoPlayer/blob/release-v2/library/core/src/main/java/com/google/android/exoplayer2/source/MediaSource.java

## Using the extension ##

Pass a single-window content `MediaSource` to `ImaAdsMediaSource`'s constructor,
along with a `ViewGroup` that is on top of the player and the ad tag URI to
show. The IMA documentation includes some [sample ad tags][] for testing. Then
pass the `ImaAdsMediaSource` to `ExoPlayer.prepare`.

You can try the IMA extension in the ExoPlayer demo app. To do this you must
select and build one of the `withExtensions` build variants of the demo app in
Android Studio. You can find IMA test content in the "IMA sample ad tags"
section of the app.

[sample ad tags]: https://developers.google.com/interactive-media-ads/docs/sdks/android/tags

## Known issues ##

This is a preview version with some known issues:

* Seeking is not yet ad aware. This means that it's possible to seek back into
  ads that have already been played, and also seek past midroll ads without
  them being played. Seeking will be made ad aware for the first stable release.
* Midroll ads are not yet fully supported. `playAd` and `AD_STARTED` events are
  sometimes delayed, meaning that midroll ads take a long time to start and the
  ad overlay does not show immediately.
* Tapping the 'More info' button on an ad in the demo app will pause the
  activity, which destroys the ImaAdsMediaSource. Played ad breaks will be
  shown to the user again if the demo app returns to the foreground.
