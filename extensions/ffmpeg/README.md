# ExoPlayer FFmpeg extension #

The FFmpeg extension provides `FfmpegAudioRenderer`, which uses FFmpeg for
decoding and can render audio encoded in a variety of formats.

## License note ##

Please note that whilst the code in this repository is licensed under
[Apache 2.0][], using this extension also requires building and including one or
more external libraries as described below. These are licensed separately.

[Apache 2.0]: https://github.com/google/ExoPlayer/blob/release-v2/LICENSE

## Build instructions (Linux, macOS) ##

To use this extension you need to clone the ExoPlayer repository and depend on
its modules locally. Instructions for doing this can be found in ExoPlayer's
[top level README][]. The extension is not provided via Google's Maven
repository (see [#2781][] for more information).

In addition, it's necessary to manually build the FFmpeg library, so that gradle
can bundle the FFmpeg binaries in the APK:

* Set the following shell variable:

```
cd "<path to exoplayer checkout>"
EXOPLAYER_ROOT="$(pwd)"
FFMPEG_EXT_PATH="${EXOPLAYER_ROOT}/extensions/ffmpeg/src/main"
```

* Download the [Android NDK][] and set its location in a shell variable.
  This build configuration has been tested on NDK r21.

```
NDK_PATH="<path to Android NDK>"
```

* Set the host platform (use "darwin-x86_64" for Mac OS X):

```
HOST_PLATFORM="linux-x86_64"
```

* Fetch FFmpeg and checkout an appropriate branch. We cannot guarantee
  compatibility with all versions of FFmpeg. We currently recommend version 4.2:

```
cd "<preferred location for ffmpeg>" && \
git clone git://source.ffmpeg.org/ffmpeg && \
cd ffmpeg && \
git checkout release/4.2 && \
FFMPEG_PATH="$(pwd)"
```

* Configure the decoders to include. See the [Supported formats][] page for
  details of the available decoders, and which formats they support.

```
ENABLED_DECODERS=(vorbis opus flac)
```

* Add a link to the FFmpeg source code in the FFmpeg extension `jni` directory.

```
cd "${FFMPEG_EXT_PATH}/jni" && \
ln -s "$FFMPEG_PATH" ffmpeg
```

* Execute `build_ffmpeg.sh` to build FFmpeg for `armeabi-v7a`, `arm64-v8a`,
  `x86` and `x86_64`. The script can be edited if you need to build for
  different architectures:

```
cd "${FFMPEG_EXT_PATH}/jni" && \
./build_ffmpeg.sh \
  "${FFMPEG_EXT_PATH}" "${NDK_PATH}" "${HOST_PLATFORM}" "${ENABLED_DECODERS[@]}"
```

## Build instructions (Windows) ##

We do not provide support for building this extension on Windows, however it
should be possible to follow the Linux instructions in [Windows PowerShell][].

[Windows PowerShell]: https://docs.microsoft.com/en-us/powershell/scripting/getting-started/getting-started-with-windows-powershell

## Using the extension ##

Once you've followed the instructions above to check out, build and depend on
the extension, the next step is to tell ExoPlayer to use `FfmpegAudioRenderer`.
How you do this depends on which player API you're using:

* If you're passing a `DefaultRenderersFactory` to `SimpleExoPlayer.Builder`,
  you can enable using the extension by setting the `extensionRendererMode`
  parameter of the `DefaultRenderersFactory` constructor to
  `EXTENSION_RENDERER_MODE_ON`. This will use `FfmpegAudioRenderer` for playback
  if `MediaCodecAudioRenderer` doesn't support the input format. Pass
  `EXTENSION_RENDERER_MODE_PREFER` to give `FfmpegAudioRenderer` priority over
  `MediaCodecAudioRenderer`.
* If you've subclassed `DefaultRenderersFactory`, add an `FfmpegAudioRenderer`
  to the output list in `buildAudioRenderers`. ExoPlayer will use the first
  `Renderer` in the list that supports the input media format.
* If you've implemented your own `RenderersFactory`, return an
  `FfmpegAudioRenderer` instance from `createRenderers`. ExoPlayer will use the
  first `Renderer` in the returned array that supports the input media format.
* If you're using `ExoPlayer.Builder`, pass an `FfmpegAudioRenderer` in the
  array of `Renderer`s. ExoPlayer will use the first `Renderer` in the list that
  supports the input media format.

Note: These instructions assume you're using `DefaultTrackSelector`. If you have
a custom track selector the choice of `Renderer` is up to your implementation,
so you need to make sure you are passing an `FfmpegAudioRenderer` to the player,
then implement your own logic to use the renderer for a given track.

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
[Android NDK]: https://developer.android.com/tools/sdk/ndk/index.html
[#2781]: https://github.com/google/ExoPlayer/issues/2781
[Supported formats]: https://exoplayer.dev/supported-formats.html#ffmpeg-extension

## Using the extension in the demo application ##

To try out playback using the extension in the [demo application][], see
[enabling extension decoders][].

[demo application]: https://exoplayer.dev/demo-application.html
[enabling extension decoders]: https://exoplayer.dev/demo-application.html#enabling-extension-decoders

## Links ##

* [Troubleshooting using extensions][]
* [Javadoc][]: Classes matching `com.google.android.exoplayer2.ext.ffmpeg.*`
  belong to this module.

[Troubleshooting using extensions]: https://exoplayer.dev/troubleshooting.html#how-can-i-get-a-decoding-extension-to-load-and-be-used-for-playback
[Javadoc]: https://exoplayer.dev/doc/reference/index.html
