# Release notes

### 1.0.1 (2023-04-18)

This release corresponds to the
[ExoPlayer 2.18.6 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.6).

*   Core library:
    *   Reset target live stream override when seeking to default position
        ([#11051](https://github.com/google/ExoPlayer/pull/11051)).
    *   Fix bug where empty sample streams in the media could cause playback to
        be stuck.
*   Session:
    *   Fix bug where multiple identical queue items published by a legacy
        `MediaSessionCompat` result in an exception in `MediaController`
        ([#290](https://github.com/androidx/media/issues/290)).
    *   Add missing forwarding of `MediaSession.broadcastCustomCommand` to the
        legacy `MediaControllerCompat.Callback.onSessionEvent`
        ([#293](https://github.com/androidx/media/issues/293)).
    *   Fix bug where calling `MediaSession.setPlayer` doesn't update the
        available commands.
    *   Fix issue that `TrackSelectionOverride` instances sent from a
        `MediaController` are ignored if they reference a group with
        `Format.metadata`
        ([#296](https://github.com/androidx/media/issues/296)).
    *   Fix issue where `Player.COMMAND_GET_CURRENT_MEDIA_ITEM` needs to be
        available to access metadata via the legacy `MediaSessionCompat`.
    *   Fix issue where `MediaSession` instances on a background thread cause
        crashes when used in `MediaSessionService`
        ([#318](https://github.com/androidx/media/issues/318)).
    *   Fix issue where a media button receiver was declared by the library
        without the app having intended this
        ([#314](https://github.com/androidx/media/issues/314)).
*   DASH:
    *   Fix handling of empty segment timelines
        ([#11014](https://github.com/google/ExoPlayer/issues/11014)).
*   RTSP:
    *   Retry with TCP if RTSP Setup with UDP fails with RTSP Error 461
        UnsupportedTransport
        ([#11069](https://github.com/google/ExoPlayer/issues/11069)).

### 1.0.0 (2023-03-22)

This release corresponds to the
[ExoPlayer 2.18.5 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.5).

There are no changes since 1.0.0-rc02.

### 1.0.0-rc02 (2023-03-02)

This release corresponds to the
[ExoPlayer 2.18.4 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.4).

*   Core library:
    *   Fix network type detection on API 33
        ([#10970](https://github.com/google/ExoPlayer/issues/10970)).
    *   Fix `NullPointerException` when calling `ExoPlayer.isTunnelingEnabled`
        ([#10977](https://github.com/google/ExoPlayer/issues/10977)).
*   Downloads:
    *   Make the maximum difference of the start time of two segments to be
        merged configurable in `SegmentDownloader` and subclasses
        ([#248](https://github.com/androidx/media/pull/248)).
*   Audio:
    *   Fix broken gapless MP3 playback on Samsung devices
        ([#8594](https://github.com/google/ExoPlayer/issues/8594)).
    *   Fix bug where playback speeds set immediately after disabling audio may
        be overridden by a previous speed change
        ([#10882](https://github.com/google/ExoPlayer/issues/10882)).
*   Video:
    *   Map HEVC HDR10 format to `HEVCProfileMain10HDR10` instead of
        `HEVCProfileMain10`.
    *   Add workaround for a device issue on Chromecast with Google TV and
        Lenovo M10 FHD Plus that causes 60fps AVC streams to be marked as
        unsupported
        ([#10898](https://github.com/google/ExoPlayer/issues/10898)).
    *   Fix frame release performance issues when playing media with a frame
        rate far higher than the screen refresh rate.
*   Cast:
    *   Fix transient `STATE_IDLE` when transitioning between media items
        ([#245](https://github.com/androidx/media/issues/245)).
*   RTSP:
    *   Catch the IllegalArgumentException thrown in parsing of invalid RTSP
        Describe response messages
        ([#10971](https://github.com/google/ExoPlayer/issues/10971)).
*   Session:
    *   Fix a bug where notification play/pause button doesn't update with
        player state ([#192](https://github.com/androidx/media/issues/192)).
*   IMA extension:
    *   Fix a bug which prevented DAI streams without any ads from starting
        because the first (and in the case without ads the only) `LOADED` event
        wasn't received.

### 1.0.0-rc01 (2023-02-16)

This release corresponds to the
[ExoPlayer 2.18.3 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.3).

*   Core library:
    *   Tweak the renderer's decoder ordering logic to uphold the
        `MediaCodecSelector`'s preferences, even if a decoder reports it may not
        be able to play the media performantly. For example with default
        selector, hardware decoder with only functional support will be
        preferred over software decoder that fully supports the format
        ([#10604](https://github.com/google/ExoPlayer/issues/10604)).
    *   Add `ExoPlayer.Builder.setPlaybackLooper` that sets a pre-existing
        playback thread for a new ExoPlayer instance.
    *   Allow download manager helpers to be cleared
        ([#10776](https://github.com/google/ExoPlayer/issues/10776)).
    *   Add parameter to `BasePlayer.seekTo` to also indicate the command used
        for seeking.
    *   Use theme when loading drawables on API 21+
        ([#220](https://github.com/androidx/media/issues/220)).
    *   Add `ConcatenatingMediaSource2` that allows combining multiple media
        items into a single window
        ([#247](https://github.com/androidx/media/issues/247)).
*   Extractors:
    *   Throw a `ParserException` instead of a `NullPointerException` if the
        sample table (stbl) is missing a required sample description (stsd) when
        parsing trak atoms.
    *   Correctly skip samples when seeking directly to a sync frame in fMP4
        ([#10941](https://github.com/google/ExoPlayer/issues/10941)).
*   Audio:
    *   Use the compressed audio format bitrate to calculate the min buffer size
        for `AudioTrack` in direct playbacks (passthrough).
*   Text:
    *   Fix `TextRenderer` passing an invalid (negative) index to
        `Subtitle.getEventTime` if a subtitle file contains no cues.
    *   SubRip: Add support for UTF-16 files if they start with a byte order
        mark.
*   Metadata:
    *   Parse multiple null-separated values from ID3 frames, as permitted by
        ID3 v2.4.
    *   Add `MediaMetadata.mediaType` to denote the type of content or the type
        of folder described by the metadata.
    *   Add `MediaMetadata.isBrowsable` as a replacement for
        `MediaMetadata.folderType`. The folder type will be deprecated in the
        next release.
*   DASH:
    *   Add full parsing for image adaptation sets, including tile counts
        ([#3752](https://github.com/google/ExoPlayer/issues/3752)).
*   UI:
    *   Fix the deprecated
        `PlayerView.setControllerVisibilityListener(PlayerControlView.VisibilityListener)`
        to ensure visibility changes are passed to the registered listener
        ([#229](https://github.com/androidx/media/issues/229)).
    *   Fix the ordering of the center player controls in `PlayerView` when
        using a right-to-left (RTL) layout
        ([#227](https://github.com/androidx/media/issues/227)).
*   Session:
    *   Add abstract `SimpleBasePlayer` to help implement the `Player` interface
        for custom players.
    *   Add helper method to convert platform session token to Media3
        `SessionToken` ([#171](https://github.com/androidx/media/issues/171)).
    *   Use `onMediaMetadataChanged` to trigger updates of the platform media
        session ([#219](https://github.com/androidx/media/issues/219)).
    *   Add the media session as an argument of `getMediaButtons()` of the
        `DefaultMediaNotificationProvider` and use immutable lists for clarity
        ([#216](https://github.com/androidx/media/issues/216)).
    *   Add `onSetMediaItems` callback listener to provide means to modify/set
        `MediaItem` list, starting index and position by session before setting
        onto Player ([#156](https://github.com/androidx/media/issues/156)).
    *   Avoid double tap detection for non-Bluetooth media button events
        ([#233](https://github.com/androidx/media/issues/233)).
    *   Make `QueueTimeline` more robust in case of a shady legacy session state
        ([#241](https://github.com/androidx/media/issues/241)).
*   Cast extension:
    *   Bump Cast SDK version to 21.2.0.
*   IMA extension:
    *   Map `PLAYER_STATE_LOADING` to `STATE_BUFFERING`
        ([#245](\(https://github.com/androidx/media/issues/245\)).
*   IMA extension
    *   Remove player listener of the `ImaServerSideAdInsertionMediaSource` on
        the application thread to avoid threading issues.
    *   Add a property `focusSkipButtonWhenAvailable` to the
        `ImaServerSideAdInsertionMediaSource.AdsLoader.Builder` to request
        focusing the skip button on TV devices and set it to true by default.
    *   Add a method `focusSkipButton()` to the
        `ImaServerSideAdInsertionMediaSource.AdsLoader` to programmatically
        request to focus the skip button.
    *   Fix a bug which prevented playback from starting for a DAI stream
        without any ads.
    *   Bump IMA SDK version to 3.29.0.
*   Demo app:
    *   Request notification permission for download notifications at runtime
        ([#10884](https://github.com/google/ExoPlayer/issues/10884)).

### 1.0.0-beta03 (2022-11-22)

This release corresponds to the
[ExoPlayer 2.18.2 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.2).

*   Core library:
    *   Add `ExoPlayer.isTunnelingEnabled` to check if tunneling is enabled for
        the currently selected tracks
        ([#2518](https://github.com/google/ExoPlayer/issues/2518)).
    *   Add `WrappingMediaSource` to simplify wrapping a single `MediaSource`
        ([#7279](https://github.com/google/ExoPlayer/issues/7279)).
    *   Discard back buffer before playback gets stuck due to insufficient
        available memory.
    *   Close the Tracing "doSomeWork" block when offload is enabled.
    *   Fix session tracking problem with fast seeks in `PlaybackStatsListener`
        ([#180](https://github.com/androidx/media/issues/180)).
    *   Send missing `onMediaItemTransition` callback when calling `seekToNext`
        or `seekToPrevious` in a single-item playlist
        ([#10667](https://github.com/google/ExoPlayer/issues/10667)).
    *   Add `Player.getSurfaceSize` that returns the size of the surface on
        which the video is rendered.
    *   Fix bug where removing listeners during the player release can cause an
        `IllegalStateException`
        ([#10758](https://github.com/google/ExoPlayer/issues/10758)).
*   Build:
    *   Enforce minimum `compileSdkVersion` to avoid compilation errors
        ([#10684](https://github.com/google/ExoPlayer/issues/10684)).
    *   Avoid publishing block when included in another gradle build.
*   Track selection:
    *   Prefer other tracks to Dolby Vision if display does not support it.
        ([#8944](https://github.com/google/ExoPlayer/issues/8944)).
*   Downloads:
    *   Fix potential infinite loop in `ProgressiveDownloader` caused by
        simultaneous download and playback with the same `PriorityTaskManager`
        ([#10570](https://github.com/google/ExoPlayer/pull/10570)).
    *   Make download notification appear immediately
        ([#183](https://github.com/androidx/media/pull/183)).
    *   Limit parallel download removals to 1 to avoid excessive thread creation
        ([#10458](https://github.com/google/ExoPlayer/issues/10458)).
*   Video:
    *   Try alternative decoder for Dolby Vision if display does not support it.
        ([#9794](https://github.com/google/ExoPlayer/issues/9794)).
*   Audio:
    *   Use `SingleThreadExecutor` for releasing `AudioTrack` instances to avoid
        OutOfMemory errors when releasing multiple players at the same time
        ([#10057](https://github.com/google/ExoPlayer/issues/10057)).
    *   Adds `AudioOffloadListener.onExperimentalOffloadedPlayback` for the
        AudioTrack offload state.
        ([#134](https://github.com/androidx/media/issues/134)).
    *   Make `AudioTrackBufferSizeProvider` a public interface.
    *   Add `ExoPlayer.setPreferredAudioDevice` to set the preferred audio
        output device ([#135](https://github.com/androidx/media/issues/135)).
    *   Rename `androidx.media3.exoplayer.audio.AudioProcessor` to
        `androidx.media3.common.audio.AudioProcessor`.
    *   Map 8-channel and 12-channel audio to the 7.1 and 7.1.4 channel masks
        respectively on all Android versions
        ([#10701](https://github.com/google/ExoPlayer/issues/10701)).
*   Metadata:
    *   `MetadataRenderer` can now be configured to render metadata as soon as
        they are available. Create an instance with
        `MetadataRenderer(MetadataOutput, Looper, MetadataDecoderFactory,
        boolean)` to specify whether the renderer will output metadata early or
        in sync with the player position.
*   DRM:
    *   Work around a bug in the Android 13 ClearKey implementation that returns
        a non-empty but invalid license URL.
    *   Fix `setMediaDrmSession failed: session not opened` error when switching
        between DRM schemes in a playlist (e.g. Widevine to ClearKey).
*   Text:
    *   CEA-608: Ensure service switch commands on field 2 are handled correctly
        ([#10666](https://github.com/google/ExoPlayer/issues/10666)).
*   DASH:
    *   Parse `EventStream.presentationTimeOffset` from manifests
        ([#10460](https://github.com/google/ExoPlayer/issues/10460)).
*   UI:
    *   Use current overrides of the player as preset in
        `TrackSelectionDialogBuilder`
        ([#10429](https://github.com/google/ExoPlayer/issues/10429)).
*   Session:
    *   Ensure commands are always executed in the correct order even if some
        require asynchronous resolution
        ([#85](https://github.com/androidx/media/issues/85)).
    *   Add `DefaultMediaNotificationProvider.Builder` to build
        `DefaultMediaNotificationProvider` instances. The builder can configure
        the notification ID, the notification channel ID and the notification
        channel name used by the provider. Also, add method
        `DefaultMediaNotificationProvider.setSmallIcon(int)` to set the
        notifications small icon.
        ([#104](https://github.com/androidx/media/issues/104)).
    *   Ensure commands sent before `MediaController.release()` are not dropped
        ([#99](https://github.com/androidx/media/issues/99)).
    *   `SimpleBitmapLoader` can load bitmap from `file://` URIs
        ([#108](https://github.com/androidx/media/issues/108)).
    *   Fix assertion that prevents `MediaController` to seek over an ad in a
        period ([#122](https://github.com/androidx/media/issues/122)).
    *   When playback ends, the `MediaSessionService` is stopped from the
        foreground and a notification is shown to restart playback of the last
        played media item
        ([#112](https://github.com/androidx/media/issues/112)).
    *   Don't start a foreground service with a pending intent for pause
        ([#167](https://github.com/androidx/media/issues/167)).
    *   Manually hide the 'badge' associated with the notification created by
        `DefaultNotificationProvider` on API 26 and API 27 (the badge is
        automatically hidden on API 28+)
        ([#131](https://github.com/androidx/media/issues/131)).
    *   Fix bug where a second binder connection from a legacy MediaSession to a
        Media3 MediaController causes IllegalStateExceptions
        ([#49](https://github.com/androidx/media/issues/49)).
*   RTSP:
    *   Add H263 fragmented packet handling
        ([#119](https://github.com/androidx/media/pull/119)).
    *   Add support for MP4A-LATM
        ([#162](https://github.com/androidx/media/pull/162)).
*   IMA:
    *   Add timeout for loading ad information to handle cases where the IMA SDK
        gets stuck loading an ad
        ([#10510](https://github.com/google/ExoPlayer/issues/10510)).
    *   Prevent skipping mid-roll ads when seeking to the end of the content
        ([#10685](https://github.com/google/ExoPlayer/issues/10685)).
    *   Correctly calculate window duration for live streams with server-side
        inserted ads, for example IMA DAI
        ([#10764](https://github.com/google/ExoPlayer/issues/10764)).
*   FFmpeg extension:
    *   Add newly required flags to link FFmpeg libraries with NDK 23.1.7779620
        and above ([#9933](https://github.com/google/ExoPlayer/issues/9933)).
*   AV1 extension:
    *   Update CMake version to avoid incompatibilities with the latest Android
        Studio releases
        ([#9933](https://github.com/google/ExoPlayer/issues/9933)).
*   Cast extension:
    *   Implement `getDeviceInfo()` to be able to identify `CastPlayer` when
        controlling playback with a `MediaController`
        ([#142](https://github.com/androidx/media/issues/142)).
*   Transformer:
    *   Add muxer watchdog timer to detect when generating an output sample is
        too slow.
*   Remove deprecated symbols:
    *   Remove `Transformer.Builder.setOutputMimeType(String)`. This feature has
        been removed. The MIME type will always be MP4 when the default muxer is
        used.

### 1.0.0-beta02 (2022-07-21)

This release corresponds to the
[ExoPlayer 2.18.1 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.1).

*   Core library:
    *   Ensure that changing the `ShuffleOrder` with `ExoPlayer.setShuffleOrder`
        results in a call to `Player.Listener#onTimelineChanged` with
        `reason=Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED`
        ([#9889](https://github.com/google/ExoPlayer/issues/9889)).
    *   For progressive media, only include selected tracks in buffered position
        ([#10361](https://github.com/google/ExoPlayer/issues/10361)).
    *   Allow custom logger for all ExoPlayer log output
        ([#9752](https://github.com/google/ExoPlayer/issues/9752)).
    *   Fix implementation of `setDataSourceFactory` in
        `DefaultMediaSourceFactory`, which was non-functional in some cases
        ([#116](https://github.com/androidx/media/issues/116)).
*   Extractors:
    *   Fix parsing of H265 short term reference picture sets
        ([#10316](https://github.com/google/ExoPlayer/issues/10316)).
    *   Fix parsing of bitrates from `esds` boxes
        ([#10381](https://github.com/google/ExoPlayer/issues/10381)).
*   DASH:
    *   Parse ClearKey license URL from manifests
        ([#10246](https://github.com/google/ExoPlayer/issues/10246)).
*   UI:
    *   Ensure TalkBack announces the currently active speed option in the
        playback controls menu
        ([#10298](https://github.com/google/ExoPlayer/issues/10298)).
*   RTSP:
    *   Add VP8 fragmented packet handling
        ([#110](https://github.com/androidx/media/pull/110)).
    *   Support frames/fragments in VP9
        ([#115](https://github.com/androidx/media/pull/115)).
*   Leanback extension:
    *   Listen to `playWhenReady` changes in `LeanbackAdapter`
        ([10420](https://github.com/google/ExoPlayer/issues/10420)).
*   Cast:
    *   Use the `MediaItem` that has been passed to the playlist methods as
        `Window.mediaItem` in `CastTimeline`
        ([#25](https://github.com/androidx/media/issues/25),
        [#8212](https://github.com/google/ExoPlayer/issues/8212)).
    *   Support `Player.getMetadata()` and `Listener.onMediaMetadataChanged()`
        with `CastPlayer` ([#25](https://github.com/androidx/media/issues/25)).

### 1.0.0-beta01 (2022-06-16)

This release corresponds to the
[ExoPlayer 2.18.0 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.0).

*   Core library:
    *   Enable support for Android platform diagnostics via
        `MediaMetricsManager`. ExoPlayer will forward playback events and
        performance data to the platform, which helps to provide system
        performance and debugging information on the device. This data may also
        be collected by Google
        [if sharing usage and diagnostics data is enabled](https://support.google.com/accounts/answer/6078260)
        by the user of the device. Apps can opt-out of contributing to platform
        diagnostics for ExoPlayer with
        `ExoPlayer.Builder.setUsePlatformDiagnostics(false)`.
    *   Fix bug that tracks are reset too often when using `MergingMediaSource`,
        for example when side-loading subtitles and changing the selected
        subtitle mid-playback
        ([#10248](https://github.com/google/ExoPlayer/issues/10248)).
    *   Stop detecting 5G-NSA network type on API 29 and 30. These playbacks
        will assume a 4G network.
    *   Disallow passing `null` to
        `MediaSource.Factory.setDrmSessionManagerProvider` and
        `MediaSource.Factory.setLoadErrorHandlingPolicy`. Instances of
        `DefaultDrmSessionManagerProvider` and `DefaultLoadErrorHandlingPolicy`
        can be passed explicitly if required.
    *   Add `MediaItem.RequestMetadata` to represent metadata needed to play
        media when the exact `LocalConfiguration` is not known. Also remove
        `MediaMetadata.mediaUrl` as this is now included in `RequestMetadata`.
    *   Add `Player.Command.COMMAND_SET_MEDIA_ITEM` to enable players to allow
        setting a single item.
*   Track selection:
    *   Flatten `TrackSelectionOverrides` class into `TrackSelectionParameters`,
        and promote `TrackSelectionOverride` to a top level class.
    *   Rename `TracksInfo` to `Tracks` and `TracksInfo.TrackGroupInfo` to
        `Tracks.Group`. `Player.getCurrentTracksInfo` and
        `Player.Listener.onTracksInfoChanged` have also been renamed to
        `Player.getCurrentTracks` and `Player.Listener.onTracksChanged`. This
        includes 'un-deprecating' the `Player.Listener.onTracksChanged` method
        name, but with different parameter types.
    *   Change `DefaultTrackSelector.buildUponParameters` and
        `DefaultTrackSelector.Parameters.buildUpon` to return
        `DefaultTrackSelector.Parameters.Builder` instead of the deprecated
        `DefaultTrackSelector.ParametersBuilder`.
    *   Add
        `DefaultTrackSelector.Parameters.constrainAudioChannelCountToDeviceCapabilities`
        which is enabled by default. When enabled, the `DefaultTrackSelector`
        will prefer audio tracks whose channel count does not exceed the device
        output capabilities. On handheld devices, the `DefaultTrackSelector`
        will prefer stereo/mono over multichannel audio formats, unless the
        multichannel format can be
        [Spatialized](https://developer.android.com/reference/android/media/Spatializer)
        (Android 12L+) or is a Dolby surround sound format. In addition, on
        devices that support audio spatialization, the `DefaultTrackSelector`
        will monitor for changes in the
        [Spatializer properties](https://developer.android.com/reference/android/media/Spatializer.OnSpatializerStateChangedListener)
        and trigger a new track selection upon these. Devices with a
        `television`
        [UI mode](https://developer.android.com/guide/topics/resources/providing-resources#UiModeQualifier)
        are excluded from these constraints and the format with the highest
        channel count will be preferred. To enable this feature, the
        `DefaultTrackSelector` instance must be constructed with a `Context`.
*   Video:
    *   Rename `DummySurface` to `PlaceholderSurface`.
    *   Add AV1 support to the `MediaCodecVideoRenderer.getCodecMaxInputSize`.
*   Audio:
    *   Use LG AC3 audio decoder advertising non-standard MIME type.
    *   Change the return type of `AudioAttributes.getAudioAttributesV21()` from
        `android.media.AudioAttributes` to a new `AudioAttributesV21` wrapper
        class, to prevent slow ART verification on API < 21.
    *   Query the platform (API 29+) or assume the audio encoding channel count
        for audio passthrough when the format audio channel count is unset,
        which occurs with HLS chunkless preparation
        ([#10204](https://github.com/google/ExoPlayer/issues/10204)).
    *   Configure `AudioTrack` with channel mask
        `AudioFormat.CHANNEL_OUT_7POINT1POINT4` if the decoder outputs 12
        channel PCM audio
        ([#10322](#https://github.com/google/ExoPlayer/pull/10322)).
*   DRM
    *   Ensure the DRM session is always correctly updated when seeking
        immediately after a format change
        ([#10274](https://github.com/google/ExoPlayer/issues/10274)).
*   Text:
    *   Change `Player.getCurrentCues()` to return `CueGroup` instead of
        `List<Cue>`.
    *   SSA: Support `OutlineColour` style setting when `BorderStyle == 3` (i.e.
        `OutlineColour` sets the background of the cue)
        ([#8435](https://github.com/google/ExoPlayer/issues/8435)).
    *   CEA-708: Parse data into multiple service blocks and ignore blocks not
        associated with the currently selected service number.
    *   Remove `RawCcExtractor`, which was only used to handle a Google-internal
        subtitle format.
*   Extractors:
    *   Add support for AVI
        ([#2092](https://github.com/google/ExoPlayer/issues/2092)).
    *   Matroska: Parse `DiscardPadding` for Opus tracks.
    *   MP4: Parse bitrates from `esds` boxes.
    *   Ogg: Allow duplicate Opus ID and comment headers
        ([#10038](https://github.com/google/ExoPlayer/issues/10038)).
*   UI:
    *   Fix delivery of events to `OnClickListener`s set on `PlayerView`, in the
        case that `useController=false`
        ([#9605](https://github.com/google/ExoPlayer/issues/9605)). Also fix
        delivery of events to `OnLongClickListener` for all view configurations.
    *   Fix incorrectly treating a sequence of touch events that exit the bounds
        of `PlayerView` before `ACTION_UP` as a click
        ([#9861](https://github.com/google/ExoPlayer/issues/9861)).
    *   Fix `PlayerView` accessibility issue where tapping might toggle playback
        rather than hiding the controls
        ([#8627](https://github.com/google/ExoPlayer/issues/8627)).
    *   Rewrite `TrackSelectionView` and `TrackSelectionDialogBuilder` to work
        with the `Player` interface rather than `ExoPlayer`. This allows the
        views to be used with other `Player` implementations, and removes the
        dependency from the UI module to the ExoPlayer module. This is a
        breaking change.
    *   Don't show forced text tracks in the `PlayerView` track selector, and
        keep a suitable forced text track selected if "None" is selected
        ([#9432](https://github.com/google/ExoPlayer/issues/9432)).
*   DASH:
    *   Parse channel count from DTS `AudioChannelConfiguration` elements. This
        re-enables audio passthrough for DTS streams
        ([#10159](https://github.com/google/ExoPlayer/issues/10159)).
    *   Disallow passing `null` to
        `DashMediaSource.Factory.setCompositeSequenceableLoaderFactory`.
        Instances of `DefaultCompositeSequenceableLoaderFactory` can be passed
        explicitly if required.
*   HLS:
    *   Fallback to chunkful preparation if the playlist CODECS attribute does
        not contain the audio codec
        ([#10065](https://github.com/google/ExoPlayer/issues/10065)).
    *   Disallow passing `null` to
        `HlsMediaSource.Factory.setCompositeSequenceableLoaderFactory`,
        `HlsMediaSource.Factory.setPlaylistParserFactory`, and
        `HlsMediaSource.Factory.setPlaylistTrackerFactory`. Instances of
        `DefaultCompositeSequenceableLoaderFactory`,
        `DefaultHlsPlaylistParserFactory`, or a reference to
        `DefaultHlsPlaylistTracker.FACTORY` can be passed explicitly if
        required.
*   Smooth Streaming:
    *   Disallow passing `null` to
        `SsMediaSource.Factory.setCompositeSequenceableLoaderFactory`. Instances
        of `DefaultCompositeSequenceableLoaderFactory` can be passed explicitly
        if required.
*   RTSP:
    *   Add RTP reader for H263
        ([#63](https://github.com/androidx/media/pull/63)).
    *   Add RTP reader for MPEG4
        ([#35](https://github.com/androidx/media/pull/35)).
    *   Add RTP reader for HEVC
        ([#36](https://github.com/androidx/media/pull/36)).
    *   Add RTP reader for AMR. Currently only mono-channel, non-interleaved AMR
        streams are supported. Compound AMR RTP payload is not supported.
        ([#46](https://github.com/androidx/media/pull/46))
    *   Add RTP reader for VP8
        ([#47](https://github.com/androidx/media/pull/47)).
    *   Add RTP reader for WAV
        ([#56](https://github.com/androidx/media/pull/56)).
    *   Fix RTSP basic authorization header.
        ([#9544](https://github.com/google/ExoPlayer/issues/9544)).
    *   Stop checking mandatory SDP fields as ExoPlayer doesn't need them
        ([#10049](https://github.com/google/ExoPlayer/issues/10049)).
    *   Throw checked exception when parsing RTSP timing
        ([#10165](https://github.com/google/ExoPlayer/issues/10165)).
    *   Add RTP reader for VP9
        ([#47](https://github.com/androidx/media/pull/64)).
    *   Add RTP reader for OPUS
        ([#53](https://github.com/androidx/media/pull/53)).
*   Session:
    *   Replace `MediaSession.MediaItemFiller` with
        `MediaSession.Callback.onAddMediaItems` to allow asynchronous resolution
        of requests.
    *   Support `setMediaItems(s)` methods when `MediaController` connects to a
        legacy media session.
    *   Remove `MediaController.setMediaUri` and
        `MediaSession.Callback.onSetMediaUri`. The same functionality can be
        achieved by using `MediaController.setMediaItem` and
        `MediaSession.Callback.onAddMediaItems`.
    *   Forward legacy `MediaController` calls to play media to
        `MediaSession.Callback.onAddMediaItems` instead of `onSetMediaUri`.
    *   Add `MediaNotification.Provider` and `DefaultMediaNotificationProvider`
        to provide customization of the notification.
    *   Add `BitmapLoader` and `SimpleBitmapLoader` for downloading artwork
        images.
    *   Add `MediaSession.setCustomLayout()` to provide backwards compatibility
        with the legacy session.
    *   Add `MediaSession.setSessionExtras()` to provide feature parity with
        legacy session.
    *   Rename `MediaSession.MediaSessionCallback` to `MediaSession.Callback`,
        `MediaLibrarySession.MediaLibrarySessionCallback` to
        `MediaLibrarySession.Callback` and
        `MediaSession.Builder.setSessionCallback` to `setCallback`.
    *   Fix NPE in `MediaControllerImplLegacy`
        ([#59](https://github.com/androidx/media/pull/59)).
    *   Update session position info on timeline
        change([#51](https://github.com/androidx/media/issues/51)).
    *   Fix NPE in `MediaControllerImplBase` after releasing controller
        ([#74](https://github.com/androidx/media/issues/74)).
    *   Fix `IndexOutOfBoundsException` when setting less media items than in
        the current playlist
        ([#86](https://github.com/androidx/media/issues/86)).
*   Ad playback / IMA:
    *   Decrease ad polling rate from every 100ms to every 200ms, to line up
        with Media Rating Council (MRC) recommendations.
*   FFmpeg extension:
    *   Update CMake version to `3.21.0+` to avoid a CMake bug causing
        AndroidStudio's gradle sync to fail
        ([#9933](https://github.com/google/ExoPlayer/issues/9933)).
*   Remove deprecated symbols:
    *   Remove `Player.Listener.onTracksChanged(TrackGroupArray,
        TrackSelectionArray)`. Use `Player.Listener.onTracksChanged(Tracks)`
        instead.
    *   Remove `Player.getCurrentTrackGroups` and
        `Player.getCurrentTrackSelections`. Use `Player.getCurrentTracks`
        instead. You can also continue to use `ExoPlayer.getCurrentTrackGroups`
        and `ExoPlayer.getCurrentTrackSelections`, although these methods remain
        deprecated.
    *   Remove `DownloadHelper`
        `DEFAULT_TRACK_SELECTOR_PARAMETERS_WITHOUT_VIEWPORT` and
        `DEFAULT_TRACK_SELECTOR_PARAMETERS` constants. Use
        `getDefaultTrackSelectorParameters(Context)` instead when possible, and
        `DEFAULT_TRACK_SELECTOR_PARAMETERS_WITHOUT_CONTEXT` otherwise.
    *   Remove constructor `DefaultTrackSelector(ExoTrackSelection.Factory)`.
        Use `DefaultTrackSelector(Context, ExoTrackSelection.Factory)` instead.
    *   Remove `Transformer.Builder.setContext`. The `Context` should be passed
        to the `Transformer.Builder` constructor instead.

### 1.0.0-alpha03 (2022-03-14)

This release corresponds to the
[ExoPlayer 2.17.1 release](https://github.com/google/ExoPlayer/releases/tag/r2.17.1).

*   Audio:
    *   Fix error checking audio capabilities for Dolby Atmos (E-AC3-JOC) in
        HLS.
*   Extractors:
    *   FMP4: Fix issue where emsg sample metadata could be output in the wrong
        order for streams containing both v0 and v1 emsg atoms
        ([#9996](https://github.com/google/ExoPlayer/issues/9996)).
*   Text:
    *   Fix the interaction of `SingleSampleMediaSource.Factory.setTrackId` and
        `MediaItem.SubtitleConfiguration.Builder.setId` to prioritise the
        `SubtitleConfiguration` field and fall back to the `Factory` value if
        it's not set
        ([#10016](https://github.com/google/ExoPlayer/issues/10016)).
*   Ad playback:
    *   Fix audio underruns between ad periods in live HLS SSAI streams.

### 1.0.0-alpha02 (2022-03-02)

This release corresponds to the
[ExoPlayer 2.17.0 release](https://github.com/google/ExoPlayer/releases/tag/r2.17.0).

*   Core Library:
    *   Add protected method `DefaultRenderersFactory.getCodecAdapterFactory()`
        so that subclasses of `DefaultRenderersFactory` that override
        `buildVideoRenderers()` or `buildAudioRenderers()` can access the codec
        adapter factory and pass it to `MediaCodecRenderer` instances they
        create.
    *   Propagate ICY header fields `name` and `genre` to
        `MediaMetadata.station` and `MediaMetadata.genre` respectively so that
        they reach the app via `Player.Listener.onMediaMetadataChanged()`
        ([#9677](https://github.com/google/ExoPlayer/issues/9677)).
    *   Remove null keys from `DefaultHttpDataSource#getResponseHeaders`.
    *   Sleep and retry when creating a `MediaCodec` instance fails. This works
        around an issue that occurs on some devices when switching a surface
        from a secure codec to another codec
        ([#8696](https://github.com/google/ExoPlayer/issues/8696)).
    *   Add `MediaCodecAdapter.getMetrics()` to allow users obtain metrics data
        from `MediaCodec`
        ([#9766](https://github.com/google/ExoPlayer/issues/9766)).
    *   Fix Maven dependency resolution
        ([#8353](https://github.com/google/ExoPlayer/issues/8353)).
    *   Disable automatic speed adjustment for live streams that neither have
        low-latency features nor a user request setting the speed
        ([#9329](https://github.com/google/ExoPlayer/issues/9329)).
    *   Rename `DecoderCounters#inputBufferCount` to `queuedInputBufferCount`.
    *   Make `SimpleExoPlayer.renderers` private. Renderers can be accessed via
        `ExoPlayer.getRenderer`.
    *   Updated some `AnalyticsListener.EventFlags` constant values to match
        values in `Player.EventFlags`.
    *   Split `AnalyticsCollector` into an interface and default implementation
        to allow it to be stripped by R8 if an app doesn't need it.
*   Track selection:
    *   Support preferred video role flags in track selection
        ([#9402](https://github.com/google/ExoPlayer/issues/9402)).
    *   Update video track selection logic to take preferred MIME types and role
        flags into account when selecting multiple video tracks for adaptation
        ([#9519](https://github.com/google/ExoPlayer/issues/9519)).
    *   Update video and audio track selection logic to only choose formats for
        adaptive selections that have the same level of decoder and hardware
        support ([#9565](https://github.com/google/ExoPlayer/issues/9565)).
    *   Update video track selection logic to prefer more efficient codecs if
        multiple codecs are supported by primary, hardware-accelerated decoders
        ([#4835](https://github.com/google/ExoPlayer/issues/4835)).
    *   Prefer audio content preferences (for example, the "default" audio track
        or a track matching the system locale language) over technical track
        selection constraints (for example, preferred MIME type, or maximum
        channel count).
    *   Fix track selection issue where overriding one track group did not
        disable other track groups of the same type
        ([#9675](https://github.com/google/ExoPlayer/issues/9675)).
    *   Fix track selection issue where a mixture of non-empty and empty track
        overrides is not applied correctly
        ([#9649](https://github.com/google/ExoPlayer/issues/9649)).
    *   Prohibit duplicate `TrackGroup`s in a `TrackGroupArray`. `TrackGroup`s
        can always be made distinguishable by setting an `id` in the
        `TrackGroup` constructor. This fixes a crash when resuming playback
        after backgrounding the app with an active track override
        ([#9718](https://github.com/google/ExoPlayer/issues/9718)).
    *   Amend logic in `AdaptiveTrackSelection` to allow a quality increase
        under sufficient network bandwidth even if playback is very close to the
        live edge ([#9784](https://github.com/google/ExoPlayer/issues/9784)).
*   Video:
    *   Fix decoder fallback logic for Dolby Vision to use a compatible
        H264/H265 decoder if needed.
*   Audio:
    *   Fix decoder fallback logic for Dolby Atmos (E-AC3-JOC) to use a
        compatible E-AC3 decoder if needed.
    *   Change `AudioCapabilities` APIs to require passing explicitly
        `AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES` instead of `null`.
    *   Allow customization of the `AudioTrack` buffer size calculation by
        injecting an `AudioTrackBufferSizeProvider` to `DefaultAudioSink`
        ([#8891](https://github.com/google/ExoPlayer/issues/8891)).
    *   Retry `AudioTrack` creation if the requested buffer size was > 1MB
        ([#9712](https://github.com/google/ExoPlayer/issues/9712)).
*   Extractors:
    *   WAV: Add support for RF64 streams
        ([#9543](https://github.com/google/ExoPlayer/issues/9543)).
    *   Fix incorrect parsing of H.265 SPS NAL units
        ([#9719](https://github.com/google/ExoPlayer/issues/9719)).
    *   Parse Vorbis Comments (including `METADATA_BLOCK_PICTURE`) in Ogg Opus
        and Ogg Vorbis files.
*   Text:
    *   Add a `MediaItem.SubtitleConfiguration.id` field which is propagated to
        the `Format.id` field of the subtitle track created from the
        configuration
        ([#9673](https://github.com/google/ExoPlayer/issues/9673)).
    *   Add basic support for WebVTT subtitles in Matroska containers
        ([#9886](https://github.com/google/ExoPlayer/issues/9886)).
    *   Prevent `Cea708Decoder` from reading more than the declared size of a
        service block.
*   DRM:
    *   Remove `playbackLooper` from `DrmSessionManager.(pre)acquireSession`.
        When a `DrmSessionManager` is used by an app in a custom `MediaSource`,
        the `playbackLooper` needs to be passed to `DrmSessionManager.setPlayer`
        instead.
*   Ad playback / IMA:
    *   Add support for
        [IMA Dynamic Ad Insertion (DAI)](https://support.google.com/admanager/answer/6147120)
        ([#8213](https://github.com/google/ExoPlayer/issues/8213)).
    *   Add a method to `AdPlaybackState` to allow resetting an ad group so that
        it can be played again
        ([#9615](https://github.com/google/ExoPlayer/issues/9615)).
    *   Enforce playback speed of 1.0 during ad playback
        ([#9018](https://github.com/google/ExoPlayer/issues/9018)).
    *   Fix issue where an ad group that failed to load caused an immediate
        playback reset
        ([#9929](https://github.com/google/ExoPlayer/issues/9929)).
*   UI:
    *   Fix the color of the numbers in `StyledPlayerView` rewind and
        fastforward buttons when using certain themes
        ([#9765](https://github.com/google/ExoPlayer/issues/9765)).
    *   Correctly translate playback speed strings
        ([#9811](https://github.com/google/ExoPlayer/issues/9811)).
*   DASH:
    *   Add parsed essential and supplemental properties to the `Representation`
        ([#9579](https://github.com/google/ExoPlayer/issues/9579)).
    *   Support the `forced-subtitle` track role
        ([#9727](https://github.com/google/ExoPlayer/issues/9727)).
    *   Stop interpreting the `main` track role as `C.SELECTION_FLAG_DEFAULT`.
    *   Fix base URL exclusion logic for manifests that do not declare the DVB
        namespace ([#9856](https://github.com/google/ExoPlayer/issues/9856)).
    *   Support relative `MPD.Location` URLs
        ([#9939](https://github.com/google/ExoPlayer/issues/9939)).
*   HLS:
    *   Correctly populate `Format.label` for audio only HLS streams
        ([#9608](https://github.com/google/ExoPlayer/issues/9608)).
    *   Use chunkless preparation by default to improve start up time. If your
        renditions contain muxed closed-caption tracks that are **not** declared
        in the master playlist, you should add them to the master playlist to be
        available for playback, or turn off chunkless preparation with
        `HlsMediaSource.Factory.setAllowChunklessPreparation(false)`.
    *   Support key-frame accurate seeking in HLS
        ([#2882](https://github.com/google/ExoPlayer/issues/2882)).
*   RTSP:
    *   Provide a client API to override the `SocketFactory` used for any server
        connection ([#9606](https://github.com/google/ExoPlayer/pull/9606)).
    *   Prefer DIGEST authentication method over BASIC if both are present
        ([#9800](https://github.com/google/ExoPlayer/issues/9800)).
    *   Handle when RTSP track timing is not available
        ([#9775](https://github.com/google/ExoPlayer/issues/9775)).
    *   Ignore invalid RTP-Info header values
        ([#9619](https://github.com/google/ExoPlayer/issues/9619)).
*   Transformer:
    *   Increase required min API version to 21.
    *   `TransformationException` is now used to describe errors that occur
        during a transformation.
    *   Add `TransformationRequest` for specifying the transformation options.
    *   Allow multiple listeners to be registered.
    *   Fix Transformer being stuck when the codec output is partially read.
    *   Fix potential NPE in `Transformer.getProgress` when releasing the muxer
        throws.
    *   Add a demo app for applying transformations.
*   MediaSession extension:
    *   By default, `MediaSessionConnector` now clears the playlist on stop.
        Apps that want the playlist to be retained can call
        `setClearMediaItemsOnStop(false)` on the connector.
*   Cast extension:
    *   Fix bug that prevented `CastPlayer` from calling `onIsPlayingChanged`
        correctly ([#9792](https://github.com/google/ExoPlayer/issues/9792)).
    *   Support audio metadata including artwork with
        `DefaultMediaItemConverter`
        ([#9663](https://github.com/google/ExoPlayer/issues/9663)).
*   FFmpeg extension:
    *   Make `build_ffmpeg.sh` depend on LLVM's bin utils instead of GNU's
        ([#9933](https://github.com/google/ExoPlayer/issues/9933)).
*   Android 12 compatibility:
    *   Upgrade the Cast extension to depend on
        `com.google.android.gms:play-services-cast-framework:20.1.0`. Earlier
        versions of `play-services-cast-framework` are not compatible with apps
        targeting Android 12, and will crash with an `IllegalArgumentException`
        when creating `PendingIntent`s
        ([#9528](https://github.com/google/ExoPlayer/issues/9528)).
*   Remove deprecated symbols:
    *   Remove `Player.EventListener`. Use `Player.Listener` instead.
    *   Remove `MediaSourceFactory.setDrmSessionManager`,
        `MediaSourceFactory.setDrmHttpDataSourceFactory`, and
        `MediaSourceFactory.setDrmUserAgent`. Use
        `MediaSourceFactory.setDrmSessionManagerProvider` instead.
    *   Remove `MediaSourceFactory.setStreamKeys`. Use
        `MediaItem.Builder.setStreamKeys` instead.
    *   Remove `MediaSourceFactory.createMediaSource(Uri)`. Use
        `MediaSourceFactory.createMediaSource(MediaItem)` instead.
    *   Remove `setTag` from `DashMediaSource`, `HlsMediaSource` and
        `SsMediaSource`. Use `MediaItem.Builder.setTag` instead.
    *   Remove `DashMediaSource.setLivePresentationDelayMs(long, boolean)`. Use
        `MediaItem.Builder.setLiveConfiguration` and
        `MediaItem.LiveConfiguration.Builder.setTargetOffsetMs` to override the
        manifest, or `DashMediaSource.setFallbackTargetLiveOffsetMs` to provide
        a fallback value.
    *   Remove `(Simple)ExoPlayer.setThrowsWhenUsingWrongThread`. Opting out of
        the thread enforcement is no longer possible.
    *   Remove `ActionFile` and `ActionFileUpgradeUtil`. Use ExoPlayer 2.16.1 or
        before to use `ActionFileUpgradeUtil` to merge legacy action files into
        `DefaultDownloadIndex`.
    *   Remove `ProgressiveMediaSource.setExtractorsFactory`. Use
        `ProgressiveMediaSource.Factory(DataSource.Factory, ExtractorsFactory)`
        constructor instead.
    *   Remove `ProgressiveMediaSource.Factory.setTag` and
        `ProgressiveMediaSource.Factory.setCustomCacheKey`. Use
        `MediaItem.Builder.setTag` and `MediaItem.Builder.setCustomCacheKey`
        instead.
    *   Remove `DefaultRenderersFactory(Context, @ExtensionRendererMode int)`
        and `DefaultRenderersFactory(Context, @ExtensionRendererMode int, long)`
        constructors. Use the `DefaultRenderersFactory(Context)` constructor,
        `DefaultRenderersFactory.setExtensionRendererMode`, and
        `DefaultRenderersFactory.setAllowedVideoJoiningTimeMs` instead.
    *   Remove all public `CronetDataSource` constructors. Use
        `CronetDataSource.Factory` instead.
*   Change the following `IntDefs` to `@Target(TYPE_USE)` only. This may break
    the compilation of usages in Kotlin, which can be fixed by moving the
    annotation to annotate the type (`Int`).
    *   `@AacAudioObjectType`
    *   `@Ac3Util.SyncFrameInfo.StreamType`
    *   `@AdLoadException.Type`
    *   `@AdtsExtractor.Flags`
    *   `@AmrExtractor.Flags`
    *   `@AspectRatioFrameLayout.ResizeMode`
    *   `@AudioFocusManager.PlayerCommand`
    *   `@AudioSink.SinkFormatSupport`
    *   `@BinarySearchSeeker.TimestampSearchResult.Type`
    *   `@BufferReplacementMode`
    *   `@C.BufferFlags`
    *   `@C.ColorRange`
    *   `@C.ColorSpace`
    *   `@C.ColorTransfer`
    *   `@C.CryptoMode`
    *   `@C.Encoding`
    *   `@C.PcmEncoding`
    *   `@C.Projection`
    *   `@C.SelectionReason`
    *   `@C.StereoMode`
    *   `@C.VideoOutputMode`
    *   `@CacheDataSource.Flags`
    *   `@CaptionStyleCompat.EdgeType`
    *   `@DataSpec.Flags`
    *   `@DataSpec.HttpMethods`
    *   `@DecoderDiscardReasons`
    *   `@DecoderReuseResult`
    *   `@DefaultAudioSink.OutputMode`
    *   `@DefaultDrmSessionManager.Mode`
    *   `@DefaultTrackSelector.SelectionEligibility`
    *   `@DefaultTsPayloadReaderFactory.Flags`
    *   `@EGLSurfaceTexture.SecureMode`
    *   `@EbmlProcessor.ElementType`
    *   `@ExoMediaDrm.KeyRequest.RequestType`
    *   `@ExtensionRendererMode`
    *   `@Extractor.ReadResult`
    *   `@FileTypes.Type`
    *   `@FlacExtractor.Flags` (in `com.google.android.exoplayer2.ext.flac`
        package)
    *   `@FlacExtractor.Flags` (in
        `com.google.android.exoplayer2.extractor.flac` package)
    *   `@FragmentedMp4Extractor.Flags`
    *   `@HlsMediaPlaylist.PlaylistType`
    *   `@HttpDataSourceException.Type`
    *   `@IllegalClippingException.Reason`
    *   `@IllegalMergeException.Reason`
    *   `@LoadErrorHandlingPolicy.FallbackType`
    *   `@MatroskaExtractor.Flags`
    *   `@Mp3Extractor.Flags`
    *   `@Mp4Extractor.Flags`
    *   `@NotificationUtil.Importance`
    *   `@PlaybackException.FieldNumber`
    *   `@PlayerNotificationManager.Priority`
    *   `@PlayerNotificationManager.Visibility`
    *   `@PlayerView.ShowBuffering`
    *   `@Renderer.State`
    *   `@RendererCapabilities.AdaptiveSupport`
    *   `@RendererCapabilities.Capabilities`
    *   `@RendererCapabilities.DecoderSupport`
    *   `@RendererCapabilities.FormatSupport`
    *   `@RendererCapabilities.HardwareAccelerationSupport`
    *   `@RendererCapabilities.TunnelingSupport`
    *   `@SampleStream.ReadDataResult`
    *   `@SampleStream.ReadFlags`
    *   `@StyledPlayerView.ShowBuffering`
    *   `@SubtitleView.ViewType`
    *   `@TextAnnotation.Position`
    *   `@TextEmphasisSpan.MarkFill`
    *   `@TextEmphasisSpan.MarkShape`
    *   `@Track.Transformation`
    *   `@TrackOutput.SampleDataPart`
    *   `@Transformer.ProgressState`
    *   `@TsExtractor.Mode`
    *   `@TsPayloadReader.Flags`
    *   `@WebvttCssStyle.FontSizeUnit`

### 1.0.0-alpha01

AndroidX Media is the new home for media support libraries, including ExoPlayer.
The first alpha contains early, functional implementations of libraries for
implementing media use cases, including:

*   ExoPlayer, an application-level media player for Android that is easy to
    customize and extend.
*   Media session functionality, for exposing and controlling playbacks. This
    new session module uses the same `Player` interface as ExoPlayer.
*   UI components for building media playback user interfaces.
*   Modules wrapping functionality in other libraries for use with ExoPlayer,
    for example, ad insertion via the IMA SDK.

ExoPlayer was previously hosted in a separate
[ExoPlayer GitHub project](https://github.com/google/ExoPlayer). In AndroidX
Media its package name is `androidx.media3.exoplayer`. We plan to continue to
maintain and release the ExoPlayer GitHub project for a while to give apps time
to migrate. AndroidX Media has replacements for all the ExoPlayer modules,
except for the legacy media2 and mediasession extensions, which are together
replaced by the new `media3-session` module. This provides direct integration
between players and media sessions without needing to use an adapter/connector
class.
