/*
 * Copyright (C) 2021 The Android Open Source Project
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
package androidx.media3.common.util;

import static com.google.common.truth.Truth.assertThat;

import android.media.MediaFormat;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link MediaFormatUtil}. */
@RunWith(AndroidJUnit4.class)
public class MediaFormatUtilTest {
  @Test
  public void createFormatFromMediaFormat_withEmptyMap_generatesExpectedFormat() {
    Format format = MediaFormatUtil.createFormatFromMediaFormat(new MediaFormat());

    assertThat(format.sampleMimeType).isNull();
    assertThat(format.language).isNull();
    assertThat(format.peakBitrate).isEqualTo(Format.NO_VALUE);
    assertThat(format.averageBitrate).isEqualTo(Format.NO_VALUE);
    assertThat(format.codecs).isNull();
    assertThat(format.frameRate).isEqualTo(Format.NO_VALUE);
    assertThat(format.width).isEqualTo(Format.NO_VALUE);
    assertThat(format.height).isEqualTo(Format.NO_VALUE);
    assertThat(format.pixelWidthHeightRatio).isEqualTo(1.0f);
    assertThat(format.maxInputSize).isEqualTo(Format.NO_VALUE);
    assertThat(format.rotationDegrees).isEqualTo(0);
    assertThat(format.colorInfo).isNull();
    assertThat(format.sampleRate).isEqualTo(Format.NO_VALUE);
    assertThat(format.channelCount).isEqualTo(Format.NO_VALUE);
    assertThat(format.pcmEncoding).isEqualTo(Format.NO_VALUE);
    assertThat(format.initializationData).isEmpty();
  }

  @Test
  public void createFormatFromMediaFormat_withPopulatedMap_generatesExpectedFormat() {
    MediaFormat mediaFormat = new MediaFormat();
    mediaFormat.setString(MediaFormat.KEY_MIME, MimeTypes.VIDEO_H264);
    mediaFormat.setString(MediaFormat.KEY_LANGUAGE, "eng");
    mediaFormat.setInteger(MediaFormatUtil.KEY_MAX_BIT_RATE, 128000);
    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
    mediaFormat.setString(MediaFormat.KEY_CODECS_STRING, "avc.123");
    mediaFormat.setFloat(MediaFormat.KEY_FRAME_RATE, 4);
    mediaFormat.setInteger(MediaFormat.KEY_WIDTH, 10);
    mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, 8);
    mediaFormat.setInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH, 15);
    mediaFormat.setInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT, 5);
    mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 10);
    mediaFormat.setInteger(MediaFormat.KEY_ROTATION, 90);
    mediaFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD, C.COLOR_SPACE_BT601);
    mediaFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER, C.COLOR_TRANSFER_HLG);
    mediaFormat.setInteger(MediaFormat.KEY_COLOR_RANGE, C.COLOR_RANGE_FULL);
    mediaFormat.setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, ByteBuffer.wrap(new byte[] {3}));
    mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 11);
    mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
    mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, C.ENCODING_PCM_8BIT);
    mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(new byte[] {7}));
    mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(new byte[] {10}));

    Format format = MediaFormatUtil.createFormatFromMediaFormat(mediaFormat);

    assertThat(format.sampleMimeType).isEqualTo(mediaFormat.getString(MediaFormat.KEY_MIME));
    // Format stores normalized language code.
    assertThat(format.language)
        .isEqualTo(Util.normalizeLanguageCode(mediaFormat.getString(MediaFormat.KEY_LANGUAGE)));
    assertThat(format.peakBitrate)
        .isEqualTo(mediaFormat.getInteger(MediaFormatUtil.KEY_MAX_BIT_RATE));
    assertThat(format.averageBitrate).isEqualTo(mediaFormat.getInteger(MediaFormat.KEY_BIT_RATE));
    assertThat(format.codecs).isEqualTo(mediaFormat.getString(MediaFormat.KEY_CODECS_STRING));
    assertThat(format.frameRate).isEqualTo(mediaFormat.getFloat(MediaFormat.KEY_FRAME_RATE));
    assertThat(format.width).isEqualTo(mediaFormat.getInteger(MediaFormat.KEY_WIDTH));
    assertThat(format.height).isEqualTo(mediaFormat.getInteger(MediaFormat.KEY_HEIGHT));
    // Ratio of MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH (15) and
    // MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT (5).
    assertThat(format.pixelWidthHeightRatio).isEqualTo(3.0f);
    assertThat(format.maxInputSize)
        .isEqualTo(mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
    assertThat(format.rotationDegrees).isEqualTo(mediaFormat.getInteger(MediaFormat.KEY_ROTATION));
    assertThat(format.colorInfo.colorSpace)
        .isEqualTo(mediaFormat.getInteger(MediaFormat.KEY_COLOR_STANDARD));
    assertThat(format.colorInfo.colorTransfer)
        .isEqualTo(mediaFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER));
    assertThat(format.colorInfo.colorRange)
        .isEqualTo(mediaFormat.getInteger(MediaFormat.KEY_COLOR_RANGE));
    assertThat(format.colorInfo.hdrStaticInfo)
        .isEqualTo(mediaFormat.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO).array());
    assertThat(format.sampleRate).isEqualTo(mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
    assertThat(format.channelCount)
        .isEqualTo(mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
    assertThat(format.pcmEncoding).isEqualTo(mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING));
    assertThat(format.initializationData.get(0))
        .isEqualTo(mediaFormat.getByteBuffer("csd-0").array());
    assertThat(format.initializationData.get(1))
        .isEqualTo(mediaFormat.getByteBuffer("csd-1").array());
  }

  @Test
  public void createMediaFormatFromFormat_withEmptyFormat_generatesExpectedEntries() {
    MediaFormat mediaFormat =
        MediaFormatUtil.createMediaFormatFromFormat(new Format.Builder().build());
    // Assert that no invalid keys are accidentally being populated.
    assertThat(mediaFormat.getKeys())
        .containsExactly(
            MediaFormatUtil.KEY_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT,
            MediaFormat.KEY_ENCODER_DELAY,
            MediaFormat.KEY_ENCODER_PADDING,
            MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH,
            MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT,
            MediaFormat.KEY_IS_DEFAULT,
            MediaFormat.KEY_IS_FORCED_SUBTITLE,
            MediaFormat.KEY_IS_AUTOSELECT,
            MediaFormat.KEY_ROTATION);
    assertThat(mediaFormat.getFloat(MediaFormatUtil.KEY_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT))
        .isEqualTo(1.f);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_ENCODER_DELAY)).isEqualTo(0);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_ENCODER_PADDING)).isEqualTo(0);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH)).isEqualTo(1);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT)).isEqualTo(1);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_IS_DEFAULT)).isEqualTo(0);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_IS_FORCED_SUBTITLE)).isEqualTo(0);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_IS_AUTOSELECT)).isEqualTo(0);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_ROTATION)).isEqualTo(0);
  }

  @Test
  public void createMediaFormatFromFormat_withPopulatedFormat_generatesExpectedEntries() {
    Format format =
        new Format.Builder()
            .setAverageBitrate(1)
            .setChannelCount(2)
            .setColorInfo(
                new ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT601)
                    .setColorRange(C.COLOR_RANGE_FULL)
                    .setColorTransfer(C.COLOR_TRANSFER_HLG)
                    .setHdrStaticInfo(new byte[] {3})
                    .build())
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setCodecs("avc.123")
            .setFrameRate(4)
            .setWidth(5)
            .setHeight(6)
            .setInitializationData(ImmutableList.of(new byte[] {7}, new byte[] {8}))
            .setPcmEncoding(C.ENCODING_PCM_8BIT)
            .setLanguage("en")
            .setMaxInputSize(9)
            .setRotationDegrees(10)
            .setSampleRate(11)
            .setAccessibilityChannel(12)
            .setSelectionFlags(
                C.SELECTION_FLAG_AUTOSELECT | C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED)
            .setEncoderDelay(13)
            .setEncoderPadding(14)
            .setPixelWidthHeightRatio(.5f)
            .build();
    MediaFormat mediaFormat = MediaFormatUtil.createMediaFormatFromFormat(format);

    assertThat(mediaFormat.getInteger(MediaFormat.KEY_BIT_RATE)).isEqualTo(format.bitrate);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
        .isEqualTo(format.channelCount);

    ColorInfo colorInfo = Assertions.checkNotNull(format.colorInfo);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER))
        .isEqualTo(colorInfo.colorTransfer);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_COLOR_RANGE)).isEqualTo(colorInfo.colorRange);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_COLOR_STANDARD))
        .isEqualTo(colorInfo.colorSpace);
    assertThat(mediaFormat.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO).array())
        .isEqualTo(colorInfo.hdrStaticInfo);

    assertThat(mediaFormat.getString(MediaFormat.KEY_MIME)).isEqualTo(format.sampleMimeType);
    assertThat(mediaFormat.getString(MediaFormat.KEY_CODECS_STRING)).isEqualTo(format.codecs);
    assertThat(mediaFormat.getFloat(MediaFormat.KEY_FRAME_RATE)).isEqualTo(format.frameRate);

    assertThat(mediaFormat.getInteger(MediaFormat.KEY_WIDTH)).isEqualTo(format.width);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)).isEqualTo(format.height);

    assertThat(mediaFormat.getByteBuffer("csd-0").array())
        .isEqualTo(format.initializationData.get(0));
    assertThat(mediaFormat.getByteBuffer("csd-1").array())
        .isEqualTo(format.initializationData.get(1));

    assertThat(mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)).isEqualTo(format.pcmEncoding);
    assertThat(mediaFormat.getInteger(MediaFormatUtil.KEY_PCM_ENCODING_EXTENDED))
        .isEqualTo(format.pcmEncoding);

    assertThat(mediaFormat.getString(MediaFormat.KEY_LANGUAGE)).isEqualTo(format.language);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
        .isEqualTo(format.maxInputSize);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_ROTATION)).isEqualTo(format.rotationDegrees);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)).isEqualTo(format.sampleRate);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_CAPTION_SERVICE_NUMBER))
        .isEqualTo(format.accessibilityChannel);

    assertThat(mediaFormat.getInteger(MediaFormat.KEY_IS_AUTOSELECT)).isNotEqualTo(0);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_IS_DEFAULT)).isNotEqualTo(0);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_IS_FORCED_SUBTITLE)).isNotEqualTo(0);

    assertThat(mediaFormat.getInteger(MediaFormat.KEY_ENCODER_DELAY))
        .isEqualTo(format.encoderDelay);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_ENCODER_PADDING))
        .isEqualTo(format.encoderPadding);

    float calculatedPixelAspectRatio =
        (float) mediaFormat.getInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH)
            / mediaFormat.getInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT);
    assertThat(calculatedPixelAspectRatio).isWithin(.0001f).of(format.pixelWidthHeightRatio);
    assertThat(mediaFormat.getFloat(MediaFormatUtil.KEY_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT))
        .isEqualTo(format.pixelWidthHeightRatio);
  }

  @Test
  public void createMediaFormatFromFormat_withCustomPcmEncoding_setsCustomPcmEncodingEntry() {
    Format format = new Format.Builder().setPcmEncoding(C.ENCODING_PCM_16BIT_BIG_ENDIAN).build();

    MediaFormat mediaFormat = MediaFormatUtil.createMediaFormatFromFormat(format);

    assertThat(mediaFormat.getInteger(MediaFormatUtil.KEY_PCM_ENCODING_EXTENDED))
        .isEqualTo(C.ENCODING_PCM_16BIT_BIG_ENDIAN);
    assertThat(mediaFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)).isFalse();
  }
}
