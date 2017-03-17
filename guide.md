---
layout: default
title: Developer guide
weight: 1
---

{% include infobox.html content="This guide is for ExoPlayer 2.x. If you're
still using 1.x, you can find the old developer guide [here](guide-v1.html)." %}

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
Android's low level media APIs. The open source project contains both the
ExoPlayer library and a demo app:

* [ExoPlayer library][] &ndash; This part of the project contains the core
  library classes.
* [Demo app][] &ndash; This part of the project demonstrates usage of ExoPlayer.

This guide describes the ExoPlayer library and its use. It refers to code in the
demo app in order to provide concrete examples. The guide touches on the pros
and cons of using ExoPlayer. It shows how to use ExoPlayer to play DASH,
SmoothStreaming and HLS adaptive streams, as well as formats such as MP4, M4A,
FMP4, WebM, MKV, MP3, Ogg, WAV, MPEG-TS, MPEG-PS, FLV and ADTS (AAC). It also
discusses ExoPlayer events, messages, customization and DRM support.

## Pros and cons ##

ExoPlayer has a number of advantages over Android's built in MediaPlayer:

* Support for Dynamic Adaptive Streaming over HTTP (DASH) and SmoothStreaming,
  neither of which are supported by MediaPlayer. Many other formats are also
  supported. See the [Supported formats][] page for details.
* Support for advanced HLS features, such as correct handling of
  `#EXT-X-DISCONTINUITY` tags.
* The ability to seamlessly merge, concatenate and loop media.
* The ability to customize and extend the player to suit your use case.
  ExoPlayer is designed specifically with this in mind, and allows many
  components to be replaced with custom implementations.
* Easily update the player along with your application. Because ExoPlayer is a
  library that you include in your application apk, you have control over which
  version you use and you can easily update to a newer version as part of a
  regular application update.
* Fewer device specific issues.
* Support for Widevine common encryption on Android 4.4 (API level 19) and
  higher.

It's important to note that there are also some disadvantages:

* **ExoPlayer's standard audio and video components rely on Android's
  `MediaCodec` API, which was released in Android 4.1 (API level 16). Hence they
  do not work on earlier versions of Android. Widevine common encryption is
  available on Android 4.4 (API level 19) and higher.**

## Library overview ##

At the core of the ExoPlayer library is the `ExoPlayer` interface. An
`ExoPlayer` exposes traditional high-level media player functionality such as
the ability to buffer media, play, pause and seek. Implementations are designed
to make few assumptions about (and hence impose few restrictions on) the type of
media being played, how and where it is stored, and how it is rendered. Rather
than implementing the loading and rendering of media directly, `ExoPlayer`
implementaitons delegate this work to components that are injected when a player
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
the components listed above above delegate work to further injected components.
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
`PlayerActivity` in the ExoPlayer demo app.

### Add ExoPlayer as a dependency ###

The first step to getting started is to make sure you have the jcenter
repository included in the `build.gradle` file in the root of your project.

```gradle
repositories {
    jcenter()
}
```

Next add a gradle compile dependency for the ExoPlayer library to the
`build.gradle` file of your app module.

```gradle
compile 'com.google.android.exoplayer:exoplayer:r2.X.X'
```

where `r2.X.X` is the your preferred version. For the latest version, see the
project's [Releases][]. For more details, see the project on [Bintray][].

### Creating the player ###

Now you can create an `ExoPlayer` instance using `ExoPlayerFactory`. The factory
provides a range of methods for creating `ExoPlayer` instances with varying
levels of customization. For the vast majority of use cases the default
`Renderer` implementations provided by the library are sufficient. For such
cases one of the `ExoPlayerFactory.newSimpleInstance` methods should be used.
These methods return `SimpleExoPlayer`, which extends `ExoPlayer` to add
additional high level player functionality. The code below is an example of
creating a `SimpleExoPlayer`.

{% highlight java %}
// 1. Create a default TrackSelector
Handler mainHandler = new Handler();
BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
TrackSelection.Factory videoTrackSelectionFactory =
    new AdaptiveTrackSelection.Factory(bandwidthMeter);
TrackSelector trackSelector =
    new DefaultTrackSelector(videoTrackSelectionFactory);

// 2. Create a default LoadControl
LoadControl loadControl = new DefaultLoadControl();

// 3. Create the player
SimpleExoPlayer player =
    ExoPlayerFactory.newSimpleInstance(context, trackSelector, loadControl);
{% endhighlight %}

### Attaching the player to a view ###

The ExoPlayer library provides a `SimpleExoPlayerView`, which encapsulates a
`PlaybackControlView` and a `Surface` onto which video is rendered. A
`SimpleExoPlayerView` can be included in your application's layout xml.
Binding the player to the view is as simple as:

{% highlight java %}
// Bind the player to the view.
simpleExoPlayerView.setPlayer(player);
{% endhighlight %}

If you require fine-grained control over the player controls and the `Surface`
onto which video is rendered, you can set the player's target `SurfaceView`,
`TextureView`, `SurfaceHolder` or `Surface` directly using `SimpleExoPlayer`'s
`setVideoSurfaceView`, `setVideoTextureView`, `setVideoSurfaceHolder` and
`setVideoSurface` methods respectively. You can use `PlaybackControlView` as a
standalone component, or implement your own playback controls that interact
directly with the player. `setTextOutput` and `setId3Output` can be used to
receive caption and ID3 metadata output during playback.

### Preparing the player ###

In ExoPlayer every piece of media is represented by `MediaSource`. To play a
piece of media you must first create a corresponding `MediaSource` and then
pass this object to `ExoPlayer.prepare`. The ExoPlayer library provides
`MediaSource` implementations for DASH (`DashMediaSource`), SmoothStreaming
(`SsMediaSource`), HLS (`HlsMediaSource`) and regular media files
(`ExtractorMediaSource`). These implementations are described in more detail
later in this guide. The following code shows how to prepare the player with a
`MediaSource` suitable for playback of an MP4 file.

{% highlight java %}
// Measures bandwidth during playback. Can be null if not required.
DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
// Produces DataSource instances through which media data is loaded.
DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
    Util.getUserAgent(this, "yourApplicationName"), bandwidthMeter);
// Produces Extractor instances for parsing the media data.
ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
// This is the MediaSource representing the media to be played.
MediaSource videoSource = new ExtractorMediaSource(mp4VideoUri,
    dataSourceFactory, extractorsFactory, null, null);
// Prepare the player with the source.
player.prepare(videoSource);
{% endhighlight %}

Once the player has been prepared, playback can be controlled by calling methods
on the player. For example `setPlayWhenReady` can be used to start and pause
playback, and the various `seekTo` methods can be used to seek within the media.
If the player was bound to a `SimpleExoPlayerView` or `PlaybackControlView` then
user interaction with these components will cause corresponding methods on the
player to be invoked.

### Releasing the player ###

It's important to release the player when it's no longer needed, so as to free
up limited resources such as video decoders for use by other applications. This
can be done by calling `ExoPlayer.release`.

## MediaSource ##

In ExoPlayer every piece of media is represented by `MediaSource`. The ExoPlayer
library provides `MediaSource` implementations for DASH (`DashMediaSource`),
SmoothStreaming (`SsMediaSource`), HLS (`HlsMediaSource`) and regular media
files (`ExtractorMediaSource`). Examples of how to instantiate all four can be
found in `PlayerActivity` in the ExoPlayer demo app.

In addition to the MediaSource implementations described above, the ExoPlayer
library also provides `MergingMediaSource`, `LoopingMediaSource` and
`ConcatenatingMediaSource`. These `MediaSource` implementations enable more
complex playback functionality through composition. Some of the common use cases
are described below. Note that although the following examples are described in
the context of video playback, they apply equally to audio only playback too,
and indeed to the playback of any supported media type(s).

### Side-loading a subtitle file ###

Given a video file and a separate subtitle file, `MergingMediaSource` can be
used to merge them into a single source for playback.

{% highlight java %}
MediaSource videoSource = new ExtractorMediaSource(videoUri, ...);
MediaSource subtitleSource = new SingleSampleMediaSource(subtitleUri, ...);
// Plays the video with the sideloaded subtitle.
MergingMediaSource mergedSource =
    new MergingMediaSource(videoSource, subtitleSource);
{% endhighlight %}

### Seamlessly looping a video ###

A video can be seamlessly looped using a `LoopingMediaSource`. The following
example loops a video indefinitely. It’s also possible to specify a finite loop
count when creating a `LoopingMediaSource`.

{% highlight java %}
MediaSource source = new ExtractorMediaSource(videoUri, ...);
// Loops the video indefinitely.
LoopingMediaSource loopingSource = new LoopingMediaSource(source);
{% endhighlight %}

### Seamlessly playing a sequence of videos ###

`ConcatenatingMediaSource` enables sequential playback of two or more individual
`MediaSource`s. The following example plays two videos in sequence. Transitions
between sources are seamless. There is no requirement that the sources being
concatenated are of the same format (e.g., it’s fine to concatenate a video file
containing 480p H264 with one that contains 720p VP9). The sources may even be
of different types (e.g., it’s fine to concatenate a video with an audio only
stream).

{% highlight java %}
MediaSource firstSource = new ExtractorMediaSource(firstVideoUri, ...);
MediaSource secondSource = new ExtractorMediaSource(secondVideoUri, ...);
// Plays the first video, then the second video.
ConcatenatingMediaSource concatenatedSource =
    new ConcatenatingMediaSource(firstSource, secondSource);
{% endhighlight %}

### Advanced composition ###

It’s possible to further combine composite `MediaSource`s for more unusual use
cases. Given two videos A and B, the following example shows how
`LoopingMediaSource` and `ConcatenatingMediaSource` can be used together to loop
the sequence (A,A,B) indefinitely.

{% highlight java %}
MediaSource firstSource = new ExtractorMediaSource(firstVideoUri, ...);
MediaSource secondSource = new ExtractorMediaSource(secondVideoUri, ...);
// Plays the first video twice.
LoopingMediaSource firstSourceTwice = new LoopingMediaSource(firstSource, 2);
// Plays the first video twice, then the second video.
ConcatenatingMediaSource concatenatedSource =
    new ConcatenatingMediaSource(firstSourceTwice, secondSource);
// Loops the sequence indefinitely.
LoopingMediaSource compositeSource = new LoopingMediaSource(concatenatedSource);
{% endhighlight %}

The following example is equivalent, demonstrating that there can be more than
one way of achieving the same result.

{% highlight java %}
MediaSource firstSource = new ExtractorMediaSource(firstVideoUri, ...);
MediaSource secondSource = new ExtractorMediaSource(secondVideoUri, ...);
// Plays the first video twice, then the second video.
ConcatenatingMediaSource concatenatedSource =
    new ConcatenatingMediaSource(firstSource, firstSource, secondSource);
// Loops the sequence indefinitely.
LoopingMediaSource compositeSource = new LoopingMediaSource(concatenatedSource);

{% endhighlight %}

{% include infobox.html content="It is important to avoid using the same
`MediaSource` instance multiple times in a composition, unless explicitly
allowed according to the documentation. The use of firstSource twice in the
example above is one such case, since the Javadoc for `ConcatenatingMediaSource`
explicitly states that duplicate entries are allowed. In general, however, the
graph of objects formed by a composition should be a tree. Using multiple
equivalent `MediaSource` instances in a composition is allowed." %}

## Player events ##

During playback, your app can listen for events generated by ExoPlayer that
indicate the overall state of the player. These events are useful as triggers
for updating the app user interface such as playback controls. Many ExoPlayer
components also report their own component specific low level events, which can
be useful for performance monitoring.

### High level events ###

ExoPlayer allows instances of `ExoPlayer.EventListener` to be added and removed
using its `addListener` and `removeListener` methods. Registered listeners
are notified of changes in playback state, as well as when errors occur that
cause playback to fail.

Developers who implement custom playback controls should register a listener and
use it to update their controls as the player’s state changes. An app should
also show an appropriate error to the user if playback fails.

When using `SimpleExoPlayer`, additional listeners can be set on the player. In
particular `setVideoListener` allows an application to receive events related to
video rendering that may be useful for adjusting the UI (e.g., the aspect ratio
of the `Surface` onto which video is being rendered). Other listeners can be set
to on a `SimpleExoPlayer` to receive debugging information, for example by
calling `setVideoDebugListener` and `setAudioDebugListener`.

### Low level events ###

In addition to high level listeners, many of the individual components provided
by the ExoPlayer library allow their own event listeners. You are typically
required to pass a `Handler` object to such components, which determines the
thread on which the listener's methods are invoked. In most cases, you should
use a `Handler` associated with the app’s main thread.

## Sending messages to components ##

Some ExoPlayer components allow changes in configuration during playback. By
convention, you make these changes by passing messages through the `ExoPlayer`
to the component, using the `sendMessages` or `blockingSendMessages` methods.
This approach ensures both thread safety and that the configuration change is
executed in order with any other operations being performed on the player.

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
* `Extractor` &ndash; If you need to support a container format not currently
  supported by the library, consider implementing a custom `Extractor` class,
  which can then be used to together with `ExtractorMediaSource` to play media
  of that type.
* `MediaSource` &ndash; Implementing a custom `MediaSource` class may be
  appropriate if you wish to obtain media samples to feed to renderers in a
  custom way, or if you wish to implement custom `MediaSource` compositing
  behavior.
* `TrackSelector` &ndash; Implementing a custom `TrackSelector` allows an app
  developer to change the way in which tracks exposed by a `MediaSource` are
  selected for consumption by each of the available `Renderer`s.
* `DataSource` &ndash; ExoPlayer’s upstream package already contains a number of
  `DataSource` implementations for different use cases. You may want to
  implement you own `DataSource` class to load data in another way, such as over
  a custom protocol, using a custom HTTP stack, or through a persistent cache.

### Customization guidelines ###

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

## Digital Rights Management ##

On Android 4.4 (API level 19) and higher, ExoPlayer supports Digital Rights
Managment (DRM) protected playback. In order to play DRM protected content with
ExoPlayer, your app must inject a `DrmSessionManager` when instantiating the
player. `ExoPlayerFactory` provides factory methods allowing this. A
`DrmSessionManager` object is responsible for providing `DrmSession` instances,
which provide `MediaCrypto` objects for decryption as well as ensuring that the
required decryption keys are available to the underlying DRM module being used.

The ExoPlayer library provides a default implementation of `DrmSessionManager`,
called `DefaultDrmSessionManager`, which uses `MediaDrm`. The session manager
supports any DRM scheme for which a modular DRM component exists on the device.
All Android devices are required to support Widevine modular DRM (with L3
security, although many devices also support L1). Some devices may support
additional schemes such as PlayReady. All Android TV devices support PlayReady.

`PlayerActivity` in the ExoPlayer demo app demonstrates how a
`DrmSessionManager` can be created and injected when instantiating the player.

[Supported formats]: https://google.github.io/ExoPlayer/supported-formats.html
[ExoPlayer library]: https://github.com/google/ExoPlayer/tree/release-v2/library
[Demo app]: https://github.com/google/ExoPlayer/tree/release-v2/demo
[`MediaPlayer`]: {{ site.sdkurl }}/android/media/MediaPlayer.html
[`MediaCodec`]: {{ site.sdkurl }}/android/media/MediaCodec.html
[`AudioTrack`]: {{ site.sdkurl }}/android/media/AudioTrack.html
[`MediaDrm`]: {{ site.sdkurl }}/android/media/MediaDrm.html
[Releases]: https://github.com/google/ExoPlayer/releases
[Bintray]: https://bintray.com/google/exoplayer/exoplayer/view
