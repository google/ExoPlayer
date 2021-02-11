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
package com.google.android.exoplayer2.util;

import static com.google.common.truth.Truth.assertThat;

import android.media.MediaFormat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Unit tests for {@link MediaFormatUtil}. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 29) // Allows using MediaFormat.getKeys() to make assertions over the expected keys.
public class MediaFormatUtilTest {

  @Test
  public void createMediaFormatFromEmptyExoPlayerFormat_generatesExpectedEntries() {
    MediaFormat mediaFormat =
        MediaFormatUtil.createMediaFormatFromFormat(new Format.Builder().build());
    // Assert that no invalid keys are accidentally being populated.
    assertThat(mediaFormat.getKeys())
        .containsExactly(
            MediaFormatUtil.KEY_EXO_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT,
            MediaFormat.KEY_ENCODER_DELAY,
            MediaFormat.KEY_ENCODER_PADDING,
            MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH,
            MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT,
            MediaFormat.KEY_IS_DEFAULT,
            MediaFormat.KEY_IS_FORCED_SUBTITLE,
            MediaFormat.KEY_IS_AUTOSELECT,
            MediaFormat.KEY_ROTATION);
    assertThat(mediaFormat.getFloat(MediaFormatUtil.KEY_EXO_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT))
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
  public void createMediaFormatFromPopulatedExoPlayerFormat_generatesExpectedMediaFormatEntries() {
    Format format =
        new Format.Builder()
            .setAverageBitrate(1)
            .setChannelCount(2)
            .setColorInfo(
                new ColorInfo(
                    /* colorSpace= */ C.COLOR_SPACE_BT601,
                    /* colorRange= */ C.COLOR_RANGE_FULL,
                    /* colorTransfer= */ C.COLOR_TRANSFER_HLG,
                    new byte[] {3}))
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
    assertThat(mediaFormat.getInteger(MediaFormatUtil.KEY_EXO_PCM_ENCODING))
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
    assertThat(mediaFormat.getFloat(MediaFormatUtil.KEY_EXO_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT))
        .isEqualTo(format.pixelWidthHeightRatio);
  }

  @Test
  public void createMediaFormatWithExoPlayerPcmEncoding_containsExoPlayerSpecificEncoding() {
    Format format = new Format.Builder().setPcmEncoding(C.ENCODING_PCM_32BIT).build();
    MediaFormat mediaFormat = MediaFormatUtil.createMediaFormatFromFormat(format);
    assertThat(mediaFormat.getInteger(MediaFormatUtil.KEY_EXO_PCM_ENCODING))
        .isEqualTo(C.ENCODING_PCM_32BIT);
    assertThat(mediaFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)).isFalse();
  }
}
