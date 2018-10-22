# ExoPlayer Shoutcast Metadata Protocol (ICY) extension #
The Shoutcast Metadata Protocol extension provides **IcyHttpDataSource** and
**IcyHttpDataSourceFactory** which can parse ICY metadata information such as
stream name and genre as well as current song information from a music stream.

You can find the protocol description here:

- https://cast.readme.io/v1.0/docs/icy
- http://www.smackfu.com/stuff/programming/shoutcast.html

## Getting the extension ##

The easiest way to use the extension is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:extension-icy:2.X.X'
```

where `2.X.X` is the version, which must match the version of the ExoPlayer
library being used.

Alternatively, you can clone the ExoPlayer repository and depend on the module
locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md

## Using the extension ##

To receive information about the current music stream (such as name and genre,
see **IcyHeaders** class) as well as current song information (see
**IcyMetadata** class), pass an instance of **IcyHttpDataSourceFactory** instead
of an **DefaultHttpDataSourceFactory** like this (in Kotlin):

```kotlin
// ... exoPlayer instance already created

// Custom HTTP data source factory which requests Icy metadata and parses it if
// the stream server supports it
val client = OkHttpClient.Builder().build()
val icyHttpDataSourceFactory = IcyHttpDataSourceFactory.Builder(client)
    .setUserAgent(userAgent)
    .setIcyHeadersListener { icyHeaders ->
        Log.d("XXX", "onIcyHeaders: %s".format(icyHeaders.toString()))
    }
    .setIcyMetadataChangeListener { icyMetadata ->
        Log.d("XXX", "onIcyMetaData: %s".format(icyMetadata.toString()))
    }
    .build()

// Produces DataSource instances through which media data is loaded
val dataSourceFactory = DefaultDataSourceFactory(applicationContext, null, icyHttpDataSourceFactory)

// The MediaSource represents the media to be played
val mediaSource = ExtractorMediaSource.Factory(dataSourceFactory)
    .setExtractorsFactory(DefaultExtractorsFactory())
    .createMediaSource(sourceUri)

// exoPlayer?.prepare(mediaSource) ...
```

## Links ##

* [Javadoc][]: Classes matching `com.google.android.exoplayer2.ext.icy.*`
  belong to this module.

[Javadoc]: https://google.github.io/ExoPlayer/doc/reference/index.html
