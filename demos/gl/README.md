# ExoPlayer GL demo

This app demonstrates how to render video to a [GLSurfaceView][] while applying
a GL shader.

The shader shows an overlap bitmap on top of the video. The overlay bitmap is
drawn using an Android canvas, and includes the current frame's presentation
timestamp, to show how to get the timestamp of the frame currently in the
off-screen surface texture.

Please see the [demos README](../README.md) for instructions on how to build and
run this demo.

[GLSurfaceView]: https://developer.android.com/reference/android/opengl/GLSurfaceView
