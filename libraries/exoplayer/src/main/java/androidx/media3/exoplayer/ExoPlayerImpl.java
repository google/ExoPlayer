/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer;

import static androidx.media3.common.C.TRACK_TYPE_AUDIO;
import static androidx.media3.common.C.TRACK_TYPE_CAMERA_MOTION;
import static androidx.media3.common.C.TRACK_TYPE_IMAGE;
import static androidx.media3.common.C.TRACK_TYPE_VIDEO;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.castNonNull;
import static androidx.media3.exoplayer.Renderer.MSG_SET_AUDIO_ATTRIBUTES;
import static androidx.media3.exoplayer.Renderer.MSG_SET_AUDIO_SESSION_ID;
import static androidx.media3.exoplayer.Renderer.MSG_SET_AUX_EFFECT_INFO;
import static androidx.media3.exoplayer.Renderer.MSG_SET_CAMERA_MOTION_LISTENER;
import static androidx.media3.exoplayer.Renderer.MSG_SET_CHANGE_FRAME_RATE_STRATEGY;
import static androidx.media3.exoplayer.Renderer.MSG_SET_IMAGE_OUTPUT;
import static androidx.media3.exoplayer.Renderer.MSG_SET_PREFERRED_AUDIO_DEVICE;
import static androidx.media3.exoplayer.Renderer.MSG_SET_SCALING_MODE;
import static androidx.media3.exoplayer.Renderer.MSG_SET_SKIP_SILENCE_ENABLED;
import static androidx.media3.exoplayer.Renderer.MSG_SET_VIDEO_EFFECTS;
import static androidx.media3.exoplayer.Renderer.MSG_SET_VIDEO_FRAME_METADATA_LISTENER;
import static androidx.media3.exoplayer.Renderer.MSG_SET_VIDEO_OUTPUT;
import static androidx.media3.exoplayer.Renderer.MSG_SET_VIDEO_OUTPUT_RESOLUTION;
import static androidx.media3.exoplayer.Renderer.MSG_SET_VOLUME;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaFormat;
import android.media.metrics.LogSessionId;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.AuxEffectInfo;
import androidx.media3.common.BasePlayer;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.IllegalSeekPositionException;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Metadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.common.util.ListenerSet;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.PlayerMessage.Target;
import androidx.media3.exoplayer.Renderer.MessageType;
import androidx.media3.exoplayer.analytics.AnalyticsCollector;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.analytics.DefaultAnalyticsCollector;
import androidx.media3.exoplayer.analytics.MediaMetricsListener;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.image.ImageOutput;
import androidx.media3.exoplayer.metadata.MetadataOutput;
import androidx.media3.exoplayer.source.MaskingMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.ShuffleOrder;
import androidx.media3.exoplayer.source.TimelineWithUpdatedMediaItem;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.TrackSelectionArray;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelectorResult;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.video.VideoDecoderOutputBufferRenderer;
import androidx.media3.exoplayer.video.VideoFrameMetadataListener;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.exoplayer.video.spherical.CameraMotionListener;
import androidx.media3.exoplayer.video.spherical.SphericalGLSurfaceView;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeoutException;

/** The default implementation of {@link ExoPlayer}. */
/* package */ final class ExoPlayerImpl extends BasePlayer
    implements ExoPlayer,
        ExoPlayer.AudioComponent,
        ExoPlayer.VideoComponent,
        ExoPlayer.TextComponent,
        ExoPlayer.DeviceComponent {

  static {
    MediaLibraryInfo.registerModule("media3.exoplayer");
  }

  private static final String TAG = "ExoPlayerImpl";

  /**
   * This empty track selector result can only be used for {@link PlaybackInfo#trackSelectorResult}
   * when the player does not have any track selection made (such as when player is reset, or when
   * player seeks to an unprepared period). It will not be used as result of any {@link
   * TrackSelector#selectTracks(RendererCapabilities[], TrackGroupArray, MediaPeriodId, Timeline)}
   * operation.
   */
  /* package */ final TrackSelectorResult emptyTrackSelectorResult;

  /* package */ final Commands permanentAvailableCommands;

  private final ConditionVariable constructorFinished;
  private final Context applicationContext;
  private final Player wrappingPlayer;
  private final Renderer[] renderers;
  private final TrackSelector trackSelector;
  private final HandlerWrapper playbackInfoUpdateHandler;
  private final ExoPlayerImplInternal.PlaybackInfoUpdateListener playbackInfoUpdateListener;
  private final ExoPlayerImplInternal internalPlayer;

  private final ListenerSet<Listener> listeners;
  private final CopyOnWriteArraySet<AudioOffloadListener> audioOffloadListeners;
  private final Timeline.Period period;
  private final List<MediaSourceHolderSnapshot> mediaSourceHolderSnapshots;
  private final boolean useLazyPreparation;
  private final MediaSource.Factory mediaSourceFactory;
  private final AnalyticsCollector analyticsCollector;
  private final Looper applicationLooper;
  private final BandwidthMeter bandwidthMeter;
  private final long seekBackIncrementMs;
  private final long seekForwardIncrementMs;
  private final Clock clock;
  private final ComponentListener componentListener;
  private final FrameMetadataListener frameMetadataListener;
  private final AudioBecomingNoisyManager audioBecomingNoisyManager;
  private final AudioFocusManager audioFocusManager;
  @Nullable private final StreamVolumeManager streamVolumeManager;
  private final WakeLockManager wakeLockManager;
  private final WifiLockManager wifiLockManager;
  private final long detachSurfaceTimeoutMs;
  @Nullable private AudioManager audioManager;
  private final boolean suppressPlaybackOnUnsuitableOutput;

  private @RepeatMode int repeatMode;
  private boolean shuffleModeEnabled;
  private int pendingOperationAcks;
  private @DiscontinuityReason int pendingDiscontinuityReason;
  private boolean pendingDiscontinuity;
  private @PlayWhenReadyChangeReason int pendingPlayWhenReadyChangeReason;
  private boolean foregroundMode;
  private SeekParameters seekParameters;
  private ShuffleOrder shuffleOrder;
  private boolean pauseAtEndOfMediaItems;
  private Commands availableCommands;
  private MediaMetadata mediaMetadata;
  private MediaMetadata playlistMetadata;
  @Nullable private Format videoFormat;
  @Nullable private Format audioFormat;
  @Nullable private AudioTrack keepSessionIdAudioTrack;
  @Nullable private Object videoOutput;
  @Nullable private Surface ownedSurface;
  @Nullable private SurfaceHolder surfaceHolder;
  @Nullable private SphericalGLSurfaceView sphericalGLSurfaceView;
  private boolean surfaceHolderSurfaceIsVideoOutput;
  @Nullable private TextureView textureView;
  private @C.VideoScalingMode int videoScalingMode;
  private @C.VideoChangeFrameRateStrategy int videoChangeFrameRateStrategy;
  private Size surfaceSize;
  @Nullable private DecoderCounters videoDecoderCounters;
  @Nullable private DecoderCounters audioDecoderCounters;
  private int audioSessionId;
  private AudioAttributes audioAttributes;
  private float volume;
  private boolean skipSilenceEnabled;
  private CueGroup currentCueGroup;
  @Nullable private VideoFrameMetadataListener videoFrameMetadataListener;
  @Nullable private CameraMotionListener cameraMotionListener;
  private boolean throwsWhenUsingWrongThread;
  private boolean hasNotifiedFullWrongThreadWarning;
  @Nullable private PriorityTaskManager priorityTaskManager;
  private boolean isPriorityTaskManagerRegistered;
  private boolean playerReleased;
  private DeviceInfo deviceInfo;
  private VideoSize videoSize;

  // MediaMetadata built from static (TrackGroup Format) and dynamic (onMetadata(Metadata)) metadata
  // sources.
  private MediaMetadata staticAndDynamicMediaMetadata;

  // Playback information when there is no pending seek/set source operation.
  private PlaybackInfo playbackInfo;

  // Playback information when there is a pending seek/set source operation.
  private int maskingWindowIndex;
  private int maskingPeriodIndex;
  private long maskingWindowPositionMs;

  @SuppressLint("HandlerLeak")
  @SuppressWarnings("deprecation") // Control flow for old volume commands
  public ExoPlayerImpl(ExoPlayer.Builder builder, @Nullable Player wrappingPlayer) {
    constructorFinished = new ConditionVariable();
    try {
      Log.i(
          TAG,
          "Init "
              + Integer.toHexString(System.identityHashCode(this))
              + " ["
              + MediaLibraryInfo.VERSION_SLASHY
              + "] ["
              + Util.DEVICE_DEBUG_INFO
              + "]");
      applicationContext = builder.context.getApplicationContext();
      analyticsCollector = builder.analyticsCollectorFunction.apply(builder.clock);
      priorityTaskManager = builder.priorityTaskManager;
      audioAttributes = builder.audioAttributes;
      videoScalingMode = builder.videoScalingMode;
      videoChangeFrameRateStrategy = builder.videoChangeFrameRateStrategy;
      skipSilenceEnabled = builder.skipSilenceEnabled;
      detachSurfaceTimeoutMs = builder.detachSurfaceTimeoutMs;
      componentListener = new ComponentListener();
      frameMetadataListener = new FrameMetadataListener();
      Handler eventHandler = new Handler(builder.looper);
      renderers =
          builder
              .renderersFactorySupplier
              .get()
              .createRenderers(
                  eventHandler,
                  componentListener,
                  componentListener,
                  componentListener,
                  componentListener);
      checkState(renderers.length > 0);
      this.trackSelector = builder.trackSelectorSupplier.get();
      this.mediaSourceFactory = builder.mediaSourceFactorySupplier.get();
      this.bandwidthMeter = builder.bandwidthMeterSupplier.get();
      this.useLazyPreparation = builder.useLazyPreparation;
      this.seekParameters = builder.seekParameters;
      this.seekBackIncrementMs = builder.seekBackIncrementMs;
      this.seekForwardIncrementMs = builder.seekForwardIncrementMs;
      this.pauseAtEndOfMediaItems = builder.pauseAtEndOfMediaItems;
      this.applicationLooper = builder.looper;
      this.clock = builder.clock;
      this.wrappingPlayer = wrappingPlayer == null ? this : wrappingPlayer;
      this.suppressPlaybackOnUnsuitableOutput = builder.suppressPlaybackOnUnsuitableOutput;
      listeners =
          new ListenerSet<>(
              applicationLooper,
              clock,
              (listener, flags) -> listener.onEvents(this.wrappingPlayer, new Events(flags)));
      audioOffloadListeners = new CopyOnWriteArraySet<>();
      mediaSourceHolderSnapshots = new ArrayList<>();
      shuffleOrder = new ShuffleOrder.DefaultShuffleOrder(/* length= */ 0);
      emptyTrackSelectorResult =
          new TrackSelectorResult(
              new RendererConfiguration[renderers.length],
              new ExoTrackSelection[renderers.length],
              Tracks.EMPTY,
              /* info= */ null);
      period = new Timeline.Period();
      permanentAvailableCommands =
          new Commands.Builder()
              .addAll(
                  COMMAND_PLAY_PAUSE,
                  COMMAND_PREPARE,
                  COMMAND_STOP,
                  COMMAND_SET_SPEED_AND_PITCH,
                  COMMAND_SET_SHUFFLE_MODE,
                  COMMAND_SET_REPEAT_MODE,
                  COMMAND_GET_CURRENT_MEDIA_ITEM,
                  COMMAND_GET_TIMELINE,
                  COMMAND_GET_METADATA,
                  COMMAND_SET_PLAYLIST_METADATA,
                  COMMAND_SET_MEDIA_ITEM,
                  COMMAND_CHANGE_MEDIA_ITEMS,
                  COMMAND_GET_TRACKS,
                  COMMAND_GET_AUDIO_ATTRIBUTES,
                  COMMAND_SET_AUDIO_ATTRIBUTES,
                  COMMAND_GET_VOLUME,
                  COMMAND_SET_VOLUME,
                  COMMAND_SET_VIDEO_SURFACE,
                  COMMAND_GET_TEXT,
                  COMMAND_RELEASE)
              .addIf(
                  COMMAND_SET_TRACK_SELECTION_PARAMETERS, trackSelector.isSetParametersSupported())
              .addIf(COMMAND_GET_DEVICE_VOLUME, builder.deviceVolumeControlEnabled)
              .addIf(COMMAND_SET_DEVICE_VOLUME, builder.deviceVolumeControlEnabled)
              .addIf(COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS, builder.deviceVolumeControlEnabled)
              .addIf(COMMAND_ADJUST_DEVICE_VOLUME, builder.deviceVolumeControlEnabled)
              .addIf(COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS, builder.deviceVolumeControlEnabled)
              .build();
      availableCommands =
          new Commands.Builder()
              .addAll(permanentAvailableCommands)
              .add(COMMAND_SEEK_TO_DEFAULT_POSITION)
              .add(COMMAND_SEEK_TO_MEDIA_ITEM)
              .build();
      playbackInfoUpdateHandler = clock.createHandler(applicationLooper, /* callback= */ null);
      playbackInfoUpdateListener =
          playbackInfoUpdate ->
              playbackInfoUpdateHandler.post(() -> handlePlaybackInfo(playbackInfoUpdate));
      playbackInfo = PlaybackInfo.createDummy(emptyTrackSelectorResult);
      analyticsCollector.setPlayer(this.wrappingPlayer, applicationLooper);
      PlayerId playerId =
          Util.SDK_INT < 31
              ? new PlayerId()
              : Api31.registerMediaMetricsListener(
                  applicationContext, /* player= */ this, builder.usePlatformDiagnostics);
      internalPlayer =
          new ExoPlayerImplInternal(
              renderers,
              trackSelector,
              emptyTrackSelectorResult,
              builder.loadControlSupplier.get(),
              bandwidthMeter,
              repeatMode,
              shuffleModeEnabled,
              analyticsCollector,
              seekParameters,
              builder.livePlaybackSpeedControl,
              builder.releaseTimeoutMs,
              pauseAtEndOfMediaItems,
              applicationLooper,
              clock,
              playbackInfoUpdateListener,
              playerId,
              builder.playbackLooper);

      volume = 1;
      repeatMode = Player.REPEAT_MODE_OFF;
      mediaMetadata = MediaMetadata.EMPTY;
      playlistMetadata = MediaMetadata.EMPTY;
      staticAndDynamicMediaMetadata = MediaMetadata.EMPTY;
      maskingWindowIndex = C.INDEX_UNSET;
      if (Util.SDK_INT < 21) {
        audioSessionId = initializeKeepSessionIdAudioTrack(C.AUDIO_SESSION_ID_UNSET);
      } else {
        audioSessionId = Util.generateAudioSessionIdV21(applicationContext);
      }
      currentCueGroup = CueGroup.EMPTY_TIME_ZERO;
      throwsWhenUsingWrongThread = true;

      addListener(analyticsCollector);
      bandwidthMeter.addEventListener(new Handler(applicationLooper), analyticsCollector);
      addAudioOffloadListener(componentListener);
      if (builder.foregroundModeTimeoutMs > 0) {
        internalPlayer.experimentalSetForegroundModeTimeoutMs(builder.foregroundModeTimeoutMs);
      }

      audioBecomingNoisyManager =
          new AudioBecomingNoisyManager(builder.context, eventHandler, componentListener);
      audioBecomingNoisyManager.setEnabled(builder.handleAudioBecomingNoisy);
      audioFocusManager = new AudioFocusManager(builder.context, eventHandler, componentListener);
      audioFocusManager.setAudioAttributes(builder.handleAudioFocus ? audioAttributes : null);
      if (suppressPlaybackOnUnsuitableOutput && Util.SDK_INT >= 23) {
        audioManager = (AudioManager) applicationContext.getSystemService(Context.AUDIO_SERVICE);
        Api23.registerAudioDeviceCallback(
            audioManager,
            new NoSuitableOutputPlaybackSuppressionAudioDeviceCallback(),
            new Handler(applicationLooper));
      }
      if (builder.deviceVolumeControlEnabled) {
        streamVolumeManager =
            new StreamVolumeManager(builder.context, eventHandler, componentListener);
        streamVolumeManager.setStreamType(Util.getStreamTypeForAudioUsage(audioAttributes.usage));
      } else {
        streamVolumeManager = null;
      }
      wakeLockManager = new WakeLockManager(builder.context);
      wakeLockManager.setEnabled(builder.wakeMode != C.WAKE_MODE_NONE);
      wifiLockManager = new WifiLockManager(builder.context);
      wifiLockManager.setEnabled(builder.wakeMode == C.WAKE_MODE_NETWORK);
      deviceInfo = createDeviceInfo(streamVolumeManager);
      videoSize = VideoSize.UNKNOWN;
      surfaceSize = Size.UNKNOWN;

      trackSelector.setAudioAttributes(audioAttributes);
      sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_AUDIO_SESSION_ID, audioSessionId);
      sendRendererMessage(TRACK_TYPE_VIDEO, MSG_SET_AUDIO_SESSION_ID, audioSessionId);
      sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_AUDIO_ATTRIBUTES, audioAttributes);
      sendRendererMessage(TRACK_TYPE_VIDEO, MSG_SET_SCALING_MODE, videoScalingMode);
      sendRendererMessage(
          TRACK_TYPE_VIDEO, MSG_SET_CHANGE_FRAME_RATE_STRATEGY, videoChangeFrameRateStrategy);
      sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_SKIP_SILENCE_ENABLED, skipSilenceEnabled);
      sendRendererMessage(
          TRACK_TYPE_VIDEO, MSG_SET_VIDEO_FRAME_METADATA_LISTENER, frameMetadataListener);
      sendRendererMessage(
          TRACK_TYPE_CAMERA_MOTION, MSG_SET_CAMERA_MOTION_LISTENER, frameMetadataListener);
    } finally {
      constructorFinished.open();
    }
  }

  @CanIgnoreReturnValue
  @SuppressWarnings("deprecation") // Returning deprecated class.
  @Override
  @Deprecated
  public AudioComponent getAudioComponent() {
    verifyApplicationThread();
    return this;
  }

  @CanIgnoreReturnValue
  @SuppressWarnings("deprecation") // Returning deprecated class.
  @Override
  @Deprecated
  public VideoComponent getVideoComponent() {
    verifyApplicationThread();
    return this;
  }

  @CanIgnoreReturnValue
  @SuppressWarnings("deprecation") // Returning deprecated class.
  @Override
  @Deprecated
  public TextComponent getTextComponent() {
    verifyApplicationThread();
    return this;
  }

  @CanIgnoreReturnValue
  @SuppressWarnings("deprecation") // Returning deprecated class.
  @Override
  @Deprecated
  public DeviceComponent getDeviceComponent() {
    verifyApplicationThread();
    return this;
  }

  @Override
  public boolean isSleepingForOffload() {
    verifyApplicationThread();
    return playbackInfo.sleepingForOffload;
  }

  @Override
  public Looper getPlaybackLooper() {
    // Don't verify application thread. We allow calls to this method from any thread.
    return internalPlayer.getPlaybackLooper();
  }

  @Override
  public Looper getApplicationLooper() {
    // Don't verify application thread. We allow calls to this method from any thread.
    return applicationLooper;
  }

  @Override
  public Clock getClock() {
    // Don't verify application thread. We allow calls to this method from any thread.
    return clock;
  }

  @Override
  public void addAudioOffloadListener(AudioOffloadListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    audioOffloadListeners.add(listener);
  }

  @Override
  public void removeAudioOffloadListener(AudioOffloadListener listener) {
    verifyApplicationThread();
    audioOffloadListeners.remove(listener);
  }

  @Override
  public Commands getAvailableCommands() {
    verifyApplicationThread();
    return availableCommands;
  }

  @Override
  public @State int getPlaybackState() {
    verifyApplicationThread();
    return playbackInfo.playbackState;
  }

  @Override
  public @PlaybackSuppressionReason int getPlaybackSuppressionReason() {
    verifyApplicationThread();
    return playbackInfo.playbackSuppressionReason;
  }

  @Override
  @Nullable
  public ExoPlaybackException getPlayerError() {
    verifyApplicationThread();
    return playbackInfo.playbackError;
  }

  @Override
  public void prepare() {
    verifyApplicationThread();
    boolean playWhenReady = getPlayWhenReady();
    @AudioFocusManager.PlayerCommand
    int playerCommand = audioFocusManager.updateAudioFocus(playWhenReady, Player.STATE_BUFFERING);
    updatePlayWhenReady(
        playWhenReady, playerCommand, getPlayWhenReadyChangeReason(playWhenReady, playerCommand));
    if (playbackInfo.playbackState != Player.STATE_IDLE) {
      return;
    }
    PlaybackInfo playbackInfo = this.playbackInfo.copyWithPlaybackError(null);
    playbackInfo =
        playbackInfo.copyWithPlaybackState(
            playbackInfo.timeline.isEmpty() ? STATE_ENDED : STATE_BUFFERING);
    // Trigger internal prepare first before updating the playback info and notifying external
    // listeners to ensure that new operations issued in the listener notifications reach the
    // player after this prepare. The internal player can't change the playback info immediately
    // because it uses a callback.
    pendingOperationAcks++;
    internalPlayer.prepare();
    updatePlaybackInfo(
        playbackInfo,
        /* ignored */ TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ C.TIME_UNSET,
        /* ignored */ C.INDEX_UNSET,
        /* repeatCurrentMediaItem= */ false);
  }

  @Override
  @Deprecated
  public void prepare(MediaSource mediaSource) {
    verifyApplicationThread();
    setMediaSource(mediaSource);
    prepare();
  }

  @Override
  @Deprecated
  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    verifyApplicationThread();
    setMediaSource(mediaSource, resetPosition);
    prepare();
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    verifyApplicationThread();
    setMediaSources(createMediaSources(mediaItems), resetPosition);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    verifyApplicationThread();
    setMediaSources(createMediaSources(mediaItems), startIndex, startPositionMs);
  }

  @Override
  public void setMediaSource(MediaSource mediaSource) {
    verifyApplicationThread();
    setMediaSources(Collections.singletonList(mediaSource));
  }

  @Override
  public void setMediaSource(MediaSource mediaSource, long startPositionMs) {
    verifyApplicationThread();
    setMediaSources(
        Collections.singletonList(mediaSource), /* startWindowIndex= */ 0, startPositionMs);
  }

  @Override
  public void setMediaSource(MediaSource mediaSource, boolean resetPosition) {
    verifyApplicationThread();
    setMediaSources(Collections.singletonList(mediaSource), resetPosition);
  }

  @Override
  public void setMediaSources(List<MediaSource> mediaSources) {
    verifyApplicationThread();
    setMediaSources(mediaSources, /* resetPosition= */ true);
  }

  @Override
  public void setMediaSources(List<MediaSource> mediaSources, boolean resetPosition) {
    verifyApplicationThread();
    setMediaSourcesInternal(
        mediaSources,
        /* startWindowIndex= */ C.INDEX_UNSET,
        /* startPositionMs= */ C.TIME_UNSET,
        /* resetToDefaultPosition= */ resetPosition);
  }

  @Override
  public void setMediaSources(
      List<MediaSource> mediaSources, int startWindowIndex, long startPositionMs) {
    verifyApplicationThread();
    setMediaSourcesInternal(
        mediaSources, startWindowIndex, startPositionMs, /* resetToDefaultPosition= */ false);
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    verifyApplicationThread();
    addMediaSources(index, createMediaSources(mediaItems));
  }

  @Override
  public void addMediaSource(MediaSource mediaSource) {
    verifyApplicationThread();
    addMediaSources(Collections.singletonList(mediaSource));
  }

  @Override
  public void addMediaSource(int index, MediaSource mediaSource) {
    verifyApplicationThread();
    addMediaSources(index, Collections.singletonList(mediaSource));
  }

  @Override
  public void addMediaSources(List<MediaSource> mediaSources) {
    verifyApplicationThread();
    addMediaSources(/* index= */ mediaSourceHolderSnapshots.size(), mediaSources);
  }

  @Override
  public void addMediaSources(int index, List<MediaSource> mediaSources) {
    verifyApplicationThread();
    checkArgument(index >= 0);
    index = min(index, mediaSourceHolderSnapshots.size());
    if (mediaSourceHolderSnapshots.isEmpty()) {
      // Handle initial items in a playlist as a set operation to ensure state changes and initial
      // position are updated correctly.
      setMediaSources(mediaSources, /* resetPosition= */ maskingWindowIndex == C.INDEX_UNSET);
      return;
    }
    PlaybackInfo newPlaybackInfo = addMediaSourcesInternal(playbackInfo, index, mediaSources);
    updatePlaybackInfo(
        newPlaybackInfo,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ C.TIME_UNSET,
        /* ignored */ C.INDEX_UNSET,
        /* repeatCurrentMediaItem= */ false);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    verifyApplicationThread();
    checkArgument(fromIndex >= 0 && toIndex >= fromIndex);
    int playlistSize = mediaSourceHolderSnapshots.size();
    toIndex = min(toIndex, playlistSize);
    if (fromIndex >= playlistSize || fromIndex == toIndex) {
      // Do nothing.
      return;
    }
    PlaybackInfo newPlaybackInfo = removeMediaItemsInternal(playbackInfo, fromIndex, toIndex);
    boolean positionDiscontinuity =
        !newPlaybackInfo.periodId.periodUid.equals(playbackInfo.periodId.periodUid);
    updatePlaybackInfo(
        newPlaybackInfo,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        positionDiscontinuity,
        DISCONTINUITY_REASON_REMOVE,
        /* discontinuityWindowStartPositionUs= */ getCurrentPositionUsInternal(newPlaybackInfo),
        /* ignored */ C.INDEX_UNSET,
        /* repeatCurrentMediaItem= */ false);
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newFromIndex) {
    verifyApplicationThread();
    checkArgument(fromIndex >= 0 && fromIndex <= toIndex && newFromIndex >= 0);
    int playlistSize = mediaSourceHolderSnapshots.size();
    toIndex = min(toIndex, playlistSize);
    newFromIndex = min(newFromIndex, playlistSize - (toIndex - fromIndex));
    if (fromIndex >= playlistSize || fromIndex == toIndex || fromIndex == newFromIndex) {
      // Do nothing.
      return;
    }
    Timeline oldTimeline = getCurrentTimeline();
    pendingOperationAcks++;
    Util.moveItems(mediaSourceHolderSnapshots, fromIndex, toIndex, newFromIndex);
    Timeline newTimeline = createMaskingTimeline();
    PlaybackInfo newPlaybackInfo =
        maskTimelineAndPosition(
            playbackInfo,
            newTimeline,
            getPeriodPositionUsAfterTimelineChanged(
                oldTimeline,
                newTimeline,
                getCurrentWindowIndexInternal(playbackInfo),
                getContentPositionInternal(playbackInfo)));
    internalPlayer.moveMediaSources(fromIndex, toIndex, newFromIndex, shuffleOrder);
    updatePlaybackInfo(
        newPlaybackInfo,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ C.TIME_UNSET,
        /* ignored */ C.INDEX_UNSET,
        /* repeatCurrentMediaItem= */ false);
  }

  @Override
  public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    verifyApplicationThread();
    checkArgument(fromIndex >= 0 && toIndex >= fromIndex);
    int playlistSize = mediaSourceHolderSnapshots.size();
    if (fromIndex > playlistSize) {
      // Do nothing.
      return;
    }
    toIndex = min(toIndex, playlistSize);
    if (canUpdateMediaSourcesWithMediaItems(fromIndex, toIndex, mediaItems)) {
      // Update MediaSources directly without creating new ones if possible.
      updateMediaSourcesWithMediaItems(fromIndex, toIndex, mediaItems);
      return;
    }
    List<MediaSource> mediaSources = createMediaSources(mediaItems);
    if (mediaSourceHolderSnapshots.isEmpty()) {
      // Handle initial items in a playlist as a set operation to ensure state changes and initial
      // position are updated correctly.
      setMediaSources(mediaSources, /* resetPosition= */ maskingWindowIndex == C.INDEX_UNSET);
      return;
    }
    PlaybackInfo newPlaybackInfo = addMediaSourcesInternal(playbackInfo, toIndex, mediaSources);
    newPlaybackInfo = removeMediaItemsInternal(newPlaybackInfo, fromIndex, toIndex);
    boolean positionDiscontinuity =
        !newPlaybackInfo.periodId.periodUid.equals(playbackInfo.periodId.periodUid);
    updatePlaybackInfo(
        newPlaybackInfo,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        positionDiscontinuity,
        DISCONTINUITY_REASON_REMOVE,
        /* discontinuityWindowStartPositionUs= */ getCurrentPositionUsInternal(newPlaybackInfo),
        /* ignored */ C.INDEX_UNSET,
        /* repeatCurrentMediaItem= */ false);
  }

  @Override
  public void setShuffleOrder(ShuffleOrder shuffleOrder) {
    verifyApplicationThread();
    checkArgument(shuffleOrder.getLength() == mediaSourceHolderSnapshots.size());
    this.shuffleOrder = shuffleOrder;
    Timeline timeline = createMaskingTimeline();
    PlaybackInfo newPlaybackInfo =
        maskTimelineAndPosition(
            playbackInfo,
            timeline,
            maskWindowPositionMsOrGetPeriodPositionUs(
                timeline, getCurrentMediaItemIndex(), getCurrentPosition()));
    pendingOperationAcks++;
    internalPlayer.setShuffleOrder(shuffleOrder);
    updatePlaybackInfo(
        newPlaybackInfo,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ C.TIME_UNSET,
        /* ignored */ C.INDEX_UNSET,
        /* repeatCurrentMediaItem= */ false);
  }

  @Override
  public void setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems) {
    verifyApplicationThread();
    if (this.pauseAtEndOfMediaItems == pauseAtEndOfMediaItems) {
      return;
    }
    this.pauseAtEndOfMediaItems = pauseAtEndOfMediaItems;
    internalPlayer.setPauseAtEndOfWindow(pauseAtEndOfMediaItems);
  }

  @Override
  public boolean getPauseAtEndOfMediaItems() {
    verifyApplicationThread();
    return pauseAtEndOfMediaItems;
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    verifyApplicationThread();
    @AudioFocusManager.PlayerCommand
    int playerCommand = audioFocusManager.updateAudioFocus(playWhenReady, getPlaybackState());
    updatePlayWhenReady(
        playWhenReady, playerCommand, getPlayWhenReadyChangeReason(playWhenReady, playerCommand));
  }

  @Override
  public boolean getPlayWhenReady() {
    verifyApplicationThread();
    return playbackInfo.playWhenReady;
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    verifyApplicationThread();
    if (this.repeatMode != repeatMode) {
      this.repeatMode = repeatMode;
      internalPlayer.setRepeatMode(repeatMode);
      listeners.queueEvent(
          Player.EVENT_REPEAT_MODE_CHANGED, listener -> listener.onRepeatModeChanged(repeatMode));
      updateAvailableCommands();
      listeners.flushEvents();
    }
  }

  @Override
  public @RepeatMode int getRepeatMode() {
    verifyApplicationThread();
    return repeatMode;
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    verifyApplicationThread();
    if (this.shuffleModeEnabled != shuffleModeEnabled) {
      this.shuffleModeEnabled = shuffleModeEnabled;
      internalPlayer.setShuffleModeEnabled(shuffleModeEnabled);
      listeners.queueEvent(
          Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
          listener -> listener.onShuffleModeEnabledChanged(shuffleModeEnabled));
      updateAvailableCommands();
      listeners.flushEvents();
    }
  }

  @Override
  public boolean getShuffleModeEnabled() {
    verifyApplicationThread();
    return shuffleModeEnabled;
  }

  @Override
  public boolean isLoading() {
    verifyApplicationThread();
    return playbackInfo.isLoading;
  }

  @Override
  public void seekTo(
      int mediaItemIndex,
      long positionMs,
      @Player.Command int seekCommand,
      boolean isRepeatingCurrentItem) {
    verifyApplicationThread();
    checkArgument(mediaItemIndex >= 0);
    analyticsCollector.notifySeekStarted();
    Timeline timeline = playbackInfo.timeline;
    if (!timeline.isEmpty() && mediaItemIndex >= timeline.getWindowCount()) {
      return;
    }
    pendingOperationAcks++;
    if (isPlayingAd()) {
      // TODO: Investigate adding support for seeking during ads. This is complicated to do in
      // general because the midroll ad preceding the seek destination must be played before the
      // content position can be played, if a different ad is playing at the moment.
      Log.w(TAG, "seekTo ignored because an ad is playing");
      ExoPlayerImplInternal.PlaybackInfoUpdate playbackInfoUpdate =
          new ExoPlayerImplInternal.PlaybackInfoUpdate(this.playbackInfo);
      playbackInfoUpdate.incrementPendingOperationAcks(1);
      playbackInfoUpdateListener.onPlaybackInfoUpdate(playbackInfoUpdate);
      return;
    }
    PlaybackInfo newPlaybackInfo = playbackInfo;
    if (playbackInfo.playbackState == Player.STATE_READY
        || (playbackInfo.playbackState == Player.STATE_ENDED && !timeline.isEmpty())) {
      newPlaybackInfo = playbackInfo.copyWithPlaybackState(Player.STATE_BUFFERING);
    }
    int oldMaskingMediaItemIndex = getCurrentMediaItemIndex();
    newPlaybackInfo =
        maskTimelineAndPosition(
            newPlaybackInfo,
            timeline,
            maskWindowPositionMsOrGetPeriodPositionUs(timeline, mediaItemIndex, positionMs));
    internalPlayer.seekTo(timeline, mediaItemIndex, Util.msToUs(positionMs));
    updatePlaybackInfo(
        newPlaybackInfo,
        /* ignored */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* positionDiscontinuity= */ true,
        /* positionDiscontinuityReason= */ DISCONTINUITY_REASON_SEEK,
        /* discontinuityWindowStartPositionUs= */ getCurrentPositionUsInternal(newPlaybackInfo),
        oldMaskingMediaItemIndex,
        isRepeatingCurrentItem);
  }

  @Override
  public long getSeekBackIncrement() {
    verifyApplicationThread();
    return seekBackIncrementMs;
  }

  @Override
  public long getSeekForwardIncrement() {
    verifyApplicationThread();
    return seekForwardIncrementMs;
  }

  @Override
  public long getMaxSeekToPreviousPosition() {
    verifyApplicationThread();
    return C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS;
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    verifyApplicationThread();
    if (playbackParameters == null) {
      playbackParameters = PlaybackParameters.DEFAULT;
    }
    if (playbackInfo.playbackParameters.equals(playbackParameters)) {
      return;
    }
    PlaybackInfo newPlaybackInfo = playbackInfo.copyWithPlaybackParameters(playbackParameters);
    pendingOperationAcks++;
    internalPlayer.setPlaybackParameters(playbackParameters);
    updatePlaybackInfo(
        newPlaybackInfo,
        /* ignored */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ C.TIME_UNSET,
        /* ignored */ C.INDEX_UNSET,
        /* repeatCurrentMediaItem= */ false);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    verifyApplicationThread();
    return playbackInfo.playbackParameters;
  }

  @Override
  public void setSeekParameters(@Nullable SeekParameters seekParameters) {
    verifyApplicationThread();
    if (seekParameters == null) {
      seekParameters = SeekParameters.DEFAULT;
    }
    if (!this.seekParameters.equals(seekParameters)) {
      this.seekParameters = seekParameters;
      internalPlayer.setSeekParameters(seekParameters);
    }
  }

  @Override
  public SeekParameters getSeekParameters() {
    verifyApplicationThread();
    return seekParameters;
  }

  @Override
  public void setForegroundMode(boolean foregroundMode) {
    verifyApplicationThread();
    if (this.foregroundMode != foregroundMode) {
      this.foregroundMode = foregroundMode;
      if (!internalPlayer.setForegroundMode(foregroundMode)) {
        // One of the renderers timed out releasing its resources.
        stopInternal(
            ExoPlaybackException.createForUnexpected(
                new ExoTimeoutException(ExoTimeoutException.TIMEOUT_OPERATION_SET_FOREGROUND_MODE),
                PlaybackException.ERROR_CODE_TIMEOUT));
      }
    }
  }

  @Override
  public void stop() {
    verifyApplicationThread();
    audioFocusManager.updateAudioFocus(getPlayWhenReady(), Player.STATE_IDLE);
    stopInternal(/* error= */ null);
    currentCueGroup = new CueGroup(ImmutableList.of(), playbackInfo.positionUs);
  }

  @Override
  public void release() {
    Log.i(
        TAG,
        "Release "
            + Integer.toHexString(System.identityHashCode(this))
            + " ["
            + MediaLibraryInfo.VERSION_SLASHY
            + "] ["
            + Util.DEVICE_DEBUG_INFO
            + "] ["
            + MediaLibraryInfo.registeredModules()
            + "]");
    verifyApplicationThread();
    if (Util.SDK_INT < 21 && keepSessionIdAudioTrack != null) {
      keepSessionIdAudioTrack.release();
      keepSessionIdAudioTrack = null;
    }
    audioBecomingNoisyManager.setEnabled(false);
    if (streamVolumeManager != null) {
      streamVolumeManager.release();
    }
    wakeLockManager.setStayAwake(false);
    wifiLockManager.setStayAwake(false);
    audioFocusManager.release();
    if (!internalPlayer.release()) {
      // One of the renderers timed out releasing its resources.
      listeners.sendEvent(
          Player.EVENT_PLAYER_ERROR,
          listener ->
              listener.onPlayerError(
                  ExoPlaybackException.createForUnexpected(
                      new ExoTimeoutException(ExoTimeoutException.TIMEOUT_OPERATION_RELEASE),
                      PlaybackException.ERROR_CODE_TIMEOUT)));
    }
    listeners.release();
    playbackInfoUpdateHandler.removeCallbacksAndMessages(null);
    bandwidthMeter.removeEventListener(analyticsCollector);
    if (playbackInfo.sleepingForOffload) {
      playbackInfo = playbackInfo.copyWithEstimatedPosition();
    }
    playbackInfo = playbackInfo.copyWithPlaybackState(Player.STATE_IDLE);
    playbackInfo = playbackInfo.copyWithLoadingMediaPeriodId(playbackInfo.periodId);
    playbackInfo.bufferedPositionUs = playbackInfo.positionUs;
    playbackInfo.totalBufferedDurationUs = 0;
    analyticsCollector.release();
    trackSelector.release();
    removeSurfaceCallbacks();
    if (ownedSurface != null) {
      ownedSurface.release();
      ownedSurface = null;
    }
    if (isPriorityTaskManagerRegistered) {
      checkNotNull(priorityTaskManager).remove(C.PRIORITY_PLAYBACK);
      isPriorityTaskManagerRegistered = false;
    }
    currentCueGroup = CueGroup.EMPTY_TIME_ZERO;
    playerReleased = true;
  }

  @Override
  public PlayerMessage createMessage(Target target) {
    verifyApplicationThread();
    return createMessageInternal(target);
  }

  @Override
  public int getCurrentPeriodIndex() {
    verifyApplicationThread();
    if (playbackInfo.timeline.isEmpty()) {
      return maskingPeriodIndex;
    } else {
      return playbackInfo.timeline.getIndexOfPeriod(playbackInfo.periodId.periodUid);
    }
  }

  @Override
  public int getCurrentMediaItemIndex() {
    verifyApplicationThread();
    int currentWindowIndex = getCurrentWindowIndexInternal(playbackInfo);
    return currentWindowIndex == C.INDEX_UNSET ? 0 : currentWindowIndex;
  }

  @Override
  public long getDuration() {
    verifyApplicationThread();
    if (isPlayingAd()) {
      MediaPeriodId periodId = playbackInfo.periodId;
      playbackInfo.timeline.getPeriodByUid(periodId.periodUid, period);
      long adDurationUs = period.getAdDurationUs(periodId.adGroupIndex, periodId.adIndexInAdGroup);
      return Util.usToMs(adDurationUs);
    }
    return getContentDuration();
  }

  @Override
  public long getCurrentPosition() {
    verifyApplicationThread();
    return Util.usToMs(getCurrentPositionUsInternal(playbackInfo));
  }

  @Override
  public long getBufferedPosition() {
    verifyApplicationThread();
    if (isPlayingAd()) {
      return playbackInfo.loadingMediaPeriodId.equals(playbackInfo.periodId)
          ? Util.usToMs(playbackInfo.bufferedPositionUs)
          : getDuration();
    }
    return getContentBufferedPosition();
  }

  @Override
  public long getTotalBufferedDuration() {
    verifyApplicationThread();
    return Util.usToMs(playbackInfo.totalBufferedDurationUs);
  }

  @Override
  public boolean isPlayingAd() {
    verifyApplicationThread();
    return playbackInfo.periodId.isAd();
  }

  @Override
  public int getCurrentAdGroupIndex() {
    verifyApplicationThread();
    return isPlayingAd() ? playbackInfo.periodId.adGroupIndex : C.INDEX_UNSET;
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    verifyApplicationThread();
    return isPlayingAd() ? playbackInfo.periodId.adIndexInAdGroup : C.INDEX_UNSET;
  }

  @Override
  public long getContentPosition() {
    verifyApplicationThread();
    return getContentPositionInternal(playbackInfo);
  }

  @Override
  public long getContentBufferedPosition() {
    verifyApplicationThread();
    if (playbackInfo.timeline.isEmpty()) {
      return maskingWindowPositionMs;
    }
    if (playbackInfo.loadingMediaPeriodId.windowSequenceNumber
        != playbackInfo.periodId.windowSequenceNumber) {
      return playbackInfo.timeline.getWindow(getCurrentMediaItemIndex(), window).getDurationMs();
    }
    long contentBufferedPositionUs = playbackInfo.bufferedPositionUs;
    if (playbackInfo.loadingMediaPeriodId.isAd()) {
      Timeline.Period loadingPeriod =
          playbackInfo.timeline.getPeriodByUid(playbackInfo.loadingMediaPeriodId.periodUid, period);
      contentBufferedPositionUs =
          loadingPeriod.getAdGroupTimeUs(playbackInfo.loadingMediaPeriodId.adGroupIndex);
      if (contentBufferedPositionUs == C.TIME_END_OF_SOURCE) {
        contentBufferedPositionUs = loadingPeriod.durationUs;
      }
    }
    return Util.usToMs(
        periodPositionUsToWindowPositionUs(
            playbackInfo.timeline, playbackInfo.loadingMediaPeriodId, contentBufferedPositionUs));
  }

  @Override
  public int getRendererCount() {
    verifyApplicationThread();
    return renderers.length;
  }

  @Override
  public @C.TrackType int getRendererType(int index) {
    verifyApplicationThread();
    return renderers[index].getTrackType();
  }

  @Override
  public Renderer getRenderer(int index) {
    verifyApplicationThread();
    return renderers[index];
  }

  @Override
  public TrackSelector getTrackSelector() {
    verifyApplicationThread();
    return trackSelector;
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    verifyApplicationThread();
    return playbackInfo.trackGroups;
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    verifyApplicationThread();
    return new TrackSelectionArray(playbackInfo.trackSelectorResult.selections);
  }

  @Override
  public Tracks getCurrentTracks() {
    verifyApplicationThread();
    return playbackInfo.trackSelectorResult.tracks;
  }

  @Override
  public TrackSelectionParameters getTrackSelectionParameters() {
    verifyApplicationThread();
    return trackSelector.getParameters();
  }

  @Override
  public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    verifyApplicationThread();
    if (!trackSelector.isSetParametersSupported()
        || parameters.equals(trackSelector.getParameters())) {
      return;
    }
    trackSelector.setParameters(parameters);
    listeners.sendEvent(
        EVENT_TRACK_SELECTION_PARAMETERS_CHANGED,
        listener -> listener.onTrackSelectionParametersChanged(parameters));
  }

  @Override
  public MediaMetadata getMediaMetadata() {
    verifyApplicationThread();
    return mediaMetadata;
  }

  @Override
  public MediaMetadata getPlaylistMetadata() {
    verifyApplicationThread();
    return playlistMetadata;
  }

  @Override
  public void setPlaylistMetadata(MediaMetadata playlistMetadata) {
    verifyApplicationThread();
    checkNotNull(playlistMetadata);
    if (playlistMetadata.equals(this.playlistMetadata)) {
      return;
    }
    this.playlistMetadata = playlistMetadata;
    listeners.sendEvent(
        EVENT_PLAYLIST_METADATA_CHANGED,
        listener -> listener.onPlaylistMetadataChanged(this.playlistMetadata));
  }

  @Override
  public Timeline getCurrentTimeline() {
    verifyApplicationThread();
    return playbackInfo.timeline;
  }

  @Override
  public void setVideoEffects(List<Effect> videoEffects) {
    verifyApplicationThread();
    try {
      // LINT.IfChange(set_video_effects)
      Class.forName("androidx.media3.effect.PreviewingSingleInputVideoGraph$Factory")
          .getConstructor(VideoFrameProcessor.Factory.class);
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      throw new IllegalStateException("Could not find required lib-effect dependencies.", e);
    }
    sendRendererMessage(TRACK_TYPE_VIDEO, MSG_SET_VIDEO_EFFECTS, videoEffects);
  }

  @Override
  public void setVideoScalingMode(@C.VideoScalingMode int videoScalingMode) {
    verifyApplicationThread();
    this.videoScalingMode = videoScalingMode;
    sendRendererMessage(TRACK_TYPE_VIDEO, MSG_SET_SCALING_MODE, videoScalingMode);
  }

  @Override
  public @C.VideoScalingMode int getVideoScalingMode() {
    verifyApplicationThread();
    return videoScalingMode;
  }

  @Override
  public void setVideoChangeFrameRateStrategy(
      @C.VideoChangeFrameRateStrategy int videoChangeFrameRateStrategy) {
    verifyApplicationThread();
    if (this.videoChangeFrameRateStrategy == videoChangeFrameRateStrategy) {
      return;
    }
    this.videoChangeFrameRateStrategy = videoChangeFrameRateStrategy;
    sendRendererMessage(
        TRACK_TYPE_VIDEO, MSG_SET_CHANGE_FRAME_RATE_STRATEGY, videoChangeFrameRateStrategy);
  }

  @Override
  public @C.VideoChangeFrameRateStrategy int getVideoChangeFrameRateStrategy() {
    verifyApplicationThread();
    return videoChangeFrameRateStrategy;
  }

  @Override
  public VideoSize getVideoSize() {
    verifyApplicationThread();
    return videoSize;
  }

  @Override
  public Size getSurfaceSize() {
    verifyApplicationThread();
    return surfaceSize;
  }

  @Override
  public void clearVideoSurface() {
    verifyApplicationThread();
    removeSurfaceCallbacks();
    setVideoOutputInternal(/* videoOutput= */ null);
    maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
  }

  @Override
  public void clearVideoSurface(@Nullable Surface surface) {
    verifyApplicationThread();
    if (surface != null && surface == videoOutput) {
      clearVideoSurface();
    }
  }

  @Override
  public void setVideoSurface(@Nullable Surface surface) {
    verifyApplicationThread();
    removeSurfaceCallbacks();
    setVideoOutputInternal(surface);
    int newSurfaceSize = surface == null ? 0 : C.LENGTH_UNSET;
    maybeNotifySurfaceSizeChanged(/* width= */ newSurfaceSize, /* height= */ newSurfaceSize);
  }

  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    verifyApplicationThread();
    if (surfaceHolder == null) {
      clearVideoSurface();
    } else {
      removeSurfaceCallbacks();
      this.surfaceHolderSurfaceIsVideoOutput = true;
      this.surfaceHolder = surfaceHolder;
      surfaceHolder.addCallback(componentListener);
      Surface surface = surfaceHolder.getSurface();
      if (surface != null && surface.isValid()) {
        setVideoOutputInternal(surface);
        Rect surfaceSize = surfaceHolder.getSurfaceFrame();
        maybeNotifySurfaceSizeChanged(surfaceSize.width(), surfaceSize.height());
      } else {
        setVideoOutputInternal(/* videoOutput= */ null);
        maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
      }
    }
  }

  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    verifyApplicationThread();
    if (surfaceHolder != null && surfaceHolder == this.surfaceHolder) {
      clearVideoSurface();
    }
  }

  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    verifyApplicationThread();
    if (surfaceView instanceof VideoDecoderOutputBufferRenderer) {
      removeSurfaceCallbacks();
      setVideoOutputInternal(surfaceView);
      setNonVideoOutputSurfaceHolderInternal(surfaceView.getHolder());
    } else if (surfaceView instanceof SphericalGLSurfaceView) {
      removeSurfaceCallbacks();
      sphericalGLSurfaceView = (SphericalGLSurfaceView) surfaceView;
      createMessageInternal(frameMetadataListener)
          .setType(FrameMetadataListener.MSG_SET_SPHERICAL_SURFACE_VIEW)
          .setPayload(sphericalGLSurfaceView)
          .send();
      sphericalGLSurfaceView.addVideoSurfaceListener(componentListener);
      setVideoOutputInternal(sphericalGLSurfaceView.getVideoSurface());
      setNonVideoOutputSurfaceHolderInternal(surfaceView.getHolder());
    } else {
      setVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
    }
  }

  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    verifyApplicationThread();
    clearVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
  }

  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {
    verifyApplicationThread();
    if (textureView == null) {
      clearVideoSurface();
    } else {
      removeSurfaceCallbacks();
      this.textureView = textureView;
      if (textureView.getSurfaceTextureListener() != null) {
        Log.w(TAG, "Replacing existing SurfaceTextureListener.");
      }
      textureView.setSurfaceTextureListener(componentListener);
      @Nullable
      SurfaceTexture surfaceTexture =
          textureView.isAvailable() ? textureView.getSurfaceTexture() : null;
      if (surfaceTexture == null) {
        setVideoOutputInternal(/* videoOutput= */ null);
        maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
      } else {
        setSurfaceTextureInternal(surfaceTexture);
        maybeNotifySurfaceSizeChanged(textureView.getWidth(), textureView.getHeight());
      }
    }
  }

  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {
    verifyApplicationThread();
    if (textureView != null && textureView == this.textureView) {
      clearVideoSurface();
    }
  }

  @Override
  public void setAudioAttributes(AudioAttributes newAudioAttributes, boolean handleAudioFocus) {
    verifyApplicationThread();
    if (playerReleased) {
      return;
    }
    if (!Util.areEqual(this.audioAttributes, newAudioAttributes)) {
      this.audioAttributes = newAudioAttributes;
      sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_AUDIO_ATTRIBUTES, newAudioAttributes);
      if (streamVolumeManager != null) {
        streamVolumeManager.setStreamType(
            Util.getStreamTypeForAudioUsage(newAudioAttributes.usage));
      }
      // Queue event only and flush after updating playWhenReady in case both events are triggered.
      listeners.queueEvent(
          EVENT_AUDIO_ATTRIBUTES_CHANGED,
          listener -> listener.onAudioAttributesChanged(newAudioAttributes));
    }

    audioFocusManager.setAudioAttributes(handleAudioFocus ? newAudioAttributes : null);
    trackSelector.setAudioAttributes(newAudioAttributes);
    boolean playWhenReady = getPlayWhenReady();
    @AudioFocusManager.PlayerCommand
    int playerCommand = audioFocusManager.updateAudioFocus(playWhenReady, getPlaybackState());
    updatePlayWhenReady(
        playWhenReady, playerCommand, getPlayWhenReadyChangeReason(playWhenReady, playerCommand));
    listeners.flushEvents();
  }

  @Override
  public AudioAttributes getAudioAttributes() {
    verifyApplicationThread();
    return audioAttributes;
  }

  @Override
  public void setAudioSessionId(int audioSessionId) {
    verifyApplicationThread();
    if (this.audioSessionId == audioSessionId) {
      return;
    }
    if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
      if (Util.SDK_INT < 21) {
        audioSessionId = initializeKeepSessionIdAudioTrack(C.AUDIO_SESSION_ID_UNSET);
      } else {
        audioSessionId = Util.generateAudioSessionIdV21(applicationContext);
      }
    } else if (Util.SDK_INT < 21) {
      // We need to re-initialize keepSessionIdAudioTrack to make sure the session is kept alive for
      // as long as the player is using it.
      initializeKeepSessionIdAudioTrack(audioSessionId);
    }
    this.audioSessionId = audioSessionId;
    sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_AUDIO_SESSION_ID, audioSessionId);
    sendRendererMessage(TRACK_TYPE_VIDEO, MSG_SET_AUDIO_SESSION_ID, audioSessionId);
    int finalAudioSessionId = audioSessionId;
    listeners.sendEvent(
        EVENT_AUDIO_SESSION_ID, listener -> listener.onAudioSessionIdChanged(finalAudioSessionId));
  }

  @Override
  public int getAudioSessionId() {
    verifyApplicationThread();
    return audioSessionId;
  }

  @Override
  public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
    verifyApplicationThread();
    sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_AUX_EFFECT_INFO, auxEffectInfo);
  }

  @Override
  public void clearAuxEffectInfo() {
    verifyApplicationThread();
    setAuxEffectInfo(new AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, /* sendLevel= */ 0f));
  }

  @RequiresApi(23)
  @Override
  public void setPreferredAudioDevice(@Nullable AudioDeviceInfo audioDeviceInfo) {
    verifyApplicationThread();
    sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_PREFERRED_AUDIO_DEVICE, audioDeviceInfo);
  }

  @Override
  public void setVolume(float volume) {
    verifyApplicationThread();
    volume = Util.constrainValue(volume, /* min= */ 0, /* max= */ 1);
    if (this.volume == volume) {
      return;
    }
    this.volume = volume;
    sendVolumeToRenderers();
    float finalVolume = volume;
    listeners.sendEvent(EVENT_VOLUME_CHANGED, listener -> listener.onVolumeChanged(finalVolume));
  }

  @Override
  public float getVolume() {
    verifyApplicationThread();
    return volume;
  }

  @Override
  public boolean getSkipSilenceEnabled() {
    verifyApplicationThread();
    return skipSilenceEnabled;
  }

  @Override
  public void setSkipSilenceEnabled(boolean newSkipSilenceEnabled) {
    verifyApplicationThread();
    if (skipSilenceEnabled == newSkipSilenceEnabled) {
      return;
    }
    skipSilenceEnabled = newSkipSilenceEnabled;
    sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_SKIP_SILENCE_ENABLED, newSkipSilenceEnabled);
    listeners.sendEvent(
        EVENT_SKIP_SILENCE_ENABLED_CHANGED,
        listener -> listener.onSkipSilenceEnabledChanged(newSkipSilenceEnabled));
  }

  @Override
  public AnalyticsCollector getAnalyticsCollector() {
    verifyApplicationThread();
    return analyticsCollector;
  }

  @Override
  public void addAnalyticsListener(AnalyticsListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    analyticsCollector.addListener(checkNotNull(listener));
  }

  @Override
  public void removeAnalyticsListener(AnalyticsListener listener) {
    verifyApplicationThread();
    analyticsCollector.removeListener(checkNotNull(listener));
  }

  @Override
  public void setHandleAudioBecomingNoisy(boolean handleAudioBecomingNoisy) {
    verifyApplicationThread();
    if (playerReleased) {
      return;
    }
    audioBecomingNoisyManager.setEnabled(handleAudioBecomingNoisy);
  }

  @Override
  public void setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {
    verifyApplicationThread();
    if (Util.areEqual(this.priorityTaskManager, priorityTaskManager)) {
      return;
    }
    if (isPriorityTaskManagerRegistered) {
      checkNotNull(this.priorityTaskManager).remove(C.PRIORITY_PLAYBACK);
    }
    if (priorityTaskManager != null && isLoading()) {
      priorityTaskManager.add(C.PRIORITY_PLAYBACK);
      isPriorityTaskManagerRegistered = true;
    } else {
      isPriorityTaskManagerRegistered = false;
    }
    this.priorityTaskManager = priorityTaskManager;
  }

  @Override
  @Nullable
  public Format getVideoFormat() {
    verifyApplicationThread();
    return videoFormat;
  }

  @Override
  @Nullable
  public Format getAudioFormat() {
    verifyApplicationThread();
    return audioFormat;
  }

  @Override
  @Nullable
  public DecoderCounters getVideoDecoderCounters() {
    verifyApplicationThread();
    return videoDecoderCounters;
  }

  @Override
  @Nullable
  public DecoderCounters getAudioDecoderCounters() {
    verifyApplicationThread();
    return audioDecoderCounters;
  }

  @Override
  public void setVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
    verifyApplicationThread();
    videoFrameMetadataListener = listener;
    createMessageInternal(frameMetadataListener)
        .setType(FrameMetadataListener.MSG_SET_VIDEO_FRAME_METADATA_LISTENER)
        .setPayload(listener)
        .send();
  }

  @Override
  public void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
    verifyApplicationThread();
    if (videoFrameMetadataListener != listener) {
      return;
    }
    createMessageInternal(frameMetadataListener)
        .setType(FrameMetadataListener.MSG_SET_VIDEO_FRAME_METADATA_LISTENER)
        .setPayload(null)
        .send();
  }

  @Override
  public void setCameraMotionListener(CameraMotionListener listener) {
    verifyApplicationThread();
    cameraMotionListener = listener;
    createMessageInternal(frameMetadataListener)
        .setType(FrameMetadataListener.MSG_SET_CAMERA_MOTION_LISTENER)
        .setPayload(listener)
        .send();
  }

  @Override
  public void clearCameraMotionListener(CameraMotionListener listener) {
    verifyApplicationThread();
    if (cameraMotionListener != listener) {
      return;
    }
    createMessageInternal(frameMetadataListener)
        .setType(FrameMetadataListener.MSG_SET_CAMERA_MOTION_LISTENER)
        .setPayload(null)
        .send();
  }

  @Override
  public CueGroup getCurrentCues() {
    verifyApplicationThread();
    return currentCueGroup;
  }

  @Override
  public void addListener(Listener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    listeners.add(checkNotNull(listener));
  }

  @Override
  public void removeListener(Listener listener) {
    verifyApplicationThread();
    listeners.remove(checkNotNull(listener));
  }

  @Override
  public void setWakeMode(@C.WakeMode int wakeMode) {
    verifyApplicationThread();
    switch (wakeMode) {
      case C.WAKE_MODE_NONE:
        wakeLockManager.setEnabled(false);
        wifiLockManager.setEnabled(false);
        break;
      case C.WAKE_MODE_LOCAL:
        wakeLockManager.setEnabled(true);
        wifiLockManager.setEnabled(false);
        break;
      case C.WAKE_MODE_NETWORK:
        wakeLockManager.setEnabled(true);
        wifiLockManager.setEnabled(true);
        break;
      default:
        break;
    }
  }

  @Override
  public DeviceInfo getDeviceInfo() {
    verifyApplicationThread();
    return deviceInfo;
  }

  @Override
  public int getDeviceVolume() {
    verifyApplicationThread();
    if (streamVolumeManager != null) {
      return streamVolumeManager.getVolume();
    } else {
      return 0;
    }
  }

  @Override
  public boolean isDeviceMuted() {
    verifyApplicationThread();
    if (streamVolumeManager != null) {
      return streamVolumeManager.isMuted();
    } else {
      return false;
    }
  }

  /**
   * @deprecated Use {@link #setDeviceVolume(int, int)} instead.
   */
  @Deprecated
  @Override
  public void setDeviceVolume(int volume) {
    verifyApplicationThread();
    if (streamVolumeManager != null) {
      streamVolumeManager.setVolume(volume, C.VOLUME_FLAG_SHOW_UI);
    }
  }

  @Override
  public void setDeviceVolume(int volume, @C.VolumeFlags int flags) {
    verifyApplicationThread();
    if (streamVolumeManager != null) {
      streamVolumeManager.setVolume(volume, flags);
    }
  }

  /**
   * @deprecated Use {@link #increaseDeviceVolume(int)} instead.
   */
  @Deprecated
  @Override
  public void increaseDeviceVolume() {
    verifyApplicationThread();
    if (streamVolumeManager != null) {
      streamVolumeManager.increaseVolume(C.VOLUME_FLAG_SHOW_UI);
    }
  }

  @Override
  public void increaseDeviceVolume(@C.VolumeFlags int flags) {
    verifyApplicationThread();
    if (streamVolumeManager != null) {
      streamVolumeManager.increaseVolume(flags);
    }
  }

  /**
   * @deprecated Use {@link #decreaseDeviceVolume(int)} instead.
   */
  @Deprecated
  @Override
  public void decreaseDeviceVolume() {
    verifyApplicationThread();
    if (streamVolumeManager != null) {
      streamVolumeManager.decreaseVolume(C.VOLUME_FLAG_SHOW_UI);
    }
  }

  @Override
  public void decreaseDeviceVolume(@C.VolumeFlags int flags) {
    verifyApplicationThread();
    if (streamVolumeManager != null) {
      streamVolumeManager.decreaseVolume(flags);
    }
  }

  /**
   * @deprecated Use {@link #setDeviceMuted(boolean, int)} instead.
   */
  @Deprecated
  @Override
  public void setDeviceMuted(boolean muted) {
    verifyApplicationThread();
    if (streamVolumeManager != null) {
      streamVolumeManager.setMuted(muted, C.VOLUME_FLAG_SHOW_UI);
    }
  }

  @Override
  public void setDeviceMuted(boolean muted, @C.VolumeFlags int flags) {
    verifyApplicationThread();
    if (streamVolumeManager != null) {
      streamVolumeManager.setMuted(muted, flags);
    }
  }

  @Override
  public boolean isTunnelingEnabled() {
    verifyApplicationThread();
    for (@Nullable
    RendererConfiguration config : playbackInfo.trackSelectorResult.rendererConfigurations) {
      if (config != null && config.tunneling) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void setImageOutput(ImageOutput imageOutput) {
    verifyApplicationThread();
    sendRendererMessage(TRACK_TYPE_IMAGE, MSG_SET_IMAGE_OUTPUT, imageOutput);
  }

  @SuppressWarnings("deprecation") // Calling deprecated methods.
  /* package */ void setThrowsWhenUsingWrongThread(boolean throwsWhenUsingWrongThread) {
    this.throwsWhenUsingWrongThread = throwsWhenUsingWrongThread;
    listeners.setThrowsWhenUsingWrongThread(throwsWhenUsingWrongThread);
    if (analyticsCollector instanceof DefaultAnalyticsCollector) {
      ((DefaultAnalyticsCollector) analyticsCollector)
          .setThrowsWhenUsingWrongThread(throwsWhenUsingWrongThread);
    }
  }

  /**
   * Stops the player.
   *
   * @param error An optional {@link ExoPlaybackException} to set.
   */
  private void stopInternal(@Nullable ExoPlaybackException error) {
    PlaybackInfo playbackInfo =
        this.playbackInfo.copyWithLoadingMediaPeriodId(this.playbackInfo.periodId);
    playbackInfo.bufferedPositionUs = playbackInfo.positionUs;
    playbackInfo.totalBufferedDurationUs = 0;
    playbackInfo = playbackInfo.copyWithPlaybackState(Player.STATE_IDLE);
    if (error != null) {
      playbackInfo = playbackInfo.copyWithPlaybackError(error);
    }
    pendingOperationAcks++;
    internalPlayer.stop();
    updatePlaybackInfo(
        playbackInfo,
        /* ignored */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ C.TIME_UNSET,
        /* ignored */ C.INDEX_UNSET,
        /* repeatCurrentMediaItem= */ false);
  }

  private int getCurrentWindowIndexInternal(PlaybackInfo playbackInfo) {
    if (playbackInfo.timeline.isEmpty()) {
      return maskingWindowIndex;
    }
    return playbackInfo.timeline.getPeriodByUid(playbackInfo.periodId.periodUid, period)
        .windowIndex;
  }

  private long getContentPositionInternal(PlaybackInfo playbackInfo) {
    if (playbackInfo.periodId.isAd()) {
      playbackInfo.timeline.getPeriodByUid(playbackInfo.periodId.periodUid, period);
      return playbackInfo.requestedContentPositionUs == C.TIME_UNSET
          ? playbackInfo
              .timeline
              .getWindow(getCurrentWindowIndexInternal(playbackInfo), window)
              .getDefaultPositionMs()
          : period.getPositionInWindowMs() + Util.usToMs(playbackInfo.requestedContentPositionUs);
    }
    return Util.usToMs(getCurrentPositionUsInternal(playbackInfo));
  }

  private long getCurrentPositionUsInternal(PlaybackInfo playbackInfo) {
    if (playbackInfo.timeline.isEmpty()) {
      return Util.msToUs(maskingWindowPositionMs);
    }

    long positionUs =
        playbackInfo.sleepingForOffload
            ? playbackInfo.getEstimatedPositionUs()
            : playbackInfo.positionUs;

    if (playbackInfo.periodId.isAd()) {
      return positionUs;
    }
    return periodPositionUsToWindowPositionUs(
        playbackInfo.timeline, playbackInfo.periodId, positionUs);
  }

  private List<MediaSource> createMediaSources(List<MediaItem> mediaItems) {
    List<MediaSource> mediaSources = new ArrayList<>();
    for (int i = 0; i < mediaItems.size(); i++) {
      mediaSources.add(mediaSourceFactory.createMediaSource(mediaItems.get(i)));
    }
    return mediaSources;
  }

  private void handlePlaybackInfo(ExoPlayerImplInternal.PlaybackInfoUpdate playbackInfoUpdate) {
    pendingOperationAcks -= playbackInfoUpdate.operationAcks;
    if (playbackInfoUpdate.positionDiscontinuity) {
      pendingDiscontinuityReason = playbackInfoUpdate.discontinuityReason;
      pendingDiscontinuity = true;
    }
    if (playbackInfoUpdate.hasPlayWhenReadyChangeReason) {
      pendingPlayWhenReadyChangeReason = playbackInfoUpdate.playWhenReadyChangeReason;
    }
    if (pendingOperationAcks == 0) {
      Timeline newTimeline = playbackInfoUpdate.playbackInfo.timeline;
      if (!this.playbackInfo.timeline.isEmpty() && newTimeline.isEmpty()) {
        // Update the masking variables, which are used when the timeline becomes empty because a
        // ConcatenatingMediaSource has been cleared.
        maskingWindowIndex = C.INDEX_UNSET;
        maskingWindowPositionMs = 0;
        maskingPeriodIndex = 0;
      }
      if (!newTimeline.isEmpty()) {
        List<Timeline> timelines = ((PlaylistTimeline) newTimeline).getChildTimelines();
        checkState(timelines.size() == mediaSourceHolderSnapshots.size());
        for (int i = 0; i < timelines.size(); i++) {
          mediaSourceHolderSnapshots.get(i).updateTimeline(timelines.get(i));
        }
      }
      boolean positionDiscontinuity = false;
      long discontinuityWindowStartPositionUs = C.TIME_UNSET;
      if (pendingDiscontinuity) {
        positionDiscontinuity =
            !playbackInfoUpdate.playbackInfo.periodId.equals(playbackInfo.periodId)
                || playbackInfoUpdate.playbackInfo.discontinuityStartPositionUs
                    != playbackInfo.positionUs;
        if (positionDiscontinuity) {
          discontinuityWindowStartPositionUs =
              newTimeline.isEmpty() || playbackInfoUpdate.playbackInfo.periodId.isAd()
                  ? playbackInfoUpdate.playbackInfo.discontinuityStartPositionUs
                  : periodPositionUsToWindowPositionUs(
                      newTimeline,
                      playbackInfoUpdate.playbackInfo.periodId,
                      playbackInfoUpdate.playbackInfo.discontinuityStartPositionUs);
        }
      }
      pendingDiscontinuity = false;
      updatePlaybackInfo(
          playbackInfoUpdate.playbackInfo,
          TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
          pendingPlayWhenReadyChangeReason,
          positionDiscontinuity,
          pendingDiscontinuityReason,
          discontinuityWindowStartPositionUs,
          /* ignored */ C.INDEX_UNSET,
          /* repeatCurrentMediaItem= */ false);
    }
  }

  // Calling deprecated listeners.
  @SuppressWarnings("deprecation")
  private void updatePlaybackInfo(
      PlaybackInfo playbackInfo,
      @TimelineChangeReason int timelineChangeReason,
      @PlayWhenReadyChangeReason int playWhenReadyChangeReason,
      boolean positionDiscontinuity,
      @DiscontinuityReason int positionDiscontinuityReason,
      long discontinuityWindowStartPositionUs,
      int oldMaskingMediaItemIndex,
      boolean repeatCurrentMediaItem) {

    // Assign playback info immediately such that all getters return the right values, but keep
    // snapshot of previous and new state so that listener invocations are triggered correctly.
    PlaybackInfo previousPlaybackInfo = this.playbackInfo;
    PlaybackInfo newPlaybackInfo = playbackInfo;
    this.playbackInfo = playbackInfo;

    boolean timelineChanged = !previousPlaybackInfo.timeline.equals(newPlaybackInfo.timeline);
    Pair<Boolean, Integer> mediaItemTransitionInfo =
        evaluateMediaItemTransitionReason(
            newPlaybackInfo,
            previousPlaybackInfo,
            positionDiscontinuity,
            positionDiscontinuityReason,
            timelineChanged,
            repeatCurrentMediaItem);
    boolean mediaItemTransitioned = mediaItemTransitionInfo.first;
    int mediaItemTransitionReason = mediaItemTransitionInfo.second;
    @Nullable MediaItem mediaItem = null;
    if (mediaItemTransitioned) {
      if (!newPlaybackInfo.timeline.isEmpty()) {
        int windowIndex =
            newPlaybackInfo.timeline.getPeriodByUid(newPlaybackInfo.periodId.periodUid, period)
                .windowIndex;
        mediaItem = newPlaybackInfo.timeline.getWindow(windowIndex, window).mediaItem;
      }
      staticAndDynamicMediaMetadata = MediaMetadata.EMPTY;
    }
    if (mediaItemTransitioned
        || !previousPlaybackInfo.staticMetadata.equals(newPlaybackInfo.staticMetadata)) {
      staticAndDynamicMediaMetadata =
          staticAndDynamicMediaMetadata
              .buildUpon()
              .populateFromMetadata(newPlaybackInfo.staticMetadata)
              .build();
    }
    MediaMetadata newMediaMetadata = buildUpdatedMediaMetadata();
    boolean metadataChanged = !newMediaMetadata.equals(mediaMetadata);
    mediaMetadata = newMediaMetadata;
    boolean playWhenReadyChanged =
        previousPlaybackInfo.playWhenReady != newPlaybackInfo.playWhenReady;
    boolean playbackStateChanged =
        previousPlaybackInfo.playbackState != newPlaybackInfo.playbackState;
    if (playbackStateChanged || playWhenReadyChanged) {
      updateWakeAndWifiLock();
    }
    boolean isLoadingChanged = previousPlaybackInfo.isLoading != newPlaybackInfo.isLoading;
    if (isLoadingChanged) {
      updatePriorityTaskManagerForIsLoadingChange(newPlaybackInfo.isLoading);
    }

    if (timelineChanged) {
      listeners.queueEvent(
          Player.EVENT_TIMELINE_CHANGED,
          listener -> listener.onTimelineChanged(newPlaybackInfo.timeline, timelineChangeReason));
    }
    if (positionDiscontinuity) {
      PositionInfo previousPositionInfo =
          getPreviousPositionInfo(
              positionDiscontinuityReason, previousPlaybackInfo, oldMaskingMediaItemIndex);
      PositionInfo positionInfo = getPositionInfo(discontinuityWindowStartPositionUs);
      listeners.queueEvent(
          Player.EVENT_POSITION_DISCONTINUITY,
          listener -> {
            listener.onPositionDiscontinuity(positionDiscontinuityReason);
            listener.onPositionDiscontinuity(
                previousPositionInfo, positionInfo, positionDiscontinuityReason);
          });
    }
    if (mediaItemTransitioned) {
      @Nullable final MediaItem finalMediaItem = mediaItem;
      listeners.queueEvent(
          Player.EVENT_MEDIA_ITEM_TRANSITION,
          listener -> listener.onMediaItemTransition(finalMediaItem, mediaItemTransitionReason));
    }
    if (previousPlaybackInfo.playbackError != newPlaybackInfo.playbackError) {
      listeners.queueEvent(
          Player.EVENT_PLAYER_ERROR,
          listener -> listener.onPlayerErrorChanged(newPlaybackInfo.playbackError));
      if (newPlaybackInfo.playbackError != null) {
        listeners.queueEvent(
            Player.EVENT_PLAYER_ERROR,
            listener -> listener.onPlayerError(newPlaybackInfo.playbackError));
      }
    }
    if (previousPlaybackInfo.trackSelectorResult != newPlaybackInfo.trackSelectorResult) {
      trackSelector.onSelectionActivated(newPlaybackInfo.trackSelectorResult.info);
      listeners.queueEvent(
          Player.EVENT_TRACKS_CHANGED,
          listener -> listener.onTracksChanged(newPlaybackInfo.trackSelectorResult.tracks));
    }
    if (metadataChanged) {
      final MediaMetadata finalMediaMetadata = mediaMetadata;
      listeners.queueEvent(
          EVENT_MEDIA_METADATA_CHANGED,
          listener -> listener.onMediaMetadataChanged(finalMediaMetadata));
    }
    if (isLoadingChanged) {
      listeners.queueEvent(
          Player.EVENT_IS_LOADING_CHANGED,
          listener -> {
            listener.onLoadingChanged(newPlaybackInfo.isLoading);
            listener.onIsLoadingChanged(newPlaybackInfo.isLoading);
          });
    }
    if (playbackStateChanged || playWhenReadyChanged) {
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener ->
              listener.onPlayerStateChanged(
                  newPlaybackInfo.playWhenReady, newPlaybackInfo.playbackState));
    }
    if (playbackStateChanged) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          listener -> listener.onPlaybackStateChanged(newPlaybackInfo.playbackState));
    }
    if (playWhenReadyChanged) {
      listeners.queueEvent(
          Player.EVENT_PLAY_WHEN_READY_CHANGED,
          listener ->
              listener.onPlayWhenReadyChanged(
                  newPlaybackInfo.playWhenReady, playWhenReadyChangeReason));
    }
    if (previousPlaybackInfo.playbackSuppressionReason
        != newPlaybackInfo.playbackSuppressionReason) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
          listener ->
              listener.onPlaybackSuppressionReasonChanged(
                  newPlaybackInfo.playbackSuppressionReason));
    }
    if (previousPlaybackInfo.isPlaying() != newPlaybackInfo.isPlaying()) {
      listeners.queueEvent(
          Player.EVENT_IS_PLAYING_CHANGED,
          listener -> listener.onIsPlayingChanged(newPlaybackInfo.isPlaying()));
    }
    if (!previousPlaybackInfo.playbackParameters.equals(newPlaybackInfo.playbackParameters)) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
          listener -> listener.onPlaybackParametersChanged(newPlaybackInfo.playbackParameters));
    }
    updateAvailableCommands();
    listeners.flushEvents();

    if (previousPlaybackInfo.sleepingForOffload != newPlaybackInfo.sleepingForOffload) {
      for (AudioOffloadListener listener : audioOffloadListeners) {
        listener.onSleepingForOffloadChanged(newPlaybackInfo.sleepingForOffload);
      }
    }
  }

  private PositionInfo getPreviousPositionInfo(
      @DiscontinuityReason int positionDiscontinuityReason,
      PlaybackInfo oldPlaybackInfo,
      int oldMaskingMediaItemIndex) {
    @Nullable Object oldWindowUid = null;
    @Nullable Object oldPeriodUid = null;
    int oldMediaItemIndex = oldMaskingMediaItemIndex;
    int oldPeriodIndex = C.INDEX_UNSET;
    @Nullable MediaItem oldMediaItem = null;
    Timeline.Period oldPeriod = new Timeline.Period();
    if (!oldPlaybackInfo.timeline.isEmpty()) {
      oldPeriodUid = oldPlaybackInfo.periodId.periodUid;
      oldPlaybackInfo.timeline.getPeriodByUid(oldPeriodUid, oldPeriod);
      oldMediaItemIndex = oldPeriod.windowIndex;
      oldPeriodIndex = oldPlaybackInfo.timeline.getIndexOfPeriod(oldPeriodUid);
      oldWindowUid = oldPlaybackInfo.timeline.getWindow(oldMediaItemIndex, window).uid;
      oldMediaItem = window.mediaItem;
    }
    long oldPositionUs;
    long oldContentPositionUs;
    if (positionDiscontinuityReason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
      if (oldPlaybackInfo.periodId.isAd()) {
        // The old position is the end of the previous ad.
        oldPositionUs =
            oldPeriod.getAdDurationUs(
                oldPlaybackInfo.periodId.adGroupIndex, oldPlaybackInfo.periodId.adIndexInAdGroup);
        // The ad cue point is stored in the old requested content position.
        oldContentPositionUs = getRequestedContentPositionUs(oldPlaybackInfo);
      } else if (oldPlaybackInfo.periodId.nextAdGroupIndex != C.INDEX_UNSET) {
        // The old position is the end of a clipped content before an ad group. Use the exact ad
        // cue point as the transition position.
        oldPositionUs = getRequestedContentPositionUs(playbackInfo);
        oldContentPositionUs = oldPositionUs;
      } else {
        // The old position is the end of a Timeline period. Use the exact duration.
        oldPositionUs = oldPeriod.positionInWindowUs + oldPeriod.durationUs;
        oldContentPositionUs = oldPositionUs;
      }
    } else if (oldPlaybackInfo.periodId.isAd()) {
      oldPositionUs = oldPlaybackInfo.positionUs;
      oldContentPositionUs = getRequestedContentPositionUs(oldPlaybackInfo);
    } else {
      oldPositionUs = oldPeriod.positionInWindowUs + oldPlaybackInfo.positionUs;
      oldContentPositionUs = oldPositionUs;
    }
    return new PositionInfo(
        oldWindowUid,
        oldMediaItemIndex,
        oldMediaItem,
        oldPeriodUid,
        oldPeriodIndex,
        Util.usToMs(oldPositionUs),
        Util.usToMs(oldContentPositionUs),
        oldPlaybackInfo.periodId.adGroupIndex,
        oldPlaybackInfo.periodId.adIndexInAdGroup);
  }

  private PositionInfo getPositionInfo(long discontinuityWindowStartPositionUs) {
    @Nullable Object newWindowUid = null;
    @Nullable Object newPeriodUid = null;
    int newMediaItemIndex = getCurrentMediaItemIndex();
    int newPeriodIndex = C.INDEX_UNSET;
    @Nullable MediaItem newMediaItem = null;
    if (!playbackInfo.timeline.isEmpty()) {
      newPeriodUid = playbackInfo.periodId.periodUid;
      playbackInfo.timeline.getPeriodByUid(newPeriodUid, period);
      newPeriodIndex = playbackInfo.timeline.getIndexOfPeriod(newPeriodUid);
      newWindowUid = playbackInfo.timeline.getWindow(newMediaItemIndex, window).uid;
      newMediaItem = window.mediaItem;
    }
    long positionMs = Util.usToMs(discontinuityWindowStartPositionUs);
    return new PositionInfo(
        newWindowUid,
        newMediaItemIndex,
        newMediaItem,
        newPeriodUid,
        newPeriodIndex,
        positionMs,
        /* contentPositionMs= */ playbackInfo.periodId.isAd()
            ? Util.usToMs(getRequestedContentPositionUs(playbackInfo))
            : positionMs,
        playbackInfo.periodId.adGroupIndex,
        playbackInfo.periodId.adIndexInAdGroup);
  }

  private static long getRequestedContentPositionUs(PlaybackInfo playbackInfo) {
    Timeline.Window window = new Timeline.Window();
    Timeline.Period period = new Timeline.Period();
    playbackInfo.timeline.getPeriodByUid(playbackInfo.periodId.periodUid, period);
    return playbackInfo.requestedContentPositionUs == C.TIME_UNSET
        ? playbackInfo.timeline.getWindow(period.windowIndex, window).getDefaultPositionUs()
        : period.getPositionInWindowUs() + playbackInfo.requestedContentPositionUs;
  }

  private Pair<Boolean, Integer> evaluateMediaItemTransitionReason(
      PlaybackInfo playbackInfo,
      PlaybackInfo oldPlaybackInfo,
      boolean positionDiscontinuity,
      @DiscontinuityReason int positionDiscontinuityReason,
      boolean timelineChanged,
      boolean repeatCurrentMediaItem) {

    Timeline oldTimeline = oldPlaybackInfo.timeline;
    Timeline newTimeline = playbackInfo.timeline;
    if (newTimeline.isEmpty() && oldTimeline.isEmpty()) {
      return new Pair<>(/* isTransitioning */ false, /* mediaItemTransitionReason */ C.INDEX_UNSET);
    } else if (newTimeline.isEmpty() != oldTimeline.isEmpty()) {
      return new Pair<>(/* isTransitioning */ true, MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
    }

    int oldWindowIndex =
        oldTimeline.getPeriodByUid(oldPlaybackInfo.periodId.periodUid, period).windowIndex;
    Object oldWindowUid = oldTimeline.getWindow(oldWindowIndex, window).uid;
    int newWindowIndex =
        newTimeline.getPeriodByUid(playbackInfo.periodId.periodUid, period).windowIndex;
    Object newWindowUid = newTimeline.getWindow(newWindowIndex, window).uid;
    if (!oldWindowUid.equals(newWindowUid)) {
      @Player.MediaItemTransitionReason int transitionReason;
      if (positionDiscontinuity
          && positionDiscontinuityReason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
        transitionReason = MEDIA_ITEM_TRANSITION_REASON_AUTO;
      } else if (positionDiscontinuity
          && positionDiscontinuityReason == DISCONTINUITY_REASON_SEEK) {
        transitionReason = MEDIA_ITEM_TRANSITION_REASON_SEEK;
      } else if (timelineChanged) {
        transitionReason = MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED;
      } else {
        // A change in window uid must be justified by one of the reasons above.
        throw new IllegalStateException();
      }
      return new Pair<>(/* isTransitioning */ true, transitionReason);
    } else {
      // Only mark changes within the current item as a transition if we are repeating automatically
      // or via a seek to next/previous.
      if (positionDiscontinuity
          && positionDiscontinuityReason == DISCONTINUITY_REASON_AUTO_TRANSITION
          && oldPlaybackInfo.periodId.windowSequenceNumber
              < playbackInfo.periodId.windowSequenceNumber) {
        return new Pair<>(/* isTransitioning */ true, MEDIA_ITEM_TRANSITION_REASON_REPEAT);
      }
      if (positionDiscontinuity
          && positionDiscontinuityReason == DISCONTINUITY_REASON_SEEK
          && repeatCurrentMediaItem) {
        return new Pair<>(/* isTransitioning */ true, MEDIA_ITEM_TRANSITION_REASON_SEEK);
      }
    }
    return new Pair<>(/* isTransitioning */ false, /* mediaItemTransitionReason */ C.INDEX_UNSET);
  }

  private void updateAvailableCommands() {
    Commands previousAvailableCommands = availableCommands;
    availableCommands = Util.getAvailableCommands(wrappingPlayer, permanentAvailableCommands);
    if (!availableCommands.equals(previousAvailableCommands)) {
      listeners.queueEvent(
          Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
          listener -> listener.onAvailableCommandsChanged(availableCommands));
    }
  }

  private void setMediaSourcesInternal(
      List<MediaSource> mediaSources,
      int startWindowIndex,
      long startPositionMs,
      boolean resetToDefaultPosition) {
    int currentWindowIndex = getCurrentWindowIndexInternal(playbackInfo);
    long currentPositionMs = getCurrentPosition();
    pendingOperationAcks++;
    if (!mediaSourceHolderSnapshots.isEmpty()) {
      removeMediaSourceHolders(
          /* fromIndex= */ 0, /* toIndexExclusive= */ mediaSourceHolderSnapshots.size());
    }
    List<MediaSourceList.MediaSourceHolder> holders =
        addMediaSourceHolders(/* index= */ 0, mediaSources);
    Timeline timeline = createMaskingTimeline();
    if (!timeline.isEmpty() && startWindowIndex >= timeline.getWindowCount()) {
      throw new IllegalSeekPositionException(timeline, startWindowIndex, startPositionMs);
    }
    // Evaluate the actual start position.
    if (resetToDefaultPosition) {
      startWindowIndex = timeline.getFirstWindowIndex(shuffleModeEnabled);
      startPositionMs = C.TIME_UNSET;
    } else if (startWindowIndex == C.INDEX_UNSET) {
      startWindowIndex = currentWindowIndex;
      startPositionMs = currentPositionMs;
    }
    PlaybackInfo newPlaybackInfo =
        maskTimelineAndPosition(
            playbackInfo,
            timeline,
            maskWindowPositionMsOrGetPeriodPositionUs(timeline, startWindowIndex, startPositionMs));
    // Mask the playback state.
    int maskingPlaybackState = newPlaybackInfo.playbackState;
    if (startWindowIndex != C.INDEX_UNSET && newPlaybackInfo.playbackState != STATE_IDLE) {
      // Position reset to startWindowIndex (results in pending initial seek).
      if (timeline.isEmpty() || startWindowIndex >= timeline.getWindowCount()) {
        // Setting an empty timeline or invalid seek transitions to ended.
        maskingPlaybackState = STATE_ENDED;
      } else {
        maskingPlaybackState = STATE_BUFFERING;
      }
    }
    newPlaybackInfo = newPlaybackInfo.copyWithPlaybackState(maskingPlaybackState);
    internalPlayer.setMediaSources(
        holders, startWindowIndex, Util.msToUs(startPositionMs), shuffleOrder);
    boolean positionDiscontinuity =
        !playbackInfo.periodId.periodUid.equals(newPlaybackInfo.periodId.periodUid)
            && !playbackInfo.timeline.isEmpty();
    updatePlaybackInfo(
        newPlaybackInfo,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* positionDiscontinuity= */ positionDiscontinuity,
        DISCONTINUITY_REASON_REMOVE,
        /* discontinuityWindowStartPositionUs= */ getCurrentPositionUsInternal(newPlaybackInfo),
        /* ignored */ C.INDEX_UNSET,
        /* repeatCurrentMediaItem= */ false);
  }

  private List<MediaSourceList.MediaSourceHolder> addMediaSourceHolders(
      int index, List<MediaSource> mediaSources) {
    List<MediaSourceList.MediaSourceHolder> holders = new ArrayList<>();
    for (int i = 0; i < mediaSources.size(); i++) {
      MediaSourceList.MediaSourceHolder holder =
          new MediaSourceList.MediaSourceHolder(mediaSources.get(i), useLazyPreparation);
      holders.add(holder);
      mediaSourceHolderSnapshots.add(
          i + index, new MediaSourceHolderSnapshot(holder.uid, holder.mediaSource));
    }
    shuffleOrder =
        shuffleOrder.cloneAndInsert(
            /* insertionIndex= */ index, /* insertionCount= */ holders.size());
    return holders;
  }

  private PlaybackInfo addMediaSourcesInternal(
      PlaybackInfo playbackInfo, int index, List<MediaSource> mediaSources) {
    Timeline oldTimeline = playbackInfo.timeline;
    pendingOperationAcks++;
    List<MediaSourceList.MediaSourceHolder> holders = addMediaSourceHolders(index, mediaSources);
    Timeline newTimeline = createMaskingTimeline();
    PlaybackInfo newPlaybackInfo =
        maskTimelineAndPosition(
            playbackInfo,
            newTimeline,
            getPeriodPositionUsAfterTimelineChanged(
                oldTimeline,
                newTimeline,
                getCurrentWindowIndexInternal(playbackInfo),
                getContentPositionInternal(playbackInfo)));
    internalPlayer.addMediaSources(index, holders, shuffleOrder);
    return newPlaybackInfo;
  }

  private PlaybackInfo removeMediaItemsInternal(
      PlaybackInfo playbackInfo, int fromIndex, int toIndex) {
    int currentIndex = getCurrentWindowIndexInternal(playbackInfo);
    long contentPositionMs = getContentPositionInternal(playbackInfo);
    Timeline oldTimeline = playbackInfo.timeline;
    int currentMediaSourceCount = mediaSourceHolderSnapshots.size();
    pendingOperationAcks++;
    removeMediaSourceHolders(fromIndex, /* toIndexExclusive= */ toIndex);
    Timeline newTimeline = createMaskingTimeline();
    PlaybackInfo newPlaybackInfo =
        maskTimelineAndPosition(
            playbackInfo,
            newTimeline,
            getPeriodPositionUsAfterTimelineChanged(
                oldTimeline, newTimeline, currentIndex, contentPositionMs));
    // Player transitions to STATE_ENDED if the current index is part of the removed tail.
    final boolean transitionsToEnded =
        newPlaybackInfo.playbackState != STATE_IDLE
            && newPlaybackInfo.playbackState != STATE_ENDED
            && fromIndex < toIndex
            && toIndex == currentMediaSourceCount
            && currentIndex >= newPlaybackInfo.timeline.getWindowCount();
    if (transitionsToEnded) {
      newPlaybackInfo = newPlaybackInfo.copyWithPlaybackState(STATE_ENDED);
    }
    internalPlayer.removeMediaSources(fromIndex, toIndex, shuffleOrder);
    return newPlaybackInfo;
  }

  private void removeMediaSourceHolders(int fromIndex, int toIndexExclusive) {
    for (int i = toIndexExclusive - 1; i >= fromIndex; i--) {
      mediaSourceHolderSnapshots.remove(i);
    }
    shuffleOrder = shuffleOrder.cloneAndRemove(fromIndex, toIndexExclusive);
  }

  private Timeline createMaskingTimeline() {
    return new PlaylistTimeline(mediaSourceHolderSnapshots, shuffleOrder);
  }

  private PlaybackInfo maskTimelineAndPosition(
      PlaybackInfo playbackInfo, Timeline timeline, @Nullable Pair<Object, Long> periodPositionUs) {
    checkArgument(timeline.isEmpty() || periodPositionUs != null);
    // Get the old timeline and position before updating playbackInfo.
    Timeline oldTimeline = playbackInfo.timeline;
    long oldContentPositionMs = getContentPositionInternal(playbackInfo);
    // Mask the timeline.
    playbackInfo = playbackInfo.copyWithTimeline(timeline);

    if (timeline.isEmpty()) {
      // Reset periodId and loadingPeriodId.
      MediaPeriodId dummyMediaPeriodId = PlaybackInfo.getDummyPeriodForEmptyTimeline();
      long positionUs = Util.msToUs(maskingWindowPositionMs);
      playbackInfo =
          playbackInfo.copyWithNewPosition(
              dummyMediaPeriodId,
              positionUs,
              /* requestedContentPositionUs= */ positionUs,
              /* discontinuityStartPositionUs= */ positionUs,
              /* totalBufferedDurationUs= */ 0,
              TrackGroupArray.EMPTY,
              emptyTrackSelectorResult,
              /* staticMetadata= */ ImmutableList.of());
      playbackInfo = playbackInfo.copyWithLoadingMediaPeriodId(dummyMediaPeriodId);
      playbackInfo.bufferedPositionUs = playbackInfo.positionUs;
      return playbackInfo;
    }

    Object oldPeriodUid = playbackInfo.periodId.periodUid;
    boolean playingPeriodChanged = !oldPeriodUid.equals(castNonNull(periodPositionUs).first);
    MediaPeriodId newPeriodId =
        playingPeriodChanged ? new MediaPeriodId(periodPositionUs.first) : playbackInfo.periodId;
    long newContentPositionUs = periodPositionUs.second;
    long oldContentPositionUs = Util.msToUs(oldContentPositionMs);
    if (!oldTimeline.isEmpty()) {
      oldContentPositionUs -=
          oldTimeline.getPeriodByUid(oldPeriodUid, period).getPositionInWindowUs();
    }

    if (playingPeriodChanged || newContentPositionUs < oldContentPositionUs) {
      checkState(!newPeriodId.isAd());
      // The playing period changes or a backwards seek within the playing period occurs.
      playbackInfo =
          playbackInfo.copyWithNewPosition(
              newPeriodId,
              /* positionUs= */ newContentPositionUs,
              /* requestedContentPositionUs= */ newContentPositionUs,
              /* discontinuityStartPositionUs= */ newContentPositionUs,
              /* totalBufferedDurationUs= */ 0,
              playingPeriodChanged ? TrackGroupArray.EMPTY : playbackInfo.trackGroups,
              playingPeriodChanged ? emptyTrackSelectorResult : playbackInfo.trackSelectorResult,
              playingPeriodChanged ? ImmutableList.of() : playbackInfo.staticMetadata);
      playbackInfo = playbackInfo.copyWithLoadingMediaPeriodId(newPeriodId);
      playbackInfo.bufferedPositionUs = newContentPositionUs;
    } else if (newContentPositionUs == oldContentPositionUs) {
      // Period position remains unchanged.
      int loadingPeriodIndex =
          timeline.getIndexOfPeriod(playbackInfo.loadingMediaPeriodId.periodUid);
      if (loadingPeriodIndex == C.INDEX_UNSET
          || timeline.getPeriod(loadingPeriodIndex, period).windowIndex
              != timeline.getPeriodByUid(newPeriodId.periodUid, period).windowIndex) {
        // Discard periods after the playing period, if the loading period is discarded or the
        // playing and loading period are not in the same window.
        timeline.getPeriodByUid(newPeriodId.periodUid, period);
        long maskedBufferedPositionUs =
            newPeriodId.isAd()
                ? period.getAdDurationUs(newPeriodId.adGroupIndex, newPeriodId.adIndexInAdGroup)
                : period.durationUs;
        playbackInfo =
            playbackInfo.copyWithNewPosition(
                newPeriodId,
                /* positionUs= */ playbackInfo.positionUs,
                /* requestedContentPositionUs= */ playbackInfo.positionUs,
                playbackInfo.discontinuityStartPositionUs,
                /* totalBufferedDurationUs= */ maskedBufferedPositionUs - playbackInfo.positionUs,
                playbackInfo.trackGroups,
                playbackInfo.trackSelectorResult,
                playbackInfo.staticMetadata);
        playbackInfo = playbackInfo.copyWithLoadingMediaPeriodId(newPeriodId);
        playbackInfo.bufferedPositionUs = maskedBufferedPositionUs;
      }
    } else {
      checkState(!newPeriodId.isAd());
      // A forward seek within the playing period (timeline did not change).
      long maskedTotalBufferedDurationUs =
          max(
              0,
              playbackInfo.totalBufferedDurationUs - (newContentPositionUs - oldContentPositionUs));
      long maskedBufferedPositionUs = playbackInfo.bufferedPositionUs;
      if (playbackInfo.loadingMediaPeriodId.equals(playbackInfo.periodId)) {
        maskedBufferedPositionUs = newContentPositionUs + maskedTotalBufferedDurationUs;
      }
      playbackInfo =
          playbackInfo.copyWithNewPosition(
              newPeriodId,
              /* positionUs= */ newContentPositionUs,
              /* requestedContentPositionUs= */ newContentPositionUs,
              /* discontinuityStartPositionUs= */ newContentPositionUs,
              maskedTotalBufferedDurationUs,
              playbackInfo.trackGroups,
              playbackInfo.trackSelectorResult,
              playbackInfo.staticMetadata);
      playbackInfo.bufferedPositionUs = maskedBufferedPositionUs;
    }
    return playbackInfo;
  }

  @Nullable
  private Pair<Object, Long> getPeriodPositionUsAfterTimelineChanged(
      Timeline oldTimeline,
      Timeline newTimeline,
      int currentWindowIndexInternal,
      long contentPositionMs) {
    if (oldTimeline.isEmpty() || newTimeline.isEmpty()) {
      boolean isCleared = !oldTimeline.isEmpty() && newTimeline.isEmpty();
      return maskWindowPositionMsOrGetPeriodPositionUs(
          newTimeline,
          isCleared ? C.INDEX_UNSET : currentWindowIndexInternal,
          isCleared ? C.TIME_UNSET : contentPositionMs);
    }
    @Nullable
    Pair<Object, Long> oldPeriodPositionUs =
        oldTimeline.getPeriodPositionUs(
            window, period, currentWindowIndexInternal, Util.msToUs(contentPositionMs));
    Object periodUid = castNonNull(oldPeriodPositionUs).first;
    if (newTimeline.getIndexOfPeriod(periodUid) != C.INDEX_UNSET) {
      // The old period position is still available in the new timeline.
      return oldPeriodPositionUs;
    }
    // Period uid not found in new timeline. Try to get subsequent period.
    @Nullable
    Object nextPeriodUid =
        ExoPlayerImplInternal.resolveSubsequentPeriod(
            window, period, repeatMode, shuffleModeEnabled, periodUid, oldTimeline, newTimeline);
    if (nextPeriodUid != null) {
      // Reset position to the default position of the window of the subsequent period.
      newTimeline.getPeriodByUid(nextPeriodUid, period);
      return maskWindowPositionMsOrGetPeriodPositionUs(
          newTimeline,
          period.windowIndex,
          newTimeline.getWindow(period.windowIndex, window).getDefaultPositionMs());
    } else {
      // No subsequent period found and the new timeline is not empty. Use the default position.
      return maskWindowPositionMsOrGetPeriodPositionUs(
          newTimeline, /* windowIndex= */ C.INDEX_UNSET, /* windowPositionMs= */ C.TIME_UNSET);
    }
  }

  @Nullable
  private Pair<Object, Long> maskWindowPositionMsOrGetPeriodPositionUs(
      Timeline timeline, int windowIndex, long windowPositionMs) {
    if (timeline.isEmpty()) {
      // If empty we store the initial seek in the masking variables.
      maskingWindowIndex = windowIndex;
      maskingWindowPositionMs = windowPositionMs == C.TIME_UNSET ? 0 : windowPositionMs;
      maskingPeriodIndex = 0;
      return null;
    }
    if (windowIndex == C.INDEX_UNSET || windowIndex >= timeline.getWindowCount()) {
      // Use default position of timeline if window index still unset or if a previous initial seek
      // now turns out to be invalid.
      windowIndex = timeline.getFirstWindowIndex(shuffleModeEnabled);
      windowPositionMs = timeline.getWindow(windowIndex, window).getDefaultPositionMs();
    }
    return timeline.getPeriodPositionUs(window, period, windowIndex, Util.msToUs(windowPositionMs));
  }

  private long periodPositionUsToWindowPositionUs(
      Timeline timeline, MediaPeriodId periodId, long positionUs) {
    timeline.getPeriodByUid(periodId.periodUid, period);
    positionUs += period.getPositionInWindowUs();
    return positionUs;
  }

  private PlayerMessage createMessageInternal(Target target) {
    int currentWindowIndex = getCurrentWindowIndexInternal(playbackInfo);
    return new PlayerMessage(
        internalPlayer,
        target,
        playbackInfo.timeline,
        currentWindowIndex == C.INDEX_UNSET ? 0 : currentWindowIndex,
        clock,
        internalPlayer.getPlaybackLooper());
  }

  /**
   * Builds a {@link MediaMetadata} from the main sources.
   *
   * <p>{@link MediaItem} {@link MediaMetadata} is prioritized, with any gaps/missing fields
   * populated by metadata from static ({@link TrackGroup} {@link Format}) and dynamic ({@link
   * MetadataOutput#onMetadata(Metadata)}) sources.
   */
  private MediaMetadata buildUpdatedMediaMetadata() {
    Timeline timeline = getCurrentTimeline();
    if (timeline.isEmpty()) {
      return staticAndDynamicMediaMetadata;
    }
    MediaItem mediaItem = timeline.getWindow(getCurrentMediaItemIndex(), window).mediaItem;
    // MediaItem metadata is prioritized over metadata within the media.
    return staticAndDynamicMediaMetadata.buildUpon().populate(mediaItem.mediaMetadata).build();
  }

  private void removeSurfaceCallbacks() {
    if (sphericalGLSurfaceView != null) {
      createMessageInternal(frameMetadataListener)
          .setType(FrameMetadataListener.MSG_SET_SPHERICAL_SURFACE_VIEW)
          .setPayload(null)
          .send();
      sphericalGLSurfaceView.removeVideoSurfaceListener(componentListener);
      sphericalGLSurfaceView = null;
    }
    if (textureView != null) {
      if (textureView.getSurfaceTextureListener() != componentListener) {
        Log.w(TAG, "SurfaceTextureListener already unset or replaced.");
      } else {
        textureView.setSurfaceTextureListener(null);
      }
      textureView = null;
    }
    if (surfaceHolder != null) {
      surfaceHolder.removeCallback(componentListener);
      surfaceHolder = null;
    }
  }

  private void setSurfaceTextureInternal(SurfaceTexture surfaceTexture) {
    Surface surface = new Surface(surfaceTexture);
    setVideoOutputInternal(surface);
    ownedSurface = surface;
  }

  private void setVideoOutputInternal(@Nullable Object videoOutput) {
    // Note: We don't turn this method into a no-op if the output is being replaced with itself so
    // as to ensure onRenderedFirstFrame callbacks are still called in this case.
    List<PlayerMessage> messages = new ArrayList<>();
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == TRACK_TYPE_VIDEO) {
        messages.add(
            createMessageInternal(renderer)
                .setType(MSG_SET_VIDEO_OUTPUT)
                .setPayload(videoOutput)
                .send());
      }
    }
    boolean messageDeliveryTimedOut = false;
    if (this.videoOutput != null && this.videoOutput != videoOutput) {
      // We're replacing an output. Block to ensure that this output will not be accessed by the
      // renderers after this method returns.
      try {
        for (PlayerMessage message : messages) {
          message.blockUntilDelivered(detachSurfaceTimeoutMs);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (TimeoutException e) {
        messageDeliveryTimedOut = true;
      }
      if (this.videoOutput == ownedSurface) {
        // We're replacing a surface that we are responsible for releasing.
        ownedSurface.release();
        ownedSurface = null;
      }
    }
    this.videoOutput = videoOutput;
    if (messageDeliveryTimedOut) {
      stopInternal(
          ExoPlaybackException.createForUnexpected(
              new ExoTimeoutException(ExoTimeoutException.TIMEOUT_OPERATION_DETACH_SURFACE),
              PlaybackException.ERROR_CODE_TIMEOUT));
    }
  }

  /**
   * Sets the holder of the surface that will be displayed to the user, but which should
   * <em>not</em> be the output for video renderers. This case occurs when video frames need to be
   * rendered to an intermediate surface (which is not the one held by the provided holder).
   *
   * @param nonVideoOutputSurfaceHolder The holder of the surface that will eventually be displayed
   *     to the user.
   */
  private void setNonVideoOutputSurfaceHolderInternal(SurfaceHolder nonVideoOutputSurfaceHolder) {
    // Although we won't use the view's surface directly as the video output, still use the holder
    // to query the surface size, to be informed in changes to the size via componentListener, and
    // for equality checking in clearVideoSurfaceHolder.
    surfaceHolderSurfaceIsVideoOutput = false;
    surfaceHolder = nonVideoOutputSurfaceHolder;
    surfaceHolder.addCallback(componentListener);
    Surface surface = surfaceHolder.getSurface();
    if (surface != null && surface.isValid()) {
      Rect surfaceSize = surfaceHolder.getSurfaceFrame();
      maybeNotifySurfaceSizeChanged(surfaceSize.width(), surfaceSize.height());
    } else {
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    }
  }

  private void maybeNotifySurfaceSizeChanged(int width, int height) {
    if (width != surfaceSize.getWidth() || height != surfaceSize.getHeight()) {
      surfaceSize = new Size(width, height);
      listeners.sendEvent(
          EVENT_SURFACE_SIZE_CHANGED, listener -> listener.onSurfaceSizeChanged(width, height));
      sendRendererMessage(
          TRACK_TYPE_VIDEO, MSG_SET_VIDEO_OUTPUT_RESOLUTION, new Size(width, height));
    }
  }

  private void sendVolumeToRenderers() {
    float scaledVolume = volume * audioFocusManager.getVolumeMultiplier();
    sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_VOLUME, scaledVolume);
  }

  private void updatePlayWhenReady(
      boolean playWhenReady,
      @AudioFocusManager.PlayerCommand int playerCommand,
      @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason) {
    playWhenReady = playWhenReady && playerCommand != AudioFocusManager.PLAYER_COMMAND_DO_NOT_PLAY;
    @PlaybackSuppressionReason
    int playbackSuppressionReason = computePlaybackSuppressionReason(playWhenReady, playerCommand);
    if (playbackInfo.playWhenReady == playWhenReady
        && playbackInfo.playbackSuppressionReason == playbackSuppressionReason) {
      return;
    }
    updatePlaybackInfoForPlayWhenReadyAndSuppressionReasonStates(
        playWhenReady, playWhenReadyChangeReason, playbackSuppressionReason);
  }

  private void updatePlaybackInfoForPlayWhenReadyAndSuppressionReasonStates(
      boolean playWhenReady,
      @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason,
      @PlaybackSuppressionReason int playbackSuppressionReason) {
    pendingOperationAcks++;
    // Position estimation and copy must occur before changing/masking playback state.
    PlaybackInfo newPlaybackInfo =
        this.playbackInfo.sleepingForOffload
            ? this.playbackInfo.copyWithEstimatedPosition()
            : this.playbackInfo;
    newPlaybackInfo =
        newPlaybackInfo.copyWithPlayWhenReady(playWhenReady, playbackSuppressionReason);
    internalPlayer.setPlayWhenReady(playWhenReady, playbackSuppressionReason);
    updatePlaybackInfo(
        newPlaybackInfo,
        /* ignored */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        playWhenReadyChangeReason,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ C.TIME_UNSET,
        /* ignored */ C.INDEX_UNSET,
        /* repeatCurrentMediaItem= */ false);
  }

  @PlaybackSuppressionReason
  private int computePlaybackSuppressionReason(
      boolean playWhenReady, @AudioFocusManager.PlayerCommand int playerCommand) {
    if (playWhenReady && playerCommand != AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY) {
      return Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS;
    }
    if (suppressPlaybackOnUnsuitableOutput) {
      if (playWhenReady && !hasSupportedAudioOutput()) {
        return Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT;
      }
      if (!playWhenReady
          && playbackInfo.playbackSuppressionReason
              == PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT) {
        return Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT;
      }
    }
    return Player.PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  private boolean hasSupportedAudioOutput() {
    if (audioManager == null || Util.SDK_INT < 23) {
      // The Audio Manager API to determine the list of connected audio devices is available only in
      // API >= 23.
      return true;
    }
    return Api23.isSuitableAudioOutputPresentInAudioDeviceInfoList(
        applicationContext, audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
  }

  private void updateWakeAndWifiLock() {
    @State int playbackState = getPlaybackState();
    switch (playbackState) {
      case Player.STATE_READY:
      case Player.STATE_BUFFERING:
        boolean isSleeping = isSleepingForOffload();
        wakeLockManager.setStayAwake(getPlayWhenReady() && !isSleeping);
        // The wifi lock is not released while sleeping to avoid interrupting downloads.
        wifiLockManager.setStayAwake(getPlayWhenReady());
        break;
      case Player.STATE_ENDED:
      case Player.STATE_IDLE:
        wakeLockManager.setStayAwake(false);
        wifiLockManager.setStayAwake(false);
        break;
      default:
        throw new IllegalStateException();
    }
  }

  private void verifyApplicationThread() {
    // The constructor may be executed on a background thread. Wait with accessing the player from
    // the app thread until the constructor finished executing.
    constructorFinished.blockUninterruptible();
    if (Thread.currentThread() != getApplicationLooper().getThread()) {
      String message =
          Util.formatInvariant(
              "Player is accessed on the wrong thread.\n"
                  + "Current thread: '%s'\n"
                  + "Expected thread: '%s'\n"
                  + "See https://developer.android.com/guide/topics/media/issues/"
                  + "player-accessed-on-wrong-thread",
              Thread.currentThread().getName(), getApplicationLooper().getThread().getName());
      if (throwsWhenUsingWrongThread) {
        throw new IllegalStateException(message);
      }
      Log.w(TAG, message, hasNotifiedFullWrongThreadWarning ? null : new IllegalStateException());
      hasNotifiedFullWrongThreadWarning = true;
    }
  }

  private void sendRendererMessage(
      @C.TrackType int trackType, int messageType, @Nullable Object payload) {
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == trackType) {
        createMessageInternal(renderer).setType(messageType).setPayload(payload).send();
      }
    }
  }

  /**
   * Initializes {@link #keepSessionIdAudioTrack} to keep an audio session ID alive. If the audio
   * session ID is {@link C#AUDIO_SESSION_ID_UNSET} then a new audio session ID is generated.
   *
   * <p>Use of this method is only required on API level 21 and earlier.
   *
   * @param audioSessionId The audio session ID, or {@link C#AUDIO_SESSION_ID_UNSET} to generate a
   *     new one.
   * @return The audio session ID.
   */
  private int initializeKeepSessionIdAudioTrack(int audioSessionId) {
    if (keepSessionIdAudioTrack != null
        && keepSessionIdAudioTrack.getAudioSessionId() != audioSessionId) {
      keepSessionIdAudioTrack.release();
      keepSessionIdAudioTrack = null;
    }
    if (keepSessionIdAudioTrack == null) {
      int sampleRate = 4000; // Minimum sample rate supported by the platform.
      int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
      @C.PcmEncoding int encoding = C.ENCODING_PCM_16BIT;
      int bufferSize = 2; // Use a two byte buffer, as it is not actually used for playback.
      keepSessionIdAudioTrack =
          new AudioTrack(
              C.STREAM_TYPE_DEFAULT,
              sampleRate,
              channelConfig,
              encoding,
              bufferSize,
              AudioTrack.MODE_STATIC,
              audioSessionId);
    }
    return keepSessionIdAudioTrack.getAudioSessionId();
  }

  private void updatePriorityTaskManagerForIsLoadingChange(boolean isLoading) {
    if (priorityTaskManager != null) {
      if (isLoading && !isPriorityTaskManagerRegistered) {
        priorityTaskManager.add(C.PRIORITY_PLAYBACK);
        isPriorityTaskManagerRegistered = true;
      } else if (!isLoading && isPriorityTaskManagerRegistered) {
        priorityTaskManager.remove(C.PRIORITY_PLAYBACK);
        isPriorityTaskManagerRegistered = false;
      }
    }
  }

  private boolean canUpdateMediaSourcesWithMediaItems(
      int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    if (toIndex - fromIndex != mediaItems.size()) {
      // Number of items doesn't match.
      return false;
    }
    for (int i = fromIndex; i < toIndex; i++) {
      MediaSource mediaSource = mediaSourceHolderSnapshots.get(i).mediaSource;
      if (!mediaSource.canUpdateMediaItem(mediaItems.get(i - fromIndex))) {
        return false;
      }
    }
    return true;
  }

  private void updateMediaSourcesWithMediaItems(
      int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    pendingOperationAcks++;
    internalPlayer.updateMediaSourcesWithMediaItems(fromIndex, toIndex, mediaItems);
    for (int i = fromIndex; i < toIndex; i++) {
      MediaSourceHolderSnapshot snapshot = mediaSourceHolderSnapshots.get(i);
      snapshot.updateTimeline(
          new TimelineWithUpdatedMediaItem(snapshot.getTimeline(), mediaItems.get(i - fromIndex)));
    }
    Timeline newTimeline = createMaskingTimeline();
    PlaybackInfo newPlaybackInfo = playbackInfo.copyWithTimeline(newTimeline);
    updatePlaybackInfo(
        newPlaybackInfo,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* ignored */ false,
        /* ignored */ DISCONTINUITY_REASON_REMOVE,
        /* ignored */ C.TIME_UNSET,
        /* ignored */ C.INDEX_UNSET,
        /* ignored */ false);
  }

  private static DeviceInfo createDeviceInfo(@Nullable StreamVolumeManager streamVolumeManager) {
    return new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL)
        .setMinVolume(streamVolumeManager != null ? streamVolumeManager.getMinVolume() : 0)
        .setMaxVolume(streamVolumeManager != null ? streamVolumeManager.getMaxVolume() : 0)
        .build();
  }

  private static int getPlayWhenReadyChangeReason(boolean playWhenReady, int playerCommand) {
    return playWhenReady && playerCommand != AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY
        ? PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
        : PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
  }

  private static final class MediaSourceHolderSnapshot implements MediaSourceInfoHolder {

    private final Object uid;
    private final MediaSource mediaSource;

    private Timeline timeline;

    public MediaSourceHolderSnapshot(Object uid, MaskingMediaSource mediaSource) {
      this.uid = uid;
      this.mediaSource = mediaSource;
      this.timeline = mediaSource.getTimeline();
    }

    @Override
    public Object getUid() {
      return uid;
    }

    @Override
    public Timeline getTimeline() {
      return timeline;
    }

    public void updateTimeline(Timeline timeline) {
      this.timeline = timeline;
    }
  }

  private final class ComponentListener
      implements VideoRendererEventListener,
          AudioRendererEventListener,
          TextOutput,
          MetadataOutput,
          SurfaceHolder.Callback,
          TextureView.SurfaceTextureListener,
          SphericalGLSurfaceView.VideoSurfaceListener,
          AudioFocusManager.PlayerControl,
          AudioBecomingNoisyManager.EventListener,
          StreamVolumeManager.Listener,
          AudioOffloadListener {

    // VideoRendererEventListener implementation

    @Override
    public void onVideoEnabled(DecoderCounters counters) {
      videoDecoderCounters = counters;
      analyticsCollector.onVideoEnabled(counters);
    }

    @Override
    public void onVideoDecoderInitialized(
        String decoderName, long initializedTimestampMs, long initializationDurationMs) {
      analyticsCollector.onVideoDecoderInitialized(
          decoderName, initializedTimestampMs, initializationDurationMs);
    }

    @Override
    public void onVideoInputFormatChanged(
        Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
      videoFormat = format;
      analyticsCollector.onVideoInputFormatChanged(format, decoderReuseEvaluation);
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
      analyticsCollector.onDroppedFrames(count, elapsed);
    }

    @Override
    public void onVideoSizeChanged(VideoSize newVideoSize) {
      videoSize = newVideoSize;
      listeners.sendEvent(
          EVENT_VIDEO_SIZE_CHANGED, listener -> listener.onVideoSizeChanged(newVideoSize));
    }

    @Override
    public void onRenderedFirstFrame(Object output, long renderTimeMs) {
      analyticsCollector.onRenderedFirstFrame(output, renderTimeMs);
      if (videoOutput == output) {
        listeners.sendEvent(EVENT_RENDERED_FIRST_FRAME, Listener::onRenderedFirstFrame);
      }
    }

    @Override
    public void onVideoDecoderReleased(String decoderName) {
      analyticsCollector.onVideoDecoderReleased(decoderName);
    }

    @Override
    public void onVideoDisabled(DecoderCounters counters) {
      analyticsCollector.onVideoDisabled(counters);
      videoFormat = null;
      videoDecoderCounters = null;
    }

    @Override
    public void onVideoFrameProcessingOffset(long totalProcessingOffsetUs, int frameCount) {
      analyticsCollector.onVideoFrameProcessingOffset(totalProcessingOffsetUs, frameCount);
    }

    @Override
    public void onVideoCodecError(Exception videoCodecError) {
      analyticsCollector.onVideoCodecError(videoCodecError);
    }

    // AudioRendererEventListener implementation

    @Override
    public void onAudioEnabled(DecoderCounters counters) {
      audioDecoderCounters = counters;
      analyticsCollector.onAudioEnabled(counters);
    }

    @Override
    public void onAudioDecoderInitialized(
        String decoderName, long initializedTimestampMs, long initializationDurationMs) {
      analyticsCollector.onAudioDecoderInitialized(
          decoderName, initializedTimestampMs, initializationDurationMs);
    }

    @Override
    public void onAudioInputFormatChanged(
        Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
      audioFormat = format;
      analyticsCollector.onAudioInputFormatChanged(format, decoderReuseEvaluation);
    }

    @Override
    public void onAudioPositionAdvancing(long playoutStartSystemTimeMs) {
      analyticsCollector.onAudioPositionAdvancing(playoutStartSystemTimeMs);
    }

    @Override
    public void onAudioUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
      analyticsCollector.onAudioUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
    }

    @Override
    public void onAudioDecoderReleased(String decoderName) {
      analyticsCollector.onAudioDecoderReleased(decoderName);
    }

    @Override
    public void onAudioDisabled(DecoderCounters counters) {
      analyticsCollector.onAudioDisabled(counters);
      audioFormat = null;
      audioDecoderCounters = null;
    }

    @Override
    public void onSkipSilenceEnabledChanged(boolean newSkipSilenceEnabled) {
      if (skipSilenceEnabled == newSkipSilenceEnabled) {
        return;
      }
      skipSilenceEnabled = newSkipSilenceEnabled;
      listeners.sendEvent(
          EVENT_SKIP_SILENCE_ENABLED_CHANGED,
          listener -> listener.onSkipSilenceEnabledChanged(newSkipSilenceEnabled));
    }

    @Override
    public void onAudioSinkError(Exception audioSinkError) {
      analyticsCollector.onAudioSinkError(audioSinkError);
    }

    @Override
    public void onAudioCodecError(Exception audioCodecError) {
      analyticsCollector.onAudioCodecError(audioCodecError);
    }

    @Override
    public void onAudioTrackInitialized(AudioSink.AudioTrackConfig audioTrackConfig) {
      analyticsCollector.onAudioTrackInitialized(audioTrackConfig);
    }

    @Override
    public void onAudioTrackReleased(AudioSink.AudioTrackConfig audioTrackConfig) {
      analyticsCollector.onAudioTrackReleased(audioTrackConfig);
    }

    // TextOutput implementation
    @SuppressWarnings("deprecation") // Intentionally forwarding deprecating callback
    @Override
    public void onCues(List<Cue> cues) {
      listeners.sendEvent(EVENT_CUES, listener -> listener.onCues(cues));
    }

    @Override
    public void onCues(CueGroup cueGroup) {
      currentCueGroup = cueGroup;
      listeners.sendEvent(EVENT_CUES, listener -> listener.onCues(cueGroup));
    }

    // MetadataOutput implementation

    @Override
    public void onMetadata(Metadata metadata) {
      staticAndDynamicMediaMetadata =
          staticAndDynamicMediaMetadata.buildUpon().populateFromMetadata(metadata).build();
      MediaMetadata newMediaMetadata = buildUpdatedMediaMetadata();
      if (!newMediaMetadata.equals(mediaMetadata)) {
        mediaMetadata = newMediaMetadata;
        listeners.queueEvent(
            EVENT_MEDIA_METADATA_CHANGED,
            listener -> listener.onMediaMetadataChanged(mediaMetadata));
      }
      listeners.queueEvent(EVENT_METADATA, listener -> listener.onMetadata(metadata));
      listeners.flushEvents();
    }

    // SurfaceHolder.Callback implementation

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
      if (surfaceHolderSurfaceIsVideoOutput) {
        setVideoOutputInternal(holder.getSurface());
      }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
      if (surfaceHolderSurfaceIsVideoOutput) {
        setVideoOutputInternal(/* videoOutput= */ null);
      }
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    }

    // TextureView.SurfaceTextureListener implementation

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
      setSurfaceTextureInternal(surfaceTexture);
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
      setVideoOutputInternal(/* videoOutput= */ null);
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
      // Do nothing.
    }

    // SphericalGLSurfaceView.VideoSurfaceListener

    @Override
    public void onVideoSurfaceCreated(Surface surface) {
      setVideoOutputInternal(surface);
    }

    @Override
    public void onVideoSurfaceDestroyed(Surface surface) {
      setVideoOutputInternal(/* videoOutput= */ null);
    }

    // AudioFocusManager.PlayerControl implementation

    @Override
    public void setVolumeMultiplier(float volumeMultiplier) {
      sendVolumeToRenderers();
    }

    @Override
    public void executePlayerCommand(@AudioFocusManager.PlayerCommand int playerCommand) {
      boolean playWhenReady = getPlayWhenReady();
      updatePlayWhenReady(
          playWhenReady, playerCommand, getPlayWhenReadyChangeReason(playWhenReady, playerCommand));
    }

    // AudioBecomingNoisyManager.EventListener implementation.

    @Override
    public void onAudioBecomingNoisy() {
      updatePlayWhenReady(
          /* playWhenReady= */ false,
          AudioFocusManager.PLAYER_COMMAND_DO_NOT_PLAY,
          Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY);
    }

    // StreamVolumeManager.Listener implementation.

    @Override
    public void onStreamTypeChanged(@C.StreamType int streamType) {
      DeviceInfo newDeviceInfo = createDeviceInfo(streamVolumeManager);
      if (!newDeviceInfo.equals(deviceInfo)) {
        deviceInfo = newDeviceInfo;
        listeners.sendEvent(
            EVENT_DEVICE_INFO_CHANGED, listener -> listener.onDeviceInfoChanged(newDeviceInfo));
      }
    }

    @Override
    public void onStreamVolumeChanged(int streamVolume, boolean streamMuted) {
      listeners.sendEvent(
          EVENT_DEVICE_VOLUME_CHANGED,
          listener -> listener.onDeviceVolumeChanged(streamVolume, streamMuted));
    }

    // Player.AudioOffloadListener implementation.

    @Override
    public void onSleepingForOffloadChanged(boolean sleepingForOffload) {
      updateWakeAndWifiLock();
    }
  }

  /** Listeners that are called on the playback thread. */
  private static final class FrameMetadataListener
      implements VideoFrameMetadataListener, CameraMotionListener, PlayerMessage.Target {

    public static final @MessageType int MSG_SET_VIDEO_FRAME_METADATA_LISTENER =
        Renderer.MSG_SET_VIDEO_FRAME_METADATA_LISTENER;

    public static final @MessageType int MSG_SET_CAMERA_MOTION_LISTENER =
        Renderer.MSG_SET_CAMERA_MOTION_LISTENER;

    public static final @MessageType int MSG_SET_SPHERICAL_SURFACE_VIEW = Renderer.MSG_CUSTOM_BASE;

    @Nullable private VideoFrameMetadataListener videoFrameMetadataListener;
    @Nullable private CameraMotionListener cameraMotionListener;
    @Nullable private VideoFrameMetadataListener internalVideoFrameMetadataListener;
    @Nullable private CameraMotionListener internalCameraMotionListener;

    @Override
    public void handleMessage(@MessageType int messageType, @Nullable Object message) {
      switch (messageType) {
        case MSG_SET_VIDEO_FRAME_METADATA_LISTENER:
          videoFrameMetadataListener = (VideoFrameMetadataListener) message;
          break;
        case MSG_SET_CAMERA_MOTION_LISTENER:
          cameraMotionListener = (CameraMotionListener) message;
          break;
        case MSG_SET_SPHERICAL_SURFACE_VIEW:
          @Nullable SphericalGLSurfaceView surfaceView = (SphericalGLSurfaceView) message;
          if (surfaceView == null) {
            internalVideoFrameMetadataListener = null;
            internalCameraMotionListener = null;
          } else {
            internalVideoFrameMetadataListener = surfaceView.getVideoFrameMetadataListener();
            internalCameraMotionListener = surfaceView.getCameraMotionListener();
          }
          break;
        case Renderer.MSG_SET_AUDIO_ATTRIBUTES:
        case Renderer.MSG_SET_AUDIO_SESSION_ID:
        case Renderer.MSG_SET_AUX_EFFECT_INFO:
        case Renderer.MSG_SET_CHANGE_FRAME_RATE_STRATEGY:
        case Renderer.MSG_SET_SCALING_MODE:
        case Renderer.MSG_SET_SKIP_SILENCE_ENABLED:
        case Renderer.MSG_SET_VIDEO_OUTPUT:
        case Renderer.MSG_SET_VOLUME:
        case Renderer.MSG_SET_WAKEUP_LISTENER:
        default:
          break;
      }
    }

    // VideoFrameMetadataListener

    @Override
    public void onVideoFrameAboutToBeRendered(
        long presentationTimeUs,
        long releaseTimeNs,
        Format format,
        @Nullable MediaFormat mediaFormat) {
      if (internalVideoFrameMetadataListener != null) {
        internalVideoFrameMetadataListener.onVideoFrameAboutToBeRendered(
            presentationTimeUs, releaseTimeNs, format, mediaFormat);
      }
      if (videoFrameMetadataListener != null) {
        videoFrameMetadataListener.onVideoFrameAboutToBeRendered(
            presentationTimeUs, releaseTimeNs, format, mediaFormat);
      }
    }

    // CameraMotionListener

    @Override
    public void onCameraMotion(long timeUs, float[] rotation) {
      if (internalCameraMotionListener != null) {
        internalCameraMotionListener.onCameraMotion(timeUs, rotation);
      }
      if (cameraMotionListener != null) {
        cameraMotionListener.onCameraMotion(timeUs, rotation);
      }
    }

    @Override
    public void onCameraMotionReset() {
      if (internalCameraMotionListener != null) {
        internalCameraMotionListener.onCameraMotionReset();
      }
      if (cameraMotionListener != null) {
        cameraMotionListener.onCameraMotionReset();
      }
    }
  }

  @RequiresApi(31)
  private static final class Api31 {
    private Api31() {}

    @DoNotInline
    public static PlayerId registerMediaMetricsListener(
        Context context, ExoPlayerImpl player, boolean usePlatformDiagnostics) {
      @Nullable MediaMetricsListener listener = MediaMetricsListener.create(context);
      if (listener == null) {
        Log.w(TAG, "MediaMetricsService unavailable.");
        return new PlayerId(LogSessionId.LOG_SESSION_ID_NONE);
      }
      if (usePlatformDiagnostics) {
        player.addAnalyticsListener(listener);
      }
      return new PlayerId(listener.getLogSessionId());
    }
  }

  @RequiresApi(23)
  private static final class Api23 {
    private Api23() {}

    @DoNotInline
    public static boolean isSuitableAudioOutputPresentInAudioDeviceInfoList(
        Context context, AudioDeviceInfo[] audioDeviceInfos) {
      if (!Util.isWear(context)) {
        return true;
      }
      for (AudioDeviceInfo device : audioDeviceInfos) {
        if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            || device.getType() == AudioDeviceInfo.TYPE_LINE_ANALOG
            || device.getType() == AudioDeviceInfo.TYPE_LINE_DIGITAL
            || device.getType() == AudioDeviceInfo.TYPE_USB_DEVICE
            || device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            || device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
          return true;
        }
        if (Util.SDK_INT >= 26 && device.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
          return true;
        }
        if (Util.SDK_INT >= 28 && device.getType() == AudioDeviceInfo.TYPE_HEARING_AID) {
          return true;
        }
        if (Util.SDK_INT >= 31
            && (device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET
                || device.getType() == AudioDeviceInfo.TYPE_BLE_SPEAKER)) {
          return true;
        }
        if (Util.SDK_INT >= 33 && device.getType() == AudioDeviceInfo.TYPE_BLE_BROADCAST) {
          return true;
        }
      }
      return false;
    }

    @DoNotInline
    public static void registerAudioDeviceCallback(
        AudioManager audioManager, AudioDeviceCallback audioDeviceCallback, Handler handler) {
      audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler);
    }
  }

  /**
   * A {@link AudioDeviceCallback} to change playback suppression reason when suitable audio outputs
   * are either added in unsuitable output based playback suppression state or removed during an
   * ongoing playback.
   */
  @RequiresApi(23)
  private final class NoSuitableOutputPlaybackSuppressionAudioDeviceCallback
      extends AudioDeviceCallback {

    @Override
    public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
      if (hasSupportedAudioOutput()
          && playbackInfo.playbackSuppressionReason
              == Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT) {
        updatePlaybackInfoForPlayWhenReadyAndSuppressionReasonStates(
            playbackInfo.playWhenReady,
            PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            Player.PLAYBACK_SUPPRESSION_REASON_NONE);
      }
    }

    @Override
    public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
      if (!hasSupportedAudioOutput()) {
        updatePlaybackInfoForPlayWhenReadyAndSuppressionReasonStates(
            playbackInfo.playWhenReady,
            PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT);
      }
    }
  }
}
