---
layout: default
title: FAQs
weight: 3
---

* [What formats does ExoPlayer support?][]
* [How do I get smooth animation/scrolling of video?][]
* [Should I use SurfaceView or TextureView?][]

---

#### What formats does ExoPlayer support? ####

See the [Supported formats][] page.

#### How do I get smooth animation/scrolling of video? ####

`SurfaceView` rendering wasn't properly synchronized with view animations until Android N. On
earlier releases this could result in unwanted effects when a SurfaceView was placed into scrolling
container, or when it was subjected to animation. Such effects included the `SurfaceView`'s contents
appearing to lag slightly behind where it should be displayed, and the view turning black when
subjected to animation.

To achieve smooth animation or scrolling of video prior to Android N, it's therefore necessary to
use `TextureView` rather than `SurfaceView`. If smooth animation or scrolling is not required then
`SurfaceView` should be preferred (see [Should I use SurfaceView or TextureView?][]).

#### Should I use SurfaceView or TextureView? ####

`SurfaceView` has a number of benefits over `TextureView` for video playback:

* Significantly lower power consumption on many devices.
* More accurate frame timing, resulting in smoother video playback.
* Support for secure output when playing DRM protected content.

`SurfaceView` should therefore be prefered over `TextureView` where possible. `TextureView` should
be used only if SurfaceView does not meet your needs. One example is where smooth animations or
scrolling of the video surface is required prior to Android N (see
[How do I get smooth animation/scrolling of video?][]).For this case, it's preferable to use
`TextureView` only when [`SDK_INT`][] is less than 24 (Android N), and `SurfaceView` otherwise.

[What formats does ExoPlayer support?]: #what-formats-does-exoplayer-support?
[How do I get smooth animation/scrolling of video?]: #how-do-i-get-smooth-animation/scrolling-of-video?
[Should I use SurfaceView or TextureView?]: #should-i-use-surfaceview-or-textureview?
[Supported formats]: https://google.github.io/ExoPlayer/supported-formats.html
[`SDK_INT`]: https://developer.android.com/reference/android/os/Build.VERSION.html#SDK_INT
