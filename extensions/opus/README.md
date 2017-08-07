# ExoPlayer Opus extension #

## Description ##

The Opus extension is a [Renderer][] implementation that helps you bundle
libopus (the Opus decoding library) into your app and use it along with
ExoPlayer to play Opus audio on Android devices.

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
OPUS_EXT_PATH="${EXOPLAYER_ROOT}/extensions/opus/src/main"
```

* Download the [Android NDK][] and set its location in an environment variable:

```
NDK_PATH="<path to Android NDK>"
```

* Fetch libopus:

```
cd "${OPUS_EXT_PATH}/jni" && \
git clone https://git.xiph.org/opus.git libopus
```

* Run the script to convert arm assembly to NDK compatible format:

```
cd ${OPUS_EXT_PATH}/jni && ./convert_android_asm.sh
```

* Build the JNI native libraries from the command line:

```
cd "${OPUS_EXT_PATH}"/jni && \
${NDK_PATH}/ndk-build APP_ABI=all -j4
```

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
[Android NDK]: https://developer.android.com/tools/sdk/ndk/index.html

## Notes ##

* Every time there is a change to the libopus checkout:
  * Arm assembly should be converted by running `convert_android_asm.sh`
  * Clean and re-build the project.
* If you want to use your own version of libopus, place it in
  `${OPUS_EXT_PATH}/jni/libopus`.
