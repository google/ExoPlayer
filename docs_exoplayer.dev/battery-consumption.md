---
title: Battery consumption
---

## How important is battery consumption due to media playback? ##

Avoiding unnecessary battery consumption is an important aspect of developing a
good Android application. Media playback can be a major cause of battery drain,
however its importance for a particular app heavily depends on its usage
patterns. If an app is only used to play small amounts of media each day, then
the corresponding battery consumption will only be a small percentage of the
total consumption of the device. In such it makes sense to prioritize feature
set and reliability over optimizing for battery when selecting which player to
use. On the other hand, if an app is often used to play large amounts of media
each day, then optimizing for battery consumption should be weighted more
heavily when choosing between a number of viable options.

## How power efficient is ExoPlayer? ##

The diverse nature of the Android device and media content ecosystems means that
it’s difficult to make widely applicable statements about ExoPlayer’s battery
consumption, and in particular how it compares with Android’s MediaPlayer API.
Both absolute and relative performance vary by hardware, Android version and the
media being played. Hence the information provided below should be treated as
guidance only.

### Video playback ###

For video playback, our measurements show that ExoPlayer and MediaPlayer draw
similar amounts of power. The power required for the display and decoding the
video stream are the same in both cases, and these account for most of the power
consumed during playback.

Regardless of which media player is used, choosing between `SurfaceView` and
`TextureView` for output can have a significant impact on power consumption.
`SurfaceView` is more power efficient, with `TextureView` increasing total power
draw during video playback by as much as 30% on some devices. `SurfaceView`
should therefore be preferred where possible. Read more about choosing between
`SurfaceView` and `TextureView`
[here]({{ site.baseurl }}/ui-components.html#choosing-a-surface-type).

Below are some power consumption measurements for playing 1080p and 480p video
on Pixel 2, measured using a [Monsoon power monitor][]. As mentioned above,
these numbers should not be used to draw general conclusions about power
consumption across the Android device and media content ecosystems.

|                   | MediaPlayer | ExoPlayer |
|-------------------|:-----------:|:----------|
| SurfaceView 1080p | 202 mAh     | 214 mAh   |
| TextureView 1080p | 219 mAh     | 221 mAh   |
| SurfaceView 480p  | 194 mAh     | 207 mAh   |
| TextureView 480p  | 212 mAh     | 215 mAh   |

### Audio playback ###

For short audio playbacks or playbacks when the screen is on, using ExoPlayer
does not have a significant impact on power compared to using MediaPlayer.

For long playbacks with the screen off, ExoPlayer's audio offload mode needs to
be used or ExoPlayer may consume significantly more power than MediaPlayer.
Audio offload allows audio processing to be offloaded from the CPU to a
dedicated signal processor. It is used by default by MediaPlayer but not
ExoPlayer. ExoPlayer introduced support for audio offload in 2.12 as an
experimental feature. See `DefaultRenderersFactory.setEnableAudioOffload` and
`ExoPlayer.experimentalSetOffloadSchedulingEnabled` for more details on how
to enable it.

Due to SDK API limitations, ExoPlayer's audio offload mode is only available on
devices running Android 10 and above. MediaPlayer can use audio offload on
devices running earlier versions of Android. Whether the increased robustness,
flexibility and feature set that ExoPlayer provides over MediaPlayer is worth
the increased power consumption for audio only use cases on older devices is
something an app developer must decide, taking their requirements and app usage
patterns into account.

[Monsoon power monitor]: https://www.msoon.com/battery-configuration
