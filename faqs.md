---
layout: default
title: FAQs
weight: 5
---

* [What formats does ExoPlayer support?][]
* [Why are some media files not seekable?][]
* [How do I keep audio playing when my app is backgrounded?][]
* [How do I get smooth animation/scrolling of video?][]
* [Should I use SurfaceView or TextureView?][]
* [Does ExoPlayer support emulators?][]

---

#### What formats does ExoPlayer support? ####

See the [Supported formats][] page.

#### Why are some media files not seekable? ####

ExoPlayer does not support seeking in media where the only method for performing
accurate seek operations is for the player to scan and index the entire file.
ExoPlayer considers such files as unseekable. Most modern media container
formats include metadata for seeking (e.g., a sample index), have a well defined
seek algorithm (e.g., interpolated bisection search for Ogg), or indicate that
their content is constant bitrate. Efficient seek operations are possible and
supported by ExoPlayer in these cases.

If you require seeking but have unseekable media, we suggest converting your
content to use a more appropriate container format. In the specific case of
unseekable MP3 files, you can enable seeking under the assumption that the
files have a constant bitrate using [FLAG_ENABLE_CONSTANT_BITRATE_SEEKING][].

#### How do I keep audio playing when my app is backgrounded? ####

There are a few steps that you need to take to ensure continued playback of
audio when your app is in the background:

1. You need to have a running [foreground service][]. This prevents the system
   from killing your process to free up resources.
1. You need to hold a [WifiLock][] and a [WakeLock][]. These ensure that the
   system keeps the WiFi radio and CPU awake.

It's important that you stop the service and release the locks as soon as audio
is no longer being played.

#### How do I get smooth animation/scrolling of video? ####

`SurfaceView` rendering wasn't properly synchronized with view animations until
Android N. On earlier releases this could result in unwanted effects when a
`SurfaceView` was placed into scrolling container, or when it was subjected to
animation. Such effects included the `SurfaceView`'s contents appearing to lag
slightly behind where it should be displayed, and the view turning black when
subjected to animation.

To achieve smooth animation or scrolling of video prior to Android N, it's
therefore necessary to use `TextureView` rather than `SurfaceView`. If smooth
animation or scrolling is not required then `SurfaceView` should be preferred
(see [Should I use SurfaceView or TextureView?][]).

#### Should I use SurfaceView or TextureView? ####

`SurfaceView` has a number of benefits over `TextureView` for video playback:

* Significantly lower power consumption on many devices.
* More accurate frame timing, resulting in smoother video playback.
* Support for secure output when playing DRM protected content.

`SurfaceView` should therefore be preferred over `TextureView` where possible.
`TextureView` should be used only if `SurfaceView` does not meet your needs. One
example is where smooth animations or scrolling of the video surface is required
prior to Android N (see [How do I get smooth animation/scrolling of video?][]).
For this case, it's preferable to use `TextureView` only when [`SDK_INT`][] is
less than 24 (Android N) and `SurfaceView` otherwise.

#### Does ExoPlayer support emulators? ####

If you're seeing ExoPlayer fail when using an emulator, this is usually because
the emulator does not properly implement components of Android's media stack.
This is an issue with the emulator, not with ExoPlayer. Android's official
emulator ("Virtual Devices" in Android Studio) supports ExoPlayer provided the
system image has an API level of at least 23. System images with earlier API
levels do not support ExoPlayer. The level of support provided by third party
emulators varies. If you find a third party emulator on which ExoPlayer fails,
you should report this to the developer of the emulator rather than to the
ExoPlayer team. Where possible, we recommend testing media applications on
physical devices rather than emulators.

[What formats does ExoPlayer support?]: #what-formats-does-exoplayer-support
[Why are some media files not seekable?]: #why-are-some-media-files-not-seekable
[How do I keep audio playing when my app is backgrounded?]: #how-do-i-keep-audio-playing-when-my-app-is-backgrounded
[How do I get smooth animation/scrolling of video?]: #how-do-i-get-smooth-animationscrolling-of-video
[Should I use SurfaceView or TextureView?]: #should-i-use-surfaceview-or-textureview
[Does ExoPlayer support emulators?]: #does-exoplayer-support-emulators

[Supported formats]: https://google.github.io/ExoPlayer/supported-formats.html
[FLAG_ENABLE_CONSTANT_BITRATE_SEEKING]: https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/extractor/mp3/Mp3Extractor.html#FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
[foreground service]: https://developer.android.com/guide/components/services.html#Foreground
[WifiLock]: https://developer.android.com/reference/android/net/wifi/WifiManager.WifiLock.html
[WakeLock]: https://developer.android.com/reference/android/os/PowerManager.WakeLock.html
[`SDK_INT`]: https://developer.android.com/reference/android/os/Build.VERSION.html#SDK_INT
