---
title: Listening to player events
---

Events such as changes in state and playback errors are reported to registered
[`Player.EventListener`][] instances. Registering a listener to receive such
events is easy:

~~~
// Add a listener to receive events from the player.
player.addListener(eventListener);
~~~
{: .language-java}

`Player.EventListener` has empty default methods, so you only need to implement
the methods you're interested in. See the [Javadoc][] for a full description of
the methods and when they're called. Two of the most important are
`onPlayerStateChanged` and `onPlayerError`, which are described in more detail
below.

### Player state changes ###

Changes in player state can be received by implementing
`onPlayerStateChanged(boolean playWhenReady, int playbackState)` in a registered
`Player.EventListener`. The player can be in one of four playback states:

* `Player.STATE_IDLE`: This is the initial state, the state when the player is
  stopped, and when playback failed.
* `Player.STATE_BUFFERING`: The player is not able to immediately play from its
  current position. This mostly happens because more data needs to be loaded.
* `Player.STATE_READY`: The player is able to immediately play from its current
  position.
* `Player.STATE_ENDED`: The player finished playing all media.

In addition to these states, the player has a `playWhenReady` flag to indicate
the user intention to play. The player is only playing if the state is
`Player.STATE_READY` and `playWhenReady=true`.

~~~
@Override
public void onPlayerStateChanged(
      boolean playWhenReady, @Player.State int playbackState) {
  if (playWhenReady && playbackState == Player.STATE_READY) {
    // Active playback.
  } else if (playWhenReady) {
    // Not playing because playback ended, the player is buffering, stopped or
    // failed. Check playbackState and player.getPlaybackError for details.
  } else {
    // Paused by app.
  }
}
~~~
{: .language-java }

### Player errors ###

Errors that cause playback to fail can be received by implementing
`onPlayerError(ExoPlaybackException error)` in a registered
`Player.EventListener`. When a failure occurs, this method will be called
immediately before the playback state transitions to `Player.STATE_IDLE`.
[`ExoPlaybackException`][] has a `type` field, as well as corresponding getter
methods that return cause exceptions providing more information about the
failure. The example below shows how to detect when a playback has failed due to
a HTTP networking issue.

~~~
@Override
public void onPlayerError(ExoPlaybackException error) {
  if (error.type == ExoPlaybackException.TYPE_SOURCE) {
    IOException cause = error.getSourceException();
    if (cause instanceof HttpDataSourceException) {
      // An HTTP error occurred.
      HttpDataSourceException httpError = (HttpDataSourceException) cause;
      // This is the request for which the error occurred.
      DataSpec requestDataSpec = httpError.dataSpec;
      // It's possible to find out more about the error both by casting and by
      // querying the cause.
      if (httpError instanceof HttpDataSource.InvalidResponseCodeException) {
        // Cast to InvalidResponseCodeException and retrieve the response code,
        // message and headers.
      } else {
        // Try calling httpError.getCause() to retrieve the underlying cause,
        // although note that it may be null.
      }
    }
  }
}
~~~
{: .language-java }

## Additional SimpleExoPlayer listeners ##

When using `SimpleExoPlayer`, additional listeners can be registered with the
player.

* `addAnalyticsListener`: Listen to detailed events that may be useful for
  analytics and reporting purposes.
* `addVideoListener`: Listen to events related to video rendering that may be
  useful for adjusting the UI (e.g., the aspect ratio of the `Surface` onto
  which video is being rendered).
* `addAudioListener`: Listen to events related to audio, such as when an audio
  session ID is set, and when the player volume is changed.
* `addTextOutput`: Listen to changes in the subtitle or caption cues.
* `addMetadataOutput`: Listen to timed metadata events, such as timed ID3 and
  EMSG data.

ExoPlayer's UI components, such as `PlayerView`, will register themselves as
listeners to events that they are interested in. Hence manual registration using
the methods above is only useful for applications that implement their own
player UI, or need to listen to events for some other purpose.

## Using EventLogger ##

By default ExoPlayer only logs errors. To log player events to the console, the
`EventLogger` class can be used. The additional logging it provides can be
helpful for understanding what the player is doing, as well as for debugging
playback issues. `EventLogger` implements `AnalyticsListener`, and so
registering an instance with a `SimpleExoPlayer` is easy:

```
player.addAnalyticsListener(new EventLogger(trackSelector));
```
{: .language-java}

### Intepreting the log output ###

The easiest way to observe the log is using Android Studio's [logcat tab][]. You
can select your app as debuggable process by the package name (
`com.google.android.exoplayer2.demo` if using the demo app) and tell the logcat
tab to log only for that app by selecting 'show only selected application'. It's
possible to further filter the logging with the expression
`EventLogger|ExoPlayerImpl`, to get only logging from `EventLogger` and the
player itself.

An alternative to using Android Studio's logcat tab is to use the console. For
example:

~~~
adb logcat | grep 'EventLogger\|ExoPlayerImpl'
~~~
{: .language-shell}

#### Player information ####

The `ExoPlayerImpl` class delivers two important lines about the player version,
the device and OS the app is running on and the modules of ExoPlayer that have
been loaded:

```
ExoPlayerImpl: Release 2cd6e65 [ExoPlayerLib/2.9.6] [marlin, Pixel XL, Google, 26] [goog.exo.core, goog.exo.ui, goog.exo.dash]
ExoPlayerImpl: Init 2e5194c [ExoPlayerLib/2.9.6] [marlin, Pixel XL, Google, 26]
```

#### Playback state ####

Player state changes are logged in lines like the ones below. In this example
the playback never rebuffers (after the initial buffering) and is paused by the
user once:

```
EventLogger: state [0.00, 0.00, window=0, true, BUFFERING]
EventLogger: state [0.92, 0.04, window=0, period=0, true, READY]
EventLogger: state [11.53, 10.60, window=0, period=0, false, READY]
EventLogger: state [14.26, 10.60, window=0, period=0, true, READY]
EventLogger: state [131.89, 128.27, window=0, period=0, true, ENDED]
```

The elements within the square brackets are:

* `[float]`: The wall clock time since player creation.
* `[float]`: The current playback position.
* `[window=int]`: The current window index.
* `[period=int]`: The current period in that window.
* `[boolean]`: The `playWhenReady` flag.
* `[string]`: The current playback state.

#### Media tracks ####

Track information is logged when the available or selected tracks change. This
happens at least once at the start of playback. The example below shows track
logging for an adaptive stream:

```
EventLogger: tracksChanged [2.32, 0.00, window=0, period=0,
EventLogger:   Renderer:0 [
EventLogger:     Group:0, adaptive_supported=YES [
EventLogger:       [X] Track:0, id=133, mimeType=video/avc, bitrate=261112, codecs=avc1.4d4015, res=426x240, fps=30.0, supported=YES
EventLogger:       [X] Track:1, id=134, mimeType=video/avc, bitrate=671331, codecs=avc1.4d401e, res=640x360, fps=30.0, supported=YES
EventLogger:       [X] Track:2, id=135, mimeType=video/avc, bitrate=1204535, codecs=avc1.4d401f, res=854x480, fps=30.0, supported=YES
EventLogger:       [X] Track:3, id=160, mimeType=video/avc, bitrate=112329, codecs=avc1.4d400c, res=256x144, fps=30.0, supported=YES
EventLogger:       [X] Track:4, id=136, mimeType=video/avc, bitrate=2400538, codecs=avc1.4d401f, res=1280x720, fps=30.0, supported=YES
EventLogger:     ]
EventLogger:   ]
EventLogger:   Renderer:1 [
EventLogger:     Group:0, adaptive_supported=YES_NOT_SEAMLESS [
EventLogger:       [ ] Track:0, id=139, mimeType=audio/mp4a-latm, bitrate=48582, codecs=mp4a.40.5, channels=2, sample_rate=22050, supported=YES
EventLogger:       [X] Track:1, id=140, mimeType=audio/mp4a-latm, bitrate=127868, codecs=mp4a.40.2, channels=2, sample_rate=44100, supported=YES
EventLogger:     ]
EventLogger:   ]
EventLogger: ]
```

When playing an adaptive stream, changes in the format being played are logged
during playback, along with the properties of the selected tracks:

```
EventLogger: downstreamFormatChanged [3.64, 0.00, window=0, period=0, id=134, mimeType=video/avc, bitrate=671331, codecs=avc1.4d401e, res=640x360, fps=30.0]
EventLogger: downstreamFormatChanged [3.64, 0.00, window=0, period=0, id=140, mimeType=audio/mp4a-latm, bitrate=127868, codecs=mp4a.40.2, channels=2, sample_rate=44100]
```

#### Decoder selection ####

In most cases ExoPlayer renders media using a `MediaCodec` acquired from the
underlying platform. Before any playback state is reported you'll find logging
telling you which decoders have been initialized. For example:

```
EventLogger: decoderInitialized [0.77, 0.00, window=0, period=0, video, OMX.qcom.video.decoder.avc]
EventLogger: decoderInitialized [0.79, 0.00, window=0, period=0, audio, OMX.google.aac.decoder]
```

[`Player.EventListener`]: {{ site.exo_sdk }}/Player.EventListener.html
[Javadoc]: {{ site.exo_sdk }}/Player.EventListener.html
[`ExoPlaybackException`]: {{ site.exo_sdk }}/ExoPlaybackException.html
[log output]: event-logger.html
[`Parameters`]: {{ site.exo_sdk }}/trackselection/DefaultTrackSelector.Parameters.html
[`ParametersBuilder`]: {{ site.exo_sdk }}/trackselection/DefaultTrackSelector.ParametersBuilder.html
[`DefaultTrackSelector`]: {{ site.exo_sdk }}/trackselection/DefaultTrackSelector.html
[ExoPlayer demo app]: {{ site.baseurl }}/demo-application.html#playing-your-own-content
[latest ExoPlayer version]: https://github.com/google/ExoPlayer/releases
[logcat tab]: https://developer.android.com/studio/debug/am-logcat
