---
title: Pros and cons
---

ExoPlayer has a number of advantages over Android's built in MediaPlayer:

* Fewer device specific issues and less variation in behavior across different
  devices and versions of Android.
* The ability to update the player along with your application. Because
  ExoPlayer is a library that you include in your application apk, you have
  control over which version you use and you can easily update to a newer
  version as part of a regular application update.
* The ability to [customize and extend the player][] to suit your use case.
  ExoPlayer is designed specifically with this in mind, and allows many
  components to be replaced with custom implementations.
* Support for [playlists][].
* Support for [DASH][] and [SmoothStreaming][], neither of which are supported
  by MediaPlayer. Many other formats are also supported. See the [Supported
  formats page][] for details.
* Support for advanced [HLS][] features, such as correct handling of
  `#EXT-X-DISCONTINUITY` tags.
* Support for [Widevine common encryption][] on Android 4.4 (API level 19) and
  higher.
* The ability to quickly integrate with a number of additional libraries using
  official extensions. For example the [IMA extension][] makes it easy to
  monetize your content using the [Interactive Media Ads SDK][].

It's important to note that there are also some disadvantages:

* For audio only playback on some devices, ExoPlayer may consume significantly
  more battery than MediaPlayer. See the [Battery consumption page][] for
  details.
* Including ExoPlayer in your app adds a few hundred kilobytes to the APK size.
  This is likely only a concern for extremely lightweight apps. Guidance for
  shrinking ExoPlayer can be found on the [APK shrinking page][].

[Supported formats page]: {{ site.baseurl }}/supported-formats.html
[IMA extension]: {{ site.release_v2 }}/extensions/ima
[Interactive Media Ads SDK]: https://developers.google.com/interactive-media-ads
[Battery consumption page]: {{ site.baseurl }}/battery-consumption.html
[customize and extend the player]: {{ site.baseurl }}/customization.html
[APK shrinking page]: {{ site.baseurl }}/shrinking.html
[playlists]: {{ site.baseurl }}/playlists.html
[DASH]: {{ site.baseurl }}/dash.html
[SmoothStreaming]: {{ site.baseurl }}/smoothstreaming.html
[HLS]: {{ site.baseurl }}/hls.html
[Widevine common encryption]: {{ site.baseurl }}/drm.html
