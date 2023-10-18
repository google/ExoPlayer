# HttpEngine DataSource module

This module provides an [HttpDataSource][] implementation that uses
[HttpEngine][].

HttpEngine uses best HTTP stack available on the current platform. HttpEngine
was added in API level 34.

[HttpDataSource]: ../datasource/src/main/java/com/google/android/exoplayer2/upstream/HttpDataSource.java
[HttpEngine]: https://developer.android.com/reference/android/net/http/HttpEngine

## Getting the module

The easiest way to get the module is to add it as a gradle dependency:

```gradle
implementation 'androidx.media3:media3-datasource-httpengine:1.X.X'
```

where `1.X.X` is the version, which must match the version of the other media
modules being used.

Alternatively, you can clone this GitHub project and depend on the module
locally. Instructions for doing this can be found in the [top level README][].

[top level README]: ../../README.md

## Using the module

Media components request data through `DataSource` instances. These instances
are obtained from instances of `DataSource.Factory`, which are instantiated and
injected from application code.

If your application only needs to play http(s) content and the device is running
at least API level 34, using the HttpEngine extension is as simple as updating
`DataSource.Factory` instantiations in your application code to use
`HttpEngineDataSource.Factory`. If your application also needs to play
non-http(s) content such as local files, use:

```
new DefaultDataSource.Factory(
    ...
    /* baseDataSourceFactory= */ new HttpEngineDataSource.Factory(...) );
```

## Cronet implementations

To instantiate an `HttpEngineDataSource.Factory` you'll need an `HttpEngine`. A
`HttpEngine` can be obtained from the platform in API level 34 or greater. It's
recommended that an application should only have a single `HttpEngine` instance.

## Links

*   [Javadoc][]

[Javadoc]: https://developer.android.com/reference/com/google/android/exoplayer2/upstream/httpengine/package-summary
