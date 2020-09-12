---
title: Progressive
---

{% include_relative _page_fragments/supported-formats-progressive.md %}

## Using MediaItem ##

To play a progressive stream, create a `MediaItem` with the media URI and pass
it to the player.

~~~
// Create a player instance.
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context).build();
// Set the media item to be played.
player.setMediaItem(MediaItem.fromUri(progressiveUri));
// Prepare the player.
player.prepare();
~~~
{: .language-java}

## Using ProgressiveMediaSource ##

For more customization options, you can create a `ProgressiveMediaSource` and
directly to the player instead of a `MediaItem`.

~~~
// Create a data source factory.
DataSource.Factory dataSourceFactory = new DefaultHttpDataSourceFactory();
// Create a progressive media source pointing to a stream uri.
MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
    .createMediaSource(MediaItem.fromUri(progressiveUri));
// Create a player instance.
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context).build();
// Set the media source to be played.
player.setMediaSource(mediaSource);
// Prepare the player.
player.prepare();
~~~
{: .language-java}

## Customizing playback ##

ExoPlayer provides multiple ways for you to tailor playback experience to your
app's needs. See the [Customization page][] for examples.

[Customization page]: {{ site.baseurl }}/customization.html
