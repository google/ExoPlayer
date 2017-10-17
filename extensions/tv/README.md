# ExoPlayer TV tuner extension #

Provides components for broadcast TV playback with ExoPlayer.

## Links ##

* [Javadoc][]: Classes matching `com.google.android.exoplayer2.ext.tv.*`
  belong to this extension.

[Javadoc]: https://google.github.io/ExoPlayer/doc/reference/index.html

## Build Instructions ##

* Checkout ExoPlayer:

```
git clone https://github.com/google/ExoPlayer.git
```

* Set the following environment variables:

```
cd "<path to exoplayer checkout>"
EXOPLAYER_ROOT="$(pwd)"
TV_MODULE_PATH="${EXOPLAYER_ROOT}/extensions/tv/src/main"
```

* Download the [Android NDK][] and set its location in an environment variable:

[Android NDK]: https://developer.android.com/tools/sdk/ndk/index.html

```
NDK_PATH="<path to Android NDK>"
```

* Build the JNI native libraries from the command line:

```
cd "${TV_MODULE_PATH}"/jni && \
${NDK_PATH}/ndk-build APP_ABI=all -j<N>
```
