---
layout: default
title: ExoPlayer developer guide
---

<div id="table-of-contents">
<div id="table-of-contents-header">Contents</div>
<div markdown="1">
* TOC
{:toc}
</div>
</div>

Playing videos and music is a popular activity on Android devices. The Android framework provides
[`MediaPlayer`][] as a quick solution for playing media with minimal code, and the [`MediaCodec`][]
and [`MediaExtractor`][] classes are provided for building custom media players. The open source
project, ExoPlayer, is a solution between these two options, providing a pre-built player that you
can extend.

ExoPlayer supports features not currently provided by [`MediaPlayer`][], including Dynamic adaptive
streaming over HTTP (DASH), SmoothStreaming, and persistent caching. ExoPlayer can be extended to
handle additional media formats, and because you include it as part of your app code, you can update
it along with your app.

This guide describes how to use ExoPlayer for playing Android supported media formats, as well as
DASH and SmoothStreaming playback. This guide also discusses ExoPlayer events, messages, DRM support
and guidelines for customizing the player.

The project contains a library and a demo app:

* [ExoPlayer Library](https://github.com/google/ExoPlayer/tree/master/library) - This part of the
  project contains the core library classes.
* [Demo App](https://github.com/google/ExoPlayer/tree/master/demo) - This part of the project
  demonstrates usage of ExoPlayer, including the ability to select between multiple audio tracks, a
  background audio mode, event logging and DRM protected playback.

## Overview ##

ExoPlayer is a media player built on top of the [`MediaExtractor`][] and [`MediaCodec`][] APIs
released in Android 4.1 (API level 16). At the core of this library is the `ExoPlayer` class. This
class maintains the player’s global state, but makes few assumptions about the nature of the media
being played, such as how the media data is obtained, how it is buffered or its format. You inject
this functionality through ExoPlayer’s 'prepare()' method in the form of `TrackRenderer` objects.

ExoPlayer provides default `TrackRenderer` implementations for audio and video, which make use of
the [`MediaCodec`][] and [`AudioTrack`][] classes in the Android framework. Both renderers require a
`SampleSource` object, from which they obtain individual media samples for playback. Figure 1 shows
the high level object model for an ExoPlayer implementation configured to play audio and video using
these components.

{% include figure.html url="/images/object-model.png" index="1" caption="High level object model for an ExoPlayer configured to play audio and video using TrackRenderer objects" %}

## TrackRenderer ##

A `TrackRenderer` processes a component of media for playback, such as video, audio or text. The
ExoPlayer class invokes methods on its `TrackRenderer` instances from a single playback thread, and
by doing so causes each media component to be rendered as the global playback position is advanced.
The ExoPlayer library provides `MediaCodecVideoTrackRenderer` as the default implementations
rendering video and `MediaCodecAudioTrackRenderer` for audio. Both implementations make use of
[`MediaCodec`][] to decode individual media samples. They can handle all audio and video formats
supported by a given Android device
(see [Supported Media Formats](http://developer.android.com/guide/appendix/media-formats.html) for
details). The ExoPlayer library also provides an implementation for rendering text called
`TextTrackRenderer`.


The code example below outlines the main steps required to instantiate an ExoPlayer to play video
and audio using the standard `TrackRenderer` implementations.

{% highlight java %}
// 1. Instantiate the player.
player = ExoPlayer.Factory.newInstance(RENDERER_COUNT);
// 2. Construct renderers.
MediaCodecVideoTrackRenderer videoRenderer = ...
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

For a complete example, see `PlayerActivity` and `DemoPlayer` in the ExoPlayer demo app. Between
them these classes correctly manage an ExoPlayer instance with respect to both the [`Activity`][]
and [`Surface`][] lifecycles.

## SampleSource ##

A standard `TrackRenderer` implementation requires a `SampleSource` to be provided in its
constructor. A `SampleSource` object provides format information and media samples to be rendered.
The ExoPlayer library provides `FrameworkSampleSource` and `ChunkSampleSource`. The
`FrameworkSampleSource` class uses [`MediaExtractor`][] to request, buffer and extract the media
samples. The 'ChunkSampleSource' class provides adaptive playback using DASH or SmoothStreaming,
and implements networking, buffering and media extraction within the ExoPlayer library.


### Providing media using MediaExtractor ###

In order to render media formats supported by the Android framework, the 'FrameworkSampleSource'
class uses [`MediaExtractor`][] for networking, buffering and sample extraction functionality. By
doing so, it supports any media container format supported by the version of Android where it is
running. For more information about media formats supported by Android, see
[Supported Media Formats](http://developer.android.com/guide/appendix/media-formats.html).


The diagram in Figure 2 shows the object model for an ExoPlayer implementation using
`FrameworkSampleSource`.

{% include figure.html url="/images/frameworksamplesource.png" index="2" caption="Object model for an implementation of ExoPlayer that renders media formats supported by Android using FrameworkSampleSource" %}

The following code example outlines how the video and audio renderers are constructed to load the
video from a specified URI.

{% highlight java %}
FrameworkSampleSource sampleSource = new FrameworkSampleSource(
    activity, uri, null, 2);
MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(
    sampleSource, null, true, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 0,
    mainHandler, playerActivity, 50);
MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(
    sampleSource, null, true);
{% endhighlight %}

The ExoPlayer demo app provides a complete implementation of this code in `DefaultRendererBuilder`.
The `PlayerActivity` class uses it to play one of the videos available in the demo app. Note that in
the example, video and audio are muxed, meaning they are streamed together from a single URI. The
`FrameworkSampleSource` instance provides video samples to the `videoRenderer` object and audio
samples to the `audioRenderer` object as they are extracted from the media container format. It is
also possible to play demuxed media, where video and audio are streamed separately from different
URIs. This functionality can be achieved by having two `FrameworkSampleSource` instances instead of
one.


### Providing media for adaptive playback ###

ExoPlayer supports adaptive streaming, which allows the quality of the media data to be adjusted
during playback based on the network conditions. DASH and SmoothStreaming are examples of adaptive
streaming technologies. Both these approaches load media in small chunks (typically 2 to 10 seconds
in duration). Whenever a chunk of media is requested, the client selects from a number of possible
formats. For example, a client may select a high quality format if network conditions are good, or
a low quality format if network conditions are bad. In both techniques, video and audio are streamed
separately.

ExoPlayer supports adaptive playback through use of the `ChunkSampleSource` class, which loads
chunks of media data from which individual samples can be extracted. Each 'ChunkSampleSource'
requires a `ChunkSource` object to be injected through its constructor, which is responsible for
providing media chunks from which to load and read samples. The 'DashChunkSource' class provides
DASH playback using the FMP4 and WebM container formats. The `SmoothStreamingChunkSource` class
provides SmoothStreaming playback using the FMP4 container format.

All of the standard `ChunkSource` implementations require a `FormatEvaluator` and a `DataSource` to
be injected through their constructors. The `FormatEvaluator` objects select from the available
formats before each chunk is loaded. The `DataSource` objects are responsible for actually loading
the data. Finally, the `ChunkSampleSources` require a `LoadControl` object that controls the chunk
buffering policy.

The object model of an ExoPlayer configured for a DASH adaptive playback is shown in the diagram
below. This example uses an `HttpDataSource` object to stream the media over the network. The video
quality is varied at runtime using the adaptive implementation of 'FormatEvaluator', while audio is
played at a fixed quality level.

{% include figure.html url="/images/adaptive-streaming.png" index="3" caption="Object model for a DASH adaptive playback using ExoPlayer" %}

The following code example outlines how the video and audio renderers are constructed.

{% highlight java %}
Handler mainHandler = playerActivity.getMainHandler();
LoadControl loadControl = new DefaultLoadControl(
    new BufferPool(BUFFER_SEGMENT_SIZE));
BandwidthMeter bandwidthMeter = new BandwidthMeter();

// Build the video renderer.
DataSource videoDataSource = new HttpDataSource(userAgent,
    HttpDataSource.REJECT_PAYWALL_TYPES, bandwidthMeter);
ChunkSource videoChunkSource = new DashChunkSource(videoDataSource,
    new AdaptiveEvaluator(bandwidthMeter), videoRepresentations);
ChunkSampleSource videoSampleSource = new ChunkSampleSource(videoChunkSource,
    loadControl, VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, true);
MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(
    videoSampleSource, null, true, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT,
    0, mainHandler, playerActivity, 50);

// Build the audio renderer.
DataSource audioDataSource = new HttpDataSource(userAgent,
    HttpDataSource.REJECT_PAYWALL_TYPES, bandwidthMeter);
ChunkSource audioChunkSource = new DashChunkSource(audioDataSource,
    new FormatEvaluator.FixedEvaluator(), audioRepresentation);
SampleSource audioSampleSource = new ChunkSampleSource(audioChunkSource,
    loadControl, AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, true);
MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(
    audioSampleSource, null, true);
{% endhighlight %}

In this code, `videoRepresentations` and `audioRepresentation` are 'Representation' objects,
  each of which describes one of the available media streams. In the DASH
  model, these streams are parsed from a media presentation description (MPD) file. The ExoPlayer
  library provides a `MediaPresentationDescriptionParser` class to obtain 'Representation' objects
  from MPD files.

The ExoPlayer demo app provides complete implementation of this code in `DashRendererBuilder`. The
`PlayerActivity` class uses this builder to construct renderers for playing DASH sample videos in
the demo app. For an equivalent SmoothStreaming example, see the `SmoothStreamingRendererBuilder`
class in the demo app.

#### Format selection for adaptive playback ####

For DASH and SmoothStreaming playback, consider both static format selection at the start of
playback and dynamic format selection during playback. Static format selection should be used to
filter out formats that should not be used throughout the playback, for example formats with
resolutions higher than the maximum supported by the playback device. Dynamic selection varies the
selected format during playback, typically to adapt video quality in response to changes in network
conditions.

##### Static format selection #####

When preparing a player, you should consider filtering out some of the available formats if they are
not useable for playback. Static format selection allows you to filter out formats that cannot be
used on a particular device or are not compatible with your player. For audio playback, this often
means picking a single format to play and discarding the others.

For video playback, filtering formats can be more complicated. Apps should first eliminate any
streams that whose resolution is too high to be played by the device. For H.264, which is normally
used for DASH and SmoothStreaming playback, ExoPlayer’s `MediaCodecUtil` class provides a
`maxH264DecodableFrameSize()` method that can be used to determine what resolution streams the
device is able to handle, as shown in the following code example:

{% highlight java %}
int maxDecodableFrameSize = MediaCodecUtil.maxH264DecodableFrameSize();
Format format = representation.format;
if (format.width * format.height &lt;= maxDecodableFrameSize) {
  // The device can play this stream.
  videoRepresentations.add(representation);
} else {
  // The device isn't capable of playing this stream.
}
{% endhighlight %}

This approach is used to filter `Representations` in the `DashRendererBuilder` class of the
ExoPlayer demo app, and similarly to filter track indices in 'SmoothStreamingRendererBuilder'.

In addition to eliminating unsupported formats, it should be noted that the ability to seamlessly
switch between H.264 streams of different resolution is an optional decoder feature available in
Android 4.3 (API level 16) and higher, and so is not supported by all devices. The availability of
an adaptive H.264 decoder can be queried using `MediaCodecUtil`, as shown in the following code
example:

{% highlight java %}
boolean isAdaptive = MediaCodecUtil.getDecoderInfo(MimeTypes.VIDEO_H264).adaptive;
{% endhighlight %}

The `MediaCodecVideoTrackRenderer` class is still able to handle resolution changes on devices that
do not have adaptive decoders, however the switch is not seamless. Typically, the switch creates a
small discontinuity in visual output lasting around 50-100ms. For devices that do not provide an
adaptive decoder, app developers may choose to adapt between formats at a single fixed resolution
so as to avoid discontinuities. The ExoPlayer demo app implementation does not pick a fixed
resolution.

##### Dynamic format selection #####

During playback, you can use a `FormatEvaluator` to dynamically select from the available video
formats. The ExoPlayer library provides a `FormatEvaluator.Adaptive` implementation for dynamically
selecting between video formats based on the current network conditions.

This class provides a simple, general purpose reference implementation, however you are encouraged
to write your own `FormatEvaluator` implementation to best suit your particular needs.

## Player Events ##

During playback, your app can listen for events generated by the ExoPlayer that indicate the overall
state of the player. These events are useful as triggers for updating the app user interface such as
playback controls. Many ExoPlayer components also report their own component specific low level
events, which can be useful for performance monitoring.

### High level events ###

ExoPlayer allows instances of `ExoPlayer.Listener` to be added and removed using its `addListener()`
and `removeListener()` methods. Registered listeners are notified of changes in playback state, as
well as when errors occur that cause playback to fail. For more information about the valid playback
states and the possible transitions between them, see the ExoPlayer source code.

Developers who implement custom playback controls should register a listener and use it to update
their controls as the player’s state changes. An app should also show an appropriate error to the
user if playback fails.

### Low level events ###

In addition to high level listeners, many of the individual components provided by the ExoPlayer
library allow their own event listeners. For example, `MediaCodecVideoTrackRenderer` has
constructors that take a `MediaCodecVideoTrackRenderer.EventListener`. In the ExoPlayer demo app,
`DemoPlayer` acts as the listener to multiple individual components, forwarding events to
`PlayerActivity`. This approach allows `PlayerActivity` to adjust the dimensions of the target
surface to have the correct height and width ratio for the video being played:

{% highlight java %}
@Override
public void onVideoSizeChanged(int width, int height, float pixelWidthAspectRatio) {
  surfaceView.setVideoWidthHeightRatio(
      height == 0 ? 1 : (width * pixelWidthAspectRatio) / height);
}
{% endhighlight %}

The `RendererBuilder` classes in the ExoPlayer demo app inject the `DemoPlayer` as the listener to
each component, for example in the `DashRendererBuilder` class:

{% highlight java %}
MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(
    sampleSource, null, true, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000,
    null, <strong>player.getMainHandler(), player</strong>, 50);
{% endhighlight %}

Note that you must pass a [`Handler`][] object to the renderer, which determines the thread on which
the listener’s methods are invoked. In most cases, you should use a [`Handler`][] associated with the
app’s main thread, as is the case in this example.

Listening to individual components can be useful for adjusting UI based on player events, as in the
example above. Listening to component events can also be helpful for logging performance metrics. For
example, `MediaCodecVideoTrackRenderer` notifies its listener of dropped video frames. A developer
may wish to log such metrics to track playback performance in their app.

Many components also notify their listeners when errors occur. Such errors may or may not cause
playback to fail. If an error does not cause playback to fail, it may still result in degraded
performance, and so you may wish to log all errors in order to track playback performance. Note that
an ExoPlayer instance always notifies its high level listeners of errors that cause playback to fail,
in addition to the listener of the individual component from which the error originated. Hence, you
should display error messages to users only from high level listeners. Within individual component
listeners, you should use error notifications only for informational purposes.


## Sending messages to components ##

Some ExoPlayer components allow changes in configuration during playback. By convention, you
make these changes by passing asynchronous messages through the ExoPlayer to the component. This
approach ensures both thread safety and that the configuration change is executed in order with any
other operations being performed on the player.

The most common use of messaging is passing a target surface to`MediaCodecVideoTrackRenderer`:

{% highlight java %}
player.sendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE,
    surface);
{% endhighlight %}

Note that if the surface needs to be cleared because[`SurfaceHolder.Callback.surfaceDestroyed()`](ht
tp://developer.android.com/reference/android/view/SurfaceHolder.Callback.html#surfaceDestroyed) has
been invoked, then you must send this message using the blocking variant of `sendMessage()`:

{% highlight java %}
player.blockingSendMessage(videoRenderer,
    MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, null);
{% endhighlight %}

You must use a blocking message because the contract of [`surfaceDestroyed()`](http://developer.andr
oid.com/reference/android/view/SurfaceHolder.Callback.html#surfaceDestroyed) requires that the app
does not attempt to access the surface after the method returns.

## Customizing ExoPlayer ##

One of the main benefits of ExoPlayer over [`MediaPlayer`][] is the ability tocustomize and extend
the player to better suit the developer’s use case. The ExoPlayer library is designed specifically
with this in mind, defining a number of abstract base classes and interfaces that make it possible
for app developers to easily replace the default implementations provided by the library. Here are
some use cases for building custom components:

* **`TrackRenderer`** - You may want to implement a custom `TrackRenderer` to handle media types
  other than audio and video. The  `TextTrackRenderer` class within the ExoPlayer library is an
  example of how to implement a  custom renderer. You could use the approach it demonstrates to
  render custom  overlays or annotations. Implementing this kind of functionality as a
  `TrackRenderer`  makes it easy to keep the overlays or annotations in sync with the other media
  being played.
* **`SampleSource`** - If you need to support a container format not already handled by
  [`MediaExtractor`][] or ExoPlayer, consider implementing a custom `SampleSource` class.
* **`FormatEvaluator`** - The ExoPlayer library provides `FormatEvaluator.Adaptive` as a simple
  reference implementation that switches between different quality video formats based on the
  available bandwidth. App developers are encouraged to develop their own adaptive
  `FormatEvaluator` implementations, which can be designed to suit their use specific needs.
* **`DataSource`** - ExoPlayer’s upstream package already contains a number of `DataSource`
  implementations for different use cases, such as writing and reading to and from a persistent
  media cache. You may want to implement you own `DataSource` class to load data in another way,
  such as a custom protocol or HTTP stack for data input.

### Custom component guidelines ###

If a custom component needs to report events back to the app, we recommend that you do so using the
same model as existing ExoPlayer components, where an event listener is passed together with a
[`Handler`][] to the constructor of the component.

We recommended that custom components use the same model as existing ExoPlayer components to allow
reconfiguration by the app during playback, as described in [Sending messages to components
](#sending-messages-to-components). To do this, you should implement a `ExoPlayerComponent` and
receive configuration changes in its `handleMessage()` method. Your app should pass configuration
changes by calling ExoPlayer’s `sendMessage()` and 'blockingSendMessage()' methods.


## Digital Rights Management ##

On Android 4.3 (API level 18) and higher, ExoPlayer supports Digital Rights Managment (DRM)
protected playback. In order to play DRM protected content with ExoPlayer, your app must inject a
`DrmSessionManager` into the `MediaCodecVideoTrackRenderer` and `MediaCodecAudioTrackRenderer`
constructors. A `DrmSessionManager` object is responsible for providing the `MediaCrypto` object
required for decryption, as well as ensuring that the required decryption keys are available to the
underlying DRM module being used.

The ExoPlayer library provides a default implementation of `DrmSessionManager`,
called `StreamingDrmSessionManager`, which uses [`MediaDrm`][]. The session manager supports any DRM
scheme for which a modular DRM component exists on the device. All Android devices are required to
support Widevine modular DRM (with L3 security, although many devices also support L1). Some devices
may support additional schemes such as PlayReady.

The `StreamingDrmSessionManager` class requires a `MediaDrmCallback` to be injected into its
constructor, which is responsible for actually making provisioning and key requests. You should
implement this interface to make network requests to your license server and obtain the required
keys. The `WidevineTestMediaDrmCallback` class in the ExoPlayer demo app sends requests to a Widevine
test server.

[`MediaPlayer`]: {{ site.sdkurl }}/android/media/MediaPlayer.html
[`MediaCodec`]: {{ site.sdkurl }}/android/media/MediaCodec.html
[`AudioTrack`]: {{ site.sdkurl }}/android/media/AudioTrack.html
[`MediaExtractor`]: {{ site.sdkurl }}/android/media/MediaExtractor.html
[`MediaDrm`]: {{ site.sdkurl }}/android/media/MediaDrm.html
[`Activity`]: {{ site.sdkurl }}/android/app/Activity.html
[`Surface`]: {{ site.sdkurl }}/android/view/Surface.html
[`Handler`]: {{ site.sdkurl }}/android/os/Handler.html