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
package androidx.media3.test.utils.robolectric;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaFormat;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import org.junit.rules.ExternalResource;
import org.robolectric.shadows.MediaCodecInfoBuilder;
import org.robolectric.shadows.ShadowMediaCodec;
import org.robolectric.shadows.ShadowMediaCodecList;

/**
 * A JUnit @Rule to configure Roboelectric's {@link ShadowMediaCodec}.
 *
 * <p>Registers a {@link org.robolectric.shadows.ShadowMediaCodec.CodecConfig} for each audio/video
 * MIME type known by ExoPlayer.
 */
@UnstableApi
public final class ShadowMediaCodecConfig extends ExternalResource {
  private static final ImmutableMap<String, CodecImpl> ALL_SUPPORTED_CODECS =
      createAllSupportedCodecs();

  public static ShadowMediaCodecConfig forAllSupportedMimeTypes() {
    return new ShadowMediaCodecConfig(ALL_SUPPORTED_CODECS.keySet());
  }

  public static ShadowMediaCodecConfig withNoDefaultSupportedMimeTypes() {
    return new ShadowMediaCodecConfig(ImmutableSet.of());
  }

  private final Set<String> supportedMimeTypes;

  private ShadowMediaCodecConfig(Set<String> mimeTypes) {
    supportedMimeTypes = new HashSet<>(mimeTypes);
  }

  public void addSupportedMimeTypes(String... mimeTypes) {
    for (String mimeType : mimeTypes) {
      checkState(!supportedMimeTypes.contains(mimeType), "MIME type already added: " + mimeType);
      checkArgument(
          ALL_SUPPORTED_CODECS.containsKey(mimeType), "MIME type not supported: " + mimeType);
    }
    ImmutableSet<String> addedMimeTypes = ImmutableSet.copyOf(mimeTypes);
    supportedMimeTypes.addAll(addedMimeTypes);
    configureCodecs(addedMimeTypes);
  }

  @Override
  protected void before() throws Throwable {
    if (Util.SDK_INT <= 19) {
      // Codec config not supported with Robolectric on API <= 19. Skip rule set up step.
      return;
    }
    configureCodecs(supportedMimeTypes);
  }

  @Override
  protected void after() {
    supportedMimeTypes.clear();
    MediaCodecUtil.clearDecoderInfoCache();
    if (Util.SDK_INT <= 19) {
      // Codec config not supported with Robolectric on API <= 19. Skip rule tear down step.
      return;
    }
    ShadowMediaCodecList.reset();
    ShadowMediaCodec.clearCodecs();
  }

  private void configureCodecs(Set<String> mimeTypes) {
    for (String mimeType : mimeTypes) {
      checkStateNotNull(ALL_SUPPORTED_CODECS.get(mimeType)).configure();
    }
  }

  private static ImmutableMap<String, CodecImpl> createAllSupportedCodecs() {
    ImmutableMap.Builder<String, CodecImpl> codecs = new ImmutableMap.Builder<>();
    // Video codecs
    codecs.put(
        MimeTypes.VIDEO_H264,
        new CodecImpl(
            /* codecName= */ "exotest.video.avc",
            /* mimeType= */ MimeTypes.VIDEO_H264,
            /* profileLevels= */ ImmutableList.of(
                createProfileLevel(
                    MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                    MediaCodecInfo.CodecProfileLevel.AVCLevel62)),
            /* colorFormats= */ ImmutableList.of(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)));
    codecs.put(
        MimeTypes.VIDEO_H265,
        new CodecImpl(
            /* codecName= */ "exotest.video.hevc",
            /* mimeType= */ MimeTypes.VIDEO_H265,
            /* profileLevels= */ ImmutableList.of(
                createProfileLevel(
                    CodecProfileLevel.HEVCProfileMain, CodecProfileLevel.HEVCMainTierLevel61)),
            /* colorFormats= */ ImmutableList.of(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)));
    codecs.put(
        MimeTypes.VIDEO_MPEG2,
        new CodecImpl(
            /* codecName= */ "exotest.video.mpeg2",
            /* mimeType= */ MimeTypes.VIDEO_MPEG2,
            /* profileLevels= */ ImmutableList.of(
                createProfileLevel(
                    MediaCodecInfo.CodecProfileLevel.MPEG2ProfileMain,
                    MediaCodecInfo.CodecProfileLevel.MPEG2LevelML)),
            /* colorFormats= */ ImmutableList.of(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)));
    codecs.put(
        MimeTypes.VIDEO_VP9,
        new CodecImpl(
            /* codecName= */ "exotest.video.vp9",
            /* mimeType= */ MimeTypes.VIDEO_VP9,
            /* profileLevels= */ ImmutableList.of(),
            /* colorFormats= */ ImmutableList.of(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)));

    // Audio codecs
    codecs.put(
        MimeTypes.AUDIO_AAC,
        new CodecImpl(/* codecName= */ "exotest.audio.aac", /* mimeType= */ MimeTypes.AUDIO_AAC));
    codecs.put(
        MimeTypes.AUDIO_AC3,
        new CodecImpl(/* codecName= */ "exotest.audio.ac3", /* mimeType= */ MimeTypes.AUDIO_AC3));
    codecs.put(
        MimeTypes.AUDIO_AC4,
        new CodecImpl(/* codecName= */ "exotest.audio.ac4", /* mimeType= */ MimeTypes.AUDIO_AC4));
    codecs.put(
        MimeTypes.AUDIO_E_AC3,
        new CodecImpl(
            /* codecName= */ "exotest.audio.eac3", /* mimeType= */ MimeTypes.AUDIO_E_AC3));
    codecs.put(
        MimeTypes.AUDIO_E_AC3_JOC,
        new CodecImpl(
            /* codecName= */ "exotest.audio.eac3joc", /* mimeType= */ MimeTypes.AUDIO_E_AC3_JOC));
    codecs.put(
        MimeTypes.AUDIO_FLAC,
        new CodecImpl(/* codecName= */ "exotest.audio.flac", /* mimeType= */ MimeTypes.AUDIO_FLAC));
    codecs.put(
        MimeTypes.AUDIO_MPEG,
        new CodecImpl(/* codecName= */ "exotest.audio.mpeg", /* mimeType= */ MimeTypes.AUDIO_MPEG));
    codecs.put(
        MimeTypes.AUDIO_MPEG_L2,
        new CodecImpl(
            /* codecName= */ "exotest.audio.mpegl2", /* mimeType= */ MimeTypes.AUDIO_MPEG_L2));
    codecs.put(
        MimeTypes.AUDIO_OPUS,
        new CodecImpl(/* codecName= */ "exotest.audio.opus", /* mimeType= */ MimeTypes.AUDIO_OPUS));
    codecs.put(
        MimeTypes.AUDIO_VORBIS,
        new CodecImpl(
            /* codecName= */ "exotest.audio.vorbis", /* mimeType= */ MimeTypes.AUDIO_VORBIS));
    // Raw audio should use a bypass mode and never need this codec. However, to easily assert
    // failures of the bypass mode we want to detect when the raw audio is decoded by this
    codecs.put(
        MimeTypes.AUDIO_RAW,
        new CodecImpl(/* codecName= */ "exotest.audio.raw", /* mimeType= */ MimeTypes.AUDIO_RAW));

    return codecs.buildOrThrow();
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

    private final String codecName;
    private final String mimeType;
    private final ImmutableList<MediaCodecInfo.CodecProfileLevel> profileLevels;
    private final ImmutableList<Integer> colorFormats;
    private final @C.TrackType int trackType;

    public CodecImpl(String codecName, String mimeType) {
      this(
          codecName,
          mimeType,
          /* profileLevels= */ ImmutableList.of(),
          /* colorFormats= */ ImmutableList.of());
    }

    public CodecImpl(
        String codecName,
        String mimeType,
        ImmutableList<CodecProfileLevel> profileLevels,
        ImmutableList<Integer> colorFormats) {
      this.codecName = codecName;
      this.mimeType = mimeType;
      this.profileLevels = profileLevels;
      this.colorFormats = colorFormats;
      trackType = MimeTypes.getTrackType(mimeType);
    }

    public void configure() {
      MediaFormat mediaFormat = new MediaFormat();
      mediaFormat.setString(MediaFormat.KEY_MIME, mimeType);
      MediaCodecInfoBuilder.CodecCapabilitiesBuilder capabilities =
          MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder().setMediaFormat(mediaFormat);
      if (!profileLevels.isEmpty()) {
        capabilities.setProfileLevels(
            profileLevels.toArray(new MediaCodecInfo.CodecProfileLevel[0]));
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
      int bufferSize = mimeType.equals(MimeTypes.VIDEO_H265) ? 250_000 : 100_000;
      ShadowMediaCodec.addDecoder(
          codecName,
          new ShadowMediaCodec.CodecConfig(
              /* inputBufferSize= */ bufferSize, /* outputBufferSize= */ bufferSize, this));
    }

    @Override
    public void process(ByteBuffer in, ByteBuffer out) {
      byte[] bytes = new byte[in.remaining()];
      in.get(bytes);

      // TODO(internal b/174737370): Output audio bytes as well.
      if (trackType != C.TRACK_TYPE_AUDIO) {
        out.put(bytes);
      }
    }
  }
}
