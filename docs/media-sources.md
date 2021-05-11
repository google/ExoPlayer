---
title: Media sources
redirect_from:
  - /mediasource.html
---

In ExoPlayer every piece of media is represented by a `MediaItem`. However
internally, the player needs `MediaSource` instances to play the content. The
player creates these from media items using a `MediaSourceFactory`.

By default the player uses a `DefaultMediaSourceFactory`, which can create
instances of the following content `MediaSource` implementations:

* `DashMediaSource` for [DASH][].
* `SsMediaSource` for [SmoothStreaming][].
* `HlsMediaSource` for [HLS][].
* `ProgressiveMediaSource` for [regular media files][].
* `RtspMediaSource` for [RTSP][].

`DefaultMediaSourceFactory` can also create more complex media sources depending
on the properties of the corresponding media items. This is described in more
detail on the [Media items page]({{ site.baseurl }}/media-items.html).

For apps that need media source setups that are not supported by the
default configuration of the player, there are several options for
customization.

## Customizing media source creation ##

When building the player, a `MediaSourceFactory` can be injected. For example, if
an app wants to insert ads and use a `CacheDataSource.Factory` to support
caching, an instance of `DefaultMediaSourceFactory` can be configured to match
these requirements and injected during player construction:

~~~
MediaSourceFactory mediaSourceFactory =
    new DefaultMediaSourceFactory(cacheDataSourceFactory)
        .setAdsLoaderProvider(adsLoaderProvider)
        .setAdViewProvider(playerView);
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context)
    .setMediaSourceFactory(mediaSourceFactory)
    .build();
~~~
{: .language-java}

The
[`DefaultMediaSourceFactory` JavaDoc]({{ site.baseurl }}/doc/reference/com/google/android/exoplayer2/source/DefaultMediaSourceFactory.html)
describes the available options in more detail.

It's also possible to inject a custom `MediaSourceFactory` implementation, for
example to support creation of a custom media source type. The factory's
`createMediaSource(MediaItem)` will be called to create a media source for each
media item that is
[added to the playlist]({{ site.baseurl }}/playlists.html).

## Media source based playlist API ##

The [`ExoPlayer`] interface defines additional playlist methods that accept
media sources rather than media items. This makes it possible to bypass the
player's internal `MediaSourceFactory` and pass media source instances to the
player directly:

~~~
// Set a list of media sources as initial playlist.
exoPlayer.setMediaSources(listOfMediaSources);
// Add a single media source.
exoPlayer.addMediaSource(anotherMediaSource);

// Can be combined with the media item API.
exoPlayer.addMediaItem(/* index= */ 3, MediaItem.fromUri(videoUri));

exoPlayer.prepare();
exoPlayer.play();
~~~
{: .language-java}

[DASH]: {{ site.baseurl }}/dash.html
[SmoothStreaming]: {{ site.baseurl }}/smoothstreaming.html
[HLS]: {{ site.baseurl }}/hls.html
[regular media files]: {{ site.baseurl }}/progressive.html
[`ExoPlayer`]: {{ site.baseurl }}/doc/reference/com/google/android/exoplayer2/ExoPlayer.html
