# Release notes #

### Current dev branch (from r1.4.2) ###

* Nothing yet.

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
* HLS: Added support for MPEG audio (e.g. MP3).
* HLS: Improved robustness against missing chunks and variants.
* MP4: Added support for embedded MPEG audio (e.g. MP3).
* TTML: Improved handling of whitespace.
* DASH: Support Mpd.Location element.
* Add option to TsExtractor to allow non-IDR keyframes.
* (WorkInProgress) - First steps to supporting seeking in DASH DVR window.
* (WorkInProgress) - First steps to supporting styled + positioned subtitles.
* Misc bug fixes.

### r1.3.3 ###

* HLS: Fix failure when playing HLS AAC streams.
* Misc bug fixes.

### r1.3.2 ###

* DataSource improvements: `DefaultUriDataSource` now handles http://, https://, file://, asset://
  and content:// URIs automatically. It also handles file:///android_asset/* URIs, and file paths
  like /path/to/media.mp4 where the scheme is omitted.
* HLS: Fix for some ID3 events being dropped.
* HLS: Correctly handle 0x0 and floating point RESOLUTION tags.
* Mp3Extractor: robustness improvements.

### r1.3.1 ###

* No notes provided.
