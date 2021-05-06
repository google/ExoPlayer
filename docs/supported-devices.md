---
title: Supported devices
---

The minimum Android versions required for core ExoPlayer use cases are:

| Use case | Android version number | Android API level |
|----------|:------------:|:------------:|
| Audio playback | 4.1 | 16 |
| Video playback | 4.1 | 16 |
| DASH (no DRM) | 4.1 | 16 |
| DASH (Widevine CENC; "cenc" scheme) | 4.4 | 19 |
| DASH (Widevine CENC; "cbcs" scheme) | 7.1 | 25 |
| DASH (ClearKey; "cenc" scheme) | 5.0 | 21 |
| SmoothStreaming (no DRM) | 4.1 | 16 |
| SmoothStreaming (PlayReady SL2000; "cenc" scheme) | AndroidTV | AndroidTV |
| HLS (no DRM) | 4.1 | 16 |
| HLS (AES-128 encryption) | 4.1 | 16 |
| HLS (Widevine CENC; "cenc" scheme) | 4.4 | 19 |
| HLS (Widevine CENC; "cbcs" scheme) | 7.1 | 25 |

For a given use case, we aim to support ExoPlayer on all Android devices that
satisfy the minimum version requirement. Known device specific compatibility
issues are listed below. Device specific issues on our GitHub issue tracker can
be found
[here](https://github.com/google/ExoPlayer/labels/bug%3A%20device%20specific).

* **FireOS (version 4 and earlier)** - Whilst we endeavour to support FireOS
  devices, FireOS is a fork of Android and as a result we are unable to
  guarantee support. Device specific issues encountered on FireOS are normally
  caused by incompatibilities in the support that FireOS provides for running
  Android applications. Such issues should be reported to Amazon in the first
  instance. We are aware of issues affecting FireOS version 4 and earlier. We
  believe FireOS version 5 resolved these issues.
* **Nexus Player (only when using an HDMI to DVI cable)** - There is a known
  issue affecting Nexus Player, only when the device is connected to a monitor
  using a certain type of HDMI to DVI cable, which causes video being played too
  quickly. Use of an HDMI to DVI cable is not realistic for an end user setup
  because such cables cannot carry audio. Hence this issue can be safely
  ignored. We suggest using a realistic end user setup (e.g., the device
  connected to a TV using a standard HDMI cable) for development and testing.
* **Emulators** - Some Android emulators do not properly implement components of
  Android's media stack, and as a result do not support ExoPlayer. This is an
  issue with the emulator, not with ExoPlayer. Android's official emulator
  ("Virtual Devices" in Android Studio) supports ExoPlayer provided the system
  image has an API level of at least 23. System images with earlier API levels
  do not support ExoPlayer. The level of support provided by third party
  emulators varies. Issues running ExoPlayer on third party emulators should be
  reported to the developer of the emulator rather than to the ExoPlayer team.
  Where possible, we recommend testing media applications on physical devices
  rather than emulators.
