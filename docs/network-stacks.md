---
title: Network stacks
---

This documentation may be out-of-date. Please refer to the
[documentation for the latest ExoPlayer release][] on developer.android.com.
{:.info}

ExoPlayer is commonly used for streaming media over the internet. It supports
multiple network stacks for making its underlying network requests. Your choice
of network stack can have a significant impact on streaming performance.

This page outlines how to configure ExoPlayer to use your network stack of
choice, lists the available options, and provides some guidance on how to choose
a network stack for your application.

## Configuring ExoPlayer to use a specific network stack ##

ExoPlayer loads data through `DataSource` components, which it obtains from
`DataSource.Factory` instances that are injected from application code.

If your application only needs to play http(s) content, selecting a network
stack is as simple as updating any `DataSource.Factory` instances that your
application injects to be instances of the `HttpDataSource.Factory`
that corresponds to the network stack you wish to use. If your application also
needs to play non-http(s) content such as local files, use

~~~
new DefaultDataSource.Factory(
    ...
    /* baseDataSourceFactory= */ new PreferredHttpDataSource.Factory(...));
~~~
{: .language-java}

where `PreferredHttpDataSource.Factory` is the factory corresponding to your
preferred network stack. The `DefaultDataSource.Factory` layer adds in support
for non-http(s) sources such as local files.

The example below shows how to build an `ExoPlayer` that will use the Cronet
network stack and also support playback of non-http(s) content.

~~~
// Given a CronetEngine and Executor, build a CronetDataSource.Factory.
CronetDataSource.Factory cronetDataSourceFactory =
    new CronetDataSource.Factory(cronetEngine, executor);

// Wrap the CronetDataSource.Factory in a DefaultDataSource.Factory, which adds
// in support for requesting data from other sources (e.g., files, resources,
// etc).
DefaultDataSource.Factory dataSourceFactory =
    new DefaultDataSource.Factory(
        context,
        /* baseDataSourceFactory= */ cronetDataSourceFactory);

// Inject the DefaultDataSource.Factory when creating the player.
ExoPlayer player =
    new ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            new DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory))
        .build();
~~~
{: .language-java}

## Supported network stacks ##

ExoPlayer provides direct support for Cronet, OkHttp and Android's built-in
network stack. It can also be extended to support any other network stack that
works on Android.

### Cronet ###

[Cronet](https://developer.android.com/guide/topics/connectivity/cronet) is the
Chromium network stack made available to Android apps as a library. It takes
advantage of multiple technologies that reduce the latency and increase the
throughput of the network requests that your app needs to work, including those
made by ExoPlayer. It natively supports the HTTP, HTTP/2, and HTTP/3 over QUIC
protocols. Cronet is used by some of the world's biggest streaming applications,
including YouTube.

ExoPlayer supports Cronet via its
[Cronet extension](https://github.com/google/ExoPlayer/tree/dev-v2/extensions/cronet).
Please see the extension's `README.md` for detailed instructions on how to use
it. Note that the Cronet extension is able to use three underlying Cronet
implementations:

1. **Google Play Services:** We recommend using this implementation in most
  cases, and falling back to Android's built-in network stack
  (i.e., `DefaultHttpDataSource`) if Google Play Services is not available.
1. **Cronet Embedded:** May be a good choice if a large percentage of your users
  are in markets where Google Play Services is not widely available, or if you
  want to control the exact version of the Cronet implementation being used. The
  major disadvantage of Cronet Embedded is that it adds approximately 8MB to
  your application.
1. **Cronet Fallback:** The fallback implementation of Cronet implements
  Cronet's API as a wrapper around Android's built-in network stack. It should
  not be used with ExoPlayer, since using Android's built-in network stack
  directly (i.e., by using `DefaultHttpDataSource`) is more efficient.

### OkHttp ###

[OkHttp](https://square.github.io/okhttp/) is another modern network stack that
is widely used by many popular Android applications. It supports HTTP and
HTTP/2, but does not yet support HTTP/3 over QUIC.

ExoPlayer supports OkHttp via its
[OkHttp extension](https://github.com/google/ExoPlayer/tree/dev-v2/extensions/okhttp).
Please see the extension's `README.md` for detailed instructions on how to use
it. When using the OkHttp extension, the network stack is embedded within the
application. This is similar to Cronet Embedded, however OkHttp is significantly
smaller, adding under 1MB to your application.

### Android's built-in network stack ###

ExoPlayer supports use of Android's built-in network stack with
`DefaultHttpDataSource` and `DefaultHttpDataSource.Factory`, which are part of
the core ExoPlayer library.

The exact network stack implementation depends on the software running on the
underlying device. On most devices (as of 2021) only HTTP is supported (i.e.,
HTTP/2 and HTTP/3 over QUIC are not supported).

### Other network stacks ###

It's possible for applications to integrate other network stacks with ExoPlayer.
To do this, implement an `HttpDataSource` that wraps the network stack,
together with a corresponding `HttpDataSource.Factory`. ExoPlayer's Cronet and
OkHttp extensions are good examples of how to do this.

When integrating with a pure Java network stack, it's a good idea to implement a
`DataSourceContractTest` to check that your `HttpDataSource` implementation
behaves correctly. `OkHttpDataSourceContractTest` in the OkHttp extension is a
good example of how to do this.

## Choosing a network stack ##

The table below outlines the pros and cons of the network stacks supported by
ExoPlayer.

| Network stack | Protocols | APK size impact | Notes |
|:---|:--:|:--:|:---|
| Cronet (Google Play Services) | HTTP<br>HTTP/2<br>HTTP/3&nbsp;over&nbsp;QUIC | Small<br>(<100KB) | Requires Google Play Services. Cronet version updated automatically |
| Cronet (Embedded) | HTTP<br>HTTP/2<br>HTTP/3&nbsp;over&nbsp;QUIC | Large<br>(~8MB) | Cronet version controlled by app developer |
| Cronet (Fallback) | HTTP<br>(varies&nbsp;by&nbsp;device) | Small<br>(<100KB) | Not recommended for ExoPlayer |
| OkHttp | HTTP<br>HTTP/2 | Small<br>(<1MB) | Requires Kotlin runtime |
| Built-in network stack | HTTP<br>(varies&nbsp;by&nbsp;device) | None | Implementation varies by device |

The HTTP/2 and HTTP/3 over QUIC protocols can significantly improve media
streaming performance. In particular when streaming adaptive media distributed
via a content distribution network (CDN), there are cases for which use of these
protocols can allow CDNs to operate much more efficiently. For this reason,
Cronet's support for both HTTP/2 and HTTP/3 over QUIC (and OkHttp's support for
HTTP/2), is a major benefit compared to using Android's built-in network stack,
provided the servers on which the content is hosted also support these
protocols.

When considering media streaming in isolation, we recommend use of Cronet
provided by Google Play Services, falling back to `DefaultHttpDataSource` if
Google Play Services is unavailable. This recommendation strikes a good balance
between enabling use of HTTP/2 and HTTP/3 over QUIC on most devices, and
avoiding a significant increase in APK size. There are exceptions to this
recommendation. For cases where Google Play Services is likely to be unavailable
on a significant fraction of devices that will be running your application,
using Cronet Embedded or OkHttp may be more appropriate. Use of the built-in
network stack may be acceptable if APK size is a critical concern, or if media
streaming is only a minor part of your application's functionality.

Beyond just media, it's normally a good idea to choose a single network stack
for all of the networking performed by your application. This allows resources
(e.g., sockets) to be efficiently pooled and shared between ExoPlayer and other
application components.

To assist with resource sharing, it's recommended to use a single `CronetEngine`
or `OkHttpClient` instance throughout your application, when using Cronet or
OkHttp respectively.
{:.info}

Since your application will most likely need to perform networking not related
to media playback, your choice of network stack should ultimately factor in our
recommendations above for media streaming in isolation, the requirements of any
other components that perform networking, and their relative importance to your
application.

[documentation for the latest ExoPlayer release]: https://developer.android.com/guide/topics/media/exoplayer/network-stacks
