# AndroidX Media

AndroidX Media is a collection of libraries for implementing media use cases on
Android, including local playback (via ExoPlayer) and media sessions.

## Current status

AndroidX Media is currently in alpha and we welcome your feedback via the
[issue tracker][]. Please consult the [release notes][] for more details about
the alpha release.

ExoPlayer's new home will be in AndroidX Media, but for now we are publishing it
both in AndroidX Media and via the existing [ExoPlayer project][]. While
AndroidX Media is in alpha we recommend that production apps using ExoPlayer
continue to depend on the existing ExoPlayer project. We are still handling
ExoPlayer issues on the [ExoPlayer issue tracker][].

Updated documentation, including information on migration and a developer guide,
is coming soon.

For a high level overview of the initial version of AndroidX Media please see
the Android Dev Summit talk [What's next for AndroidX Media and ExoPlayer][].

[release notes]: RELEASENOTES.md
[issue tracker]: https://github.com/androidx/media/issues/new
[ExoPlayer project]: https://github.com/google/ExoPlayer
[ExoPlayer issue tracker]: https://github.com/google/ExoPlayer/issues
[What's next for AndroidX Media and ExoPlayer]: https://youtu.be/sTIBDcyCmCg

## Using the libraries

You can get the libraries from [the Google Maven repository][]. It's
also possible to clone this GitHub repository and depend on the modules locally.

[the Google Maven repository]: https://developer.android.com/studio/build/dependencies#google-maven

### From the Google Maven repository

#### 1. Add module dependencies

The easiest way to get started using AndroidX Media is to add gradle
dependencies on the libraries you need in the `build.gradle` file of your app
module.

For example, to depend on ExoPlayer with DASH playback support and UI components
you can add dependencies on the modules like this:

```gradle
implementation 'androidx.media3:media3-exoplayer:1.X.X'
implementation 'androidx.media3:media3-exoplayer-dash:1.X.X'
implementation 'androidx.media3:media3-ui:1.X.X'
```

where `1.X.X` is your preferred version. All modules must be the same version.

Please see the [AndroidX Media3 developer.android.com page][] for more
information, including a full list of library modules.

This repository includes some modules that depend on external libraries that
need to be built manually, and are not available from the Maven repository.
Please see the individual READMEs under the [libraries directory][] for more
details.

[AndroidX Media3 developer.android.com page]: https://developer.android.com/jetpack/androidx/releases/media3#declaring_dependencies
[libraries directory]: libraries

#### 2. Turn on Java 8 support

If not enabled already, you also need to turn on Java 8 support in all
`build.gradle` files depending on AndroidX Media, by adding the following to the
`android` section:

```gradle
compileOptions {
  targetCompatibility JavaVersion.VERSION_1_8
}
```

#### 3. Enable multidex

If your Gradle `minSdkVersion` is 20 or lower, you should
[enable multidex](https://developer.android.com/studio/build/multidex) in order
to prevent build errors.

### Locally

Cloning the repository and depending on the modules locally is required when
using some libraries. It's also a suitable approach if you want to make local
changes, or if you want to use the main branch.

First, clone the repository into a local directory and checkout the desired
branch:

```sh
git clone https://github.com/androidx/media.git
cd media
git checkout main
```

Next, add the following to your project's `settings.gradle` file, replacing
`path/to/media` with the path to your local copy:

```gradle
gradle.ext.androidxMediaModulePrefix = 'media-'
apply from: file("path/to/media/core_settings.gradle")
```

You should now see the AndroidX Media modules appear as part of your project.
You can depend on them as you would on any other local module, for example:

```gradle
implementation project(':media-lib-exoplayer')
implementation project(':media-lib-exoplayer-dash')
implementation project(':media-lib-ui')
```

## Developing AndroidX Media

#### Project branches

Development work happens on the `main` branch. Pull requests should normally be
made to this branch.

We plan to add a release branch soon.

#### Using Android Studio

To develop AndroidX Media using Android Studio, simply open the project in the
root directory of this repository.
