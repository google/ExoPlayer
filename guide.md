---
layout: default
title: Developer guide
weight: 1
---

<div id="table-of-contents">
<div id="table-of-contents-header">Contents</div>
<div markdown="1">
* TOC
{:toc}
</div>
</div>

Playing videos and music is a popular activity on Android devices. The Android
framework provides [`MediaPlayer`][] as a quick solution for playing media with
minimal code. It also provides low level media APIs such as [`MediaCodec`][],
[`AudioTrack`][] and [`MediaDrm`][], which can be used to build custom media
player solutions.

ExoPlayer is an open source, application level media player built on top of
Android's low level media APIs. This guide describes the ExoPlayer library and
its use. It refers to code in ExoPlayer's [main demo app][] in order to provide
concrete examples. The guide touches on the pros and cons of using ExoPlayer.
It shows how to use ExoPlayer to play DASH, SmoothStreaming and HLS adaptive
streams, as well as formats such as MP4, M4A, FMP4, WebM, MKV, MP3, Ogg, WAV,
MPEG-TS, MPEG-PS, FLV and ADTS (AAC). It also discusses ExoPlayer events,
messages, customization and DRM support.

## Pros and cons ##

ExoPlayer has a number of advantages over Android's built in MediaPlayer:

* Support for Dynamic Adaptive Streaming over HTTP (DASH) and SmoothStreaming,
  neither of which are supported by MediaPlayer. Many other formats are also
  supported. See the [Supported formats][] page for details.
* Support for advanced HLS features, such as correct handling of
  `#EXT-X-DISCONTINUITY` tags.
* The ability to seamlessly merge, concatenate and loop media.
* The ability to update the player along with your application. Because
  ExoPlayer is a library that you include in your application apk, you have
  control over which version you use and you can easily update to a newer
  version as part of a regular application update.
* Fewer device specific issues and less variation in behavior across different
  devices and versions of Android.
* Support for Widevine common encryption on Android 4.4 (API level 19) and
  higher.
* The ability to customize and extend the player to suit your use case.
  ExoPlayer is designed specifically with this in mind, and allows many
  components to be replaced with custom implementations.
* The ability to quickly integrate with a number of additional libraries using
  official extensions. For example the [IMA extension][] makes it easy to
  monetize your content using the [Interactive Media Ads SDK][].

It's important to note that there are also some disadvantages:

* For audio only playback on some devices, ExoPlayer may consume significantly
  more battery than MediaPlayer. See the [Battery consumption page][] for
  details.

## Library overview ##

At the core of the ExoPlayer library is the `ExoPlayer` interface. An
`ExoPlayer` exposes traditional high-level media player functionality such as
the ability to buffer media, play, pause and seek. Implementations are designed
to make few assumptions about (and hence impose few restrictions on) the type of
media being played, how and where it is stored, and how it is rendered. Rather
than implementing the loading and rendering of media directly, `ExoPlayer`
implementations delegate this work to components that are injected when a player
is created or when it's prepared for playback. Components common to all
`ExoPlayer` implementations are:

* A `MediaSource` that defines the media to be played, loads the media, and from
  which the loaded media can be read. A `MediaSource` is injected via
  `ExoPlayer.prepare` at the start of playback.
* `Renderer`s that render individual components of the media. `Renderer`s are
  injected when the player is created.
* A `TrackSelector` that selects tracks provided by the `MediaSource` to be
  consumed by each of the available `Renderer`s. A `TrackSelector` is injected
  when the player is created.
* A `LoadControl` that controls when the `MediaSource` buffers more media, and
  how much media is buffered. A `LoadControl` is injected when the player is
  created.

The library provides default implementations of these components for common use
cases, as described in more detail below. An `ExoPlayer` can make use of these
components, but may also be built using custom implementations if non-standard
behaviors are required. For example a custom `LoadControl` could be injected to
change the player's buffering strategy, or a custom `Renderer` could be injected
to use a video codec not supported natively by Android.

The concept of injecting components that implement pieces of player
functionality is present throughout the library. The default implementations of
the components listed above delegate work to further injected components.
This allows many sub-components to be individually replaced with custom
implementations. For example the default `MediaSource` implementations require
one or more `DataSource` factories to be injected via their constructors. By
providing a custom factory it's possible to load data from a non-standard source
or through a different network stack.

## Getting started ##

For simple use cases, getting started with `ExoPlayer` consists of implementing
the following steps:

1. Add ExoPlayer as a dependency to your project.
1. Create a `SimpleExoPlayer` instance.
1. Attach the player to a view (for video output and user input).
1. Prepare the player with a `MediaSource` to play.
1. Release the player when done.

These steps are outlined in more detail below. For a complete example, refer to
`PlayerActivity` in the [main demo app][].

### Adding ExoPlayer as a dependency ###

#### Add repositories ####

The first step to getting started is to make sure you have the Google and
JCenter repositories included in the `build.gradle` file in the root of your
project.

```gradle
repositories {
    google()
    jcenter()
}
```

#### Add ExoPlayer modules ####

Next add a dependency in the `build.gradle` file of your app module. The
following will add a dependency to the full ExoPlayer library:

```gradle
implementation 'com.google.android.exoplayer:exoplayer:2.X.X'
```

where `2.X.X` is your preferred version.

As an alternative to the full library, you can depend on only the library
modules that you actually need. For example the following will add dependencies
on the Core, DASH and UI library modules, as might be required for an app that
plays DASH content:

```gradle
implementation 'com.google.android.exoplayer:exoplayer-core:2.X.X'
implementation 'com.google.android.exoplayer:exoplayer-dash:2.X.X'
implementation 'com.google.android.exoplayer:exoplayer-ui:2.X.X'
```

The available library modules are listed below. Adding a dependency to the full
ExoPlayer library is equivalent to adding dependencies on all of the library
modules individually.

* `exoplayer-core`: Core functionality (required).
* `exoplayer-dash`: Support for DASH content.
* `exoplayer-hls`: Support for HLS content.
* `exoplayer-smoothstreaming`: Support for SmoothStreaming content.
* `exoplayer-ui`: UI components and resources for use with ExoPlayer.

In addition to library modules, ExoPlayer has multiple extension modules that
depend on external libraries to provide additional functionality. These are
beyond the scope of this guide. Browse the [extensions directory][] and their
individual READMEs for details.

#### Turn on Java 8 support ####

If not enabled already, you also need to turn on Java 8 support in all
`build.gradle` files depending on ExoPlayer, by adding the following to the
`android` section:

```gradle
compileOptions {
  targetCompatibility JavaVersion.VERSION_1_8
}
```

Note that if you want to use Java 8 features in your own code, the following
additional options need to be set:

```gradle
// For Java compilers:
compileOptions {
  sourceCompatibility JavaVersion.VERSION_1_8
}
// For Kotlin compilers:
kotlinOptions {
  jvmTarget = JavaVersion.VERSION_1_8
}
```

### Creating the player ###

You can create an `ExoPlayer` instance using `ExoPlayerFactory`. The factory
provides a range of methods for creating `ExoPlayer` instances with varying
levels of customization. For the vast majority of use cases one of the
`ExoPlayerFactory.newSimpleInstance` methods should be used. These methods
return `SimpleExoPlayer`, which extends `ExoPlayer` to add additional high level
player functionality. The code below is an example of creating a
`SimpleExoPlayer`.

{% highlight java %}
SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(context);
{% endhighlight %}

ExoPlayer instances must be accessed from a single application thread. For the
vast majority of cases this should be the application's main thread. Using the
application's main thread is also a requirement when using ExoPlayer's UI
components or the IMA extension.

The thread on which an ExoPlayer instance must be accessed can be explicitly
specified by passing a `Looper` when creating the player. If no `Looper` is
specified, then the `Looper` of the thread that the player is created on is
used, or if that thread does not have a `Looper`, the `Looper` of the
application's main thread is used. In all cases the `Looper` of the thread from
which the player must be accessed can be queried using
`Player.getApplicationLooper`.

### Attaching the player to a view ###

The ExoPlayer library provides a `PlayerView`, which encapsulates a
`PlayerControlView` and a `Surface` onto which video is rendered. A `PlayerView`
can be included in your application's layout xml. Binding the player to the view
is as simple as:

{% highlight java %}
// Bind the player to the view.
playerView.setPlayer(player);
{% endhighlight %}

If you require fine-grained control over the player controls and the `Surface`
onto which video is rendered, you can set the player's target `SurfaceView`,
`TextureView`, `SurfaceHolder` or `Surface` directly using `SimpleExoPlayer`'s
`setVideoSurfaceView`, `setVideoTextureView`, `setVideoSurfaceHolder` and
`setVideoSurface` methods respectively. You can use `PlayerControlView` as a
standalone component, or implement your own playback controls that interact
directly with the player. `setTextOutput` and `setId3Output` can be used to
receive caption and ID3 metadata output during playback.

### Preparing the player ###

In ExoPlayer every piece of media is represented by `MediaSource`. To play a
piece of media you must first create a corresponding `MediaSource` and then
pass this object to `ExoPlayer.prepare`. The ExoPlayer library provides
`MediaSource` implementations for DASH (`DashMediaSource`), SmoothStreaming
(`SsMediaSource`), HLS (`HlsMediaSource`) and regular media files
(`ExtractorMediaSource`). The following code shows how to prepare the player
with a `MediaSource` suitable for playback of an MP4 file.

{% highlight java %}
// Produces DataSource instances through which media data is loaded.
DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context,
    Util.getUserAgent(context, "yourApplicationName"));
// This is the MediaSource representing the media to be played.
MediaSource videoSource = new ExtractorMediaSource.Factory(dataSourceFactory)
    .createMediaSource(mp4VideoUri);
// Prepare the player with the source.
player.prepare(videoSource);
{% endhighlight %}

### Controlling the player ###

Once the player has been prepared, playback can be controlled by calling methods
on the player. For example `setPlayWhenReady` starts and pauses playback, the
various `seekTo` methods seek within the media,`setRepeatMode` controls if and
how media is looped, `setShuffleModeEnabled` controls playlist shuffling, and
`setPlaybackParameters` adjusts playback speed and pitch.

If the player is bound to a `PlayerView` or `PlayerControlView` then user
interaction with these components will cause corresponding methods on the player
to be invoked.

### Listening to player events ###

Events such as changes in state and playback errors are reported to registered
`Player.EventListener` instances. Registering a listener to receive such events
is easy:

{% highlight java %}
// Add a listener to receive events from the player.
player.addListener(eventListener);
{% endhighlight %}

If you're only interested in a subset of events, extending
`Player.DefaultEventListener` rather than implementing `Player.EventListener`
allows you to implement only the methods you're interested in.

When using `SimpleExoPlayer`, additional listeners can be set on the player. The
`addVideoListener` method allows you to receive events related to video
rendering that may be useful for adjusting the UI (e.g., the aspect ratio of the
`Surface` onto which video is being rendered). The `addAnalyticsListener` method
allows you to receive detailed events, which may be useful for analytics
purposes.

### Releasing the player ###

It's important to release the player when it's no longer needed, so as to free
up limited resources such as video decoders for use by other applications. This
can be done by calling `ExoPlayer.release`.

## MediaSource ##

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

### Playlists ###

Playlists are supported using `ConcatenatingMediaSource`, which enables
sequential playback of multiple `MediaSource`s. The following example represents
a playlist consisting of two videos.

{% highlight java %}
MediaSource firstSource =
    new ExtractorMediaSource.Factory(...).createMediaSource(firstVideoUri);
MediaSource secondSource =
    new ExtractorMediaSource.Factory(...).createMediaSource(secondVideoUri);
// Plays the first video, then the second video.
ConcatenatingMediaSource concatenatedSource =
    new ConcatenatingMediaSource(firstSource, secondSource);
{% endhighlight %}

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

### Clipping a video ###

`ClippingMediaSource` can be used to clip a `MediaSource` so that only part of
it is played. The following example clips a video playback to start at 5 seconds
and end at 10 seconds.

{% highlight java %}
MediaSource videoSource =
    new ExtractorMediaSource.Factory(...).createMediaSource(videoUri);
// Clip to start at 5 seconds and end at 10 seconds.
ClippingMediaSource clippingSource =
    new ClippingMediaSource(
        videoSource,
        /* startPositionUs= */ 5_000_000,
        /* endPositionUs= */ 10_000_000);
{% endhighlight %}

To clip only the start of the source, `endPositionUs` can be set to
`C.TIME_END_OF_SOURCE`. To clip only to a particular duration, there is a
constructor that takes a `durationUs` argument.

{% include info-box.html content="When clipping the start of a video file, try
to align the start position with a keyframe if possible. If the start position
is not aligned with a keyframe then the player will need to decode and discard
data from the previous keyframe up to the start position before playback can
begin. This will introduce a short delay at the start of playback, including
when the player transitions to playing the `ClippingMediaSource` as part of a
playlist or due to looping." %}

### Looping a video ###

{% include info-box.html content="To loop indefinitely, it is usually better to
use `ExoPlayer.setRepeatMode` instead of `LoopingMediaSource`." %}

A video can be seamlessly looped a fixed number of times using a
`LoopingMediaSource`. The following example plays a video twice.

{% highlight java %}
MediaSource source =
    new ExtractorMediaSource.Factory(...).createMediaSource(videoUri);
// Plays the video twice.
LoopingMediaSource loopingSource = new LoopingMediaSource(source, 2);
{% endhighlight %}

### Side-loading a subtitle file ###

Given a video file and a separate subtitle file, `MergingMediaSource` can be
used to merge them into a single source for playback.

{% highlight java %}
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
{% endhighlight %}

### Advanced composition ###

It’s possible to further combine composite `MediaSource`s for more unusual use
cases. Given two videos A and B, the following example shows how
`LoopingMediaSource` and `ConcatenatingMediaSource` can be used together to play
the sequence (A,A,B).

{% highlight java %}
MediaSource firstSource =
    new ExtractorMediaSource.Factory(...).createMediaSource(firstVideoUri);
MediaSource secondSource =
    new ExtractorMediaSource.Factory(...).createMediaSource(secondVideoUri);
// Plays the first video twice.
LoopingMediaSource firstSourceTwice = new LoopingMediaSource(firstSource, 2);
// Plays the first video twice, then the second video.
ConcatenatingMediaSource concatenatedSource =
    new ConcatenatingMediaSource(firstSourceTwice, secondSource);
{% endhighlight %}

The following example is equivalent, demonstrating that there can be more than
one way of achieving the same result.

{% highlight java %}
MediaSource firstSource =
    new ExtractorMediaSource.Builder(firstVideoUri, ...).build();
MediaSource secondSource =
    new ExtractorMediaSource.Builder(secondVideoUri, ...).build();
// Plays the first video twice, then the second video.
ConcatenatingMediaSource concatenatedSource =
    new ConcatenatingMediaSource(firstSource, firstSource, secondSource);
{% endhighlight %}

## Track selection ##

Track selection determines which of the available media tracks are played by the
player's `Renderer`s. Track selection is the responsibility of a
`TrackSelector`, an instance of which can be provided whenever an `ExoPlayer`
is built.

{% highlight java %}
DefaultTrackSelector trackSelector = new DefaultTrackSelector();
SimpleExoPlayer player =
    ExoPlayerFactory.newSimpleInstance(context, trackSelector);
{% endhighlight %}

`DefaultTrackSelector` is a flexible `TrackSelector` suitable for most use
cases. When using a `DefaultTrackSelector`, it's possible to control which
tracks it selects by modifying its `Parameters`. This can be done before or
during playback. For example the following code tells the selector to restrict
video track selections to SD, and to select a German audio track if there is
one:

{% highlight java %}
trackSelector.setParameters(
    trackSelector
        .buildUponParameters()
        .setMaxVideoSizeSd()
        .setPreferredAudioLanguage("deu"));
{% endhighlight %}

This is an example of constraint based track selection, in which constraints are
specified without knowledge of the tracks that are actually available. Many
different types of constraint can be specified using `Parameters`. `Parameters`
can also be used to select specific tracks from those that are available. See
the [`DefaultTrackSelector`][], [`Parameters`][] and [`ParametersBuilder`][]
documentation for more details.

## Sending messages to components ##

It's possible to send messages to ExoPlayer components. These can be created
using `createMessage` and then sent using `PlayerMessage.send`. By default,
messages are delivered on the playback thread as soon as possible, but this can
be customized by setting another callback thread (using
`PlayerMessage.setHandler`) or by specifying a delivery playback position
(using `PlayerMessage.setPosition`). Sending messages through the `ExoPlayer`
ensures that the operation is executed in order with any other operations being
performed on the player.

Most of ExoPlayer's out-of-the-box renderers support messages that allow
changes to their configuration during playback. For example, the audio
renderers accept messages to set the volume and the video renderers accept
messages to set the surface. These messages should be delivered
on the playback thread to ensure thread safety.

## Customization ##

One of the main benefits of ExoPlayer over Android's `MediaPlayer` is the
ability to customize and extend the player to better suit the developer’s use
case. The ExoPlayer library is designed specifically with this in mind, defining
a number of interfaces and abstract base classes that make it possible for app
developers to easily replace the default implementations provided by the
library. Here are some use cases for building custom components:

* `Renderer` &ndash; You may want to implement a custom `Renderer` to handle a
  media type not supported by the default implementations provided by the
  library.
* `TrackSelector` &ndash; Implementing a custom `TrackSelector` allows an app
  developer to change the way in which tracks exposed by a `MediaSource` are
  selected for consumption by each of the available `Renderer`s.
* `LoadControl` &ndash; Implementing a custom `LoadControl` allows an app
  developer to change the player's buffering policy.
* `Extractor` &ndash; If you need to support a container format not currently
  supported by the library, consider implementing a custom `Extractor` class,
  which can then be used to together with `ExtractorMediaSource` to play media
  of that type.
* `MediaSource` &ndash; Implementing a custom `MediaSource` class may be
  appropriate if you wish to obtain media samples to feed to renderers in a
  custom way, or if you wish to implement custom `MediaSource` compositing
  behavior.
* `DataSource` &ndash; ExoPlayer’s upstream package already contains a number of
  `DataSource` implementations for different use cases. You may want to
  implement you own `DataSource` class to load data in another way, such as over
  a custom protocol, using a custom HTTP stack, or from a custom persistent
  cache.

When building custom components, we recommend the following:

* If a custom component needs to report events back to the app, we recommend
  that you do so using the same model as existing ExoPlayer components, where an
  event listener is passed together with a `Handler` to the constructor of the
  component.
* We recommended that custom components use the same model as existing ExoPlayer
  components to allow reconfiguration by the app during playback, as described
  in [Sending messages to components](#sending-messages-to-components). To do
  this, you should implement an `ExoPlayerComponent` and receive configuration
  changes in its `handleMessage` method. Your app should pass configuration
  changes by calling ExoPlayer’s `sendMessages` and `blockingSendMessages`
  methods.

## Advanced topics ##

### Digital Rights Management ###

ExoPlayer supports Digital Rights Management (DRM) protected playback from
Android 4.4 (API level 19). See the [DRM page][] for more details.

### Battery consumption ###

Information about battery consumption when using ExoPlayer can be found on the
[Battery consumption page][].

### Shrinking the ExoPlayer library ###

Advice on minimizing the size of the ExoPlayer library can be found on the
[Shrinking ExoPlayer page][].

[Supported formats]: {{ site.baseurl }}/supported-formats.html
[IMA extension]: {{ site.releasev2 }}/extensions/ima
[Interactive Media Ads SDK]: https://developers.google.com/interactive-media-ads
[Battery consumption page]: {{ site.baseurl }}/battery-consumption.html
[DRM page]: {{ site.baseurl }}/drm.html
[Shrinking ExoPlayer page]: {{ site.baseurl }}/shrinking.html
[ExoPlayer library]: {{ site.releasev2 }}/library
[main demo app]: {{ site.releasev2 }}/demos/main
[`MediaPlayer`]: {{ site.android_sdk }}/android/media/MediaPlayer.html
[`MediaCodec`]: {{ site.android_sdk }}/android/media/MediaCodec.html
[`AudioTrack`]: {{ site.android_sdk }}/android/media/AudioTrack.html
[`MediaDrm`]: {{ site.android_sdk }}/android/media/MediaDrm.html
[`DefaultTrackSelector`]: {{ site.exo_sdk }}/trackselection/DefaultTrackSelector.html
[`Parameters`]: {{ site.exo_sdk }}/trackselection/DefaultTrackSelector.Parameters.html
[`ParametersBuilder`]: {{ site.exo_sdk }}/trackselection/DefaultTrackSelector.ParametersBuilder.html
[extensions directory]: {{ site.releasev2 }}/extensions/
