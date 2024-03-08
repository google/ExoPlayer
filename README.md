# AndroidX Media

AndroidX Media is a collection of libraries for implementing media use cases on
Android, including local playback (via ExoPlayer), video editing (via Transformer) and media sessions.

## Documentation

*   The [developer guide][] provides a wealth of information.
*   The [class reference][] documents the classes and methods.
*   The [release notes][] document the major changes in each release.
*   The [media dev center][] provides samples and guidelines.
*   Follow our [developer blog][] to keep up to date with the latest
    developments!

[developer guide]: https://developer.android.com/guide/topics/media/media3
[class reference]: https://developer.android.com/reference/androidx/media3/common/package-summary
[release notes]: RELEASENOTES.md
[media dev center]: https://developer.android.com/media
[developer blog]: https://medium.com/google-exoplayer

## Migration for existing ExoPlayer and MediaSession projects

You'll find a [migration guide for existing ExoPlayer and MediaSession users][]
on developer.android.com.

[migration guide for existing ExoPlayer and MediaSession users]: https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide

## API stability

AndroidX Media releases provide API stability guarantees, ensuring that the API
surface remains backwards compatible for the most commonly used APIs. APIs
intended for more advanced use cases are marked as unstable. To use an unstable
method or class without lint warnings, you’ll need to add the OptIn annotation
before using it. For more information see the [UnstableApi][] documentation.

[UnstableApi]: https://github.com/androidx/media/blob/main/libraries/common/src/main/java/androidx/media3/common/util/UnstableApi.java

## Using the libraries

You can get the libraries from [the Google Maven repository][]. It's
also possible to clone this GitHub repository and depend on the modules locally.

[the Google Maven repository]: https://developer.android.com/studio/build/dependencies#google-maven

### From the Google Maven repository

#### 1. Add module dependencies

The easiest way to get started using AndroidX Media is to add gradle
dependencies on the libraries you need in the `build.gradle.kts` file of your
app module.

For example, to depend on ExoPlayer with DASH playback support and UI components
you can add dependencies on the modules like this:

```kotlin
implementation("androidx.media3:media3-exoplayer:1.X.X")
implementation("androidx.media3:media3-exoplayer-dash:1.X.X")
implementation("androidx.media3:media3-ui:1.X.X")
```

Or in Gradle Groovy DSL `build.gradle`:

```groovy
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
`build.gradle.kts` files depending on AndroidX Media, by adding the following to
the `android` section:

```kotlin
compileOptions {
  targetCompatibility = JavaVersion.VERSION_1_8
}
```

Or in Gradle Groovy DSL `build.gradle`:

```groovy
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
changes, or if you want to use the `main` branch.

First, clone the repository into a local directory:

```sh
git clone https://github.com/androidx/media.git
cd media
```

Next, add the following to your project's `settings.gradle.kts` file, replacing
`path/to/media` with the path to your local copy:

```kotlin
gradle.extra.apply {
  set("androidxMediaModulePrefix", "media-")
}
apply(from = file("path/to/media/core_settings.gradle"))
```

Or in Gradle Groovy DSL `settings.gradle`:

```groovy
gradle.ext.androidxMediaModulePrefix = 'media-'
apply from: file("path/to/media/core_settings.gradle")
```

You should now see the AndroidX Media modules appear as part of your project.
You can depend on them from `build.gradle.kts` as you would on any other local
module, for example:

```kotlin
implementation(project(":media-lib-exoplayer"))
implementation(project(":media-lib-exoplayer-dash"))
implementation(project(":media-lib-ui"))
```

Or in Gradle Groovy DSL `build.gradle`:

```groovy
implementation project(':media-lib-exoplayer')
implementation project(':media-lib-exoplayer-dash')
implementation project(':media-lib-ui')
```

#### MIDI module

By default the [MIDI module](libraries/decoder_midi) is disabled as a local
dependency, because it requires additional Maven repository config. If you want
to use it as a local dependency, please configure the JitPack repository as
[described in the module README](libraries/decoder_midi/README.md#getting-the-module),
and then enable building the module in your `settings.gradle.kts` file:

```kotlin
gradle.extra.apply {
  set("androidxMediaEnableMidiModule", true)
}
```

Or in Gradle Groovy DSL `settings.gradle`:

```groovy
gradle.ext.androidxMediaEnableMidiModule = true
```

## Developing AndroidX Media

#### Project branches

Development work happens on the `main` branch. Pull requests should normally be
made to this branch.

The `release` branch holds the most recent stable release.

#### Using Android Studio

To develop AndroidX Media using Android Studio, simply open the project in the
root directory of this repository.
