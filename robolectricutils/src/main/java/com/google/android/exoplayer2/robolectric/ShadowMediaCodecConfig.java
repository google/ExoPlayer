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
package com.google.android.exoplayer2.robolectric;

import android.media.MediaCodecInfo;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.transformer.EncoderUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.rules.ExternalResource;
import org.robolectric.shadows.MediaCodecInfoBuilder;
import org.robolectric.shadows.ShadowMediaCodec;
import org.robolectric.shadows.ShadowMediaCodecList;

/**
 * A JUnit @Rule to configure {@link ShadowMediaCodec} for transcoding or decoding.
 *
 * <p>Registers {@link org.robolectric.shadows.ShadowMediaCodec.CodecConfig} instances for ExoPlayer
 * and Transformer tests.
 */
public final class ShadowMediaCodecConfig extends ExternalResource {

  private static final String EXOTEST_VIDEO_AVC = "exotest.video.avc";
  private static final String EXOTEST_VIDEO_MPEG2 = "exotest.video.mpeg2";
  private static final String EXOTEST_VIDEO_VP9 = "exotest.video.vp9";
  private static final String EXOTEST_AUDIO_AAC = "exotest.audio.aac";
  private static final String EXOTEST_AUDIO_AC3 = "exotest.audio.ac3";
  private static final String EXOTEST_AUDIO_AC4 = "exotest.audio.ac4";
  private static final String EXOTEST_AUDIO_E_AC3 = "exotest.audio.eac3";
  private static final String EXOTEST_AUDIO_E_AC3_JOC = "exotest.audio.eac3joc";
  private static final String EXOTEST_AUDIO_FLAC = "exotest.audio.flac";
  private static final String EXOTEST_AUDIO_MPEG = "exotest.audio.mpeg";
  private static final String EXOTEST_AUDIO_MPEG_L2 = "exotest.audio.mpegl2";
  private static final String EXOTEST_AUDIO_OPUS = "exotest.audio.opus";
  private static final String EXOTEST_AUDIO_VORBIS = "exotest.audio.vorbis";
  private static final String EXOTEST_AUDIO_RAW = "exotest.audio.raw";

  private final boolean forTranscoding;

  private ShadowMediaCodecConfig(boolean forTranscoding) {
    this.forTranscoding = forTranscoding;
  }

  /** Creates an instance that configures {@link ShadowMediaCodec} for Transformer transcoding. */
  public static ShadowMediaCodecConfig forTranscoding() {
    return new ShadowMediaCodecConfig(/* forTranscoding= */ true);
  }

  /** Creates an instance that configures {@link ShadowMediaCodec} for Exoplayer decoding. */
  public static ShadowMediaCodecConfig forAllSupportedMimeTypes() {
    return new ShadowMediaCodecConfig(/* forTranscoding= */ false);
  }

  @Override
  protected void before() throws Throwable {
    if (forTranscoding) {
      addTranscodingCodecs();
    } else {
      addDecodingCodecs();
    }
  }

  private void addTranscodingCodecs() {
    ShadowMediaCodec.CodecConfig codecConfig =
        new ShadowMediaCodec.CodecConfig(
            /* inputBufferSize= */ 10_000,
            /* outputBufferSize= */ 10_000,
            /* codec= */ (in, out) -> out.put(in));
    addTransformerCodec(MimeTypes.AUDIO_AAC, codecConfig, /* isDecoder= */ true);
    addTransformerCodec(MimeTypes.AUDIO_AC3, codecConfig, /* isDecoder= */ true);
    addTransformerCodec(MimeTypes.AUDIO_AMR_NB, codecConfig, /* isDecoder= */ true);
    addTransformerCodec(MimeTypes.AUDIO_AAC, codecConfig, /* isDecoder= */ false);

    ShadowMediaCodec.CodecConfig throwingCodecConfig =
        new ShadowMediaCodec.CodecConfig(
            /* inputBufferSize= */ 10_000,
            /* outputBufferSize= */ 10_000,
            new ShadowMediaCodec.CodecConfig.Codec() {

              @Override
              public void process(ByteBuffer in, ByteBuffer out) {
                out.put(in);
              }

              @Override
              public void onConfigured(
                  MediaFormat format,
                  @Nullable Surface surface,
                  @Nullable MediaCrypto crypto,
                  int flags) {
                throw new IllegalArgumentException("Format unsupported");
              }
            });
    addTransformerCodec(MimeTypes.AUDIO_AMR_WB, throwingCodecConfig, /* isDecoder= */ true);
    addTransformerCodec(MimeTypes.AUDIO_AMR_NB, throwingCodecConfig, /* isDecoder= */ false);
  }

  private void addDecodingCodecs() {
    // Video codecs
    MediaCodecInfo.CodecProfileLevel avcProfileLevel =
        createProfileLevel(
            MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
            MediaCodecInfo.CodecProfileLevel.AVCLevel62);
    addExoplayerCodec(
        EXOTEST_VIDEO_AVC,
        MimeTypes.VIDEO_H264,
        generateDecodingCodecConfig(MimeTypes.VIDEO_H264),
        ImmutableList.of(avcProfileLevel),
        ImmutableList.of(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible));

    MediaCodecInfo.CodecProfileLevel mpeg2ProfileLevel =
        createProfileLevel(
            MediaCodecInfo.CodecProfileLevel.MPEG2ProfileMain,
            MediaCodecInfo.CodecProfileLevel.MPEG2LevelML);
    addExoplayerCodec(
        EXOTEST_VIDEO_MPEG2,
        MimeTypes.VIDEO_MPEG2,
        generateDecodingCodecConfig(MimeTypes.VIDEO_MPEG2),
        ImmutableList.of(mpeg2ProfileLevel),
        ImmutableList.of(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible));
    addExoplayerCodec(
        EXOTEST_VIDEO_VP9,
        MimeTypes.VIDEO_VP9,
        generateDecodingCodecConfig(MimeTypes.VIDEO_VP9),
        ImmutableList.of(),
        ImmutableList.of(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible));

    // Audio codecs
    addExoplayerCodec(
        EXOTEST_AUDIO_AAC, MimeTypes.AUDIO_AAC, generateDecodingCodecConfig(MimeTypes.AUDIO_AAC));
    addExoplayerCodec(
        EXOTEST_AUDIO_AC3, MimeTypes.AUDIO_AC3, generateDecodingCodecConfig(MimeTypes.AUDIO_AC3));
    addExoplayerCodec(
        EXOTEST_AUDIO_AC4, MimeTypes.AUDIO_AC4, generateDecodingCodecConfig(MimeTypes.AUDIO_AC4));
    addExoplayerCodec(
        EXOTEST_AUDIO_E_AC3,
        MimeTypes.AUDIO_E_AC3,
        generateDecodingCodecConfig(MimeTypes.AUDIO_E_AC3));
    addExoplayerCodec(
        EXOTEST_AUDIO_E_AC3_JOC,
        MimeTypes.AUDIO_E_AC3_JOC,
        generateDecodingCodecConfig(MimeTypes.AUDIO_E_AC3_JOC));
    addExoplayerCodec(
        EXOTEST_AUDIO_FLAC,
        MimeTypes.AUDIO_FLAC,
        generateDecodingCodecConfig(MimeTypes.AUDIO_FLAC));
    addExoplayerCodec(
        EXOTEST_AUDIO_MPEG,
        MimeTypes.AUDIO_MPEG,
        generateDecodingCodecConfig(MimeTypes.AUDIO_MPEG));
    addExoplayerCodec(
        EXOTEST_AUDIO_MPEG_L2,
        MimeTypes.AUDIO_MPEG_L2,
        generateDecodingCodecConfig(MimeTypes.AUDIO_MPEG_L2));
    addExoplayerCodec(
        EXOTEST_AUDIO_OPUS,
        MimeTypes.AUDIO_OPUS,
        generateDecodingCodecConfig(MimeTypes.AUDIO_OPUS));
    addExoplayerCodec(
        EXOTEST_AUDIO_VORBIS,
        MimeTypes.AUDIO_VORBIS,
        generateDecodingCodecConfig(MimeTypes.AUDIO_VORBIS));

    // Raw audio should use a bypass mode and never need this codec. However, to easily assert
    // failures of the bypass mode we want to detect when the raw audio is decoded by this class and
    // thus we need a codec to output samples.
    addExoplayerCodec(
        EXOTEST_AUDIO_RAW, MimeTypes.AUDIO_RAW, generateDecodingCodecConfig(MimeTypes.AUDIO_RAW));
  }

  @Override
  protected void after() {
    if (!forTranscoding) {
      MediaCodecUtil.clearDecoderInfoCache();
    } else {
      EncoderUtil.clearCachedEncoders();
    }
    ShadowMediaCodecList.reset();
    ShadowMediaCodec.clearCodecs();
  }

  private ShadowMediaCodec.CodecConfig generateDecodingCodecConfig(String mimeType) {
    // TODO: Update ShadowMediaCodec to consider the MediaFormat.KEY_MAX_INPUT_SIZE value passed
    //  to configure() so we don't have to specify large buffers here.
    CodecImpl codec = new CodecImpl(mimeType);
    return new ShadowMediaCodec.CodecConfig(
        /* inputBufferSize= */ 100_000, /* outputBufferSize= */ 100_000, codec);
  }

  private void addTransformerCodec(
      String mimeType, ShadowMediaCodec.CodecConfig codecConfig, boolean isDecoder) {
    String codecName =
        Util.formatInvariant(
            isDecoder ? "transformertest.%s.decoder" : "transformertest.%s.encoder",
            mimeType.replace('/', '.'));
    addCodec(
        codecName,
        mimeType,
        codecConfig,
        /* profileLevels= */ ImmutableList.of(),
        /* colorFormats= */ ImmutableList.of(),
        isDecoder);
  }

  private void addExoplayerCodec(
      String codecName, String mimeType, ShadowMediaCodec.CodecConfig codecConfig) {
    addExoplayerCodec(
        codecName,
        mimeType,
        codecConfig,
        /* profileLevels= */ ImmutableList.of(),
        /* colorFormats= */ ImmutableList.of());
  }

  private void addExoplayerCodec(
      String codecName,
      String mimeType,
      ShadowMediaCodec.CodecConfig codecConfig,
      List<MediaCodecInfo.CodecProfileLevel> profileLevels,
      List<Integer> colorFormats) {
    addCodec(codecName, mimeType, codecConfig, profileLevels, colorFormats, /* isDecoder= */ true);
  }

  private void addCodec(
      String codecName,
      String mimeType,
      ShadowMediaCodec.CodecConfig codecConfig,
      List<MediaCodecInfo.CodecProfileLevel> profileLevels,
      List<Integer> colorFormats,
      boolean isDecoder) {
    MediaFormat mediaFormat = new MediaFormat();
    mediaFormat.setString(MediaFormat.KEY_MIME, mimeType);
    MediaCodecInfoBuilder.CodecCapabilitiesBuilder capabilities =
        MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
            .setMediaFormat(mediaFormat)
            .setIsEncoder(!isDecoder);
    if (!profileLevels.isEmpty()) {
      capabilities.setProfileLevels(profileLevels.toArray(new MediaCodecInfo.CodecProfileLevel[0]));
    }
    if (!colorFormats.isEmpty()) {
      capabilities.setColorFormats(Ints.toArray(colorFormats));
    }

    ShadowMediaCodecList.addCodec(
        MediaCodecInfoBuilder.newBuilder()
            .setName(codecName)
            .setIsEncoder(!isDecoder)
            .setCapabilities(capabilities.build())
            .build());

    if (isDecoder) {
      ShadowMediaCodec.addDecoder(codecName, codecConfig);
    } else {
      ShadowMediaCodec.addEncoder(codecName, codecConfig);
    }
  }

  private static MediaCodecInfo.CodecProfileLevel createProfileLevel(int profile, int level) {
    MediaCodecInfo.CodecProfileLevel profileLevel = new MediaCodecInfo.CodecProfileLevel();
    profileLevel.profile = profile;
    profileLevel.level = level;
    return profileLevel;
  }

  /**
   * A {@link ShadowMediaCodec.CodecConfig.Codec} that passes data through without modifying it.
   *
   * <p>Note: This currently drops all audio data - removing this restriction is tracked in
   * [internal b/174737370].
   */
  private static final class CodecImpl implements ShadowMediaCodec.CodecConfig.Codec {

    private final String mimeType;

    public CodecImpl(String mimeType) {
      this.mimeType = mimeType;
    }

    @Override
    public void process(ByteBuffer in, ByteBuffer out) {
      byte[] bytes = new byte[in.remaining()];
      in.get(bytes);

      // TODO(internal b/174737370): Output audio bytes as well.
      if (!MimeTypes.isAudio(mimeType)) {
        out.put(bytes);
      }
    }
  }
}
