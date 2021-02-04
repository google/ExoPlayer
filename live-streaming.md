---
title: Live streaming
---

ExoPlayer plays most adaptive live streams out-of-the-box without any special
configuration. See the [Supported Formats page][] for more details.

Adaptive live streams usually offer a window of available media that is updated
in regular intervals to move with the current real-time. That means the playback
position will always be somewhere in this window, in most cases close to the
current real-time at which the stream is being produced. The difference between
the current real-time and the playback position is called *live offset*. Note
that some live streams don't have a live window and can only be played at one
position.

ExoPlayer adjusts the live offset by slightly changing the playback speed.
The player will try to match user and media preferences but will also try to
react to changing network conditions. For example, if rebuffers occur during
playback, the player will move further away from the live edge and if there is
enough available buffer over a longer period of time, the player will move
closer to the live edge again.

## Detecting and monitoring live playbacks ##

Every time a live window is updated, registered `Player.EventListener`s will
receive an `onTimelineChanged` event. You can retrieve details about the
current live playback by querying various `Player` and `Timeline.Window` methods
as listed below and shown in the following figure.

{% include figure.html url="/images/live-window.png" index="1" caption="Live window" %}

* `Player.isCurrentWindowLive` - Returns whether the currently playing media
  item is a live stream. This value is still true even if the live stream ended.
* `Player.isCurrentWindowDynamic` - Returns whether the currently playing media
  item is still being updated. This is usually true for live streams that are
  not yet ended. Note that this flag is also true for non-live streams in some
  cases.
* `Player.getCurrentLiveOffset` - Returns the offset between the current real
  time and the playback position (if available).
* `Player.getDuration` - Returns the length of the current live window that can
  be used for seeking.
* `Player.getCurrentPosition` - Returns the playback position relative to the
  start of the live window.
* `Player.getCurrentMediaItem` and `MediaItem.liveConfiguration` - Contains
  app-provided overrides for the target live offset and other configuration
  values that are used for automatic live offset adjustment.
* `Player.getCurrentTimeline` - Returns the current media structure in a
  `Timeline`:
   * `Timeline.Window.liveConfiguration` - Contains the target live offset and
     other configuration values that are used by the player for automatic live
     offset adjustment. These values are based on information in the media and
     app-provided override values set in `MediaItem.LiveConfiguration`.
   * `Timeline.Window.windowStartTimeMs` - The time since the Unix Epoch at
     which the live window starts.
   * `Timeline.Window.getCurrentUnixTimeMs` - The time since the Unix Epoch of
     the current real-time. This value may be corrected by a known clock
     difference between the server and the client.
   * `Timeline.Window.getDefaultPositionMs` - The position in the live window at
     which the player will start playback by default.

## Seeking in live streams ##

You can seek to anywhere within the available live window using `Player.seekTo`
as long as seeking is supported by the live stream
(`Player.isCurrentWindowSeekable`). Note that the seek position is relative to
the start of the available live window similar to the playback position, so for
example `seekTo(0)` would seek to the start of the current live window. The
player will try to keep the same live offset as the seeked-to position after
that seek.

The live window also has a default position at which playback is supposed to
start. This position is usually somewhere close to the real time live edge. You
can seek back to the default position by calling `Player.seekToDefaultPosition`.

## Live playback UI ##

ExoPlayer's [default UI components][] will show the duration of the live window
and the playback position relative to the start of this live window. This means
the position will appear to jump backwards every time the live window is
updated. If you desire another type of UI, for example showing the Unix time or
the current live offset, you can fork `StyledPlayerControlView` to update the
behavior to your needs.

There is a [pending GitHub feature request (#2213)][] to provide more live
stream UI options with the ExoPlayer default UI components.
{:.info}

## Configuring the live playback parameters ##

By default, ExoPlayer uses the values provided by the media and no further
configuration is needed.

If you want to configure the live playback parameters, you can set them on a
per `MediaItem` basis by calling `MediaItem.Builder.setLiveXXX` methods. If
you'd like to set these values globally for all items, you can set them in the
`DefaultMediaSourceFactory` provided to the player. Note that in both cases, the
provided values override values provided by the media itself.

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
* `targetOffsetMs` - The target live offset. The player will attempt to get
  close to this live offset during playback if possible.
* `minOffsetMs` - The minimum allowed live offset. Even when adjusting the
  offset to current network conditions, the player will not attempt to get below
  this offset during playback.
* `maxOffsetMs` - The maximum allowed live offset. Even when adjusting the
  offset to current network conditions, the player will not attempt to get above
  this offset during playback.
* `minPlaybackSpeed` - The minimum playback speed the player can use to fall
  back when trying to reach the target live offset.
* `maxPlaybackSpeed` - The maximum playback speed the player can use to catch up
  when trying to reach the target live offset.

If the automatic playback speed adjustment is not desired, it can be completely
disabled by setting `minPlaybackSpeed` and `maxPlaybackSpeed` to `1.0f`.

## BehindLiveWindowException ##

The playback position may fall behind the available live window, for example if
the player is paused or buffering for a long enough period of time. In this case
a `BehindLiveWindowException` is reported via
`Player.EventListener.onPlayerError`, which can be caught to resume the player
at the live edge if required. The [PlayerActivity][] of the demo app exemplifies
this approach.

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

To stay close to the configured target live offset, the player uses a
`LivePlaybackSpeedControl` component that can control the playback speed for
live playbacks. Besides writing a completely custom component, it's possible to
customize the existing default algorithm if desired. In both cases the component
can be set as part of the `Player` setup:

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

Relevant customization parameters of the `DefaultLivePlaybackSpeedControl` are:
* `fallbackMinPlaybackSpeed`/`fallbackMaxPlaybackSpeed` - The minimum and
  maximum playback speeds that can be used for adjustment if neither the media
  nor the app-provided `MediaItem` define limits.
* `proportionalControlFactor` - The factor controls how smooth the speed
  adjustment is. A high value makes adjustments more sudden and reactive, but
  also more likely to be audible, whereas a smaller value results in a smoother
  transition between speeds at the cost of being slower.
* `targetLiveOffsetIncrementOnRebufferMs` - This value is added to the current
  target live offset whenever a rebuffer occurs to be more cautious in the
  future. If such an increment is not desired, the feature can be disabled by
  setting the value to 0.
* `minPossibleLiveOffsetSmoothingFactor` - An exponential smoothing factor that
  is used to track the minimum possible live offset based on the currently
  buffered media. A value very close to 1 means that the estimation is more
  cautious and may take longer to adjust to improved conditions, whereas a lower
  value means the estimation more quickly adapts to changing network conditions
  at a higher risk of running into rebuffers.

[Supported Formats page]: {{ site.baseurl }}/supported-formats.html
[default UI components]: {{ site.baseurl }}/ui-components.htmlhttps://github.com/google/ExoPlayer/issues/2213
[pending GitHub feature request (#2213)]: https://github.com/google/ExoPlayer/issues/2213
[PlayerActivity]: {{ site.release_v2 }}/demos/main/src/main/java/com/google/android/exoplayer2/demo/PlayerActivity.java
