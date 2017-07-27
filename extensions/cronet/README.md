# ExoPlayer Cronet Extension #

## Description ##

[Cronet][] is Chromium's Networking stack packaged as a library.

The Cronet Extension is an [HttpDataSource][] implementation using [Cronet][].

[HttpDataSource]: https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer/upstream/HttpDataSource.html
[Cronet]: https://chromium.googlesource.com/chromium/src/+/master/components/cronet?autodive=0%2F%2F

## Build Instructions ##

* Checkout ExoPlayer along with Extensions:

```
git clone https://github.com/google/ExoPlayer.git
```

* Get the Cronet libraries:

1. Find the latest Cronet release [here][] and navigate to its `Release/cronet`
   directory
1. Download `cronet_api.jar`, `cronet_impl_common_java.jar`,
   `cronet_impl_native_java.jar` and the `libs` directory
1. Copy the three jar files into the `libs` directory of this extension
1. Copy the content of the downloaded `libs` directory into the `jniLibs`
   directory of this extension

* In ExoPlayer's `settings.gradle` file, uncomment the Cronet extension

[here]: https://console.cloud.google.com/storage/browser/chromium-cronet/android
