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
package androidx.media3.transformer;

import static androidx.media3.common.MimeTypes.VIDEO_AV1;
import static androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION;
import static androidx.media3.common.MimeTypes.VIDEO_H264;
import static androidx.media3.common.MimeTypes.VIDEO_H265;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.SDK_INT;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.media.MediaFormat;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.common.util.Util;
import androidx.media3.effect.DefaultGlObjectsProvider;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.media3.test.utils.VideoDecodingWrapper;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;

/** Utilities for instrumentation tests. */
public final class AndroidTestUtil {
  private static final String TAG = "AndroidTestUtil";

  /** An {@link Effects} instance that forces video transcoding. */
  public static final Effects FORCE_TRANSCODE_VIDEO_EFFECTS =
      new Effects(
          /* audioProcessors= */ ImmutableList.of(),
          ImmutableList.of(
              new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build()));

  public static final String PNG_ASSET_URI_STRING =
      "asset:///media/bitmap/input_images/media3test.png";
  public static final String JPG_ASSET_URI_STRING = "asset:///media/bitmap/input_images/london.jpg";
  public static final String JPG_PORTRAIT_ASSET_URI_STRING =
      "asset:///media/bitmap/input_images/tokyo.jpg";

  public static final String MP4_TRIM_OPTIMIZATION_URI_STRING =
      "asset:///media/mp4/internal_emulator_transformer_output.mp4";

  public static final String MP4_ASSET_URI_STRING = "asset:///media/mp4/sample.mp4";
  public static final Format MP4_ASSET_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1080)
          .setHeight(720)
          .setFrameRate(29.97f)
          .setCodecs("avc1.64001F")
          .build();

  // Result of the following command for MP4_ASSET_URI_STRING
  // ffprobe -count_frames -select_streams v:0 -show_entries stream=nb_read_frames sample.mp4
  public static final int MP4_ASSET_FRAME_COUNT = 30;

  public static final String MP4_PORTRAIT_ASSET_URI_STRING =
      "asset:///media/mp4/sample_portrait.mp4";
  public static final Format MP4_PORTRAIT_ASSET_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(720)
          .setHeight(1080)
          .setFrameRate(29.97f)
          .setCodecs("avc1.64001F")
          .build();

  public static final String MP4_ASSET_AV1_VIDEO_URI_STRING = "asset:///media/mp4/sample_av1.mp4";
  public static final Format MP4_ASSET_AV1_VIDEO_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_AV1)
          .setWidth(1080)
          .setHeight(720)
          .setFrameRate(30.0f)
          .build();

  public static final String MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING =
      "asset:///media/mp4/sample_with_increasing_timestamps.mp4";
  public static final Format MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1920)
          .setHeight(1080)
          .setFrameRate(30.00f)
          .setCodecs("avc1.42C033")
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
          .setCodecs("avc1.42C015")
          .build();

  public static final String MP4_ASSET_SEF_URI_STRING =
      "asset:///media/mp4/sample_sef_slow_motion.mp4";
  public static final Format MP4_ASSET_SEF_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(320)
          .setHeight(240)
          .setFrameRate(30.472f)
          .setCodecs("avc1.64000D")
          .build();

  public static final String MP4_ASSET_SEF_H265_URI_STRING =
      "asset:///media/mp4/sample_sef_slow_motion_hevc.mp4";
  public static final Format MP4_ASSET_SEF_H265_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H265)
          .setWidth(1920)
          .setHeight(1080)
          .setFrameRate(30.01679f)
          .setCodecs("hvc1.1.6.L120.B0")
          .build();

  public static final String MP4_ASSET_BT2020_SDR = "asset:///media/mp4/bt2020-sdr.mp4";
  public static final Format MP4_ASSET_BT2020_SDR_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(3840)
          .setHeight(2160)
          .setFrameRate(29.822f)
          .setColorInfo(
              new ColorInfo.Builder()
                  .setColorSpace(C.COLOR_SPACE_BT2020)
                  .setColorRange(C.COLOR_RANGE_LIMITED)
                  .setColorTransfer(C.COLOR_TRANSFER_SDR)
                  .build())
          .setCodecs("avc1.640033")
          .build();

  public static final String MP4_ASSET_1080P_5_SECOND_HLG10 = "asset:///media/mp4/hlg-1080p.mp4";
  public static final Format MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H265)
          .setWidth(1920)
          .setHeight(1080)
          .setFrameRate(30.000f)
          .setColorInfo(
              new ColorInfo.Builder()
                  .setColorSpace(C.COLOR_SPACE_BT2020)
                  .setColorRange(C.COLOR_RANGE_LIMITED)
                  .setColorTransfer(C.COLOR_TRANSFER_HLG)
                  .build())
          .setCodecs("hvc1.2.4.L153")
          .build();
  public static final String MP4_ASSET_720P_4_SECOND_HDR10 = "asset:///media/mp4/hdr10-720p.mp4";
  public static final Format MP4_ASSET_720P_4_SECOND_HDR10_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H265)
          .setWidth(1280)
          .setHeight(720)
          .setFrameRate(29.97f)
          .setColorInfo(
              new ColorInfo.Builder()
                  .setColorSpace(C.COLOR_SPACE_BT2020)
                  .setColorRange(C.COLOR_RANGE_LIMITED)
                  .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                  .build())
          .setCodecs("hvc1.2.4.L153")
          .build();

  // This file needs alternative MIME type, meaning the decoder needs to be configured with
  // video/hevc instead of video/dolby-vision.
  public static final String MP4_ASSET_DOLBY_VISION_HDR = "asset:///media/mp4/dolbyVision-hdr.MOV";
  public static final Format MP4_ASSET_DOLBY_VISION_HDR_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_DOLBY_VISION)
          .setWidth(1280)
          .setHeight(720)
          .setFrameRate(30.00f)
          .setCodecs("hev1.08.02")
          .setColorInfo(
              new ColorInfo.Builder()
                  .setColorTransfer(C.COLOR_TRANSFER_HLG)
                  .setColorRange(C.COLOR_RANGE_LIMITED)
                  .setColorSpace(C.COLOR_SPACE_BT2020)
                  .build())
          .build();

  public static final String MP4_ASSET_4K60_PORTRAIT_URI_STRING =
      "asset:///media/mp4/portrait_4k60.mp4";
  public static final Format MP4_ASSET_4K60_PORTRAIT_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(3840)
          .setHeight(2160)
          .setFrameRate(60.00f)
          .setCodecs("avc1.640033")
          .build();

  public static final String MP4_REMOTE_10_SECONDS_URI_STRING =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4";
  public static final Format MP4_REMOTE_10_SECONDS_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1280)
          .setHeight(720)
          .setFrameRate(29.97f)
          .setCodecs("avc1.64001F")
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
          .setCodecs("avc1.64001F")
          .build();

  public static final String MP4_ASSET_8K24_URI_STRING = "asset:///media/mp4/8k24fps_300ms.mp4";
  public static final Format MP4_ASSET_8K24_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H265)
          .setWidth(7680)
          .setHeight(4320)
          .setFrameRate(24.00f)
          .setCodecs("hvc1.1.6.L183")
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
          .setCodecs("avc1.64001F")
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
          .setCodecs("avc1.640028")
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
          .setCodecs("avc1.640028")
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
          .setCodecs("avc1.640033")
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
          .setCodecs("avc1.64001F")
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
          .setCodecs("avc1.640028")
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
          .setCodecs("avc1.640033")
          .build();

  public static final String MP4_REMOTE_256W_144H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/OnePlusNord2_downsampled_256w_144h_30s_roof.mp4";
  public static final Format MP4_REMOTE_256W_144H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(256)
          .setHeight(144)
          .setFrameRate(30)
          .setCodecs("avc1.64000C")
          .build();

  public static final String MP4_REMOTE_426W_240H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/OnePlusNord2_downsampled_426w_240h_30s_roof.mp4";
  public static final Format MP4_REMOTE_426W_240H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(426)
          .setHeight(240)
          .setFrameRate(30)
          .setCodecs("avc1.640015")
          .build();

  public static final String MP4_REMOTE_640W_360H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/OnePlusNord2_downsampled_640w_360h_30s_roof.mp4";
  public static final Format MP4_REMOTE_640W_360H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(640)
          .setHeight(360)
          .setFrameRate(30)
          .setCodecs("avc1.64001E")
          .build();

  public static final String MP4_REMOTE_854W_480H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/OnePlusNord2_downsampled_854w_480h_30s_roof.mp4";
  public static final Format MP4_REMOTE_854W_480H_30_SECOND_ROOF_ONEPLUSNORD2_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(854)
          .setHeight(480)
          .setFrameRate(30)
          .setCodecs("avc1.64001F")
          .build();

  public static final String MP4_REMOTE_256W_144H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/RedmiNote9_downsampled_256w_144h_30s_roof.mp4";
  public static final Format MP4_REMOTE_256W_144H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(256)
          .setHeight(144)
          .setFrameRate(30)
          .setCodecs("avc1.64000C")
          .build();

  public static final String MP4_REMOTE_426W_240H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/RedmiNote9_downsampled_426w_240h_30s_roof.mp4";
  public static final Format MP4_REMOTE_426W_240H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(426)
          .setHeight(240)
          .setFrameRate(30)
          .setCodecs("avc1.640015")
          .build();

  public static final String MP4_REMOTE_640W_360H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/RedmiNote9_downsampled_640w_360h_30s_roof.mp4";
  public static final Format MP4_REMOTE_640W_360H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(640)
          .setHeight(360)
          .setFrameRate(30)
          .setCodecs("avc1.64001E")
          .build();

  public static final String MP4_REMOTE_854W_480H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/device_videos/RedmiNote9_downsampled_854w_480h_30s_roof.mp4";
  public static final Format MP4_REMOTE_854W_480H_30_SECOND_ROOF_REDMINOTE9_DOWNSAMPLED_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(854)
          .setHeight(480)
          .setFrameRate(30)
          .setCodecs("avc1.64001F")
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
          .setCodecs("avc1.64001E")
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
          .setCodecs("avc1.640028")
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
          .setCodecs("avc1.64001F")
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
          .setCodecs("avc1.640028")
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
          .setCodecs("avc1.640028")
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
          .setCodecs("avc1.64002A")
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
          .setCodecs("hvc1.2.4.L153.B0")
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
          .setCodecs("avc1.640028")
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
          .setColorInfo(
              new ColorInfo.Builder()
                  .setColorSpace(C.COLOR_SPACE_BT2020)
                  .setColorRange(C.COLOR_RANGE_FULL)
                  .setColorTransfer(C.COLOR_TRANSFER_SDR)
                  .build())
          .setCodecs("avc1.640033")
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
          .setCodecs("hvc1.1.6.L183.B0")
          .build();

  public static final String MP3_ASSET_URI_STRING = "asset:///media/mp3/test-cbr-info-header.mp3";

  /**
   * Creates the GL objects needed to set up a GL environment including an {@link EGLDisplay} and an
   * {@link EGLContext}.
   */
  public static EGLContext createOpenGlObjects() throws GlUtil.GlException {
    EGLDisplay eglDisplay = GlUtil.getDefaultEglDisplay();
    GlObjectsProvider glObjectsProvider =
        new DefaultGlObjectsProvider(/* sharedEglContext= */ null);
    EGLContext eglContext =
        glObjectsProvider.createEglContext(
            eglDisplay, /* openGlVersion= */ 2, GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888);
    glObjectsProvider.createFocusedPlaceholderEglSurface(eglContext, eglDisplay);
    return eglContext;
  }

  /**
   * Generates a {@linkplain android.opengl.GLES10#GL_TEXTURE_2D traditional GLES texture} from the
   * given bitmap.
   *
   * <p>Must have a GL context set up.
   */
  public static int generateTextureFromBitmap(Bitmap bitmap) throws GlUtil.GlException {
    return GlUtil.createTexture(bitmap);
  }

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

  public static ImmutableList<Bitmap> extractBitmapsFromVideo(Context context, String filePath)
      throws IOException, InterruptedException {
    // b/298599172 - runUntilComparisonFrameOrEnded fails on this device because reading decoder
    //  output as a bitmap doesn't work.
    assumeFalse(Util.SDK_INT == 21 && Ascii.toLowerCase(Util.MODEL).contains("nexus"));
    ImmutableList.Builder<Bitmap> bitmaps = new ImmutableList.Builder<>();
    try (VideoDecodingWrapper decodingWrapper =
        new VideoDecodingWrapper(
            context, filePath, /* comparisonInterval= */ 1, /* maxImagesAllowed= */ 1)) {
      while (true) {
        @Nullable Image image = decodingWrapper.runUntilComparisonFrameOrEnded();
        if (image == null) {
          break;
        }
        bitmaps.add(BitmapPixelTestUtil.createGrayscaleArgb8888BitmapFromYuv420888Image(image));
        image.close();
      }
    }
    return bitmaps.build();
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
    public Codec createForAudioEncoding(Format format) throws ExportException {
      return encoderFactory.createForAudioEncoding(format);
    }

    @Override
    public Codec createForVideoEncoding(Format format) throws ExportException {
      return encoderFactory.createForVideoEncoding(format);
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
    testJson.put("testId", testId).put("device", JsonUtil.getDeviceDetailsAsJsonObject());

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
   * Returns whether the test should be skipped because the device is incapable of decoding the
   * input format, or encoding/muxing the output format. Assumes the input will always need to be
   * decoded, and both encoded and muxed if {@code outputFormat} is non-null.
   *
   * <p>If the test should be skipped, logs the reason for skipping.
   *
   * @param context The {@link Context context}.
   * @param testId The test ID.
   * @param inputFormat The {@link Format format} to decode.
   * @param outputFormat The {@link Format format} to encode/mux or {@code null} if the output won't
   *     be encoded or muxed.
   * @return Whether the test should be skipped.
   */
  public static boolean skipAndLogIfFormatsUnsupported(
      Context context, String testId, Format inputFormat, @Nullable Format outputFormat)
      throws IOException, JSONException, MediaCodecUtil.DecoderQueryException {
    // TODO(b/278657595): Make this capability check match the default codec factory selection code.
    boolean canDecode = canDecode(inputFormat);

    boolean canEncode = outputFormat == null || canEncode(outputFormat);
    boolean canMux = outputFormat == null || canMux(outputFormat);
    if (canDecode && canEncode && canMux) {
      return false;
    }

    StringBuilder skipReasonBuilder = new StringBuilder();
    if (!canDecode) {
      skipReasonBuilder.append("Cannot decode ").append(inputFormat).append('\n');
    }
    if (!canEncode) {
      skipReasonBuilder.append("Cannot encode ").append(outputFormat).append('\n');
    }
    if (!canMux) {
      skipReasonBuilder.append("Cannot mux ").append(outputFormat);
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
      case MP4_ASSET_SEF_H265_URI_STRING:
        return MP4_ASSET_SEF_H265_FORMAT;
      case MP4_ASSET_4K60_PORTRAIT_URI_STRING:
        return MP4_ASSET_4K60_PORTRAIT_FORMAT;
      case MP4_REMOTE_10_SECONDS_URI_STRING:
        return MP4_REMOTE_10_SECONDS_FORMAT;
      case MP4_REMOTE_H264_MP3_URI_STRING:
        return MP4_REMOTE_H264_MP3_FORMAT;
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

  private static boolean canDecode(Format format) {
    // Check decoding capability in the same way as the default decoder factory.
    MediaFormat mediaFormat = MediaFormatUtil.createMediaFormatFromFormat(format);
    @Nullable
    Pair<Integer, Integer> codecProfileAndLevel = MediaCodecUtil.getCodecProfileAndLevel(format);
    if (codecProfileAndLevel != null) {
      MediaFormatUtil.maybeSetInteger(
          mediaFormat, MediaFormat.KEY_PROFILE, codecProfileAndLevel.first);
    }
    return EncoderUtil.findCodecForFormat(mediaFormat, /* isDecoder= */ true) != null
        && !deviceNeedsDisable8kWorkaround(format);
  }

  private static boolean deviceNeedsDisable8kWorkaround(Format format) {
    // Fixed on API 31+. See http://b/278234847#comment40 for more information.
    // Duplicate of DefaultDecoderFactory#deviceNeedsDisable8kWorkaround.
    return SDK_INT < 31
        && format.width >= 7680
        && format.height >= 4320
        && format.sampleMimeType != null
        && format.sampleMimeType.equals(MimeTypes.VIDEO_H265)
        && (Util.MODEL.equals("SM-F711U1") || Util.MODEL.equals("SM-F926U1"));
  }

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

  private static boolean canMux(Format format) {
    String mimeType = checkNotNull(format.sampleMimeType);
    return new DefaultMuxer.Factory()
        .getSupportedSampleMimeTypes(MimeTypes.getTrackType(mimeType))
        .contains(mimeType);
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
