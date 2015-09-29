# ExoPlayer Readme #

## Fork information ##

Based off ExoPlayer 1.4.2

ExoPlayer does not correctly identify the display resolution on Api level bellow 23. This is a bug at the Android level and it's logged here:

https://github.com/google/ExoPlayer/issues/800

This is a shame because currently Sony Bravia TV's have a 4K variant that runs Android TV and because of this shortcoming they don't detect these displays to be 4K defaulting to 1080p resolution.

In this fork, a simple Util class is introduced that handles this case by using the android.os.Build.MODEL to detect TV models with ‘4K’ in the name. This approach permits ExoPlayer to output 4K video on Sony Bravia TVs bellow api level 23. The approach is based off official Sony Bravia TV Developer documentation.

Rest of official ExoPlayer README follows.

## Description ##

ExoPlayer is an application level media player for Android. It provides an
alternative to Android’s MediaPlayer API for playing audio and video both
locally and over the Internet. ExoPlayer supports features not currently
supported by Android’s MediaPlayer API, including DASH and SmoothStreaming
adaptive playbacks. Unlike the MediaPlayer API, ExoPlayer is easy to
customize and extend, and can be updated through Play Store application
updates.

## News ##

Read news, hints and tips on the [news][] page.

[news]: https://google.github.io/ExoPlayer/news.html

## Documentation ##

* The [developer guide][] provides a wealth of information to help you get
started.
* The [class reference][] documents the ExoPlayer library classes.
* The [release notes][] document the major changes in each release.

[developer guide]: https://google.github.io/ExoPlayer/guide.html
[class reference]: https://google.github.io/ExoPlayer/doc/reference
[release notes]: https://github.com/google/ExoPlayer/blob/dev/RELEASENOTES.md

## Project branches ##

  * The [master][] branch holds the most recent minor release.
  * Most development work happens on the [dev][] branch.
  * Additional development branches may be established for major features.

[master]: https://github.com/google/ExoPlayer/tree/master
[dev]: https://github.com/google/ExoPlayer/tree/dev

## Using Eclipse ##

The repository includes Eclipse projects for both the ExoPlayer library and its
accompanying demo application. To get started:

  1. Install Eclipse and setup the [Android SDK][].

  1. Open Eclipse and navigate to File->Import->General->Existing Projects into
     Workspace.

  1. Select the root directory of the repository.

  1. Import the ExoPlayerDemo and ExoPlayerLib projects.

[Android SDK]: http://developer.android.com/sdk/index.html


## Using Gradle ##

ExoPlayer can also be built using Gradle. You can include it as a dependent project and build from source:

```
// settings.gradle
include ':app', ':..:ExoPlayer:library'

// app/build.gradle
dependencies {
    compile project(':..:ExoPlayer:library')
}
```

If you want to use ExoPlayer as a jar, run:

```
./gradlew jarRelease
```

and copy library.jar to the libs-folder of your new project.

The project is also available on [jCenter](https://bintray.com/google/exoplayer/exoplayer/view):

```
compile 'com.google.android.exoplayer:exoplayer:rX.X.X'
```

Where `rX.X.X` should be replaced with the desired version.
