# ExoPlayer GVR Extension #

## Description ##

The GVR extension wraps the [Google VR SDK for Android][]. It provides a
GvrBufferProcessor, which uses [GvrAudioSurround][] to provide binaural
rendering of surround sound and ambisonic soundfields.

## Instructions ##

If using SimpleExoPlayer, override SimpleExoPlayer.buildBufferProcessors to
return a GvrBufferProcessor.

If constructing renderers directly, pass a GvrBufferProcessor to
MediaCodecAudioRenderer's constructor.

[Google VR SDK for Android]: https://developers.google.com/vr/android/
[GvrAudioSurround]: https://developers.google.com/vr/android/reference/com/google/vr/sdk/audio/GvrAudioSurround

