---
title: Progressive
---

{% include_relative supported-formats-progressive.md %}

## Creating a MediaSource ##

To play a progressive stream, create a `ProgressiveMediaSource` and prepare the
player with it as usual.

~~~
// Create a data source factory.
DataSource.Factory dataSourceFactory =
    new DefaultHttpDataSourceFactory(Util.getUserAgent(context, "app-name"));
// Create a progressive media source pointing to a stream uri.
MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
    .createMediaSource(progressiveUri);
// Create a player instance.
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context).build();
// Prepare the player with the media source.
player.prepare(mediaSource);
~~~
{: .language-java}

## Customizing progressive playbacks ##

ExoPlayer provides multiple ways for you to tailor playback experience to your
app's needs. The following sections briefly document some of the customization
options available when building a `ProgressiveMediaSource`. See the
[Customization page][] for more general customization options.

### Setting extractor flags ###

Extractor flags can be used to control how individual formats are extracted.
They can be set on a `DefaultExtractorsFactory`, which can then be used when
instantiating a `ProgressiveMediaSource.Factory`. The following example passes a
flag which disables edit list parsing for the MP4 streams.

~~~
DefaultExtractorsFactory extractorsFactory =
    new DefaultExtractorsFactory()
        .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS);
ProgressiveMediaSource progressiveMediaSource =
    new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
        .createMediaSource(progressiveUri);
~~~
{: .language-java}

### Enabling constant bitrate seeking ###

For MP3, ADTS and AMR streams, you can enable approximate seeking using a
constant bitrate assumption with `FLAG_ENABLE_CONSTANT_BITRATE_SEEKING` flags.
These flags can be set for individual extractors using the approach described
above. To enable constant bitrate seeking for all extractors that support it,
use `DefaultExtractorsFactory.setConstantBitrateSeekingEnabled`.

~~~
DefaultExtractorsFactory extractorsFactory =
    new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);
ProgressiveMediaSource progressiveMediaSource =
    new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
        .createMediaSource(progressiveUri);
~~~
{: .language-java}

### Customizing server interactions ###

Some apps may want to intercept HTTP requests and responses. You may want to
inject custom request headers, read the server's response headers, modify the
requests' URIs, etc. For example, your app may authenticate itself by injecting
a token as a header when requesting the media segments.

The following example demonstrates how to implement these behaviors by
injecting custom [HttpDataSources][] into a `ProgressiveMediaSource`:

~~~
ProgressiveMediaSource progressiveMediaSource =
    new ProgressiveMediaSource.Factory(
            () -> {
              HttpDataSource dataSource =
                  new DefaultHttpDataSource(userAgent);
              // Set a custom authentication request header.
              dataSource.setRequestProperty("Header", "Value");
              return dataSource;
            })
        .createMediaSource(progressiveUri);
~~~
{: .language-java}

In the code snippet above, the injected `HttpDataSource` includes the header
`"Header: Value"` in every HTTP request triggered by `progressiveMediaSource`.
This behavior is *fixed* for every interaction of `progressiveMediaSource`
with an HTTP source.

For a more granular approach, you can inject just-in-time behavior using a
`ResolvingDataSource`. The following code snippet shows how to inject
request headers just before interacting with an HTTP source:

~~~
ProgressiveMediaSource progressiveMediaSource =
    new ProgressiveMediaSource.Factory(
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
ProgressiveMediaSource progressiveMediaSource =
    new ProgressiveMediaSource.Factory(
        new ResolvingDataSource.Factory(
            new DefaultHttpDataSourceFactory(userAgent),
            // Provide just-in-time URI resolution logic.
            (DataSpec dataSpec)-> dataSpec.withUri(resolveUri(dataSpec.uri))))
        .createMediaSource(customSchemeUri);
~~~
{: .language-java}

### Customizing error handling ###

Implementing a custom [LoadErrorHandlingPolicy][] allows apps to customize the
way ExoPlayer reacts to load errors. For example, an app may want fail fast
instead of retrying many times, or may want to customize the back-off logic that
controls how long the player waits between each retry. The following snippet
shows how to implement custom back-off logic when creating a
`ProgressiveMediaSource`:

~~~
ProgressiveMediaSource progressiveMediaSource =
    new ProgressiveMediaSource.Factory(dataSourceFactory)
        .setLoadErrorHandlingPolicy(
            new DefaultLoadErrorHandlingPolicy() {
              @Override
              public long getRetryDelayMsFor(...) {
                // Implement custom back-off logic here.
              }
            })
        .createMediaSource(progressiveUri);
~~~
{: .language-java}

You will find more information in our [Medium post about error handling][].

[Customization page]: {{ site.baseurl }}/customization.html
[HttpDataSources]: {{ site.exo_sdk }}/upstream/HttpDataSource.html
[LoadErrorHandlingPolicy]: {{ site.exo_sdk }}/upstream/LoadErrorHandlingPolicy.html
[Medium post about error handling]: https://medium.com/google-exoplayer/load-error-handling-in-exoplayer-488ab6908137
