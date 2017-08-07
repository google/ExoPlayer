# ExoPlayer Flac extension #

## Description ##

The Flac extension is a [Renderer][] implementation that helps you bundle
libFLAC (the Flac decoding library) into your app and use it along with
ExoPlayer to play Flac audio on Android devices.

[Renderer]: https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/Renderer.html

## Build instructions ##

To use this extension you need to clone the ExoPlayer repository and depend on
its modules locally. Instructions for doing this can be found in ExoPlayer's
[top level README][]. In addition, it's necessary to build the extension's
native components as follows:

* Set the following environment variables:

```
cd "<path to exoplayer checkout>"
EXOPLAYER_ROOT="$(pwd)"
FLAC_EXT_PATH="${EXOPLAYER_ROOT}/extensions/flac/src/main"
```

* Download the [Android NDK][] and set its location in an environment variable:

```
NDK_PATH="<path to Android NDK>"
```

* Download and extract flac-1.3.1 as "${FLAC_EXT_PATH}/jni/flac" folder:

```
cd "${FLAC_EXT_PATH}/jni" && \
curl http://downloads.xiph.org/releases/flac/flac-1.3.1.tar.xz | tar xJ && \
mv flac-1.3.1 flac
```

* Build the JNI native libraries from the command line:

```
cd "${FLAC_EXT_PATH}"/jni && \
${NDK_PATH}/ndk-build APP_ABI=all -j4
```

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
[Android NDK]: https://developer.android.com/tools/sdk/ndk/index.html
