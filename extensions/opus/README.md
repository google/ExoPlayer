# ExoPlayer Opus Extension #

## Description ##

The Opus Extension is a [Renderer][] implementation that helps you bundle
libopus (the Opus decoding library) into your app and use it along with
ExoPlayer to play Opus audio on Android devices.

[Renderer]: https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/Renderer.html

## Build Instructions ##

* Checkout ExoPlayer along with Extensions:

```
git clone https://github.com/google/ExoPlayer.git
```

* Set the following environment variables:

```
cd "<path to exoplayer checkout>"
EXOPLAYER_ROOT="$(pwd)"
OPUS_EXT_PATH="${EXOPLAYER_ROOT}/extensions/opus/src/main"
```

* Download the [Android NDK][] and set its location in an environment variable:

[Android NDK]: https://developer.android.com/tools/sdk/ndk/index.html

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

* In your project, you can add a dependency to the Opus Extension by using a
rule like this:

```
// in settings.gradle
include ':..:ExoPlayer:library'
include ':..:ExoPlayer:extension-opus'

// in build.gradle
dependencies {
    compile project(':..:ExoPlayer:library')
    compile project(':..:ExoPlayer:extension-opus')
}
```

* Now, when you build your app, the Opus extension will be built and the native
  libraries will be packaged along with the APK.

## Notes ##

* Every time there is a change to the libopus checkout:
  * Arm assembly should be converted by running `convert_android_asm.sh`
  * Clean and re-build the project.
* If you want to use your own version of libopus, place it in
  `${OPUS_EXT_PATH}/jni/libopus`.
