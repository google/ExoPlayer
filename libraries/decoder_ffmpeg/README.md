# FFmpeg decoder module

The FFmpeg module provides `FfmpegAudioRenderer`, which uses FFmpeg for decoding
and can render audio encoded in a variety of formats.

## License note

Please note that whilst the code in this repository is licensed under
[Apache 2.0][], using this module also requires building and including one or
more external libraries as described below. These are licensed separately.

[Apache 2.0]: ../../LICENSE

## Build instructions (Linux, macOS)

To use the module you need to clone this GitHub project and depend on its
modules locally. Instructions for doing this can be found in the
[top level README][]. The module is not provided via Google's Maven repository
(see [ExoPlayer issue 2781][] for more information).

In addition, it's necessary to manually build the FFmpeg library, so that gradle
can bundle the FFmpeg binaries in the APK:

* Set the following shell variable:

```
cd "<path to project checkout>"
FFMPEG_MODULE_PATH="$(pwd)/libraries/decoder_ffmpeg/src/main"
```

*   Download the [Android NDK][] and set its location in a shell variable. This
    build configuration has been tested on NDK r26b (r23c if ANDROID_ABI is less
    than 21).

```
NDK_PATH="<path to Android NDK>"
```

* Set the host platform (use "darwin-x86_64" for Mac OS X):

```
HOST_PLATFORM="linux-x86_64"
```

*   Set the ABI version for native code (typically it's equal to minSdk and must
    not exceed it):

```
ANDROID_ABI=21
```

*   Fetch FFmpeg and checkout an appropriate branch. We cannot guarantee
    compatibility with all versions of FFmpeg. We currently recommend version
    6.0:

```
cd "<preferred location for ffmpeg>" && \
git clone git://source.ffmpeg.org/ffmpeg && \
cd ffmpeg && \
git checkout release/6.0 && \
FFMPEG_PATH="$(pwd)"
```

* Configure the decoders to include. See the [Supported formats][] page for
  details of the available decoders, and which formats they support.

```
ENABLED_DECODERS=(vorbis opus flac)
```

*   Add a link to the FFmpeg source code in the FFmpeg module `jni` directory.

```
cd "${FFMPEG_MODULE_PATH}/jni" && \
ln -s "$FFMPEG_PATH" ffmpeg
```

* Execute `build_ffmpeg.sh` to build FFmpeg for `armeabi-v7a`, `arm64-v8a`,
  `x86` and `x86_64`. The script can be edited if you need to build for
  different architectures:

```
cd "${FFMPEG_MODULE_PATH}/jni" && \
./build_ffmpeg.sh \
  "${FFMPEG_MODULE_PATH}" "${NDK_PATH}" "${HOST_PLATFORM}" "${ANDROID_ABI}" "${ENABLED_DECODERS[@]}"
```

## Build instructions (Windows)

We do not provide support for building this module on Windows, however it should
be possible to follow the Linux instructions in [Windows PowerShell][].

[Windows PowerShell]: https://docs.microsoft.com/en-us/powershell/scripting/getting-started/getting-started-with-windows-powershell

## Using the module with ExoPlayer

Once you've followed the instructions above to check out, build and depend on
the module, the next step is to tell ExoPlayer to use `FfmpegAudioRenderer`. How
you do this depends on which player API you're using:

*   If you're passing a `DefaultRenderersFactory` to `ExoPlayer.Builder`, you
    can enable using the module by setting the `extensionRendererMode` parameter
    of the `DefaultRenderersFactory` constructor to
    `EXTENSION_RENDERER_MODE_ON`. This will use `FfmpegAudioRenderer` for
    playback if `MediaCodecAudioRenderer` doesn't support the input format. Pass
    `EXTENSION_RENDERER_MODE_PREFER` to give `FfmpegAudioRenderer` priority over
    `MediaCodecAudioRenderer`.
*   If you've subclassed `DefaultRenderersFactory`, add an `FfmpegAudioRenderer`
    to the output list in `buildAudioRenderers`. ExoPlayer will use the first
    `Renderer` in the list that supports the input media format.
*   If you've implemented your own `RenderersFactory`, return an
    `FfmpegAudioRenderer` instance from `createRenderers`. ExoPlayer will use
    the first `Renderer` in the returned array that supports the input media
    format.
*   If you're using `ExoPlayer.Builder`, pass an `FfmpegAudioRenderer` in the
    array of `Renderer`s. ExoPlayer will use the first `Renderer` in the list
    that supports the input media format.

Note: These instructions assume you're using `DefaultTrackSelector`. If you have
a custom track selector the choice of `Renderer` is up to your implementation,
so you need to make sure you are passing an `FfmpegAudioRenderer` to the player,
then implement your own logic to use the renderer for a given track.

[top level README]: ../../README.md
[Android NDK]: https://developer.android.com/tools/sdk/ndk/index.html
[ExoPlayer issue 2781]: https://github.com/google/ExoPlayer/issues/2781
[Supported formats]: https://developer.android.com/media/media3/exoplayer/supported-formats#ffmpeg-library

## Links

*   [Troubleshooting using decoding extensions][]

[Troubleshooting using decoding extensions]: https://developer.android.com/media/media3/exoplayer/troubleshooting#how-can-i-get-a-decoding-library-to-load-and-be-used-for-playback
