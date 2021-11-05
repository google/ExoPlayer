# Leanback UI module

This module provides a [PlayerAdapter][] wrapper for `Player`, making it
possible to connect `Player` implementations such as `ExoPlayer` to the playback
widgets provided by `androidx.leanback:leanback`.

[PlayerAdapter]: https://developer.android.com/reference/android/support/v17/leanback/media/PlayerAdapter.html
[Leanback]: https://developer.android.com/reference/android/support/v17/leanback/package-summary.html

## Getting the module

The easiest way to get the module is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:extension-leanback:2.X.X'
```

where `2.X.X` is the version, which must match the version of the other media
modules being used.

Alternatively, you can clone this GitHub project and depend on the module
locally. Instructions for doing this can be found in the [top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md

## Links

* [Javadoc][]

[Javadoc]: https://exoplayer.dev/doc/reference/index.html
