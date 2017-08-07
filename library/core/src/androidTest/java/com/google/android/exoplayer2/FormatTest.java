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
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.C.WIDEVINE_UUID;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_MP4;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_WEBM;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.MediaFormat;
import android.os.Parcel;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import junit.framework.TestCase;

/**
 * Unit test for {@link Format}.
 */
public final class FormatTest extends TestCase {

  private static final List<byte[]> INIT_DATA;
  static {
    byte[] initData1 = new byte[] {1, 2, 3};
    byte[] initData2 = new byte[] {4, 5, 6};
    List<byte[]> initData = new ArrayList<>();
    initData.add(initData1);
    initData.add(initData2);
    INIT_DATA = Collections.unmodifiableList(initData);
  }

  public void testParcelable() {
    DrmInitData.SchemeData DRM_DATA_1 = new DrmInitData.SchemeData(WIDEVINE_UUID, "cenc", VIDEO_MP4,
        TestUtil.buildTestData(128, 1 /* data seed */));
    DrmInitData.SchemeData DRM_DATA_2 = new DrmInitData.SchemeData(C.UUID_NIL, null, VIDEO_WEBM,
        TestUtil.buildTestData(128, 1 /* data seed */));
    DrmInitData drmInitData = new DrmInitData(DRM_DATA_1, DRM_DATA_2);
    byte[] projectionData = new byte[] {1, 2, 3};
    Metadata metadata = new Metadata(
        new TextInformationFrame("id1", "description1", "value1"),
        new TextInformationFrame("id2", "description2", "value2"));
    ColorInfo colorInfo =  new ColorInfo(C.COLOR_SPACE_BT709,
        C.COLOR_RANGE_LIMITED, C.COLOR_TRANSFER_SDR, new byte[] {1, 2, 3, 4, 5, 6, 7});

    Format formatToParcel = new Format("id", MimeTypes.VIDEO_MP4, MimeTypes.VIDEO_H264, null,
        1024, 2048, 1920, 1080, 24, 90, 2, projectionData, C.STEREO_MODE_TOP_BOTTOM, colorInfo, 6,
        44100, C.ENCODING_PCM_24BIT, 1001, 1002, 0, "und", Format.NO_VALUE,
        Format.OFFSET_SAMPLE_RELATIVE, INIT_DATA, drmInitData, metadata);

    Parcel parcel = Parcel.obtain();
    formatToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    Format formatFromParcel = Format.CREATOR.createFromParcel(parcel);
    assertEquals(formatToParcel, formatFromParcel);

    parcel.recycle();
  }

  public void testConversionToFrameworkMediaFormat() {
    if (Util.SDK_INT < 16) {
      // Test doesn't apply.
      return;
    }

    testConversionToFrameworkMediaFormatV16(Format.createVideoSampleFormat(null, "video/xyz", null,
        5000, 102400, 1280, 720, 30, INIT_DATA, null));
    testConversionToFrameworkMediaFormatV16(Format.createVideoSampleFormat(null, "video/xyz", null,
        5000, Format.NO_VALUE, 1280, 720, 30, null, null));
    testConversionToFrameworkMediaFormatV16(Format.createAudioSampleFormat(null, "audio/xyz", null,
        500, 128, 5, 44100, INIT_DATA, null, 0, null));
    testConversionToFrameworkMediaFormatV16(Format.createAudioSampleFormat(null, "audio/xyz", null,
        500, Format.NO_VALUE, 5, 44100, null, null, 0, null));
    testConversionToFrameworkMediaFormatV16(Format.createTextSampleFormat(null, "text/xyz", 0,
        "eng"));
    testConversionToFrameworkMediaFormatV16(Format.createTextSampleFormat(null, "text/xyz", 0,
        null));
  }

  @SuppressLint("InlinedApi")
  @TargetApi(16)
  private static void testConversionToFrameworkMediaFormatV16(Format in) {
    MediaFormat out = in.getFrameworkMediaFormatV16();
    assertEquals(in.sampleMimeType, out.getString(MediaFormat.KEY_MIME));
    assertOptionalV16(out, MediaFormat.KEY_LANGUAGE, in.language);
    assertOptionalV16(out, MediaFormat.KEY_MAX_INPUT_SIZE, in.maxInputSize);
    assertOptionalV16(out, MediaFormat.KEY_WIDTH, in.width);
    assertOptionalV16(out, MediaFormat.KEY_HEIGHT, in.height);
    assertOptionalV16(out, MediaFormat.KEY_CHANNEL_COUNT, in.channelCount);
    assertOptionalV16(out, MediaFormat.KEY_SAMPLE_RATE, in.sampleRate);
    assertOptionalV16(out, MediaFormat.KEY_FRAME_RATE, in.frameRate);

    for (int i = 0; i < in.initializationData.size(); i++) {
      byte[] originalData = in.initializationData.get(i);
      ByteBuffer frameworkBuffer = out.getByteBuffer("csd-" + i);
      byte[] frameworkData = Arrays.copyOf(frameworkBuffer.array(), frameworkBuffer.limit());
      assertTrue(Arrays.equals(originalData, frameworkData));
    }
  }

  @TargetApi(16)
  private static void assertOptionalV16(MediaFormat format, String key, String value) {
    if (value == null) {
      assertFalse(format.containsKey(key));
    } else {
      assertEquals(value, format.getString(key));
    }
  }

  @TargetApi(16)
  private static void assertOptionalV16(MediaFormat format, String key, int value) {
    if (value == Format.NO_VALUE) {
      assertFalse(format.containsKey(key));
    } else {
      assertEquals(value, format.getInteger(key));
    }
  }

  @TargetApi(16)
  private static void assertOptionalV16(MediaFormat format, String key, float value) {
    if (value == Format.NO_VALUE) {
      assertFalse(format.containsKey(key));
    } else {
      assertEquals(value, format.getFloat(key));
    }
  }

}
