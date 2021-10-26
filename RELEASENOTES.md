# Release notes

### 1.0.0-alpha01

AndroidX Media is the new home for media support libraries, including ExoPlayer.
The first alpha contains early, functional implementations of libraries for
implementing media use cases, including:

*   ExoPlayer, an application-level media player for Android that is easy to
    customize and extend.
*   Media session functionality, for exposing and controlling playbacks. This
    new session module uses the same `Player` interface as ExoPlayer.
*   UI components for building media playback user interfaces.
*   Modules wrapping functionality in other libraries for use with ExoPlayer,
    for example, ad insertion via the IMA SDK.

ExoPlayer was previously hosted in a separate
[ExoPlayer GitHub project](https://github.com/google/ExoPlayer). In AndroidX
Media its package name is `androidx.media3.exoplayer`. We plan to continue to
maintain and release the ExoPlayer GitHub project for a while to give apps time
to migrate. AndroidX Media has replacements for all the ExoPlayer modules,
except for the legacy media2 and mediasession extensions, which are together
replaced by the new `media3-session` module. This provides direct integration
between players and media sessions without needing to use an adapter/connector
class.
