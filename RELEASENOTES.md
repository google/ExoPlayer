# Release notes #

### r2.0.0 ###

ExoPlayer 2.x is a major iteration of the library. It includes significant API
and architectural changes, new features and many bug fixes. You can read about
some of the motivations behind ExoPlayer 2.x
[here](https://medium.com/google-exoplayer/exoplayer-2-x-why-what-and-when-74fd9cb139#.am7h8nytm).

* Root package name changed to `com.google.android.exoplayer2`. The library
  structure and class names have also been sanitized. Read more
  [here](https://medium.com/google-exoplayer/exoplayer-2-x-new-package-and-class-names-ef8e1d9ba96f#.lv8sd4nez).
* Key architectural changes:
 * Late binding between rendering and media source components. Allows the same
   rendering components to be re-used from one playback to another. Enables
   features such as gapless playback through playlists and DASH multi-period
   support.
 * Improved track selection design. More details can be found
   [here](https://medium.com/google-exoplayer/exoplayer-2-x-track-selection-2b62ff712cc9#.n00zo76b6).
 * LoadControl now used to control buffering and loading across all playback
   types.
 * Media source components given additional structure. A new MediaSource class
   has been introduced. MediaSources expose Timelines that describe the media
   they expose, and can consist of multiple MediaPeriods. This enables features
   such as seeking in live playbacks and DASH multi-period support.
 * Responsibility for loading the initial DASH/SmoothStreaming/HLS manifest is
   promoted to the corresponding MediaSource components and is no longer the
   application's responsibility.
 * Higher level abstractions such as SimpleExoPlayer have been added to the
   library. These make the library easier to use for common use cases. The demo
   app is halved in size as a result, whilst at the same time gaining more
   functionality. Read more
   [here](https://medium.com/google-exoplayer/exoplayer-2-x-improved-demo-app-d97171aaaaa1).
 * Enhanced library support for implementing audio extensions. Read more
   [here](https://medium.com/google-exoplayer/exoplayer-2-x-new-audio-features-cfb26c2883a#.ua75vu4s3).
 * Format and MediaFormat are replaced by a single Format class.
* Key new features:
 * Playlist support. Includes support for gapless playback between playlist
   items and consistent application of LoadControl and TrackSelector policies
   when transitioning between items
   ([#1270](https://github.com/google/ExoPlayer/issues/1270)).
 * Seeking in live playbacks for DASH and SmoothStreaming
   ([#291](https://github.com/google/ExoPlayer/issues/291)).
 * DASH multi-period support
   ([#557](https://github.com/google/ExoPlayer/issues/557)).
 * MediaSource composition allows MediaSources to be concatenated into a
   playlist, merged and looped. Read more
   [here](https://medium.com/google-exoplayer/exoplayer-2-x-mediasource-composition-6c285fcbca1f#.zfha8qupz).
 * Looping support (see above)
   ([#490](https://github.com/google/ExoPlayer/issues/490)).
 * Ability to query information about all tracks in a piece of media (including
   those not supported by the device)
  ([#1121](https://github.com/google/ExoPlayer/issues/1121)).
 * Improved player controls.
 * Support for PSSH in fMP4 moof atoms
   ([#1143](https://github.com/google/ExoPlayer/issues/1143)).
 * Support for Opus in Ogg
   ([#1447](https://github.com/google/ExoPlayer/issues/1447)).
 * CacheDataSource support for standalone media file playbacks (mp3, mp4 etc).
 * FFMPEG extension (for audio only).
* Key bug fixes:
 * Removed unnecessary secondary requests when playing standalone media files
   ([#1041](https://github.com/google/ExoPlayer/issues/1041)).
 * Fixed playback of video only (i.e. no audio) live streams
   ([#758](https://github.com/google/ExoPlayer/issues/758)).
 * Fixed silent failure when media buffer is too small
   ([#583](https://github.com/google/ExoPlayer/issues/583)).
 * Suppressed "Sending message to a Handler on a dead thread" warnings
   ([#426](https://github.com/google/ExoPlayer/issues/426)).

### r1.5.11 ###

* Cronet network stack extension.
* HLS: Fix propagation of language for alternative audio renditions
  ([#1784](https://github.com/google/ExoPlayer/issues/1784)).
* WebM: Support for subsample encryption.
* ID3: Fix EOS detection for 2-byte encodings
  ([#1774](https://github.com/google/ExoPlayer/issues/1774)).
* MPEG-TS: Support multiple tracks of the same type.
* MPEG-TS: Work toward robust handling of stream corruption.
* Fix ContentDataSource failures triggered by garbage collector
  ([#1759](https://github.com/google/ExoPlayer/issues/1759)).

### r1.5.10 ###

* HLS: Stability fixes.
* MP4: Support for stz2 Atoms.
* Enable 4K format selection on Sony AndroidTV + nVidia SHIELD.
* TX3G caption fixes.

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
