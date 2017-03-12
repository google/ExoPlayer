# ExoPlayer GVR Extension #

## Description ##

The GVR extension wraps the [Google VR SDK for Android][]. It provides a
GvrAudioProcessor, which uses [GvrAudioSurround][] to provide binaural rendering
of surround sound and ambisonic soundfields.

## Instructions ##

If using SimpleExoPlayer, override SimpleExoPlayer.buildAudioProcessors to
return a GvrAudioProcessor.

If constructing renderers directly, pass a GvrAudioProcessor to
MediaCodecAudioRenderer's constructor.

[Google VR SDK for Android]: https://developers.google.com/vr/android/
[GvrAudioSurround]: https://developers.google.com/vr/android/reference/com/google/vr/sdk/audio/GvrAudioSurround
