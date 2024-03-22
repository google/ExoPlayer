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

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1280W_720H_30_SECOND_HIGHMOTION;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1280W_720H_30_SECOND_ROOF_ONEPLUSNORD2;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1280W_720H_32_SECOND_ROOF_REDMINOTE9;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1280W_720H_5_SECOND_HIGHMOTION;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1440W_1440H_31_SECOND_ROOF_SAMSUNGS20ULTRA5G;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1440W_1440H_5_SECOND_HIGHMOTION;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1920W_1080H_30_SECOND_HIGHMOTION;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1920W_1080H_5_SECOND_HIGHMOTION;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1920W_1080H_60_FPS_30_SECOND_ROOF_ONEPLUSNORD2;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_1920W_1080H_60_FPS_30_SECOND_ROOF_REDMINOTE9;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_2400W_1080H_34_SECOND_ROOF_SAMSUNGS20ULTRA5G;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_3840W_2160H_30_SECOND_ROOF_ONEPLUSNORD2;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_3840W_2160H_30_SECOND_ROOF_REDMINOTE9;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_3840W_2160H_32_SECOND_HIGHMOTION;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_3840W_2160H_5_SECOND_HIGHMOTION;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_640W_480H_31_SECOND_ROOF_SONYXPERIAXZ3;
import static androidx.media3.transformer.AndroidTestUtil.MP4_REMOTE_7680W_4320H_31_SECOND_ROOF_SAMSUNGS20ULTRA5G;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Assertions;
import androidx.media3.transformer.AndroidTestUtil;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.media3.transformer.VideoEncoderSettings;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Instrumentation tests for analysing output bitrate and quality for a given input bitrate. */
@RunWith(Parameterized.class)
@Ignore(
    "Analysis tests are not used for confirming Transformer is running properly, and not configured"
        + " for this use as they're missing skip checks for unsupported devices.")
public class BitrateAnalysisTest {
  private static final ImmutableList<String> INPUT_FILES =
      ImmutableList.of(
          MP4_REMOTE_640W_480H_31_SECOND_ROOF_SONYXPERIAXZ3,
          MP4_REMOTE_1280W_720H_5_SECOND_HIGHMOTION,
          MP4_REMOTE_1280W_720H_30_SECOND_HIGHMOTION,
          MP4_REMOTE_1280W_720H_30_SECOND_ROOF_ONEPLUSNORD2,
          MP4_REMOTE_1280W_720H_32_SECOND_ROOF_REDMINOTE9,
          MP4_REMOTE_1440W_1440H_5_SECOND_HIGHMOTION,
          MP4_REMOTE_1440W_1440H_31_SECOND_ROOF_SAMSUNGS20ULTRA5G,
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

  private static final ImmutableList<Integer> INPUT_BITRATE_MODES =
      ImmutableList.of(BITRATE_MODE_VBR, BITRATE_MODE_CBR);

  private static final int START_BITRATE = 2_000_000;
  private static final int END_BITRATE = 10_000_000;
  private static final int BITRATE_INTERVAL = 1_000_000;

  @Parameter(0)
  public int bitrate;

  @Parameter(1)
  public int bitrateMode;

  @Parameter(2)
  public @MonotonicNonNull String fileUri;

  @Parameters(name = "analyzeBitrate_{0}_{1}_{2}")
  public static List<Object[]> parameters() {
    List<Object[]> parameterList = new ArrayList<>();
    for (int bitrate = START_BITRATE; bitrate <= END_BITRATE; bitrate += BITRATE_INTERVAL) {
      for (int mode : INPUT_BITRATE_MODES) {
        for (String file : INPUT_FILES) {
          parameterList.add(new Object[] {bitrate, mode, file});
        }
      }
    }

    return parameterList;
  }

  @Test
  public void analyzeBitrate() throws Exception {
    Assertions.checkNotNull(fileUri);
    String fileName = Assertions.checkNotNull(Iterables.getLast(Splitter.on("/").split(fileUri)));
    String testId = String.format("analyzeBitrate_ssim_%s_%d_%s", bitrate, bitrateMode, fileName);

    Map<String, Object> inputValues = new HashMap<>();
    inputValues.put("targetBitrate", bitrate);
    inputValues.put("inputFilename", fileName);
    if (bitrateMode == BITRATE_MODE_CBR) {
      inputValues.put("bitrateMode", "CBR");
    } else if (bitrateMode == BITRATE_MODE_VBR) {
      inputValues.put("bitrateMode", "VBR");
    }

    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ AndroidTestUtil.getFormatForTestFile(fileUri),
        /* outputFormat= */ AndroidTestUtil.getFormatForTestFile(fileUri)
            .buildUpon()
            .setAverageBitrate(bitrate)
            .build());

    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new AndroidTestUtil.ForceEncodeEncoderFactory(
                    /* wrappedEncoderFactory= */ new DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(
                            new VideoEncoderSettings.Builder()
                                .setBitrate(bitrate)
                                .setBitrateMode(bitrateMode)
                                .build())
                        .setEnableFallback(false)
                        .build()))
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(fileUri)))
            .setRemoveAudio(true)
            .build();

    new TransformerAndroidTestRunner.Builder(context, transformer)
        .setInputValues(inputValues)
        .setRequestCalculateSsim(true)
        .build()
        .run(testId, editedMediaItem);
  }
}
