---
title: Playlists
---

Playlists are supported using `ConcatenatingMediaSource`, which enables
sequential playback of multiple `MediaSource`s. The following example represents
a playlist consisting of two videos.

~~~
MediaSource firstSource =
    new ProgressiveMediaSource.Factory(...).createMediaSource(firstVideoUri);
MediaSource secondSource =
    new ProgressiveMediaSource.Factory(...).createMediaSource(secondVideoUri);
// Plays the first video, then the second video.
ConcatenatingMediaSource concatenatedSource =
    new ConcatenatingMediaSource(firstSource, secondSource);
~~~
{: .language-java}

Transitions between items in a playlist are seamless. There's no requirement
that they're of the same format (e.g., it’s fine for a playlist to contain both
H264 and VP9 videos). They may even be of different types (e.g., it’s fine for a
playlist to contain both videos and audio only streams). It's allowed to use the
same `MediaSource` multiple times within a playlist.

## Modifying a playlist ##

It's possible to dynamically modify a playlist by adding, removing and moving
`MediaSource`s within a `ConcatenatingMediaSource`. This can be done both before
and during playback by calling the corresponding `ConcatenatingMediaSource`
methods. The player automatically handles modifications during playback in the
correct way. For example if the currently playing `MediaSource` is moved,
playback is not interrupted and its new successor will be played upon
completion. If the currently playing `MediaSource` is removed, the player will
automatically move to playing the first remaining successor, or transition to
the ended state if no such successor exists.

## Identifying playlist items ##

To simplify identification of playlist items, each `MediaSource` can be set up
with a custom tag in the factory class of the `MediaSource`. This could be the
uri, the title, or any other custom object. It's possible to query the tag of
the currently playing item with `player.getCurrentTag`. The current `Timeline`
returned by `player.getCurrentTimeline` also contains all tags as part of the
`Timeline.Window` objects.

~~~
public void addItem() {
  // Add mediaId (e.g. uri) as tag to the MediaSource.
  MediaSource mediaSource =
      new ProgressiveMediaSource.Factory(...)
          .setTag(mediaId)
          .createMediaSource(uri);
  concatenatedSource.addMediaSource(mediaSource);
}

@Override
public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
  // Load metadata for mediaId and update the UI.
  CustomMetadata metadata = CustomMetadata.get(player.getCurrentTag());
  titleView.setText(metadata.getTitle());
}
~~~
{: .language-java}

## Detecting when playback transitions to another item ##

There are three types of events that may be called when the current playback
item changes:

1. `EventListener.onPositionDiscontinuity` with `reason =
   Player.DISCONTINUITY_REASON_PERIOD_TRANSITION`. This happens when playback
   automatically transitions from one item to the next.
1. `EventListener.onPositionDiscontinuity` with `reason =
   Player.DISCONTINUITY_REASON_SEEK`. This happens when the current playback
   item changes as part of a seek operation, for example when calling
   `Player.next`.
1. `EventListener.onTimelineChanged` with `reason =
   Player.TIMELINE_CHANGE_REASON_DYNAMIC`. This happens when the playlist
   changes, e.g. if items are added, moved, or removed.

In all cases, when your application code receives the event, you can query the
player to determine which item in the playlist is now being played. This can be
done using methods such as `Player.getCurrentWindowIndex` and
`Player.getCurrentTag`. If you only want to detect playlist item changes, then
it's necessary to compare against the last known window index or tag, because
the mentioned events may be triggered for other reasons.
