/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.media3.exoplayer.mediacodec;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint;
import androidx.annotation.DoNotInline;
import androidx.annotation.IntDef;
import androidx.annotation.RequiresApi;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Utility class checking media codec support through PerformancePoints. */
@UnstableApi
/* package */ final class MediaCodecPerformancePointCoverageProvider {

  /**
   * Whether if the device provides a PerformancePoints and coverage results should be ignored as
   * the PerformancePoints do not cover CDD requirements.
   */
  @SuppressWarnings("NonFinalStaticField")
  private static @MonotonicNonNull Boolean shouldIgnorePerformancePoints;

  private MediaCodecPerformancePointCoverageProvider() {}

  /** Possible outcomes of evaluating {@link PerformancePoint} coverage. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED,
    COVERAGE_RESULT_NO,
    COVERAGE_RESULT_YES
  })
  @interface PerformancePointCoverageResult {}

  /**
   * The {@link VideoCapabilities} do not contain any valid {@linkplain PerformancePoint
   * PerformancePoints}.
   */
  /* package */ static final int COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED = 0;

  /**
   * The decoder has at least one PerformancePoint, but none cover the resolution and frame rate.
   */
  /* package */ static final int COVERAGE_RESULT_NO = 1;

  /** The decoder has a PerformancePoint that covers the resolution and frame rate. */
  /* package */ static final int COVERAGE_RESULT_YES = 2;

  /**
   * This method returns if a decoder's {@link VideoCapabilities} cover a resolution and frame rate
   * with its {@link PerformancePoint} list.
   *
   * @param videoCapabilities A decoder's {@link VideoCapabilities}
   * @param width Width in pixels.
   * @param height Height in pixels.
   * @param frameRate Optional frame rate in frames per second. Ignored if set to {@link
   *     Format#NO_VALUE} or any value less than or equal to 0.
   * @return {@link #COVERAGE_RESULT_YES} if the {@link VideoCapabilities} has a {@link
   *     PerformancePoint} list that covers the resolution and frame rate or {@link
   *     #COVERAGE_RESULT_NO} if the list does not provide coverage. {@link
   *     #COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED} is returned if the {@link
   *     VideoCapabilities} does not contain a list of valid {@code PerformancePoints}
   */
  public static @PerformancePointCoverageResult int areResolutionAndFrameRateCovered(
      VideoCapabilities videoCapabilities, int width, int height, double frameRate) {
    if (Util.SDK_INT < 29
        || (shouldIgnorePerformancePoints != null && shouldIgnorePerformancePoints)) {
      return COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED;
    }

    return Api29.areResolutionAndFrameRateCovered(videoCapabilities, width, height, frameRate);
  }

  @RequiresApi(29)
  private static final class Api29 {
    @DoNotInline
    public static @PerformancePointCoverageResult int areResolutionAndFrameRateCovered(
        VideoCapabilities videoCapabilities, int width, int height, double frameRate) {
      List<PerformancePoint> performancePointList =
          videoCapabilities.getSupportedPerformancePoints();
      if (performancePointList == null || performancePointList.isEmpty()) {
        return COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED;
      }

      // Round frame rate down to to avoid situations where a range check in
      // covers fails due to slightly exceeding the limits for a standard format
      // (e.g., 1080p at 30 fps). [Internal ref: b/134706676]
      PerformancePoint targetPerformancePoint =
          new PerformancePoint(width, height, (int) frameRate);

      @PerformancePointCoverageResult
      int performancePointCoverageResult =
          evaluatePerformancePointCoverage(performancePointList, targetPerformancePoint);

      if (performancePointCoverageResult == COVERAGE_RESULT_NO
          && shouldIgnorePerformancePoints == null) {
        // See https://github.com/google/ExoPlayer/issues/10898,
        // https://github.com/androidx/media/issues/693,
        // https://github.com/androidx/media/issues/966 and [internal ref: b/267324685].
        shouldIgnorePerformancePoints = shouldIgnorePerformancePoints();
        if (shouldIgnorePerformancePoints) {
          return COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED;
        }
      }

      return performancePointCoverageResult;
    }

    /**
     * Checks if the CDD-requirement to support H264 720p at 60 fps is covered by PerformancePoints.
     */
    private static boolean shouldIgnorePerformancePoints() {
      try {
        Format formatH264 = new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build();
        // Null check required to pass RequiresNonNull annotation on getDecoderInfosSoftMatch.
        if (formatH264.sampleMimeType != null) {
          List<MediaCodecInfo> decoderInfos =
              MediaCodecUtil.getDecoderInfosSoftMatch(
                  MediaCodecSelector.DEFAULT,
                  formatH264,
                  /* requiresSecureDecoder= */ false,
                  /* requiresTunnelingDecoder= */ false);
          for (int i = 0; i < decoderInfos.size(); i++) {
            if (decoderInfos.get(i).capabilities != null
                && decoderInfos.get(i).capabilities.getVideoCapabilities() != null) {
              List<PerformancePoint> performancePointListH264 =
                  decoderInfos
                      .get(i)
                      .capabilities
                      .getVideoCapabilities()
                      .getSupportedPerformancePoints();
              if (performancePointListH264 != null && !performancePointListH264.isEmpty()) {
                PerformancePoint targetPerformancePointH264 =
                    new PerformancePoint(/* width= */ 1280, /* height= */ 720, /* frameRate= */ 60);
                return evaluatePerformancePointCoverage(
                        performancePointListH264, targetPerformancePointH264)
                    == COVERAGE_RESULT_NO;
              }
            }
          }
        }
        return true;
      } catch (MediaCodecUtil.DecoderQueryException ignored) {
        return true;
      }
    }

    private static @PerformancePointCoverageResult int evaluatePerformancePointCoverage(
        List<PerformancePoint> performancePointList, PerformancePoint targetPerformancePoint) {
      for (int i = 0; i < performancePointList.size(); i++) {
        if (performancePointList.get(i).covers(targetPerformancePoint)) {
          return COVERAGE_RESULT_YES;
        }
      }
      return COVERAGE_RESULT_NO;
    }
  }
}
