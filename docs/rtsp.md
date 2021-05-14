---
title: RTSP
---

{% include_relative _page_fragments/supported-formats-rtsp.md %}

## Using MediaItem ##

To play an RTSP stream, you need to depend on the RTSP module.

~~~
implementation 'com.google.android.exoplayer:exoplayer-rtsp:2.X.X'
~~~
{: .language-gradle}

You can then create a `MediaItem` for an RTSP URI and pass it to the player.

~~~
// Create a player instance.
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context).build();
// Set the media item to be played.
player.setMediaItem(MediaItem.fromUri(rtspUri));
// Prepare the player.
player.prepare();
~~~
{: .language-java}


## Using RtspMediaSource ##

For more customization options, you can create an `RtspMediaSource` and pass it
directly to the player instead of a `MediaItem`.

~~~
// Create an RTSP media source pointing to an RTSP uri.
MediaSource mediaSource =
    new RtspMediaSource.Factory()
        .createMediaSource(MediaItem.fromUri(rtspUri));
// Create a player instance.
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context).build();
// Set the media source to be played.
player.setMediaSource(mediaSource);
// Prepare the player.
player.prepare();
~~~
{: .language-java}

## Using RTSP behind a NAT ##

ExoPlayer uses UDP as the default protocol for RTP transport.

When streaming RTSP behind a NAT layer, the NAT might not be able to forward the
incoming RTP/UDP packets to the device. This occurs if the NAT lacks the
necessary UDP port mapping. If ExoPlayer detects there have not been incoming
RTP packets for a while and the playback has not started yet, ExoPlayer tears
down the current RTSP playback session, and retries playback using RTP-over-RTSP
(transmitting RTP packets using the TCP connection opened for RTSP).
