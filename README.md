# ExoPlayer #

ExoPlayer is an application level media player for Android. It provides an
alternative to Android’s MediaPlayer API for playing audio and video both
locally and over the Internet. ExoPlayer supports features not currently
supported by Android’s MediaPlayer API, including DASH and SmoothStreaming
adaptive playbacks. Unlike the MediaPlayer API, ExoPlayer is easy to customize
and extend, and can be updated through Play Store application updates.

## Documentation ##

* The [developer guide][] provides a wealth of information to help you get
  started.
* The [class reference][] documents the ExoPlayer library classes.
* The [release notes][] document the major changes in each release.

[developer guide]: https://google.github.io/ExoPlayer/guide.html
[class reference]: https://google.github.io/ExoPlayer/doc/reference
[release notes]: https://github.com/google/ExoPlayer/blob/dev-v2/RELEASENOTES.md

## Using ExoPlayer ##

ExoPlayer modules can be obtained via jCenter. It's also possible to clone the
repository and depend on the modules locally.

### Via jCenter ###

The easiest way to get started using ExoPlayer is to add it as a gradle
dependency. You need to make sure you have the jcenter repository included in
the `build.gradle` file in the root of your project:

```gradle
repositories {
    jcenter()
}
```

Next add a gradle compile dependency to the `build.gradle` file of your app
module. The following will add a dependency to the full ExoPlayer library:

```gradle
compile 'com.google.android.exoplayer:exoplayer:r2.X.X'
```

where `r2.X.X` is your preferred version. Alternatively, you can depend on only
the library modules that you actually need. For example the following will add
dependencies on the Core, DASH and UI library modules, as might be required for
an app that plays DASH content:

```gradle
compile 'com.google.android.exoplayer:exoplayer-core:r2.X.X'
compile 'com.google.android.exoplayer:exoplayer-dash:r2.X.X'
compile 'com.google.android.exoplayer:exoplayer-ui:r2.X.X'
```

The available modules are listed below. Adding a dependency to the full
ExoPlayer library is equivalent to adding dependencies on all of the modules
individually.

* `exoplayer-core`: Core functionality (required).
* `exoplayer-dash`: Support for DASH content.
* `exoplayer-hls`: Support for HLS content.
* `exoplayer-smoothstreaming`: Support for SmoothStreaming content.
* `exoplayer-ui`: UI components and resources for use with ExoPlayer.

For more details, see the project on [Bintray][]. For information about the
latest versions, see the [Release notes][].

[Bintray]: https://bintray.com/google/exoplayer
[Release notes]: https://github.com/google/ExoPlayer/blob/release-v2/RELEASENOTES.md

### Locally ###

Cloning the repository and depending on the modules locally is required when
using some ExoPlayer extension modules. It's also a suitable approach if you
want to make local changes to ExoPlayer, or if you want to use a development
branch.

First, clone the repository into a local directory and checkout the desired
branch:

```sh
git clone https://github.com/google/ExoPlayer.git
git checkout release-v2
```

Next, add the following to your project's `settings.gradle` file, replacing
`path/to/exoplayer` with the path to your local copy:

```gradle
gradle.ext.exoplayerRoot = 'path/to/exoplayer'
gradle.ext.exoplayerModulePrefix = 'exoplayer-'
apply from: new File(gradle.ext.exoplayerRoot, 'core_settings.gradle')
```

You should now see the ExoPlayer modules appear as part of your project. You can
depend on them as you would on any other local module, for example:

```gradle
compile project(':exoplayer-library-core')
compile project(':exoplayer-library-dash')
compile project(':exoplayer-library-ui)
```

## Developing ExoPlayer ##

#### Project branches ####

  * The project has `dev-vX` and `release-vX` branches, where `X` is the major
    version number.
  * Most development work happens on the `dev-vX` branch with the highest major
    version number. Pull requests should normally be made to this branch.
  * Bug fixes may be submitted to older `dev-vX` branches. When doing this, the
    same (or an equivalent) fix should also be submitted to all subsequent
    `dev-vX` branches.
  * A `release-vX` branch holds the most recent stable release for major version
    `X`.

#### Using Android Studio ####

To develop ExoPlayer using Android Studio, simply open the ExoPlayer project in
the root directory of the repository.
