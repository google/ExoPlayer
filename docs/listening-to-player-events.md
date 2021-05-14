---
title: Player events
---

## Listening to playback events ##

Events such as changes in state and playback errors are reported to registered
[`Player.Listener`][] instances. Registering a listener to receive such
events is easy:

~~~
// Add a listener to receive events from the player.
player.addListener(listener);
~~~
{: .language-java}

`Player.Listener` has empty default methods, so you only need to implement
the methods you're interested in. See the [Javadoc][] for a full description of
the methods and when they're called. Some of the most important methods are
described in more detail below.

Listeners have the choice between implementing individual event callbacks or a
generic `onEvents` callback that's called after one or more events occur
together. See [`Individual callbacks vs onEvents`][] for an explanation of which
should be preferred for different use cases.

### Playback state changes ###

Changes in player state can be received by implementing
`onPlaybackStateChanged(@State int state)` in a registered
`Player.Listener`. The player can be in one of four playback states:

* `Player.STATE_IDLE`: This is the initial state, the state when the player is
  stopped, and when playback failed.
* `Player.STATE_BUFFERING`: The player is not able to immediately play from its
  current position. This mostly happens because more data needs to be loaded.
* `Player.STATE_READY`: The player is able to immediately play from its current
  position.
* `Player.STATE_ENDED`: The player finished playing all media.

In addition to these states, the player has a `playWhenReady` flag to indicate
the user intention to play. Changes in this flag can be received by implementing
`onPlayWhenReadyChanged(playWhenReady, @PlayWhenReadyChangeReason int reason)`.

A player is playing (i.e., its position is advancing and media is being
presented to the user) when it's in the `Player.STATE_READY` state,
`playWhenReady` is `true`, and playback is not suppressed for a reason returned
by `Player.getPlaybackSuppressionReason`. Rather than having to check these
properties individually, `Player.isPlaying` can be called. Changes to this
state can be received by implementing `onIsPlayingChanged(boolean isPlaying)`:

~~~
@Override
public void onIsPlayingChanged(boolean isPlaying) {
  if (isPlaying) {
    // Active playback.
  } else {
    // Not playing because playback is paused, ended, suppressed, or the player
    // is buffering, stopped or failed. Check player.getPlayWhenReady,
    // player.getPlaybackState, player.getPlaybackSuppressionReason and
    // player.getPlaybackError for details.
  }
}
~~~
{: .language-java }

### Playback errors ###

Errors that cause playback to fail can be received by implementing
`onPlayerError(ExoPlaybackException error)` in a registered
`Player.Listener`. When a failure occurs, this method will be called
immediately before the playback state transitions to `Player.STATE_IDLE`.
Failed or stopped playbacks can be retried by calling `ExoPlayer.retry`.

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

### Playlist transitions ###

Whenever the player changes to a new media item in the playlist
`onMediaItemTransition(MediaItem mediaItem,
@MediaItemTransitionReason int reason)` is called on registered
`Player.Listener`s. The reason indicates whether this was an automatic
transition, a seek (for example after calling `player.next()`), a repetition of
the same item, or caused by a playlist change (e.g., if the currently playing
item is removed).

### Seeking ###

Calling `Player.seekTo` methods results in a series of callbacks to registered
`Player.Listener` instances:

1. `onPositionDiscontinuity` with `reason=DISCONTINUITY_REASON_SEEK`. This is
   the direct result of calling `Player.seekTo`.
1. `onPlaybackStateChanged` with any immediate state change related to the seek.
   Note that there might not be such a change.

If you are using an `AnalyticsListener`, there will be an additional event
`onSeekStarted` just before `onPositionDiscontinuity`, to indicate the playback
position immediately before the seek started.

### Individual callbacks vs onEvents ###

Listeners can choose between implementing individual callbacks like
`onIsPlayingChanged(boolean isPlaying)`, and the generic
`onEvents(Player player, Events events)` callback. The generic callback provides
access to the `Player` object and specifies the set of `events` that occurred
together. It's always called after the callbacks that correspond to the
individual events.

~~~
@Override
public void onEvents(Player player, Events events) {
  if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
      || events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
    uiModule.updateUi(player);
  }
}
~~~
{: .language-java }

Individual events should be preferred in the following cases:
* The listener is interested in the reasons for changes. For example the reasons
  provided for `onPlayWhenReadyChanged` or `onMediaItemTransition`.
* The listener only acts on the new values provided through callback parameters,
  or triggers something else that doesn't depend on the callback parameters.
* The listener implementation prefers a clear readable indication of what
  triggered the event in the method name.
* The listener reports to an analytics system that needs to know about all
  individual events and state changes.

The generic `onEvents(Player player, Events events)` should be preferred in the
following cases:
* The listener wants to trigger the same logic for multiple events. For
  example updating a UI for both `onPlaybackStateChanged` and
  `onPlayWhenReadyChanged`.
* The listener needs access the `Player` object to trigger further events,
  for example seeking after a media item transition.
* The listener intends to use multiple state values that are reported
  through separate callbacks together, or in combination with `Player` getter
  methods. For example, using `Player.getCurrentWindowIndex()` with the
  `Timeline` provided in `onTimelineChanged` is only safe from within the
  `onEvents` callback.
* The listener is interested in whether events logically occurred together. For
  example `onPlaybackStateChanged` to `STATE_BUFFERING` because of a media item
  transition.

In some cases, listeners may need to combine the individual callbacks with the
generic `onEvents` callback, for example to record media item change reasons
with `onMediaItemTransition`, but only act once all state changes can be used
together in `onEvents`.

## Using AnalyticsListener ##

When using `SimpleExoPlayer`, an `AnalyticsListener` can be registered with the
player by calling `addAnalyticsListener`. `AnalyticsListener` implementations
are able to listen to detailed events that may be useful for analytics and
logging purposes. Please refer to the [analytics page][] for more details.

### Using EventLogger ###

`EventLogger` is an `AnalyticsListener` provided directly by the library for
logging purposes. It can be added to a `SimpleExoPlayer` to enable useful
additional logging with a single line.

```
player.addAnalyticsListener(new EventLogger(trackSelector));
```
{: .language-java}

Passing the `trackSelector` enables additional logging, but is optional and so
`null` can be passed instead. See the [debug logging page][] for more details.

## Firing events at specified playback positions ##

Some use cases require firing events at specified playback positions. This is
supported using `PlayerMessage`. A `PlayerMessage` can be created using
`ExoPlayer.createMessage`. The playback position at which it should be executed
can be set using `PlayerMessage.setPosition`. Messages are executed on the
playback thread by default, but this can be customized using
`PlayerMessage.setLooper`. `PlayerMessage.setDeleteAfterDelivery` can be used
to control whether the message will be executed every time the specified
playback position is encountered (this may happen multiple times due to seeking
and repeat modes), or just the first time. Once the `PlayerMessage` is
configured, it can be scheduled using `PlayerMessage.send`.

~~~
player
    .createMessage(
        (messageType, payload) -> {
          // Do something at the specified playback position.
        })
    .setLooper(Looper.getMainLooper())
    .setPosition(/* windowIndex= */ 0, /* positionMs= */ 120_000)
    .setPayload(customPayloadData)
    .setDeleteAfterDelivery(false)
    .send();
~~~
{: .language-java }

[`Player.Listener`]: {{ site.exo_sdk }}/Player.Listener.html
[Javadoc]: {{ site.exo_sdk }}/Player.Listener.html
[`Individual callbacks vs onEvents`]: #individual-callbacks-vs-onevents
[`ExoPlaybackException`]: {{ site.exo_sdk }}/ExoPlaybackException.html
[log output]: event-logger.html
[`Parameters`]: {{ site.exo_sdk }}/trackselection/DefaultTrackSelector.Parameters.html
[`ParametersBuilder`]: {{ site.exo_sdk }}/trackselection/DefaultTrackSelector.ParametersBuilder.html
[`DefaultTrackSelector`]: {{ site.exo_sdk }}/trackselection/DefaultTrackSelector.html
[ExoPlayer demo app]: {{ site.baseurl }}/demo-application.html#playing-your-own-content
[latest ExoPlayer version]: https://github.com/google/ExoPlayer/releases
[analytics page]: {{ site.baseurl }}/analytics.html
[debug logging page]: {{ site.baseurl }}/debug-logging.html
