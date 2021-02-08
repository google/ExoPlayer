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

* **Event collection**:
  This can be done by registering an `AnalyticsListener` on an `ExoPlayer`
  instance. Registered analytics listeners receive events as they occur during
  usage of the player. Each event is associated with the corresponding media
  item in the playlist, as well as playback position and timestamp metadata.
* **Event processing**:
  Some analytics systems upload raw events to a server, with all event
  processing performed server-side. It's also possible to process events on the
  device, and doing so may be simpler or reduce the amount of information that
  needs to be uploaded. ExoPlayer provides `PlaybackStatsListener`, which
  allows you to perform the following processing steps:
  1. **Event interpretation**: To be useful for analytics purposes, events need
     to be interpreted in the context of a single playback. For example the raw
     event of a player state change to `STATE_BUFFERING` may correspond to
     initial buffering, a rebuffer, or buffering that happens after a seek.
  1. **State tracking**: This step converts events to counters. For example,
     state change events can be converted to counters tracking how much time is
     spent in each playback state. The result is a basic set of analytics data
     values for a single playback.
  1. **Aggregation**: This step combines the analytics data across multiple
     playbacks, typically by adding up counters.
  1. **Calculation of summary metrics**: Many of the most useful metrics are
     those that compute averages or combine the basic analytics data values in
     other ways. Summary metrics can be calculated for single or multiple
     playbacks.

## Event collection with AnalyticsListener ##

Raw playback events from the player are reported to `AnalyticsListener`
implementations. You can easily add your own listener and override only the
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

The `EventTime` that's passed to each callback associates the event to a media
item in the playlist, as well as playback position and timestamp metadata:

* `realtimeMs`: The wall clock time of the event.
* `timeline`, `windowIndex` and `mediaPeriodId`: Defines the playlist and the
  item within the playlist to which the event belongs. The `mediaPeriodId`
  contains optional additional information, for example indicating whether the
  event belongs to an ad within the item.
* `eventPlaybackPositionMs`: The playback position in the item when the event
  occurred.
* `currentTimeline`, `currentWindowIndex`, `currentMediaPeriodId` and
  `currentPlaybackPositionMs`: As above but for the currently playing item. The
  currently playing item may be different from the item to which the event
  belongs, for example if the event corresponds to pre-buffering of the next
  item to be played.

## Event processing with PlaybackStatsListener ##

`PlaybackStatsListener` is an `AnalyticsListener` that implements on device
event processing. It calculates `PlaybackStats`, with counters and derived
metrics including:

* Summary metrics, for example the total playback time.
* Adaptive playback quality metrics, for example the average video resolution.
* Rendering quality metrics, for example the rate of dropped frames.
* Resource usage metrics, for example the number of bytes read over the network.

You will find a complete list of the available counts and derived metrics in the
[`PlaybackStats` Javadoc][].

`PlaybackStatsListener` calculates separate `PlaybackStats` for each media item
in the playlist, and also each client-side ad inserted within these items. You
can provide a callback to `PlaybackStatsListener` to be informed about finished
playbacks, and use the `EventTime` passed to the callback to identify which
playback finished. It's possible to [aggregate the analytics data][] for
multiple playbacks. It's also possible to query the `PlaybackStats` for the
current playback session at any time using
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
should only turn it on if you need access to the full history of processed
events, rather than just to the final analytics data.

Note that `PlaybackStats` uses an extended set of states to indicate not only
the state of the media, but also the user intention to play and more detailed
information such as why playback was interrupted or ended:

| Playback state | User intention to play  | No intention to play |
|:---|:---|:---|
| Before playback | `JOINING_FOREGROUND` | `NOT_STARTED`, `JOINING_BACKGROUND` |
| Active playback | `PLAYING` | |
| Interrupted playback | `BUFFERING`, `SEEKING` | `PAUSED`, `PAUSED_BUFFERING`, `SUPPRESSED`, `SUPPRESSED_BUFFERING`, `INTERRUPTED_BY_AD` |
| End states | | `ENDED`, `STOPPED`, `FAILED`, `ABANDONED` |

The user intention to play is important to distinguish times when the user was
actively waiting for playback to continue from passive wait times. For example,
`PlaybackStats.getTotalWaitTimeMs` returns the total time spent in the
`JOINING_FOREGROUND`, `BUFFERING` and `SEEKING` states, but not the time when
playback was paused. Similarly, `PlaybackStats.getTotalPlayAndWaitTimeMs` will
return the total time with a user intention to play, that is the total active
wait time and the total time spent in the `PLAYING` state.

### Processed and interpreted events ###

You can record processed and interpreted events by using `PlaybackStatsListener`
with `keepHistory=true`. The resulting `PlaybackStats` will contain the
following event lists:

* `playbackStateHistory`: An ordered list of extended playback states with
  the `EventTime` at which they started to apply. You can also use
  `PlaybackStats.getPlaybackStateAtTime` to look up the state at a given wall
  clock time.
* `mediaTimeHistory`: A history of wall clock time and media time pairs allowing
  you to reconstruct which parts of the media were played at which time. You can
  also use `PlaybackStats.getMediaTimeMsAtRealtimeMs` to look up the playback
  position at a given wall clock time.
* `videoFormatHistory` and `audioFormatHistory`: Ordered lists of video and
  audio formats used during playback with the `EventTime` at which they started
  to be used.
* `fatalErrorHistory` and `nonFatalErrorHistory`: Ordered lists of fatal and
  non-fatal errors with the `EventTime` at which they occurred. Fatal errors are
  those that ended playback, whereas non-fatal errors may have been recoverable.

### Single-playback analytics data ###

This data is automatically collected if you use `PlaybackStatsListener`, even
with `keepHistory=false`. The final values are the public fields that you can
find in the [`PlaybackStats` Javadoc][] and the playback state durations
returned by `getPlaybackStateDurationMs`. For convenience, you'll also find
methods like `getTotalPlayTimeMs` and `getTotalWaitTimeMs` that return the
duration of specific playback state combinations.

~~~
Log.d("DEBUG", "Playback summary: "
    + "play time = " + playbackStats.getTotalPlayTimeMs()
    + ", rebuffers = " + playbackStats.totalRebufferCount);
~~~
{: .language-java}

Some values like `totalVideoFormatHeightTimeProduct` are only useful when
calculating derived summary metrics like the average video height, but are
required to correctly combine multiple `PlaybackStats` together.
{:.info}

### Aggregate analytics data of multiple playbacks ###

You can combine multiple `PlaybackStats` together by calling
`PlaybackStats.merge`. The resulting `PlaybackStats` will contain the aggregated
data of all merged playbacks. Note that it won't contain the history of
individual playback events, since these cannot be aggregated.

`PlaybackStatsListener.getCombinedPlaybackStats` can be used to get an
aggregated view of all analytics data collected in the lifetime of a
`PlaybackStatsListener`.

### Calculated summary metrics ###

In addition to the basic analytics data, `PlaybackStats` provides many methods
to calculate summary metrics.

~~~
Log.d("DEBUG", "Additional calculated summary metrics: "
    + "average video bitrate = " + playbackStats.getMeanVideoFormatBitrate()
    + ", mean time between rebuffers = "
        + playbackStats.getMeanTimeBetweenRebuffers());
~~~
{: .language-java}

## Advanced topics ##

### Associating analytics data with playback metadata ###

When collecting analytics data for individual playbacks, you may wish to
associate the playback analytics data with metadata about the media being
played.

It's advisable to set media-specific metadata with `MediaItem.Builder.setTag`.
The media tag is part of the `EventTime` reported for raw events and when
`PlaybackStats` are finished, so it can be easily retrieved when handling the
corresponding analytics data:

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

[`PlaybackStats` Javadoc]: {{ site.exo_sdk }}/analytics/PlaybackStats.html
[aggregate the analytics data]: {{ site.baseurl }}/analytics.html#aggregate-analytics-data-of-multiple-playbacks
