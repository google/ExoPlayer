# Script to build the ffmpeg audio extension

Running `./build-natives.sh` will download the Ndk (if missing), and build the native parts of the ffmpeg extension.

If you already have the Ndk installed you can pass it, `NDK_PATH=YOUR_PATH ./build-natives.sh`, and it will use the Ndk you provide in `YOUR_PATH`.

You can pass additional ffmpeg confgure args to the command using the `FFMPEG_EXT_ARGS` environment.  For example to build with ac3 support you could do the following

```
# FFMPEG_EXT_ARGS="--enable-decoder=ac3" ./build-natives.sh
```

After you build the native parts, you still need to be able to consume ExoPlayer and the extension in your project.  You can do that using the README provided in the root of the ffmpeg extension directory, or, you can publish ExoPlayer and the ffmpeg extension to your local maven repository, and then reference it as a gradle dependency in your project.

```
# cd $EXOPLAYER_HOME
# ./gradlew -Dexoplayer.version=YOUR_VERSION assemble publishToMavenLocal
```

Then in your build.gradle, you can add the following dependencies

```
compile 'com.google.android.exoplayer:exoplayer:YOUR_VERSION@aar'
compile 'com.google.android.exoplayer:extension-ffmpeg:YOUR_VERSION@aar'
```

Where `YOUR_VERSION` might be `r2.0.4-SNAPSHOT`

