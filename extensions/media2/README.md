# Media2 module

The Media2 module provides builders for [SessionPlayer][] and
[MediaSession.SessionCallback][] in the [Media2 library][].

Compared to [MediaSessionConnector][] that uses [MediaSessionCompat][], this provides finer grained
control for incoming calls, so you can selectively allow/reject commands per controller.

## Getting the module

The easiest way to get the module is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:extension-media2:2.X.X'
```

where `2.X.X` is the version, which must match the version of the other media
modules being used.

Alternatively, you can clone this GitHub project and depend on the module
locally. Instructions for doing this can be found in the [top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md

## Using the module

### Using `SessionPlayerConnector`

`SessionPlayerConnector` is a [SessionPlayer][] implementation wrapping a given `Player`.
You can use a [SessionPlayer][] instance to build a [MediaSession][], or to set the player
associated with a [VideoView][] or [MediaControlView][]

### Using `SessionCallbackBuilder`

`SessionCallbackBuilder` lets you build a [MediaSession.SessionCallback][] instance given its
collaborators. You can use a [MediaSession.SessionCallback][] to build a [MediaSession][].

## Links

* [Javadoc][]: Classes matching
  `com.google.android.exoplayer2.ext.media2.*` belong to this module.

[Javadoc]: https://exoplayer.dev/doc/reference/index.html

[SessionPlayer]: https://developer.android.com/reference/androidx/media2/common/SessionPlayer
[MediaSession]: https://developer.android.com/reference/androidx/media2/session/MediaSession
[MediaSession.SessionCallback]: https://developer.android.com/reference/androidx/media2/session/MediaSession.SessionCallback
[Media2 library]: https://developer.android.com/jetpack/androidx/releases/media2
[MediaSessionCompat]: https://developer.android.com/reference/android/support/v4/media/session/MediaSessionCompat
[MediaSessionConnector]: https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/ext/mediasession/MediaSessionConnector.html
[VideoView]: https://developer.android.com/reference/androidx/media2/widget/VideoView
[MediaControlView]: https://developer.android.com/reference/androidx/media2/widget/MediaControlView
