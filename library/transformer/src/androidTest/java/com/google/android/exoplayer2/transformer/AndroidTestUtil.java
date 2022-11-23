/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_H264;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_H265;

import android.content.Context;
import android.os.Build;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/** Utilities for instrumentation tests. */
public final class AndroidTestUtil {
  private static final String TAG = "AndroidTestUtil";

  // Format values are sourced from `mediainfo` command.

  public static final String MP4_ASSET_URI_STRING = "asset:///media/mp4/sample.mp4";
  public static final Format MP4_ASSET_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1080)
          .setHeight(720)
          .setFrameRate(29.97f)
          .build();

  public static final String MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING =
      "asset:///media/mp4/sample_with_increasing_timestamps.mp4";
  public static final Format MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1920)
          .setHeight(1080)
          .setFrameRate(30.00f)
          .build();

  /** Baseline profile level 3.0 H.264 stream, which should be supported on all devices. */
  public static final String MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING =
      "asset:///media/mp4/sample_with_increasing_timestamps_320w_240h.mp4";

  public static final Format MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(320)
          .setHeight(240)
          .setFrameRate(30.00f)
          .build();

  public static final String MP4_ASSET_SEF_URI_STRING =
      "asset:///media/mp4/sample_sef_slow_motion.mp4";
  public static final Format MP4_ASSET_SEF_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(320)
          .setHeight(240)
          .setFrameRate(30.472f)
          .build();

  public static final String MP4_ASSET_1080P_4_SECOND_HDR10 =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/samsung-s21-hdr-hdr10.mp4";
  public static final Format MP4_ASSET_1080P_4_SECOND_HDR10_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H265)
          .setWidth(1920)
          .setHeight(1080)
          .setFrameRate(23.517f)
          .setColorInfo(
              new ColorInfo(
                  C.COLOR_SPACE_BT2020,
                  C.COLOR_RANGE_LIMITED,
                  C.COLOR_TRANSFER_ST2084,
                  /* hdrStaticInfo= */ null))
          .build();
  public static final String MP4_ASSET_1080P_1_SECOND_HDR10_VIDEO_SDR_CONTAINER =
      "asset:///media/mp4/hdr10-video-with-sdr-container.mp4";

  public static final String MP4_REMOTE_10_SECONDS_URI_STRING =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4";
  public static final Format MP4_REMOTE_10_SECONDS_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1280)
          .setHeight(720)
          .setFrameRate(29.97f)
          .build();

  /** Test clip transcoded from {@link #MP4_REMOTE_10_SECONDS_URI_STRING} with H264 and MP3. */
  public static final String MP4_REMOTE_H264_MP3_URI_STRING =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/%20android-screens-10s-h264-mp3.mp4";

  public static final Format MP4_REMOTE_H264_MP3_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1280)
          .setHeight(720)
          .setFrameRate(29.97f)
          .build();

  public static final String MP4_REMOTE_4K60_PORTRAIT_URI_STRING =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/portrait_4k60.mp4";
  public static final Format MP4_REMOTE_4K60_PORTRAIT_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(3840)
          .setHeight(2160)
          .setFrameRate(57.39f)
          .build();

  public static final String MP4_REMOTE_8K24_URI_STRING =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/8k24fps_4s.mp4";
  public static final Format MP4_REMOTE_8K24_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H265)
          .setWidth(7680)
          .setHeight(4320)
          .setFrameRate(24.00f)
          .build();

  // The 7 HIGHMOTION files are H264 and AAC.
  public static final String MP4_REMOTE_1280W_720H_5_SECOND_HIGHMOTION =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/1280w_720h_highmotion.mp4";
  public static final Format MP4_REMOTE_1280W_720H_5_SECOND_HIGHMOTION_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1280)
          .setHeight(720)
          .setAverageBitrate(8_939_000)
          .setFrameRate(30.075f)
          .build();

  public static final String MP4_REMOTE_1440W_1440H_5_SECOND_HIGHMOTION =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/1440w_1440h_highmotion.mp4";
  public static final Format MP4_REMOTE_1440W_1440H_5_SECOND_HIGHMOTION_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1440)
          .setHeight(1440)
          .setAverageBitrate(17_000_000)
          .setFrameRate(29.97f)
          .build();

  public static final String MP4_REMOTE_1920W_1080H_5_SECOND_HIGHMOTION =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/1920w_1080h_highmotion.mp4";
  public static final Format MP4_REMOTE_1920W_1080H_5_SECOND_HIGHMOTION_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1920)
          .setHeight(1080)
          .setAverageBitrate(17_100_000)
          .setFrameRate(30.037f)
          .build();

  public static final String MP4_REMOTE_3840W_2160H_5_SECOND_HIGHMOTION =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/3840w_2160h_highmotion.mp4";
  public static final Format MP4_REMOTE_3840W_2160H_5_SECOND_HIGHMOTION_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(3840)
          .setHeight(2160)
          .setAverageBitrate(48_300_000)
          .setFrameRate(30.090f)
          .build();

  public static final String MP4_REMOTE_1280W_720H_30_SECOND_HIGHMOTION =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/1280w_720h_30s_highmotion.mp4";
  public static final Format MP4_REMOTE_1280W_720H_30_SECOND_HIGHMOTION_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1280)
          .setHeight(720)
          .setAverageBitrate(9_962_000)
          .setFrameRate(30.078f)
          .build();

  public static final String MP4_REMOTE_1920W_1080H_30_SECOND_HIGHMOTION =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/1920w_1080h_30s_highmotion.mp4";
  public static final Format MP4_REMOTE_1920W_1080H_30_SECOND_HIGHMOTION_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1920)
          .setHeight(1080)
          .setAverageBitrate(15_000_000)
          .setFrameRate(28.561f)
          .build();

  public static final String MP4_REMOTE_3840W_2160H_32_SECOND_HIGHMOTION =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/3840w_2160h_32s_highmotion.mp4";
  public static final Format MP4_REMOTE_3840W_2160H_32_SECOND_HIGHMOTION_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(3840)
          .setHeight(2160)
          .setAverageBitrate(47_800_000)
          .setFrameRate(28.414f)
          .build();

  public static final String MP4_REMOTE_256W_144H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/OnePlusNord2_downsampled_256w_144h_30s_roof.mp4";
  public static final Format MP4_REMOTE_256W_144H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(256)
          .setHeight(144)
          .setFrameRate(30)
          .build();

  public static final String MP4_REMOTE_426W_240H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/OnePlusNord2_downsampled_426w_240h_30s_roof.mp4";
  public static final Format MP4_REMOTE_426W_240H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(426)
          .setHeight(240)
          .setFrameRate(30)
          .build();

  public static final String MP4_REMOTE_640W_360H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/OnePlusNord2_downsampled_640w_360h_30s_roof.mp4";
  public static final Format MP4_REMOTE_640W_360H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(640)
          .setHeight(360)
          .setFrameRate(30)
          .build();

  public static final String MP4_REMOTE_854W_480H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/OnePlusNord2_downsampled_854w_480h_30s_roof.mp4";
  public static final Format MP4_REMOTE_854W_480H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(854)
          .setHeight(480)
          .setFrameRate(30)
          .build();

  public static final String MP4_REMOTE_256W_144H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/RedmiNote9_downsampled_256w_144h_30s_roof.mp4";
  public static final Format MP4_REMOTE_256W_144H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(256)
          .setHeight(144)
          .setFrameRate(30)
          .build();

  public static final String MP4_REMOTE_426W_240H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/RedmiNote9_downsampled_426w_240h_30s_roof.mp4";
  public static final Format MP4_REMOTE_426W_240H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(426)
          .setHeight(240)
          .setFrameRate(30)
          .build();

  public static final String MP4_REMOTE_640W_360H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/RedmiNote9_downsampled_640w_360h_30s_roof.mp4";
  public static final Format MP4_REMOTE_640W_360H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(640)
          .setHeight(360)
          .setFrameRate(30)
          .build();

  public static final String MP4_REMOTE_854W_480H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/RedmiNote9_downsampled_854w_480h_30s_roof.mp4";
  public static final Format MP4_REMOTE_854W_480H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(854)
          .setHeight(480)
          .setFrameRate(30)
          .build();

  public static final String MP4_REMOTE_640W_480H_31_SECOND_ROOF_SONYXPERIAXZ3 =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/SonyXperiaXZ3_640w_480h_31s_roof.mp4";
  public static final Format MP4_REMOTE_640W_480H_31_SECOND_ROOF_SONYXPERIAXZ3_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(640)
          .setHeight(480)
          .setAverageBitrate(3_578_000)
          .setFrameRate(30)
          .build();

  public static final String MP4_REMOTE_1280W_720H_30_SECOND_ROOF_ONEPLUSNORD2 =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/OnePlusNord2_1280w_720h_30s_roof.mp4";
  public static final Format MP4_REMOTE_1280W_720H_30_SECOND_ROOF_ONEPLUSNORD2_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1280)
          .setHeight(720)
          .setAverageBitrate(8_966_000)
          .setFrameRate(29.763f)
          .build();

  public static final String MP4_REMOTE_1280W_720H_32_SECOND_ROOF_REDMINOTE9 =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/RedmiNote9_1280w_720h_32s_roof.mp4";
  public static final Format MP4_REMOTE_1280W_720H_32_SECOND_ROOF_REDMINOTE9_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1280)
          .setHeight(720)
          .setAverageBitrate(14_100_000)
          .setFrameRate(30)
          .build();

  public static final String MP4_REMOTE_1440W_1440H_31_SECOND_ROOF_SAMSUNGS20ULTRA5G =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/SsS20Ultra5G_1440hw_31s_roof.mp4";
  public static final Format MP4_REMOTE_1440W_1440H_31_SECOND_ROOF_SAMSUNGS20ULTRA5G_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1440)
          .setHeight(1440)
          .setAverageBitrate(16_300_000)
          .setFrameRate(25.931f)
          .build();

  public static final String MP4_REMOTE_1920W_1080H_60_FPS_30_SECOND_ROOF_ONEPLUSNORD2 =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/OnePlusNord2_1920w_1080h_60fr_30s_roof.mp4";
  public static final Format MP4_REMOTE_1920W_1080H_60_FPS_30_SECOND_ROOF_ONEPLUSNORD2_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1920)
          .setHeight(1080)
          .setAverageBitrate(20_000_000)
          .setFrameRate(59.94f)
          .build();

  public static final String MP4_REMOTE_1920W_1080H_60_FPS_30_SECOND_ROOF_REDMINOTE9 =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/RedmiNote9_1920w_1080h_60fps_30s_roof.mp4";
  public static final Format MP4_REMOTE_1920W_1080H_60_FPS_30_SECOND_ROOF_REDMINOTE9_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1920)
          .setHeight(1080)
          .setAverageBitrate(20_100_000)
          .setFrameRate(61.069f)
          .build();

  public static final String MP4_REMOTE_2400W_1080H_34_SECOND_ROOF_SAMSUNGS20ULTRA5G =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/SsS20Ultra5G_2400w_1080h_34s_roof.mp4";
  public static final Format MP4_REMOTE_2400W_1080H_34_SECOND_ROOF_SAMSUNGS20ULTRA5G_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H265)
          .setWidth(2400)
          .setHeight(1080)
          .setAverageBitrate(29_500_000)
          .setFrameRate(27.472f)
          .build();

  public static final String MP4_REMOTE_3840W_2160H_30_SECOND_ROOF_ONEPLUSNORD2 =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/OnePlusNord2_3840w_2160h_30s_roof.mp4";
  public static final Format MP4_REMOTE_3840W_2160H_30_SECOND_ROOF_ONEPLUSNORD2_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(3840)
          .setHeight(2160)
          .setAverageBitrate(49_800_000)
          .setFrameRate(29.802f)
          .build();

  public static final String MP4_REMOTE_3840W_2160H_30_SECOND_ROOF_REDMINOTE9 =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/RedmiNote9_3840w_2160h_30s_roof.mp4";
  public static final Format MP4_REMOTE_3840W_2160H_30_SECOND_ROOF_REDMINOTE9_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(3840)
          .setHeight(2160)
          .setAverageBitrate(42_100_000)
          .setFrameRate(30)
          .build();

  public static final String MP4_REMOTE_7680W_4320H_31_SECOND_ROOF_SAMSUNGS20ULTRA5G =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/SsS20Ultra5G_7680w_4320h_31s_roof.mp4";
  public static final Format MP4_REMOTE_7680W_4320H_31_SECOND_ROOF_SAMSUNGS20ULTRA5G_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H265)
          .setWidth(7680)
          .setHeight(4320)
          .setAverageBitrate(79_900_000)
          .setFrameRate(23.163f)
          .build();
  /**
   * Log in logcat and in an analysis file that this test was skipped.
   *
   * <p>Analysis file is a JSON summarising the test, saved to the application cache.
   *
   * <p>The analysis json will contain a {@code skipReason} key, with the reason for skipping the
   * test case.
   */
  public static void recordTestSkipped(Context context, String testId, String reason)
      throws JSONException, IOException {
    Log.i(TAG, testId + ": " + reason);
    JSONObject testJson = new JSONObject();
    testJson.put("skipReason", reason);

    writeTestSummaryToFile(context, testId, testJson);
  }

  /** A customizable forwarding {@link Codec.EncoderFactory} that forces encoding. */
  public static final class ForceEncodeEncoderFactory implements Codec.EncoderFactory {

    private final Codec.EncoderFactory encoderFactory;

    /** Creates an instance that wraps {@link DefaultEncoderFactory}. */
    public ForceEncodeEncoderFactory(Context context) {
      encoderFactory = new DefaultEncoderFactory.Builder(context).build();
    }

    /**
     * Creates an instance that wraps {@link DefaultEncoderFactory} that wraps another {@link
     * Codec.EncoderFactory}.
     */
    public ForceEncodeEncoderFactory(Codec.EncoderFactory wrappedEncoderFactory) {
      this.encoderFactory = wrappedEncoderFactory;
    }

    @Override
    public Codec createForAudioEncoding(Format format, List<String> allowedMimeTypes)
        throws TransformationException {
      return encoderFactory.createForAudioEncoding(format, allowedMimeTypes);
    }

    @Override
    public Codec createForVideoEncoding(Format format, List<String> allowedMimeTypes)
        throws TransformationException {
      return encoderFactory.createForVideoEncoding(format, allowedMimeTypes);
    }

    @Override
    public boolean audioNeedsEncoding() {
      return true;
    }

    @Override
    public boolean videoNeedsEncoding() {
      return true;
    }
  }

  /**
   * Returns a {@link JSONObject} containing device specific details from {@link Build}, including
   * manufacturer, model, SDK version and build fingerprint.
   */
  public static JSONObject getDeviceDetailsAsJsonObject() throws JSONException {
    return new JSONObject()
        .put("manufacturer", Build.MANUFACTURER)
        .put("model", Build.MODEL)
        .put("sdkVersion", Build.VERSION.SDK_INT)
        .put("fingerprint", Build.FINGERPRINT);
  }

  /**
   * Converts an exception to a {@link JSONObject}.
   *
   * <p>If the exception is a {@link TransformationException}, {@code errorCode} is included.
   */
  public static JSONObject exceptionAsJsonObject(Exception exception) throws JSONException {
    JSONObject exceptionJson = new JSONObject();
    exceptionJson.put("message", exception.getMessage());
    exceptionJson.put("type", exception.getClass());
    if (exception instanceof TransformationException) {
      exceptionJson.put("errorCode", ((TransformationException) exception).errorCode);
    }
    exceptionJson.put("stackTrace", Log.getThrowableString(exception));
    return exceptionJson;
  }

  /**
   * Writes the summary of a test run to the application cache file.
   *
   * <p>The cache filename follows the pattern {@code <testId>-result.txt}.
   *
   * @param context The {@link Context}.
   * @param testId A unique identifier for the transformer test run.
   * @param testJson A {@link JSONObject} containing a summary of the test run.
   */
  public static void writeTestSummaryToFile(Context context, String testId, JSONObject testJson)
      throws IOException, JSONException {
    testJson.put("testId", testId).put("device", getDeviceDetailsAsJsonObject());

    String analysisContents = testJson.toString(/* indentSpaces= */ 2);

    // Log contents as well as writing to file, for easier visibility on individual device testing.
    for (String line : Util.split(analysisContents, "\n")) {
      Log.i(TAG, testId + ": " + line);
    }

    File analysisFile = createExternalCacheFile(context, /* fileName= */ testId + "-result.txt");
    try (FileWriter fileWriter = new FileWriter(analysisFile)) {
      fileWriter.write(analysisContents);
    }
  }

  /**
   * Checks whether the test should be skipped because the device is incapable of decoding and
   * encoding the given formats.
   *
   * <p>If the test should be skipped, logs the reason for skipping.
   *
   * @param context The {@link Context context}.
   * @param testId The test ID.
   * @param decodingFormat The {@link Format format} to decode.
   * @param encodingFormat The {@link Format format} to encode, optional.
   * @return Whether the test should be skipped.
   */
  public static boolean skipAndLogIfInsufficientCodecSupport(
      Context context, String testId, Format decodingFormat, @Nullable Format encodingFormat)
      throws IOException, JSONException {
    boolean canDecode = false;
    @Nullable MediaCodecUtil.DecoderQueryException queryException = null;
    try {
      canDecode = canDecode(decodingFormat);
    } catch (MediaCodecUtil.DecoderQueryException e) {
      queryException = e;
    }

    boolean canEncode = encodingFormat == null || canEncode(encodingFormat);

    if (canDecode && canEncode) {
      return false;
    }

    StringBuilder skipReasonBuilder = new StringBuilder();
    if (!canDecode) {
      skipReasonBuilder.append("Cannot decode ").append(decodingFormat).append('\n');
      if (queryException != null) {
        skipReasonBuilder.append(queryException).append('\n');
      }
    }
    if (!canEncode) {
      skipReasonBuilder.append("Cannot encode ").append(encodingFormat);
    }
    recordTestSkipped(context, testId, skipReasonBuilder.toString());
    return true;
  }

  /**
   * Returns the {@link Format} of the given test asset.
   *
   * @param uri The string {@code uri} to the test file. The {@code uri} must be defined in this
   *     file.
   * @throws IllegalArgumentException If the given {@code uri} is not defined in this file.
   */
  public static Format getFormatForTestFile(String uri) {
    switch (uri) {
      case MP4_ASSET_URI_STRING:
        return MP4_ASSET_FORMAT;
      case MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING:
        return MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT;
      case MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING:
        return MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT;
      case MP4_ASSET_SEF_URI_STRING:
        return MP4_ASSET_SEF_FORMAT;
      case MP4_REMOTE_10_SECONDS_URI_STRING:
        return MP4_REMOTE_10_SECONDS_FORMAT;
      case MP4_REMOTE_H264_MP3_URI_STRING:
        return MP4_REMOTE_H264_MP3_FORMAT;
      case MP4_REMOTE_4K60_PORTRAIT_URI_STRING:
        return MP4_REMOTE_4K60_PORTRAIT_FORMAT;
      case MP4_REMOTE_256W_144H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED:
        return MP4_REMOTE_256W_144H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED_FORMAT;
      case MP4_REMOTE_426W_240H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED:
        return MP4_REMOTE_426W_240H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED_FORMAT;
      case MP4_REMOTE_640W_360H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED:
        return MP4_REMOTE_640W_360H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED_FORMAT;
      case MP4_REMOTE_854W_480H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED:
        return MP4_REMOTE_854W_480H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED_FORMAT;
      case MP4_REMOTE_256W_144H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED:
        return MP4_REMOTE_256W_144H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED_FORMAT;
      case MP4_REMOTE_426W_240H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED:
        return MP4_REMOTE_426W_240H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED_FORMAT;
      case MP4_REMOTE_640W_360H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED:
        return MP4_REMOTE_640W_360H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED_FORMAT;
      case MP4_REMOTE_854W_480H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED:
        return MP4_REMOTE_854W_480H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED_FORMAT;
      case MP4_REMOTE_640W_480H_31_SECOND_ROOF_SONYXPERIAXZ3:
        return MP4_REMOTE_640W_480H_31_SECOND_ROOF_SONYXPERIAXZ3_FORMAT;
      case MP4_REMOTE_1280W_720H_5_SECOND_HIGHMOTION:
        return MP4_REMOTE_1280W_720H_5_SECOND_HIGHMOTION_FORMAT;
      case MP4_REMOTE_1280W_720H_30_SECOND_HIGHMOTION:
        return MP4_REMOTE_1280W_720H_30_SECOND_HIGHMOTION_FORMAT;
      case MP4_REMOTE_1280W_720H_30_SECOND_ROOF_ONEPLUSNORD2:
        return MP4_REMOTE_1280W_720H_30_SECOND_ROOF_ONEPLUSNORD2_FORMAT;
      case MP4_REMOTE_1280W_720H_32_SECOND_ROOF_REDMINOTE9:
        return MP4_REMOTE_1280W_720H_32_SECOND_ROOF_REDMINOTE9_FORMAT;
      case MP4_REMOTE_1440W_1440H_5_SECOND_HIGHMOTION:
        return MP4_REMOTE_1440W_1440H_5_SECOND_HIGHMOTION_FORMAT;
      case MP4_REMOTE_1440W_1440H_31_SECOND_ROOF_SAMSUNGS20ULTRA5G:
        return MP4_REMOTE_1440W_1440H_31_SECOND_ROOF_SAMSUNGS20ULTRA5G_FORMAT;
      case MP4_REMOTE_1920W_1080H_5_SECOND_HIGHMOTION:
        return MP4_REMOTE_1920W_1080H_5_SECOND_HIGHMOTION_FORMAT;
      case MP4_REMOTE_1920W_1080H_30_SECOND_HIGHMOTION:
        return MP4_REMOTE_1920W_1080H_30_SECOND_HIGHMOTION_FORMAT;
      case MP4_REMOTE_1920W_1080H_60_FPS_30_SECOND_ROOF_ONEPLUSNORD2:
        return MP4_REMOTE_1920W_1080H_60_FPS_30_SECOND_ROOF_ONEPLUSNORD2_FORMAT;
      case MP4_REMOTE_1920W_1080H_60_FPS_30_SECOND_ROOF_REDMINOTE9:
        return MP4_REMOTE_1920W_1080H_60_FPS_30_SECOND_ROOF_REDMINOTE9_FORMAT;
      case MP4_REMOTE_2400W_1080H_34_SECOND_ROOF_SAMSUNGS20ULTRA5G:
        return MP4_REMOTE_2400W_1080H_34_SECOND_ROOF_SAMSUNGS20ULTRA5G_FORMAT;
      case MP4_REMOTE_3840W_2160H_5_SECOND_HIGHMOTION:
        return MP4_REMOTE_3840W_2160H_5_SECOND_HIGHMOTION_FORMAT;
      case MP4_REMOTE_3840W_2160H_32_SECOND_HIGHMOTION:
        return MP4_REMOTE_3840W_2160H_32_SECOND_HIGHMOTION_FORMAT;
      case MP4_REMOTE_3840W_2160H_30_SECOND_ROOF_ONEPLUSNORD2:
        return MP4_REMOTE_3840W_2160H_30_SECOND_ROOF_ONEPLUSNORD2_FORMAT;
      case MP4_REMOTE_3840W_2160H_30_SECOND_ROOF_REDMINOTE9:
        return MP4_REMOTE_3840W_2160H_30_SECOND_ROOF_REDMINOTE9_FORMAT;
      case MP4_REMOTE_7680W_4320H_31_SECOND_ROOF_SAMSUNGS20ULTRA5G:
        return MP4_REMOTE_7680W_4320H_31_SECOND_ROOF_SAMSUNGS20ULTRA5G_FORMAT;
      default:
        throw new IllegalArgumentException("The format for the given uri is not found.");
    }
  }

  private static boolean canDecode(Format format) throws MediaCodecUtil.DecoderQueryException {
    @Nullable
    MediaCodecInfo decoderInfo =
        MediaCodecUtil.getDecoderInfo(
            checkNotNull(format.sampleMimeType), /* secure= */ false, /* tunneling= */ false);
    if (decoderInfo == null) {
      return false;
    }
    // Use Format.NO_VALUE for frame rate to only check whether size is supported.
    return decoderInfo.isVideoSizeAndRateSupportedV21(
        format.width, format.height, /* frameRate= */ Format.NO_VALUE);
  }

  /**
   * Checks whether the top ranked encoder from {@link EncoderUtil#getSupportedEncoders} supports
   * the given resolution and {@linkplain Format#averageBitrate bitrate}.
   *
   * <p>Assumes support encoding if the {@link Format#averageBitrate bitrate} is not set.
   */
  private static boolean canEncode(Format format) {
    String mimeType = checkNotNull(format.sampleMimeType);
    ImmutableList<android.media.MediaCodecInfo> supportedEncoders =
        EncoderUtil.getSupportedEncoders(mimeType);
    if (supportedEncoders.isEmpty()) {
      return false;
    }

    android.media.MediaCodecInfo encoder = supportedEncoders.get(0);
    boolean sizeSupported =
        EncoderUtil.isSizeSupported(encoder, mimeType, format.width, format.height);
    boolean bitrateSupported =
        format.averageBitrate == Format.NO_VALUE
            || EncoderUtil.getSupportedBitrateRange(encoder, mimeType)
                .contains(format.averageBitrate);
    return sizeSupported && bitrateSupported;
  }

  /**
   * Creates a {@link File} of the {@code fileName} in the application cache directory.
   *
   * <p>If a file of that name already exists, it is overwritten.
   */
  /* package */ static File createExternalCacheFile(Context context, String fileName)
      throws IOException {
    File file = new File(context.getExternalCacheDir(), fileName);
    checkState(!file.exists() || file.delete(), "Could not delete file: " + file.getAbsolutePath());
    checkState(file.createNewFile(), "Could not create file: " + file.getAbsolutePath());
    return file;
  }

  private AndroidTestUtil() {}
}
