---
title: Media items
---

The [playlist API][] is based on `MediaItem`s, which can be conveniently built
using `MediaItem.Builder`. Inside the player, media items are converted into
playable `MediaSource`s by a `MediaSourceFactory`. Without
[custom configuration]({{ site.baseurl }}/media-sources.html#customizing-media-source-creation),
this conversion is carried out by a `DefaultMediaSourceFactory`, which is
capable of building complex media sources corresponding to the properties of the
media item. Some of the properties that can be set on media items are outlined
below.

## Simple media items ##

A media item consisting only of the stream URI can be built with the `fromUri`
convenience method:

~~~
MediaItem mediaItem = MediaItem.fromUri(videoUri);
~~~
{: .language-java}

For all other cases a `MediaItem.Builder` can be used. In the example below, a
media item is built with an ID and some attached metadata:

~~~
MediaItem mediaItem = new MediaItem.Builder()
    .setUri(videoUri)
    .setMediaId(mediaId)
    .setTag(metadata)
    .build();
~~~
{: .language-java}

Attaching metadata can be useful for
[updating your app's UI]({{ site.baseurl }}/playlists.html#detecting-when-playback-transitions-to-another-media-item)
when playlist transitions occur.

## Handling non-standard file extensions

The ExoPlayer library provides adaptive media sources for DASH, HLS and
SmoothStreaming. If the URI of such an adaptive media item ends with a standard
file extension, the corresponding media source is automatically created. If the
URI has a non-standard extension or no extension at all, then the MIME type can
be set explicitly to indicate the type of the media item:

~~~
// Use the explicit MIME type to build an HLS media item.
MediaItem mediaItem = new MediaItem.Builder()
    .setUri(hlsUri)
    .setMimeType(MimeTypes.APPLICATION_M3U8)
    .build();
~~~
{: .language-java}

For progressive media streams a MIME type is not required.

## Protected content ##

For protected content, the media item's DRM properties should be set:

~~~
MediaItem mediaItem = new MediaItem.Builder()
    .setUri(videoUri)
    .setDrmUuid(C.WIDEVINE_UUID)
    .setDrmLicenseUri(licenseUri)
    .setDrmLicenseRequestHeaders(httpRequestHeaders)
    .setDrmMultiSession(true)
    .build();
~~~
{: .language-java}

This example builds a media item for Widevine protected content. Inside the
player, `DefaultMediaSourceFactory` will pass these properties to a
`DrmSessionManagerProvider` to obtain a `DrmSessionManager`, which is then
injected into the created `MediaSource`. DRM behaviour can be
[further customized]({{ site.baseurl }}/drm.html#using-a-custom-drmsessionmanager)
to your needs.

## Sideloading subtitle tracks ##

To sideload subtitle tracks, `MediaItem.Subtitle` instances can be added when
when building a media item:

~~~
MediaItem.Subtitle subtitle =
    new MediaItem.Subtitle(
        subtitleUri,
        MimeTypes.APPLICATION_SUBRIP, // The correct MIME type.
        language, // The subtitle language. May be null.
        selectionFlags); // Selection flags for the track.

MediaItem mediaItem = new MediaItem.Builder()
    .setUri(videoUri)
    .setSubtitles(Lists.newArrayList(subtitle))
    .build();
~~~
{: .language-java}

Internally, `DefaultMediaSourceFactory` will use a `MergingMediaSource` to
combine the content media source with a `SingleSampleMediaSource` for each
subtitle track.

## Clipping a media stream ##

It's possible to clip the content referred to by a media item by setting custom
start and end positions:

~~~
MediaItem mediaItem = new MediaItem.Builder()
    .setUri(videoUri)
    .setClipStartPositionMs(startPositionMs)
    .setClipEndPositionMs(endPositionMs)
    .build();
~~~
{: .language-java}

Internally, `DefaultMediaSourceFactory` will use a `ClippingMediaSource` to wrap
the content media source. There are additional clipping properties. See the
[`MediaItem.Builder` Javadoc][] for more details.

When clipping the start of a video file, try to align the start position with a
keyframe if possible. If the start position is not aligned with a keyframe then
the player will need to decode and discard data from the previous keyframe up to
the start position before playback can begin. This will introduce a short delay
at the start of playback, including when the player transitions to playing a
clipped media source as part of a playlist or due to looping.
{:.info}

## Ad insertion ##

To insert ads, a media item's ad tag URI property should be set:

~~~
MediaItem mediaItem = new MediaItem.Builder()
    .setUri(videoUri)
    .setAdTagUri(adTagUri)
    .build();
~~~
{: .language-java}

Internally, `DefaultMediaSourceFactory` will wrap the content media source in an
`AdsMediaSource` to insert ads as defined by the ad tag. For this to work, the
the player also needs to have its `DefaultMediaSourceFactory`
[configured accordingly]({{ site.baseurl }}/ad-insertion.html#declarative-ad-support).

[playlist API]: {{ site.baseurl }}/playlists.html
[`MediaItem.Builder` Javadoc]: {{ site.baseurl }}/doc/reference/com/google/android/exoplayer2/MediaItem.Builder.html
