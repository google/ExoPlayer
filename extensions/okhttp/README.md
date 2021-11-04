# OkHttp DataSource module

This module provides an [HttpDataSource][] implementation that uses Square's
[OkHttp][].

OkHttp is a modern network stack that's widely used by many popular Android
applications. It supports the HTTP and HTTP/2 protocols.

[HttpDataSource]: https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/upstream/HttpDataSource.html
[OkHttp]: https://square.github.io/okhttp/

## License note

Please note that whilst the code in this repository is licensed under
[Apache 2.0][], using this extension requires depending on OkHttp, which is
licensed separately.

[Apache 2.0]: https://github.com/google/ExoPlayer/blob/release-v2/LICENSE

## Getting the module

The easiest way to get the module is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:extension-okhttp:2.X.X'
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

If your application only needs to play http(s) content, using the OkHttp
extension is as simple as updating any `DataSource.Factory` instantiations in
your application code to use `OkHttpDataSource.Factory`. If your application
also needs to play non-http(s) content such as local files, use:
```
new DefaultDataSourceFactory(
    ...
    /* baseDataSourceFactory= */ new OkHttpDataSource.Factory(...));
```

## Links

* [Javadoc][]

[Javadoc]: https://exoplayer.dev/doc/reference/index.html
