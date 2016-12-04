#!/usr/bin/env bash

if [ ! -e "../../ffmpeg" ] ; then
    echo "Must run from the 'contrib' directory"
    exit 1
fi

NDK_VERSION=${NDK_VERSION:-r13b}
DOWNLOAD_NDK=${DOWNLOAD_NDK:-1}

if [ "$NDK_PATH" = "" ] ; then
    if [ -e `pwd`/Ndk/android-ndk-${NDK_VERSION} ] ; then
       NDK_PATH=`pwd`/Ndk/android-ndk-${NDK_VERSION}
    else
        if [ "1" = "${DOWNLOAD_NDK}" ] ; then
            echo "Setting up NDK"
            mkdir Ndk
            cd Ndk/
            wget http://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux-x86_64.zip
            unzip ./android-ndk-${NDK_VERSION}-linux-x86_64.zip
            cd ..
            NDK_PATH=`pwd`/Ndk/android-ndk-${NDK_VERSION}
        else
            echo "Set NDK_PATH to be the location of your Ndk"
            exit 1
        fi
    fi
fi

pushd .

EXOPLAYER_ROOT=`realpath ../../../`
echo "ExoPlayer Root: $EXOPLAYER_ROOT"
FFMPEG_EXT_PATH="${EXOPLAYER_ROOT}/extensions/ffmpeg/src/main"
# can be used to pass "--enable-decoder=ac3" and other enable args
FFMPEG_EXT_ARGS=${FFMPEG_EXT_ARGS:-""}

cd "${FFMPEG_EXT_PATH}/jni" && \
if [ -e ffmpeg ] ; then rm -rf ffmpeg ; fi && \
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
    ${FFMPEG_EXT_ARGS} \
    && \
make -j4 && \
make install-libs

cd "${FFMPEG_EXT_PATH}"/jni && \
${NDK_PATH}/ndk-build APP_ABI=armeabi-v7a -j4

popd

echo "build completed"
echo "   FFMPEG Extra Args were ${FFMPEG_EXT_ARGS}"
echo -n
echo "You can now run "
echo "# ./gradlew -Dexoplayer.version=r2.0.4-SNAPSHOT assemble publishToMavenLocal"
echo "From the ${EXOPLAYER_ROOT} directory to create, assemble, and publish the the artifacts"
echo "to your local maven repository. (replacing the version with whatever version you like)"
echo -n
echo "In your projects that are consuming ExoPlayer, you can use the dependencies"
echo " compile 'com.google.android.exoplayer:exoplayer:r2.0.4-SNAPSHOT@aar'"
echo " compile 'com.google.android.exoplayer:extension-ffmpeg:r2.0.4-SNAPSHOT@aar'"
