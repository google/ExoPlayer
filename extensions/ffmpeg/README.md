# FfmpegAudioRenderer #

## Description ##

The FFmpeg extension is a [Renderer][] implementation that uses FFmpeg to decode
audio.

[Renderer]: https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/Renderer.html

## Build instructions ##

* Checkout ExoPlayer along with Extensions

```
git clone https://github.com/google/ExoPlayer.git
```

* Set the following environment variables:

```
cd "<path to exoplayer checkout>"
EXOPLAYER_ROOT="$(pwd)"
FFMPEG_EXT_PATH="${EXOPLAYER_ROOT}/extensions/ffmpeg/src/main"
```

* Download the [Android NDK][] and set its location in an environment variable:

[Android NDK]: https://developer.android.com/tools/sdk/ndk/index.html

```
NDK_PATH="<path to Android NDK>"
```

* Fetch and build FFmpeg.

For example, to fetch and build for armv7a:

```
cd "${FFMPEG_EXT_PATH}/jni" && \
git clone git://source.ffmpeg.org/ffmpeg ffmpeg && cd ffmpeg && \
./configure \
    --libdir=android-libs/armeabi-v7a \
    --arch=arm \
    --cpu=armv7-a \
    --cross-prefix="${NDK_PATH}/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64/bin/arm-linux-androideabi-" \
    --target-os=android \
    --sysroot="${NDK_PATH}/platforms/android-9/arch-arm/" \
    --extra-cflags="-march=armv7-a -mfloat-abi=softfp" \
    --extra-ldflags="-Wl,--fix-cortex-a8" \
    --extra-ldexeflags=-pie \
    --disable-static \
    --enable-shared \
    --disable-doc \
    --disable-programs \
    --disable-everything \
    --disable-avdevice \
    --disable-avformat \
    --disable-swscale \
    --disable-postproc \
    --disable-avfilter \
    --disable-symver \
    --enable-avresample \
    --enable-decoder=vorbis \
    --enable-decoder=opus \
    --enable-decoder=flac \
    --enable-decoder=alac \
    && \
make -j4 && \
make install-libs
```

* Build the JNI native libraries.

```
cd "${FFMPEG_EXT_PATH}"/jni && \
${NDK_PATH}/ndk-build APP_ABI=armeabi-v7a -j4
```

Repeat these steps for any other architectures you need to support.

* In your project, you can add a dependency on the extension by using a rule
  like this:

```
// in settings.gradle
include ':..:ExoPlayer:library'
include ':..:ExoPlayer:extension-ffmpeg'

// in build.gradle
dependencies {
    compile project(':..:ExoPlayer:library')
    compile project(':..:ExoPlayer:extension-ffmpeg')
}
```

* Now, when you build your app, the extension will be built and the native
  libraries will be packaged along with the APK.
