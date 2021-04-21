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

* The player can expose metadata and functionality relating to ads via its API.
* [ExoPlayer UI components][] can show markers for ad positions automatically,
  and change their behavior depending on whether ad is playing.
* Internally, the player can keep a consistent buffer across transitions between
  ads and content.

In this setup, the player takes care of switching between ads and content, which
means that apps don't need to take care of controlling multiple separate
background/foreground players for ads and content.

When preparing content videos and ad tags for use with client-side ad insertion,
ads should ideally be positioned at synchronization samples (keyframes) in the
content video so that the player can resume content playback seamlessly.

### Declarative ad support ###

An ad tag URI can be specified when building a `MediaItem`:

~~~
MediaItem mediaItem =
    new MediaItem.Builder().setUri(videoUri).setAdTagUri(adTagUri).build();
~~~
{: .language-java}

To enable player support for media items that specify ad tags, it's necessary to
build and inject a `DefaultMediaSourceFactory` configured with an
`AdsLoaderProvider` and an `AdViewProvider` when creating the player:

~~~
MediaSourceFactory mediaSourceFactory =
    new DefaultMediaSourceFactory(context)
        .setAdsLoaderProvider(adsLoaderProvider)
        .setAdViewProvider(playerView);
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context)
    .setMediaSourceFactory(mediaSourceFactory)
    .build();
~~~
{: .language-java}

Internally, `DefaultMediaSourceFactory` will wrap the content media source in an
`AdsMediaSource`. The `AdsMediaSource` will obtain an `AdsLoader` from the
`AdsLoaderProvider` and use it to insert ads as defined by the media item's ad
tag.

ExoPlayer's `StyledPlayerView` and `PlayerView` UI components both implement
`AdViewProvider`. The IMA extension provides an easy to use `AdsLoader`, as
described below.

### Playlists with ads ###

When playing a [playlist][] with multiple media items, the default behavior is
to request the ad tag and store ad playback state once for each media ID and ad
tag URI combination. This means that users will see ads for every media item
with ads that has a distinct media ID, even if the ad tag URIs match. If a
media item is repeated, the user will see the corresponding ads only once (the
ad playback state stores whether ads have been played, so they are skipped
after their first occurrence).

It's possible to customize this behavior by passing an opaque ads identifier
with which ad playback state for a given media item is linked, based on object
equality. Here is an example where ad playback state is linked to the ad tag
URI only, rather than the combination of the media ID and ad tag URI, by
passing the ad tag URI as the ads identifier. The effect is that ads will load
only once and the user will not see ads on the second item when playing the
playlist from start to finish.

~~~
// Build the media items, passing the same ads identifier for both items,
// which means they share ad playback state so ads play only once.
MediaItem firstItem =
    new MediaItem.Builder()
        .setUri(firstVideoUri)
        .setAdTagUri(adTagUri, /* adsId= */ adTagUri)
        .build();
MediaItem secondItem =
    new MediaItem.Builder()
        .setUri(secondVideoUri)
        .setAdTagUri(adTagUri, /* adsId= */ adTagUri)
        .build();
player.addMediaItem(firstItem);
player.addMediaItem(secondItem);
~~~
{: .language-java}

### IMA extension ###

The [ExoPlayer IMA extension][] provides `ImaAdsLoader`, making it easy to
integrate client-side ad insertion into your app. It wraps the functionality of
the [client-side IMA SDK][] to support insertion of VAST/VMAP ads. For
instructions on how to use the extension, including how to handle backgrounding
and resuming playback, please see the [README][].

The [demo application][] uses the IMA extension, and includes several sample
VAST/VMAP ad tags in the sample list.

#### UI considerations ####

`StyledPlayerView` and `PlayerView` hide controls during playback of ads by
default, but apps can toggle this behavior by calling
`setControllerHideDuringAds`, which is defined on both views. The IMA SDK will
show additional views on top of the player while an ad is playing (e.g., a 'more
info' link and a skip button, if applicable).

Since advertisers expect a consistent experience across apps, the IMA SDK does
not allow customization of the views that it shows while an ad is playing. It is
therefore not possible to remove or reposition the skip button, change the
fonts, or make other customizations to the visual appearance of these views.
{:.info}

The IMA SDK may report whether ads are obscured by application provided views
rendered on top of the player. Apps that need to overlay views that are
essential for controlling playback must register them with the IMA SDK so that
they can be omitted from viewability calculations. When using `StyledPlayerView`
or `PlayerView` as the `AdViewProvider`, they will automatically register their
control overlays. Apps that use a custom player UI must register overlay views
by returning them from `AdViewProvider.getAdOverlayInfos`.

For more information about overlay views, see
[Open Measurement in the IMA SDK][].

#### Companion ads ####

Some ad tags contain additional companion ads that can be shown in 'slots' in an
app UI. These slots can be passed via
`ImaAdsLoader.Builder.setCompanionAdSlots(slots)`. For more information see
[Adding Companion Ads][].

#### Standalone ads ####

The IMA SDK is designed for inserting ads into media content, not for playing
standalone ads by themselves. Hence playback of standalone ads is not supported
by the IMA extension. We recommend using the [Google Mobile Ads SDK][] instead
for this use case.

### Using a third-party ads SDK ###

If you need to load ads via a third-party ads SDK, it's worth checking whether
it already provides an ExoPlayer integration. If not, implementing a custom
`AdsLoader` that wraps the third-party ads SDK is the recommended approach,
since it provides the benefits of `AdsMediaSource` described above.
`ImaAdsLoader` acts as an example implementation.

Alternatively, you can use ExoPlayer's [playlist support][] to build a sequence
of ads and content clips:

~~~
// A pre-roll ad.
MediaItem preRollAd = MediaItem.fromUri(preRollAdUri);
// The start of the content.
MediaItem contentStart =
    new MediaItem.Builder()
        .setUri(contentUri)
        .setClipEndPositionMs(120_000)
        .build();
// A mid-roll ad.
MediaItem midRollAd = MediaItem.fromUri(midRollAdUri);
// The rest of the content
MediaItem contentEnd =
    new MediaItem.Builder()
        .setUri(contentUri)
        .setClipStartPositionMs(120_000)
        .build();

// Build the playlist.
player.addMediaItem(preRollAd);
player.addMediaItem(contentStart);
player.addMediaItem(midRollAd);
player.addMediaItem(contentEnd);
~~~
{: .language-java}

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
[ExoPlayer UI components]: {{ site.baseurl }}/ui-components.html
[ExoPlayer IMA extension]: https://github.com/google/ExoPlayer/tree/release-v2/extensions/ima
[client-side IMA SDK]: https://developers.google.com/interactive-media-ads/docs/sdks/android
[README]: https://github.com/google/ExoPlayer/tree/release-v2/extensions/ima
[demo application]: {{ site.baseurl }}/demo-application.html
[Open Measurement in the IMA SDK]: https://developers.google.com/interactive-media-ads/docs/sdks/android/omsdk
[Adding Companion Ads]: https://developers.google.com/interactive-media-ads/docs/sdks/android/companions
[playlist]: {{ site.baseurl }}/playlists.html
[playlist support]: {{ site.baseurl }}/playlists.html
[incorporating ads into a playlist]: https://developer.apple.com/documentation/http_live_streaming/example_playlists_for_http_live_streaming/incorporating_ads_into_a_playlist
[supported formats]: {{ site.baseurl }}/supported-formats.html
[Google Mobile Ads SDK]: https://developers.google.com/admob/android/quick-start
