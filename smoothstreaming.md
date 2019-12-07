---
title: SmoothStreaming
---

{% include_relative supported-formats-smoothstreaming.md %}

## Creating a media source ##

To play a SmoothStreaming stream, create a `SsMediaSource` and prepare the
player with it as usual.

~~~
// Create a data source factory.
DataSource.Factory dataSourceFactory =
    new DefaultHttpDataSourceFactory(Util.getUserAgent(context, "app-name"));
// Create a SmoothStreaming media source pointing to a manifest uri.
MediaSource mediaSource =
    new SsMediaSource.Factory(dataSourceFactory).createMediaSource(ssUri);
// Create a player instance.
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context).build();
// Prepare the player with the media source.
player.prepare(mediaSource);
~~~
{: .language-java}

ExoPlayer will automatically adapt between streams defined in the manifest,
taking into account both available bandwidth and device capabilities.

## Accessing the manifest ##

You can retrieve the current manifest by calling `Player.getCurrentManifest`.
For SmoothStreaming you should cast the returned object to `SsManifest`. The
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
          SsManifest ssManifest = (SsManifest) manifest;
          // Do something with the manifest.
        }
      }
    });
~~~
{: .language-java}

## Sideloading a manifest ##

For specifc use cases there is an alternative way to provide the manifest, by
passing a `SsManifest` object to the constructor.

~~~
DataSource.Factory dataSourceFactory =
    new DefaultHttpDataSourceFactory(Util.getUserAgent(context, "app-name"));
// Create a smooth streaming media source with a smooth streaming  manifest.
MediaSource mediaSource =
    new SsMediaSource.Factory(dataSourceFactory).createMediaSource(ssManifest);
// Create a player instance which gets an adaptive track selector by default.
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context).build();
// Prepare the player with the media source.
player.prepare(mediaSource);
~~~
{: .language-java}

## Customizing SmoothStreaming playback ##

ExoPlayer provides multiple ways for you to tailor playback experience to your
app's needs. The following sections briefly document some of the customization
options available when building a `SsMediaSource`. See the
[Customization page][] for more general customization options.

### Customizing server interactions ###

Some apps may want to intercept HTTP requests and responses. You may want to
inject custom request headers, read the server's response headers, modify the
requests' URIs, etc. For example, your app may authenticate itself by injecting
a token as a header when requesting the media segments.

The following example demonstrates how to implement these behaviors by
injecting custom [HttpDataSources][] into an `SsMediaSource`:

~~~
SsMediaSource ssMediaSource =
    new SsMediaSource.Factory(
            () -> {
              HttpDataSource dataSource = new DefaultHttpDataSource(userAgent);
              // Set a custom authentication request header.
              dataSource.setRequestProperty("Header", "Value");
              return dataSource;
            })
        .createMediaSource(ssUri);
~~~
{: .language-java}

In the code snippet above, the injected `HttpDataSource` includes the header
`"Header: Value"` in every HTTP request triggered by `ssMediaSource`. This
behavior is *fixed* for every interaction of `ssMediaSource` with an HTTP
source.

For a more granular approach, you can inject just-in-time behavior using a
`ResolvingDataSource`. The following code snippet shows how to inject
request headers just before interacting with an HTTP source:

~~~
SsMediaSource ssMediaSource =
    new SsMediaSource.Factory(
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
SsMediaSource ssMediaSource =
    new SsMediaSource.Factory(
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
shows how to implement custom back-off logic when creating a `SsMediaSource`:

~~~
SsMediaSource ssMediaSource =
    new SsMediaSource.Factory(dataSourceFactory)
        .setLoadErrorHandlingPolicy(
            new DefaultLoadErrorHandlingPolicy() {
              @Override
              public long getRetryDelayMsFor(...) {
                // Implement custom back-off logic here.
              }
            })
        .createMediaSource(ssUri);
~~~
{: .language-java}

You will find more information in our [Medium post about error handling][].

{% include_relative behind-live-window.md %}

[Customization page]: {{ site.baseurl }}/customization.html
[HttpDataSources]: {{ site.exo_sdk }}/upstream/HttpDataSource.html
[LoadErrorHandlingPolicy]: {{ site.exo_sdk }}/upstream/LoadErrorHandlingPolicy.html
[Medium post about error handling]: https://medium.com/google-exoplayer/load-error-handling-in-exoplayer-488ab6908137
