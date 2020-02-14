---
title: Ad insertion
---

ExoPlayer can be used for both client-side and server-side ad insertion.

## Client-side ad insertion ##

In client-side ad insertion, the player switches between loading media from
different URLs as it transitions between playing content and ads. Information
about ads is loaded separately from the media, such as from an XML [VAST][] or
[VMAP][] ad tag. This can include ad cue positions relative to the start of the
content, the actual ad media URIs and metadata such as whether a given ad is
skippable.

When using ExoPlayer's `AdsMediaSource` for client-side ad insertion, the player
has information about the ads to be played. This has several benefits:
- The player can expose metadata and functionality relating to ads via its API.
- [ExoPlayer UI components][] can show markers for ad positions automatically,
and change behavior depending on whether ad is playing.
- Internally, the player can keep a consistent buffer across transitions between
ads and content.

In this setup, the player takes care of switching between ads and content, which
means that apps don't need to take care of controlling multiple separate
background/foreground players for ads and content.

When preparing content videos and ad tags for use with client-side ad insertion,
ads should ideally be positioned at synchronization samples (keyframes) in the
content video so that the player can resume content playback seamlessly.

### IMA extension ###

The [ExoPlayer IMA extension][] makes it easy to integrate client-side ad
insertion into your app. It wraps the functionality of the [client-side IMA
SDK][] to support playback of VAST/VMAP ad tags. For instructions on how to use
the extension, please see the [README][], which describes how to set up playback
using an `AdsMediaSource` and the extension's `ImaAdsLoader`, and how to handle
backgrounding/resuming playback.

The [demo application][] can also use the IMA extension when it is built using a
build variant with extensions, and includes several sample VAST/VMAP ad tags in
the sample list.

#### UI considerations ####

`PlayerView` will hide controls during playback of ads by default, but apps can
toggle this behavior via `PlayerView.setControllerHideDuringAds(false)`. The IMA
SDK will show additional views on top of the player while an ad is playing
(e.g., a 'more info' link and a skip button, if applicable).

Since advertisers expect a consistent experience across apps, the IMA SDK does
not allow customization of the views that it shows while an ad is playing.
{:.info}

The IMA SDK may report whether ads are obscured by application provided views
rendered on top of the player. Apps that need to overlay views that are
essential for controlling playback must register them with the IMA SDK so that
they can be omitted from viewability calculations. To do that, implement the
`AdsLoader.AdViewProvider` interface and pass the implementation when
constructing the `AdsMediaSource`. `PlayerView` implements this interface to
register its controls overlay. For more information, see [Open Measurement in
the IMA SDK][].

#### Companion ads ####

Some ad tags contain additional companion ads that can be shown in 'slots' in an
app UI. These slots can be passed via
`ImaAdsLoader.getAdDisplayContainer().setCompanionSlots(slots)`. For more
information see [Adding Companion Ads][].

### Using a third-party ads SDK ###

If you need to load ads via a third-party ads SDK, it's worth checking first
whether it already provides an ExoPlayer integration.

If not, implementing a custom `AdsLoader` allows you to take advantage of
`AdsMediaSource`. `ImaAdsLoader` acts as an example implementation.

Alternatively you can use ExoPlayer's [playlist support][] to build a sequence
of ads and content clips. To produce a content clip, wrap the content media
source in a `ClippingMediaSource`, passing the relevant start/end times based on
positions of the preceding/following ads (if any). In this approach the player
doesn't know about ads, but it's still possible show ad markers in ExoPlayer's
PlayerView via `PlayerView.setExtraAdGroupMarkers`.

## Server-side ad insertion ##

In server-side ad insertion (also called dynamic ad insertion, or DAI), the
media stream contains both ads and content. A DASH manifest may point to both
content and ad segments, possibly in separate periods. For HLS, see the Apple
documentation on [incorporating ads into a playlist][].

When using server-side ad insertion the client may need to report tracking
events to an ad SDK or ad server. For example, the media stream may include
timed events that need to be reported by the client (see [supported formats][]
for information on what timed metadata formats are supported by ExoPlayer). Apps
can listen for timed metadata events from the player, e.g., via
`SimpleExoPlayer.addMetadataOutput`.

The IMA extension currently only handles client-side ad insertion. It does not
provide any integration with the DAI part of the IMA SDK.
{:.info}

[VAST]: https://www.iab.com/wp-content/uploads/2015/06/VASTv3_0.pdf
[VMAP]: https://www.iab.com/guidelines/digital-video-multiple-ad-playlist-vmap-1-0-1/
[ExoPlayer UI components]: {{ site.baseurl }}/ui-components.md
[ExoPlayer IMA extension]: https://github.com/google/ExoPlayer/tree/release-v2/extensions/ima
[client-side IMA SDK]: https://developers.google.com/interactive-media-ads/docs/sdks/android
[README]: https://github.com/google/ExoPlayer/tree/release-v2/extensions/ima
[demo application]: {{ site.baseurl }}/demo-application.html
[Open Measurement in the IMA SDK]: https://developers.google.com/interactive-media-ads/docs/sdks/android/omsdk
[Adding Companion Ads]: https://developers.google.com/interactive-media-ads/docs/sdks/android/companions
[playlist support]: {{ site.baseurl }}/playlists.md
[incorporating ads into a playlist]: https://developer.apple.com/documentation/http_live_streaming/example_playlists_for_http_live_streaming/incorporating_ads_into_a_playlist
[supported formats]: {{ site.baseurl }}/supported-formats.html
