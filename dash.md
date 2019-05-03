---
title: DASH
---

{% include_relative supported-formats-dash.md %}

## Creating a MediaSource ##

To play a DASH stream, create a `DashMediaSource` and prepare the player with
it as usual.

~~~
// Create a data source factory.
DataSource.Factory dataSourceFactory =
    new DefaultHttpDataSourceFactory(Util.getUserAgent(context, "app-name"));
// Create a DASH media source pointing to a DASH manifest uri.
MediaSource mediaSource = new DashMediaSource.Factory(dataSourceFactory)
    .createMediaSource(dashUri);
// Create a player instance.
SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(context);
// Prepare the player with the DASH media source.
player.prepare(mediaSource);
~~~
{: .language-java}

ExoPlayer will automatically adapt between representations defined in the
manifest, taking into account both available bandwidth and device capabilities.

## Accessing the manifest ##

You can retrieve the current manifest by calling `Player.getCurrentManifest`.
For DASH you should cast the returned object to `DashManifest`. The
`onTimelineChanged` callback of `Player.EventListener` is also called whenever
the manifest is loaded. This will happen once for a on-demand content, and
possibly many times for live content. The code snippet below shows how an app
can do something whenever the manifest is loaded.

~~~
player.addListener(new Player.EventListener() {
  @Override
  public void onTimelineChanged(
      Timeline timeline, Object manifest, int reason) {
    if (manifest != null) {
      DashManifest dashManifest = (DashManifest) manifest;
      // Do something with the manifest.
    }
  }
 });
~~~
{: .language-java}

## Sideloading a manifest ##

For specifc use cases there is an alternative way to provide the manifest, by
passing a `DashManifest` object to the constructor.

~~~
DataSource.Factory dataSourceFactory =
    new DefaultHttpDataSourceFactory(Util.getUserAgent(context, "app-name"));
// Create a dash media source with a dash manifest.
MediaSource mediaSource = new DashMediaSource.Factory(dataSourceFactory)
    .createMediaSource(dashManifest);
// Create a player instance which gets an adaptive track selector by default.
SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(context);
// Prepare the player with the dash media source.
player.prepare(mediaSource);
~~~
{: .language-java}

## Customizing DASH playback ##

ExoPlayer provides multiple ways for you to tailor playback experience to your
app's needs. The following sections briefly document some of the customization
options available when building a `DashMediaSource`. See the
[Customization page][] for more general customization options.

### Customizing server interactions ###

Some apps may want to intercept HTTP requests and responses. You may want to
inject custom request headers, read the server's response headers, modify the
requests' URIs, etc. For example, your app may authenticate itself by injecting
a token as a header when requesting the media segments. You can achieve these
behaviors by injecting custom [HttpDataSources][] into the `DashMediaSource` you
create. The following snippet shows an example of header injection:

~~~
DashMediaSource dashMediaSource =
    new DashMediaSource.Factory(
            () -> {
              HttpDataSource dataSource =
                  new DefaultHttpDataSource(
                      "ExoPlayer",
                      /* contentTypePredicate= */ null);
              // Set a custom authentication request header.
              dataSource.setRequestProperty("Header", "Value");
              return dataSource;
            })
        .createMediaSource(uri);
~~~
{: .language-java}

### Customizing error handling ###

Implementing a custom [LoadErrorHandlingPolicy][] allows apps to customize the
way ExoPlayer reacts to load errors. For example, an app may want fail fast
instead of retrying many times, or may want to customize the back-off logic that
controls how long the player waits between each retry. The following snippet
shows how to implement custom back-off logic when creating a `DashMediaSource`:

~~~
DashMediaSource =
    new DashMediaSource.Factory(dataSourceFactory)
        .setLoadErrorHandlingPolicy(
            new DefaultLoadErrorHandlingPolicy() {
              @Override
              public long getRetryDelayMsFor(...) {
                // Implement custom back-off logic here.
              }
            })
        .createMediaSource(uri);
~~~
{: .language-java}

You will find more information in our [Medium post about error handling][].

{% include_relative behind-live-window.md %}

[Customization page]: {{ site.baseurl }}/customization.html
[HttpDataSources]: {{ site.exo_sdk }}/upstream/HttpDataSource.html
[LoadErrorHandlingPolicy]: {{ site.exo_sdk }}/upstream/LoadErrorHandlingPolicy.html
[Medium post about error handling]: https://medium.com/google-exoplayer/load-error-handling-in-exoplayer-488ab6908137
