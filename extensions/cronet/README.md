# Cronet DataSource module

This module provides an [HttpDataSource][] implementation that uses [Cronet][].

Cronet is the Chromium network stack made available to Android apps as a
library. It takes advantage of multiple technologies that reduce the latency and
increase the throughput of the network requests that your app needs to work. It
natively supports the HTTP, HTTP/2, and HTTP/3 over QUIC protocols. Cronet is
used by some of the world's biggest streaming applications, including YouTube,
and is our recommended network stack for most use cases.

[HttpDataSource]: https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/upstream/HttpDataSource.html
[Cronet]: https://developer.android.com/guide/topics/connectivity/cronet

## Getting the module

The easiest way to get the module is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:extension-cronet:2.X.X'
```

where `2.X.X` is the version, which must match the version of the other media
modules being used.

Alternatively, you can clone this GitHub project and depend on the module
locally. Instructions for doing this can be found in the [top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md

## Using the module

Media components request data through `DataSource` instances. These instances
are obtained from instances of `DataSource.Factory`, which are instantiated and
injected from application code.

If your application only needs to play http(s) content, using the Cronet
extension is as simple as updating `DataSource.Factory` instantiations in your
application code to use `CronetDataSource.Factory`. If your application also
needs to play non-http(s) content such as local files, use:

```
new DefaultDataSource.Factory(
    ...
    /* baseDataSourceFactory= */ new CronetDataSource.Factory(...) );
```

## Cronet implementations

To instantiate a `CronetDataSource.Factory` you'll need a `CronetEngine`. A
`CronetEngine` can be obtained from one of a number of Cronet implementations.
It's recommended that an application should only have a single `CronetEngine`
instance.

### Available implementations

#### Google Play Services

By default, this module depends on
`com.google.android.gms:play-services-cronet`, which loads an implementation of
Cronet from Google Play Services. When Google Play Services is available, this
approach is beneficial because:

* The increase in application size is negligible.
* The implementation is updated automatically by Google Play Services.

The disadvantage of this approach is that the implementation is not usable on
devices that do not have Google Play Services. Unless your application also
includes one of the alternative Cronet implementations described below, you will
not be able to instantiate a `CronetEngine` in this case. Your application code
should handle this by falling back to use `DefaultHttpDataSource` instead.

#### Cronet Embedded

Cronet Embedded bundles a full Cronet implementation directly into your
application. To use it, add an additional dependency on
`org.chromium.net:cronet-embedded`. Cronet Embedded adds approximately 8MB to
your application, and so we do not recommend it for most use cases. That said,
use of Cronet Embedded may be appropriate if:

* A large percentage of your users are in markets where Google Play Services is
  not widely available.
* You want to control the exact version of the Cronet implementation being used.

#### Cronet Fallback

There's also a fallback implementation of Cronet, which uses Android's default
network stack under the hood. It can be used by adding a dependency on
`org.chromium.net:cronet-fallback`. This implementation should *not* be used
with `CronetDataSource`, since it's more efficient to use
`DefaultHttpDataSource` directly in this case.

When using Cronet Fallback for other networking in your application, use the
more advanced approach to instantiating a `CronetEngine` described below so that
you know when your application's `CronetEngine` has been obtained from the
fallback implementation. In this case, avoid `CronetDataSource` and use
`DefaultHttpDataSource` instead.

### CronetEngine instantiation

Cronet's [Send a simple request][] page documents the simplest way of building a
`CronetEngine`, which is suitable if your application is only using the
Google Play Services implementation of Cronet.

For cases where your application also includes one of the other Cronet
implementations, you can use `CronetProvider.getAllProviders` to list the
available implementations. Providers can be identified by name:

* `CronetProviderInstaller.PROVIDER_NAME`: Google Play Services implementation.
* `CronetProvider.PROVIDER_NAME_APP_PACKAGED`: Embedded implementation.
* `CronetProvider.PROVIDER_NAME_FALLBACK`: Fallback implementation.

This makes it possible to iterate through the providers in your own order of
preference, trying to build a `CronetEngine` from each in turn using
`CronetProvider.createBuilder()` until one has been successfully created. This
approach also allows you to determine when the `CronetEngine` has been obtained
from Cronet Fallback, in which case you can avoid using `CronetDataSource`
whilst still using Cronet Fallback for other networking performed by your
application.

[Send a simple request]: https://developer.android.com/guide/topics/connectivity/cronet/start

## Links

* [Javadoc][]

[Javadoc]: https://exoplayer.dev/doc/reference/index.html
