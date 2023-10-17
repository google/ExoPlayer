# MIDI decoder module

The MIDI module provides `MidiExtractor` for parsing standard MIDI files, and
`MidiRenderer` which uses the audio synthesization library [JSyn][] to process
MIDI commands and render the PCM output.

## Getting the module

The easiest way to get the module is to add it as a gradle dependency:

```gradle
implementation 'androidx.media3:media3-exoplayer-midi:1.X.X'
```

where `1.X.X` is the version, which must match the version of the other media
modules being used.

Alternatively, you can clone this GitHub project and depend on the module
locally. Instructions for doing this can be found in the [top level README][].

The module depends on [JSyn][] as a maven dependency from
[jitpack.io](https://jitpack.io) and you will need to define the maven
repository in your build scripts. For example, add

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
```

in the `build.gradle` of module in your app that is using the MIDI module.

## Use in the demo app

Modify the demo app's `build.script` file and uncomment the definition of the
`jitpack.io` maven repository, as well as uncomment the dependency to the MIDI
module in the `dependencies` section.

[JSyn]: https://github.com/philburk/jsyn

[top level README]: ../../README.md

