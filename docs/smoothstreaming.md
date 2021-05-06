---
title: SmoothStreaming
---

{% include_relative _page_fragments/supported-formats-smoothstreaming.md %}

## Using MediaItem ##

To play a SmoothStreaming stream, you need to depend on the SmoothStreaming
module.

~~~
implementation 'com.google.android.exoplayer:exoplayer-smoothstreaming:2.X.X'
~~~
{: .language-gradle}

You can then create a `MediaItem` for a SmoothStreaming manifest URI and pass it
to the player.

~~~
// Create a player instance.
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context).build();
// Set the media item to be played.
player.setMediaItem(MediaItem.fromUri(ssUri));
// Prepare the player.
player.prepare();
~~~
{: .language-java}

If your URI doesn't end with `.ism/Manifest`, you can pass
`MimeTypes.APPLICATION_SS` to `setMimeType` of `MediaItem.Builder` to explicitly
indicate the type of the content.

ExoPlayer will automatically adapt between representations defined in the
manifest, taking into account both available bandwidth and device capabilities.

## Using SsMediaSource ##

For more customization options, you can create a `SsMediaSource` and pass it
directly to the player instead of a `MediaItem`.

~~~
// Create a data source factory.
DataSource.Factory dataSourceFactory = new DefaultHttpDataSourceFactory();
// Create a SmoothStreaming media source pointing to a manifest uri.
MediaSource mediaSource =
    new SsMediaSource.Factory(dataSourceFactory)
        .createMediaSource(MediaItem.fromUri(ssUri));
// Create a player instance.
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context).build();
// Set the media source to be played.
player.setMediaSource(mediaSource);
// Prepare the player.
player.prepare();
~~~
{: .language-java}

## Accessing the manifest ##

You can retrieve the current manifest by calling `Player.getCurrentManifest`.
For SmoothStreaming you should cast the returned object to `SsManifest`. The
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
          SsManifest ssManifest = (SsManifest) manifest;
          // Do something with the manifest.
        }
      }
    });
~~~
{: .language-java}

## Customizing playback ##

ExoPlayer provides multiple ways for you to tailor playback experience to your
app's needs. See the [Customization page][] for examples.

[Customization page]: {{ site.baseurl }}/customization.html
