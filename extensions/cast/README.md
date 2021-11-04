# Cast module

This module provides a [Player][] implementation that controls a Cast receiver
app.

[Player]: https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/Player.html

## Getting the module

The easiest way to get the module is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:extension-cast:2.X.X'
```

where `2.X.X` is the version, which must match the version of the other media
modules being used.

Alternatively, you can clone this GitHub project and depend on the module
locally. Instructions for doing this can be found in the [top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md

## Using the module

Create a `CastPlayer` and use it to control a Cast receiver app. Since
`CastPlayer` implements the `Player` interface, it can be passed to all media
components that accept a `Player`, including the UI components provided by the
UI module.

## Links

*   [Javadoc][]

[Javadoc]: https://exoplayer.dev/doc/reference/index.html
