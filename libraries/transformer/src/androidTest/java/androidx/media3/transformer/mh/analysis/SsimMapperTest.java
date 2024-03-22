/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.transformer.mh.analysis;

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1280W_720H_30_SECOND_HIGHMOTION;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1280W_720H_30_SECOND_ROOF_ONEPLUSNORD2;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1280W_720H_32_SECOND_ROOF_REDMINOTE9;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1280W_720H_5_SECOND_HIGHMOTION;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1920W_1080H_30_SECOND_HIGHMOTION;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1920W_1080H_5_SECOND_HIGHMOTION;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1920W_1080H_60_FPS_30_SECOND_ROOF_ONEPLUSNORD2;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1920W_1080H_60_FPS_30_SECOND_ROOF_REDMINOTE9;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_2400W_1080H_34_SECOND_ROOF_SAMSUNGS20ULTRA5G;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_256W_144H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_256W_144H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_3840W_2160H_30_SECOND_ROOF_ONEPLUSNORD2;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_3840W_2160H_30_SECOND_ROOF_REDMINOTE9;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_3840W_2160H_32_SECOND_HIGHMOTION;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_3840W_2160H_5_SECOND_HIGHMOTION;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_426W_240H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_426W_240H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_640W_360H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_640W_360H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_640W_480H_31_SECOND_ROOF_SONYXPERIAXZ3;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_7680W_4320H_31_SECOND_ROOF_SAMSUNGS20ULTRA5G;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_854W_480H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_854W_480H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static androidx.media3.transformer.AndroidTestUtil.getFormatForTestFile;
import static androidx.media3.transformer.ExportTestResult.SSIM_UNSET;
import static com.google.common.collect.Iterables.getLast;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.transformer.AndroidTestUtil;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.media3.transformer.VideoEncoderSettings;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Finds the bitrate mapping for a given SSIM value.
 *
 * <p>SSIM increases monotonically with bitrate.
 */
@RunWith(Parameterized.class)
@Ignore(
    "Analysis tests are not used for confirming Transformer is running properly, and not configured"
        + " for this use as they're missing skip checks for unsupported devices.")
public class SsimMapperTest {

  private static final Splitter FORWARD_SLASH_SPLITTER = Splitter.on('/');

  // When running this test, input file list should be restricted more than this. Binary search can
  // take up to 40 minutes to complete for a single clip on lower end devices.
  private static final ImmutableList<String> INPUT_FILES =
      ImmutableList.of(
          MP4_REMOTE_256W_144H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED,
          MP4_REMOTE_256W_144H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED,
          MP4_REMOTE_426W_240H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED,
          MP4_REMOTE_426W_240H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED,
          MP4_REMOTE_640W_360H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED,
          MP4_REMOTE_640W_360H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED,
          MP4_REMOTE_854W_480H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED,
          MP4_REMOTE_854W_480H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED,
          MP4_REMOTE_640W_480H_31_SECOND_ROOF_SONYXPERIAXZ3,
          MP4_REMOTE_1280W_720H_5_SECOND_HIGHMOTION,
          MP4_REMOTE_1280W_720H_30_SECOND_HIGHMOTION,
          MP4_REMOTE_1280W_720H_30_SECOND_ROOF_ONEPLUSNORD2,
          MP4_REMOTE_1280W_720H_32_SECOND_ROOF_REDMINOTE9,
          MP4_REMOTE_1920W_1080H_5_SECOND_HIGHMOTION,
          MP4_REMOTE_1920W_1080H_30_SECOND_HIGHMOTION,
          MP4_REMOTE_1920W_1080H_60_FPS_30_SECOND_ROOF_ONEPLUSNORD2,
          MP4_REMOTE_1920W_1080H_60_FPS_30_SECOND_ROOF_REDMINOTE9,
          MP4_REMOTE_2400W_1080H_34_SECOND_ROOF_SAMSUNGS20ULTRA5G,
          MP4_REMOTE_3840W_2160H_5_SECOND_HIGHMOTION,
          MP4_REMOTE_3840W_2160H_32_SECOND_HIGHMOTION,
          MP4_REMOTE_3840W_2160H_30_SECOND_ROOF_ONEPLUSNORD2,
          MP4_REMOTE_3840W_2160H_30_SECOND_ROOF_REDMINOTE9,
          MP4_REMOTE_7680W_4320H_31_SECOND_ROOF_SAMSUNGS20ULTRA5G);

  @Parameters
  public static List<Object[]> parameters() {
    List<Object[]> parameterList = new ArrayList<>();
    for (String file : INPUT_FILES) {
      parameterList.add(new Object[] {file, MimeTypes.VIDEO_H264});
      // TODO(b/210593256): Test pre 24 once in-app muxing implemented.
      if (Util.SDK_INT >= 24) {
        parameterList.add(new Object[] {file, MimeTypes.VIDEO_H265});
      }
    }
    return parameterList;
  }

  @Parameter(0)
  @Nullable
  public String fileUri;

  @Parameter(1)
  @Nullable
  public String mimeType;

  @Test
  public void findSsimMapping() throws Exception {
    String fileUri = checkNotNull(this.fileUri);
    String mimeType = checkNotNull(this.mimeType);

    String testIdPrefix =
        String.format(
            "ssim_search_VBR_%s", checkNotNull(getLast(FORWARD_SLASH_SPLITTER.split(mimeType))));

    assumeFormatsSupported(
        ApplicationProvider.getApplicationContext(),
        testIdPrefix + "_codecSupport",
        /* inputFormat= */ getFormatForTestFile(fileUri),
        /* outputFormat= */ null);

    new SsimBinarySearcher(
            ApplicationProvider.getApplicationContext(), testIdPrefix, fileUri, mimeType)
        .search();
  }

  private static final class SsimBinarySearcher {
    private static final String TAG = "SsimBinarySearcher";
    private static final double SSIM_ACCEPTABLE_TOLERANCE = 0.005;
    private static final double SSIM_TARGET = 0.95;
    private static final int MAX_EXPORTS = 12;

    private final Context context;
    private final String testIdPrefix;
    private final String videoUri;
    private final Format format;
    private final String outputMimeType;

    private int exportsLeft;
    private double ssimLowerBound;
    private double ssimUpperBound;
    private int bitrateLowerBound;
    private int bitrateUpperBound;

    /**
     * Creates a new instance.
     *
     * @param context The {@link Context}.
     * @param testIdPrefix The test ID prefix.
     * @param videoUri The URI of the video to transform.
     * @param outputMimeType The video sample MIME type to output, see {@link
     *     Transformer.Builder#setVideoMimeType}.
     */
    public SsimBinarySearcher(
        Context context, String testIdPrefix, String videoUri, String outputMimeType) {
      this.context = context;
      this.testIdPrefix = testIdPrefix;
      this.videoUri = videoUri;
      this.outputMimeType = outputMimeType;
      exportsLeft = MAX_EXPORTS;
      format = AndroidTestUtil.getFormatForTestFile(videoUri);
    }

    /**
     * Finds valid upper and lower bounds for the SSIM binary search.
     *
     * @return Whether to perform a binary search within the bounds.
     */
    private boolean setupBinarySearchBounds() throws Exception {
      // Starting point based on Kush Gauge formula with a medium motion factor.
      int currentBitrate = (int) (format.width * format.height * format.frameRate * 0.07 * 2);
      ssimLowerBound = SSIM_UNSET;
      ssimUpperBound = SSIM_UNSET;

      // 1280x720, 30fps video: 112kbps.
      int minBitrateToCheck = currentBitrate / 32;
      // 1280x720, 30fps video: 118Mbps.
      int maxBitrateToCheck = currentBitrate * 32;

      do {
        double currentSsim = exportAndGetSsim(currentBitrate);
        if (isSsimAcceptable(currentSsim)) {
          return false;
        }

        if (currentSsim > SSIM_TARGET) {
          ssimUpperBound = currentSsim;
          bitrateUpperBound = currentBitrate;
          currentBitrate /= 2;
          if (currentBitrate < minBitrateToCheck) {
            return false;
          }
        } else if (currentSsim < SSIM_TARGET) {
          ssimLowerBound = currentSsim;
          bitrateLowerBound = currentBitrate;
          currentBitrate *= 2;
          if (currentBitrate > maxBitrateToCheck) {
            return false;
          }
        }
      } while ((ssimLowerBound == SSIM_UNSET || ssimUpperBound == SSIM_UNSET) && exportsLeft > 0);

      return exportsLeft > 0;
    }

    /**
     * Transforms the video with different encoder target bitrates, calculating output SSIM.
     *
     * <p>Performs a binary search of the bitrate between the {@link #bitrateLowerBound} and {@link
     * #bitrateUpperBound}.
     *
     * <p>Runs until the target SSIM is found or the maximum number of exports is reached.
     */
    public void search() throws Exception {
      if (!setupBinarySearchBounds()) {
        return;
      }

      while (exportsLeft > 0) {
        // At this point, we have under and over bitrate bounds, with associated SSIMs.
        // Go between the two, and replace either the under or the over.

        int currentBitrate = (bitrateUpperBound + bitrateLowerBound) / 2;
        double currentSsim = exportAndGetSsim(currentBitrate);
        if (isSsimAcceptable(currentSsim)) {
          return;
        }

        if (currentSsim < SSIM_TARGET) {
          checkState(currentSsim >= ssimLowerBound, "SSIM has decreased with a higher bitrate.");
          bitrateLowerBound = currentBitrate;
          ssimLowerBound = currentSsim;
        } else if (currentSsim > SSIM_TARGET) {
          checkState(currentSsim <= ssimUpperBound, "SSIM has increased with a lower bitrate.");
          bitrateUpperBound = currentBitrate;
          ssimUpperBound = currentSsim;
        } else {
          throw new IllegalStateException(
              "Impossible - SSIM is not above, below, or matching target.");
        }
      }
    }

    private double exportAndGetSsim(int bitrate) throws Exception {
      // TODO(b/238094555): Force specific encoders to be used.

      String fileName = checkNotNull(getLast(FORWARD_SLASH_SPLITTER.split(videoUri)));
      String testId = String.format("%s_%s_%s", testIdPrefix, bitrate, fileName);

      Map<String, Object> inputValues = new HashMap<>();
      inputValues.put("targetBitrate", bitrate);
      inputValues.put("inputFilename", fileName);
      inputValues.put("bitrateMode", "VBR");
      inputValues.put("width", format.width);
      inputValues.put("height", format.height);
      inputValues.put("framerate", format.frameRate);

      Transformer transformer =
          new Transformer.Builder(context)
              .setVideoMimeType(outputMimeType)
              .setEncoderFactory(
                  new DefaultEncoderFactory.Builder(context)
                      .setRequestedVideoEncoderSettings(
                          new VideoEncoderSettings.Builder()
                              .setBitrate(bitrate)
                              .setBitrateMode(BITRATE_MODE_VBR)
                              .build())
                      .setEnableFallback(false)
                      .build())
              .build();
      EditedMediaItem editedMediaItem =
          new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(videoUri)))
              .setRemoveAudio(true)
              .build();

      exportsLeft--;

      double ssim =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .setInputValues(inputValues)
              .setRequestCalculateSsim(true)
              .build()
              .run(testId, editedMediaItem)
              .ssim;

      checkState(ssim != SSIM_UNSET, "SSIM has not been calculated.");
      return ssim;
    }

    /**
     * Returns whether the SSIM is acceptable.
     *
     * <p>Acceptable is defined as {@code ssim >= ssimTarget && ssim < ssimTarget +
     * positiveTolerance}, where {@code ssimTarget} is {@link #SSIM_TARGET} and {@code
     * positiveTolerance} is {@link #SSIM_ACCEPTABLE_TOLERANCE}.
     */
    private static boolean isSsimAcceptable(double ssim) {
      double ssimDifference = ssim - SsimBinarySearcher.SSIM_TARGET;
      return (0 <= ssimDifference)
          && (ssimDifference < SsimBinarySearcher.SSIM_ACCEPTABLE_TOLERANCE);
    }
  }
}
