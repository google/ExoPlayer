# ExoPlayer IMA extension #

## Description ##

The IMA extension is a [MediaSource][] implementation wrapping the
[Interactive Media Ads SDK for Android][IMA]. You can use it to insert ads
alongside content.

[IMA]: https://developers.google.com/interactive-media-ads/docs/sdks/android/
[MediaSource]: https://github.com/google/ExoPlayer/blob/release-v2/library/src/main/java/com/google/android/exoplayer2/source/MediaSource.java

## Using the extension ##

Pass a single-window content `MediaSource` to `ImaAdsMediaSource`'s constructor,
along with a `ViewGroup` that is on top of the player and the ad tag URI to
show. The IMA documentation includes some [sample ad tags][] for testing. Then
pass the `ImaAdsMediaSource` to `ExoPlayer.prepare`.

[sample ad tags]: https://developers.google.com/interactive-media-ads/docs/sdks/android/tags

## Known issues ##

This is a preview version with some known issues:

* Midroll ads are not yet fully supported. In particular, seeking with midroll
ads is not yet supported. Played ad periods are not removed. Also, `playAd` and
`AD_STARTED` events are sometimes delayed, meaning that midroll ads take a long
time to start and the ad overlay does not show immediately.
* Tapping the 'More info' button on an ad in the demo app will pause the
activity, which destroys the ImaAdsMediaSource. Played ad breaks will be
shown to the user again if the demo app returns to the foreground.
