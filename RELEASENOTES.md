# Release notes #

### r1.5.9 ###

* MP4: Fixed incorrect sniffing in some cases (#1523).
* MP4: Improved file compatibility (#1567).
* ID3: Support for TIT2 and APIC frames.
* Fixed querying of platform decoders on some devices.
* Misc bug fixes.

### r1.5.8 ###

* HLS: Fix handling of HTTP redirects.
* Audio: Minor adjustment to improve A/V sync.
* OGG: Support FLAC in OGG.
* TTML: Support regions.
* WAV/PCM: Support 8, 24 and 32-bit WAV and PCM audio.
* Misc bug fixes and performance optimizations.

### r1.5.7 ###

* OGG: Support added for OGG.
* FLAC: Support for FLAC extraction and playback (via an extension).
* HLS: Multiple audio track support (via Renditions).
* FMP4: Support multiple tracks in fragmented MP4 (not applicable to
  DASH/SmoothStreaming).
* WAV: Support for 16-bit WAV files.
* MKV: Support non-square pixel formats.
* Misc bug fixes.

### r1.5.6 ###

* MP3: Fix mono streams playing at 2x speed on some MediaTek based devices
  (#801).
* MP3: Fix playback of some streams when stream length is unknown.
* ID3: Support multiple frames of the same type in a single tag.
* EIA608: Correctly handle repeated control characters, fixing an issue in which
  captions would immediately disappear.
* AVC3: Fix decoder failures on some MediaTek devices in the case where the
  first buffer fed to the decoder does not start with SPS/PPS NAL units.
* Misc bug fixes.

### r1.5.5 ###

* DASH: Enable MP4 embedded WebVTT playback (#1185)
* HLS: Fix handling of extended ID3 tags in MPEG-TS (#1181)
* MP3: Fix incorrect position calculation in VBRI header (#1197)
* Fix issue seeking backward using SingleSampleSource (#1193)

### r1.5.4 ###

* HLS: Support for variant selection and WebVtt subtitles.
* MP4: Support for embedded WebVtt.
* Improved device compatibility.
* Fix for resource leak (Issue #1066).
* Misc bug fixes + minor features.

### r1.5.3 ###

* Support for FLV (without seeking).
* MP4: Fix for playback of media containing basic edit lists.
* QuickTime: Fix parsing of QuickTime style audio sample entry.
* HLS: Add H262 support for devices that have an H262 decoder.
* Allow AudioTrack PlaybackParams (e.g. speed/pitch) on API level 23+.
* Correctly detect 4K displays on API level 23+.
* Misc bug fixes.

### r1.5.2 ###

* MPEG-TS/HLS: Fix frame drops playing H265 video.
* SmoothStreaming: Fix parsing of ProtectionHeader.

### r1.5.1 ###

* Enable smooth frame release by default.
* Added OkHttpDataSource extension.
* AndroidTV: Correctly detect 4K display size on Bravia devices.
* FMP4: Handle non-sample data in mdat boxes.
* TTML: Fix parsing of some colors on Jellybean.
* SmoothStreaming: Ignore tfdt boxes.
* Misc bug fixes.

### r1.5.0 ###

* Multi-track support.
* DASH: Limited support for multi-period manifests.
* HLS: Smoother format adaptation.
* HLS: Support for MP3 media segments.
* TTML: Support for most embedded TTML styling.
* WebVTT: Enhanced positioning support.
* Initial playback tests.
* Misc bug fixes.

### r1.4.2 ###

* Implemented automatic format detection for regular container formats.
* Added UdpDataSource for connecting to multicast streams.
* Improved robustness for MP4 playbacks.
* Misc bug fixes.

### r1.4.1 ###

* HLS: Fix premature playback failures that could occur in some cases.

### r1.4.0 ###

* Support for extracting Matroska streams (implemented by WebmExtractor).
* Support for tx3g captions in MP4 streams.
* Support for H.265 in MPEG-TS streams on supported devices.
* HLS: Added support for MPEG audio (e.g. MP3) in TS media segments.
* HLS: Improved robustness against missing chunks and variants.
* MP4: Added support for embedded MPEG audio (e.g. MP3).
* TTML: Improved handling of whitespace.
* DASH: Support Mpd.Location element.
* Add option to TsExtractor to allow non-IDR keyframes.
* Added MulticastDataSource for connecting to multicast streams.
* (WorkInProgress) - First steps to supporting seeking in DASH DVR window.
* (WorkInProgress) - First steps to supporting styled + positioned subtitles.
* Misc bug fixes.

### r1.3.3 ###

* HLS: Fix failure when playing HLS AAC streams.
* Misc bug fixes.

### r1.3.2 ###

* DataSource improvements: `DefaultUriDataSource` now handles http://, https://,
  file://, asset:// and content:// URIs automatically. It also handles
  file:///android_asset/* URIs, and file paths like /path/to/media.mp4 where the
  scheme is omitted.
* HLS: Fix for some ID3 events being dropped.
* HLS: Correctly handle 0x0 and floating point RESOLUTION tags.
* Mp3Extractor: robustness improvements.

### r1.3.1 ###

* No notes provided.
