# Leanback UI module

This module provides a [PlayerAdapter][] wrapper for `Player`, making it
possible to connect `Player` implementations such as `ExoPlayer` to the playback
widgets provided by `androidx.leanback:leanback`.

[PlayerAdapter]: https://developer.android.com/reference/android/support/v17/leanback/media/PlayerAdapter.html
[Leanback]: https://developer.android.com/reference/android/support/v17/leanback/package-summary.html

## Getting the module

The easiest way to get the module is to add it as a gradle dependency:

```gradle
implementation 'androidx.media3:media3-ui-leanback:1.X.X'
```

where `1.X.X` is the version, which must match the version of the other media
modules being used.

Alternatively, you can clone this GitHub project and depend on the module
locally. Instructions for doing this can be found in the [top level README][].

[top level README]: ../../README.md
