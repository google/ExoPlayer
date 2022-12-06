# ExoPlayer AV1 module

The AV1 module provides `Libgav1VideoRenderer`, which uses libgav1 native
library to decode AV1 videos.

## License note

Please note that whilst the code in this repository is licensed under
[Apache 2.0][], using this module also requires building and including one or
more external libraries as described below. These are licensed separately.

[Apache 2.0]: https://github.com/google/ExoPlayer/blob/release-v2/LICENSE

## Build instructions (Linux, macOS)

To use the module you need to clone this GitHub project and depend on its
modules locally. Instructions for doing this can be found in the
[top level README][].

In addition, it's necessary to fetch cpu_features library and libgav1 with its
dependencies as follows:

* Set the following environment variables:

```
cd "<path to project checkout>"
AV1_MODULE_PATH="$(pwd)/extensions/av1/src/main"
```

* Fetch cpu_features library:

```
cd "${AV1_MODULE_PATH}/jni" && \
git clone https://github.com/google/cpu_features
```

* Fetch libgav1:

```
cd "${AV1_MODULE_PATH}/jni" && \
git clone https://chromium.googlesource.com/codecs/libgav1
```

* Fetch Abseil:

```
cd "${AV1_MODULE_PATH}/jni/libgav1" && \
git clone https://github.com/abseil/abseil-cpp.git third_party/abseil-cpp
```

* [Install CMake][].

Having followed these steps, gradle will build the module automatically when run
on the command line or via Android Studio, using [CMake][] and [Ninja][] to
configure and build libgav1 and the module's [JNI wrapper library][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
[Install CMake]: https://developer.android.com/studio/projects/install-ndk
[CMake]: https://cmake.org/
[Ninja]: https://ninja-build.org
[JNI wrapper library]: https://github.com/google/ExoPlayer/blob/release-v2/extensions/av1/src/main/jni/gav1_jni.cc

## Build instructions (Windows)

We do not provide support for building this module on Windows, however it should
be possible to follow the Linux instructions in [Windows PowerShell][].

[Windows PowerShell]: https://docs.microsoft.com/en-us/powershell/scripting/getting-started/getting-started-with-windows-powershell

## Using the module

Once you've followed the instructions above to check out, build and depend on
the module, the next step is to tell ExoPlayer to use `Libgav1VideoRenderer`.
How you do this depends on which player API you're using:

*   If you're passing a `DefaultRenderersFactory` to `ExoPlayer.Builder`, you
    can enable using the module by setting the `extensionRendererMode` parameter
    of the `DefaultRenderersFactory` constructor to
    `EXTENSION_RENDERER_MODE_ON`. This will use `Libgav1VideoRenderer` for
    playback if `MediaCodecVideoRenderer` doesn't support decoding the input AV1
    stream. Pass `EXTENSION_RENDERER_MODE_PREFER` to give `Libgav1VideoRenderer`
    priority over `MediaCodecVideoRenderer`.
*   If you've subclassed `DefaultRenderersFactory`, add a
    `Libvgav1VideoRenderer` to the output list in `buildVideoRenderers`.
    ExoPlayer will use the first `Renderer` in the list that supports the input
    media format.
*   If you've implemented your own `RenderersFactory`, return a
    `Libgav1VideoRenderer` instance from `createRenderers`. ExoPlayer will use
    the first `Renderer` in the returned array that supports the input media
    format.
*   If you're using `ExoPlayer.Builder`, pass a `Libgav1VideoRenderer` in the
    array of `Renderer`s. ExoPlayer will use the first `Renderer` in the list
    that supports the input media format.

Note: These instructions assume you're using `DefaultTrackSelector`. If you have
a custom track selector the choice of `Renderer` is up to your implementation.
You need to make sure you are passing a `Libgav1VideoRenderer` to the player and
then you need to implement your own logic to use the renderer for a given track.

## Using the module in the demo application

To try out playback using the module in the [demo application][], see
[enabling extension decoders][].

[demo application]: https://exoplayer.dev/demo-application.html
[enabling extension decoders]: https://exoplayer.dev/demo-application.html#enabling-extension-decoders

## Rendering options

There are two possibilities for rendering the output `Libgav1VideoRenderer`
gets from the libgav1 decoder:

*   GL rendering using GL shader for color space conversion

    *   If you are using `ExoPlayer` with `StyledPlayerView`, enable this option
        by setting the `surface_type` of the view to be
        `video_decoder_gl_surface_view`.
    *   Otherwise, enable this option by sending `Libgav1VideoRenderer` a
        message of type `Renderer.MSG_SET_VIDEO_OUTPUT` with an instance of
        `VideoDecoderOutputBufferRenderer` as its object.
        `VideoDecoderGLSurfaceView` is the concrete
        `VideoDecoderOutputBufferRenderer` implementation used by
        `StyledPlayerView`.

*   Native rendering using `ANativeWindow`

    *   If you are using `ExoPlayer` with `StyledPlayerView`, this option is
        enabled by default.
    *   Otherwise, enable this option by sending `Libgav1VideoRenderer` a
        message of type `Renderer.MSG_SET_VIDEO_OUTPUT` with an instance of
        `SurfaceView` as its object.

Note: Although the default option uses `ANativeWindow`, based on our testing the
GL rendering mode has better performance, so should be preferred

## Links

*   [Troubleshooting using decoding extensions][]
*   [Javadoc][]

[Troubleshooting using decoding extensions]: https://exoplayer.dev/troubleshooting.html#how-can-i-get-a-decoding-extension-to-load-and-be-used-for-playback
[Javadoc]: https://exoplayer.dev/doc/reference/index.html
