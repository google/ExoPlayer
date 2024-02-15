/*
 * Copyright (C) 2017 The Android Open Source Project
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
package androidx.media3.test.exoplayer.playback.gts;

import static androidx.media3.common.C.WIDEVINE_UUID;

import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;
import android.view.Surface;
import android.widget.FrameLayout;
import androidx.annotation.Size;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback;
import androidx.media3.exoplayer.drm.MediaDrmCallback;
import androidx.media3.exoplayer.drm.UnsupportedDrmException;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import androidx.media3.exoplayer.trackselection.RandomTrackSelection;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.test.utils.ActionSchedule;
import androidx.media3.test.utils.DecoderCountersUtil;
import androidx.media3.test.utils.ExoHostedTest;
import androidx.media3.test.utils.HostActivity;
import androidx.media3.test.utils.HostActivity.HostedTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** {@link DashHostedTest} builder. */
/* package */ final class DashTestRunner {

  static final int VIDEO_RENDERER_INDEX = 0;
  static final int AUDIO_RENDERER_INDEX = 1;

  private static final long TEST_TIMEOUT_MS = 5 * 60 * 1000;

  // Whether adaptive tests should enable video formats beyond those mandated by the Android CDD
  // if the device advertises support for them.
  private static final boolean ALLOW_ADDITIONAL_VIDEO_FORMATS = Util.SDK_INT >= 24;

  private static final String AUDIO_TAG_SUFFIX = ":Audio";
  private static final String VIDEO_TAG_SUFFIX = ":Video";

  private static final int MIN_LOADABLE_RETRY_COUNT = 10;
  private static final int MAX_CONSECUTIVE_DROPPED_VIDEO_FRAMES = 10;
  private static final float MAX_DROPPED_VIDEO_FRAME_FRACTION = 0.01f;

  private static final String WIDEVINE_SECURITY_LEVEL_1 = "L1";
  private static final String WIDEVINE_SECURITY_LEVEL_3 = "L3";
  private static final String SECURITY_LEVEL_PROPERTY = "securityLevel";

  @Size(max = 23)
  private final String tag;

  private final HostActivity activity;

  private String streamName;
  private boolean fullPlaybackNoSeeking;
  private String audioFormat;
  private boolean canIncludeAdditionalVideoFormats;
  private ActionSchedule actionSchedule;
  private byte[] offlineLicenseKeySetId;
  private String[] videoFormats;
  private String manifestUrl;
  private boolean useL1Widevine;
  private String widevineLicenseUrl;
  private DataSource.Factory dataSourceFactory;

  @SuppressWarnings("ResourceType")
  public static boolean isL1WidevineAvailable(String mimeType) {
    try (MediaDrm mediaDrm = new MediaDrm(WIDEVINE_UUID)) {
      // Force L3 if secure decoder is not available.
      if (MediaCodecUtil.getDecoderInfo(mimeType, /* secure= */ true, /* tunneling= */ false)
          == null) {
        return false;
      }
      String securityProperty = mediaDrm.getPropertyString(SECURITY_LEVEL_PROPERTY);
      return WIDEVINE_SECURITY_LEVEL_1.equals(securityProperty);
    } catch (UnsupportedSchemeException | MediaCodecUtil.DecoderQueryException e) {
      throw new IllegalStateException(e);
    }
  }

  public DashTestRunner(@Size(max = 23) String tag, HostActivity activity) {
    this.tag = tag;
    this.activity = activity;
  }

  @CanIgnoreReturnValue
  public DashTestRunner setStreamName(String streamName) {
    this.streamName = streamName;
    return this;
  }

  @CanIgnoreReturnValue
  public DashTestRunner setFullPlaybackNoSeeking(boolean fullPlaybackNoSeeking) {
    this.fullPlaybackNoSeeking = fullPlaybackNoSeeking;
    return this;
  }

  @CanIgnoreReturnValue
  public DashTestRunner setCanIncludeAdditionalVideoFormats(
      boolean canIncludeAdditionalVideoFormats) {
    this.canIncludeAdditionalVideoFormats =
        canIncludeAdditionalVideoFormats && ALLOW_ADDITIONAL_VIDEO_FORMATS;
    return this;
  }

  @CanIgnoreReturnValue
  public DashTestRunner setActionSchedule(ActionSchedule actionSchedule) {
    this.actionSchedule = actionSchedule;
    return this;
  }

  @CanIgnoreReturnValue
  public DashTestRunner setOfflineLicenseKeySetId(byte[] offlineLicenseKeySetId) {
    this.offlineLicenseKeySetId = offlineLicenseKeySetId;
    return this;
  }

  @CanIgnoreReturnValue
  public DashTestRunner setAudioVideoFormats(String audioFormat, String... videoFormats) {
    this.audioFormat = audioFormat;
    this.videoFormats = videoFormats;
    return this;
  }

  @CanIgnoreReturnValue
  public DashTestRunner setManifestUrl(String manifestUrl) {
    this.manifestUrl = manifestUrl;
    return this;
  }

  @CanIgnoreReturnValue
  public DashTestRunner setWidevineInfo(String mimeType, boolean videoIdRequiredInLicenseUrl) {
    this.useL1Widevine = isL1WidevineAvailable(mimeType);
    this.widevineLicenseUrl =
        DashTestData.getWidevineLicenseUrl(videoIdRequiredInLicenseUrl, useL1Widevine);
    return this;
  }

  @CanIgnoreReturnValue
  public DashTestRunner setDataSourceFactory(DataSource.Factory dataSourceFactory) {
    this.dataSourceFactory = dataSourceFactory;
    return this;
  }

  public void run() {
    DashHostedTest test = createDashHostedTest(canIncludeAdditionalVideoFormats, false);
    activity.runTest(test, TEST_TIMEOUT_MS);
    // Retry test exactly once if adaptive test fails due to excessive dropped buffers when
    // playing non-CDD required formats (b/28220076).
    if (test.needsCddLimitedRetry) {
      activity.runTest(createDashHostedTest(false, true), TEST_TIMEOUT_MS);
    }
  }

  private DashHostedTest createDashHostedTest(
      boolean canIncludeAdditionalVideoFormats, boolean isCddLimitedRetry) {
    MetricsLogger metricsLogger =
        MetricsLogger.DEFAULT_FACTORY.create(
            InstrumentationRegistry.getInstrumentation(), tag, streamName);
    return new DashHostedTest(
        tag,
        streamName,
        manifestUrl,
        metricsLogger,
        fullPlaybackNoSeeking,
        audioFormat,
        canIncludeAdditionalVideoFormats,
        isCddLimitedRetry,
        actionSchedule,
        offlineLicenseKeySetId,
        widevineLicenseUrl,
        useL1Widevine,
        dataSourceFactory,
        videoFormats);
  }

  /** A {@link HostedTest} for DASH playback tests. */
  private static final class DashHostedTest extends ExoHostedTest {

    private final String streamName;
    private final String manifestUrl;
    private final MetricsLogger metricsLogger;
    private final boolean fullPlaybackNoSeeking;
    private final boolean isCddLimitedRetry;
    private final DashTestTrackSelector trackSelector;
    private final byte[] offlineLicenseKeySetId;
    private final String widevineLicenseUrl;
    private final boolean useL1Widevine;
    private final DataSource.Factory dataSourceFactory;

    private boolean needsCddLimitedRetry;

    /**
     * @param tag A tag to use for logging.
     * @param streamName The name of the test stream for metric logging.
     * @param manifestUrl The manifest url.
     * @param metricsLogger Logger to log metrics from the test.
     * @param fullPlaybackNoSeeking Whether the test will play the entire source with no seeking.
     * @param audioFormat The audio format.
     * @param canIncludeAdditionalVideoFormats Whether to use video formats in addition to those
     *     listed in the videoFormats argument, if the device is capable of playing them.
     * @param isCddLimitedRetry Whether this is a CDD limited retry following a previous failure.
     * @param actionSchedule The action schedule for the test.
     * @param offlineLicenseKeySetId The key set id of the license to be used.
     * @param widevineLicenseUrl If the video is Widevine encrypted, this is the license url
     *     otherwise null.
     * @param useL1Widevine Whether to use L1 Widevine.
     * @param dataSourceFactory If not null, used to load manifest and media.
     * @param videoFormats The video formats.
     */
    private DashHostedTest(
        String tag,
        String streamName,
        String manifestUrl,
        MetricsLogger metricsLogger,
        boolean fullPlaybackNoSeeking,
        String audioFormat,
        boolean canIncludeAdditionalVideoFormats,
        boolean isCddLimitedRetry,
        ActionSchedule actionSchedule,
        byte[] offlineLicenseKeySetId,
        String widevineLicenseUrl,
        boolean useL1Widevine,
        DataSource.Factory dataSourceFactory,
        String... videoFormats) {
      super(tag, fullPlaybackNoSeeking);
      Assertions.checkArgument(!(isCddLimitedRetry && canIncludeAdditionalVideoFormats));
      this.streamName = streamName;
      this.manifestUrl = manifestUrl;
      this.metricsLogger = metricsLogger;
      this.fullPlaybackNoSeeking = fullPlaybackNoSeeking;
      this.isCddLimitedRetry = isCddLimitedRetry;
      this.offlineLicenseKeySetId = offlineLicenseKeySetId;
      this.widevineLicenseUrl = widevineLicenseUrl;
      this.useL1Widevine = useL1Widevine;
      this.dataSourceFactory = dataSourceFactory;
      trackSelector =
          new DashTestTrackSelector(
              tag, audioFormat, videoFormats, canIncludeAdditionalVideoFormats);
      if (actionSchedule != null) {
        setSchedule(actionSchedule);
      }
    }

    @Override
    protected DefaultTrackSelector buildTrackSelector(HostActivity host) {
      return trackSelector;
    }

    @Override
    protected DrmSessionManager buildDrmSessionManager() {
      if (widevineLicenseUrl == null) {
        return DrmSessionManager.DRM_UNSUPPORTED;
      }
      MediaDrmCallback drmCallback =
          new HttpMediaDrmCallback(widevineLicenseUrl, new DefaultHttpDataSource.Factory());
      DefaultDrmSessionManager drmSessionManager =
          new DefaultDrmSessionManager.Builder()
              .setUuidAndExoMediaDrmProvider(
                  C.WIDEVINE_UUID,
                  uuid -> {
                    try {
                      FrameworkMediaDrm drm = FrameworkMediaDrm.newInstance(WIDEVINE_UUID);
                      if (!useL1Widevine) {
                        drm.setPropertyString(SECURITY_LEVEL_PROPERTY, WIDEVINE_SECURITY_LEVEL_3);
                      }
                      return drm;
                    } catch (UnsupportedDrmException e) {
                      throw new IllegalStateException(e);
                    }
                  })
              .build(drmCallback);

      if (offlineLicenseKeySetId != null) {
        drmSessionManager.setMode(DefaultDrmSessionManager.MODE_PLAYBACK, offlineLicenseKeySetId);
      }
      return drmSessionManager;
    }

    @Override
    protected ExoPlayer buildExoPlayer(
        HostActivity host, Surface surface, MappingTrackSelector trackSelector) {
      ExoPlayer player =
          new ExoPlayer.Builder(host, new DebugRenderersFactory(host))
              .setTrackSelector(trackSelector)
              .build();
      player.setVideoSurface(surface);
      return player;
    }

    @Override
    protected MediaSource buildSource(
        HostActivity host, DrmSessionManager drmSessionManager, FrameLayout overlayFrameLayout) {
      DataSource.Factory dataSourceFactory =
          this.dataSourceFactory != null
              ? this.dataSourceFactory
              : new DefaultDataSource.Factory(host);
      return new DashMediaSource.Factory(dataSourceFactory)
          .setDrmSessionManagerProvider(unusedMediaItem -> drmSessionManager)
          .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(MIN_LOADABLE_RETRY_COUNT))
          .createMediaSource(MediaItem.fromUri(manifestUrl));
    }

    @Override
    protected void logMetrics(DecoderCounters audioCounters, DecoderCounters videoCounters) {
      metricsLogger.logMetric(MetricsLogger.KEY_TEST_NAME, streamName);
      metricsLogger.logMetric(MetricsLogger.KEY_IS_CDD_LIMITED_RETRY, isCddLimitedRetry);
      metricsLogger.logMetric(
          MetricsLogger.KEY_FRAMES_DROPPED_COUNT, videoCounters.droppedBufferCount);
      metricsLogger.logMetric(
          MetricsLogger.KEY_MAX_CONSECUTIVE_FRAMES_DROPPED_COUNT,
          videoCounters.maxConsecutiveDroppedBufferCount);
      metricsLogger.logMetric(
          MetricsLogger.KEY_FRAMES_SKIPPED_COUNT, videoCounters.skippedOutputBufferCount);
      metricsLogger.logMetric(
          MetricsLogger.KEY_FRAMES_RENDERED_COUNT, videoCounters.renderedOutputBufferCount);
      metricsLogger.close();
    }

    @Override
    protected void assertPassed(DecoderCounters audioCounters, DecoderCounters videoCounters) {
      if (fullPlaybackNoSeeking) {
        // We shouldn't have skipped any output buffers.
        DecoderCountersUtil.assertSkippedOutputBufferCount(
            tag + AUDIO_TAG_SUFFIX, audioCounters, 0);
        DecoderCountersUtil.assertSkippedOutputBufferCount(
            tag + VIDEO_TAG_SUFFIX, videoCounters, 0);
        DecoderCountersUtil.assertTotalBufferCount(tag + AUDIO_TAG_SUFFIX, audioCounters);
        DecoderCountersUtil.assertTotalBufferCount(tag + VIDEO_TAG_SUFFIX, videoCounters);
      }
      try {
        if (!shouldSkipDroppedOutputBufferPerformanceAssertions()) {
          int droppedFrameLimit =
              (int)
                  Math.ceil(
                      MAX_DROPPED_VIDEO_FRAME_FRACTION
                          * DecoderCountersUtil.getTotalBufferCount(videoCounters));
          // Assert that performance is acceptable.
          // Assert that total dropped frames were within limit.
          DecoderCountersUtil.assertDroppedBufferLimit(
              tag + VIDEO_TAG_SUFFIX, videoCounters, droppedFrameLimit);
          // Assert that consecutive dropped frames were within limit.
          DecoderCountersUtil.assertConsecutiveDroppedBufferLimit(
              tag + VIDEO_TAG_SUFFIX, videoCounters, MAX_CONSECUTIVE_DROPPED_VIDEO_FRAMES);
        }
      } catch (AssertionError e) {
        if (trackSelector.includedAdditionalVideoFormats) {
          // Retry limiting to CDD mandated formats (b/28220076).
          Log.e(tag, "Too many dropped or consecutive dropped frames.", e);
          needsCddLimitedRetry = true;
        } else {
          throw e;
        }
      }
    }
  }

  /** Provides a hook to skip dropped output buffer assertions in specific circumstances. */
  private static boolean shouldSkipDroppedOutputBufferPerformanceAssertions() {
    return false;
  }

  private static final class DashTestTrackSelector extends DefaultTrackSelector {

    @Size(max = 23)
    private final String tag;

    private final String audioFormatId;
    private final String[] videoFormatIds;
    private final boolean canIncludeAdditionalVideoFormats;

    public boolean includedAdditionalVideoFormats;

    private DashTestTrackSelector(
        String tag,
        String audioFormatId,
        String[] videoFormatIds,
        boolean canIncludeAdditionalVideoFormats) {
      super(
          ApplicationProvider.getApplicationContext(),
          new RandomTrackSelection.Factory(/* seed= */ 0));
      this.tag = tag;
      this.audioFormatId = audioFormatId;
      this.videoFormatIds = videoFormatIds;
      this.canIncludeAdditionalVideoFormats = canIncludeAdditionalVideoFormats;
    }

    @Override
    protected ExoTrackSelection.Definition[] selectAllTracks(
        MappedTrackInfo mappedTrackInfo,
        int[][][] rendererFormatSupports,
        int[] rendererMixedMimeTypeAdaptationSupports,
        Parameters parameters) {
      Assertions.checkState(
          mappedTrackInfo.getRendererType(VIDEO_RENDERER_INDEX) == C.TRACK_TYPE_VIDEO);
      Assertions.checkState(
          mappedTrackInfo.getRendererType(AUDIO_RENDERER_INDEX) == C.TRACK_TYPE_AUDIO);
      TrackGroupArray videoTrackGroups = mappedTrackInfo.getTrackGroups(VIDEO_RENDERER_INDEX);
      TrackGroupArray audioTrackGroups = mappedTrackInfo.getTrackGroups(AUDIO_RENDERER_INDEX);
      Assertions.checkState(videoTrackGroups.length == 1);
      Assertions.checkState(audioTrackGroups.length == 1);
      ExoTrackSelection.Definition[] definitions =
          new ExoTrackSelection.Definition[mappedTrackInfo.getRendererCount()];
      definitions[VIDEO_RENDERER_INDEX] =
          new ExoTrackSelection.Definition(
              videoTrackGroups.get(0),
              getVideoTrackIndices(
                  videoTrackGroups.get(0),
                  rendererFormatSupports[VIDEO_RENDERER_INDEX][0],
                  videoFormatIds,
                  canIncludeAdditionalVideoFormats));
      definitions[AUDIO_RENDERER_INDEX] =
          new ExoTrackSelection.Definition(
              audioTrackGroups.get(0), getTrackIndex(audioTrackGroups.get(0), audioFormatId));
      includedAdditionalVideoFormats =
          definitions[VIDEO_RENDERER_INDEX].tracks.length > videoFormatIds.length;
      return definitions;
    }

    private int[] getVideoTrackIndices(
        TrackGroup trackGroup,
        int[] formatSupports,
        String[] formatIds,
        boolean canIncludeAdditionalFormats) {
      List<Integer> trackIndices = new ArrayList<>();

      // Always select explicitly listed representations.
      for (String formatId : formatIds) {
        int trackIndex = getTrackIndex(trackGroup, formatId);
        Log.d(
            tag,
            "Adding base video format: " + Format.toLogString(trackGroup.getFormat(trackIndex)));
        trackIndices.add(trackIndex);
      }

      // Select additional video representations, if supported by the device.
      if (canIncludeAdditionalFormats) {
        for (int i = 0; i < trackGroup.length; i++) {
          if (!trackIndices.contains(i) && isFormatHandled(formatSupports[i])) {
            Log.d(tag, "Adding extra video format: " + Format.toLogString(trackGroup.getFormat(i)));
            trackIndices.add(i);
          }
        }
      }

      int[] trackIndicesArray = Ints.toArray(trackIndices);
      Arrays.sort(trackIndicesArray);
      return trackIndicesArray;
    }

    private static int getTrackIndex(TrackGroup trackGroup, String formatId) {
      for (int i = 0; i < trackGroup.length; i++) {
        if (trackGroup.getFormat(i).id.equals(formatId)) {
          return i;
        }
      }
      throw new IllegalStateException("Format " + formatId + " not found.");
    }

    private static boolean isFormatHandled(int formatSupport) {
      return RendererCapabilities.getFormatSupport(formatSupport) == C.FORMAT_HANDLED;
    }
  }
}
