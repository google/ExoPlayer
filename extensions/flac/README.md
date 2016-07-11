# ExoPlayer Flac Extension #

## Description ##

The Flac Extension is a [Renderer][] implementation that helps you bundle
libFLAC (the Flac decoding library) into your app and use it along with
ExoPlayer to play Flac audio on Android devices.

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
FLAC_EXT_PATH="${EXOPLAYER_ROOT}/extensions/flac/src/main"
```

* Download the [Android NDK][] and set its location in an environment variable:

[Android NDK]: https://developer.android.com/tools/sdk/ndk/index.html

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

* In your project, you can add a dependency to the Flac Extension by using a
  rule like this:

```
// in settings.gradle
include ':..:ExoPlayer:library'
include ':..:ExoPlayer:extension-flac'

// in build.gradle
dependencies {
    compile project(':..:ExoPlayer:library')
    compile project(':..:ExoPlayer:extension-flac')
}
```

* Now, when you build your app, the Flac extension will be built and the native
  libraries will be packaged along with the APK.
