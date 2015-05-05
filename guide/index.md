---
layout: default
title: ExoPlayer developer guide
---

Playing videos and music is a popular activity on Android devices. The Android framework
provides {@link android.media.MediaPlayer} as a quick solution for playing media with minimal
code, and the {@link android.media.MediaCodec} and {@link android.media.MediaExtractor} classes
are provided for building custom media players. The open source project, ExoPlayer, is a
solution between these two options, providing a pre-built player that you can extend.

ExoPlayer supports features not currently provided by
{@link android.media.MediaPlayer}, including Dynamic adaptive streaming
over HTTP (DASH), SmoothStreaming, and persistent caching. ExoPlayer can be extended
to handle additional media formats, and because you include it as part of your app code,
you can update it along with your app.

This guide describes how to use ExoPlayer for playing Android supported media formats, as well as
DASH and SmoothStreaming playback. This guide also discusses ExoPlayer events, messages, DRM
support and guidelines for customizing the player.

* [ExoPlayer Library](https://github.com/google/ExoPlayer/tree/master/library) - This part of the
  project contains the core library classes.
* [Demo App](https://github.com/google/ExoPlayer/tree/master/demo) - This part of the project
  demonstrates usage of ExoPlayer, including the ability to select between multiple audio tracks,
  a background audio mode, event logging and DRM protected playback.

## Overview ##

ExoPlayer is a media player built on top of the {@link android.media.MediaExtractor} and
{@link android.media.MediaCodec} APIs released in Android 4.1 (API level 16). At the core of this
library is the {@code ExoPlayer} class. This class maintains the player’s global state, but makes few
assumptions about the nature of the media being played, such as how the media data is obtained,
how it is buffered or its format. You inject this functionality through ExoPlayer’s {@code
prepare()} method in the form of {@code TrackRenderer} objects.

ExoPlayer provides default {@code TrackRenderer} implementations for audio and
video, which make use of the {@link android.media.MediaCodec} and {@link android.media.AudioTrack}
classes in the Android framework. Both renderers require a {@code SampleSource} object, from which
they obtain individual media samples for playback. Figure 1 shows the high level object model for
an ExoPlayer implementation configured to play audio and video using these components.

## TrackRenderer ##

etc

The start...!

{% sdk_link Test %}

{% highlight java %}
// 1. Instantiate the player.
player = ExoPlayer.Factory.newInstance(RENDERER_COUNT);
// 2. Construct renderers.
MediaCodecVideoTrackRenderer videoRenderer = …
MediaCodecAudioTrackRenderer audioRenderer = ...
// 3. Inject the renderers through prepare.
player.prepare(videoRenderer, audioRenderer);
// 4. Pass the surface to the video renderer.
player.sendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE,
        surface);
// 5. Start playback.
player.setPlayWhenReady(true);
...
player.release(); // Don’t forget to release when done!
{% endhighlight %}

The end!