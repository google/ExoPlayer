# ExoPlayer FFmpeg extension #

The Ffmpeg extension provides `FfmpegAudioRenderer` and `FfmpegVideoRenderer`, which uses FFmpeg 
native library to decode videos.

***This extension is currently in its very infancy and is under development.***

***Whats working?***
video supported codec: only H.264
audio supported codec: same as original extension
supported surface type: video_decoder_gl_surface_view

***On Plan:***
- [ ] Support other surface types
- [ ] Organize the code
- [ ] Fix possible issues
- [ ] Video Decoder support Format.rotationDegrees
- [ ] Support other codecs


## License note ##

Please note that whilst the code in this repository is licensed under
[Apache 2.0][], using this extension also requires building and including one or
more external libraries as described below. These are licensed separately.

[Apache 2.0]: https://github.com/google/ExoPlayer/blob/release-v2/LICENSE

## Build instructions (Linux, macOS) ##

To use this extension you need to clone the ExoPlayer repository and depend on
its modules locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

I provided the compiled FFmpeg [*.so files][] and [header files][]. You need to copy 
the .so files to the `src/main/libs` directory and the header files to 
the `src/main/jni/include` directory. Of course you can also compile it yourself.


## Using the extension ##

Like av1 extension, pass `EXTENSION_RENDERER_MODE_PREFER`, use `FFmpegRenderersFactory` 
instead of `DefaultRenderersFactory` to create `FfmpegVideoRenderer` and `FfmpegAudioRenderer`. 
Then you can observe the related logs of `EventLogger#decoderInitialized` in logcat 
to determine whether the ffmpeg extension is used correctly.

## Using the extension in the demo application ##

To try out playback using the extension in the [demo application][], see
[enabling extension decoders][].

use `FFmpegRenderersFactory` instead of `DefaultRenderersFactory`.

[demo application]: https://exoplayer.dev/demo-application.html
[enabling extension decoders]: https://exoplayer.dev/demo-application.html#enabling-extension-decoders

## Rendering options ##

There are two possibilities for rendering the output `Libgav1VideoRenderer`
gets from the libgav1 decoder:

* GL rendering using GL shader for color space conversion
  * If you are using `SimpleExoPlayer` with `PlayerView`, enable this option by
    setting `surface_type` of `PlayerView` to be
    `video_decoder_gl_surface_view`.
  * Otherwise, enable this option by sending `Libgav1VideoRenderer` a message
    of type `C.MSG_SET_VIDEO_DECODER_OUTPUT_BUFFER_RENDERER` with an instance of
    `VideoDecoderOutputBufferRenderer` as its object.

* Native rendering using `ANativeWindow`
  * If you are using `SimpleExoPlayer` with `PlayerView`, this option is enabled
    by default.
  * Otherwise, enable this option by sending `Libgav1VideoRenderer` a message of
    type `C.MSG_SET_SURFACE` with an instance of `SurfaceView` as its object.

Note: Although the default option uses `ANativeWindow`, based on our testing the
GL rendering mode has better performance, so should be preferred

## Links ##

* [Javadoc][]: Classes matching `com.google.android.exoplayer2.ext.av1.*`
  belong to this module.

[Javadoc]: https://exoplayer.dev/doc/reference/index.html
[*.so files]: https://drive.google.com/open?id=14v4tz5L_jU7di3xWrY-uhuS7K5mcwj3g
[header files]: https://drive.google.com/open?id=1dDZ9R4cLPpgcHOCoUpClrOlqnGL2UTSr
