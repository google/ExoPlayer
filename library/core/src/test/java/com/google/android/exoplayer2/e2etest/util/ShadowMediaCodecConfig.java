/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.e2etest.util;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.rules.ExternalResource;
import org.robolectric.shadows.MediaCodecInfoBuilder;
import org.robolectric.shadows.ShadowMediaCodec;
import org.robolectric.shadows.ShadowMediaCodecList;

/**
 * A JUnit @Rule to configure Roboelectric's {@link ShadowMediaCodec}.
 *
 * <p>Registers a {@link org.robolectric.shadows.ShadowMediaCodec.CodecConfig} for each audio/video
 * MIME type known by ExoPlayer, and provides access to the bytes passed to these via {@link
 * TeeCodec}.
 */
public final class ShadowMediaCodecConfig extends ExternalResource {

  private final Map<String, TeeCodec> codecsByMimeType;

  private ShadowMediaCodecConfig() {
    this.codecsByMimeType = new HashMap<>();
  }

  public static ShadowMediaCodecConfig forAllSupportedMimeTypes() {
    return new ShadowMediaCodecConfig();
  }

  public ImmutableMap<String, TeeCodec> getCodecs() {
    return ImmutableMap.copyOf(codecsByMimeType);
  }

  @Override
  protected void before() throws Throwable {
    // Video codecs
    MediaCodecInfo.CodecProfileLevel avcProfileLevel =
        createProfileLevel(
            MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
            MediaCodecInfo.CodecProfileLevel.AVCLevel62);
    configureCodec(
        /* codecName= */ "exotest.video.avc",
        MimeTypes.VIDEO_H264,
        ImmutableList.of(avcProfileLevel),
        ImmutableList.of(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible));
    MediaCodecInfo.CodecProfileLevel mpeg2ProfileLevel =
        createProfileLevel(
            MediaCodecInfo.CodecProfileLevel.MPEG2ProfileMain,
            MediaCodecInfo.CodecProfileLevel.MPEG2LevelML);
    configureCodec(
        /* codecName= */ "exotest.video.mpeg2",
        MimeTypes.VIDEO_MPEG2,
        ImmutableList.of(mpeg2ProfileLevel),
        ImmutableList.of(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible));

    // Audio codecs
    configureCodec("exotest.audio.aac", MimeTypes.AUDIO_AAC);
    configureCodec("exotest.audio.mpegl2", MimeTypes.AUDIO_MPEG_L2);
  }

  @Override
  protected void after() {
    codecsByMimeType.clear();
    ShadowMediaCodecList.reset();
    ShadowMediaCodec.clearCodecs();
  }

  private void configureCodec(String codecName, String mimeType) {
    configureCodec(
        codecName,
        mimeType,
        /* profileLevels= */ ImmutableList.of(),
        /* colorFormats= */ ImmutableList.of());
  }

  private void configureCodec(
      String codecName,
      String mimeType,
      List<MediaCodecInfo.CodecProfileLevel> profileLevels,
      List<Integer> colorFormats) {
    MediaFormat mediaFormat = new MediaFormat();
    mediaFormat.setString(MediaFormat.KEY_MIME, mimeType);
    MediaCodecInfoBuilder.CodecCapabilitiesBuilder capabilities =
        MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder().setMediaFormat(mediaFormat);
    if (!profileLevels.isEmpty()) {
      capabilities.setProfileLevels(profileLevels.toArray(new MediaCodecInfo.CodecProfileLevel[0]));
    }
    if (!colorFormats.isEmpty()) {
      capabilities.setColorFormats(Ints.toArray(colorFormats));
    }
    ShadowMediaCodecList.addCodec(
        MediaCodecInfoBuilder.newBuilder()
            .setName(codecName)
            .setCapabilities(capabilities.build())
            .build());
    // TODO: Update ShadowMediaCodec to consider the MediaFormat.KEY_MAX_INPUT_SIZE value passed
    // to configure() so we don't have to specify large buffers here.
    TeeCodec codec = new TeeCodec(mimeType);
    ShadowMediaCodec.addDecoder(
        codecName,
        new ShadowMediaCodec.CodecConfig(
            /* inputBufferSize= */ 50_000, /* outputBufferSize= */ 50_000, codec));
    codecsByMimeType.put(mimeType, codec);
  }

  private static MediaCodecInfo.CodecProfileLevel createProfileLevel(int profile, int level) {
    MediaCodecInfo.CodecProfileLevel profileLevel = new MediaCodecInfo.CodecProfileLevel();
    profileLevel.profile = profile;
    profileLevel.level = level;
    return profileLevel;
  }
}
