# Release notes

### dev-v2 (not yet released)

*   Core Library:
    *   Add `needsReconfiguration` API to the `MediaCodecAdapter` interface.
    *   Add `fastForward` and `rewind` methods to `Player`.
*   Remove deprecated symbols:
    *   Remove `Player.getPlaybackError`. Use `Player.getPlayerError` instead.
    *   Remove `Player.getCurrentTag`. Use `Player.getCurrentMediaItem` and
        `MediaItem.PlaybackProperties.tag` instead.
    *   Remove `Player.Listener.onTimelineChanged(Timeline, Object, int)`. Use
        `Player.Listener.onTimelineChanged(Timeline, int)` instead. The manifest
        can be accessed using `Player.getCurrentManifest`.
    *   Remove `PlaybackPreparer`. UI components that previously had
        `setPlaybackPreparer` methods will now call `Player.prepare` by default.
        If this behavior is sufficient, use of `PlaybackPreparer` can be removed
        from application code without replacement. For custom preparation logic,
        replace calls to `setPlaybackPreparer` with calls to
        `setControlDispatcher` on the same components, passing a
        `ControlDispatcher` that implements custom preparation logic in
        `dispatchPrepare`. Extend `DefaultControlDispatcher` to avoid having to
        implement the other `ControlDispatcher` methods.
    *   Remove `setRewindIncrementMs` and `setFastForwardIncrementMs` from UI
        components. Use `setControlDispatcher` on the same components, passing a
        `DefaultControlDispatcher` built using `DefaultControlDispatcher(long,
        long)`.
    *   Remove `PlayerNotificationManager` constructors and `createWith`
        methods. Use `PlayerNotificationManager.Builder` instead.
    *   Remove `PlayerNotificationManager.setNotificationListener`. Use
        `PlayerNotificationManager.Builder.setNotificationListener` instead.
    *   Remove `PlayerNotificationManager` `setUseNavigationActions` and
        `setUseNavigationActionsInCompactView`. Use `setUseNextAction`,
        `setUsePreviousAction`, `setUseNextActionInCompactView` and
        `setUsePreviousActionInCompactView` instead.
    *   Remove `Format.create` methods. Use `Format.Builder` instead.
    *   Remove `Timeline.getWindow(int, Window, boolean)`. Use
        `Timeline.getWindow(int, Window)` instead, which will always set tags.
    *   Remove `MediaSource.getTag`. Use `MediaSource.getMediaItem` and
        `MediaItem.PlaybackProperties.tag` instead.
    *   Remove `CastPlayer` specific playlist manipulation methods. Use
        `setMediaItems`, `addMediaItems`, `removeMediaItem` and `moveMediaItem`
        instead.
*   UI
    *   Fix focusability of `StyledPlayerView` and `StyledPlayerControlView`
        popup menus on API levels prior to 26
        ([#9061](https://github.com/google/ExoPlayer/issues/9061)).
*   Video:
    *   Fix `IncorrectContextUseViolation` strict mode warning on Android 11
        ([#8246](https://github.com/google/ExoPlayer/pull/8246)).
*   Audio:
    *   Fix track selection for E-AC-3 streams.
    *   Use `AudioTrack.isDirectPlaybackSupported` to check for encoded audio
        passthrough capability from API 29 onwards, instead of using the HDMI
        audio plug intent
        ([#6500](https://github.com/google/ExoPlayer/pull/6500)).
*   Extractors:
    *   Fix issue where a `trun` atom could be associated with the wrong track
        in an FMP4 stream
        ([#9056](https://github.com/google/ExoPlayer/pull/9056)). The fix
        removes a previous workaround to handle content in which the `track_ID`
        is set incorrectly
        ([#4083](https://github.com/google/ExoPlayer/issues/4083)). Such content
        is malformed and should be re-encoded.
*   HLS:
    *   Fix issue where a new initialization segment, as specified by an
        `EXT-X-MAP` tag in a media playlist, would not be loaded when
        encountered during playback
        ([#9004](https://github.com/google/ExoPlayer/issues/9004)).
*   Ad playback:
    *   Support changing ad break positions in the player logic
        ([#5067](https://github.com/google/ExoPlayer/issues/5067).
    *   Support resuming content with an offset after an ad group.
*   DRM:
    *   Allow repeated provisioning in `DefaultDrmSession(Manager)`.
*   PlayerNotificationManager:
    *   Add `PendingIntent.FLAG_IMMUTABLE` flag to BroadcastReceiver to support
        Android 12.
    *   Add `setUseFastForwardActionInCompactView(boolean)` and
        `setUseRewindActionInCompactView(boolean)` to make it possible to show
        seek actions in compact view mode.
*   HLS
    *   Forward the FRAME-RATE value from the master playlist to renditions.
        ([#8960](https://github.com/google/ExoPlayer/issues/8960)).

### 2.14.1 (2021-06-11)

*   Core Library:
    *   Fix gradle config to allow specifying a relative path for
        `exoplayerRoot` when [depending on ExoPlayer locally](README.md#locally)
        ([#8927](https://github.com/google/ExoPlayer/issues/8927)).
    *   Update `MediaItem.Builder` javadoc to discourage calling setters that
        will be (currently) ignored if another setter is not also called.
*   Extractors:
    *   Add support for MPEG-H 3D Audio in MP4 extractors
        ([#8860](https://github.com/google/ExoPlayer/pull/8860)).
*   Video:
    *   Fix bug that could cause `CodecException: Error 0xffffffff` to be thrown
        from `MediaCodec.native_setSurface` in use cases that involve both
        swapping the output `Surface` and a mixture of secure and non-secure
        content being played
        ([#8776](https://github.com/google/ExoPlayer/issues/8776)).
*   HLS:
    *   Use the `PRECISE` attribute in `EXT-X-START` to select the default start
        position.
    *   Fix a bug where skipping into spliced-in chunks triggered an assertion
        error ([#8937](https://github.com/google/ExoPlayer/issues/8937)).
*   DRM:
    *   Keep secure `MediaCodec` instances initialized when disabling (but not
        resetting) `MediaCodecRenderer`. This helps re-use secure decoders in
        more contexts, which avoids the 'black flash' caused by detaching a
        `Surface` from a secure decoder on some devices
        ([#8842](https://github.com/google/ExoPlayer/issues/8842)). It will also
        result in DRM license refresh network requests while the player is
        stopped if `Player#setForegroundMode` is true.
    *   Fix issue where offline keys were unnecessarily (and incorrectly)
        restored into a session before being released. This call sequence is
        explicitly disallowed in OEMCrypto v16.
*   UI:
    *   Keep subtitle language features embedded (e.g. rubies & tate-chu-yoko)
        in `Cue.text` even when `SubtitleView#setApplyEmbeddedStyles()` is
        `false`.
    *   Fix `NullPointerException` in `StyledPlayerView` that could occur after
        calling `StyledPlayerView.setPlayer(null)`
        ([#8985](https://github.com/google/ExoPlayer/issues/8985)).
*   RTSP:
    *   Add support for RTSP basic and digest authentication
        ([#8941](https://github.com/google/ExoPlayer/issues/8941)).
    *   Enable using repeat mode and playlist with RTSP
        ([#8994](https://github.com/google/ExoPlayer/issues/8994)).
    *   Add `RtspMediaSource.Factory` option to set the RTSP user agent.
    *   Add `RtspMediaSource.Factory` option to force using TCP for streaming.
*   GL demo app:
    *   Fix texture transformation to avoid green bars shown on some videos
        ([#8992](https://github.com/google/ExoPlayer/issues/8992)).

### 2.14.0 (2021-05-13)

*   Core Library:
    *   Move `Player` components to `ExoPlayer`. For example
        `Player.VideoComponent` is now `ExoPlayer.VideoComponent`.
    *   The most used methods of `Player`'s audio, video, text and metadata
        components have been added directly to `Player`.
    *   Add `Player.getAvailableCommands`, `Player.isCommandAvailable` and
        `Listener.onAvailableCommandsChanged` to query which commands
        that can be executed on the player.
    *   Add a `Player.Listener` interface to receive all player events.
        Component listeners and `EventListener` have been deprecated.
    *   Add `Player.getMediaMetadata`, which returns a combined and structured
        `MediaMetadata` object. Changes to metadata are reported to
        `Listener.onMediaMetadataChanged`.
    *   `Player.setPlaybackParameters` no longer accepts null, use
        `PlaybackParameters.DEFAULT` instead.
    *   Report information about the old and the new playback positions to
        `Listener.onPositionDiscontinuity`. Add `DISCONTINUITY_REASON_SKIP`
        and `DISCONTINUITY_REASON_REMOVE` as discontinuity reasons, and rename
        `DISCONTINUITY_REASON_PERIOD_TRANSITION` to
        `DISCONTINUITY_REASON_AUTO_TRANSITION`. Remove
        `DISCONTINUITY_REASON_AD_INSERTION`, for which
        `DISCONTINUITY_REASON_AUTO_TRANSITION` is used instead
        ([#6163](https://github.com/google/ExoPlayer/issues/6163),
        [#4768](https://github.com/google/ExoPlayer/issues/4768)).
    *   Deprecate `ExoPlayer.Builder`. Use `SimpleExoPlayer.Builder` instead.
    *   Move `Player.getRendererCount` and `Player.getRendererType` to
        `ExoPlayer`.
    *   Use an empty string instead of the URI if the media ID is not explicitly
        set with `MediaItem.Builder.setMediaId(String)`.
    *   Remove `MediaCodecRenderer.configureCodec()` and add
        `MediaCodecRenderer.getMediaCodecConfiguration()`. The new method is
        called just before the `MediaCodec` is created and returns the
        parameters needed to create and configure the `MediaCodec` instance.
        Applications can override `MediaCodecRenderer.onCodecInitialized()` to
        be notified after a `MediaCodec` is initialized, or they can inject a
        custom `MediaCodecAdapter.Factory` if they want to control how the
        `MediaCodec` is configured.
    *   Promote `AdaptiveTrackSelection.AdaptationCheckpoint` to `public`
        visibility to allow Kotlin subclasses of
        `AdaptiveTrackSelection.Factory`
        ([#8830](https://github.com/google/ExoPlayer/issues/8830)).
    *   Fix bug when transitions from content to ad periods called
        `onMediaItemTransition` by mistake.
    *   `AdsLoader.AdViewProvider` and `AdsLoader.OverlayInfo` have been renamed
        `com.google.android.exoplayer2.ui.AdViewProvider` and
        `com.google.android.exoplayer2.ui.AdOverlayInfo` respectively.
    *   `CaptionStyleCompat` has been moved to the
        `com.google.android.exoplayer2.ui` package.
    *   `DebugTextViewHelper` has been moved from the `ui` package to the `util`
        package.
*   RTSP:
    *   Initial support for RTSP playbacks
        ([#55](https://github.com/google/ExoPlayer/issues/55)).
*   Downloads and caching:
    *   Fix `CacheWriter` to correctly handle cases where the request `DataSpec`
        extends beyond the end of the underlying resource. Caching will now
        succeed in this case, with data up to the end of the resource being
        cached. This behaviour is enabled by default, and so the
        `allowShortContent` parameter has been removed
        ([#7326](https://github.com/google/ExoPlayer/issues/7326)).
    *   Fix `CacheWriter` to correctly handle `DataSource.close` failures, for
        which it cannot be assumed that data was successfully written to the
        cache.
*   DRM:
    *   Prepare DRM sessions (and fetch keys) ahead of the playback position
        ([#4133](https://github.com/google/ExoPlayer/issues/4133)).
    *   Only dispatch DRM session acquire and release events once per period
        when playing content that uses the same encryption keys for both audio &
        video tracks. Previously, separate acquire and release events were
        dispatched for each track in each period.
    *   Include the session state in DRM session-acquired listener methods.
*   UI:
    *   Add `PlayerNotificationManager.Builder`, with the ability to
        specify which group the notification should belong to.
    *   Remove `setUseSensorRotation` from `PlayerView` and `StyledPlayerView`.
        Instead, cast the view returned by `getVideoSurfaceView` to
        `SphericalGLSurfaceView`, and then call `setUseSensorRotation` on the
        `SphericalGLSurfaceView` directly.
*   Analytics:
    *   Add `onAudioCodecError` and `onVideoCodecError` to `AnalyticsListener`.
*   Video:
    *   Add `Player.getVideoSize()` to retrieve the current size of the video
        stream. Add `Listener.onVideoSizeChanged(VideoSize)` and deprecate
        `Listener.onVideoSizeChanged(int, int, int, float)`.
*   Audio:
    *   Report unexpected audio discontinuities to
        `AnalyticsListener.onAudioSinkError`
        ([#6384](https://github.com/google/ExoPlayer/issues/6384)).
    *   Allow forcing offload for gapless content even if gapless playback is
        not supported.
    *   Allow fall back from DTS-HD to DTS when playing via passthrough.
*   Text:
    *   Fix overlapping lines when using `SubtitleView.VIEW_TYPE_WEB`.
    *   Parse SSA/ASS underline & strikethrough info in `Style:` lines
        ([#8435](https://github.com/google/ExoPlayer/issues/8435)).
    *   Ensure TTML `tts:textAlign` is correctly propagated from `<p>` nodes to
        child nodes.
    *   Support TTML `ebutts:multiRowAlign` attributes.
*   Allow the use of Android platform extractors through
    [MediaParser](https://developer.android.com/reference/android/media/MediaParser).
    Only supported on API 30+.
    *   You can use platform extractors for progressive media by passing
        `MediaParserExtractorAdapter.FACTORY` when creating a
        `ProgressiveMediaSource.Factory`.
    *   You can use platform extractors for HLS by passing
        `MediaParserHlsMediaChunkExtractor.FACTORY` when creating a
        `HlsMediaSource.Factory`.
    *   You can use platform extractors for DASH by passing a
        `DefaultDashChunkSource` that uses `MediaParserChunkExtractor.FACTORY`
        when creating a `DashMediaSource.Factory`.
*   Cast extension:
    *   Trigger `onMediaItemTransition` event for all reasons except
        `MEDIA_ITEM_TRANSITION_REASON_REPEAT`.
*   MediaSession extension:
    *   Remove dependency on `exoplayer-core`, relying only `exoplayer-common`
        instead. To achieve this, `TimelineQueueEditor` uses a new
        `MediaDescriptionConverter` interface, and no longer relies on
        `ConcatenatingMediaSource`.
*   Remove deprecated symbols:
    *   Remove `ExoPlayerFactory`. Use `SimpleExoPlayer.Builder` instead.
    *   Remove `Player.DefaultEventListener`. Use `Player.Listener` instead.
    *   Remove `ExtractorMediaSource`. Use `ProgressiveMediaSource` instead.
    *   Remove `DefaultMediaSourceEventListener`. Use `MediaSourceEventListener`
        instead.
    *   Remove `DashManifest` constructor. Use the remaining constructor with
        `programInformation` and `serviceDescription` set to `null` instead.
    *   Remove `CryptoInfo.getFrameworkCryptoInfoV16`. Use
        `CryptoInfo.getFrameworkCryptoInfo` instead.
    *   Remove `NotificationUtil.createNotificationChannel(Context, String, int,
        int)`. Use `createNotificationChannel(Context, String, int, int, int)`
        instead.
    *   Remove `PlayerNotificationManager.setNotificationListener`. Use
        `PlayerNotificationManager.Builder.setNotificationListener` instead.
    *   Remove `PlayerNotificationManager.NotificationListener`
        `onNotificationStarted(int, Notification)` and
        `onNotificationCancelled(int)`. Use `onNotificationPosted(int,
        Notification, boolean)` and `onNotificationCancelled(int, boolean)`
        instead.
    *   Remove `DownloadNotificationUtil`. Use `DownloadNotificationHelper`
        instead.
    *   Remove `extension-jobdispatcher` module. Use the `extension-workmanager`
        module instead.

### 2.13.3 (2021-04-14)

*   Published via the Google Maven repository (i.e., google()) rather than JCenter.
*   Core:
    *   Reset playback speed when live playback speed control becomes unused
        ([#8664](https://github.com/google/ExoPlayer/issues/8664)).
    *   Fix playback position issue when re-preparing playback after a
        `BehindLiveWindowException`
        ([#8675](https://github.com/google/ExoPlayer/issues/8675)).
    *   Assume Dolby Vision content is encoded as H264 when calculating maximum
        codec input size
        ([#8705](https://github.com/google/ExoPlayer/issues/8705)).
*   UI:
    *   Fix `StyledPlayerView` scrubber not reappearing correctly in some cases
        ([#8646](https://github.com/google/ExoPlayer/issues/8646)).
    *   Fix measurement of `StyledPlayerView` and `StyledPlayerControlView` when
        `wrap_content` is used
        ([#8726](https://github.com/google/ExoPlayer/issues/8726)).
    *   Fix `StyledPlayerControlView` to stay in full mode (rather than minimal
        mode) when possible
        ([#8763](https://github.com/google/ExoPlayer/issues/8763)).
*   DASH:
    *   Parse `forced_subtitle` role from DASH manifests
        ([#8781](https://github.com/google/ExoPlayer/issues/8781)).
*   DASH:
    *   Fix rounding error that could cause `SegmentTemplate.getSegmentCount()`
        to return incorrect values
        ([#8804](https://github.com/google/ExoPlayer/issues/8804)).
*   HLS:
    *   Fix bug of ignoring `EXT-X-START` when setting the live target offset
        ([#8764](https://github.com/google/ExoPlayer/pull/8764)).
    *   Fix incorrect application of byte ranges to `EXT-X-MAP` tags
        ([#8783](https://github.com/google/ExoPlayer/issues/8783)).
    *   Fix issue that could cause playback to become stuck if corresponding
        `EXT-X-DISCONTINUITY` tags in different media playlists occur at
        different positions in time
        ([#8372](https://github.com/google/ExoPlayer/issues/8372)).
    *   Fix issue that could cause playback of on-demand content to not start in
        cases where the media playlists referenced by the master playlist have
        different starting `EXT-X-PROGRAM-DATE-TIME` tags.
    *   Fix container type detection for segments with incorrect file extension
        or HTTP Content-Type
        ([#8733](https://github.com/google/ExoPlayer/issues/8733)).
*   Extractors:
    *   Add support for `GContainer` and `GContainerItem` XMP namespace prefixes
        in JPEG motion photo parsing.
    *   Allow JFIF APP0 marker segment preceding Exif APP1 segment in
        `JpegExtractor`.
*   Text:
    *   Parse SSA/ASS bold & italic info in `Style:` lines
        ([#8435](https://github.com/google/ExoPlayer/issues/8435)).
    *   Don't display subtitles after the end position of the current media
        period (if known). This ensures sideloaded subtitles respect the end
        point of `ClippingMediaPeriod` and prevents content subtitles from
        continuing to be displayed over mid-roll ads
        ([#5317](https://github.com/google/ExoPlayer/issues/5317),
        [#8456](https://github.com/google/ExoPlayer/issues/8456)).
    *   Fix CEA-708 priority handling to sort cues in the order defined by the
        spec ([#8704](https://github.com/google/ExoPlayer/issues/8704)).
    *   Support TTML `textEmphasis` attributes, used for Japanese boutens.
    *   Support TTML `shear` attributes.
*   Metadata:
    *   Ensure that timed metadata near the end of a period is not dropped
        ([#8710](https://github.com/google/ExoPlayer/issues/8710)).
*   Cast extension:
    *   Fix `onPositionDiscontinuity` event so that it is not triggered with
        reason `DISCONTINUITY_REASON_PERIOD_TRANSITION` after a seek to another
        media item and so that it is not triggered after a timeline change.
*   IMA extension:
    *   Fix error caused by `AdPlaybackState` ad group times being cleared,
        which can occur if the `ImaAdsLoader` is released while an ad is pending
        loading ([#8693](https://github.com/google/ExoPlayer/issues/8693)).
    *   Upgrade IMA SDK dependency to 3.23.0, fixing an issue with
        `NullPointerExceptions` within `WebView` callbacks
        ([#8447](https://github.com/google/ExoPlayer/issues/8447)).
*   FFmpeg extension: Fix playback failure when switching to TrueHD tracks
    during playback ([#8616](https://github.com/google/ExoPlayer/issues/8616)).

### 2.13.2 (2021-02-25)

*   Extractors:
    *   Add support for MP4 and QuickTime meta atoms that are not full atoms.
*   UI:
    *   Make conditions to enable UI actions consistent in
        `DefaultControlDispatcher`, `PlayerControlView`,
        `StyledPlayerControlView`, `PlayerNotificationManager` and
        `TimelineQueueNavigator`.
    *   Fix conditions to enable seeking to next/previous media item to handle
        the case where a live stream has ended.
*   Audio:
    *   Fix `SimpleExoPlayer` reporting audio session ID as 0 in some cases
        ([#8585](https://github.com/google/ExoPlayer/issues/8585)).
*   IMA extension:
    *   Fix a bug where playback could get stuck when seeking into a playlist
        item with ads, if the preroll ad had preloaded but the window position
        of the seek should instead trigger playback of a midroll.
    *   Fix a bug with playback of ads in playlists, where the incorrect period
        index was used when deciding whether to trigger playback of an ad after
        a seek.
*   Text:
    *   Parse SSA/ASS font size in `Style:` lines
        ([#8435](https://github.com/google/ExoPlayer/issues/8435)).
*   VP9 extension: Update to use NDK r21
    ([#8581](https://github.com/google/ExoPlayer/issues/8581)).
*   FLAC extension: Update to use NDK r21
    ([#8581](https://github.com/google/ExoPlayer/issues/8581)).
*   Opus extension: Update to use NDK r21
    ([#8581](https://github.com/google/ExoPlayer/issues/8581)).
*   FFmpeg extension: Update to use NDK r21
    ([#8581](https://github.com/google/ExoPlayer/issues/8581)).

### 2.13.1 (2021-02-12)

*   Live streaming:
    *   Fix playback issue for HLS live streams without program date time
        information ([#8560](https://github.com/google/ExoPlayer/issues/8560)).
    *   Fix playback issue for multi-period DASH live streams
        ([#8537](https://github.com/google/ExoPlayer/issues/8537)).
    *   Fix playback failures when playing live streams with video tunneling
        enabled ([#8570](https://github.com/google/ExoPlayer/issues/8570)).
*   IMA extension:
    *   Fix handling of repeated ad loads, to avoid ads being discarded if the
        user seeks away and then back to a preloaded postroll (for example).
    *   Fix a bug where an assertion would fail if the player started to buffer
        an ad media period before the ad URI was known then an ad state update
        arrived that didn't set the ad URI.
    *   Add `ImaAdsLoader.focusSkipButton` to allow apps to request that the
        skip button should receive UI focus, if shown
        ([#8565](https://github.com/google/ExoPlayer/issues/8565)).
*   DRM:
    *   Re-use the previous `DrmSessionManager` instance when playing a playlist
        (if possible)
        ([#8523](https://github.com/google/ExoPlayer/issues/8523)).
    *   Propagate DRM configuration when creating media sources for ad content
        ([#8568](https://github.com/google/ExoPlayer/issues/8568)).
    *   Only release 'keepalive' references to `DrmSession` in
        `DefaultDrmSessionManager#release()` if keepalive is enabled
        ([#8576](https://github.com/google/ExoPlayer/issues/8576)).

### 2.13.0 (2021-02-04)

*   Core library:
    *   Verify correct thread usage in `SimpleExoPlayer` by default. Opt-out is
        still possible until the next major release using
        `setThrowsWhenUsingWrongThread(false)`
        ([#4463](https://github.com/google/ExoPlayer/issues/4463)).
    *   Add `Player.getCurrentStaticMetadata` and
        `EventListener.onStaticMetadataChanged` to expose static metadata
        belonging to the currently playing stream
        ([#7266](https://github.com/google/ExoPlayer/issues/7266)).
    *   Add `PlayerMessage.setLooper` and deprecate `PlayerMessage.setHandler`.
    *   Add option to `MergingMediaSource` to clip the durations of all sources
        to have the same length
        ([#8422](https://github.com/google/ExoPlayer/issues/8422)).
    *   Remove `Player.setVideoDecoderOutputBufferRenderer` from Player API. Use
        `setVideoSurfaceView` and `clearVideoSurfaceView` instead.
    *   Default `SingleSampleMediaSource.treatLoadErrorsAsEndOfStream` to `true`
        so that errors loading external subtitle files do not cause playback
        to fail ([#8430](https://github.com/google/ExoPlayer/issues/8430)). A
        warning will be logged by `SingleSampleMediaPeriod` whenever a load
        error is treated as though the end of the stream has been reached.
    *   Time out on release to prevent ANRs if an underlying platform call is
        stuck ([#4352](https://github.com/google/ExoPlayer/issues/4352)).
    *   Time out when detaching a surface to prevent ANRs if the underlying
        platform call is stuck
        ([#5887](https://github.com/google/ExoPlayer/issues/5887)).
    *   Fix bug where `AnalyticsListener` callbacks could arrive in the wrong
        order ([#8048](https://github.com/google/ExoPlayer/issues/8048)).
*   Media transformation:
    *   Add a new `transformer` module for converting media streams. The
        initially available transformations are changing the container format,
        removing tracks, and slow motion flattening.
*   Low latency live streaming:
    *   Support low-latency DASH (also known as ULL-CMAF) and Apple's
        low-latency HLS extension.
    *   Add `LiveConfiguration` to `MediaItem` to define live offset and
        playback speed adjustment parameters. The same parameters can be set on
        `DefaultMediaSourceFactory` to apply for all `MediaItems`.
    *   Add `LivePlaybackSpeedControl` to control playback speed adjustments
        during live playbacks. Such adjustments allow the player to stay close
        to the live offset. `DefaultLivePlaybackSpeedControl` is provided as a
        default implementation.
    *   Add `targetLiveOffsetUs` parameter to `LoadControl.shouldStartPlayback`.
*   Extractors:
    *   Populate codecs string for H.264/AVC in MP4, Matroska and FLV streams to
        allow decoder capability checks based on codec profile and level
        ([#8393](https://github.com/google/ExoPlayer/issues/8393)).
    *   Populate codecs string for H.265/HEVC in MP4, Matroska and MPEG-TS
        streams to allow decoder capability checks based on codec profile and
        level ([#8393](https://github.com/google/ExoPlayer/issues/8393)).
    *   Add support for playing JPEG motion photos
        ([#5405](https://github.com/google/ExoPlayer/issues/5405)).
    *   Handle sample size mismatches between raw audio `stsd` information and
        `stsz` fixed sample size in MP4 extractors.
    *   Fix Vorbis private codec data parsing in the Matroska extractor
        ([#8496](https://github.com/google/ExoPlayer/issues/8496)).
*   Track selection:
    *   Move `Player.getTrackSelector` to the `ExoPlayer` interface.
    *   Move the mutable parts of `TrackSelection` into an `ExoTrackSelection`
        subclass.
    *   Allow parallel adaptation of video and audio
        ([#5111](https://github.com/google/ExoPlayer/issues/5111)).
    *   Simplify enabling tunneling with `DefaultTrackSelector`.
        `ParametersBuilder.setTunnelingAudioSessionId` has been replaced with
        `ParametersBuilder.setTunnelingEnabled`. The player's audio session ID
        will be used, and so a tunneling specific ID is no longer needed.
    *   Add additional configuration parameters to `DefaultTrackSelector`.
        `DefaultTrackSelector.ParametersBuilder` now includes:
        *   `setPreferredVideoMimeType`, `setPreferredVideoMimeTypes`,
            `setPreferredAudioMimeType` and `setPreferredAudioMimeTypes` for
            specifying preferred video and audio MIME type(s)
            ([#8320](https://github.com/google/ExoPlayer/issues/8320)).
        *   `setPreferredAudioLanguages` and `setPreferredTextLanguages` for
            specifying multiple preferred audio and text languages.
        *   `setPreferredAudioRoleFlags` for specifying preferred audio role
            flags.
    *   Forward `Timeline` and `MediaPeriodId` to `TrackSelection.Factory`.
*   DASH:
    *   Support low-latency DASH playback (`availabilityTimeOffset` and
        `ServiceDescription` tags)
        ([#4904](https://github.com/google/ExoPlayer/issues/4904)).
    *   Improve logic for determining whether to refresh the manifest when a
        chunk load error occurs in a live streams that contains EMSG data
        ([#8408](https://github.com/google/ExoPlayer/issues/8408)).
*   HLS:
    *   Support playlist delta updates, blocking playlist reloads and rendition
        reports.
    *   Support low-latency HLS playback (`EXT-X-PART` and preload hints)
        ([#5011](https://github.com/google/ExoPlayer/issues/5011)).
*   UI:
    *   Improve `StyledPlayerControlView` button animations.
    *   Miscellaneous fixes for `StyledPlayerControlView` in minimal mode.
*   DRM:
    *   Fix playback failure when switching from PlayReady protected content to
        Widevine or Clearkey protected content in a playlist.
    *   Add `ExoMediaDrm.KeyRequest.getRequestType`
        ([#7847](https://github.com/google/ExoPlayer/issues/7847)).
    *   Drop key and provision responses if `DefaultDrmSession` is released
        while waiting for the response. This prevents harmless log messages of
        the form:
        `IllegalStateException: sending message to a Handler on a dead thread`
        ([#8328](https://github.com/google/ExoPlayer/issues/8328)).
    *   Allow apps to fully customize DRM behaviour for each `MediaItem` by
        passing a `DrmSessionManagerProvider` to `MediaSourceFactory`
        ([#8466](https://github.com/google/ExoPlayer/issues/8466)).
*   Analytics:
    *   Add an `onEvents` callback to `Player.EventListener` and
        `AnalyticsListener`. When one or more player states change
        simultaneously, `onEvents` is called once after all of the callbacks
        associated with the individual state changes.
    *   Pass a `DecoderReuseEvaluation` to `AnalyticsListener`'s
        `onVideoInputFormatChanged` and `onAudioInputFormatChanged` methods. The
        `DecoderReuseEvaluation` indicates whether it was possible to re-use an
        existing decoder instance for the new format, and if not then the
        reasons why.
*   Video:
    *   Fall back to AVC/HEVC decoders for Dolby Vision streams with level 10
        to 13 ([#8530](https://github.com/google/ExoPlayer/issues/8530)).
    *   Fix VP9 format capability checks on API level 23 and earlier. The
        platform does not correctly report the VP9 level supported by the
        decoder in this case, so we estimate it based on the decoder's maximum
        supported bitrate.
*   Audio:
    *   Fix handling of audio session IDs
        ([#8190](https://github.com/google/ExoPlayer/issues/8190)).
        `SimpleExoPlayer` now generates an audio session ID on construction,
        which can be immediately queried by calling
        `SimpleExoPlayer.getAudioSessionId`. The audio session ID will only
        change if application code calls `SimpleExoPlayer.setAudioSessionId`.
    *   Replace `onAudioSessionId` with `onAudioSessionIdChanged` in
        `AudioListener` and `AnalyticsListener`. Note that
        `onAudioSessionIdChanged` is called in fewer cases than
        `onAudioSessionId` was called, due to the improved handling of audio
        session IDs as described above.
    *   Retry playback after some types of `AudioTrack` error.
    *   Create E-AC3 JOC passthrough `AudioTrack` instances using the maximum
        supported channel count (instead of assuming 6 channels) from API 29.
*   Text:
    *   Add support for the SSA `primaryColour` style attribute
        ([#8435](https://github.com/google/ExoPlayer/issues/8435)).
    *   Fix CEA-708 sequence number discontinuity handling
        ([#1807](https://github.com/google/ExoPlayer/issues/1807)).
    *   Fix CEA-708 handling of unexpectedly small packets
        ([#1807](https://github.com/google/ExoPlayer/issues/1807)).
*   Data sources:
    *   For `HttpDataSource` implementations, default to using the user agent of
        the underlying network stack.
    *   Deprecate `HttpDataSource.Factory.getDefaultRequestProperties`.
        `HttpDataSource.Factory.setDefaultRequestProperties` instead.
    *   Add `DefaultHttpDataSource.Factory` and deprecate
        `DefaultHttpDataSourceFactory`.
*   Metadata retriever:
    *   Parse Google Photos HEIC and JPEG motion photo metadata.
*   IMA extension:
    *   Add support for playback of ads in playlists
        ([#3750](https://github.com/google/ExoPlayer/issues/3750)).
    *   Add `ImaAdsLoader.Builder.setEnableContinuousPlayback` for setting
        whether to request ads for continuous playback.
    *   Upgrade IMA SDK dependency to 3.22.0. This fixes leaking of the ad view
        group ([#7344](https://github.com/google/ExoPlayer/issues/7344),
        [#8339](https://github.com/google/ExoPlayer/issues/8339)).
    *   Fix a bug that could cause the next content position played after a seek
        to snap back to the cue point of the preceding ad, rather than the
        requested content position.
    *   Fix a regression that caused an ad group to be skipped after an initial
        seek to a non-zero position. Unsupported VPAID ads will still be
        skipped, but only after the preload timeout rather than instantly
        ([#8428](https://github.com/google/ExoPlayer/issues/8428),
        [#7832](https://github.com/google/ExoPlayer/issues/7832)).
    *   Fix a regression that caused a short ad followed by another ad to be
        skipped due to playback being stuck buffering waiting for the second ad
        to load ([#8492](https://github.com/google/ExoPlayer/issues/8492)).
*   FFmpeg extension:
    *   Link the FFmpeg library statically, saving 350KB in binary size on
        average.
*   OkHttp extension:
    *   Add `OkHttpDataSource.Factory` and deprecate `OkHttpDataSourceFactory`.
*   Cronet extension:
    *   Add `CronetDataSource.Factory` and deprecate `CronetDataSourceFactory`.
    *   Support setting the user agent on `CronetDataSource.Factory` and
        `CronetEngineWrapper`.
*   MediaSession extension:
    *   Support `setPlaybackSpeed(float)` and disable it by default. Use
        `MediaSessionConnector.setEnabledPlaybackActions(long)` to enable
        ([#8229](https://github.com/google/ExoPlayer/issues/8229)).
*   Remove deprecated symbols:
    *   `AdaptiveMediaSourceEventListener`. Use `MediaSourceEventListener`
        instead.
    *   `DashMediaSource.Factory.setMinLoadableRetryCount(int)`. Use
        `DashMediaSource.Factory.setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy)`
        instead.
    *   `DefaultAnalyticsListener`. Use `AnalyticsListener` instead.
    *   `DefaultLoadControl` constructors. Use `DefaultLoadControl.Builder`
        instead.
    *   `DrmInitData.get(UUID)`. Use `DrmInitData.get(int)` and
        `DrmInitData.SchemeData.matches(UUID)` instead.
    *   `ExtractorsMediaSource.Factory.setMinLoadableRetryCount(int)`. Use
        `ExtractorsMediaSource.Factory.setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy)`
        instead.
    *   `FixedTrackSelection.Factory`. If you need to disable adaptive
        selection in `DefaultTrackSelector`, enable the
        `DefaultTrackSelector.Parameters.forceHighestSupportedBitrate` flag.
    *   `HlsMediaSource.Factory.setMinLoadableRetryCount(int)`. Use
        `HlsMediaSource.Factory.setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy)`
        instead.
    *   `MappedTrackInfo.getTrackFormatSupport(int, int, int)`. Use
        `MappedTrackInfo.getTrackSupport(int, int, int)` instead.
    *   `MappedTrackInfo.getTrackTypeRendererSupport(int)`. Use
        `MappedTrackInfo.getTypeSupport(int)` instead.
    *   `MappedTrackInfo.getUnassociatedTrackGroups()`. Use
        `MappedTrackInfo.getUnmappedTrackGroups()` instead.
    *   `MappedTrackInfo.length`. Use `MappedTrackInfo.getRendererCount()`
        instead.
    *   `Player.DefaultEventListener.onTimelineChanged(Timeline, Object)`.
        Use `Player.EventListener.onTimelineChanged(Timeline, int)` instead.
    *   `Player.setAudioAttributes(AudioAttributes)`. Use
        `Player.AudioComponent.setAudioAttributes(AudioAttributes, boolean)`
        instead.
    *   `PlayerView.setDefaultArtwork(Bitmap)`. Use
        `PlayerView.setDefaultArtwork(Drawable)` instead.
    *   `PlayerView.setShowBuffering(boolean)`. Use
        `PlayerView.setShowBuffering(int)` instead.
    *   `SimpleExoPlayer.clearMetadataOutput(MetadataOutput)`. Use
        `SimpleExoPlayer.removeMetadataOutput(MetadataOutput)` instead.
    *   `SimpleExoPlayer.clearTextOutput(TextOutput)`. Use
        `SimpleExoPlayer.removeTextOutput(TextOutput)` instead.
    *   `SimpleExoPlayer.clearVideoListener()`. Use
        `SimpleExoPlayer.removeVideoListener(VideoListener)` instead.
    *   `SimpleExoPlayer.getAudioStreamType()`. Use
        `SimpleExoPlayer.getAudioAttributes()` instead.
    *   `SimpleExoPlayer.setAudioDebugListener(AudioRendererEventListener)`.
        Use `SimpleExoPlayer.addAnalyticsListener(AnalyticsListener)` instead.
    *   `SimpleExoPlayer.setAudioStreamType(int)`. Use
        `SimpleExoPlayer.setAudioAttributes(AudioAttributes)` instead.
    *   `SimpleExoPlayer.setMetadataOutput(MetadataOutput)`. Use
        `SimpleExoPlayer.addMetadataOutput(MetadataOutput)` instead. If your
        application is calling `SimpleExoPlayer.setMetadataOutput(null)`, make
        sure to replace this call with a call to
        `SimpleExoPlayer.removeMetadataOutput(MetadataOutput)`.
    *   `SimpleExoPlayer.setPlaybackParams(PlaybackParams)`. Use
        `SimpleExoPlayer.setPlaybackParameters(PlaybackParameters)` instead.
    *   `SimpleExoPlayer.setTextOutput(TextOutput)`. Use
        `SimpleExoPlayer.addTextOutput(TextOutput)` instead. If your
        application is calling `SimpleExoPlayer.setTextOutput(null)`, make sure
        to replace this call with a call to
        `SimpleExoPlayer.removeTextOutput(TextOutput)`.
    *   `SimpleExoPlayer.setVideoDebugListener(VideoRendererEventListener)`.
        Use `SimpleExoPlayer.addAnalyticsListener(AnalyticsListener)` instead.
    *   `SimpleExoPlayer.setVideoListener(VideoListener)`. Use
        `SimpleExoPlayer.addVideoListener(VideoListener)` instead. If your
        application is calling `SimpleExoPlayer.setVideoListener(null)`, make
        sure to replace this call with a call to
        `SimpleExoPlayer.removeVideoListener(VideoListener)`.
    *   `SimpleExoPlayer.VideoListener`. Use
        `com.google.android.exoplayer2.video.VideoListener` instead.
    *   `SingleSampleMediaSource.EventListener` and constructors. Use
        `MediaSourceEventListener` and `SingleSampleMediaSource.Factory`
        instead.
    *   `SimpleExoPlayer.addVideoDebugListener`,
        `SimpleExoPlayer.removeVideoDebugListener`,
        `SimpleExoPlayer.addAudioDebugListener` and
        `SimpleExoPlayer.removeAudioDebugListener`. Use
        `SimpleExoPlayer.addAnalyticsListener` and
        `SimpleExoPlayer.removeAnalyticsListener` instead.
    *   `SingleSampleMediaSource.Factory.setMinLoadableRetryCount(int)`. Use
        `SingleSampleMediaSource.Factory.setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy)`
        instead.
    *   `SsMediaSource.Factory.setMinLoadableRetryCount(int)`. Use
        `SsMediaSource.Factory.setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy)`
        instead.

### 2.12.3 (2021-01-13) ###

*   Core library:
    *   Fix `MediaCodecRenderer` issue where empty streams would fail to play in
        bypass mode ([#8374](https://github.com/google/ExoPlayer/issues/8374)).
    *   Fix playback issues after seeking during an ad
        ([#8349](https://github.com/google/ExoPlayer/issues/8349)).
    *   Fix propagation of `LoadErrorHandlingPolicy` from
        `DefaultMediaSourceFactory` into `SingleSampleMediaSource.Factory` when
        creating subtitle media sources from
        `MediaItem.playbackProperties.subtitles`
        ([#8430](https://github.com/google/ExoPlayer/issues/8430)).
*   UI:
    *   Fix issue where pop-up menus belonging to `StyledPlayerControlView`
        would not be dismissed when tapping outside of the menu area or pressing
        the back button, on API level 22 and earlier
        ([#8272](https://github.com/google/ExoPlayer/issues/8272)).
*   Downloads:
    *   Fix crash in `DownloadManager` that could occur when adding a stopped
        download with the same ID as a download currently being removed
        ([#8419](https://github.com/google/ExoPlayer/issues/8419)).
*   Text:
    *   Gracefully handle null-terminated subtitle content in Matroska
        containers.
    *   Fix CEA-708 anchor positioning
        ([#1807](https://github.com/google/ExoPlayer/issues/1807)).
*   IMA extension:
    *   Fix a condition where playback could get stuck before an empty ad
        ([#8205](https://github.com/google/ExoPlayer/issues/8205)).
    *   Log a warning rather than throwing when reaching the end of the stream
        with an ad playing but without ad media info
        ([#8290](https://github.com/google/ExoPlayer/issues/8290)).
*   Media2 extension:
    *   Make media2-extension depend on AndroidX media2:media2-session:1.1.0 to
        fix a deadlock while creating PlaybackStateCompat internally.
        ([#8011](https://github.com/google/ExoPlayer/issues/8011)).

### 2.12.2 (2020-12-01) ###

*   Core library:
    *   Suppress exceptions from registering and unregistering the stream volume
        receiver ([#8087](https://github.com/google/ExoPlayer/issues/8087),
        [#8106](https://github.com/google/ExoPlayer/issues/8106)).
    *   Suppress ProGuard warnings caused by Guava's compile-only dependencies
        ([#8103](https://github.com/google/ExoPlayer/issues/8103)).
    *   Fix issue that could cause playback to freeze when selecting tracks, if
        extension audio renderers are being used
        ([#8203](https://github.com/google/ExoPlayer/issues/8203)).
*   UI:
    *   Fix incorrect color and text alignment of the `StyledPlayerControlView`
        fast forward and rewind buttons, when used together with the
        `com.google.android.material` library
        ([#7898](https://github.com/google/ExoPlayer/issues/7898)).
    *   Add `dispatchPrepare(Player)` to `ControlDispatcher` and implement it in
        `DefaultControlDispatcher`. Deprecate `PlaybackPreparer` and
        `setPlaybackPreparer` in `StyledPlayerView`, `StyledPlayerControlView`,
        `PlayerView`, `PlayerControlView`, `PlayerNotificationManager` and
        `LeanbackPlayerAdapter` and use `ControlDispatcher` for dispatching
        prepare instead
        ([#7882](https://github.com/google/ExoPlayer/issues/7882)).
    *   Increase seekbar's touch target height in `StyledPlayerControlView`.
    *   Update `StyledPlayerControlView` menu items to behave correctly for
        right-to-left languages.
    *   Support enabling the previous and next actions individually in
        `PlayerNotificationManager`.
*   Audio:
    *   Work around `AudioManager` crashes when calling `getStreamVolume`
        ([#8191](https://github.com/google/ExoPlayer/issues/8191)).
*   Extractors:
    *   Matroska: Add support for 32-bit floating point PCM, and 8-bit and
        16-bit big endian integer PCM
        ([#8142](https://github.com/google/ExoPlayer/issues/8142)).
    *   MP4: Add support for mpeg1 video box
        ([#8257](https://github.com/google/ExoPlayer/issues/8257)).
*   IMA extension:
    *   Upgrade IMA SDK dependency to 3.21.0, and release the `AdsLoader`
        ([#7344](https://github.com/google/ExoPlayer/issues/7344)).
    *   Improve handling of ad tags with unsupported VPAID ads
        ([#7832](https://github.com/google/ExoPlayer/issues/7832)).
    *   Fix a bug that caused multiple ads in an ad pod to be skipped when one
        ad in the ad pod was skipped.
    *   Fix a bug that caused ad progress not to be updated if the player
        resumed after buffering during an ad
        ([#8239](https://github.com/google/ExoPlayer/issues/8239)).
    *   Fix passing an ads response to the `ImaAdsLoader` builder.
    *   Set the overlay language based on the device locale by default.
*   Cronet extension:
    *   Fix handling of HTTP status code 200 when making unbounded length range
        requests ([#8090](https://github.com/google/ExoPlayer/issues/8090)).
*   Text
    *   Allow tx3g subtitles with `styl` boxes with start and/or end offsets
        that lie outside the length of the cue text.
*   Media2 extension:
    *   Notify onBufferingEnded when the state of origin player becomes
        `STATE_IDLE` or `STATE_ENDED`.
    *   Allow to remove all playlist items that makes the player reset
        ([#8047](https://github.com/google/ExoPlayer/issues/8047)).

### 2.12.1 (2020-10-23) ###

*   Core library:
    *   Fix issue where `Player.setMediaItems` would ignore its `resetPosition`
        argument ([#8024](https://github.com/google/ExoPlayer/issues/8024)).
    *   Fix bug where streams with highly uneven track durations may get stuck
        in a buffering state
        ([#7943](https://github.com/google/ExoPlayer/issues/7943)).
    *   Switch Guava dependency from `implementation` to `api`
        ([#7905](https://github.com/google/ExoPlayer/issues/7905),
        [#7993](https://github.com/google/ExoPlayer/issues/7993)).
    *   Add 403, 500 and 503 to the list of HTTP status codes that can trigger
        failover to another quality variant during adaptive playbacks.
*   Data sources:
    *   Add support for `android.resource` URI scheme in `RawResourceDataSource`
        ([#7866](https://github.com/google/ExoPlayer/issues/7866)).
*   Text:
    *   Add support for `\h` SSA/ASS style override code (non-breaking space).
    *   Fix playback of WebVTT subtitles in MP4 containers in DASH streams
        ([#7985](https://github.com/google/ExoPlayer/issues/7985)).
    *   Fix `NullPointerException` in `TextRenderer` when playing content with a
        single subtitle buffer
        ([#8017](https://github.com/google/ExoPlayer/issues/8017)).
*   UI:
    *   Fix animation when `StyledPlayerView` first shows its playback controls.
    *   Improve touch targets in `StyledPlayerView` to make tapping easier.
    *   Allow `subtitleButton` to be omitted in custom `StyledPlayerView`
        layouts ([#7962](https://github.com/google/ExoPlayer/issues/7962)).
    *   Add an option to sort tracks by `Format` in `TrackSelectionView` and
        `TrackSelectionDialogBuilder`
        ([#7709](https://github.com/google/ExoPlayer/issues/7709)).
*   Audio:
    *   Fix the default audio sink position not advancing correctly when using
        `AudioTrack` based speed adjustment
        ([#7982](https://github.com/google/ExoPlayer/issues/7982)).
    *   Fix `NoClassDefFoundError` warning for `AudioTrack$StreamEventCallback`
        ([#8058](https://github.com/google/ExoPlayer/issues/8058)).
*   Extractors:
    *   MP4:
        *    Add support for `_mp2` boxes
            ([#7967](https://github.com/google/ExoPlayer/issues/7967)).
        *   Fix playback of files containing `pcm_alaw` or `pcm_mulaw` audio
            tracks, by enabling sample rechunking for such tracks.
    *   MPEG-TS:
        *   Add `TsExtractor` parameter to configure the number of bytes in
            which to search for timestamps when seeking and determining stream
            duration ([#7988](https://github.com/google/ExoPlayer/issues/7988)).
        *   Ignore negative payload size in PES packets
            ([#8005](https://github.com/google/ExoPlayer/issues/8005)).
    *   MP3: Use TLEN ID3 tag to compute the stream duration
        ([#7949](https://github.com/google/ExoPlayer/issues/7949)).
    *   Ogg: Fix regression playing files with packets that span multiple pages
        ([#7992](https://github.com/google/ExoPlayer/issues/7992)).
    *   FLV: Make files seekable by using the key frame index
        ([#7378](https://github.com/google/ExoPlayer/issues/7378)).
*   Downloads: Fix issue retrying progressive downloads, which could also result
    in a crash in `DownloadManager.InternalHandler.onContentLengthChanged`
    ([#8078](https://github.com/google/ExoPlayer/issues/8078)).
*   HLS: Fix crash affecting chunkful preparation of master playlists that start
    with an I-FRAME only variant
    ([#8025](https://github.com/google/ExoPlayer/issues/8025)).
*   IMA extension:
    *   Fix position reporting after fetch errors
        ([#7956](https://github.com/google/ExoPlayer/issues/7956)).
    *   Allow apps to specify a `VideoAdPlayerCallback`
        ([#7944](https://github.com/google/ExoPlayer/issues/7944)).
    *   Accept ad tags via the `AdsMediaSource` constructor and deprecate
        passing them via the `ImaAdsLoader` constructor/builders. Passing the ad
        tag via media item playback properties continues to be supported. This
        is in preparation for supporting ads in playlists
        ([#3750](https://github.com/google/ExoPlayer/issues/3750)).
    *   Add a way to override ad media MIME types
        ([#7961](https://github.com/google/ExoPlayer/issues/7961)).
    *   Fix incorrect truncation of large cue point positions
        ([#8067](https://github.com/google/ExoPlayer/issues/8067)).
    *   Upgrade IMA SDK dependency to 3.20.1. This brings in a fix for
        companion ads rendering when targeting API 29
        ([#6432](https://github.com/google/ExoPlayer/issues/6432)).

### 2.12.0 (2020-09-11) ###

To learn more about what's new in 2.12, read the corresponding
[blog post](https://medium.com/google-exoplayer/exoplayer-2-12-whats-new-e43ef8ff72e7).

*   Core library:
    *   `Player`:
        *   Add a top level playlist API based on a new `MediaItem` class
            ([#6161](https://github.com/google/ExoPlayer/issues/6161)). The new
            methods for playlist manipulation are `setMediaItem(s)`,
            `addMediaItem(s)`, `moveMediaItem(s)`, `removeMediaItem(s)` and
            `clearMediaItems`. The playlist can be queried using
            `getMediaItemCount` and `getMediaItemAt`. This API should be used
            instead of `ConcatenatingMediaSource` in most cases. Learn more by
            reading
            [this blog post](https://medium.com/google-exoplayer/a-top-level-playlist-api-for-exoplayer-abe0a24edb55).
        *   Add `getCurrentMediaItem` for getting the currently playing item in
            the playlist.
        *   Add `EventListener.onMediaItemTransition` to report when playback
            transitions from one item to another in the playlist.
        *   Add `play` and `pause` convenience methods. They are equivalent to
            `setPlayWhenReady(true)` and `setPlayWhenReady(false)` respectively.
        *   Add `getCurrentLiveOffset` for getting the offset of the current
            playback position from the live edge of a live stream.
        *   Add `getTrackSelector` for getting the `TrackSelector` used by the
            player.
        *   Add `AudioComponent.setAudioSessionId` to set the audio session ID.
            This method is also available on `SimpleExoPlayer`.
        *   Remove `PlaybackParameters.skipSilence`, and replace it with
            `AudioComponent.setSkipSilenceEnabled`. This method is also
            available on `SimpleExoPlayer`. An
            `AudioListener.onSkipSilenceEnabledChanged` callback is also
            added.
        *   Add `TextComponent.getCurrentCues` to get the current cues. This
            method is also available on `SimpleExoPlayer`. The current cues are
            no longer automatically forwarded to a `TextOutput` when it's added
            to a `SimpleExoPlayer`.
        *   Add `Player.DeviceComponent` to query and control the device volume.
            `SimpleExoPlayer` implements this interface.
        *   Deprecate and rename `getPlaybackError` to `getPlayerError` for
            consistency.
        *   Deprecate and rename `onLoadingChanged` to `onIsLoadingChanged` for
            consistency.
        *   Deprecate `EventListener.onPlayerStateChanged`, replacing it with
            `EventListener.onPlayWhenReadyChanged` and
            `EventListener.onPlaybackStateChanged`.
        *   Deprecate `EventListener.onSeekProcessed` because seek changes now
            happen instantly and listening to `onPositionDiscontinuity` is
            sufficient.
    *   `ExoPlayer`:
        *   Add `setMediaSource(s)` and `addMediaSource(s)` to `ExoPlayer`, for
            adding `MediaSource` instances directly to the playlist.
        *   Add `ExoPlayer.setPauseAtEndOfMediaItems` to let the player pause at
            the end of each media item
            ([#5660](https://github.com/google/ExoPlayer/issues/5660)).
        *   Allow passing `C.TIME_END_OF_SOURCE` to `PlayerMessage.setPosition`
            to send a `PlayerMessage` at the end of a stream.
    *   `SimpleExoPlayer`:
        *   `SimpleExoPlayer` implements the new `MediaItem` based playlist API,
            using a `MediaSourceFactory` to convert `MediaItem` instances to
            playable `MediaSource` instances. A `DefaultMediaSourceFactory` is
            used by default. `Builder.setMediaSourceFactory` allows setting a
            custom factory.
        *   Update [APK shrinking guide](https://exoplayer.dev/shrinking.html)
            to explain how shrinking works with the new `MediaItem` and
            `DefaultMediaSourceFactory` implementations
            ([#7937](https://github.com/google/ExoPlayer/issues/7937)).
        *   Add additional options to `Builder` that were previously only
            accessible via setters.
        *   Add opt-in to verify correct thread usage with
            `setThrowsWhenUsingWrongThread(true)`
            ([#4463](https://github.com/google/ExoPlayer/issues/4463)).
    *   `Format`:
        *   Add a `Builder` and deprecate all `create` methods and most
            `Format.copyWith` methods.
        *   Split `bitrate` into `averageBitrate` and `peakBitrate`
            ([#2863](https://github.com/google/ExoPlayer/issues/2863)).
    *   `LoadControl`:
        *   Add a `playbackPositionUs` parameter to `shouldContinueLoading`.
        *   Set the default minimum buffer duration in `DefaultLoadControl` to
            50 seconds (equal to the default maximum buffer), and treat audio
            and video the same.
    *   Add a `MetadataRetriever` API for retrieving track information and
        static metadata for a media item
        ([#3609](https://github.com/google/ExoPlayer/issues/3609)).
    *   Attach an identifier and extra information to load error events passed
        to `LoadErrorHandlingPolicy`
        ([#7309](https://github.com/google/ExoPlayer/issues/7309)).
        `LoadErrorHandlingPolicy` implementations should migrate to implementing
        the non-deprecated methods of the interface.
    *   Add an option to `MergingMediaSource` to adjust the time offsets between
        the merged sources
        ([#6103](https://github.com/google/ExoPlayer/issues/6103)).
    *   Move `MediaSourceEventListener.LoadEventInfo` and
        `MediaSourceEventListener.MediaLoadData` to be top-level classes in
        `com.google.android.exoplayer2.source`.
    *   Move `SimpleDecoderVideoRenderer` and `SimpleDecoderAudioRenderer` to
        `DecoderVideoRenderer` and `DecoderAudioRenderer` respectively, and
        generalize them to work with `Decoder` rather than `SimpleDecoder`.
    *   Deprecate `C.MSG_*` constants, replacing them with constants in
        `Renderer`.
    *   Split the `library-core` module into `library-core`, `library-common`
        and `library-extractor`. The `library-core` module has an API dependency
        on both of the new modules, so this change should be transparent to
        developers including ExoPlayer using Gradle dependencies.
    *   Add a dependency on Guava.
*   Video:
    *   Pass frame rate hint to `Surface.setFrameRate` on Android 11.
    *   Fix incorrect aspect ratio when transitioning from one video to another
        with the same resolution, but a different pixel aspect ratio
        ([#6646](https://github.com/google/ExoPlayer/issues/6646)).
*   Audio:
    *   Add experimental support for power efficient playback using audio
        offload.
    *   Add support for using framework audio speed adjustment instead of
        ExoPlayer's implementation
        ([#7502](https://github.com/google/ExoPlayer/issues/7502)). This option
        can be set using
        `DefaultRenderersFactory.setEnableAudioTrackPlaybackParams`.
    *   Add an event for the audio position starting to advance, to make it
        easier for apps to determine when audio playout started
        ([#7577](https://github.com/google/ExoPlayer/issues/7577)).
    *   Generalize support for floating point audio.
        *   Add an option to `DefaultAudioSink` for enabling floating point
            output. This option can also be set using
            `DefaultRenderersFactory.setEnableAudioFloatOutput`.
        *   Add floating point output capability to `MediaCodecAudioRenderer`
            and `LibopusAudioRenderer`, which is enabled automatically if the
            audio sink supports floating point output and if it makes sense for
            the content being played.
        *   Enable the floating point output capability of `FfmpegAudioRenderer`
            automatically if the audio sink supports floating point output and
            if it makes sense for the content being played. The option to
            manually enable floating point output has been removed, since this
            now done with the generalized option on `DefaultAudioSink`.
    *   In `MediaCodecAudioRenderer`, stop passing audio samples through
        `MediaCodec` when playing PCM audio or encoded audio using passthrough
        mode.
    *   Reuse audio decoders when transitioning through playlists of gapless
        audio, rather than reinstantiating them.
    *   Check `DefaultAudioSink` supports passthrough, in addition to checking
        the `AudioCapabilities`
        ([#7404](https://github.com/google/ExoPlayer/issues/7404)).
*   Text:
    *   Many of the changes described below improve support for Japanese
        subtitles. Read
        [this blog post](https://medium.com/google-exoplayer/improved-japanese-subtitle-support-7598fee12cf4)
        to learn more.
    *   Add a WebView-based output option to `SubtitleView`. This can display
        some features not supported by the existing Canvas-based output such as
        vertical text and rubies. It can be enabled by calling
        `SubtitleView#setViewType(VIEW_TYPE_WEB)`.
    *   Recreate the decoder when handling and swallowing decode errors in
        `TextRenderer`. This fixes a case where playback would never end when
        playing content with malformed subtitles
        ([#7590](https://github.com/google/ExoPlayer/issues/7590)).
    *   Only apply `CaptionManager` font scaling in
        `SubtitleView.setUserDefaultTextSize` if the `CaptionManager` is
        enabled.
    *   Improve positioning of vertical cues when rendered horizontally.
    *   Redefine `Cue.lineType=LINE_TYPE_NUMBER` in terms of aligning the cue
        text lines to grid of viewport lines. Only consider `Cue.lineAnchor`
        when `Cue.lineType=LINE_TYPE_FRACTION`.
    *   WebVTT:
        *   Add support for default
            [text](https://www.w3.org/TR/webvtt1/#default-text-color) and
            [background](https://www.w3.org/TR/webvtt1/#default-text-background)
            colors ([#6581](https://github.com/google/ExoPlayer/issues/6581)).
        *   Update position alignment parsing to recognise `line-left`, `center`
            and `line-right`.
        *   Implement steps 4-10 of the
            [WebVTT line computation algorithm](https://www.w3.org/TR/webvtt1/#cue-computed-line).
        *   Stop parsing unsupported CSS properties. The spec provides an
            [exhaustive list](https://www.w3.org/TR/webvtt1/#the-cue-pseudo-element)
            of which properties are supported.
        *   Parse the `ruby-position` CSS property.
        *   Parse the `text-combine-upright` CSS property (i.e., tate-chu-yoko).
        *   Parse the `<ruby>` and `<rt>` tags.
    *   TTML:
        *   Parse the `tts:combineText` property (i.e., tate-chu-yoko).
        *   Parse t`tts:ruby` and `tts:rubyPosition` properties.
    *   CEA-608:
        *   Implement timing-out of stuck captions, as permitted by
            ANSI/CTA-608-E R-2014 Annex C.9. The default timeout is set to 16
            seconds ([#7181](https://github.com/google/ExoPlayer/issues/7181)).
        *   Trim lines that exceed the maximum length of 32 characters
            ([#7341](https://github.com/google/ExoPlayer/issues/7341)).
        *   Fix positioning of roll-up captions in the top half of the screen
            ([#7475](https://github.com/google/ExoPlayer/issues/7475)).
        *   Stop automatically generating a CEA-608 track when playing
            standalone MPEG-TS files. The previous behavior can still be
            obtained by manually injecting a customized
            `DefaultTsPayloadReaderFactory` into `TsExtractor`.
*   Metadata: Add minimal DVB Application Information Table (AIT) support.
*   DASH:
    *   Add support for canceling in-progress segment fetches
        ([#2848](https://github.com/google/ExoPlayer/issues/2848)).
    *   Add support for CEA-708 embedded in FMP4.
*   SmoothStreaming:
    *   Add support for canceling in-progress segment fetches
        ([#2848](https://github.com/google/ExoPlayer/issues/2848)).
*   HLS:
    *   Add support for discarding buffered media (e.g., to allow faster
        adaptation to a higher quality variant)
        ([#6322](https://github.com/google/ExoPlayer/issues/6322)).
    *   Add support for canceling in-progress segment fetches
        ([#2848](https://github.com/google/ExoPlayer/issues/2848)).
    *   Respect 33-bit PTS wrapping when applying `X-TIMESTAMP-MAP` to WebVTT
        timestamps ([#7464](https://github.com/google/ExoPlayer/issues/7464)).
*   Extractors:
    *   Optimize the `Extractor` sniffing order to reduce start-up latency in
        `DefaultExtractorsFactory` and `DefaultHlsExtractorsFactory`
        ([#6410](https://github.com/google/ExoPlayer/issues/6410)).
    *   Use filename extensions and response header MIME types to further
        optimize `Extractor` sniffing order on a per-media basis.
    *   MP3: Add `IndexSeeker` for accurate seeks in VBR MP3 streams
        ([#6787](https://github.com/google/ExoPlayer/issues/6787)). This seeker
        can be enabled by passing `FLAG_ENABLE_INDEX_SEEKING` to the
        `Mp3Extractor`. A significant portion of the file may need to be scanned
        when a seek is performed, which may be costly for large files.
    *   MP4: Fix playback of MP4 streams that contain Opus audio.
    *   FMP4: Add support for partially fragmented MP4s
        ([#7308](https://github.com/google/ExoPlayer/issues/7308)).
    *   Matroska:
        *   Support Dolby Vision
            ([#7267](https://github.com/google/ExoPlayer/issues/7267)).
        *   Populate `Format.label` with track titles.
        *   Remove support for the `Invisible` block header flag.
    *   MPEG-TS: Add support for MPEG-4 Part 2 and H.263
        ([#1603](https://github.com/google/ExoPlayer/issues/1603),
        [#5107](https://github.com/google/ExoPlayer/issues/5107)).
    *   Ogg: Fix handling of non-contiguous pages
        ([#7230](https://github.com/google/ExoPlayer/issues/7230)).
*   UI:
    *   Add `StyledPlayerView` and `StyledPlayerControlView`, which provide a
        more polished user experience than `PlayerView` and `PlayerControlView`
        at the cost of decreased customizability.
    *   Remove the previously deprecated `SimpleExoPlayerView` and
        `PlaybackControlView` classes, along with the corresponding
        `exo_simple_player_view.xml` and `exo_playback_control_view.xml` layout
        resources. Use the equivalent `PlayerView`, `PlayerControlView`,
        `exo_player_view.xml` and `exo_player_control_view.xml` instead.
    *   Add setter methods to `PlayerView` and `PlayerControlView` to set
        whether the rewind, fast forward, previous and next buttons are shown
        ([#7410](https://github.com/google/ExoPlayer/issues/7410)).
    *   Update `TrackSelectionDialogBuilder` to use the AndroidX app compat
        `AlertDialog` rather than the platform version, if available
        ([#7357](https://github.com/google/ExoPlayer/issues/7357)).
    *   Make UI components dispatch previous, next, fast forward and rewind
        actions via their `ControlDispatcher`
        ([#6926](https://github.com/google/ExoPlayer/issues/6926)).
*   Downloads and caching:
    *   Add `DownloadRequest.Builder`.
    *   Add `DownloadRequest.keySetId` to make it easier to store an offline
        license keyset identifier alongside the other information that's
        persisted in `DownloadIndex`.
    *   Support passing an `Executor` to `DefaultDownloaderFactory` on which
        data downloads are performed.
    *   Parallelize and merge downloads in `SegmentDownloader` to improve
        download speeds
        ([#5978](https://github.com/google/ExoPlayer/issues/5978)).
    *   Replace `CacheDataSinkFactory` and `CacheDataSourceFactory` with
        `CacheDataSink.Factory` and `CacheDataSource.Factory` respectively.
    *   Remove `DownloadConstructorHelper` and instead use
        `CacheDataSource.Factory` directly.
    *   Add `Requirements.DEVICE_STORAGE_NOT_LOW`, which can be specified as a
        requirement to a `DownloadManager` for it to proceed with downloading.
    *   For failed downloads, propagate the `Exception` that caused the failure
        to `DownloadManager.Listener.onDownloadChanged`.
    *   Support multiple non-overlapping write locks for the same key in
        `SimpleCache`.
    *   Remove `CacheUtil`. Equivalent functionality is provided by a new
        `CacheWriter` class, `Cache.getCachedBytes`, `Cache.removeResource` and
        `CacheKeyFactory.DEFAULT`.
*   DRM:
    *   Remove previously deprecated APIs to inject `DrmSessionManager` into
        `Renderer` instances. `DrmSessionManager` must now be injected into
        `MediaSource` instances via the `MediaSource` factories.
    *   Add the ability to inject a custom `DefaultDrmSessionManager` into
        `OfflineLicenseHelper`
        ([#7078](https://github.com/google/ExoPlayer/issues/7078)).
    *   Keep DRM sessions alive for a short time before fully releasing them
        ([#7011](https://github.com/google/ExoPlayer/issues/7011),
        [#6725](https://github.com/google/ExoPlayer/issues/6725),
        [#7066](https://github.com/google/ExoPlayer/issues/7066)).
    *   Remove support for `cbc1` and `cens` encrytion schemes. Support for
        these schemes was removed from the Android platform from API level 30,
        and the range of API levels for which they are supported is too small to
        be useful.
    *   Remove generic types from DRM components.
    *   Rename `DefaultDrmSessionEventListener` to `DrmSessionEventListener`.
*   Track selection:
    *   Add `TrackSelection.shouldCancelMediaChunkLoad` to check whether an
        ongoing load should be canceled
        ([#2848](https://github.com/google/ExoPlayer/issues/2848)).
    *   Add `DefaultTrackSelector` constraints for minimum video resolution,
        bitrate and frame rate
        ([#4511](https://github.com/google/ExoPlayer/issues/4511)).
    *   Remove previously deprecated `DefaultTrackSelector` members.
*   Data sources:
    *   Add `HttpDataSource.InvalidResponseCodeException#responseBody` field
        ([#6853](https://github.com/google/ExoPlayer/issues/6853)).
    *   Add `DataSpec.Builder` and deprecate most `DataSpec` constructors.
    *   Add `DataSpec.customData` to allow applications to pass custom data
        through `DataSource` chains.
    *   Deprecate `CacheDataSinkFactory` and `CacheDataSourceFactory`, which are
        replaced by `CacheDataSink.Factory` and `CacheDataSource.Factory`
        respectively.
*   Analytics:
    *   Extend `EventTime` with more details about the current player state
        ([#7332](https://github.com/google/ExoPlayer/issues/7332)).
    *   Add `AnalyticsListener.onVideoFrameProcessingOffset` to report how early
        or late video frames are processed relative to them needing to be
        presented. Video frame processing offset fields are also added to
        `DecoderCounters`.
    *   Fix incorrect `MediaPeriodId` for some renderer errors reported by
        `AnalyticsListener.onPlayerError`.
    *   Remove `onMediaPeriodCreated`, `onMediaPeriodReleased` and
        `onReadingStarted` from `AnalyticsListener`.
*   Test utils: Add `TestExoPlayer`, a utility class with APIs to create
    `SimpleExoPlayer` instances with fake components for testing.
*   Media2 extension: This is a new extension that makes it easy to use
    ExoPlayer together with AndroidX Media2. Read
    [this blog post](https://medium.com/google-exoplayer/the-media2-extension-for-exoplayer-d6b7d89b9063)
    to learn more.
*   Cast extension: Implement playlist API and deprecate the old queue
    manipulation API.
*   IMA extension:
    *   Migrate to new 'friendly obstruction' IMA SDK APIs, and allow apps to
        register a purpose and detail reason for overlay views via
        `AdsLoader.AdViewProvider`.
    *   Add support for audio-only ads display containers by returning `null`
        from `AdsLoader.AdViewProvider.getAdViewGroup`, and allow skipping
        audio-only ads via `ImaAdsLoader.skipAd`
        ([#7689](https://github.com/google/ExoPlayer/issues/7689)).
    *   Add `ImaAdsLoader.Builder.setCompanionAdSlots` so it's possible to set
        companion ad slots without accessing the `AdDisplayContainer`.
    *   Add missing notification of `VideoAdPlayerCallback.onLoaded`.
    *   Fix handling of incompatible VPAID ads
        ([#7832](https://github.com/google/ExoPlayer/issues/7832)).
    *   Fix handling of empty ads at non-integer cue points
        ([#7889](https://github.com/google/ExoPlayer/issues/7889)).
*   Demo app:
    *   Replace the `extensions` variant with `decoderExtensions` and update the
        demo app use the Cronet and IMA extensions by default.
    *   Expand the `exolist.json` schema, as well the structure of intents that
        can be used to launch `PlayerActivity`. See the
        [Demo application page](https://exoplayer.dev/demo-application.html#playing-your-own-content)
        for the latest versions. Changes include:
        *   Add `drm_session_for_clear_content` to allow attaching DRM sessions
            to clear audio and video tracks.
        *   Add `clip_start_position_ms` and `clip_end_position_ms` to allow
            clipped samples.
    *   Use `StyledPlayerControlView` rather than `PlayerView`.
    *   Remove support for media tunneling, random ABR and playback of spherical
        video. Developers wishing to experiment with these features can enable
        them by modifying the demo app source code.
    *   Add support for downloading DRM-protected content using offline Widevine
        licenses.

### 2.11.8 (2020-08-25) ###

*   Fix distorted playback of floating point audio when samples exceed the
    `[-1, 1]` nominal range.
*   MP4:
    *   Add support for `piff` and `isml` brands
        ([#7584](https://github.com/google/ExoPlayer/issues/7584)).
    *   Fix playback of very short MP4 files.
*   FMP4:
    *   Fix `saiz` and `senc` sample count checks, resolving a "length
        mismatch" `ParserException` when playing certain protected FMP4 streams
        ([#7592](https://github.com/google/ExoPlayer/issues/7592)).
    *   Fix handling of `traf` boxes containing multiple `sbgp` or `sgpd`
        boxes.
*   FLV: Ignore `SCRIPTDATA` segments with invalid name types, rather than
    failing playback ([#7675](https://github.com/google/ExoPlayer/issues/7675)).
*   Better infer the content type of `.ism` and `.isml` streaming URLs.
*   Workaround an issue on Broadcom based devices where playbacks would not
    transition to `STATE_ENDED` when using video tunneling mode
    ([#7647](https://github.com/google/ExoPlayer/issues/7647)).
*   IMA extension: Upgrade to IMA SDK 3.19.4, bringing in a fix for setting the
    media load timeout
    ([#7170](https://github.com/google/ExoPlayer/issues/7170)).
*   Demo app: Fix playback of ClearKey protected content on API level 26 and
    earlier ([#7735](https://github.com/google/ExoPlayer/issues/7735)).

### 2.11.7 (2020-06-29) ###

*   IMA extension: Fix the way postroll "content complete" notifications are
    handled to avoid repeatedly refreshing the timeline after playback ends.

### 2.11.6 (2020-06-19) ###

*   UI: Prevent `PlayerView` from temporarily hiding the video surface when
    seeking to an unprepared period within the current window. For example when
    seeking over an ad group, or to the next period in a multi-period DASH
    stream ([#5507](https://github.com/google/ExoPlayer/issues/5507)).
*   IMA extension:
    *   Add option to skip ads before the start position.
    *   Catch unexpected errors in `stopAd` to avoid a crash
        ([#7492](https://github.com/google/ExoPlayer/issues/7492)).
    *   Fix a bug that caused playback to be stuck buffering on resuming from
        the background after all ads had played to the end
        ([#7508](https://github.com/google/ExoPlayer/issues/7508)).
    *   Fix a bug where the number of ads in an ad group couldn't change
        ([#7477](https://github.com/google/ExoPlayer/issues/7477)).
    *   Work around unexpected `pauseAd`/`stopAd` for ads that have preloaded
        on seeking to another position
        ([#7492](https://github.com/google/ExoPlayer/issues/7492)).
    *   Fix incorrect rounding of ad cue points.
    *   Fix handling of postrolls preloading
        ([#7518](https://github.com/google/ExoPlayer/issues/7518)).

### 2.11.5 (2020-06-05) ###

*   Improve the smoothness of video playback immediately after starting, seeking
    or resuming a playback
    ([#6901](https://github.com/google/ExoPlayer/issues/6901)).
*   Add `SilenceMediaSource.Factory` to support tags.
*   Enable the configuration of `SilenceSkippingAudioProcessor`
    ([#6705](https://github.com/google/ExoPlayer/issues/6705)).
*   Fix bug where `PlayerMessages` throw an exception after `MediaSources`
    are removed from the playlist
    ([#7278](https://github.com/google/ExoPlayer/issues/7278)).
*   Fix "Not allowed to start service" `IllegalStateException` in
    `DownloadService`
    ([#7306](https://github.com/google/ExoPlayer/issues/7306)).
*   Fix issue in `AudioTrackPositionTracker` that could cause negative positions
    to be reported at the start of playback and immediately after seeking
    ([#7456](https://github.com/google/ExoPlayer/issues/7456)).
*   Fix further cases where downloads would sometimes not resume after their
    network requirements are met
    ([#7453](https://github.com/google/ExoPlayer/issues/7453)).
*   DASH:
    *   Merge trick play adaptation sets (i.e., adaptation sets marked with
        `http://dashif.org/guidelines/trickmode`) into the same `TrackGroup` as
        the main adaptation sets to which they refer. Trick play tracks are
        marked with the `C.ROLE_FLAG_TRICK_PLAY` flag.
    *   Fix assertion failure in `SampleQueue` when playing DASH streams with
        EMSG tracks ([#7273](https://github.com/google/ExoPlayer/issues/7273)).
*   MP4: Store the Android capture frame rate only in `Format.metadata`.
    `Format.frameRate` now stores the calculated frame rate.
*   FMP4: Avoid throwing an exception while parsing default sample values whose
    most significant bits are set
    ([#7207](https://github.com/google/ExoPlayer/issues/7207)).
*   MP3: Fix issue parsing the XING headers belonging to files larger than 2GB
    ([#7337](https://github.com/google/ExoPlayer/issues/7337)).
*   MPEG-TS: Fix issue where SEI NAL units were incorrectly dropped from H.265
    samples ([#7113](https://github.com/google/ExoPlayer/issues/7113)).
*   UI:
    *   Fix `DefaultTimeBar` to respect touch transformations
        ([#7303](https://github.com/google/ExoPlayer/issues/7303)).
    *   Add `showScrubber` and `hideScrubber` methods to `DefaultTimeBar`.
*   Text:
    *   Use anti-aliasing and bitmap filtering when displaying bitmap
        subtitles.
    *   Fix `SubtitlePainter` to render `EDGE_TYPE_OUTLINE` using the correct
        color.
*   IMA extension:
    *   Upgrade to IMA SDK version 3.19.0, and migrate to new
        preloading APIs
        ([#6429](https://github.com/google/ExoPlayer/issues/6429)). This fixes
        several issues involving preloading and handling of ad loading error
        cases: ([#4140](https://github.com/google/ExoPlayer/issues/4140),
        [#5006](https://github.com/google/ExoPlayer/issues/5006),
        [#6030](https://github.com/google/ExoPlayer/issues/6030),
        [#6097](https://github.com/google/ExoPlayer/issues/6097),
        [#6425](https://github.com/google/ExoPlayer/issues/6425),
        [#6967](https://github.com/google/ExoPlayer/issues/6967),
        [#7041](https://github.com/google/ExoPlayer/issues/7041),
        [#7161](https://github.com/google/ExoPlayer/issues/7161),
        [#7212](https://github.com/google/ExoPlayer/issues/7212),
        [#7340](https://github.com/google/ExoPlayer/issues/7340)).
    *   Add support for timing out ad preloading, to avoid playback getting
        stuck if an ad group unexpectedly fails to load
        ([#5444](https://github.com/google/ExoPlayer/issues/5444),
        [#5966](https://github.com/google/ExoPlayer/issues/5966),
        [#7002](https://github.com/google/ExoPlayer/issues/7002)).
    *   Fix `AdsMediaSource` child `MediaSource`s not being released.
*   Cronet extension: Default to using the Cronet implementation in Google Play
    Services rather than Cronet Embedded. This allows Cronet to be used with a
    negligible increase in application size, compared to approximately 8MB when
    embedding the library.
*   OkHttp extension: Upgrade OkHttp dependency to 3.12.11.
*   MediaSession extension:
    *   Only set the playback state to `BUFFERING` if `playWhenReady` is true
        ([#7206](https://github.com/google/ExoPlayer/issues/7206)).
    *   Add missing `@Nullable` annotations to `MediaSessionConnector`
        ([#7234](https://github.com/google/ExoPlayer/issues/7234)).
*   AV1 extension: Add a heuristic to determine the default number of threads
    used for AV1 playback using the extension.

### 2.11.4 (2020-04-08)

*   Add `SimpleExoPlayer.setWakeMode` to allow automatic `WifiLock` and
    `WakeLock` handling
    ([#6914](https://github.com/google/ExoPlayer/issues/6914)). To use this
    feature, you must add the
    [WAKE_LOCK](https://developer.android.com/reference/android/Manifest.permission.html#WAKE_LOCK)
    permission to your application's manifest file.
*   Text:
    *   Catch and log exceptions in `TextRenderer` rather than re-throwing. This
        allows playback to continue even if subtitle decoding fails
        ([#6885](https://github.com/google/ExoPlayer/issues/6885)).
    *   Allow missing hours and milliseconds in SubRip (.srt) timecodes
        ([#7122](https://github.com/google/ExoPlayer/issues/7122)).
*   Audio:
    *   Enable playback speed adjustment and silence skipping for floating point
        PCM audio, via resampling to 16-bit integer PCM. To output the original
        floating point audio without adjustment, pass `enableFloatOutput=true`
        to the `DefaultAudioSink` constructor
        ([#7134](https://github.com/google/ExoPlayer/issues/7134)).
    *   Workaround issue that could cause slower than realtime playback of AAC
        on Android 10
        ([#6671](https://github.com/google/ExoPlayer/issues/6671)).
    *   Fix case where another app spuriously holding transient audio focus
        could prevent ExoPlayer from acquiring audio focus for an indefinite
        period of time
        ([#7182](https://github.com/google/ExoPlayer/issues/7182)).
    *   Fix case where the player volume could be permanently ducked if audio
        focus was released whilst ducking.
    *   Fix playback of WAV files with trailing non-media bytes
        ([#7129](https://github.com/google/ExoPlayer/issues/7129)).
    *   Fix playback of ADTS files with mid-stream ID3 metadata.
*   DRM:
    *   Fix stuck ad playbacks with DRM protected content
        ([#7188](https://github.com/google/ExoPlayer/issues/7188)).
    *   Fix playback of Widevine protected content that only provides V1 PSSH
        atoms on API levels 21 and 22.
    *   Fix playback of PlayReady content on Fire TV Stick (Gen 2).
*   DASH:
    *   Update the manifest URI to avoid repeated HTTP redirects
        ([#6907](https://github.com/google/ExoPlayer/issues/6907)).
    *   Parse period `AssetIdentifier` elements.
*   HLS: Recognize IMSC subtitles
    ([#7185](https://github.com/google/ExoPlayer/issues/7185)).
*   UI: Add an option to set whether to use the orientation sensor for rotation
    in spherical playbacks
    ([#6761](https://github.com/google/ExoPlayer/issues/6761)).
*   Analytics: Fix `PlaybackStatsListener` behavior when not keeping history
    ([#7160](https://github.com/google/ExoPlayer/issues/7160)).
*   FFmpeg extension: Add support for `x86_64` architecture.
*   Opus extension: Fix parsing of negative gain values
    ([#7046](https://github.com/google/ExoPlayer/issues/7046)).
*   Cast extension: Upgrade `play-services-cast-framework` dependency to 18.1.0.
    This fixes an issue where `RemoteServiceException` was thrown due to
    `Context.startForegroundService()` not calling `Service.startForeground()`
    ([#7191](https://github.com/google/ExoPlayer/issues/7191)).

### 2.11.3 (2020-02-19)

*   SmoothStreaming: Fix regression that broke playback in 2.11.2
    ([#6981](https://github.com/google/ExoPlayer/issues/6981)).
*   DRM: Fix issue switching from protected content that uses a 16-byte
    initialization vector to one that uses an 8-byte initialization vector
    ([#6982](https://github.com/google/ExoPlayer/issues/6982)).

### 2.11.2 (2020-02-13)

*   Add Java FLAC extractor
    ([#6406](https://github.com/google/ExoPlayer/issues/6406)).
*   Startup latency optimization:
    *   Reduce startup latency for DASH and SmoothStreaming playbacks by
        allowing codec initialization to occur before the network connection for
        the first media segment has been established.
    *   Reduce startup latency for on-demand DASH playbacks by allowing codec
        initialization to occur before the sidx box has been loaded.
*   Downloads:
    *   Fix download resumption when the requirements for them to continue are
        met ([#6733](https://github.com/google/ExoPlayer/issues/6733),
        [#6798](https://github.com/google/ExoPlayer/issues/6798)).
    *   Fix `DownloadHelper.createMediaSource` to use `customCacheKey` when
        creating `ProgressiveMediaSource` instances.
*   DRM: Fix `NullPointerException` when playing DRM protected content
    ([#6951](https://github.com/google/ExoPlayer/issues/6951)).
*   Metadata:
    *   Update `IcyDecoder` to try ISO-8859-1 decoding if UTF-8 decoding fails.
        Also change `IcyInfo.rawMetadata` from `String` to `byte[]` to allow
        developers to handle data that's neither UTF-8 nor ISO-8859-1
        ([#6753](https://github.com/google/ExoPlayer/issues/6753)).
    *   Select multiple metadata tracks if multiple metadata renderers are
        available ([#6676](https://github.com/google/ExoPlayer/issues/6676)).
    *   Add support for ID3 genres added in Wimamp 5.6 (2010).
*   UI:
    *   Show ad group markers in `DefaultTimeBar` even if they are after the end
        of the current window
        ([#6552](https://github.com/google/ExoPlayer/issues/6552)).
    *   Don't use notification chronometer if playback speed is != 1.0
        ([#6816](https://github.com/google/ExoPlayer/issues/6816)).
*   HLS: Fix playback of DRM protected content that uses key rotation
    ([#6903](https://github.com/google/ExoPlayer/issues/6903)).
*   WAV:
    *   Support IMA ADPCM encoded data.
    *   Improve support for G.711 A-law and mu-law encoded data.
*   MP4: Support "twos" codec (big endian PCM)
    ([#5789](https://github.com/google/ExoPlayer/issues/5789)).
*   FMP4: Add support for encrypted AC-4 tracks.
*   HLS: Fix slow seeking into long MP3 segments
    ([#6155](https://github.com/google/ExoPlayer/issues/6155)).
*   Fix handling of E-AC-3 streams that contain AC-3 syncframes
    ([#6602](https://github.com/google/ExoPlayer/issues/6602)).
*   Fix playback of TrueHD streams in Matroska
    ([#6845](https://github.com/google/ExoPlayer/issues/6845)).
*   Fix MKV subtitles to disappear when intended instead of lasting until the
    next cue ([#6833](https://github.com/google/ExoPlayer/issues/6833)).
*   OkHttp extension: Upgrade OkHttp dependency to 3.12.8, which fixes a class
    of `SocketTimeoutException` issues when using HTTP/2
    ([#4078](https://github.com/google/ExoPlayer/issues/4078)).
*   FLAC extension: Fix handling of bit depths other than 16 in `FLACDecoder`.
    This issue caused FLAC streams with other bit depths to sound like white
    noise on earlier releases, but only when embedded in a non-FLAC container
    such as Matroska or MP4.
*   Demo apps: Add
    [GL demo app](https://github.com/google/ExoPlayer/tree/dev-v2/demos/gl) to
    show how to render video to a `GLSurfaceView` while applying a GL shader.
    ([#6920](https://github.com/google/ExoPlayer/issues/6920)).

### 2.11.1 (2019-12-20)

*   UI: Exclude `DefaultTimeBar` region from system gesture detection
    ([#6685](https://github.com/google/ExoPlayer/issues/6685)).
*   ProGuard fixes:
    *   Ensure `Libgav1VideoRenderer` constructor is kept for use by
        `DefaultRenderersFactory`
        ([#6773](https://github.com/google/ExoPlayer/issues/6773)).
    *   Ensure `VideoDecoderOutputBuffer` and its members are kept for use by
        video decoder extensions.
    *   Ensure raw resources used with `RawResourceDataSource` are kept.
    *   Suppress spurious warnings about the `javax.annotation` package, and
        restructure use of `IntDef` annotations to remove spurious warnings
        about `SsaStyle$SsaAlignment`
        ([#6771](https://github.com/google/ExoPlayer/issues/6771)).
*   Fix `CacheDataSource` to correctly propagate `DataSpec.httpRequestHeaders`.
*   Fix issue with `DefaultDownloadIndex` that could result in an
    `IllegalStateException` being thrown from
    `DefaultDownloadIndex.getDownloadForCurrentRow`
    ([#6785](https://github.com/google/ExoPlayer/issues/6785)).
*   Fix `IndexOutOfBoundsException` in `SinglePeriodTimeline.getWindow`
    ([#6776](https://github.com/google/ExoPlayer/issues/6776)).
*   Add missing `@Nullable` to `MediaCodecAudioRenderer.getMediaClock` and
    `SimpleDecoderAudioRenderer.getMediaClock`
    ([#6792](https://github.com/google/ExoPlayer/issues/6792)).

### 2.11.0 (2019-12-11)

*   Core library:
    *   Replace `ExoPlayerFactory` by `SimpleExoPlayer.Builder` and
        `ExoPlayer.Builder`.
    *   Add automatic `WakeLock` handling to `SimpleExoPlayer`, which can be
        enabled by calling `SimpleExoPlayer.setHandleWakeLock`
        ([#5846](https://github.com/google/ExoPlayer/issues/5846)). To use this
        feature, you must add the
        [WAKE_LOCK](https://developer.android.com/reference/android/Manifest.permission.html#WAKE_LOCK)
        permission to your application's manifest file.
    *   Add automatic "audio becoming noisy" handling to `SimpleExoPlayer`,
        which can be enabled by calling
        `SimpleExoPlayer.setHandleAudioBecomingNoisy`.
    *   Wrap decoder exceptions in a new `DecoderException` class and report
        them as renderer errors.
    *   Add `Timeline.Window.isLive` to indicate that a window is a live stream
        ([#2668](https://github.com/google/ExoPlayer/issues/2668) and
        [#5973](https://github.com/google/ExoPlayer/issues/5973)).
    *   Add `Timeline.Window.uid` to uniquely identify window instances.
    *   Deprecate `setTag` parameter of `Timeline.getWindow`. Tags will always
        be set.
    *   Deprecate passing the manifest directly to
        `Player.EventListener.onTimelineChanged`. It can be accessed through
        `Timeline.Window.manifest` or `Player.getCurrentManifest()`
    *   Add `MediaSource.enable` and `MediaSource.disable` to improve resource
        management in playlists.
    *   Add `MediaPeriod.isLoading` to improve `Player.isLoading` state.
    *   Fix issue where player errors are thrown too early at playlist
        transitions ([#5407](https://github.com/google/ExoPlayer/issues/5407)).
    *   Add `Format` and renderer support flags to renderer
        `ExoPlaybackException`s.
    *   Where there are multiple platform decoders for a given MIME type, prefer
        to use one that advertises support for the profile and level of the
        media being played over one that does not, even if it does not come
        first in the `MediaCodecList`.
*   DRM:
    *   Inject `DrmSessionManager` into the `MediaSources` instead of
        `Renderers`. This allows each `MediaSource` in a
        `ConcatenatingMediaSource` to use a different `DrmSessionManager`
        ([#5619](https://github.com/google/ExoPlayer/issues/5619)).
    *   Add `DefaultDrmSessionManager.Builder`, and remove
        `DefaultDrmSessionManager` static factory methods that leaked
        `ExoMediaDrm` instances
        ([#4721](https://github.com/google/ExoPlayer/issues/4721)).
    *   Add support for the use of secure decoders when playing clear content
        ([#4867](https://github.com/google/ExoPlayer/issues/4867)). This can be
        enabled using `DefaultDrmSessionManager.Builder`'s
        `setUseDrmSessionsForClearContent` method.
    *   Add support for custom `LoadErrorHandlingPolicies` in key and
        provisioning requests
        ([#6334](https://github.com/google/ExoPlayer/issues/6334)). Custom
        policies can be passed via `DefaultDrmSessionManager.Builder`'s
        `setLoadErrorHandlingPolicy` method.
    *   Use `ExoMediaDrm.Provider` in `OfflineLicenseHelper` to avoid leaking
        `ExoMediaDrm` instances
        ([#4721](https://github.com/google/ExoPlayer/issues/4721)).
*   Track selection:
    *   Update `DefaultTrackSelector` to set a viewport constraint for the
        default display by default.
    *   Update `DefaultTrackSelector` to set text language and role flag
        constraints for the device's accessibility settings by default
        ([#5749](https://github.com/google/ExoPlayer/issues/5749)).
    *   Add option to set preferred text role flags using
        `DefaultTrackSelector.ParametersBuilder.setPreferredTextRoleFlags`.
*   LoadControl:
    *   Default `prioritizeTimeOverSizeThresholds` to false to prevent OOM
        errors ([#6647](https://github.com/google/ExoPlayer/issues/6647)).
*   Android 10:
    *   Set `compileSdkVersion` to 29 to enable use of Android 10 APIs.
    *   Expose new `isHardwareAccelerated`, `isSoftwareOnly` and `isVendor`
        flags in `MediaCodecInfo`
        ([#5839](https://github.com/google/ExoPlayer/issues/5839)).
    *   Add `allowedCapturePolicy` field to `AudioAttributes` to allow to
        configuration of the audio capture policy.
*   Video:
    *   Pass the codec output `MediaFormat` to `VideoFrameMetadataListener`.
    *   Fix byte order of HDR10+ static metadata to match CTA-861.3.
    *   Support out-of-band HDR10+ dynamic metadata for VP9 in WebM/Matroska.
    *   Assume that protected content requires a secure decoder when evaluating
        whether `MediaCodecVideoRenderer` supports a given video format
        ([#5568](https://github.com/google/ExoPlayer/issues/5568)).
    *   Fix Dolby Vision fallback to AVC and HEVC.
    *   Fix early end-of-stream detection when using video tunneling, on API
        level 23 and above.
    *   Fix an issue where a keyframe was rendered rather than skipped when
        performing an exact seek to a non-zero position close to the start of
        the stream.
*   Audio:
    *   Fix the start of audio getting truncated when transitioning to a new
        item in a playlist of Opus streams.
    *   Workaround broken raw audio decoding on Oppo R9
        ([#5782](https://github.com/google/ExoPlayer/issues/5782)).
    *   Reconfigure audio sink when PCM encoding changes
        ([#6601](https://github.com/google/ExoPlayer/issues/6601)).
    *   Allow `AdtsExtractor` to encounter EOF when calculating average frame
        size ([#6700](https://github.com/google/ExoPlayer/issues/6700)).
*   Text:
    *   Add support for position and overlapping start/end times in SSA/ASS
        subtitles ([#6320](https://github.com/google/ExoPlayer/issues/6320)).
    *   Require an end time or duration for SubRip (SRT) and SubStation Alpha
        (SSA/ASS) subtitles. This applies to both sidecar files & subtitles
        [embedded in Matroska streams](https://matroska.org/technical/specs/subtitles/index.html).
*   UI:
    *   Make showing and hiding player controls accessible to TalkBack in
        `PlayerView`.
    *   Rename `spherical_view` surface type to `spherical_gl_surface_view`.
    *   Make it easier to override the shuffle, repeat, fullscreen, VR and small
        notification icon assets
        ([#6709](https://github.com/google/ExoPlayer/issues/6709)).
*   Analytics:
    *   Remove `AnalyticsCollector.Factory`. Instances should be created
        directly, and the `Player` should be set by calling
        `AnalyticsCollector.setPlayer`.
    *   Add `PlaybackStatsListener` to collect `PlaybackStats` for analysis and
        analytics reporting.
*   DataSource
    *   Add `DataSpec.httpRequestHeaders` to support setting per-request headers
        for HTTP and HTTPS.
    *   Remove the `DataSpec.FLAG_ALLOW_ICY_METADATA` flag. Use is replaced by
        setting the `IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_NAME` header in
        `DataSpec.httpRequestHeaders`.
    *   Fail more explicitly when local file URIs contain invalid parts (e.g. a
        fragment) ([#6470](https://github.com/google/ExoPlayer/issues/6470)).
*   DASH: Support negative @r values in segment timelines
    ([#1787](https://github.com/google/ExoPlayer/issues/1787)).
*   HLS:
    *   Use peak bitrate rather than average bitrate for adaptive track
        selection.
    *   Fix issue where streams could get stuck in an infinite buffering state
        after a postroll ad
        ([#6314](https://github.com/google/ExoPlayer/issues/6314)).
*   Matroska: Support lacing in Blocks
    ([#3026](https://github.com/google/ExoPlayer/issues/3026)).
*   AV1 extension:
    *   New in this release. The AV1 extension allows use of the
        [libgav1 software decoder](https://chromium.googlesource.com/codecs/libgav1/)
        in ExoPlayer. You can read more about playing AV1 videos with ExoPlayer
        [here](https://medium.com/google-exoplayer/playing-av1-videos-with-exoplayer-a7cb19bedef9).
*   VP9 extension:
    *   Update to use NDK r20.
    *   Rename `VpxVideoSurfaceView` to `VideoDecoderSurfaceView` and move it to
        the core library.
    *   Move `LibvpxVideoRenderer.MSG_SET_OUTPUT_BUFFER_RENDERER` to
        `C.MSG_SET_OUTPUT_BUFFER_RENDERER`.
    *   Use `VideoDecoderRenderer` as an implementation of
        `VideoDecoderOutputBufferRenderer`, instead of
        `VideoDecoderSurfaceView`.
*   FLAC extension: Update to use NDK r20.
*   Opus extension: Update to use NDK r20.
*   FFmpeg extension:
    *   Update to use NDK r20.
    *   Update to use FFmpeg version 4.2. It is necessary to rebuild the native
        part of the extension after this change, following the instructions in
        the extension's readme.
*   MediaSession extension: Add `MediaSessionConnector.setCaptionCallback` to
    support `ACTION_SET_CAPTIONING_ENABLED` events.
*   GVR extension: This extension is now deprecated.
*   Demo apps:
    *   Add
        [SurfaceControl demo app](https://github.com/google/ExoPlayer/tree/r2.11.0/demos/surface)
        to show how to use the Android 10 `SurfaceControl` API with ExoPlayer
        ([#677](https://github.com/google/ExoPlayer/issues/677)).
    *   Add support for subtitle files to the
        [Main demo app](https://github.com/google/ExoPlayer/tree/r2.11.0/demos/main)
        ([#5523](https://github.com/google/ExoPlayer/issues/5523)).
    *   Remove the IMA demo app. IMA functionality is demonstrated by the
        [main demo app](https://github.com/google/ExoPlayer/tree/r2.11.0/demos/main).
    *   Add basic DRM support to the
        [Cast demo app](https://github.com/google/ExoPlayer/tree/r2.11.0/demos/cast).
*   TestUtils: Publish the `testutils` module to simplify unit testing with
    ExoPlayer ([#6267](https://github.com/google/ExoPlayer/issues/6267)).
*   IMA extension: Remove `AdsManager` listeners on release to avoid leaking an
    `AdEventListener` provided by the app
    ([#6687](https://github.com/google/ExoPlayer/issues/6687)).

### 2.10.8 (2019-11-19)

*   E-AC3 JOC
    *   Handle new signaling in DASH manifests
        ([#6636](https://github.com/google/ExoPlayer/issues/6636)).
    *   Fix E-AC3 JOC passthrough playback failing to initialize due to
        incorrect channel count check.
*   FLAC
    *   Fix sniffing for some FLAC streams.
    *   Fix FLAC `Format.bitrate` values.
*   Parse ALAC channel count and sample rate information from a more robust
    source when contained in MP4
    ([#6648](https://github.com/google/ExoPlayer/issues/6648)).
*   Fix seeking into multi-period content in the edge case that the period
    containing the seek position has just been removed
    ([#6641](https://github.com/google/ExoPlayer/issues/6641)).

### 2.10.7 (2019-11-06)

*   HLS: Fix detection of Dolby Atmos to match the HLS authoring specification.
*   MediaSession extension: Update shuffle and repeat modes when playback state
    is invalidated ([#6582](https://github.com/google/ExoPlayer/issues/6582)).
*   Fix the start of audio getting truncated when transitioning to a new item in
    a playlist of Opus streams.

### 2.10.6 (2019-10-17)

*   Add `Player.onPlaybackSuppressionReasonChanged` to allow listeners to detect
    playbacks suppressions (e.g. transient audio focus loss) directly
    ([#6203](https://github.com/google/ExoPlayer/issues/6203)).
*   DASH:
    *   Support `Label` elements
        ([#6297](https://github.com/google/ExoPlayer/issues/6297)).
    *   Support legacy audio channel configuration
        ([#6523](https://github.com/google/ExoPlayer/issues/6523)).
*   HLS: Add support for ID3 in EMSG when using FMP4 streams
    ([spec](https://aomediacodec.github.io/av1-id3/)).
*   MP3: Add workaround to avoid prematurely ending playback of some SHOUTcast
    live streams ([#6537](https://github.com/google/ExoPlayer/issues/6537),
    [#6315](https://github.com/google/ExoPlayer/issues/6315) and
    [#5658](https://github.com/google/ExoPlayer/issues/5658)).
*   Metadata: Expose the raw ICY metadata through `IcyInfo`
    ([#6476](https://github.com/google/ExoPlayer/issues/6476)).
*   UI:
    *   Setting `app:played_color` on `PlayerView` and `PlayerControlView` no
        longer adjusts the colors of the scrubber handle , buffered and unplayed
        parts of the time bar. These can be set separately using
        `app:scrubber_color`, `app:buffered_color` and `app_unplayed_color`
        respectively.
    *   Setting `app:ad_marker_color` on `PlayerView` and `PlayerControlView` no
        longer adjusts the color of played ad markers. The color of played ad
        markers can be set separately using `app:played_ad_marker_color`.

### 2.10.5 (2019-09-20)

*   Add `Player.isPlaying` and `EventListener.onIsPlayingChanged` to check
    whether the playback position is advancing. This helps to determine if
    playback is suppressed due to audio focus loss. Also add
    `Player.getPlaybackSuppressedReason` to determine the reason of the
    suppression ([#6203](https://github.com/google/ExoPlayer/issues/6203)).
*   Track selection
    *   Add `allowAudioMixedChannelCountAdaptiveness` parameter to
        `DefaultTrackSelector` to allow adaptive selections of audio tracks with
        different channel counts.
    *   Improve text selection logic to always prefer the better language
        matches over other selection parameters.
    *   Fix audio selection issue where languages are compared by bitrate
        ([#6335](https://github.com/google/ExoPlayer/issues/6335)).
*   Performance
    *   Increase maximum video buffer size from 13MB to 32MB. The previous
        default was too small for high quality streams.
    *   Reset `DefaultBandwidthMeter` to initial values on network change.
    *   Bypass sniffing in `ProgressiveMediaPeriod` in case a single extractor
        is provided ([#6325](https://github.com/google/ExoPlayer/issues/6325)).
*   Metadata
    *   Support EMSG V1 boxes in FMP4.
    *   Support unwrapping of nested metadata (e.g. ID3 and SCTE-35 in EMSG).
*   Add `HttpDataSource.getResponseCode` to provide the status code associated
    with the most recent HTTP response.
*   Fix transitions between packed audio and non-packed audio segments in HLS
    ([#6444](https://github.com/google/ExoPlayer/issues/6444)).
*   Fix issue where a request would be retried after encountering an error, even
    though the `LoadErrorHandlingPolicy` classified the error as fatal.
*   Fix initialization data handling for FLAC in MP4
    ([#6396](https://github.com/google/ExoPlayer/issues/6396),
    [#6397](https://github.com/google/ExoPlayer/issues/6397)).
*   Fix decoder selection for E-AC3 JOC streams
    ([#6398](https://github.com/google/ExoPlayer/issues/6398)).
*   Fix `PlayerNotificationManager` to show play icon rather than pause icon
    when playback is ended
    ([#6324](https://github.com/google/ExoPlayer/issues/6324)).
*   RTMP extension: Upgrade LibRtmp-Client-for-Android to fix RTMP playback
    issues ([#4200](https://github.com/google/ExoPlayer/issues/4200),
    [#4249](https://github.com/google/ExoPlayer/issues/4249),
    [#4319](https://github.com/google/ExoPlayer/issues/4319),
    [#4337](https://github.com/google/ExoPlayer/issues/4337)).
*   IMA extension: Fix crash in `ImaAdsLoader.onTimelineChanged`
    ([#5831](https://github.com/google/ExoPlayer/issues/5831)).

### 2.10.4 (2019-07-26)

*   Offline: Add `Scheduler` implementation that uses `WorkManager`.
*   Add ability to specify a description when creating notification channels via
    ExoPlayer library classes.
*   Switch normalized BCP-47 language codes to use 2-letter ISO 639-1 language
    tags instead of 3-letter ISO 639-2 language tags.
*   Ensure the `SilenceMediaSource` position is in range
    ([#6229](https://github.com/google/ExoPlayer/issues/6229)).
*   WAV: Calculate correct duration for clipped streams
    ([#6241](https://github.com/google/ExoPlayer/issues/6241)).
*   MP3: Use CBR header bitrate, not calculated bitrate. This reverts a change
    from 2.9.3 ([#6238](https://github.com/google/ExoPlayer/issues/6238)).
*   FLAC extension: Parse `VORBIS_COMMENT` and `PICTURE` metadata
    ([#5527](https://github.com/google/ExoPlayer/issues/5527)).
*   Fix issue where initial seek positions get ignored when playing a preroll ad
    ([#6201](https://github.com/google/ExoPlayer/issues/6201)).
*   Fix issue where invalid language tags were normalized to "und" instead of
    keeping the original
    ([#6153](https://github.com/google/ExoPlayer/issues/6153)).
*   Fix `DataSchemeDataSource` re-opening and range requests
    ([#6192](https://github.com/google/ExoPlayer/issues/6192)).
*   Fix FLAC and ALAC playback on some LG devices
    ([#5938](https://github.com/google/ExoPlayer/issues/5938)).
*   Fix issue when calling `performClick` on `PlayerView` without
    `PlayerControlView`
    ([#6260](https://github.com/google/ExoPlayer/issues/6260)).
*   Fix issue where playback speeds are not used in adaptive track selections
    after manual selection changes for other renderers
    ([#6256](https://github.com/google/ExoPlayer/issues/6256)).

### 2.10.3 (2019-07-09)

*   Display last frame when seeking to end of stream
    ([#2568](https://github.com/google/ExoPlayer/issues/2568)).
*   Audio:
    *   Fix an issue where not all audio was played out when the configuration
        for the underlying track was changing (e.g., at some period
        transitions).
    *   Fix an issue where playback speed was applied inaccurately in playlists
        ([#6117](https://github.com/google/ExoPlayer/issues/6117)).
*   UI: Fix `PlayerView` incorrectly consuming touch events if no controller is
    attached ([#6109](https://github.com/google/ExoPlayer/issues/6109)).
*   CEA608: Fix repetition of special North American characters
    ([#6133](https://github.com/google/ExoPlayer/issues/6133)).
*   FLV: Fix bug that caused playback of some live streams to not start
    ([#6111](https://github.com/google/ExoPlayer/issues/6111)).
*   SmoothStreaming: Parse text stream `Subtype` into `Format.roleFlags`.
*   MediaSession extension: Fix `MediaSessionConnector.play()` not resuming
    playback ([#6093](https://github.com/google/ExoPlayer/issues/6093)).

### 2.10.2 (2019-06-03)

*   Add `ResolvingDataSource` for just-in-time resolution of `DataSpec`s
    ([#5779](https://github.com/google/ExoPlayer/issues/5779)).
*   Add `SilenceMediaSource` that can be used to play silence of a given
    duration ([#5735](https://github.com/google/ExoPlayer/issues/5735)).
*   Offline:
    *   Prevent unexpected `DownloadHelper.Callback.onPrepared` callbacks after
        preparation of a `DownloadHelper` fails
        ([#5915](https://github.com/google/ExoPlayer/issues/5915)).
    *   Fix `CacheUtil.cache()` downloading too much data
        ([#5927](https://github.com/google/ExoPlayer/issues/5927)).
    *   Fix misreporting cached bytes when caching is paused
        ([#5573](https://github.com/google/ExoPlayer/issues/5573)).
*   UI:
    *   Allow setting `DefaultTimeBar` attributes on `PlayerView` and
        `PlayerControlView`.
    *   Change playback controls toggle from touch down to touch up events
        ([#5784](https://github.com/google/ExoPlayer/issues/5784)).
    *   Fix issue where playback controls were not kept visible on key presses
        ([#5963](https://github.com/google/ExoPlayer/issues/5963)).
*   Subtitles:
    *   CEA-608: Handle XDS and TEXT modes
        ([#5807](https://github.com/google/ExoPlayer/pull/5807)).
    *   TTML: Fix bitmap rendering
        ([#5633](https://github.com/google/ExoPlayer/pull/5633)).
*   IMA: Fix ad pod index offset calculation without preroll
    ([#5928](https://github.com/google/ExoPlayer/issues/5928)).
*   Add a `playWhenReady` flag to MediaSessionConnector.PlaybackPreparer methods
    to indicate whether a controller sent a play or only a prepare command. This
    allows to take advantage of decoder reuse with the MediaSessionConnector
    ([#5891](https://github.com/google/ExoPlayer/issues/5891)).
*   Add `ProgressUpdateListener` to `PlayerControlView`
    ([#5834](https://github.com/google/ExoPlayer/issues/5834)).
*   Add support for auto-detecting UDP streams in `DefaultDataSource`
    ([#6036](https://github.com/google/ExoPlayer/pull/6036)).
*   Allow enabling decoder fallback with `DefaultRenderersFactory`
    ([#5942](https://github.com/google/ExoPlayer/issues/5942)).
*   Gracefully handle revoked `ACCESS_NETWORK_STATE` permission
    ([#6019](https://github.com/google/ExoPlayer/issues/6019)).
*   Fix decoding problems when seeking back after seeking beyond a mid-roll ad
    ([#6009](https://github.com/google/ExoPlayer/issues/6009)).
*   Fix application of `maxAudioBitrate` for adaptive audio track groups
    ([#6006](https://github.com/google/ExoPlayer/issues/6006)).
*   Fix bug caused by parallel adaptive track selection using `Format`s without
    bitrate information
    ([#5971](https://github.com/google/ExoPlayer/issues/5971)).
*   Fix bug in `CastPlayer.getCurrentWindowIndex()`
    ([#5955](https://github.com/google/ExoPlayer/issues/5955)).

### 2.10.1 (2019-05-16)

*   Offline: Add option to remove all downloads.
*   HLS: Fix `NullPointerException` when using HLS chunkless preparation
    ([#5868](https://github.com/google/ExoPlayer/issues/5868)).
*   Fix handling of empty values and line terminators in SHOUTcast ICY metadata
    ([#5876](https://github.com/google/ExoPlayer/issues/5876)).
*   Fix DVB subtitles for SDK 28
    ([#5862](https://github.com/google/ExoPlayer/issues/5862)).
*   Add a workaround for a decoder failure on ZTE Axon7 mini devices when
    playing 48kHz audio
    ([#5821](https://github.com/google/ExoPlayer/issues/5821)).

### 2.10.0 (2019-04-15)

*   Core library:
    *   Improve decoder re-use between playbacks
        ([#2826](https://github.com/google/ExoPlayer/issues/2826)). Read
        [this blog post](https://medium.com/google-exoplayer/improved-decoder-reuse-in-exoplayer-ef4c6d99591d)
        for more details.
    *   Rename `ExtractorMediaSource` to `ProgressiveMediaSource`.
    *   Fix issue where using `ProgressiveMediaSource.Factory` would mean that
        `DefaultExtractorsFactory` would be kept by proguard. Custom
        `ExtractorsFactory` instances must now be passed via the
        `ProgressiveMediaSource.Factory` constructor, and `setExtractorsFactory`
        is deprecated.
    *   Make the default minimum buffer size equal the maximum buffer size for
        video playbacks
        ([#2083](https://github.com/google/ExoPlayer/issues/2083)).
    *   Move `PriorityTaskManager` from `DefaultLoadControl` to
        `SimpleExoPlayer`.
    *   Add new `ExoPlaybackException` types for remote exceptions and
        out-of-memory errors.
    *   Use full BCP 47 language tags in `Format`.
    *   Do not retry failed loads whose error is `FileNotFoundException`.
    *   Fix issue where not resetting the position for a new `MediaSource` in
        calls to `ExoPlayer.prepare` causes an `IndexOutOfBoundsException`
        ([#5520](https://github.com/google/ExoPlayer/issues/5520)).
*   Offline:
    *   Improve offline support. `DownloadManager` now tracks all offline
        content, not just tasks in progress. Read
        [this page](https://exoplayer.dev/downloading-media.html) for more
        details.
*   Caching:
    *   Improve performance of `SimpleCache`
        ([#4253](https://github.com/google/ExoPlayer/issues/4253)).
    *   Cache data with unknown length by default. The previous flag to opt in
        to this behavior (`DataSpec.FLAG_ALLOW_CACHING_UNKNOWN_LENGTH`) has been
        replaced with an opt out flag
        (`DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN`).
*   Extractors:
    *   MP4/FMP4: Add support for Dolby Vision.
    *   MP4: Fix issue handling meta atoms in some streams
        ([#5698](https://github.com/google/ExoPlayer/issues/5698),
        [#5694](https://github.com/google/ExoPlayer/issues/5694)).
    *   MP3: Add support for SHOUTcast ICY metadata
        ([#3735](https://github.com/google/ExoPlayer/issues/3735)).
    *   MP3: Fix ID3 frame unsychronization
        ([#5673](https://github.com/google/ExoPlayer/issues/5673)).
    *   MP3: Fix playback of badly clipped files
        ([#5772](https://github.com/google/ExoPlayer/issues/5772)).
    *   MPEG-TS: Enable HDMV DTS stream detection only if a flag is set. By
        default (i.e. if the flag is not set), the 0x82 elementary stream type
        is now treated as an SCTE subtitle track
        ([#5330](https://github.com/google/ExoPlayer/issues/5330)).
*   Track selection:
    *   Add options for controlling audio track selections to
        `DefaultTrackSelector`
        ([#3314](https://github.com/google/ExoPlayer/issues/3314)).
    *   Update `TrackSelection.Factory` interface to support creating all track
        selections together.
    *   Allow to specify a selection reason for a `SelectionOverride`.
    *   Select audio track based on system language if no preference is
        provided.
    *   When no text language preference matches, only select forced text tracks
        whose language matches the selected audio language.
*   UI:
    *   Update `DefaultTimeBar` based on duration of media and add parameter to
        set the minimum update interval to control the smoothness of the updates
        ([#5040](https://github.com/google/ExoPlayer/issues/5040)).
    *   Move creation of dialogs for `TrackSelectionView`s to
        `TrackSelectionDialogBuilder` and add option to select multiple
        overrides.
    *   Change signature of `PlayerNotificationManager.NotificationListener` to
        better fit service requirements.
    *   Add option to include navigation actions in the compact mode of
        notifications created using `PlayerNotificationManager`.
    *   Fix issues with flickering notifications on KitKat when using
        `PlayerNotificationManager` and `DownloadNotificationUtil`. For the
        latter, applications should switch to using
        `DownloadNotificationHelper`.
    *   Fix accuracy of D-pad seeking in `DefaultTimeBar`
        ([#5767](https://github.com/google/ExoPlayer/issues/5767)).
*   Audio:
    *   Allow `AudioProcessor`s to be drained of pending output after they are
        reconfigured.
    *   Fix an issue that caused audio to be truncated at the end of a period
        when switching to a new period where gapless playback information was
        newly present or newly absent.
    *   Add support for reading AC-4 streams
        ([#5303](https://github.com/google/ExoPlayer/pull/5303)).
*   Video:
    *   Remove `MediaCodecSelector.DEFAULT_WITH_FALLBACK`. Apps should instead
        signal that fallback should be used by passing `true` as the
        `enableDecoderFallback` parameter when instantiating the video renderer.
    *   Support video tunneling when the decoder is not listed first for the
        MIME type ([#3100](https://github.com/google/ExoPlayer/issues/3100)).
    *   Query `MediaCodecList.ALL_CODECS` when selecting a tunneling decoder
        ([#5547](https://github.com/google/ExoPlayer/issues/5547)).
*   DRM:
    *   Fix black flicker when keys rotate in DRM protected content
        ([#3561](https://github.com/google/ExoPlayer/issues/3561)).
    *   Work around lack of LA_URL attribute in PlayReady key request init data.
*   CEA-608: Improved conformance to the specification
    ([#3860](https://github.com/google/ExoPlayer/issues/3860)).
*   DASH:
    *   Parse role and accessibility descriptors into `Format.roleFlags`.
    *   Support multiple CEA-608 channels muxed into FMP4 representations
        ([#5656](https://github.com/google/ExoPlayer/issues/5656)).
*   HLS:
    *   Prevent unnecessary reloads of initialization segments.
    *   Form an adaptive track group out of audio renditions with matching name.
    *   Support encrypted initialization segments
        ([#5441](https://github.com/google/ExoPlayer/issues/5441)).
    *   Parse `EXT-X-MEDIA` `CHARACTERISTICS` attribute into `Format.roleFlags`.
    *   Add metadata entry for HLS tracks to expose master playlist information.
    *   Prevent `IndexOutOfBoundsException` in some live HLS scenarios
        ([#5816](https://github.com/google/ExoPlayer/issues/5816)).
*   Support for playing spherical videos on Daydream.
*   Cast extension: Work around Cast framework returning a limited-size queue
    items list ([#4964](https://github.com/google/ExoPlayer/issues/4964)).
*   VP9 extension: Remove RGB output mode and libyuv dependency, and switch to
    surface YUV output as the default. Remove constructor parameters
    `scaleToFit` and `useSurfaceYuvOutput`.
*   MediaSession extension:
    *   Let apps intercept media button events
        ([#5179](https://github.com/google/ExoPlayer/issues/5179)).
    *   Fix issue with `TimelineQueueNavigator` not publishing the queue in
        shuffled order when in shuffle mode.
    *   Allow handling of custom commands via `registerCustomCommandReceiver`.
    *   Add ability to include an extras `Bundle` when reporting a custom error.
*   Log warnings when extension native libraries can't be used, to help with
    diagnosing playback failures
    ([#5788](https://github.com/google/ExoPlayer/issues/5788)).

### 2.9.6 (2019-02-19)

*   Remove `player` and `isTopLevelSource` parameters from
    `MediaSource.prepare`.
*   IMA extension:
    *   Require setting the `Player` on `AdsLoader` instances before playback.
    *   Remove deprecated `ImaAdsMediaSource`. Create `AdsMediaSource` with an
        `ImaAdsLoader` instead.
    *   Remove deprecated `AdsMediaSource` constructors. Listen for media source
        events using `AdsMediaSource.addEventListener`, and ad interaction
        events by adding a listener when building `ImaAdsLoader`.
    *   Allow apps to register playback-related obstructing views that are on
        top of their ad display containers via `AdsLoader.AdViewProvider`.
        `PlayerView` implements this interface and will register its control
        view. This makes it possible for ad loading SDKs to calculate ad
        viewability accurately.
*   DASH: Fix issue handling large `EventStream` presentation timestamps
    ([#5490](https://github.com/google/ExoPlayer/issues/5490)).
*   HLS: Fix transition to STATE_ENDED when playing fragmented mp4 in chunkless
    preparation ([#5524](https://github.com/google/ExoPlayer/issues/5524)).
*   Revert workaround for video quality problems with Amlogic decoders, as this
    may cause problems for some devices and/or non-interlaced content
    ([#5003](https://github.com/google/ExoPlayer/issues/5003)).

### 2.9.5 (2019-01-31)

*   HLS: Parse `CHANNELS` attribute from `EXT-X-MEDIA` tag.
*   ConcatenatingMediaSource:
    *   Add `Handler` parameter to methods that take a callback `Runnable`.
    *   Fix issue with dropped messages when releasing the source
        ([#5464](https://github.com/google/ExoPlayer/issues/5464)).
*   ExtractorMediaSource: Fix issue that could cause the player to get stuck
    buffering at the end of the media.
*   PlayerView: Fix issue preventing `OnClickListener` from receiving events
    ([#5433](https://github.com/google/ExoPlayer/issues/5433)).
*   IMA extension: Upgrade IMA dependency to 3.10.6.
*   Cronet extension: Upgrade Cronet dependency to 71.3578.98.
*   OkHttp extension: Upgrade OkHttp dependency to 3.12.1.
*   MP3: Wider fix for issue where streams would play twice on some Samsung
    devices ([#4519](https://github.com/google/ExoPlayer/issues/4519)).

### 2.9.4 (2019-01-15)

*   IMA extension: Clear ads loader listeners on release
    ([#4114](https://github.com/google/ExoPlayer/issues/4114)).
*   SmoothStreaming: Fix support for subtitles in DRM protected streams
    ([#5378](https://github.com/google/ExoPlayer/issues/5378)).
*   FFmpeg extension: Treat invalid data errors as non-fatal to match the
    behavior of MediaCodec
    ([#5293](https://github.com/google/ExoPlayer/issues/5293)).
*   GVR extension: upgrade GVR SDK dependency to 1.190.0.
*   Associate fatal player errors of type SOURCE with the loading source in
    `AnalyticsListener.EventTime`
    ([#5407](https://github.com/google/ExoPlayer/issues/5407)).
*   Add `startPositionUs` to `MediaSource.createPeriod`. This fixes an issue
    where using lazy preparation in `ConcatenatingMediaSource` with an
    `ExtractorMediaSource` overrides initial seek positions
    ([#5350](https://github.com/google/ExoPlayer/issues/5350)).
*   Add subtext to the `MediaDescriptionAdapter` of the
    `PlayerNotificationManager`.
*   Add workaround for video quality problems with Amlogic decoders
    ([#5003](https://github.com/google/ExoPlayer/issues/5003)).
*   Fix issue where sending callbacks for playlist changes may cause problems
    because of parallel player access
    ([#5240](https://github.com/google/ExoPlayer/issues/5240)).
*   Fix issue with reusing a `ClippingMediaSource` with an inner
    `ExtractorMediaSource` and a non-zero start position
    ([#5351](https://github.com/google/ExoPlayer/issues/5351)).
*   Fix issue where uneven track durations in MP4 streams can cause OOM problems
    ([#3670](https://github.com/google/ExoPlayer/issues/3670)).

### 2.9.3 (2018-12-20)

*   Captions: Support PNG subtitles in SMPTE-TT
    ([#1583](https://github.com/google/ExoPlayer/issues/1583)).
*   MPEG-TS: Use random access indicators to minimize the need for
    `FLAG_ALLOW_NON_IDR_KEYFRAMES`.
*   Downloading: Reduce time taken to remove downloads
    ([#5136](https://github.com/google/ExoPlayer/issues/5136)).
*   MP3:
    *   Use the true bitrate for constant-bitrate MP3 seeking.
    *   Fix issue where streams would play twice on some Samsung devices
        ([#4519](https://github.com/google/ExoPlayer/issues/4519)).
*   Fix regression where some audio formats were incorrectly marked as being
    unplayable due to under-reporting of platform decoder capabilities
    ([#5145](https://github.com/google/ExoPlayer/issues/5145)).
*   Fix decode-only frame skipping on Nvidia Shield TV devices.
*   Workaround for MiTV (dangal) issue when swapping output surface
    ([#5169](https://github.com/google/ExoPlayer/issues/5169)).

### 2.9.2 (2018-11-28)

*   HLS:
    *   Fix issue causing unnecessary media playlist requests when playing live
        streams ([#5059](https://github.com/google/ExoPlayer/issues/5059)).
    *   Fix decoder re-instantiation issue for packed audio streams
        ([#5063](https://github.com/google/ExoPlayer/issues/5063)).
*   MP4: Support Opus and FLAC in the MP4 container, and in DASH
    ([#4883](https://github.com/google/ExoPlayer/issues/4883)).
*   DASH: Fix detecting the end of live events
    ([#4780](https://github.com/google/ExoPlayer/issues/4780)).
*   Spherical video: Fall back to `TYPE_ROTATION_VECTOR` if
    `TYPE_GAME_ROTATION_VECTOR` is unavailable
    ([#5119](https://github.com/google/ExoPlayer/issues/5119)).
*   Support seeking for a wider range of MPEG-TS streams
    ([#5097](https://github.com/google/ExoPlayer/issues/5097)).
*   Include channel count in audio capabilities check
    ([#4690](https://github.com/google/ExoPlayer/issues/4690)).
*   Fix issue with applying the `show_buffering` attribute in `PlayerView`
    ([#5139](https://github.com/google/ExoPlayer/issues/5139)).
*   Fix issue where null `Metadata` was output when it failed to decode
    ([#5149](https://github.com/google/ExoPlayer/issues/5149)).
*   Fix playback of some invalid but playable MP4 streams by replacing
    assertions with logged warnings in sample table parsing code
    ([#5162](https://github.com/google/ExoPlayer/issues/5162)).
*   Fix UUID passed to `MediaCrypto` when using `C.CLEARKEY_UUID` before API 27.

### 2.9.1 (2018-11-01)

*   Add convenience methods `Player.next`, `Player.previous`, `Player.hasNext`
    and `Player.hasPrevious`
    ([#4863](https://github.com/google/ExoPlayer/issues/4863)).
*   Improve initial bandwidth meter estimates using the current country and
    network type.
*   IMA extension:
    *   For preroll to live stream transitions, project forward the loading
        position to avoid being behind the live window.
    *   Let apps specify whether to focus the skip button on ATV
        ([#5019](https://github.com/google/ExoPlayer/issues/5019)).
*   MP3:
    *   Support seeking based on MLLT metadata
        ([#3241](https://github.com/google/ExoPlayer/issues/3241)).
    *   Fix handling of streams with appended data
        ([#4954](https://github.com/google/ExoPlayer/issues/4954)).
*   DASH: Parse ProgramInformation element if present in the manifest.
*   HLS:
    *   Add constructor to `DefaultHlsExtractorFactory` for adding TS payload
        reader factory flags
        ([#4861](https://github.com/google/ExoPlayer/issues/4861)).
    *   Fix bug in segment sniffing
        ([#5039](https://github.com/google/ExoPlayer/issues/5039)).
*   SubRip: Add support for alignment tags, and remove tags from the displayed
    captions ([#4306](https://github.com/google/ExoPlayer/issues/4306)).
*   Fix issue with blind seeking to windows with non-zero offset in a
    `ConcatenatingMediaSource`
    ([#4873](https://github.com/google/ExoPlayer/issues/4873)).
*   Fix logic for enabling next and previous actions in `TimelineQueueNavigator`
    ([#5065](https://github.com/google/ExoPlayer/issues/5065)).
*   Fix issue where audio focus handling could not be disabled after enabling it
    ([#5055](https://github.com/google/ExoPlayer/issues/5055)).
*   Fix issue where subtitles were positioned incorrectly if `SubtitleView` had
    a non-zero position offset to its parent
    ([#4788](https://github.com/google/ExoPlayer/issues/4788)).
*   Fix issue where the buffered position was not updated correctly when
    transitioning between periods
    ([#4899](https://github.com/google/ExoPlayer/issues/4899)).
*   Fix issue where a `NullPointerException` is thrown when removing an
    unprepared media source from a `ConcatenatingMediaSource` with the
    `useLazyPreparation` option enabled
    ([#4986](https://github.com/google/ExoPlayer/issues/4986)).
*   Work around an issue where a non-empty end-of-stream audio buffer would be
    output with timestamp zero, causing the player position to jump backwards
    ([#5045](https://github.com/google/ExoPlayer/issues/5045)).
*   Suppress a spurious assertion failure on some Samsung devices
    ([#4532](https://github.com/google/ExoPlayer/issues/4532)).
*   Suppress spurious "references unknown class member" shrinking warning
    ([#4890](https://github.com/google/ExoPlayer/issues/4890)).
*   Swap recommended order for google() and jcenter() in gradle config
    ([#4997](https://github.com/google/ExoPlayer/issues/4997)).

### 2.9.0 (2018-09-06)

*   Turn on Java 8 compiler support for the ExoPlayer library. Apps may need to
    add `compileOptions { targetCompatibility JavaVersion.VERSION_1_8 }` to
    their gradle settings to ensure bytecode compatibility.
*   Set `compileSdkVersion` and `targetSdkVersion` to 28.
*   Support for automatic audio focus handling via
    `SimpleExoPlayer.setAudioAttributes`.
*   Add `ExoPlayer.retry` convenience method.
*   Add `AudioListener` for listening to changes in audio configuration during
    playback ([#3994](https://github.com/google/ExoPlayer/issues/3994)).
*   Add `LoadErrorHandlingPolicy` to allow configuration of load error handling
    across `MediaSource` implementations
    ([#3370](https://github.com/google/ExoPlayer/issues/3370)).
*   Allow passing a `Looper`, which specifies the thread that must be used to
    access the player, when instantiating player instances using
    `ExoPlayerFactory`
    ([#4278](https://github.com/google/ExoPlayer/issues/4278)).
*   Allow setting log level for ExoPlayer logcat output
    ([#4665](https://github.com/google/ExoPlayer/issues/4665)).
*   Simplify `BandwidthMeter` injection: The `BandwidthMeter` should now be
    passed directly to `ExoPlayerFactory`, instead of to
    `TrackSelection.Factory` and `DataSource.Factory`. The `BandwidthMeter` is
    passed to the components that need it internally. The `BandwidthMeter` may
    also be omitted, in which case a default instance will be used.
*   Spherical video:
    *   Support for spherical video by setting `surface_type="spherical_view"`
        on `PlayerView`.
    *   Support for
        [VR180](https://github.com/google/spatial-media/blob/master/docs/vr180.md).
*   HLS:
    *   Support PlayReady.
    *   Add container format sniffing
        ([#2025](https://github.com/google/ExoPlayer/issues/2025)).
    *   Support alternative `EXT-X-KEY` tags.
    *   Support `EXT-X-INDEPENDENT-SEGMENTS` in the master playlist.
    *   Support variable substitution
        ([#4422](https://github.com/google/ExoPlayer/issues/4422)).
    *   Fix the bitrate being unset on primary track sample formats
        ([#3297](https://github.com/google/ExoPlayer/issues/3297)).
    *   Make `HlsMediaSource.Factory` take a factory of trackers instead of a
        tracker instance
        ([#4814](https://github.com/google/ExoPlayer/issues/4814)).
*   DASH:
    *   Support `messageData` attribute for in-manifest event streams.
    *   Clip periods to their specified durations
        ([#4185](https://github.com/google/ExoPlayer/issues/4185)).
*   Improve seeking support for progressive streams:
    *   Support seeking in MPEG-TS
        ([#966](https://github.com/google/ExoPlayer/issues/966)).
    *   Support seeking in MPEG-PS
        ([#4476](https://github.com/google/ExoPlayer/issues/4476)).
    *   Support approximate seeking in ADTS using a constant bitrate assumption
        ([#4548](https://github.com/google/ExoPlayer/issues/4548)). The
        `FLAG_ENABLE_CONSTANT_BITRATE_SEEKING` flag must be set on the extractor
        to enable this functionality.
    *   Support approximate seeking in AMR using a constant bitrate assumption.
        The `FLAG_ENABLE_CONSTANT_BITRATE_SEEKING` flag must be set on the
        extractor to enable this functionality.
    *   Add `DefaultExtractorsFactory.setConstantBitrateSeekingEnabled` to
        enable approximate seeking using a constant bitrate assumption on all
        extractors that support it.
*   Video:
    *   Add callback to `VideoListener` to notify of surface size changes.
    *   Improve performance when playing high frame-rate content, and when
        playing at greater than 1x speed
        ([#2777](https://github.com/google/ExoPlayer/issues/2777)).
    *   Scale up the initial video decoder maximum input size so playlist
        transitions with small increases in maximum sample size do not require
        reinitialization
        ([#4510](https://github.com/google/ExoPlayer/issues/4510)).
    *   Fix a bug where the player would not transition to the ended state when
        playing video in tunneled mode.
*   Audio:
    *   Support attaching auxiliary audio effects to the `AudioTrack` via
        `Player.setAuxEffectInfo` and `Player.clearAuxEffectInfo`.
    *   Support seamless adaptation while playing xHE-AAC streams.
        ([#4360](https://github.com/google/ExoPlayer/issues/4360)).
    *   Increase `AudioTrack` buffer sizes to the theoretical maximum required
        for each encoding for passthrough playbacks
        ([#3803](https://github.com/google/ExoPlayer/issues/3803)).
    *   WAV: Fix issue where white noise would be output at the end of playback
        ([#4724](https://github.com/google/ExoPlayer/issues/4724)).
    *   MP3: Fix issue where streams would play twice on the SM-T530
        ([#4519](https://github.com/google/ExoPlayer/issues/4519)).
*   Analytics:
    *   Add callbacks to `DefaultDrmSessionEventListener` and
        `AnalyticsListener` to be notified of acquired and released DRM
        sessions.
    *   Add uri field to `LoadEventInfo` in `MediaSourceEventListener` and
        `AnalyticsListener` callbacks. This uri is the redirected uri if
        redirection occurred
        ([#2054](https://github.com/google/ExoPlayer/issues/2054)).
    *   Add response headers field to `LoadEventInfo` in
        `MediaSourceEventListener` and `AnalyticsListener` callbacks
        ([#4361](https://github.com/google/ExoPlayer/issues/4361) and
        [#4615](https://github.com/google/ExoPlayer/issues/4615)).
*   UI:
    *   Add option to `PlayerView` to show buffering view when playWhenReady is
        false ([#4304](https://github.com/google/ExoPlayer/issues/4304)).
    *   Allow any `Drawable` to be used as `PlayerView` default artwork.
*   ConcatenatingMediaSource:
    *   Support lazy preparation of playlist media sources
        ([#3972](https://github.com/google/ExoPlayer/issues/3972)).
    *   Support range removal with `removeMediaSourceRange` methods
        ([#4542](https://github.com/google/ExoPlayer/issues/4542)).
    *   Support setting a new shuffle order with `setShuffleOrder`
        ([#4791](https://github.com/google/ExoPlayer/issues/4791)).
*   MPEG-TS: Support CEA-608/708 in H262
    ([#2565](https://github.com/google/ExoPlayer/issues/2565)).
*   Allow configuration of the back buffer in `DefaultLoadControl.Builder`
    ([#4857](https://github.com/google/ExoPlayer/issues/4857)).
*   Allow apps to pass a `CacheKeyFactory` for setting custom cache keys when
    creating a `CacheDataSource`.
*   Provide additional information for adaptive track selection.
    `TrackSelection.updateSelectedTrack` has two new parameters for the current
    queue of media chunks and iterators for information about upcoming chunks.
*   Allow `MediaCodecSelector`s to return multiple compatible decoders for
    `MediaCodecRenderer`, and provide an (optional) `MediaCodecSelector` that
    falls back to less preferred decoders like `MediaCodec.createDecoderByType`
    ([#273](https://github.com/google/ExoPlayer/issues/273)).
*   Enable gzip for requests made by `SingleSampleMediaSource`
    ([#4771](https://github.com/google/ExoPlayer/issues/4771)).
*   Fix bug reporting buffered position for multi-period windows, and add
    convenience methods `Player.getTotalBufferedDuration` and
    `Player.getContentBufferedDuration`
    ([#4023](https://github.com/google/ExoPlayer/issues/4023)).
*   Fix bug where transitions to clipped media sources would happen too early
    ([#4583](https://github.com/google/ExoPlayer/issues/4583)).
*   Fix bugs reporting events for multi-period media sources
    ([#4492](https://github.com/google/ExoPlayer/issues/4492) and
    [#4634](https://github.com/google/ExoPlayer/issues/4634)).
*   Fix issue where removing looping media from a playlist throws an exception
    ([#4871](https://github.com/google/ExoPlayer/issues/4871)).
*   Fix issue where the preferred audio or text track would not be selected if
    mapped onto a secondary renderer of the corresponding type
    ([#4711](http://github.com/google/ExoPlayer/issues/4711)).
*   Fix issue where errors of upcoming playlist items are thrown too early
    ([#4661](https://github.com/google/ExoPlayer/issues/4661)).
*   Allow edit lists which do not start with a sync sample.
    ([#4774](https://github.com/google/ExoPlayer/issues/4774)).
*   Fix issue with audio discontinuities at period transitions, e.g. when
    looping ([#3829](https://github.com/google/ExoPlayer/issues/3829)).
*   Fix issue where `player.getCurrentTag()` throws an
    `IndexOutOfBoundsException`
    ([#4822](https://github.com/google/ExoPlayer/issues/4822)).
*   Fix bug preventing use of multiple key session support (`multiSession=true`)
    for non-Widevine `DefaultDrmSessionManager` instances
    ([#4834](https://github.com/google/ExoPlayer/issues/4834)).
*   Fix issue where audio and video would desynchronize when playing
    concatenations of gapless content
    ([#4559](https://github.com/google/ExoPlayer/issues/4559)).
*   IMA extension:
    *   Refine the previous fix for empty ad groups to avoid discarding ad
        breaks unnecessarily
        ([#4030](https://github.com/google/ExoPlayer/issues/4030) and
        [#4280](https://github.com/google/ExoPlayer/issues/4280)).
    *   Fix handling of empty postrolls
        ([#4681](https://github.com/google/ExoPlayer/issues/4681)).
    *   Fix handling of postrolls with multiple ads
        ([#4710](https://github.com/google/ExoPlayer/issues/4710)).
*   MediaSession extension:
    *   Add `MediaSessionConnector.setCustomErrorMessage` to support setting
        custom error messages.
    *   Add `MediaMetadataProvider` to support setting custom metadata
        ([#3497](https://github.com/google/ExoPlayer/issues/3497)).
*   Cronet extension: Now distributed via jCenter.
*   FFmpeg extension: Support mu-law and A-law PCM.

### 2.8.4 (2018-08-17)

*   IMA extension: Improve handling of consecutive empty ad groups
    ([#4030](https://github.com/google/ExoPlayer/issues/4030)),
    ([#4280](https://github.com/google/ExoPlayer/issues/4280)).

### 2.8.3 (2018-07-23)

*   IMA extension:
    *   Fix behavior when creating/releasing the player then releasing
        `ImaAdsLoader`
        ([#3879](https://github.com/google/ExoPlayer/issues/3879)).
    *   Add support for setting slots for companion ads.
*   Captions:
    *   TTML: Fix an issue with TTML using font size as % of cell resolution
        that makes `SubtitleView.setApplyEmbeddedFontSizes()` not work
        correctly. ([#4491](https://github.com/google/ExoPlayer/issues/4491)).
    *   CEA-608: Improve handling of embedded styles
        ([#4321](https://github.com/google/ExoPlayer/issues/4321)).
*   DASH:
    *   Exclude text streams from duration calculations
        ([#4029](https://github.com/google/ExoPlayer/issues/4029)).
    *   Fix freezing when playing multi-period manifests with `EventStream`s
        ([#4492](https://github.com/google/ExoPlayer/issues/4492)).
*   DRM: Allow DrmInitData to carry a license server URL
    ([#3393](https://github.com/google/ExoPlayer/issues/3393)).
*   MPEG-TS: Fix bug preventing SCTE-35 cues from being output
    ([#4573](https://github.com/google/ExoPlayer/issues/4573)).
*   Expose all internal ID3 data stored in MP4 udta boxes, and switch from using
    CommentFrame to InternalFrame for frames with gapless metadata in MP4.
*   Add `PlayerView.isControllerVisible`
    ([#4385](https://github.com/google/ExoPlayer/issues/4385)).
*   Fix issue playing DRM protected streams on Asus Zenfone 2
    ([#4403](https://github.com/google/ExoPlayer/issues/4413)).
*   Add support for multiple audio and video tracks in MPEG-PS streams
    ([#4406](https://github.com/google/ExoPlayer/issues/4406)).
*   Add workaround for track index mismatches between trex and tkhd boxes in
    fragmented MP4 files
    ([#4477](https://github.com/google/ExoPlayer/issues/4477)).
*   Add workaround for track index mismatches between tfhd and tkhd boxes in
    fragmented MP4 files
    ([#4083](https://github.com/google/ExoPlayer/issues/4083)).
*   Ignore all MP4 edit lists if one edit list couldn't be handled
    ([#4348](https://github.com/google/ExoPlayer/issues/4348)).
*   Fix issue when switching track selection from an embedded track to a primary
    track in DASH ([#4477](https://github.com/google/ExoPlayer/issues/4477)).
*   Fix accessibility class name for `DefaultTimeBar`
    ([#4611](https://github.com/google/ExoPlayer/issues/4611)).
*   Improved compatibility with FireOS devices.

### 2.8.2 (2018-06-06)

*   IMA extension: Don't advertise support for video/mpeg ad media, as we don't
    have an extractor for this
    ([#4297](https://github.com/google/ExoPlayer/issues/4297)).
*   DASH: Fix playback getting stuck when playing representations that have both
    sidx atoms and non-zero presentationTimeOffset values.
*   HLS:
    *   Allow injection of custom playlist trackers.
    *   Fix adaptation in live playlists with EXT-X-PROGRAM-DATE-TIME tags.
*   Mitigate memory leaks when `MediaSource` loads are slow to cancel
    ([#4249](https://github.com/google/ExoPlayer/issues/4249)).
*   Fix inconsistent `Player.EventListener` invocations for recursive player
    state changes ([#4276](https://github.com/google/ExoPlayer/issues/4276)).
*   Fix `MediaCodec.native_setSurface` crash on Moto C
    ([#4315](https://github.com/google/ExoPlayer/issues/4315)).
*   Fix missing whitespace in CEA-608
    ([#3906](https://github.com/google/ExoPlayer/issues/3906)).
*   Fix crash downloading HLS media playlists
    ([#4396](https://github.com/google/ExoPlayer/issues/4396)).
*   Fix a bug where download cancellation was ignored
    ([#4403](https://github.com/google/ExoPlayer/issues/4403)).
*   Set `METADATA_KEY_TITLE` on media descriptions
    ([#4292](https://github.com/google/ExoPlayer/issues/4292)).
*   Allow apps to register custom MIME types
    ([#4264](https://github.com/google/ExoPlayer/issues/4264)).

### 2.8.1 (2018-05-22)

*   HLS:
    *   Fix playback of livestreams with EXT-X-PROGRAM-DATE-TIME tags
        ([#4239](https://github.com/google/ExoPlayer/issues/4239)).
    *   Fix playback of clipped streams starting from non-keyframe positions
        ([#4241](https://github.com/google/ExoPlayer/issues/4241)).
*   OkHttp extension: Fix to correctly include response headers in thrown
    `InvalidResponseCodeException`s.
*   Add possibility to cancel `PlayerMessage`s.
*   UI:
    *   Add `PlayerView.setKeepContentOnPlayerReset` to keep the currently
        displayed video frame or media artwork visible when the player is reset
        ([#2843](https://github.com/google/ExoPlayer/issues/2843)).
*   Fix crash when switching surface on Moto E(4)
    ([#4134](https://github.com/google/ExoPlayer/issues/4134)).
*   Fix a bug that could cause event listeners to be called with inconsistent
    information if an event listener interacted with the player
    ([#4262](https://github.com/google/ExoPlayer/issues/4262)).
*   Audio:
    *   Fix extraction of PCM in MP4/MOV
        ([#4228](https://github.com/google/ExoPlayer/issues/4228)).
    *   FLAC: Supports seeking for FLAC files without SEEKTABLE
        ([#1808](https://github.com/google/ExoPlayer/issues/1808)).
*   Captions:
    *   TTML:
    *   Fix a styling issue when there are multiple regions displayed at the
        same time that can make text size of each region much smaller than
        defined.
    *   Fix an issue when the caption line has no text (empty line or only line
        break), and the line's background is still displayed.
    *   Support TTML font size using % correctly (as percentage of document cell
        resolution).

### 2.8.0 (2018-05-03)

*   Downloading:
    *   Add `DownloadService`, `DownloadManager` and related classes
        ([#2643](https://github.com/google/ExoPlayer/issues/2643)). Information
        on using these components to download progressive formats can be found
        [here](https://medium.com/google-exoplayer/downloading-streams-6d259eec7f95).
        To see how to download DASH, HLS and SmoothStreaming media, take a look
        at the app.
    *   Updated main demo app to support downloading DASH, HLS, SmoothStreaming
        and progressive media.
*   MediaSources:
    *   Allow reusing media sources after they have been released and also in
        parallel to allow adding them multiple times to a concatenation.
        ([#3498](https://github.com/google/ExoPlayer/issues/3498)).
    *   Merged `DynamicConcatenatingMediaSource` into `ConcatenatingMediaSource`
        and deprecated `DynamicConcatenatingMediaSource`.
    *   Allow clipping of child media sources where the period and window have a
        non-zero offset with `ClippingMediaSource`.
    *   Allow adding and removing `MediaSourceEventListener`s to MediaSources
        after they have been created. Listening to events is now supported for
        all media sources including composite sources.
    *   Added callbacks to `MediaSourceEventListener` to get notified when media
        periods are created, released and being read from.
    *   Support live stream clipping with `ClippingMediaSource`.
    *   Allow setting tags for all media sources in their factories. The tag of
        the current window can be retrieved with `Player.getCurrentTag`.
*   UI:
    *   Add support for displaying error messages and a buffering spinner in
        `PlayerView`.
    *   Add support for listening to `AspectRatioFrameLayout`'s aspect ratio
        update ([#3736](https://github.com/google/ExoPlayer/issues/3736)).
    *   Add `PlayerNotificationManager` for displaying notifications reflecting
        the player state.
    *   Add `TrackSelectionView` for selecting tracks with
        `DefaultTrackSelector`.
    *   Add `TrackNameProvider` for converting track `Format`s to textual
        descriptions, and `DefaultTrackNameProvider` as a default
        implementation.
*   Track selection:
    *   Reworked `MappingTrackSelector` and `DefaultTrackSelector`.
    *   `DefaultTrackSelector.Parameters` now implements `Parcelable`.
    *   Added UI components for track selection (see above).
*   Audio:
    *   Support extracting data from AMR container formats, including both
        narrow and wide band
        ([#2527](https://github.com/google/ExoPlayer/issues/2527)).
    *   FLAC:
    *   Sniff FLAC files correctly if they have ID3 headers
        ([#4055](https://github.com/google/ExoPlayer/issues/4055)).
    *   Supports FLAC files with high sample rate (176400 and 192000)
        ([#3769](https://github.com/google/ExoPlayer/issues/3769)).
    *   Factor out `AudioTrack` position tracking from `DefaultAudioSink`.
    *   Fix an issue where the playback position would pause just after playback
        begins, and poll the audio timestamp less frequently once it starts
        advancing ([#3841](https://github.com/google/ExoPlayer/issues/3841)).
    *   Add an option to skip silent audio in `PlaybackParameters`
        ([#2635](https://github.com/google/ExoPlayer/issues/2635)).
    *   Fix an issue where playback of TrueHD streams would get stuck after
        seeking due to not finding a syncframe
        ([#3845](https://github.com/google/ExoPlayer/issues/3845)).
    *   Fix an issue with eac3-joc playback where a codec would fail to
        configure ([#4165](https://github.com/google/ExoPlayer/issues/4165)).
    *   Handle non-empty end-of-stream buffers, to fix gapless playback of
        streams with encoder padding when the decoder returns a non-empty final
        buffer.
    *   Allow trimming more than one sample when applying an elst audio edit via
        gapless playback info.
    *   Allow overriding skipping/scaling with custom `AudioProcessor`s
        ([#3142](https://github.com/google/ExoPlayer/issues/3142)).
*   Caching:
    *   Add release method to the `Cache` interface, and prevent multiple
        instances of `SimpleCache` using the same folder at the same time.
    *   Cache redirect URLs
        ([#2360](https://github.com/google/ExoPlayer/issues/2360)).
*   DRM:
    *   Allow multiple listeners for `DefaultDrmSessionManager`.
    *   Pass `DrmSessionManager` to `ExoPlayerFactory` instead of
        `RendererFactory`.
    *   Change minimum API requirement for CBC and pattern encryption from 24 to
        25 ([#4022](https://github.com/google/ExoPlayer/issues/4022)).
    *   Fix handling of 307/308 redirects when making license requests
        ([#4108](https://github.com/google/ExoPlayer/issues/4108)).
*   HLS:
    *   Fix playlist loading error propagation when the current selection does
        not include all of the playlist's variants.
    *   Fix SAMPLE-AES-CENC and SAMPLE-AES-CTR EXT-X-KEY methods
        ([#4145](https://github.com/google/ExoPlayer/issues/4145)).
    *   Preeptively declare an ID3 track in chunkless preparation
        ([#4016](https://github.com/google/ExoPlayer/issues/4016)).
    *   Add support for multiple #EXT-X-MAP tags in a media playlist
        ([#4164](https://github.com/google/ExoPlayer/issues/4182)).
    *   Fix seeking in live streams
        ([#4187](https://github.com/google/ExoPlayer/issues/4187)).
*   IMA extension:
    *   Allow setting the ad media load timeout
        ([#3691](https://github.com/google/ExoPlayer/issues/3691)).
    *   Expose ad load errors via `MediaSourceEventListener` on
        `AdsMediaSource`, and allow setting an ad event listener on
        `ImaAdsLoader`. Deprecate the `AdsMediaSource.EventListener`.
*   Add `AnalyticsListener` interface which can be registered in
    `SimpleExoPlayer` to receive detailed metadata for each ExoPlayer event.
*   Optimize seeking in FMP4 by enabling seeking to the nearest sync sample
    within a fragment. This benefits standalone FMP4 playbacks, DASH and
    SmoothStreaming.
*   Updated default max buffer length in `DefaultLoadControl`.
*   Fix ClearKey decryption error if the key contains a forward slash
    ([#4075](https://github.com/google/ExoPlayer/issues/4075)).
*   Fix crash when switching surface on Huawei P9 Lite
    ([#4084](https://github.com/google/ExoPlayer/issues/4084)), and Philips
    QM163E ([#4104](https://github.com/google/ExoPlayer/issues/4104)).
*   Support ZLIB compressed PGS subtitles.
*   Added `getPlaybackError` to `Player` interface.
*   Moved initial bitrate estimate from `AdaptiveTrackSelection` to
    `DefaultBandwidthMeter`.
*   Removed default renderer time offset of 60000000 from internal player. The
    actual renderer timestamp offset can be obtained by listening to
    `BaseRenderer.onStreamChanged`.
*   Added dependencies on checkerframework annotations for static code analysis.

### 2.7.3 (2018-04-04)

*   Fix ProGuard configuration for Cast, IMA and OkHttp extensions.
*   Update OkHttp extension to depend on OkHttp 3.10.0.

### 2.7.2 (2018-03-29)

*   Gradle: Upgrade Gradle version from 4.1 to 4.4 so it can work with Android
    Studio 3.1 ([#3708](https://github.com/google/ExoPlayer/issues/3708)).
*   Match codecs starting with "mp4a" to different Audio MimeTypes
    ([#3779](https://github.com/google/ExoPlayer/issues/3779)).
*   Fix ANR issue on Redmi 4X and Redmi Note 4
    ([#4006](https://github.com/google/ExoPlayer/issues/4006)).
*   Fix handling of zero padded strings when parsing Matroska streams
    ([#4010](https://github.com/google/ExoPlayer/issues/4010)).
*   Fix "Decoder input buffer too small" error when playing some FLAC streams.
*   MediaSession extension: Omit fast forward and rewind actions when media is
    not seekable ([#4001](https://github.com/google/ExoPlayer/issues/4001)).

### 2.7.1 (2018-03-09)

*   Gradle: Replaced 'compile' (deprecated) with 'implementation' and 'api'.
    This may lead to build breakage for applications upgrading from previous
    version that rely on indirect dependencies of certain modules. In such
    cases, application developers need to add the missing dependency to their
    gradle file. You can read more about the new dependency configurations
    [here](https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#new_configurations).
*   HlsMediaSource: Make HLS periods start at zero instead of the epoch.
    Applications that rely on HLS timelines having a period starting at the
    epoch will need to update their handling of HLS timelines. The program date
    time is still available via the informational
    `Timeline.Window.windowStartTimeMs` field
    ([#3865](https://github.com/google/ExoPlayer/issues/3865),
    [#3888](https://github.com/google/ExoPlayer/issues/3888)).
*   Enable seeking in MP4 streams where duration is set incorrectly in the track
    header ([#3926](https://github.com/google/ExoPlayer/issues/3926)).
*   Video: Force rendering a frame periodically in `MediaCodecVideoRenderer` and
    `LibvpxVideoRenderer`, even if it is late.

### 2.7.0 (2018-02-19)

*   Player interface:
    *   Add optional parameter to `stop` to reset the player when stopping.
    *   Add a reason to `EventListener.onTimelineChanged` to distinguish between
        initial preparation, reset and dynamic updates.
    *   Add `Player.DISCONTINUITY_REASON_AD_INSERTION` to the possible reasons
        reported in `Eventlistener.onPositionDiscontinuity` to distinguish
        transitions to and from ads within one period from transitions between
        periods.
    *   Replaced `ExoPlayer.sendMessages` with `ExoPlayer.createMessage` to
        allow more customization of the message. Now supports setting a message
        delivery playback position and/or a delivery handler
        ([#2189](https://github.com/google/ExoPlayer/issues/2189)).
    *   Add `Player.VideoComponent`, `Player.TextComponent` and
        `Player.MetadataComponent` interfaces that define optional video, text
        and metadata output functionality. New `getVideoComponent`,
        `getTextComponent` and `getMetadataComponent` methods provide access to
        this functionality.
*   Add `ExoPlayer.setSeekParameters` for controlling how seek operations are
    performed. The `SeekParameters` class contains defaults for exact seeking
    and seeking to the closest sync points before, either side or after
    specified seek positions. `SeekParameters` are not currently supported when
    playing HLS streams.
*   DefaultTrackSelector:
    *   Replace `DefaultTrackSelector.Parameters` copy methods with a builder.
    *   Support disabling of individual text track selection flags.
*   Buffering:
    *   Allow a back-buffer of media to be retained behind the current playback
        position, for fast backward seeking. The back-buffer can be configured
        by custom `LoadControl` implementations.
    *   Add ability for `SequenceableLoader` to re-evaluate its buffer and
        discard buffered media so that it can be re-buffered in a different
        quality.
    *   Allow more flexible loading strategy when playing media containing
        multiple sub-streams, by allowing injection of custom
        `CompositeSequenceableLoader` factories through
        `DashMediaSource.Factory`, `HlsMediaSource.Factory`,
        `SsMediaSource.Factory`, and `MergingMediaSource`.
    *   Play out existing buffer before retrying for progressive live streams
        ([#1606](https://github.com/google/ExoPlayer/issues/1606)).
*   UI:
    *   Generalized player and control views to allow them to bind with any
        `Player`, and renamed them to `PlayerView` and `PlayerControlView`
        respectively.
    *   Made `PlayerView` automatically apply video rotation when configured to
        use `TextureView`
        ([#91](https://github.com/google/ExoPlayer/issues/91)).
    *   Made `PlayerView` play button behave correctly when the player is ended
        ([#3689](https://github.com/google/ExoPlayer/issues/3689)), and call a
        `PlaybackPreparer` when the player is idle.
*   DRM: Optimistically attempt playback of DRM protected content that does not
    declare scheme specific init data in the manifest. If playback of clear
    samples without keys is allowed, delay DRM session error propagation until
    keys are actually needed
    ([#3630](https://github.com/google/ExoPlayer/issues/3630)).
*   DASH:
    *   Support in-band Emsg events targeting the player with scheme id
        `urn:mpeg:dash:event:2012` and scheme values "1", "2" and "3".
    *   Support EventStream elements in DASH manifests.
*   HLS:
    *   Add opt-in support for chunkless preparation in HLS. This allows an HLS
        source to finish preparation without downloading any chunks, which can
        significantly reduce initial buffering time
        ([#3149](https://github.com/google/ExoPlayer/issues/3149)). More details
        can be found
        [here](https://medium.com/google-exoplayer/faster-hls-preparation-f6611aa15ea6).
    *   Fail if unable to sync with the Transport Stream, rather than entering
        stuck in an indefinite buffering state.
    *   Fix mime type propagation
        ([#3653](https://github.com/google/ExoPlayer/issues/3653)).
    *   Fix ID3 context reuse across segment format changes
        ([#3622](https://github.com/google/ExoPlayer/issues/3622)).
    *   Use long for media sequence numbers
        ([#3747](https://github.com/google/ExoPlayer/issues/3747))
    *   Add initial support for the EXT-X-GAP tag.
*   Audio:
    *   Support TrueHD passthrough for rechunked samples in Matroska files
        ([#2147](https://github.com/google/ExoPlayer/issues/2147)).
    *   Support resampling 24-bit and 32-bit integer to 32-bit float for high
        resolution output in `DefaultAudioSink`
        ([#3635](https://github.com/google/ExoPlayer/pull/3635)).
*   Captions:
    *   Basic support for PGS subtitles
        ([#3008](https://github.com/google/ExoPlayer/issues/3008)).
    *   Fix handling of CEA-608 captions where multiple buffers have the same
        presentation timestamp
        ([#3782](https://github.com/google/ExoPlayer/issues/3782)).
*   Caching:
    *   Fix cache corruption issue
        ([#3762](https://github.com/google/ExoPlayer/issues/3762)).
    *   Implement periodic check in `CacheDataSource` to see whether it's
        possible to switch to reading/writing the cache having initially
        bypassed it.
*   IMA extension:
    *   Fix the player getting stuck when an ad group fails to load
        ([#3584](https://github.com/google/ExoPlayer/issues/3584)).
    *   Work around loadAd not being called beore the LOADED AdEvent arrives
        ([#3552](https://github.com/google/ExoPlayer/issues/3552)).
    *   Handle asset mismatch errors
        ([#3801](https://github.com/google/ExoPlayer/issues/3801)).
    *   Add support for playing non-Extractor content MediaSources in the IMA
        demo app ([#3676](https://github.com/google/ExoPlayer/issues/3676)).
    *   Fix handling of ad tags where ad groups are out of order
        ([#3716](https://github.com/google/ExoPlayer/issues/3716)).
    *   Fix handling of ad tags with only preroll/postroll ad groups
        ([#3715](https://github.com/google/ExoPlayer/issues/3715)).
    *   Propagate ad media preparation errors to IMA so that the ads can be
        skipped.
    *   Handle exceptions in IMA callbacks so that can be logged less verbosely.
*   New Cast extension. Simplifies toggling between local and Cast playbacks.
*   `EventLogger` moved from the demo app into the core library.
*   Fix ANR issue on the Huawei P8 Lite, Huawei Y6II, Moto C+, Meizu M5C, Lenovo
    K4 Note and Sony Xperia E5.
    ([#3724](https://github.com/google/ExoPlayer/issues/3724),
    [#3835](https://github.com/google/ExoPlayer/issues/3835)).
*   Fix potential NPE when removing media sources from a
    DynamicConcatenatingMediaSource
    ([#3796](https://github.com/google/ExoPlayer/issues/3796)).
*   Check `sys.display-size` on Philips ATVs
    ([#3807](https://github.com/google/ExoPlayer/issues/3807)).
*   Release `Extractor`s on the loading thread to avoid potentially leaking
    resources when the playback thread has quit by the time the loading task has
    completed.
*   ID3: Better handle malformed ID3 data
    ([#3792](https://github.com/google/ExoPlayer/issues/3792)).
*   Support 14-bit mode and little endianness in DTS PES packets
    ([#3340](https://github.com/google/ExoPlayer/issues/3340)).
*   Demo app: Add ability to download not DRM protected content.

### 2.6.1 (2017-12-15)

*   Add factories to `ExtractorMediaSource`, `HlsMediaSource`, `SsMediaSource`,
    `DashMediaSource` and `SingleSampleMediaSource`.
*   Use the same listener `MediaSourceEventListener` for all MediaSource
    implementations.
*   IMA extension:
    *   Support non-ExtractorMediaSource ads
        ([#3302](https://github.com/google/ExoPlayer/issues/3302)).
    *   Skip ads before the ad preceding the player's initial seek position
        ([#3527](https://github.com/google/ExoPlayer/issues/3527)).
    *   Fix ad loading when there is no preroll.
    *   Add an option to turn off hiding controls during ad playback
        ([#3532](https://github.com/google/ExoPlayer/issues/3532)).
    *   Support specifying an ads response instead of an ad tag
        ([#3548](https://github.com/google/ExoPlayer/issues/3548)).
    *   Support overriding the ad load timeout
        ([#3556](https://github.com/google/ExoPlayer/issues/3556)).
*   DASH: Support time zone designators in ISO8601 UTCTiming elements
    ([#3524](https://github.com/google/ExoPlayer/issues/3524)).
*   Audio:
    *   Support 32-bit PCM float output from `DefaultAudioSink`, and add an
        option to use this with `FfmpegAudioRenderer`.
    *   Add support for extracting 32-bit WAVE files
        ([#3379](https://github.com/google/ExoPlayer/issues/3379)).
    *   Support extraction and decoding of Dolby Atmos
        ([#2465](https://github.com/google/ExoPlayer/issues/2465)).
    *   Fix handling of playback parameter changes while paused when followed by
        a seek.
*   SimpleExoPlayer: Allow multiple audio and video debug listeners.
*   DefaultTrackSelector: Support undefined language text track selection when
    the preferred language is not available
    ([#2980](https://github.com/google/ExoPlayer/issues/2980)).
*   Add options to `DefaultLoadControl` to set maximum buffer size in bytes and
    to choose whether size or time constraints are prioritized.
*   Use surfaceless context for secure `DummySurface`, if available
    ([#3558](https://github.com/google/ExoPlayer/issues/3558)).
*   FLV: Fix playback of live streams that do not contain an audio track
    ([#3188](https://github.com/google/ExoPlayer/issues/3188)).
*   CEA-608: Fix handling of row count changes in roll-up mode
    ([#3513](https://github.com/google/ExoPlayer/issues/3513)).
*   Prevent period transitions when seeking to the end of a period when paused
    ([#2439](https://github.com/google/ExoPlayer/issues/2439)).

### 2.6.0 (2017-11-03)

*   Removed "r" prefix from versions. This release is "2.6.0", not "r2.6.0".
*   New `Player.DefaultEventListener` abstract class can be extended to avoid
    having to implement all methods defined by `Player.EventListener`.
*   Added a reason to `EventListener.onPositionDiscontinuity`
    ([#3252](https://github.com/google/ExoPlayer/issues/3252)).
*   New `setShuffleModeEnabled` method for enabling shuffled playback.
*   SimpleExoPlayer: Support for multiple video, text and metadata outputs.
*   Support for `Renderer`s that don't consume any media
    ([#3212](https://github.com/google/ExoPlayer/issues/3212)).
*   Fix reporting of internal position discontinuities via
    `Player.onPositionDiscontinuity`. `DISCONTINUITY_REASON_SEEK_ADJUSTMENT` is
    added to disambiguate position adjustments during seeks from other types of
    internal position discontinuity.
*   Fix potential `IndexOutOfBoundsException` when calling
    `ExoPlayer.getDuration`
    ([#3362](https://github.com/google/ExoPlayer/issues/3362)).
*   Fix playbacks involving looping, concatenation and ads getting stuck when
    media contains tracks with uneven durations
    ([#1874](https://github.com/google/ExoPlayer/issues/1874)).
*   Fix issue with `ContentDataSource` when reading from certain
    `ContentProvider` implementations
    ([#3426](https://github.com/google/ExoPlayer/issues/3426)).
*   Better playback experience when the video decoder cannot keep up, by
    skipping to key-frames. This is particularly relevant for variable speed
    playbacks.
*   Allow `SingleSampleMediaSource` to suppress load errors
    ([#3140](https://github.com/google/ExoPlayer/issues/3140)).
*   `DynamicConcatenatingMediaSource`: Allow specifying a callback to be invoked
    after a dynamic playlist modification has been applied
    ([#3407](https://github.com/google/ExoPlayer/issues/3407)).
*   Audio: New `AudioSink` interface allows customization of audio output path.
*   Offline: Added `Downloader` implementations for DASH, HLS, SmoothStreaming
    and progressive streams.
*   Track selection:
    *   Fixed adaptive track selection logic for live playbacks
        ([#3017](https://github.com/google/ExoPlayer/issues/3017)).
    *   Added ability to select the lowest bitrate tracks.
*   DASH:
    *   Don't crash when a malformed or unexpected manifest update occurs
        ([#2795](https://github.com/google/ExoPlayer/issues/2795)).
*   HLS:
    *   Support for Widevine protected FMP4 variants.
    *   Support CEA-608 in FMP4 variants.
    *   Support extractor injection
        ([#2748](https://github.com/google/ExoPlayer/issues/2748)).
*   DRM:
    *   Improved compatibility with ClearKey content
        ([#3138](https://github.com/google/ExoPlayer/issues/3138)).
    *   Support multiple PSSH boxes of the same type.
    *   Retry initial provisioning and key requests if they fail
    *   Fix incorrect parsing of non-CENC sinf boxes.
*   IMA extension:
    *   Expose `AdsLoader` via getter
        ([#3322](https://github.com/google/ExoPlayer/issues/3322)).
    *   Handle `setPlayWhenReady` calls during ad playbacks
        ([#3303](https://github.com/google/ExoPlayer/issues/3303)).
    *   Ignore seeks if an ad is playing
        ([#3309](https://github.com/google/ExoPlayer/issues/3309)).
    *   Improve robustness of `ImaAdsLoader` in case content is not paused
        between content to ad transitions
        ([#3430](https://github.com/google/ExoPlayer/issues/3430)).
*   UI:
    *   Allow specifying a `Drawable` for the `TimeBar` scrubber
        ([#3337](https://github.com/google/ExoPlayer/issues/3337)).
    *   Allow multiple listeners on `TimeBar`
        ([#3406](https://github.com/google/ExoPlayer/issues/3406)).
*   New Leanback extension: Simplifies binding Exoplayer to Leanback UI
    components.
*   Unit tests moved to Robolectric.
*   Misc bugfixes.

### r2.5.4 (2017-10-19)

*   Remove unnecessary media playlist fetches during playback of live HLS
    streams.
*   Add the ability to inject a HLS playlist parser through `HlsMediaSource`.
*   Fix potential `IndexOutOfBoundsException` when using `ImaMediaSource`
    ([#3334](https://github.com/google/ExoPlayer/issues/3334)).
*   Fix an issue parsing MP4 content containing non-CENC sinf boxes.
*   Fix memory leak when seeking with repeated periods.
*   Fix playback position when `ExoPlayer.prepare` is called with
    `resetPosition` set to false.
*   Ignore MP4 edit lists that seem invalid
    ([#3351](https://github.com/google/ExoPlayer/issues/3351)).
*   Add extractor flag for ignoring all MP4 edit lists
    ([#3358](https://github.com/google/ExoPlayer/issues/3358)).
*   Improve extensibility by exposing public constructors for
    `FrameworkMediaCrypto` and by making `DefaultDashChunkSource.getNextChunk`
    non-final.

### r2.5.3 (2017-09-20)

*   IMA extension: Support skipping of skippable ads on AndroidTV and other
    non-touch devices
    ([#3258](https://github.com/google/ExoPlayer/issues/3258)).
*   HLS: Fix broken WebVTT captions when PTS wraps around
    ([#2928](https://github.com/google/ExoPlayer/issues/2928)).
*   Captions: Fix issues rendering CEA-608 captions
    ([#3250](https://github.com/google/ExoPlayer/issues/3250)).
*   Workaround broken AAC decoders on Galaxy S6
    ([#3249](https://github.com/google/ExoPlayer/issues/3249)).
*   Caching: Fix infinite loop when cache eviction fails
    ([#3260](https://github.com/google/ExoPlayer/issues/3260)).
*   Caching: Force use of BouncyCastle on JellyBean to fix decryption issue
    ([#2755](https://github.com/google/ExoPlayer/issues/2755)).

### r2.5.2 (2017-09-11)

*   IMA extension: Fix issue where ad playback could end prematurely for some
    content types ([#3180](https://github.com/google/ExoPlayer/issues/3180)).
*   RTMP extension: Fix SIGABRT on fast RTMP stream restart
    ([#3156](https://github.com/google/ExoPlayer/issues/3156)).
*   UI: Allow app to manually specify ad markers
    ([#3184](https://github.com/google/ExoPlayer/issues/3184)).
*   DASH: Expose segment indices to subclasses of DefaultDashChunkSource
    ([#3037](https://github.com/google/ExoPlayer/issues/3037)).
*   Captions: Added robustness against malformed WebVTT captions
    ([#3228](https://github.com/google/ExoPlayer/issues/3228)).
*   DRM: Support forcing a specific license URL.
*   Fix playback error when seeking in media loaded through content:// URIs
    ([#3216](https://github.com/google/ExoPlayer/issues/3216)).
*   Fix issue playing MP4s in which the last atom specifies a size of zero
    ([#3191](https://github.com/google/ExoPlayer/issues/3191)).
*   Workaround playback failures on some Xiaomi devices
    ([#3171](https://github.com/google/ExoPlayer/issues/3171)).
*   Workaround SIGSEGV issue on some devices when setting and swapping surface
    for secure playbacks
    ([#3215](https://github.com/google/ExoPlayer/issues/3215)).
*   Workaround for Nexus 7 issue when swapping output surface
    ([#3236](https://github.com/google/ExoPlayer/issues/3236)).
*   Workaround for SimpleExoPlayerView's surface not being hidden properly
    ([#3160](https://github.com/google/ExoPlayer/issues/3160)).

### r2.5.1 (2017-08-08)

*   Fix an issue that could cause the reported playback position to stop
    advancing in some cases.
*   Fix an issue where a Surface could be released whilst still in use by the
    player.

### r2.5.0 (2017-08-07)

*   IMA extension: Wraps the Google Interactive Media Ads (IMA) SDK to provide
    an easy and seamless way of incorporating display ads into ExoPlayer
    playbacks. You can read more about the IMA extension
    [here](https://medium.com/google-exoplayer/playing-ads-with-exoplayer-and-ima-868dfd767ea).
*   MediaSession extension: Provides an easy way to connect ExoPlayer with
    MediaSessionCompat in the Android Support Library.
*   RTMP extension: An extension for playing streams over RTMP.
*   Build: Made it easier for application developers to depend on a local
    checkout of ExoPlayer. You can learn how to do this
    [here](https://medium.com/google-exoplayer/howto-2-depend-on-a-local-checkout-of-exoplayer-bcd7f8531720).
*   Core playback improvements:
    *   Eliminated re-buffering when changing audio and text track selections
        during playback of progressive streams
        ([#2926](https://github.com/google/ExoPlayer/issues/2926)).
    *   New DynamicConcatenatingMediaSource class to support playback of dynamic
        playlists.
    *   New ExoPlayer.setRepeatMode method for dynamic toggling of repeat mode
        during playback. Use of setRepeatMode should be preferred to
        LoopingMediaSource for most looping use cases. You can read more about
        setRepeatMode
        [here](https://medium.com/google-exoplayer/repeat-modes-in-exoplayer-19dd85f036d3).
    *   Eliminated jank when switching video playback from one Surface to
        another on API level 23+ for unencrypted content, and on devices that
        support the EGL_EXT_protected_content OpenGL extension for protected
        content ([#677](https://github.com/google/ExoPlayer/issues/677)).
    *   Enabled ExoPlayer instantiation on background threads without Loopers.
        Events from such players are delivered on the application's main thread.
*   HLS improvements:
    *   Optimized adaptive switches for playlists that specify the
        EXT-X-INDEPENDENT-SEGMENTS tag.
    *   Optimized in-buffer seeking
        ([#551](https://github.com/google/ExoPlayer/issues/551)).
    *   Eliminated re-buffering when changing audio and text track selections
        during playback, provided the new selection does not require switching
        to different renditions
        ([#2718](https://github.com/google/ExoPlayer/issues/2718)).
    *   Exposed all media playlist tags in ExoPlayer's MediaPlaylist object.
*   DASH: Support for seamless switching across streams in different
    AdaptationSet elements
    ([#2431](https://github.com/google/ExoPlayer/issues/2431)).
*   DRM: Support for additional crypto schemes (cbc1, cbcs and cens) on API
    level 24+ ([#1989](https://github.com/google/ExoPlayer/issues/1989)).
*   Captions: Initial support for SSA/ASS subtitles
    ([#889](https://github.com/google/ExoPlayer/issues/889)).
*   AndroidTV: Fixed issue where tunneled video playback would not start on some
    devices ([#2985](https://github.com/google/ExoPlayer/issues/2985)).
*   MPEG-TS: Fixed segmentation issue when parsing H262
    ([#2891](https://github.com/google/ExoPlayer/issues/2891)).
*   Cronet extension: Support for a user-defined fallback if Cronet library is
    not present.
*   Fix buffer too small IllegalStateException issue affecting some composite
    media playbacks ([#2900](https://github.com/google/ExoPlayer/issues/2900)).
*   Misc bugfixes.

### r2.4.4 (2017-07-19)

*   HLS/MPEG-TS: Some initial optimizations of MPEG-TS extractor performance
    ([#3040](https://github.com/google/ExoPlayer/issues/3040)).
*   HLS: Fix propagation of format identifier for CEA-608
    ([#3033](https://github.com/google/ExoPlayer/issues/3033)).
*   HLS: Detect playlist stuck and reset conditions
    ([#2872](https://github.com/google/ExoPlayer/issues/2872)).
*   Video: Fix video dimension reporting on some devices
    ([#3007](https://github.com/google/ExoPlayer/issues/3007)).

### r2.4.3 (2017-06-30)

*   Audio: Workaround custom audio decoders misreporting their maximum supported
    channel counts ([#2940](https://github.com/google/ExoPlayer/issues/2940)).
*   Audio: Workaround for broken MediaTek raw decoder on some devices
    ([#2873](https://github.com/google/ExoPlayer/issues/2873)).
*   Captions: Fix TTML captions appearing at the top of the screen
    ([#2953](https://github.com/google/ExoPlayer/issues/2953)).
*   Captions: Fix handling of some DVB subtitles
    ([#2957](https://github.com/google/ExoPlayer/issues/2957)).
*   Track selection: Fix setSelectionOverride(index, tracks, null)
    ([#2988](https://github.com/google/ExoPlayer/issues/2988)).
*   GVR extension: Add support for mono input
    ([#2710](https://github.com/google/ExoPlayer/issues/2710)).
*   FLAC extension: Fix failing build
    ([#2977](https://github.com/google/ExoPlayer/pull/2977)).
*   Misc bugfixes.

### r2.4.2 (2017-06-06)

*   Stability: Work around Nexus 10 reboot when playing certain content
    ([#2806](https://github.com/google/ExoPlayer/issues/2806)).
*   MP3: Correctly treat MP3s with INFO headers as constant bitrate
    ([#2895](https://github.com/google/ExoPlayer/issues/2895)).
*   HLS: Use average rather than peak bandwidth when available
    ([#2863](https://github.com/google/ExoPlayer/issues/2863)).
*   SmoothStreaming: Fix timeline for live streams
    ([#2760](https://github.com/google/ExoPlayer/issues/2760)).
*   UI: Fix DefaultTimeBar invalidation
    ([#2871](https://github.com/google/ExoPlayer/issues/2871)).
*   Misc bugfixes.

### r2.4.1 (2017-05-23)

*   Stability: Avoid OutOfMemoryError in extractors when parsing malformed media
    ([#2780](https://github.com/google/ExoPlayer/issues/2780)).
*   Stability: Avoid native crash on Galaxy Nexus. Avoid unnecessarily large
    codec input buffer allocations on all devices
    ([#2607](https://github.com/google/ExoPlayer/issues/2607)).
*   Variable speed playback: Fix interpolation for rate/pitch adjustment
    ([#2774](https://github.com/google/ExoPlayer/issues/2774)).
*   HLS: Include EXT-X-DATERANGE tags in HlsMediaPlaylist.
*   HLS: Don't expose CEA-608 track if CLOSED-CAPTIONS=NONE
    ([#2743](https://github.com/google/ExoPlayer/issues/2743)).
*   HLS: Correctly propagate errors loading the media playlist
    ([#2623](https://github.com/google/ExoPlayer/issues/2623)).
*   UI: DefaultTimeBar enhancements and bug fixes
    ([#2740](https://github.com/google/ExoPlayer/issues/2740)).
*   Ogg: Fix failure to play some Ogg files
    ([#2782](https://github.com/google/ExoPlayer/issues/2782)).
*   Captions: Don't select text tack with no language by default.
*   Captions: TTML positioning fixes
    ([#2824](https://github.com/google/ExoPlayer/issues/2824)).
*   Misc bugfixes.

### r2.4.0 (2017-04-25)

*   New modular library structure. You can read more about depending on
    individual library modules
    [here](https://medium.com/google-exoplayer/exoplayers-new-modular-structure-a916c0874907).
*   Variable speed playback support on API level 16+. You can read more about
    changing the playback speed
    [here](https://medium.com/google-exoplayer/variable-speed-playback-with-exoplayer-e6e6a71e0343)
    ([#26](https://github.com/google/ExoPlayer/issues/26)).
*   New time bar view, including support for displaying ad break markers.
*   Support DVB subtitles in MPEG-TS and MKV.
*   Support adaptive playback for audio only DASH, HLS and SmoothStreaming
    ([#1975](https://github.com/google/ExoPlayer/issues/1975)).
*   Support for setting extractor flags on DefaultExtractorsFactory
    ([#2657](https://github.com/google/ExoPlayer/issues/2657)).
*   Support injecting custom renderers into SimpleExoPlayer using a new
    RenderersFactory interface.
*   Correctly set ExoPlayer's internal thread priority to
    `THREAD_PRIORITY_AUDIO`.
*   TX3G: Support styling and positioning.
*   FLV:
    *   Support MP3 in FLV.
    *   Skip unhandled metadata rather than failing
        ([#2634](https://github.com/google/ExoPlayer/issues/2634)).
    *   Fix potential OutOfMemory errors.
*   ID3: Better handle malformed ID3 data
    ([#2604](https://github.com/google/ExoPlayer/issues/2604),
    [#2663](https://github.com/google/ExoPlayer/issues/2663)).
*   FFmpeg extension: Fixed build instructions
    ([#2561](https://github.com/google/ExoPlayer/issues/2561)).
*   VP9 extension: Reduced binary size.
*   FLAC extension: Enabled 64 bit targets.
*   Misc bugfixes.

### r2.3.1 (2017-03-23)

*   Fix NPE enabling WebVTT subtitles in DASH streams
    ([#2596](https://github.com/google/ExoPlayer/issues/2596)).
*   Fix skipping to keyframes when MediaCodecVideoRenderer is enabled but
    without a Surface
    ([#2575](https://github.com/google/ExoPlayer/issues/2575)).
*   Minor fix for CEA-708 decoder
    ([#2595](https://github.com/google/ExoPlayer/issues/2595)).

### r2.3.0 (2017-03-16)

*   GVR extension: Wraps the Google VR Audio SDK to provide spatial audio
    rendering. You can read more about the GVR extension
    [here](https://medium.com/google-exoplayer/spatial-audio-with-exoplayer-and-gvr-cecb00e9da5f#.xdjebjd7g).
*   DASH improvements:
    *   Support embedded CEA-608 closed captions
        ([#2362](https://github.com/google/ExoPlayer/issues/2362)).
    *   Support embedded EMSG events
        ([#2176](https://github.com/google/ExoPlayer/issues/2176)).
    *   Support mspr:pro manifest element
        ([#2386](https://github.com/google/ExoPlayer/issues/2386)).
    *   Correct handling of empty segment indices at the start of live events
        ([#1865](https://github.com/google/ExoPlayer/issues/1865)).
*   HLS improvements:
    *   Respect initial track selection
        ([#2353](https://github.com/google/ExoPlayer/issues/2353)).
    *   Reduced frequency of media playlist requests when playback position is
        close to the live edge
        ([#2548](https://github.com/google/ExoPlayer/issues/2548)).
    *   Exposed the master playlist through ExoPlayer.getCurrentManifest()
        ([#2537](https://github.com/google/ExoPlayer/issues/2537)).
    *   Support CLOSED-CAPTIONS #EXT-X-MEDIA type
        ([#341](https://github.com/google/ExoPlayer/issues/341)).
    *   Fixed handling of negative values in #EXT-X-SUPPORT
        ([#2495](https://github.com/google/ExoPlayer/issues/2495)).
    *   Fixed potential endless buffering state for streams with WebVTT
        subtitles ([#2424](https://github.com/google/ExoPlayer/issues/2424)).
*   MPEG-TS improvements:
    *   Support for multiple programs.
    *   Support for multiple closed captions and caption service descriptors
        ([#2161](https://github.com/google/ExoPlayer/issues/2161)).
*   MP3: Add `FLAG_ENABLE_CONSTANT_BITRATE_SEEKING` extractor option to enable
    constant bitrate seeking in MP3 files that would otherwise be unseekable
    ([#2445](https://github.com/google/ExoPlayer/issues/2445)).
*   ID3: Better handle malformed ID3 data
    ([#2486](https://github.com/google/ExoPlayer/issues/2486)).
*   Track selection: Added maxVideoBitrate parameter to DefaultTrackSelector.
*   DRM: Add support for CENC ClearKey on API level 21+
    ([#2361](https://github.com/google/ExoPlayer/issues/2361)).
*   DRM: Support dynamic setting of key request headers
    ([#1924](https://github.com/google/ExoPlayer/issues/1924)).
*   SmoothStreaming: Fixed handling of start_time placeholder
    ([#2447](https://github.com/google/ExoPlayer/issues/2447)).
*   FLAC extension: Fix proguard configuration
    ([#2427](https://github.com/google/ExoPlayer/issues/2427)).
*   Misc bugfixes.

### r2.2.0 (2017-01-30)

*   Demo app: Automatic recovery from BehindLiveWindowException, plus improved
    handling of pausing and resuming live streams
    ([#2344](https://github.com/google/ExoPlayer/issues/2344)).
*   AndroidTV: Added Support for tunneled video playback
    ([#1688](https://github.com/google/ExoPlayer/issues/1688)).
*   DRM: Renamed StreamingDrmSessionManager to DefaultDrmSessionManager and
    added support for using offline licenses
    ([#876](https://github.com/google/ExoPlayer/issues/876)).
*   DRM: Introduce OfflineLicenseHelper to help with offline license
    acquisition, renewal and release.
*   UI: Updated player control assets. Added vector drawables for use on API
    level 21 and above.
*   UI: Made player control seek bar work correctly with key events if focusable
    ([#2278](https://github.com/google/ExoPlayer/issues/2278)).
*   HLS: Improved support for streams that use EXT-X-DISCONTINUITY without
    EXT-X-DISCONTINUITY-SEQUENCE
    ([#1789](https://github.com/google/ExoPlayer/issues/1789)).
*   HLS: Support for EXT-X-START tag
    ([#1544](https://github.com/google/ExoPlayer/issues/1544)).
*   HLS: Check #EXTM3U header is present when parsing the playlist. Fail
    gracefully if not
    ([#2301](https://github.com/google/ExoPlayer/issues/2301)).
*   HLS: Fix memory leak
    ([#2319](https://github.com/google/ExoPlayer/issues/2319)).
*   HLS: Fix non-seamless first adaptation where master playlist omits
    resolution tags ([#2096](https://github.com/google/ExoPlayer/issues/2096)).
*   HLS: Fix handling of WebVTT subtitle renditions with non-standard segment
    file extensions ([#2025](https://github.com/google/ExoPlayer/issues/2025)
    and [#2355](https://github.com/google/ExoPlayer/issues/2355)).
*   HLS: Better handle inconsistent HLS playlist update
    ([#2249](https://github.com/google/ExoPlayer/issues/2249)).
*   DASH: Don't overflow when dealing with large segment numbers
    ([#2311](https://github.com/google/ExoPlayer/issues/2311)).
*   DASH: Fix propagation of language from the manifest
    ([#2335](https://github.com/google/ExoPlayer/issues/2335)).
*   SmoothStreaming: Work around "Offset to sample data was negative" failures
    ([#2292](https://github.com/google/ExoPlayer/issues/2292),
    [#2101](https://github.com/google/ExoPlayer/issues/2101) and
    [#1152](https://github.com/google/ExoPlayer/issues/1152)).
*   MP3/ID3: Added support for parsing Chapter and URL link frames
    ([#2316](https://github.com/google/ExoPlayer/issues/2316)).
*   MP3/ID3: Handle ID3 frames that end with empty text field
    ([#2309](https://github.com/google/ExoPlayer/issues/2309)).
*   Added ClippingMediaSource for playing clipped portions of media
    ([#1988](https://github.com/google/ExoPlayer/issues/1988)).
*   Added convenience methods to query whether the current window is dynamic and
    seekable ([#2320](https://github.com/google/ExoPlayer/issues/2320)).
*   Support setting of default headers on HttpDataSource.Factory implementations
    ([#2166](https://github.com/google/ExoPlayer/issues/2166)).
*   Fixed cache failures when using an encrypted cache content index.
*   Fix visual artifacts when switching output surface
    ([#2093](https://github.com/google/ExoPlayer/issues/2093)).
*   Fix gradle + proguard configurations.
*   Fix player position when replacing the MediaSource
    ([#2369](https://github.com/google/ExoPlayer/issues/2369)).
*   Misc bug fixes, including
    [#2330](https://github.com/google/ExoPlayer/issues/2330),
    [#2269](https://github.com/google/ExoPlayer/issues/2269),
    [#2252](https://github.com/google/ExoPlayer/issues/2252),
    [#2264](https://github.com/google/ExoPlayer/issues/2264) and
    [#2290](https://github.com/google/ExoPlayer/issues/2290).

### r2.1.1 (2016-12-20)

*   Fix some subtitle types (e.g. WebVTT) being displayed out of sync
    ([#2208](https://github.com/google/ExoPlayer/issues/2208)).
*   Fix incorrect position reporting for on-demand HLS media that includes
    EXT-X-PROGRAM-DATE-TIME tags
    ([#2224](https://github.com/google/ExoPlayer/issues/2224)).
*   Fix issue where playbacks could get stuck in the initial buffering state if
    over 1MB of data needs to be read to initialize the playback.

### r2.1.0 (2016-12-14)

*   HLS: Support for seeking in live streams
    ([#87](https://github.com/google/ExoPlayer/issues/87)).
*   HLS: Improved support:
    *   Support for EXT-X-PROGRAM-DATE-TIME
        ([#747](https://github.com/google/ExoPlayer/issues/747)).
    *   Improved handling of sample timestamps and their alignment across
        variants and renditions.
    *   Fix issue that could cause playbacks to get stuck in an endless initial
        buffering state.
    *   Correctly propagate BehindLiveWindowException instead of
        IndexOutOfBoundsException exception
        ([#1695](https://github.com/google/ExoPlayer/issues/1695)).
*   MP3/MP4: Support for ID3 metadata, including embedded album art
    ([#979](https://github.com/google/ExoPlayer/issues/979)).
*   Improved customization of UI components. You can read about customization of
    ExoPlayer's UI components
    [here](https://medium.com/google-exoplayer/customizing-exoplayers-ui-components-728cf55ee07a#.9ewjg7avi).
*   Robustness improvements when handling MediaSource timeline changes and
    MediaPeriod transitions.
*   CEA-608: Support for caption styling and positioning.
*   MPEG-TS: Improved support:
    *   Support injection of custom TS payload readers.
    *   Support injection of custom section payload readers.
    *   Support SCTE-35 splice information messages.
    *   Support multiple table sections in a single PSI section.
    *   Fix NullPointerException when an unsupported stream type is encountered
        ([#2149](https://github.com/google/ExoPlayer/issues/2149)).
    *   Avoid failure when expected ID3 header not found
        ([#1966](https://github.com/google/ExoPlayer/issues/1966)).
*   Improvements to the upstream cache package.
    *   Support caching of media segments for DASH, HLS and SmoothStreaming.
        Note that caching of manifest and playlist files is still not supported
        in the (normal) case where the corresponding responses are compressed.
    *   Support caching for ExtractorMediaSource based playbacks.
*   Improved flexibility of SimpleExoPlayer
    ([#2102](https://github.com/google/ExoPlayer/issues/2102)).
*   Fix issue where only the audio of a video would play due to capability
    detection issues ([#2007](https://github.com/google/ExoPlayer/issues/2007),
    [#2034](https://github.com/google/ExoPlayer/issues/2034) and
    [#2157](https://github.com/google/ExoPlayer/issues/2157)).
*   Fix issues that could cause ExtractorMediaSource based playbacks to get
    stuck buffering ([#1962](https://github.com/google/ExoPlayer/issues/1962)).
*   Correctly set SimpleExoPlayerView surface aspect ratio when an active player
    is attached ([#2077](https://github.com/google/ExoPlayer/issues/2077)).
*   OGG: Fix playback of short OGG files
    ([#1976](https://github.com/google/ExoPlayer/issues/1976)).
*   MP4: Support `.mp3` tracks
    ([#2066](https://github.com/google/ExoPlayer/issues/2066)).
*   SubRip: Don't fail playbacks if SubRip file contains negative timestamps
    ([#2145](https://github.com/google/ExoPlayer/issues/2145)).
*   Misc bugfixes.

### r2.0.4 (2016-10-20)

*   Fix crash on Jellybean devices when using playback controls
    ([#1965](https://github.com/google/ExoPlayer/issues/1965)).

### r2.0.3 (2016-10-17)

*   Fixed NullPointerException in ExtractorMediaSource
    ([#1914](https://github.com/google/ExoPlayer/issues/1914)).
*   Fixed NullPointerException in HlsMediaPeriod
    ([#1907](https://github.com/google/ExoPlayer/issues/1907)).
*   Fixed memory leak in PlaybackControlView
    ([#1908](https://github.com/google/ExoPlayer/issues/1908)).
*   Fixed strict mode violation when using
    SimpleExoPlayer.setVideoPlayerTextureView().
*   Fixed L3 Widevine provisioning
    ([#1925](https://github.com/google/ExoPlayer/issues/1925)).
*   Fixed hiding of controls with use_controller="false"
    ([#1919](https://github.com/google/ExoPlayer/issues/1919)).
*   Improvements to Cronet network stack extension.
*   Misc bug fixes.

### r2.0.2 (2016-10-06)

*   Fixes for MergingMediaSource and sideloaded subtitles.
    ([#1882](https://github.com/google/ExoPlayer/issues/1882),
    [#1854](https://github.com/google/ExoPlayer/issues/1854),
    [#1900](https://github.com/google/ExoPlayer/issues/1900)).
*   Reduced effect of application code leaking player references
    ([#1855](https://github.com/google/ExoPlayer/issues/1855)).
*   Initial support for fragmented MP4 in HLS.
*   Misc bug fixes and minor features.

### r2.0.1 (2016-09-30)

*   Fix playback of short duration content
    ([#1837](https://github.com/google/ExoPlayer/issues/1837)).
*   Fix MergingMediaSource preparation issue
    ([#1853](https://github.com/google/ExoPlayer/issues/1853)).
*   Fix live stream buffering (out of memory) issue
    ([#1825](https://github.com/google/ExoPlayer/issues/1825)).

### r2.0.0 (2016-09-14)

ExoPlayer 2.x is a major iteration of the library. It includes significant API
and architectural changes, new features and many bug fixes. You can read about
some of the motivations behind ExoPlayer 2.x
[here](https://medium.com/google-exoplayer/exoplayer-2-x-why-what-and-when-74fd9cb139#.am7h8nytm).

*   Root package name changed to `com.google.android.exoplayer2`. The library
    structure and class names have also been sanitized. Read more
    [here](https://medium.com/google-exoplayer/exoplayer-2-x-new-package-and-class-names-ef8e1d9ba96f#.lv8sd4nez).
*   Key architectural changes:
    *   Late binding between rendering and media source components. Allows the
        same rendering components to be re-used from one playback to another.
        Enables features such as gapless playback through playlists and DASH
        multi-period support.
    *   Improved track selection design. More details can be found
        [here](https://medium.com/google-exoplayer/exoplayer-2-x-track-selection-2b62ff712cc9#.n00zo76b6).
    *   LoadControl now used to control buffering and loading across all
        playback types.
    *   Media source components given additional structure. A new MediaSource
        class has been introduced. MediaSources expose Timelines that describe
        the media they expose, and can consist of multiple MediaPeriods. This
        enables features such as seeking in live playbacks and DASH multi-period
        support.
    *   Responsibility for loading the initial DASH/SmoothStreaming/HLS manifest
        is promoted to the corresponding MediaSource components and is no longer
        the application's responsibility.
    *   Higher level abstractions such as SimpleExoPlayer have been added to the
        library. These make the library easier to use for common use cases. The
        demo app is halved in size as a result, whilst at the same time gaining
        more functionality. Read more
        [here](https://medium.com/google-exoplayer/exoplayer-2-x-improved-demo-app-d97171aaaaa1).
    *   Enhanced library support for implementing audio extensions. Read more
        [here](https://medium.com/google-exoplayer/exoplayer-2-x-new-audio-features-cfb26c2883a#.ua75vu4s3).
    *   Format and MediaFormat are replaced by a single Format class.
*   Key new features:
    *   Playlist support. Includes support for gapless playback between playlist
        items and consistent application of LoadControl and TrackSelector
        policies when transitioning between items
        ([#1270](https://github.com/google/ExoPlayer/issues/1270)).
    *   Seeking in live playbacks for DASH and SmoothStreaming
        ([#291](https://github.com/google/ExoPlayer/issues/291)).
    *   DASH multi-period support
        ([#557](https://github.com/google/ExoPlayer/issues/557)).
    *   MediaSource composition allows MediaSources to be concatenated into a
        playlist, merged and looped. Read more
        [here](https://medium.com/google-exoplayer/exoplayer-2-x-mediasource-composition-6c285fcbca1f#.zfha8qupz).
    *   Looping support (see above)
        ([#490](https://github.com/google/ExoPlayer/issues/490)).
    *   Ability to query information about all tracks in a piece of media
        (including those not supported by the device)
        ([#1121](https://github.com/google/ExoPlayer/issues/1121)).
    *   Improved player controls.
    *   Support for PSSH in fMP4 moof atoms
        ([#1143](https://github.com/google/ExoPlayer/issues/1143)).
    *   Support for Opus in Ogg
        ([#1447](https://github.com/google/ExoPlayer/issues/1447)).
    *   CacheDataSource support for standalone media file playbacks (mp3, mp4
        etc).
    *   FFMPEG extension (for audio only).
*   Key bug fixes:
    *   Removed unnecessary secondary requests when playing standalone media
        files ([#1041](https://github.com/google/ExoPlayer/issues/1041)).
    *   Fixed playback of video only (i.e. no audio) live streams
        ([#758](https://github.com/google/ExoPlayer/issues/758)).
    *   Fixed silent failure when media buffer is too small
        ([#583](https://github.com/google/ExoPlayer/issues/583)).
    *   Suppressed "Sending message to a Handler on a dead thread" warnings
        ([#426](https://github.com/google/ExoPlayer/issues/426)).

# Legacy release notes

Note: Since ExoPlayer V1 is still being maintained alongside V2, there is some
overlap between these notes and the notes above. r2.0.0 followed from r1.5.11,
and hence it can be assumed that all changes in r1.5.11 and earlier are included
in all V2 releases. This cannot be assumed for changes in r1.5.12 and later,
however it can be assumed that all such changes are included in the most recent
V2 release.

### r1.5.16

*   VP9 extension: Reduced binary size.
*   FLAC extension: Enabled 64 bit targets and fixed proguard config.
*   Misc bugfixes.

### r1.5.15

*   SmoothStreaming: Fixed handling of start_time placeholder
    ([#2447](https://github.com/google/ExoPlayer/issues/2447)).
*   Misc bugfixes.

### r1.5.14

*   Fixed cache failures when using an encrypted cache content index.
*   SmoothStreaming: Work around "Offset to sample data was negative" failures
    ([#2292](https://github.com/google/ExoPlayer/issues/2292),
    [#2101](https://github.com/google/ExoPlayer/issues/2101) and
    [#1152](https://github.com/google/ExoPlayer/issues/1152)).

### r1.5.13

*   Improvements to the upstream cache package.
*   MP4: Support `.mp3` tracks
    ([#2066](https://github.com/google/ExoPlayer/issues/2066)).
*   SubRip: Don't fail playbacks if SubRip file contains negative timestamps
    ([#2145](https://github.com/google/ExoPlayer/issues/2145)).
*   MPEG-TS: Avoid failure when expected ID3 header not found
    ([#1966](https://github.com/google/ExoPlayer/issues/1966)).
*   Misc bugfixes.

### r1.5.12

*   Improvements to Cronet network stack extension.
*   Fix bug in demo app introduced in r1.5.11 that caused L3 Widevine
    provisioning requests to fail.
*   Misc bugfixes.

### r1.5.11

*   Cronet network stack extension.
*   HLS: Fix propagation of language for alternative audio renditions
    ([#1784](https://github.com/google/ExoPlayer/issues/1784)).
*   WebM: Support for subsample encryption.
*   ID3: Fix EOS detection for 2-byte encodings
    ([#1774](https://github.com/google/ExoPlayer/issues/1774)).
*   MPEG-TS: Support multiple tracks of the same type.
*   MPEG-TS: Work toward robust handling of stream corruption.
*   Fix ContentDataSource failures triggered by garbage collector
    ([#1759](https://github.com/google/ExoPlayer/issues/1759)).

### r1.5.10

*   HLS: Stability fixes.
*   MP4: Support for stz2 Atoms.
*   Enable 4K format selection on Sony AndroidTV + nVidia SHIELD.
*   TX3G caption fixes.

### r1.5.9

*   MP4: Fixed incorrect sniffing in some cases (#1523).
*   MP4: Improved file compatibility (#1567).
*   ID3: Support for TIT2 and APIC frames.
*   Fixed querying of platform decoders on some devices.
*   Misc bug fixes.

### r1.5.8

*   HLS: Fix handling of HTTP redirects.
*   Audio: Minor adjustment to improve A/V sync.
*   OGG: Support FLAC in OGG.
*   TTML: Support regions.
*   WAV/PCM: Support 8, 24 and 32-bit WAV and PCM audio.
*   Misc bug fixes and performance optimizations.

### r1.5.7

*   OGG: Support added for OGG.
*   FLAC: Support for FLAC extraction and playback (via an extension).
*   HLS: Multiple audio track support (via Renditions).
*   FMP4: Support multiple tracks in fragmented MP4 (not applicable to
    DASH/SmoothStreaming).
*   WAV: Support for 16-bit WAV files.
*   MKV: Support non-square pixel formats.
*   Misc bug fixes.

### r1.5.6

*   MP3: Fix mono streams playing at 2x speed on some MediaTek based devices
    (#801).
*   MP3: Fix playback of some streams when stream length is unknown.
*   ID3: Support multiple frames of the same type in a single tag.
*   CEA-608: Correctly handle repeated control characters, fixing an issue in
    which captions would immediately disappear.
*   AVC3: Fix decoder failures on some MediaTek devices in the case where the
    first buffer fed to the decoder does not start with SPS/PPS NAL units.
*   Misc bug fixes.

### r1.5.5

*   DASH: Enable MP4 embedded WebVTT playback (#1185)
*   HLS: Fix handling of extended ID3 tags in MPEG-TS (#1181)
*   MP3: Fix incorrect position calculation in VBRI header (#1197)
*   Fix issue seeking backward using SingleSampleSource (#1193)

### r1.5.4

*   HLS: Support for variant selection and WebVtt subtitles.
*   MP4: Support for embedded WebVtt.
*   Improved device compatibility.
*   Fix for resource leak (Issue #1066).
*   Misc bug fixes + minor features.

### r1.5.3

*   Support for FLV (without seeking).
*   MP4: Fix for playback of media containing basic edit lists.
*   QuickTime: Fix parsing of QuickTime style audio sample entry.
*   HLS: Add H262 support for devices that have an H262 decoder.
*   Allow AudioTrack PlaybackParams (e.g. speed/pitch) on API level 23+.
*   Correctly detect 4K displays on API level 23+.
*   Misc bug fixes.

### r1.5.2

*   MPEG-TS/HLS: Fix frame drops playing H265 video.
*   SmoothStreaming: Fix parsing of ProtectionHeader.

### r1.5.1

*   Enable smooth frame release by default.
*   Added OkHttpDataSource extension.
*   AndroidTV: Correctly detect 4K display size on Bravia devices.
*   FMP4: Handle non-sample data in mdat boxes.
*   TTML: Fix parsing of some colors on Jellybean.
*   SmoothStreaming: Ignore tfdt boxes.
*   Misc bug fixes.

### r1.5.0

*   Multi-track support.
*   DASH: Limited support for multi-period manifests.
*   HLS: Smoother format adaptation.
*   HLS: Support for MP3 media segments.
*   TTML: Support for most embedded TTML styling.
*   WebVTT: Enhanced positioning support.
*   Initial playback tests.
*   Misc bug fixes.

### r1.4.2

*   Implemented automatic format detection for regular container formats.
*   Added UdpDataSource for connecting to multicast streams.
*   Improved robustness for MP4 playbacks.
*   Misc bug fixes.

### r1.4.1

*   HLS: Fix premature playback failures that could occur in some cases.

### r1.4.0

*   Support for extracting Matroska streams (implemented by WebmExtractor).
*   Support for tx3g captions in MP4 streams.
*   Support for H.265 in MPEG-TS streams on supported devices.
*   HLS: Added support for MPEG audio (e.g. MP3) in TS media segments.
*   HLS: Improved robustness against missing chunks and variants.
*   MP4: Added support for embedded MPEG audio (e.g. MP3).
*   TTML: Improved handling of whitespace.
*   DASH: Support Mpd.Location element.
*   Add option to TsExtractor to allow non-IDR keyframes.
*   Added MulticastDataSource for connecting to multicast streams.
*   (WorkInProgress) - First steps to supporting seeking in DASH DVR window.
*   (WorkInProgress) - First steps to supporting styled + positioned subtitles.
*   Misc bug fixes.

### r1.3.3

*   HLS: Fix failure when playing HLS AAC streams.
*   Misc bug fixes.

### r1.3.2

*   DataSource improvements: `DefaultUriDataSource` now handles http://,
    https://, file://, asset:// and content:// URIs automatically. It also
    handles file:///android_asset/* URIs, and file paths like /path/to/media.mp4
    where the scheme is omitted.
*   HLS: Fix for some ID3 events being dropped.
*   HLS: Correctly handle 0x0 and floating point RESOLUTION tags.
*   Mp3Extractor: robustness improvements.

### r1.3.1

*   No notes provided.
