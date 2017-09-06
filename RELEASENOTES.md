# Release notes #

### r2.5.1 ###

* Fix an issue that could cause the reported playback position to stop advancing
  in some cases.
* Fix an issue where a Surface could be released whilst still in use by the
  player.

### r2.5.0 ###

* IMA extension: Wraps the Google Interactive Media Ads (IMA) SDK to provide an
  easy and seamless way of incorporating display ads into ExoPlayer playbacks.
  You can read more about the IMA extension
  [here](https://medium.com/google-exoplayer/playing-ads-with-exoplayer-and-ima-868dfd767ea).
* MediaSession extension: Provides an easy to to connect ExoPlayer with
  MediaSessionCompat in the Android Support Library.
* RTMP extension: An extension for playing streams over RTMP.
* Build: Made it easier for application developers to depend on a local checkout
  of ExoPlayer. You can learn how to do this
  [here](https://medium.com/google-exoplayer/howto-2-depend-on-a-local-checkout-of-exoplayer-bcd7f8531720).
* Core playback improvements:
  * Eliminated re-buffering when changing audio and text track selections during
    playback of progressive streams
    ([#2926](https://github.com/google/ExoPlayer/issues/2926)).
  * New DynamicConcatenatingMediaSource class to support playback of dynamic
    playlists.
  * New ExoPlayer.setRepeatMode method for dynamic toggling of repeat mode
    during playback. Use of setRepeatMode should be preferred to
    LoopingMediaSource for most looping use cases. You can read more about
    setRepeatMode
    [here](https://medium.com/google-exoplayer/repeat-modes-in-exoplayer-19dd85f036d3).
  * Eliminated jank when switching video playback from one Surface to another on
    API level 23+ for unencrypted content, and on devices that support the
    EGL_EXT_protected_content OpenGL extension for protected content
    ([#677](https://github.com/google/ExoPlayer/issues/677)).
  * Enabled ExoPlayer instantiation on background threads without Loopers.
    Events from such players are delivered on the application's main thread.
* HLS improvements:
  * Optimized adaptive switches for playlists that specify the
    EXT-X-INDEPENDENT-SEGMENTS tag.
  * Optimized in-buffer seeking
    ([#551](https://github.com/google/ExoPlayer/issues/551)).
  * Eliminated re-buffering when changing audio and text track selections during
    playback, provided the new selection does not require switching to different
    renditions ([#2718](https://github.com/google/ExoPlayer/issues/2718)).
  * Exposed all media playlist tags in ExoPlayer's MediaPlaylist object.
* DASH: Support for seamless switching across streams in different AdaptationSet
  elements ([#2431](https://github.com/google/ExoPlayer/issues/2431)).
* DRM: Support for additional crypto schemes (cbc1, cbcs and cens) on
  API level 24+ ([#1989](https://github.com/google/ExoPlayer/issues/1989)).
* Captions: Initial support for SSA/ASS subtitles
  ([#889](https://github.com/google/ExoPlayer/issues/889)).
* AndroidTV: Fixed issue where tunneled video playback would not start on some
  devices ([#2985](https://github.com/google/ExoPlayer/issues/2985)).
* MPEG-TS: Fixed segmentation issue when parsing H262
  ([#2891](https://github.com/google/ExoPlayer/issues/2891)).
* Cronet extension: Support for a user-defined fallback if Cronet library is not
  present.
* Fix buffer too small IllegalStateException issue affecting some composite
  media playbacks ([#2900](https://github.com/google/ExoPlayer/issues/2900)).
* Misc bugfixes.

### r2.4.4 ###

* HLS/MPEG-TS: Some initial optimizations of MPEG-TS extractor performance
  ([#3040](https://github.com/google/ExoPlayer/issues/3040)).
* HLS: Fix propagation of format identifier for CEA-608
  ([#3033](https://github.com/google/ExoPlayer/issues/3033)).
* HLS: Detect playlist stuck and reset conditions
  ([#2872](https://github.com/google/ExoPlayer/issues/2872)).
* Video: Fix video dimension reporting on some devices
  ([#3007](https://github.com/google/ExoPlayer/issues/3007)).

### r2.4.3 ###

* Audio: Workaround custom audio decoders misreporting their maximum supported
  channel counts ([#2940](https://github.com/google/ExoPlayer/issues/2940)).
* Audio: Workaround for broken MediaTek raw decoder on some devices
  ([#2873](https://github.com/google/ExoPlayer/issues/2873)).
* Captions: Fix TTML captions appearing at the top of the screen
  ([#2953](https://github.com/google/ExoPlayer/issues/2953)).
* Captions: Fix handling of some DVB subtitles
  ([#2957](https://github.com/google/ExoPlayer/issues/2957)).
* Track selection: Fix setSelectionOverride(index, tracks, null)
  ([#2988](https://github.com/google/ExoPlayer/issues/2988)).
* GVR extension: Add support for mono input
  ([#2710](https://github.com/google/ExoPlayer/issues/2710)).
* FLAC extension: Fix failing build
  ([#2977](https://github.com/google/ExoPlayer/pull/2977)).
* Misc bugfixes.

### r2.4.2 ###

* Stability: Work around Nexus 10 reboot when playing certain content
  ([#2806](https://github.com/google/ExoPlayer/issues/2806)).
* MP3: Correctly treat MP3s with INFO headers as constant bitrate
  ([#2895](https://github.com/google/ExoPlayer/issues/2895)).
* HLS: Use average rather than peak bandwidth when available
  ([#2863](https://github.com/google/ExoPlayer/issues/2863)).
* SmoothStreaming: Fix timeline for live streams
  ([#2760](https://github.com/google/ExoPlayer/issues/2760)).
* UI: Fix DefaultTimeBar invalidation
  ([#2871](https://github.com/google/ExoPlayer/issues/2871)).
* Misc bugfixes.

### r2.4.1 ###

* Stability: Avoid OutOfMemoryError in extractors when parsing malformed media
  ([#2780](https://github.com/google/ExoPlayer/issues/2780)).
* Stability: Avoid native crash on Galaxy Nexus. Avoid unnecessarily large codec
  input buffer allocations on all devices
  ([#2607](https://github.com/google/ExoPlayer/issues/2607)).
* Variable speed playback: Fix interpolation for rate/pitch adjustment
  ([#2774](https://github.com/google/ExoPlayer/issues/2774)).
* HLS: Include EXT-X-DATERANGE tags in HlsMediaPlaylist.
* HLS: Don't expose CEA-608 track if CLOSED-CAPTIONS=NONE
  ([#2743](https://github.com/google/ExoPlayer/issues/2743)).
* HLS: Correctly propagate errors loading the media playlist
  ([#2623](https://github.com/google/ExoPlayer/issues/2623)).
* UI: DefaultTimeBar enhancements and bug fixes
  ([#2740](https://github.com/google/ExoPlayer/issues/2740)).
* Ogg: Fix failure to play some Ogg files
  ([#2782](https://github.com/google/ExoPlayer/issues/2782)).
* Captions: Don't select text tack with no language by default.
* Captions: TTML positioning fixes
  ([#2824](https://github.com/google/ExoPlayer/issues/2824)).
* Misc bugfixes.

### r2.4.0 ###

* New modular library structure. You can read more about depending on individual
  library modules
  [here](https://medium.com/google-exoplayer/exoplayers-new-modular-structure-a916c0874907).
* Variable speed playback support on API level 16+. You can read more about
  changing the playback speed
  [here](https://medium.com/google-exoplayer/variable-speed-playback-with-exoplayer-e6e6a71e0343)
  ([#26](https://github.com/google/ExoPlayer/issues/26)).
* New time bar view, including support for displaying ad break markers.
* Support DVB subtitles in MPEG-TS and MKV.
* Support adaptive playback for audio only DASH, HLS and SmoothStreaming
  ([#1975](https://github.com/google/ExoPlayer/issues/1975)).
* Support for setting extractor flags on DefaultExtractorsFactory
  ([#2657](https://github.com/google/ExoPlayer/issues/2657)).
* Support injecting custom renderers into SimpleExoPlayer using a new
  RenderersFactory interface.
* Correctly set ExoPlayer's internal thread priority to `THREAD_PRIORITY_AUDIO`.
* TX3G: Support styling and positioning.
* FLV:
  * Support MP3 in FLV.
  * Skip unhandled metadata rather than failing
    ([#2634](https://github.com/google/ExoPlayer/issues/2634)).
  * Fix potential OutOfMemory errors.
* ID3: Better handle malformed ID3 data
  ([#2604](https://github.com/google/ExoPlayer/issues/2604),
  [#2663](https://github.com/google/ExoPlayer/issues/2663)).
* FFmpeg extension: Fixed build instructions
  ([#2561](https://github.com/google/ExoPlayer/issues/2561)).
* VP9 extension: Reduced binary size.
* FLAC extension: Enabled 64 bit targets.
* Misc bugfixes.

### r2.3.1 ###

* Fix NPE enabling WebVTT subtitles in DASH streams
  ([#2596](https://github.com/google/ExoPlayer/issues/2596)).
* Fix skipping to keyframes when MediaCodecVideoRenderer is enabled but without
  a Surface ([#2575](https://github.com/google/ExoPlayer/issues/2575)).
* Minor fix for CEA-708 decoder
  ([#2595](https://github.com/google/ExoPlayer/issues/2595)).

### r2.3.0 ###

* GVR extension: Wraps the Google VR Audio SDK to provide spatial audio
  rendering. You can read more about the GVR extension
  [here](https://medium.com/google-exoplayer/spatial-audio-with-exoplayer-and-gvr-cecb00e9da5f#.xdjebjd7g).
* DASH improvements:
  * Support embedded CEA-608 closed captions
    ([#2362](https://github.com/google/ExoPlayer/issues/2362)).
  * Support embedded EMSG events
    ([#2176](https://github.com/google/ExoPlayer/issues/2176)).
  * Support mspr:pro manifest element
    ([#2386](https://github.com/google/ExoPlayer/issues/2386)).
  * Correct handling of empty segment indices at the start of live events
    ([#1865](https://github.com/google/ExoPlayer/issues/1865)).
* HLS improvements:
  * Respect initial track selection
    ([#2353](https://github.com/google/ExoPlayer/issues/2353)).
  * Reduced frequency of media playlist requests when playback position is close
    to the live edge ([#2548](https://github.com/google/ExoPlayer/issues/2548)).
  * Exposed the master playlist through ExoPlayer.getCurrentManifest()
    ([#2537](https://github.com/google/ExoPlayer/issues/2537)).
  * Support CLOSED-CAPTIONS #EXT-X-MEDIA type
    ([#341](https://github.com/google/ExoPlayer/issues/341)).
  * Fixed handling of negative values in #EXT-X-SUPPORT
    ([#2495](https://github.com/google/ExoPlayer/issues/2495)).
  * Fixed potential endless buffering state for streams with WebVTT subtitles
    ([#2424](https://github.com/google/ExoPlayer/issues/2424)).
* MPEG-TS improvements:
  * Support for multiple programs.
  * Support for multiple closed captions and caption service descriptors
   ([#2161](https://github.com/google/ExoPlayer/issues/2161)).
* MP3: Add `FLAG_ENABLE_CONSTANT_BITRATE_SEEKING` extractor option to enable
  constant bitrate seeking in MP3 files that would otherwise be unseekable
  ([#2445](https://github.com/google/ExoPlayer/issues/2445)).
* ID3: Better handle malformed ID3 data
  ([#2486](https://github.com/google/ExoPlayer/issues/2486)).
* Track selection: Added maxVideoBitrate parameter to DefaultTrackSelector.
* DRM: Add support for CENC ClearKey on API level 21+
  ([#2361](https://github.com/google/ExoPlayer/issues/2361)).
* DRM: Support dynamic setting of key request headers
  ([#1924](https://github.com/google/ExoPlayer/issues/1924)).
* SmoothStreaming: Fixed handling of start_time placeholder
  ([#2447](https://github.com/google/ExoPlayer/issues/2447)).
* FLAC extension: Fix proguard configuration
  ([#2427](https://github.com/google/ExoPlayer/issues/2427)).
* Misc bugfixes.

### r2.2.0 ###

* Demo app: Automatic recovery from BehindLiveWindowException, plus improved
  handling of pausing and resuming live streams
  ([#2344](https://github.com/google/ExoPlayer/issues/2344)).
* AndroidTV: Added Support for tunneled video playback
  ([#1688](https://github.com/google/ExoPlayer/issues/1688)).
* DRM: Renamed StreamingDrmSessionManager to DefaultDrmSessionManager and
  added support for using offline licenses
  ([#876](https://github.com/google/ExoPlayer/issues/876)).
* DRM: Introduce OfflineLicenseHelper to help with offline license acquisition,
  renewal and release.
* UI: Updated player control assets. Added vector drawables for use on API level
  21 and above.
* UI: Made player control seek bar work correctly with key events if focusable
  ([#2278](https://github.com/google/ExoPlayer/issues/2278)).
* HLS: Improved support for streams that use EXT-X-DISCONTINUITY without
  EXT-X-DISCONTINUITY-SEQUENCE
  ([#1789](https://github.com/google/ExoPlayer/issues/1789)).
* HLS: Support for EXT-X-START tag
  ([#1544](https://github.com/google/ExoPlayer/issues/1544)).
* HLS: Check #EXTM3U header is present when parsing the playlist. Fail
  gracefully if not ([#2301](https://github.com/google/ExoPlayer/issues/2301)).
* HLS: Fix memory leak
  ([#2319](https://github.com/google/ExoPlayer/issues/2319)).
* HLS: Fix non-seamless first adaptation where master playlist omits resolution
  tags ([#2096](https://github.com/google/ExoPlayer/issues/2096)).
* HLS: Fix handling of WebVTT subtitle renditions with non-standard segment file
  extensions ([#2025](https://github.com/google/ExoPlayer/issues/2025) and
  [#2355](https://github.com/google/ExoPlayer/issues/2355)).
* HLS: Better handle inconsistent HLS playlist update
  ([#2249](https://github.com/google/ExoPlayer/issues/2249)).
* DASH: Don't overflow when dealing with large segment numbers
  ([#2311](https://github.com/google/ExoPlayer/issues/2311)).
* DASH: Fix propagation of language from the manifest
  ([#2335](https://github.com/google/ExoPlayer/issues/2335)).
* SmoothStreaming: Work around "Offset to sample data was negative" failures
  ([#2292](https://github.com/google/ExoPlayer/issues/2292),
  [#2101](https://github.com/google/ExoPlayer/issues/2101) and
  [#1152](https://github.com/google/ExoPlayer/issues/1152)).
* MP3/ID3: Added support for parsing Chapter and URL link frames
  ([#2316](https://github.com/google/ExoPlayer/issues/2316)).
* MP3/ID3: Handle ID3 frames that end with empty text field
  ([#2309](https://github.com/google/ExoPlayer/issues/2309)).
* Added ClippingMediaSource for playing clipped portions of media
  ([#1988](https://github.com/google/ExoPlayer/issues/1988)).
* Added convenience methods to query whether the current window is dynamic and
  seekable ([#2320](https://github.com/google/ExoPlayer/issues/2320)).
* Support setting of default headers on HttpDataSource.Factory implementations
  ([#2166](https://github.com/google/ExoPlayer/issues/2166)).
* Fixed cache failures when using an encrypted cache content index.
* Fix visual artifacts when switching output surface
  ([#2093](https://github.com/google/ExoPlayer/issues/2093)).
* Fix gradle + proguard configurations.
* Fix player position when replacing the MediaSource
  ([#2369](https://github.com/google/ExoPlayer/issues/2369)).
* Misc bug fixes, including
  [#2330](https://github.com/google/ExoPlayer/issues/2330),
  [#2269](https://github.com/google/ExoPlayer/issues/2269),
  [#2252](https://github.com/google/ExoPlayer/issues/2252),
  [#2264](https://github.com/google/ExoPlayer/issues/2264) and
  [#2290](https://github.com/google/ExoPlayer/issues/2290).

### r2.1.1 ###

* Fix some subtitle types (e.g. WebVTT) being displayed out of sync
  ([#2208](https://github.com/google/ExoPlayer/issues/2208)).
* Fix incorrect position reporting for on-demand HLS media that includes
  EXT-X-PROGRAM-DATE-TIME tags
  ([#2224](https://github.com/google/ExoPlayer/issues/2224)).
* Fix issue where playbacks could get stuck in the initial buffering state if
  over 1MB of data needs to be read to initialize the playback.

### r2.1.0 ###

* HLS: Support for seeking in live streams
  ([#87](https://github.com/google/ExoPlayer/issues/87)).
* HLS: Improved support:
  * Support for EXT-X-PROGRAM-DATE-TIME
    ([#747](https://github.com/google/ExoPlayer/issues/747)).
  * Improved handling of sample timestamps and their alignment across variants
    and renditions.
  * Fix issue that could cause playbacks to get stuck in an endless initial
    buffering state.
  * Correctly propagate BehindLiveWindowException instead of
    IndexOutOfBoundsException exception
    ([#1695](https://github.com/google/ExoPlayer/issues/1695)).
* MP3/MP4: Support for ID3 metadata, including embedded album art
  ([#979](https://github.com/google/ExoPlayer/issues/979)).
* Improved customization of UI components. You can read about customization of
  ExoPlayer's UI components
  [here](https://medium.com/google-exoplayer/customizing-exoplayers-ui-components-728cf55ee07a#.9ewjg7avi).
* Robustness improvements when handling MediaSource timeline changes and
  MediaPeriod transitions.
* EIA608: Support for caption styling and positioning.
* MPEG-TS: Improved support:
  * Support injection of custom TS payload readers.
  * Support injection of custom section payload readers.
  * Support SCTE-35 splice information messages.
  * Support multiple table sections in a single PSI section.
  * Fix NullPointerException when an unsupported stream type is encountered
    ([#2149](https://github.com/google/ExoPlayer/issues/2149)).
  * Avoid failure when expected ID3 header not found
    ([#1966](https://github.com/google/ExoPlayer/issues/1966)).
* Improvements to the upstream cache package.
  * Support caching of media segments for DASH, HLS and SmoothStreaming. Note
    that caching of manifest and playlist files is still not supported in the
    (normal) case where the corresponding responses are compressed.
  * Support caching for ExtractorMediaSource based playbacks.
* Improved flexibility of SimpleExoPlayer
  ([#2102](https://github.com/google/ExoPlayer/issues/2102)).
* Fix issue where only the audio of a video would play due to capability
  detection issues ([#2007](https://github.com/google/ExoPlayer/issues/2007),
  [#2034](https://github.com/google/ExoPlayer/issues/2034) and
  [#2157](https://github.com/google/ExoPlayer/issues/2157)).
* Fix issues that could cause ExtractorMediaSource based playbacks to get stuck
  buffering ([#1962](https://github.com/google/ExoPlayer/issues/1962)).
* Correctly set SimpleExoPlayerView surface aspect ratio when an active player
  is attached ([#2077](https://github.com/google/ExoPlayer/issues/2077)).
* OGG: Fix playback of short OGG files
  ([#1976](https://github.com/google/ExoPlayer/issues/1976)).
* MP4: Support `.mp3` tracks
  ([#2066](https://github.com/google/ExoPlayer/issues/2066)).
* SubRip: Don't fail playbacks if SubRip file contains negative timestamps
  ([#2145](https://github.com/google/ExoPlayer/issues/2145)).
* Misc bugfixes.

### r2.0.4 ###

* Fix crash on Jellybean devices when using playback controls
  ([#1965](https://github.com/google/ExoPlayer/issues/1965)).

### r2.0.3 ###

* Fixed NullPointerException in ExtractorMediaSource
  ([#1914](https://github.com/google/ExoPlayer/issues/1914)).
* Fixed NullPointerException in HlsMediaPeriod
  ([#1907](https://github.com/google/ExoPlayer/issues/1907)).
* Fixed memory leak in PlaybackControlView
  ([#1908](https://github.com/google/ExoPlayer/issues/1908)).
* Fixed strict mode violation when using
  SimpleExoPlayer.setVideoPlayerTextureView().
* Fixed L3 Widevine provisioning
  ([#1925](https://github.com/google/ExoPlayer/issues/1925)).
* Fixed hiding of controls with use_controller="false"
  ([#1919](https://github.com/google/ExoPlayer/issues/1919)).
* Improvements to Cronet network stack extension.
* Misc bug fixes.

### r2.0.2 ###

* Fixes for MergingMediaSource and sideloaded subtitles.
  ([#1882](https://github.com/google/ExoPlayer/issues/1882),
  [#1854](https://github.com/google/ExoPlayer/issues/1854),
  [#1900](https://github.com/google/ExoPlayer/issues/1900)).
* Reduced effect of application code leaking player references
  ([#1855](https://github.com/google/ExoPlayer/issues/1855)).
* Initial support for fragmented MP4 in HLS.
* Misc bug fixes and minor features.

### r2.0.1 ###

* Fix playback of short duration content
  ([#1837](https://github.com/google/ExoPlayer/issues/1837)).
* Fix MergingMediaSource preparation issue
  ([#1853](https://github.com/google/ExoPlayer/issues/1853)).
* Fix live stream buffering (out of memory) issue
  ([#1825](https://github.com/google/ExoPlayer/issues/1825)).

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

# Legacy release notes #

Note: Since ExoPlayer V1 is still being maintained alongside V2, there is some
overlap between these notes and the notes above. r2.0.0 followed from r1.5.11,
and hence it can be assumed that all changes in r1.5.11 and earlier are included
in all V2 releases. This cannot be assumed for changes in r1.5.12 and later,
however it can be assumed that all such changes are included in the most recent
V2 release.

### r1.5.16 ###

* VP9 extension: Reduced binary size.
* FLAC extension: Enabled 64 bit targets and fixed proguard config.
* Misc bugfixes.

### r1.5.15 ###

* SmoothStreaming: Fixed handling of start_time placeholder
  ([#2447](https://github.com/google/ExoPlayer/issues/2447)).
* Misc bugfixes.

### r1.5.14 ###

* Fixed cache failures when using an encrypted cache content index.
* SmoothStreaming: Work around "Offset to sample data was negative" failures
  ([#2292](https://github.com/google/ExoPlayer/issues/2292),
  [#2101](https://github.com/google/ExoPlayer/issues/2101) and
  [#1152](https://github.com/google/ExoPlayer/issues/1152)).

### r1.5.13 ###

* Improvements to the upstream cache package.
* MP4: Support `.mp3` tracks
  ([#2066](https://github.com/google/ExoPlayer/issues/2066)).
* SubRip: Don't fail playbacks if SubRip file contains negative timestamps
  ([#2145](https://github.com/google/ExoPlayer/issues/2145)).
* MPEG-TS: Avoid failure when expected ID3 header not found
  ([#1966](https://github.com/google/ExoPlayer/issues/1966)).
* Misc bugfixes.

### r1.5.12 ###

* Improvements to Cronet network stack extension.
* Fix bug in demo app introduced in r1.5.11 that caused L3 Widevine
  provisioning requests to fail.
* Misc bugfixes.

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
