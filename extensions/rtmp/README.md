# RTMP DataSource module

This module provides a [DataSource][] implementation for requesting [RTMP][]
streams using [LibRtmp Client for Android][].

[DataSource]: https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/upstream/DataSource.html
[RTMP]: https://en.wikipedia.org/wiki/Real-Time_Messaging_Protocol
[LibRtmp Client for Android]: https://github.com/ant-media/LibRtmp-Client-for-Android

## License note

Please note that whilst the code in this repository is licensed under
[Apache 2.0][], using this extension requires depending on LibRtmp Client for
Android, which is licensed separately.

[Apache 2.0]: https://github.com/google/ExoPlayer/blob/release-v2/LICENSE

## Getting the module

The easiest way to get the module is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:extension-rtmp:2.X.X'
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

`DefaultDataSource` will automatically use the RTMP extension whenever it's
available. Hence if your application is using `DefaultDataSource` or
`DefaultDataSource.Factory`, adding support for RTMP streams is as simple as
adding a dependency to the RTMP extension as described above. No changes to your
application code are required. Alternatively, if you know that your application
doesn't need to handle any other protocols, you can update any
`DataSource.Factory` instantiations in your application code to use
`RtmpDataSource.Factory` directly.

## Links

* [Javadoc][]

[Javadoc]: https://exoplayer.dev/doc/reference/index.html
