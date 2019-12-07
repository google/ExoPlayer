---
title: HLS
---

{% include_relative supported-formats-hls.md %}

## Creating a MediaSource ##

To play an HLS stream, create an `HlsMediaSource` and prepare the player with
it as usual.

~~~
// Create a data source factory.
DataSource.Factory dataSourceFactory =
    new DefaultHttpDataSourceFactory(Util.getUserAgent(context, "app-name"));
// Create a HLS media source pointing to a playlist uri.
HlsMediaSource hlsMediaSource =
    new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
// Create a player instance.
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context).build();
// Prepare the player with the media source.
player.prepare(hlsMediaSource);
~~~
{: .language-java}

The URI passed to `HlsMediaSource.Factory.createMediaSource()` may point to
either a media playlist or a master playlist. If the URI points to a master
playlist that declares multiple `#EXT-X-STREAM-INF` tags then ExoPlayer will
automatically adapt between variants, taking into account both available
bandwidth and device capabilities.

## Accessing the manifest ##

You can retrieve the current manifest by calling `Player.getCurrentManifest`.
For HLS you should cast the returned object to `HlsManifest`. The
`onTimelineChanged` callback of `Player.EventListener` is also called whenever
the manifest is loaded. This will happen once for a on-demand content, and
possibly many times for live content. The code snippet below shows how an app
can do something whenever the manifest is loaded.

~~~
player.addListener(
    new Player.EventListener() {
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

## Customizing HLS playback ##

ExoPlayer provides multiple ways for you to tailor playback experience to your
app's needs. The following sections briefly document some of the customization
options available when building a `HlsMediaSource`. See the
[Customization page][] for more general customization options.

### Enabling faster start-up times ###

You can improve HLS start up times noticeably by enabling chunkless preparation.
When you enable chunkless preparation and `#EXT-X-STREAM-INF` tags contain the
`CODECS` attribute, ExoPlayer will avoid downloading media segments as part of
preparation. The following snippet shows how to enable chunkless preparation:

~~~
HlsMediaSource hlsMediaSource =
    new HlsMediaSource.Factory(dataSourceFactory)
        .setAllowChunklessPreparation(true)
        .createMediaSource(hlsUri);
~~~
{: .language-java}

You can find more details in our [Medium post about chunkless preparation][].

### Customizing server interactions ###

Some apps may want to intercept HTTP requests and responses. You may want to
inject custom request headers, read the server's response headers, modify the
requests' URIs, etc. For example, your app may authenticate itself by injecting
a token as a header when requesting the media segments.

The following example demonstrates how to implement these behaviors by
injecting custom [HttpDataSources][] into an `HlsMediaSource`:

~~~
HlsMediaSource hlsMediaSource =
    new HlsMediaSource.Factory(
            dataType -> {
              HttpDataSource dataSource =
                  new DefaultHttpDataSource(userAgent);
              if (dataType == C.DATA_TYPE_MEDIA) {
                // The data source will be used for fetching media segments. We
                // set a custom authentication request header.
                dataSource.setRequestProperty("Header", "Value");
              }
              return dataSource;
            })
        .createMediaSource(hlsUri);
~~~
{: .language-java}

In the code snippet above, the injected `HttpDataSource` includes the header
`"Header: Value"` in every HTTP request triggered by `hlsMediaSource`. This
behavior is *fixed* for every interaction of `hlsMediaSource` with an HTTP
source.

For a more granular approach, you can inject just-in-time behavior using a
`ResolvingDataSource`. The following code snippet shows how to inject
request headers just before interacting with an HTTP source:

~~~
HlsMediaSource hlsMediaSource =
    new HlsMediaSource.Factory(
        new ResolvingDataSource.Factory(
            new DefaultHttpDataSourceFactory(userAgent),
            // Provide just-in-time request headers.
            (DataSpec dataSpec) ->
                dataSpec.withRequestHeaders(getCustomHeaders(dataSpec.uri))))
        .createMediaSource(customSchemeUri);
~~~
{: .language-java}

You may also use a `ResolvingDataSource`  to perform
just-in-time modifications of the URI, as shown in the following snippet:

~~~
HlsMediaSource hlsMediaSource =
    new HlsMediaSource.Factory(
        new ResolvingDataSource.Factory(
            new DefaultHttpDataSourceFactory(userAgent),
            // Provide just-in-time URI resolution logic.
            (DataSpec dataSpec) -> dataSpec.withUri(resolveUri(dataSpec.uri))))
        .createMediaSource(customSchemeUri);
~~~
{: .language-java}

### Customizing error handling ###

Implementing a custom [LoadErrorHandlingPolicy][] allows apps to customize the
way ExoPlayer reacts to load errors. For example, an app may want fail fast
instead of retrying many times, or may want to customize the back-off logic that
controls how long the player waits between each retry. The following snippet
shows how to implement custom back-off logic when creating a `HlsMediaSource`:

~~~
HlsMediaSource hlsMediaSource =
    new HlsMediaSource.Factory(dataSourceFactory)
        .setLoadErrorHandlingPolicy(
            new DefaultLoadErrorHandlingPolicy() {
              @Override
              public long getRetryDelayMsFor(...) {
                // Implement custom back-off logic here.
              }
            })
        .createMediaSource(hlsUri);
~~~
{: .language-java}

You will find more information in our [Medium post about error handling][].

## Creating high quality HLS content ##

In order to get the most out of ExoPlayer, there are certain guidelines you can
follow to improve your HLS content. Read our [Medium post about HLS playback in
ExoPlayer][] for a full explanation. The main points are:

* Use precise segment durations.
* Use a continues media stream; avoid changes in the media structure across
  segments.
* Use the `#EXT-X-INDEPENDENT-SEGMENTS` tag.
* Prefer demuxed streams, as opposed to files that include both video and audio.
* Include all information you can in the Master Playlist.

The following guidelines apply specifically for live streams:

* Use the `#EXT-X-PROGRAM-DATE-TIME` tag.
* Use the `#EXT-X-DISCONTINUITY-SEQUENCE` tag.
* Provide a long live window. One minute or more is great.

{% include_relative behind-live-window.md %}

[HlsMediaSource]: {{ site.exo_sdk }}/source/hls/HlsMediaSource.html
[HTTP Live Streaming]: https://tools.ietf.org/html/rfc8216
[PlayerView]: {{ site.exo_sdk }}/ui/PlayerView.html
[UI components]: {{ site.baseurl }}/ui-components.html
[Customization page]: {{ site.baseurl }}/customization.html
[HttpDataSources]: {{ site.exo_sdk }}/upstream/HttpDataSource.html
[LoadErrorHandlingPolicy]: {{ site.exo_sdk }}/upstream/LoadErrorHandlingPolicy.html
[Medium post about chunkless preparation]: https://medium.com/google-exoplayer/faster-hls-preparation-f6611aa15ea6
[Medium post about error handling]: https://medium.com/google-exoplayer/load-error-handling-in-exoplayer-488ab6908137
[Medium post about HLS playback in ExoPlayer]: https://medium.com/google-exoplayer/hls-playback-in-exoplayer-a33959a47be7
