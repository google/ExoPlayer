---
title: MediaSource
---

In ExoPlayer every piece of media is represented by `MediaSource`. The ExoPlayer
library provides `MediaSource` implementations for DASH (`DashMediaSource`),
SmoothStreaming (`SsMediaSource`), HLS (`HlsMediaSource`) and regular media
files (`ExtractorMediaSource`). Examples of how to instantiate all four can be
found in `PlayerActivity` in the [main demo app][].

In addition to the MediaSource implementations described above, the ExoPlayer
library also provides `ConcatenatingMediaSource`, `ClippingMediaSource`,
`LoopingMediaSource` and `MergingMediaSource`. These `MediaSource`
implementations enable more complex playback functionality through composition.
Some of the common use cases are described below. Note that although the
following examples are described in the context of video playback, they apply
equally to audio only playback too, and indeed to the playback of any supported
media type(s).

## Playlists ##

Playlists are supported using `ConcatenatingMediaSource`, which enables
sequential playback of multiple `MediaSource`s. The following example represents
a playlist consisting of two videos.

~~~
MediaSource firstSource =
    new ExtractorMediaSource.Factory(...).createMediaSource(firstVideoUri);
MediaSource secondSource =
    new ExtractorMediaSource.Factory(...).createMediaSource(secondVideoUri);
// Plays the first video, then the second video.
ConcatenatingMediaSource concatenatedSource =
    new ConcatenatingMediaSource(firstSource, secondSource);
~~~
{: .language-java}

Transitions between the concatenated sources are seamless. There is no
requirement that they are of the same format (e.g., it’s fine to concatenate a
video file containing 480p H264 with one that contains 720p VP9). They may even
be of different types (e.g., it’s fine to concatenate a video with an audio only
stream). It's allowed to use individual `MediaSource`s multiple times within a
concatenation.

It's possible to dynamically modify a playlist by adding, removing and moving
`MediaSource`s within a `ConcatenatingMediaSource`. This can be done both before
and during playback by calling the corresponding `ConcatenatingMediaSource`
methods. The player automatically handles modifications during playback in the
correct way. For example if the currently playing `MediaSource` is moved,
playback is not interrupted and its new successor will be played upon
completion. If the currently playing `MediaSource` is removed, the player will
automatically move to playing the first remaining successor, or transition to
the ended state if no such successor exists.

## Clipping a video ##

`ClippingMediaSource` can be used to clip a `MediaSource` so that only part of
it is played. The following example clips a video playback to start at 5 seconds
and end at 10 seconds.

~~~
MediaSource videoSource =
    new ExtractorMediaSource.Factory(...).createMediaSource(videoUri);
// Clip to start at 5 seconds and end at 10 seconds.
ClippingMediaSource clippingSource =
    new ClippingMediaSource(
        videoSource,
        /* startPositionUs= */ 5_000_000,
        /* endPositionUs= */ 10_000_000);
~~~
{: .language-java}

To clip only the start of the source, `endPositionUs` can be set to
`C.TIME_END_OF_SOURCE`. To clip only to a particular duration, there is a
constructor that takes a `durationUs` argument.

When clipping the start of a video file, try to align the start position with a
keyframe if possible. If the start position is not aligned with a keyframe then
the player will need to decode and discard data from the previous keyframe up to
the start position before playback can begin. This will introduce a short delay
at the start of playback, including when the player transitions to playing the
`ClippingMediaSource` as part of a playlist or due to looping.
{:.info}

## Looping a video ##

To loop indefinitely, it is usually better to use `ExoPlayer.setRepeatMode`
instead of `LoopingMediaSource`.
{:.info}

A video can be seamlessly looped a fixed number of times using a
`LoopingMediaSource`. The following example plays a video twice.

~~~
MediaSource source =
    new ExtractorMediaSource.Factory(...).createMediaSource(videoUri);
// Plays the video twice.
LoopingMediaSource loopingSource = new LoopingMediaSource(source, 2);
~~~
{: .language-java}

## Side-loading a subtitle file ##

Given a video file and a separate subtitle file, `MergingMediaSource` can be
used to merge them into a single source for playback.

~~~
// Build the video MediaSource.
MediaSource videoSource =
    new ExtractorMediaSource.Factory(...).createMediaSource(videoUri);
// Build the subtitle MediaSource.
Format subtitleFormat = Format.createTextSampleFormat(
    id, // An identifier for the track. May be null.
    MimeTypes.APPLICATION_SUBRIP, // The mime type. Must be set correctly.
    selectionFlags, // Selection flags for the track.
    language); // The subtitle language. May be null.
MediaSource subtitleSource =
    new SingleSampleMediaSource.Factory(...)
        .createMediaSource(subtitleUri, subtitleFormat, C.TIME_UNSET);
// Plays the video with the sideloaded subtitle.
MergingMediaSource mergedSource =
    new MergingMediaSource(videoSource, subtitleSource);
~~~
{: .language-java}

## Advanced composition ##

It’s possible to further combine composite `MediaSource`s for more unusual use
cases. Given two videos A and B, the following example shows how
`LoopingMediaSource` and `ConcatenatingMediaSource` can be used together to play
the sequence (A,A,B).

~~~
MediaSource firstSource =
    new ExtractorMediaSource.Factory(...).createMediaSource(firstVideoUri);
MediaSource secondSource =
    new ExtractorMediaSource.Factory(...).createMediaSource(secondVideoUri);
// Plays the first video twice.
LoopingMediaSource firstSourceTwice = new LoopingMediaSource(firstSource, 2);
// Plays the first video twice, then the second video.
ConcatenatingMediaSource concatenatedSource =
    new ConcatenatingMediaSource(firstSourceTwice, secondSource);
~~~
{: .language-java}

The following example is equivalent, demonstrating that there can be more than
one way of achieving the same result.

~~~
MediaSource firstSource =
    new ExtractorMediaSource.Builder(firstVideoUri, ...).build();
MediaSource secondSource =
    new ExtractorMediaSource.Builder(secondVideoUri, ...).build();
// Plays the first video twice, then the second video.
ConcatenatingMediaSource concatenatedSource =
    new ConcatenatingMediaSource(firstSource, firstSource, secondSource);
~~~
{: .language-java}
