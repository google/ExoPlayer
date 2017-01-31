---
layout: default
title: Developer guide (1.x)
exclude_from_menu: true
---

{% include infobox.html content="This guide is for ExoPlayer 1.x, which is now deprecated.
The latest developer guide can be found [here](guide.html)." %}

{% include infobox.html content="The class reference for ExoPlayer 1.x can be found
[here](doc/reference-v1)." %}

<div id="table-of-contents">
<div id="table-of-contents-header">Contents</div>
<div markdown="1">
* TOC
{:toc}
</div>
</div>

Playing videos and music is a popular activity on Android devices. The Android framework provides
[`MediaPlayer`][] as a quick solution for playing media with minimal code. It also provides low
level media APIs such as [`MediaCodec`][], [`AudioTrack`][] and [`MediaDrm`][], which can be used to
build custom media player solutions.

ExoPlayer is an open source, application level media player built on top of Android's low level
media APIs. The open source project contains both the ExoPlayer library and a demo app:

* [ExoPlayer library](https://github.com/google/ExoPlayer/tree/release-v1/library) &ndash; This part of
  the project contains the core library classes.
* [Demo app](https://github.com/google/ExoPlayer/tree/release-v1/demo) &ndash; This part of the project
  demonstrates usage of ExoPlayer.

This guide describes the ExoPlayer library and its use. It refers to code in the demo app throughout
in order to provide concrete examples. The guide touches on the pros and cons of using ExoPlayer. It
shows how to use ExoPlayer to play DASH, SmoothStreaming and HLS adaptive streams, as well as
formats such as FMP4, MP4, M4A, MKV, WebM, MP3, AAC, MPEG-TS, MPEG-PS, OGG, FLV and WAV. It also
discusses ExoPlayer events, messages, customization and DRM support.

## Pros and cons ##

ExoPlayer has a number of advantages over Android's built in MediaPlayer:

* Support for Dynamic Adaptive Streaming over HTTP (DASH) and SmoothStreaming, neither of which are
  are supported by MediaPlayer (it also supports HTTP Live Streaming (HLS), FMP4, MP4, M4A, MKV,
  WebM, MP3, AAC, MPEG-TS, MPEG-PS, OGG, FLV and WAV).
* Support for advanced HLS features, such as correct handling of `#EXT-X-DISCONTINUITY` tags.
* The ability to customize and extend the player to suit your use case. ExoPlayer is designed
  specifically with this in mind, and allows many components to be replaced with custom
  implementations.
* Easily update the player along with your application. Because ExoPlayer is a library that you
  include in your application apk, you have control over which version you use and you can easily
  update to a newer version as part of a regular application update.
* Fewer device specific issues.

It's important to note that there are also some disadvantages:

* **ExoPlayer's standard audio and video components rely on Android's `MediaCodec` API, which was
  released in Android 4.1 (API level 16). Hence they do not work on earlier versions of Android.**

## Library overview ##

At the core of the ExoPlayer library is the `ExoPlayer` class. This class maintains the player’s
global state, but makes few assumptions about the nature of the media being played, such as how the
media data is obtained, how it is buffered or its format. You inject this functionality through
ExoPlayer’s `prepare` method in the form of `TrackRenderer` objects.

ExoPlayer provides default audio and video renderers, which make use of the `MediaCodec` and
`AudioTrack` classes in the Android framework. Both require an injected `SampleSource` object,
from which they obtain individual media samples for playback.

Injection of components is a theme present throughout the ExoPlayer library. Figure 1 shows the
high level object model for an ExoPlayer configured to play MP4 streams. Default audio and video
renderers are injected into the `ExoPlayer` instance. An instance of a class called
`ExtractorSampleSource` is injected into the renderers to provide them with media samples.
`DataSource` and `Extractor` instances are injected into the `ExtractorSampleSource` to allow it
load the media stream and extract samples from the loaded data. In this case `DefaultUriDataSource`
and `Mp4Extractor` are used to play MP4 streams loaded from their URIs.

{% include figure.html url="/images/standard-model.png" index="1" caption="Object model for MP4 playbacks using ExoPlayer" %}

In summary, ExoPlayer instances are built by injecting components that provide the functionality
that the developer requires. This model makes it easy to build players for specific use cases, and
to inject custom components. The following sections outline three of the most important interfaces
in this model: `TrackRenderer`, `SampleSource` and `DataSource`.

### TrackRenderer ###

A `TrackRenderer` plays a specific type of media, such as video, audio or text. The ExoPlayer class
invokes methods on its `TrackRenderer` instances from a single playback thread, and by doing so
causes each type of media be rendered as the global playback position is advanced. The ExoPlayer
library provides `MediaCodecVideoTrackRenderer` as the default implementation for rendering video,
and `MediaCodecAudioTrackRenderer` for audio. Both implementations make use of Android's MediaCodec
class to decode individual media samples. They can handle all audio and video formats supported by a
given Android device (see
[Supported Media Formats](https://developer.android.com/guide/appendix/media-formats.html) for
details). The ExoPlayer library also provides an implementation for rendering text, called
`TextTrackRenderer`.

The code below is an example of the main steps required to instantiate an ExoPlayer to play video
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
player.sendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
// 5. Start playback.
player.setPlayWhenReady(true);
...
player.release(); // Don’t forget to release when done!
{% endhighlight %}

For a complete example, see `PlayerActivity` and `DemoPlayer` in the ExoPlayer demo app. Between
them these classes correctly manage an ExoPlayer instance with respect to both the `Activity` and
`Surface` lifecycles.

### SampleSource ###

The standard `TrackRenderer` implementations provided by the library require `SampleSource`
instances to be injected into their constructors. A `SampleSource` object provides format
information and media samples to be rendered. The ExoPlayer library provides several concrete
`SampleSource` implementations for different use cases:

* `ExtractorSampleSource` &ndash; For formats such as FMP4, MP4, M4A, MKV, WebM, MP3, AAC, MPEG-TS,
  MPEG-PS, OGG, FLV and WAV.
* `ChunkSampleSource` &ndash; For DASH and SmoothStreaming playbacks.
* `HlsSampleSource` &ndash; For HLS playbacks.

The use of these implementations is outlined in more detail later in this guide.

### DataSource ###

The standard `SampleSource` implementations provided by the library make use of `DataSource`
instances for loading media data. Various implementations can be found in the `upstream` package.
The most commonly used implementations are:

* `DefaultUriDataSource` &ndash; For playing media that can be either local or loaded over the
  network.
* `AssetDataSource` &ndash; For playing media stored in the `assets` folder of the application's
  apk.

## Traditional media playbacks ##

The Exoplayer library provides `ExtractorSampleSource` to play traditional media formats, including
FMP4, MP4, M4A, MKV, WebM, MP3, AAC, MPEG-TS, MPEG-PS, OGG, FLV and WAV. The diagram in Figure 1
shows the object model for an ExoPlayer built to play MP4 streams. The following code shows how the
`TrackRenderer` instances are constructed.

{% highlight java %}
Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);
DataSource dataSource = new DefaultUriDataSource(context, null, userAgent);
ExtractorSampleSource sampleSource = new ExtractorSampleSource(
    uri, dataSource, allocator, BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE);
MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(
    context, sampleSource, MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(
    sampleSource, MediaCodecSelector.DEFAULT);
{% endhighlight %}

`ExtractorSampleSource` will automatically load the correct `Extractor` for the media being played.
If there is no `Extractor` for the media you wish to play, it's possible to implement and inject
your own.

The ExoPlayer demo app provides a complete implementation of this code in
`ExtractorRendererBuilder`. The `PlayerActivity` class uses it to play some of the videos available
in the demo app.

## Adaptive media playbacks ##

ExoPlayer supports adaptive streaming, which allows the quality of the media data to be adjusted
during playback based on the network conditions. DASH, SmoothStreaming and HLS are examples of
adaptive streaming technologies. In all three, media is loaded in small chunks (typically 2 to 10
seconds in duration). Whenever a chunk of media is requested, the client selects from a number of
possible formats. For example, a client may select a high quality format if network conditions are
good, or a low quality format if network conditions are bad. In both techniques, video and audio are
streamed separately.

### DASH and SmoothStreaming ###

ExoPlayer supports DASH and SmoothStreaming adaptive playbacks through use of `ChunkSampleSource`,
which loads chunks of media data from which individual samples can be extracted. Each
`ChunkSampleSource` requires a `ChunkSource` object to be injected through its constructor, which is
responsible for providing media chunks from which to load and read samples. The `DashChunkSource`
class provides DASH playback using the FMP4 and WebM container formats. The
`SmoothStreamingChunkSource` class provides SmoothStreaming playback using the FMP4 container
format.

Both of the standard `ChunkSource` implementations require a `FormatEvaluator` and a `DataSource` to
be injected through their constructors. The `FormatEvaluator` objects select from the available
formats before each chunk is loaded, and the `DataSource` provides the means to actually load the
data. Finally, the `ChunkSampleSource` requires a `LoadControl` object that controls the chunk
buffering policy.

The typical object model for an ExoPlayer configured for DASH adaptive playbacks is shown in Figure
2. The video quality is varied at runtime using the adaptive implementation of `FormatEvaluator`,
while audio is played at a fixed quality level.

{% include figure.html url="/images/dash-model.png" index="2" caption="Object model for DASH adaptive playbacks using ExoPlayer" %}

The following code example outlines how the video and audio renderers are constructed.

{% highlight java %}
LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));
DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

// Build the video renderer.
DataSource videoDataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);
ChunkSource videoChunkSource = new DashChunkSource(manifestFetcher,
    DefaultDashTrackSelector.newVideoInstance(context, true, false), videoDataSource,
    new AdaptiveEvaluator(bandwidthMeter), LIVE_EDGE_LATENCY_MS, elapsedRealtimeOffset, null, null,
    DemoPlayer.TYPE_VIDEO);
ChunkSampleSource videoSampleSource = new ChunkSampleSource(videoChunkSource, loadControl,
    VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE);
MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(context,
    videoSampleSource, MediaCodecSelector.DEFAULT,
    MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);

// Build the audio renderer.
DataSource audioDataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);
ChunkSource audioChunkSource = new DashChunkSource(manifestFetcher,
    DefaultDashTrackSelector.newAudioInstance(), audioDataSource, null, LIVE_EDGE_LATENCY_MS,
    elapsedRealtimeOffset, null, null, DemoPlayer.TYPE_AUDIO);
ChunkSampleSource audioSampleSource = new ChunkSampleSource(audioChunkSource,
    loadControl, AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE);
MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(
    audioSampleSource, MediaCodecSelector.DEFAULT);
{% endhighlight %}

In this code, `manifestFetcher` is an object responsible for loading the DASH manifest that defines
the media. The `videoAdaptationSetIndex` and `audioAdaptationSetIndex` variables index components
of the initially loaded manifest that correspond to video and audio respectively.

The ExoPlayer demo app provides a complete implementation of this code in `DashRendererBuilder`. The
`PlayerActivity` class uses this builder to construct renderers for playing DASH sample videos in
the demo app. For an equivalent SmoothStreaming example, see the `SmoothStreamingRendererBuilder`
class in the demo app.

### HLS ###

ExoPlayer supports HLS adaptive playbacks through use of `HlsSampleSource`, which loads chunks of
media data from which individual samples can be extracted. A `HlsSampleSource` requires a
`HlsChunkSource` to be injected through its constructor, which is responsible for providing media
chunks from which to load and read samples. A `HlsChunkSource` requires a `DataSource` to be
injected through its constructor, through which the media data can be loaded.

A typical object model for an ExoPlayer configured for HLS adaptive playbacks is shown in Figure
3. The video quality is varied at runtime using the adaptive implementation of 'FormatEvaluator',
while audio is played at a fixed quality level.

{% include figure.html url="/images/hls-model.png" index="3" caption="Object model for HLS playbacks using ExoPlayer" %}

The following code example outlines how the video and audio renderers are constructed.

{% highlight java %}
LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));
DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
PtsTimestampAdjusterProvider timestampAdjusterProvider = new PtsTimestampAdjusterProvider();
DataSource dataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);
HlsChunkSource chunkSource = new HlsChunkSource(true /* isMaster */, dataSource, url, manifest,
    DefaultHlsTrackSelector.newDefaultInstance(context), bandwidthMeter, timestampAdjusterProvider,
    HlsChunkSource.ADAPTIVE_MODE_SPLICE);
HlsSampleSource sampleSource = new HlsSampleSource(chunkSource, loadControl,
    MAIN_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE);
MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(context, sampleSource,
    MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
    MediaCodecSelector.DEFAULT);
{% endhighlight %}

The ExoPlayer demo app provides complete implementation of this code in `HlsRendererBuilder`. The
`PlayerActivity` class uses this builder to construct renderers for playing HLS sample videos in
the demo app.

## Player events ##

During playback, your app can listen for events generated by ExoPlayer that indicate the overall
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
public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
    float pixelWidthHeightRatio) {
  surfaceView.setVideoWidthHeightRatio(
      height == 0 ? 1 : (width * pixelWidthAspectRatio) / height);
}
{% endhighlight %}

The `RendererBuilder` classes in the ExoPlayer demo app inject the `DemoPlayer` as the listener to
each component.

Note that you must pass a `Handler` object to the renderer, which determines the thread on which
the listener’s methods are invoked. In most cases, you should use a `Handler` associated with the
app’s main thread, as is the case in this example.

Listening to individual components can be useful for adjusting UI based on player events, as in the
example above. Listening to component events can also be helpful for logging performance metrics.
For example, `MediaCodecVideoTrackRenderer` notifies its listener of dropped video frames. A
developer may wish to log such metrics to track playback performance in their app.

Many components also notify their listeners when errors occur. Such errors may or may not cause
playback to fail. If an error does not cause playback to fail, it may still result in degraded
performance, and so you may wish to log all errors in order to track playback performance. Note that
an ExoPlayer instance always notifies its high level listeners of errors that cause playback to
fail, in addition to the listener of the individual component from which the error originated.
Hence, you should display error messages to users only from high level listeners. Within individual
component listeners, you should use error notifications only for informational purposes.

## Sending messages to components ##

Some ExoPlayer components allow changes in configuration during playback. By convention, you
make these changes by passing asynchronous messages through the ExoPlayer to the component. This
approach ensures both thread safety and that the configuration change is executed in order with any
other operations being performed on the player.

The most common use of messaging is passing a target surface to`MediaCodecVideoTrackRenderer`:

{% highlight java %}
player.sendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
{% endhighlight %}

Note that if the surface needs to be cleared because `SurfaceHolder.Callback.surfaceDestroyed()` has
been invoked, then you must send this message using the blocking variant of `sendMessage()`:

{% highlight java %}
player.blockingSendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, null);
{% endhighlight %}

You must use a blocking message because the contract of `surfaceDestroyed()` requires that the app
does not attempt to access the surface after the method returns.

## Customization ##

One of the main benefits of ExoPlayer over Android's `MediaPlayer` is the ability to customize and
extend the player to better suit the developer’s use case. The ExoPlayer library is designed
specifically with this in mind, defining a number of abstract base classes and interfaces that make
it possible for app developers to easily replace the default implementations provided by the
library. Here are some use cases for building custom components:

* `TrackRenderer` &ndash; You may want to implement a custom `TrackRenderer` to handle media types
  other than audio and video. The `TextTrackRenderer` class within the ExoPlayer library is an
  example of how to implement a  custom renderer. You could use the approach it demonstrates to
  render custom  overlays or annotations. Implementing this kind of functionality as a
  `TrackRenderer` makes it easy to keep the overlays or annotations in sync with the other media
  being played.
* `Extractor` &ndash; If you need to support a container format not currently supported by the
  ExoPlayer library, consider implementing a custom `Extractor` class, which can then be used to
  together with `ExtractorSampleSource` to play media of that type.
* `SampleSource` &ndash; Implementing a custom `SampleSource` class may be appropriate if you wish
  to obtain media samples to feed to renderers in a custom way.
* `FormatEvaluator` &ndash; For DASH and SmoothStreaming playbacks, the ExoPlayer library provides
  `FormatEvaluator.AdaptiveEvaluator` as a simple reference implementation that switches between
  different quality formats based on the available bandwidth. App developers are encouraged to
  develop their own adaptive `FormatEvaluator` implementations, which can be designed to suit their
  use specific needs.
* `DataSource` &ndash; ExoPlayer’s upstream package already contains a number of `DataSource`
  implementations for different use cases. You may want to implement you own `DataSource` class to
  load data in another way, such as over a custom protocol, using a custom HTTP stack, or through
  a persistent cache.

### Customization guidelines ###

* If a custom component needs to report events back to the app, we recommend that you do so using
  the same model as existing ExoPlayer components, where an event listener is passed together with a
  `Handler` to the constructor of the component.
* We recommended that custom components use the same model as existing ExoPlayer components to allow
  reconfiguration by the app during playback, as described in
  [Sending messages to components](#sending-messages-to-components). To do this, you should
  implement an `ExoPlayerComponent` and receive configuration changes in its `handleMessage()`
  method. Your app should pass configuration changes by calling ExoPlayer’s `sendMessage()` and
  `blockingSendMessage()` methods.

## Digital Rights Management ##

On Android 4.4 (API level 19) and higher, ExoPlayer supports Digital Rights Managment (DRM)
protected playback. In order to play DRM protected content with ExoPlayer, your app must inject a
`DrmSessionManager` into the `MediaCodecVideoTrackRenderer` and `MediaCodecAudioTrackRenderer`
constructors. A `DrmSessionManager` object is responsible for providing the `MediaCrypto` object
required for decryption, as well as ensuring that the required decryption keys are available to the
underlying DRM module being used.

The ExoPlayer library provides a default implementation of `DrmSessionManager`,
called `StreamingDrmSessionManager`, which uses `MediaDrm`. The session manager supports any DRM
scheme for which a modular DRM component exists on the device. All Android devices are required to
support Widevine modular DRM (with L3 security, although many devices also support L1). Some devices
may support additional schemes such as PlayReady.

The `StreamingDrmSessionManager` class requires a `MediaDrmCallback` to be injected into its
constructor, which is responsible for actually making provisioning and key requests. You should
implement this interface to make network requests to your license server and obtain the required
keys. The `WidevineTestMediaDrmCallback` class in the ExoPlayer demo app sends requests to a
Widevine test server.

[`MediaPlayer`]: {{ site.sdkurl }}/android/media/MediaPlayer.html
[`MediaCodec`]: {{ site.sdkurl }}/android/media/MediaCodec.html
[`AudioTrack`]: {{ site.sdkurl }}/android/media/AudioTrack.html
[`MediaDrm`]: {{ site.sdkurl }}/android/media/MediaDrm.html
