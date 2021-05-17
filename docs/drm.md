---
title: Digital rights management
---

ExoPlayer uses Android's [`MediaDrm`][] API to support DRM protected playbacks.
The minimum Android versions required for different supported DRM schemes, along
with the streaming formats for which they're supported, are:

| DRM scheme | Android version number | Android API level | Supported formats |
|---------|:------------:|:------------:|:---------------------|
| Widevine "cenc" | 4.4 | 19 | DASH, HLS (FMP4 only) |
| Widevine "cbcs" | 7.1 | 25 | DASH, HLS (FMP4 only) |
| ClearKey "cenc" | 5.0 | 21 | DASH |
| PlayReady SL2000 "cenc" | AndroidTV | AndroidTV | DASH, SmoothStreaming, HLS (FMP4 only) |

In order to play DRM protected content with ExoPlayer, the UUID of the DRM
system and the license server URI should be specified
[when building a media item]({{ site.baseurl }}/media-items.html#protected-content).
The player will then use these properties to build a default implementation of
`DrmSessionManager`, called `DefaultDrmSessionManager`, that's suitable for most
use cases. For some use cases additional DRM properties may be necessary, as
outlined in the sections below.

### Key rotation ###

To play streams with rotating keys, pass `true` to
`MediaItem.Builder.setDrmMultiSession` when building the media item.

### Multi-key content ###

Multi-key content consists of multiple streams, where some streams use different
keys than others. Multi-key content can be played in one of two ways, depending
on how the license server is configured.

##### Case 1: License server responds with all keys for the content #####

In this case, the license server is configured so that when it receives a
request for one key, it responds with all keys for the content. This case is
handled by ExoPlayer without the need for any special configuration. Adaptation
between streams (e.g. SD and HD video) is seamless even if they use different
keys.

Where possible, we recommend configuring your license server to behave in this
way. It's the most efficient and robust way to support playback of multikey
content, because it doesn't require the client to make multiple license requests
to access the different streams.

##### Case 2: License server responds with requested key only #####

In this case, the license server is configured to respond with only the key
specified in the request. Multi-key content can be played with this license
server configuration by passing `true` to `MediaItem.Builder.setDrmMultiSession`
when building the media item.

We do not recommend configuring your license server to behave in this way. It
requires extra license requests to play multi-key content, which is less
efficient and robust than the alternative described above.

### Offline keys ###

An offline key set can be loaded by passing the key set ID to
`MediaItem.Builder.setDrmKeySetId` when building the media item. This
allows playback using the keys stored in the offline key set with the specified
ID.

{% include known-issue-box.html issue-id="3872" description="Only one offline
key set can be specified per playback. As a result, offline playback of
multi-key content is currently supported only when the license server is
configured as described in Case 1 above." %}

### DRM sessions for clear content ###

Use of placeholder `DrmSessions` allows `ExoPlayer` to use the same decoders for
clear content as are used when playing encrypted content. When media contains
both clear and encrypted sections, you may want to use placeholder `DrmSessions`
to avoid re-creation of decoders when transitions between clear and encrypted
sections occur. Use of placeholder `DrmSessions` for audio and video tracks can
be enabled by passing `true` to `MediaItem.Builder.setDrmSessionForClearPeriods`
when building the media item.

### Using a custom DrmSessionManager ###

If an app wants to customise the `DrmSessionManager` used for playback, they can
implement a `DrmSessionManagerProvider` and pass this to the
`MediaSourceFactory` which is [used when building the player]. The provider can
choose whether to instantiate a new manager instance each time or not. To always
use the same instance:

~~~
DrmSessionManager customDrmSessionManager =
    new CustomDrmSessionManager(/* ... */);
// Pass a drm session manager provider to the media source factory.
MediaSourceFactory mediaSourceFactory =
    new DefaultMediaSourceFactory(dataSourceFactory)
        .setDrmSessionManagerProvider(mediaItem -> customDrmSessionManager);
~~~
{: .language-java}

[main demo app]: {{ site.release_v2 }}/demos/main
[`MediaDrm`]: {{ site.android_sdk }}/android/media/MediaDrm.html
[used when building the player]: {{ site.baseurl }}/media-sources.html#customizing-media-source-creation
