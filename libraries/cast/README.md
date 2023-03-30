# Cast module

This module provides a [Player][] implementation that controls a Cast receiver
app.

[Player]: ../common/src/main/java/androidx/media3/common/Player.html

## Getting the module

The easiest way to get the module is to add it as a gradle dependency:

```gradle
implementation 'androidx.media3:media3-cast:1.X.X'
```

where `1.X.X` is the version, which must match the version of the other media
modules being used.

Alternatively, you can clone this GitHub project and depend on the module
locally. Instructions for doing this can be found in the [top level README][].

[top level README]: ../../README.md

## Using the module

Create a `CastPlayer` and use it to control a Cast receiver app. Since
`CastPlayer` implements the `Player` interface, it can be passed to all media
components that accept a `Player`, including the UI components provided by the
UI module.

## Links

*   [Javadoc][]

[Javadoc]: https://developer.android.com/reference/androidx/media3/cast/package-summary
