# ExoPlayer Cronet extension #

The Cronet extension is an [HttpDataSource][] implementation using [Cronet][].

[HttpDataSource]: https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/upstream/HttpDataSource.html
[Cronet]: https://chromium.googlesource.com/chromium/src/+/master/components/cronet?autodive=0%2F%2F

## Getting the extension ##

The easiest way to use the extension is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:extension-cronet:2.X.X'
```

where `2.X.X` is the version, which must match the version of the ExoPlayer
library being used.

Alternatively, you can clone the ExoPlayer repository and depend on the module
locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

Note that by default, the extension will use the Cronet implementation in
Google Play Services. If you prefer, it's also possible to embed the Cronet
implementation directly into your application. See below for more details.

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md

## Using the extension ##

ExoPlayer requests data through `DataSource` instances. These instances are
either instantiated and injected from application code, or obtained from
instances of `DataSource.Factory` that are instantiated and injected from
application code.

If your application only needs to play http(s) content, using the Cronet
extension is as simple as updating any `DataSource`s and `DataSource.Factory`
instantiations in your application code to use `CronetDataSource` and
`CronetDataSourceFactory` respectively. If your application also needs to play
non-http(s) content such as local files, use
```
new DefaultDataSource(
    ...
    new CronetDataSource(...) /* baseDataSource argument */);
```
and
```
new DefaultDataSourceFactory(
    ...
    new CronetDataSourceFactory(...) /* baseDataSourceFactory argument */);
```
respectively.

## Choosing between Google Play Services Cronet and Cronet Embedded ##

The underlying Cronet implementation is available both via a [Google Play
Services](https://developers.google.com/android/guides/overview) API, and as a
library that can be embedded directly into your application. When you depend on
`com.google.android.exoplayer:extension-cronet:2.X.X`, the library will _not_ be
embedded into your application by default. The extension will attempt to use the
Cronet implementation in Google Play Services. The benefits of this approach
are:

* A negligible increase in the size of your application.
* The Cronet implementation is updated automatically by Google Play Services.

If Google Play Services is not available on a device, `CronetDataSourceFactory`
will fall back to creating `DefaultHttpDataSource` instances, or
`HttpDataSource` instances created by a `fallbackFactory` that you can specify.

It's also possible to embed the Cronet implementation directly into your
application. To do this, add an additional gradle dependency to the Cronet
Embedded library:

```gradle
implementation 'com.google.android.exoplayer:extension-cronet:2.X.X'
implementation 'org.chromium.net:cronet-embedded:XX.XXXX.XXX'
```

where `XX.XXXX.XXX` is the version of the library that you wish to use. The
extension will automatically detect and use the library. Embedding will add
approximately 8MB to your application, however it may be suitable if:

* Your application is likely to be used in markets where Google Play Services is
  not widely available.
* You want to control the exact version of the Cronet implementation being used.

If you do embed the library, you can specify which implementation should
be preferred if the Google Play Services implementation is also available. This
is controlled by a `preferGMSCoreCronet` parameter, which can be passed to the
`CronetEngineWrapper` constructor (GMS Core is another name for Google Play
Services).

## Links ##

* [Javadoc][]: Classes matching `com.google.android.exoplayer2.ext.cronet.*`
  belong to this module.

[Javadoc]: https://exoplayer.dev/doc/reference/index.html
