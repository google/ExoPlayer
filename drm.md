---
title: Digital rights management
---

ExoPlayer uses Android's [`MediaDrm`][] API to support DRM protected playbacks.
The minimum Android versions required for different supported DRM schemes, along
with the streaming formats for which they're supported, are:

| DRM scheme | Android version number | Android API level | Supported formats |
|---------|:------------:|:------------:|:---------------------|
| Widevine "cenc" | 4.4 | 19 | DASH, HLS (FMP4 only) |
| Widevine "cbcs", "cbc1" and "cens" | 7.1 | 25 | DASH, HLS (FMP4 only) |
| ClearKey | 5.0 | 21 | DASH |
| PlayReady SL2000 | AndroidTV | AndroidTV | DASH, SmoothStreaming, HLS (FMP4 only) |

In order to play DRM protected content with ExoPlayer, your app must inject a
`DrmSessionManager` when instantiating the player. `ExoPlayerFactory` provides
methods that allow this.

The library provides a default implementation of `DrmSessionManager`, called
`DefaultDrmSessionManager`, that's suitable for most use cases. `PlayerActivity`
in the [main demo app][] demonstrates how a `DefaultDrmSessionManager` can be
created and injected when instantiating the player.

For some use cases additional configuration may be necessary, as outlined in the
sections below.

### Key rotation ###

To play streams with rotating keys, it's necessary to set the `multiSession`
constructor argument to `true` when instantiating `DefaultDrmSessionManager`.

{% include known-issue-box.html issue-id="4133" description="There may be a
slight pause in playback when key rotation occurs." %}

{% include known-issue-box.html issue-id="3561" description="On API level 22
and below, the output surface may flicker when key rotation occurs." %}

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
server configuration by setting the `multiSession` constructor argument to
`true` when instantiating `DefaultDrmSessionManager`.

We do not recommend configuring your license server to behave in this way. It
requires extra license requests to play multi-key content, which is less
efficient and robust than the alternative described above.

{% include known-issue-box.html issue-id="4133" description="When using this
license server configuration, there may be a slight pause in playback when
adapting between streams that use different keys." %}

### Offline keys ###

An offline key set can be loaded into `DefaultDrmSessionManager` by setting the
`offlineLicenseKeySetId` constructor argument. This allows playback using the
keys stored in the offline key set with the specified id.

{% include known-issue-box.html issue-id="3872" description="Only one offline
key set can be specified per playback. As a result, offline playback of
multi-key content is currently supported only when the license server is
configured as described in Case 1 above." %}

[main demo app]: {{ site.release_v2 }}/demos/main
[`MediaDrm`]: {{ site.android_sdk }}/android/media/MediaDrm.html
