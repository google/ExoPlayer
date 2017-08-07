# ExoPlayer GVR extension #

## Description ##

The GVR extension wraps the [Google VR SDK for Android][]. It provides a
GvrAudioProcessor, which uses [GvrAudioSurround][] to provide binaural rendering
of surround sound and ambisonic soundfields.

[Google VR SDK for Android]: https://developers.google.com/vr/android/
[GvrAudioSurround]: https://developers.google.com/vr/android/reference/com/google/vr/sdk/audio/GvrAudioSurround

## Getting the extension ##

The easiest way to use the extension is to add it as a gradle dependency. You
need to make sure you have the jcenter repository included in the `build.gradle`
file in the root of your project:

```gradle
repositories {
    jcenter()
}
```

Next, include the following in your module's `build.gradle` file:

```gradle
compile 'com.google.android.exoplayer:extension-gvr:rX.X.X'
```

where `rX.X.X` is the version, which must match the version of the ExoPlayer
library being used.

Alternatively, you can clone the ExoPlayer repository and depend on the module
locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

## Using the extension ##

* If using SimpleExoPlayer, override SimpleExoPlayer.buildAudioProcessors to
  return a GvrAudioProcessor.
* If constructing renderers directly, pass a GvrAudioProcessor to
  MediaCodecAudioRenderer's constructor.

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
