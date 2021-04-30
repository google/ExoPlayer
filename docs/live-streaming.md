---
title: Live streaming
---

ExoPlayer plays most adaptive live streams out-of-the-box without any special
configuration. See the [Supported Formats page][] for more details.

Adaptive live streams offer a window of available media that is updated in
regular intervals to move with the current real-time. That means the playback
position will always be somewhere in this window, in most cases close to the
current real-time at which the stream is being produced. The difference between
the current real-time and the playback position is called the *live offset*.

Unlike adaptive live streams, progressive live streams do not have a live window
and can only be played at one position. The documentation on this page is only
relevant to adaptive live streams.
{:.info}

ExoPlayer adjusts the live offset by slightly changing the playback speed.
The player will try to match user and media preferences, but will also try to
react to changing network conditions. For example, if rebuffers occur during
playback, the player will move further away from the live edge. If there is
enough available buffer over a longer period of time, the player will move
closer to the live edge again.

## Detecting and monitoring live playbacks ##

Every time a live window is updated, registered `Player.Listener` instances
will receive an `onTimelineChanged` event. You can retrieve details about the
current live playback by querying various `Player` and `Timeline.Window`
methods, as listed below and shown in the following figure.

{% include figure.html url="/images/live-window.png" index="1" caption="Live window" %}

* `Player.isCurrentWindowLive` indicates whether the currently playing media
  item is a live stream. This value is still true even if the live stream has
  ended.
* `Player.isCurrentWindowDynamic` indicates whether the currently playing media
  item is still being updated. This is usually true for live streams that are
  not yet ended. Note that this flag is also true for non-live streams in some
  cases.
* `Player.getCurrentLiveOffset` returns the offset between the current real
  time and the playback position (if available).
* `Player.getDuration` returns the length of the current live window.
* `Player.getCurrentPosition` returns the playback position relative to the
  start of the live window.
* `Player.getCurrentMediaItem` returns the current media item, where
  `MediaItem.liveConfiguration` contains app-provided overrides for the target
  live offset and live offset adjustment parameters.
* `Player.getCurrentTimeline` returns the current media structure in a
  `Timeline`. The current `Timeline.Window` can be retrieved from the `Timeline`
  using `Player.getCurrentWindowIndex` and `Timeline.getWindow`. Within the
  `Window`:
  * `Window.liveConfiguration` contains the target live offset and and live
    offset adjustment parameters. These values are based on information in the
    media and any app-provided overrides set in `MediaItem.liveConfiguration`.
  * `Window.windowStartTimeMs` is the time since the Unix Epoch at which the
    live window starts.
  * `Window.getCurrentUnixTimeMs` is the time since the Unix Epoch of the
    current real-time. This value may be corrected by a known clock difference
    between the server and the client.
  * `Window.getDefaultPositionMs` is the position in the live window at which
    the player will start playback by default.

## Seeking in live streams ##

You can seek to anywhere within the live window using `Player.seekTo`. The seek
position passed is relative to the start of the live window. For example,
 `seekTo(0)` will seek to the start of the live window. The player will try to
keep the same live offset as the seeked-to position after a seek.

The live window also has a default position at which playback is supposed to
start. This position is usually somewhere close to the live edge. You can seek
to the default position by calling `Player.seekToDefaultPosition`.

## Live playback UI ##

ExoPlayer's [default UI components][] show the duration of the live window and
the current playback position within it. This means the position will appear to
jump backwards each time the live window is updated. If you need different
behavior, for example showing the Unix time or the current live offset, you can
fork `StyledPlayerControlView` and modify it to suit your needs.

There is a [pending feature request (#2213)][] for ExoPlayer's default UI
components to support additional modes when playing live streams.
{:.info}

## Configuring live playback parameters ##

By default, ExoPlayer uses live playback parameters defined by the media. If you
want to configure the live playback parameters yourself, you can set them on a
per `MediaItem` basis by calling `MediaItem.Builder.setLiveXXX` methods. If
you'd like to set these values globally for all items, you can set them on the
`DefaultMediaSourceFactory` provided to the player. In both cases, the provided
values will override parameters defined by the media.

~~~
// Global settings.
SimpleExoPlayer player =
    new SimpleExoPlayer.Builder(context)
        .setMediaSourceFactory(
            new DefaultMediaSourceFactory(context).setLiveTargetOffsetMs(5000))
        .build();

// Per MediaItem settings.
MediaItem mediaItem =
    new MediaItem.Builder()
        .setUri(mediaUri)
        .setLiveMaxPlaybackSpeed(1.02f)
        .build();
player.setMediaItem(mediaItem);
~~~
{: .language-java}

Available configuration values are:

* `targetOffsetMs`: The target live offset. The player will attempt to get
  close to this live offset during playback if possible.
* `minOffsetMs`: The minimum allowed live offset. Even when adjusting the
  offset to current network conditions, the player will not attempt to get below
  this offset during playback.
* `maxOffsetMs`: The maximum allowed live offset. Even when adjusting the
  offset to current network conditions, the player will not attempt to get above
  this offset during playback.
* `minPlaybackSpeed`: The minimum playback speed the player can use to fall back
  when trying to reach the target live offset.
* `maxPlaybackSpeed`: The maximum playback speed the player can use to catch up
  when trying to reach the target live offset.

If automatic playback speed adjustment is not desired, it can be disabled by
setting `minPlaybackSpeed` and `maxPlaybackSpeed` to `1.0f`.

## BehindLiveWindowException ##

The playback position may fall behind the live window, for example if the player
is paused or buffering for a long enough period of time. If this happens then
playback will fail and a `BehindLiveWindowException` will be reported via
`Player.Listener.onPlayerError`. Application code may wish to handle such
errors by resuming playback at the default position. The [PlayerActivity][] of
the demo app exemplifies this approach.

~~~
@Override
public void onPlayerError(ExoPlaybackException e) {
  if (isBehindLiveWindow(e)) {
    // Re-initialize player at the current live window default position.
    player.seekToDefaultPosition();
    player.prepare();
  } else {
    // Handle other errors.
  }
}

private static boolean isBehindLiveWindow(ExoPlaybackException e) {
  if (e.type != ExoPlaybackException.TYPE_SOURCE) {
    return false;
  }
  Throwable cause = e.getSourceException();
  while (cause != null) {
    if (cause instanceof BehindLiveWindowException) {
      return true;
    }
    cause = cause.getCause();
  }
  return false;
}
~~~
{: .language-java}

## Customizing the playback speed adjustment algorithm ##

To stay close to the target live offset, a `LivePlaybackSpeedControl` is used to
make adjustments to the playback speed during live playbacks. It's possible to
implement a custom `LivePlaybackSpeedControl`, or to customize the default
implementation, which is `DefaultLivePlaybackSpeedControl`. In both cases an
instance can be set when building the player:

~~~
SimpleExoPlayer player =
    new SimpleExoPlayer.Builder(context)
        .setLivePlaybackSpeedControl(
            new DefaultLivePlaybackSpeedControl.Builder()
                .setFallbackMaxPlaybackSpeed(1.04f)
                .build())
        .build();
~~~
{: .language-java}

Relevant customization parameters of `DefaultLivePlaybackSpeedControl` are:

* `fallbackMinPlaybackSpeed` and `fallbackMaxPlaybackSpeed`: The minimum and
  maximum playback speeds that can be used for adjustment if neither the media
  nor the app-provided `MediaItem` define limits.
* `proportionalControlFactor`: Controls how smooth the speed adjustment is. A
  high value makes adjustments more sudden and reactive, but also more likely to
  be audible. A smaller value results in a smoother transition between speeds,
  at the cost of being slower.
* `targetLiveOffsetIncrementOnRebufferMs`: This value is added to the target
  live offset whenever a rebuffer occurs, in order to proceed more cautiously.
  This feature can be disabled by setting the value to 0.
* `minPossibleLiveOffsetSmoothingFactor`: An exponential smoothing factor that
  is used to track the minimum possible live offset based on the currently
  buffered media. A value very close to 1 means that the estimation is more
  cautious and may take longer to adjust to improved network conditions, whereas
  a lower value means the estimation will adjust faster at a higher risk of
  running into rebuffers.

[Supported Formats page]: {{ site.baseurl }}/supported-formats.html
[default UI components]: {{ site.baseurl }}/ui-components.html
[pending feature request (#2213)]: https://github.com/google/ExoPlayer/issues/2213
[PlayerActivity]: {{ site.release_v2 }}/demos/main/src/main/java/com/google/android/exoplayer2/demo/PlayerActivity.java
