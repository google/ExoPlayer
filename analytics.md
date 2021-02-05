---
title: Analytics
---

ExoPlayer supports a wide range of playback analytics needs. Ultimately,
analytics is about collecting, interpreting, aggregating and summarizing data
from playbacks. This data can be used either on the device, for example for
logging, debugging, or to inform future playback decisions, or reported to a
server to monitor playbacks across all devices.

An analytics system usually needs to collect events first, and then process them
further to make them meaningful:

1. **Event collection**
   This step can be done by registering `AnalyticsListener`s in ExoPlayer. It
   will report events as they happen in various components of the Player. In
   addition, it will associate each event with the right media item in the
   playlist and add the correct metadata like playback position and timestamps.
1. **Event processing**
   To pull out meaningful information from the events, it's necessary to process
   them further. Some or all of these processing steps may happen on a server,
   but it is often simpler to perform them on the device to reduce the amount
   of information that needs to be transferred and stored on a server. ExoPlayer
   provides `PlaybackStatsListener`, which allows you to perform the following
   processing steps:
   1. **Event interpretation** - To be useful for analytics purposes, the events
      need to be interpreted in the context of a single playback. For example
      the raw event of a player state change to `STATE_BUFFERING` may correspond
      to the initial buffering in a new playback, it may be a rebuffer, or the
      buffering happening after a seek.
   1. **State tracking** - This step converts events to counters. For example,
      the state change events need to be converted to a counter tracking how
      much time is spent in each playback state. The result is a
      basic set of analytics data for a single playback.
   1. **Aggregation** - This combines the single-playback analytics data of
      multiple playbacks together (mostly by adding up all data) to get
      aggregated analytics data.
   1. **Calculation of summary metrics** - Many of the most useful metrics are
      those that compute averages or combine the basic analytics data values in
      other ways. These summary metrics can be computed for single playbacks as
      well as any set of aggregated data.

### Raw events and media association using AnalyticsListener ###

Raw playback events from all components are reported to `AnalyticsListener`
implementations. You can easily add your own listener and overwrite only the
methods you are interested in:

~~~
simpleExoPlayer.addAnalyticsListener(new AnalyticsListener() {
    @Override
    public void onPlaybackStateChanged(
        EventTime eventTime, @Player.State int state) {
    }

    @Override
    public void onDroppedVideoFrames(
        EventTime eventTime, int droppedFrames, long elapsedMs) {
    }
});
~~~
{: .language-java}

Each callback contains an `EventTime` that adds playlist associations and timing
metadata to the event:

* `realtimeMs` - The wall clock time of the event
* `Timeline` / `windowIndex` / `mediaPeriodId` - The item in the playlist this
  event belongs to. The playlist is described by the `Timeline`, with the
  `windowIndex` in this timeline corresponding to the `MediaItem` at this index.
  The `mediaPeriodId` contains optional additional information, for example
  indicating which period in a multi-period source this event applies to,
  whether it applies to an ad in this `MediaItem` and to which repetition of an
  item this event applies to, for example if the same item is repeated multiple
  times with `Player.REPEAT_MODE_ONE`.
* `eventPlaybackPositionMs` - The playback position in this item at which the
  event happened.
* `currentTimeline` / `currentWindowIndex` / `currentMediaPeriodId` /
  `currentPlaybackPositionMs` - Same as above but for the currently playing item
  and position in the playlist, which may be different from the item this event
  belongs to.

### Processed events and aggregated analytics data with PlaybackStatsListener ###

For all other analytics data processing steps that go beyond raw events and that
are done on the device, you can use `PlaybackStatsListener`. This listener
allows you to obtain `PlaybackStats` with counters and derived metrics for
various aspects of playback:

* Overall quality metrics, for example error counts or total playback time.
* Adaptive playback quality metrics, for example video height or buffer time.
* Rendering quality metrics, for example dropped frames or audio underruns.
* Resource usage metrics, for example network transfer bytes and times.

You will find a complete list of supported counts and derived metrics in the
[Javadoc of `PlaybackStats`][].

`PlaybackStatsListener` is an `AnalyticsListener`, which can be added in the
same way as custom `AnalyticsListeners`. It will collect separate
`PlaybackStats` for each MediaItem in the playlist and also each client-side
inserted ad within these items. You can provide a callback to
`PlaybackStatsListener` to get informed about finished playbacks and use the
`EventTime` passed to the callback to identify which playback finished. If this
definition of playback is too fine-grained for your purposes, you can [aggregate
the analytics data][] of multiple sessions. It's also possible to query the
`PlaybackStats` for the currently playing session at any time using
`PlaybackStatsListener.getPlaybackStats()`.

~~~
simpleExoPlayer.addAnalyticsListener(
    new PlaybackStatsListener(
        /* keepHistory= */ true, (eventTime, playbackStats) -> {
          // Analytics data for the session started at `eventTime` is ready.
        }));
~~~
{: .language-java}

The constructor of `PlaybackStatsListener` gives the option to keep the full
history of processed events. Note that this may incur an unknown memory overhead
depending on the length of the playback and the number of events. Therefore you
should only turn it on if you need access to the processed events, rather than
just to the final analytics data.

Note that `PlaybackStats` uses an extended set of states to indicate not only
the state of the media, but also the user intention to play and more detailed
information, for example why playback is interrupted or ended:

| Playback state | User intention to play  | No intention to play |
|:---|:---|:---|
| Before playback | JOINING_FOREGROUND | NOT_STARTED, JOINING_BACKGROUND |
| Active playback | PLAYING | |
| Interrupted playback | BUFFERING, SEEKING | PAUSED, PAUSED_BUFFERING, SUPPRESSED, SUPPRESSED_BUFFERING, INTERRUPTED_BY_AD |
| End states | | ENDED, STOPPED, FAILED, ABANDONED |

The user intention to play is important to distinguish times when the user was
actively waiting for playback to continue from passive wait times. For example,
`PlaybackStats.getTotalWaitTimeMs` returns the total time spent in
`JOINING_FOREGROUND`, `BUFFERING` or `SEEKING` states, but not the time when
playback was paused. And similarly, `PlaybackStats.getTotalPlayAndWaitTimeMs`
will return the total time with a user intention to play, that is the total
active wait time and the total time spent in the `PLAYING` state.

#### Processed and interpreted events ####

You can record processed and interpreted events by using the
`PlaybackStatsListener` with `keepHistory=true`. The resulting `PlaybackStats`
object will contain the following event lists:

* `playbackStateHistory` - An ordered list of extended playback states with
  the `EventTime` at which they started to apply. You can also use
  `PlaybackStats.getPlaybackStateAtTime` to look up the state at a given wall
  clock time.
* `mediaTimeHistory` - A history of wall clock time and media time pairs
  allowing you to reconstruct which parts of the media have been played at which
  time. You can also use `PlaybackStats.getMediaTimeMsAtRealtimeMs` to look up
  the playback position at a given wall clock time.
* `videoFormatHistory` / `audioFormatHistory` - Ordered lists of video and audio
  formats used during playback with the `EventTime` at which they started to be
  used.
* `fatalErrorHistory` / `nonFatalErrorHistory` - Ordered lists of fatal and
  non-fatal errors with the `EventTime` at which they occurred. Fatal errors are
  those that ended playback, whereas non-fatal errors may have been recoverable.

#### Single-playback analytics data ####

This data is automatically collected if you use `PlaybackStatsListener` (even
with `keepHistory=false`). The final values are all the public class
fields you can find in the [Javadoc of `PlaybackStats`][] and the playback state
durations returned by `getPlaybackStateDurationMs`. For convenience, you'll also
find methods like `getTotalPlayTimeMs` or `getTotalWaitTimeMs` that return the
duration of specific playback state combinations.

~~~
Log.d("DEBUG", "Playback summary: "
    + "play time = " + playbackStats.getTotalPlayTimeMs()
    + ", rebuffers = " + playbackStats.totalRebufferCount);
~~~
{: .language-java}

Some values like `totalVideoFormatHeightTimeProduct` are only useful when
calculating derived summary metrics like the average video height. But in order
to make them aggregatable with other playbacks, it's important to keep these
values around or report them to your server if aggregation happens in a backend.
{:.info}

#### Aggregate analytics data of multiple playbacks ####

You can combine multiple `PlaybackStats` together by calling
`PlaybackStats.merge`. The resulting analytics data will contain the aggregated
data of all merged playbacks. Note that it won't contain the history of
individual playback events anymore as these are not aggregatable.

As long as you still have the `PlaybackStatsListener` you can also call
`PlaybackStatsListener.getCombinedPlaybackStats` to get an aggregated view of
all analytics data that was collected in the lifetime of this
`PlaybackStatsListener`.

#### Calculated summary metrics ####

In addition to the basic analytics data, `PlaybackStats` provides many methods
to calculate summary metrics.

~~~
Log.d("DEBUG", "Additional calculated summary metrics: "
    + "average video bitrate = " + playbackStats.getMeanVideoFormatBitrate()
    + ", mean time between rebuffers = "
        + playbackStats.getMeanTimeBetweenRebuffers());
~~~
{: .language-java}

Note that most of these calculations involve division and the resulting values
can't be used to further aggregate them over multiple playbacks. If you intend
to calculate these metrics for multiple playbacks you need to aggregate the
basic analytics data first and then call the relevant methods on `PlaybackStats`
or replicate the same calculation on your server.

## Advanced topics ##

### Associating analytics data with playback metadata ###

Especially when collecting analytics data on an individual playback level, you
may wish to combine the playback analytics data with metadata about the
playback, user or device, for example media content IDs or the device model.

It's advisable to set media-specific metadata with `MediaItem.Builder.setTag`.
This way the metadata tag is part of the `EventTime` reported for raw events and
when `PlaybackStats` are finished. This is particularly important when dealing
with playlists that can be changed dynamically to ensure you don't have to rely
on indices in the playlist that may change at any time.

~~~
new PlaybackStatsListener(
    /* keepHistory= */ false, (eventTime, playbackStats) -> {
      Object mediaTag =
          eventTime.timeline.getWindow(eventTime.windowIndex, new Window())
              .mediaItem.playbackProperties.tag;
      // Report playbackStats with mediaTag metadata.
    });
~~~
{: .language-java}

### Reporting custom analytics events ###

In case you need to add custom events to the analytics data, you need to save
these events in your own data structure and combine them with the reported
`PlaybackStats` later. If it helps, you can extend `AnalyticsCollector` to be
able to generate `EventTime` instances for your custom events and send them to
the already registered listeners as shown in the following example.

~~~
interface ExtendedListener extends AnalyticsListener {
  void onCustomEvent(EventTime eventTime);
}

class ExtendedCollector extends AnalyticsCollector {
 public void customEvent() {
   EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
   sendEvent(eventTime, CUSTOM_EVENT_ID, listener -> {
     if (listener instanceof ExtendedListener) {
       ((ExtendedListener) listener).onCustomEvent(eventTime);
     }
   });
 }
}

// Usage - Setup and listener registration.
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context)
    .setAnalyticsCollector(new ExtendedCollector())
    .build();
player.addAnalyticsListener(new ExtendedListener() {
  @Override
  public void onCustomEvent(EventTime eventTime) {
    // Save custom event for analytics data.
  }
});
// Usage - Triggering the custom event.
((ExtendedCollector) player.getAnalyticsCollector()).customEvent();
~~~
{: .language-java}

[Javadoc of `PlaybackStats`]: {{ site.exo_sdk }}/analytics/PlaybackStats.html
[aggregate the analytics data]: {{ site.baseurl }}/analytics.html#aggregate-analytics-data-of-multiple-playbacks
