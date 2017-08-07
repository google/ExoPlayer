# ExoPlayer OkHttp extension #

## Description ##

The OkHttp extension is an [HttpDataSource][] implementation using Square's
[OkHttp][].

[HttpDataSource]: https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/upstream/HttpDataSource.html
[OkHttp]: https://square.github.io/okhttp/

## Getting the extension ##

The easiest way to use the extension is to add it as a gradle dependency:

```gradle
compile 'com.google.android.exoplayer:extension-okhttp:rX.X.X'
```

where `rX.X.X` is the version, which must match the version of the ExoPlayer
library being used.

Alternatively, you can clone the ExoPlayer repository and depend on the module
locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md

## Using the extension ##

ExoPlayer requests data through `DataSource` instances. These instances are
either instantiated and injected from application code, or obtained from
instances of `DataSource.Factory` that are instantiated and injected from
application code.

If your application only needs to play http(s) content, using the OkHttp
extension is as simple as updating any `DataSource`s and `DataSource.Factory`
instantiations in your application code to use `OkHttpDataSource` and
`OkHttpDataSourceFactory` respectively. If your application also needs to play
non-http(s) content such as local files, use
```
new DefaultDataSource(
    ...
    new OkHttpDataSource(...) /* baseDataSource argument */);
```
and
```
new DefaultDataSourceFactory(
    ...
    new OkHttpDataSourceFactory(...) /* baseDataSourceFactory argument */);
```
respectively.
