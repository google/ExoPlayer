# Release notes #

### Current dev branch (from r1.3.2) ###

* Add option to TsExtractor to allow non-IDR keyframes.
* Added MulticastDataSource for connecting to multicast streams.
* (WorkInProgress) - First steps to supporting seeking in DASH DVR window.
* (WorkInProgress) - First steps to supporting styled + positioned subtitles.

### r1.3.2 (from r1.3.1) ###

* DataSource improvements: `DefaultUriDataSource` now handles http://, https://, file://, asset://
  and content:// URIs automatically. It also handles file:///android_asset/* URIs, and file paths
  like /path/to/media.mp4 where the scheme is omitted.
* HLS: Fix for some ID3 events being dropped.
* HLS: Correctly handle 0x0 and floating point RESOLUTION tags.
* Mp3Extractor: robustness improvements.

### r1.3.1 ###

* No notes provided.
