---
title: DASH
---

{% include_relative _page_fragments/supported-formats-dash.md %}

## Using MediaItem ##

To play a DASH stream, you need to depend on the DASH module.

~~~
implementation 'com.google.android.exoplayer:exoplayer-dash:2.X.X'
~~~
{: .language-gradle}

You can then create a `MediaItem` for a DASH MPD URI and pass it to the player.

~~~
// Create a player instance.
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context).build();
// Set the media item to be played.
player.setMediaItem(MediaItem.fromUri(dashUri));
// Prepare the player.
player.prepare();
~~~
{: .language-java}

If your URI doesn't end with `.mpd`, you can pass `MimeTypes.APPLICATION_MPD`
to `setMimeType` of `MediaItem.Builder` to explicitly indicate the type of the
content.

ExoPlayer will automatically adapt between representations defined in the
manifest, taking into account both available bandwidth and device capabilities.

## Using DashMediaSource ##

For more customization options, you can create a `DashMediaSource` and pass it
directly to the player instead of a `MediaItem`.

~~~
// Create a data source factory.
DataSource.Factory dataSourceFactory = new DefaultHttpDataSourceFactory();
// Create a DASH media source pointing to a DASH manifest uri.
MediaSource mediaSource =
    new DashMediaSource.Factory(dataSourceFactory)
        .createMediaSource(MediaItem.fromUri(dashUri));
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
For DASH you should cast the returned object to `DashManifest`. The
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
          DashManifest dashManifest = (DashManifest) manifest;
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
