# ExoPlayer MediaSession extension #

## Description ##

The MediaSession extension mediates between an ExoPlayer instance and a
[MediaSession][]. It automatically retrieves and implements playback actions
and syncs the player state with the state of the media session. The behaviour
can be extended to support other playback and custom actions.

[MediaSession]: https://developer.android.com/reference/android/support/v4/media/session/MediaSessionCompat.html

## Getting the extension ##

The easiest way to use the extension is to add it as a gradle dependency:

```gradle
compile 'com.google.android.exoplayer:extension-mediasession:rX.X.X'
```

where `rX.X.X` is the version, which must match the version of the ExoPlayer
library being used.

Alternatively, you can clone the ExoPlayer repository and depend on the module
locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
