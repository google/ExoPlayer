---
title: Debug logging
---

By default ExoPlayer only logs errors. To log player events, the `EventLogger`
class can be used. The additional logging it provides can be helpful for
understanding what the player is doing, as well as for debugging playback
issues. `EventLogger` implements `AnalyticsListener`, so registering an instance
with a `SimpleExoPlayer` is easy:

```
player.addAnalyticsListener(new EventLogger(trackSelector));
```
{: .language-java}

Passing the `trackSelector` enables additional logging, but is optional and so
`null` can be passed instead.

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
adb logcat EventLogger:* ExoPlayerImpl:* *:s
~~~
{: .language-shell}

### Player information ###

The `ExoPlayerImpl` class delivers two important lines about the player version,
the device and OS the app is running on and the modules of ExoPlayer that have
been loaded:

```
ExoPlayerImpl: Release 2cd6e65 [ExoPlayerLib/2.12.0] [marlin, Pixel XL, Google, 26] [goog.exo.core, goog.exo.ui, goog.exo.dash]
ExoPlayerImpl: Init 2e5194c [ExoPlayerLib/2.12.0] [marlin, Pixel XL, Google, 26]
```

### Playback state ###

Player state changes are logged in lines like the ones below:

```
EventLogger: playWhenReady [eventTime=0.00, mediaPos=0.00, window=0, true, USER_REQUEST]
EventLogger: state [eventTime=0.01, mediaPos=0.00, window=0, BUFFERING]
EventLogger: state [eventTime=0.93, mediaPos=0.00, window=0, period=0, READY]
EventLogger: isPlaying [eventTime=0.93, mediaPos=0.00, window=0, period=0, true]
EventLogger: playWhenReady [eventTime=9.40, mediaPos=8.40, window=0, period=0, false, USER_REQUEST]
EventLogger: isPlaying [eventTime=9.40, mediaPos=8.40, window=0, period=0, false]
EventLogger: playWhenReady [eventTime=10.40, mediaPos=8.40, window=0, period=0, true, USER_REQUEST]
EventLogger: isPlaying [eventTime=10.40, mediaPos=8.40, window=0, period=0, true]
EventLogger: state [eventTime=20.40, mediaPos=18.40, window=0, period=0, ENDED]
EventLogger: isPlaying [eventTime=20.40, mediaPos=18.40, window=0, period=0, false]
```

In this example playback starts 0.93 seconds after the player is prepared. The
user pauses playback after 9.4 seconds, and resumes playback one second later at
10.4 seconds. Playback ends ten seconds later at 20.4 seconds. The common
elements within the square brackets are:

* `[eventTime=float]`: The wall clock time since player creation.
* `[mediaPos=float]`: The current playback position.
* `[window=int]`: The current window index.
* `[period=int]`: The current period in that window.

The final elements in each line indicate the value of the state being reported.

### Media tracks ###

Track information is logged when the available or selected tracks change. This
happens at least once at the start of playback. The example below shows track
logging for an adaptive stream:

```
EventLogger: tracks [eventTime=0.30, mediaPos=0.00, window=0, period=0,
EventLogger:   MediaCodecVideoRenderer [
EventLogger:     Group:0, adaptive_supported=YES [
EventLogger:       [X] Track:0, id=133, mimeType=video/avc, bitrate=261112, codecs=avc1.4d4015, res=426x240, fps=30.0, supported=YES
EventLogger:       [X] Track:1, id=134, mimeType=video/avc, bitrate=671331, codecs=avc1.4d401e, res=640x360, fps=30.0, supported=YES
EventLogger:       [X] Track:2, id=135, mimeType=video/avc, bitrate=1204535, codecs=avc1.4d401f, res=854x480, fps=30.0, supported=YES
EventLogger:       [X] Track:3, id=160, mimeType=video/avc, bitrate=112329, codecs=avc1.4d400c, res=256x144, fps=30.0, supported=YES
EventLogger:       [ ] Track:4, id=136, mimeType=video/avc, bitrate=2400538, codecs=avc1.4d401f, res=1280x720, fps=30.0, supported=NO_EXCEEDS_CAPABILITIES
EventLogger:     ]
EventLogger:   ]
EventLogger:   MediaCodecAudioRenderer [
EventLogger:     Group:0, adaptive_supported=YES_NOT_SEAMLESS [
EventLogger:       [ ] Track:0, id=139, mimeType=audio/mp4a-latm, bitrate=48582, codecs=mp4a.40.5, channels=2, sample_rate=22050, supported=YES
EventLogger:       [X] Track:1, id=140, mimeType=audio/mp4a-latm, bitrate=127868, codecs=mp4a.40.2, channels=2, sample_rate=44100, supported=YES
EventLogger:     ]
EventLogger:   ]
EventLogger: ]
```

In this example, the player has selected four of the five available video
tracks. The fifth video track is not selected because it exceeds the
capabilities of the device, as indicated by `supported=NO_EXCEEDS_CAPABILITIES`.
The player will adapt between the selected video tracks during playback. When
the player adapts from one track to another, it's logged in a line like the one
below:

```
EventLogger: downstreamFormat [eventTime=3.64, mediaPos=3.00, window=0, period=0, id=134, mimeType=video/avc, bitrate=671331, codecs=avc1.4d401e, res=640x360, fps=30.0]
```

This log line indicates that the player switched to the 640x360 resolution video
track three seconds into the media.

### Decoder selection ###

In most cases ExoPlayer renders media using a `MediaCodec` acquired from the
underlying platform. When a decoder is initialized, this is logged in lines like
the ones below:

```
EventLogger: videoDecoderInitialized [0.77, 0.00, window=0, period=0, video, OMX.qcom.video.decoder.avc]
EventLogger: audioDecoderInitialized [0.79, 0.00, window=0, period=0, audio, OMX.google.aac.decoder]
```

[logcat tab]: https://developer.android.com/studio/debug/am-logcat
