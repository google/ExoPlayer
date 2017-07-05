# ExoPlayer Cronet Extension #

## Description ##

[Cronet][] is Chromium's Networking stack packaged as a library.

The Cronet Extension is an [HttpDataSource][] implementation using [Cronet][].

[HttpDataSource]: https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer/upstream/HttpDataSource.html
[Cronet]: https://chromium.googlesource.com/chromium/src/+/master/components/cronet?autodive=0%2F%2F

## Build Instructions ##

To use this extension you need to clone the ExoPlayer repository and depend on
its modules locally. Instructions for doing this can be found in ExoPlayer's
[top level README][]. In addition, it's necessary to get the Cronet libraries
and enable the extension:

1. Find the latest Cronet release [here][] and navigate to its `Release/cronet`
   directory
1. Download `cronet_api.jar`, `cronet_impl_common_java.jar`,
   `cronet_impl_native_java.jar` and the `libs` directory
1. Copy the three jar files into the `libs` directory of this extension
1. Copy the content of the downloaded `libs` directory into the `jniLibs`
   directory of this extension

* In your `settings.gradle` file, add the following line before the line that
  applies `core_settings.gradle`:

```gradle
gradle.ext.exoplayerIncludeCronetExtension = true;
```

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
[here]: https://console.cloud.google.com/storage/browser/chromium-cronet/android
