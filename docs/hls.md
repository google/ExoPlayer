---
title: HLS
---

This documentation may be out-of-date. Please refer to the
[documentation for the latest ExoPlayer release][] on developer.android.com.
{:.info}

{% include_relative _page_fragments/supported-formats-hls.md %}

## Using MediaItem ##

To play an HLS stream, you need to depend on the HLS module.

~~~
implementation 'com.google.android.exoplayer:exoplayer-hls:2.X.X'
~~~
{: .language-gradle}

You can then create a `MediaItem` for an HLS playlist URI and pass it to the
player.

~~~
// Create a player instance.
ExoPlayer player = new ExoPlayer.Builder(context).build();
// Set the media item to be played.
player.setMediaItem(MediaItem.fromUri(hlsUri));
// Prepare the player.
player.prepare();
~~~
{: .language-java}

If your URI doesn't end with `.m3u8`, you can pass `MimeTypes.APPLICATION_M3U8`
to `setMimeType` of `MediaItem.Builder` to explicitly indicate the type of the
content.

The URI of the media item may point to either a media playlist or a multivariant
playlist. If the URI points to a multivariant playlist that declares multiple
`#EXT-X-STREAM-INF` tags then ExoPlayer will automatically adapt between
variants, taking into account both available bandwidth and device capabilities.

## Using HlsMediaSource ##

For more customization options, you can create a `HlsMediaSource` and pass it
directly to the player instead of a `MediaItem`.

~~~
// Create a data source factory.
DataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
// Create a HLS media source pointing to a playlist uri.
HlsMediaSource hlsMediaSource =
    new HlsMediaSource.Factory(dataSourceFactory)
        .createMediaSource(MediaItem.fromUri(hlsUri));
// Create a player instance.
ExoPlayer player = new ExoPlayer.Builder(context).build();
// Set the media source to be played.
player.setMediaSource(hlsMediaSource);
// Prepare the player.
player.prepare();
~~~
{: .language-java}

## Accessing the manifest ##

You can retrieve the current manifest by calling `Player.getCurrentManifest`.
For HLS you should cast the returned object to `HlsManifest`. The
`onTimelineChanged` callback of `Player.Listener` is also called whenever
the manifest is loaded. This will happen once for a on-demand content, and
possibly many times for live content. The code snippet below shows how an app
can do something whenever the manifest is loaded.

~~~
player.addListener(
    new Player.Listener() {
      @Override
      public void onTimelineChanged(
          Timeline timeline, @Player.TimelineChangeReason int reason) {
        Object manifest = player.getCurrentManifest();
        if (manifest != null) {
          HlsManifest hlsManifest = (HlsManifest) manifest;
          // Do something with the manifest.
        }
      }
    });
~~~
{: .language-java}

## Customizing playback ##

ExoPlayer provides multiple ways for you to tailor playback experience to your
app's needs. See the [Customization page][] for examples.

### Disabling chunkless preparation ###

By default, ExoPlayer will use chunkless preparation. This means that ExoPlayer
will only use the information in the multivariant playlist to prepare the
stream, which works if the `#EXT-X-STREAM-INF` tags contain the `CODECS`
attribute.

You may need to disable this feature if your media segments contain muxed
closed-caption tracks that are not declared in the multivariant playlist with a
`#EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS` tag. Otherwise, these closed-caption tracks
won't be detected and played. You can disable chunkless preparation in the
`HlsMediaSource.Factory` as shown in the following snippet. Note that this
will increase start up time as ExoPlayer needs to download a media segment to
discover these additional tracks and it is preferable to declare the
closed-caption tracks in the multivariant playlist instead.
~~~
HlsMediaSource hlsMediaSource =
    new HlsMediaSource.Factory(dataSourceFactory)
        .setAllowChunklessPreparation(false)
        .createMediaSource(MediaItem.fromUri(hlsUri));
~~~
{: .language-java}

## Creating high quality HLS content ##

In order to get the most out of ExoPlayer, there are certain guidelines you can
follow to improve your HLS content. Read our [Medium post about HLS playback in
ExoPlayer][] for a full explanation. The main points are:

* Use precise segment durations.
* Use a continuous media stream; avoid changes in the media structure across
  segments.
* Use the `#EXT-X-INDEPENDENT-SEGMENTS` tag.
* Prefer demuxed streams, as opposed to files that include both video and audio.
* Include all information you can in the Multivariant Playlist.

The following guidelines apply specifically for live streams:

* Use the `#EXT-X-PROGRAM-DATE-TIME` tag.
* Use the `#EXT-X-DISCONTINUITY-SEQUENCE` tag.
* Provide a long live window. One minute or more is great.

[documentation for the latest ExoPlayer release]: https://developer.android.com/guide/topics/media/exoplayer/hls
[HlsMediaSource]: {{ site.exo_sdk }}/source/hls/HlsMediaSource.html
[HTTP Live Streaming]: https://tools.ietf.org/html/rfc8216
[Customization page]: {{ site.baseurl }}/customization.html
[Medium post about HLS playback in ExoPlayer]: https://medium.com/google-exoplayer/hls-playback-in-exoplayer-a33959a47be7
