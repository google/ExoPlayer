# ExoPlayer SurfaceControl demo

This app demonstrates how to use the [SurfaceControl][] API to redirect video
output from ExoPlayer between different views or off-screen. `SurfaceControl`
is new in Android 10, so the app requires `minSdkVersion` 29.

The app layout has a grid of `SurfaceViews`. Initially video is output to one
of the views. Tap a `SurfaceView` to move video output to it. You can also tap
the buttons at the top of the activity to move video output off-screen, to a
full-screen `SurfaceView` or to a new activity.

When using `SurfaceControl`, the `MediaCodec` always has the same surface
attached to it, which can be freely 'reparented' to any `SurfaceView` (or
off-screen) without any interruptions to playback. This works better than
calling `MediaCodec.setOutputSurface` to change the output surface of the codec
because `MediaCodec` does not re-render its last frame when that method is
called, and because you can move output off-screen easily (`setOutputSurface`
can't take a `null` surface, so the player has to use a `DummySurface`, which
doesn't handle protected output on all devices).

Please see the [demos README](../README.md) for instructions on how to build and
run this demo.

[SurfaceControl]: https://developer.android.com/reference/android/view/SurfaceControl
