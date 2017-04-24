# ExoPlayer GVR Extension #

## Description ##

The GVR extension wraps the [Google VR SDK for Android][]. It provides a
GvrAudioProcessor, which uses [GvrAudioSurround][] to provide binaural rendering
of surround sound and ambisonic soundfields.

## Using the extension ##

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

## Using GvrAudioProcessor ##

* If using SimpleExoPlayer, override SimpleExoPlayer.buildAudioProcessors to
  return a GvrAudioProcessor.
* If constructing renderers directly, pass a GvrAudioProcessor to
  MediaCodecAudioRenderer's constructor.

[Google VR SDK for Android]: https://developers.google.com/vr/android/
[GvrAudioSurround]: https://developers.google.com/vr/android/reference/com/google/vr/sdk/audio/GvrAudioSurround
