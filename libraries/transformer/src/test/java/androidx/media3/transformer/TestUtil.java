/*
 * Copyright 2023 The Android Open Source Project
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

import android.content.Context;
import android.media.MediaFormat;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.test.utils.FakeClock;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import java.util.List;
import org.robolectric.shadows.MediaCodecInfoBuilder;
import org.robolectric.shadows.ShadowMediaCodec;
import org.robolectric.shadows.ShadowMediaCodecList;

/** Utility class for {@link Transformer} unit tests */
@UnstableApi
public final class TestUtil {

  public static final String ASSET_URI_PREFIX = "asset:///media/";
  public static final String FILE_VIDEO_ONLY = "mp4/sample_18byte_nclx_colr.mp4";
  public static final String FILE_AUDIO_ONLY = "mp3/test-cbr-info-header.mp3";
  public static final String FILE_AUDIO_VIDEO = "mp4/sample.mp4";
  public static final String FILE_AUDIO_VIDEO_STEREO = "mp4/testvid_1022ms.mp4";
  public static final String FILE_AUDIO_RAW_VIDEO = "mp4/sowt-with-video.mov";
  public static final String FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S =
      "mp4/sample_with_increasing_timestamps_320w_240h.mp4";
  public static final String FILE_AUDIO_RAW = "wav/sample.wav";
  public static final String FILE_AUDIO_RAW_STEREO_48000KHZ = "wav/sample_rf64.wav";
  public static final String FILE_WITH_SUBTITLES = "mkv/sample_with_srt.mkv";
  public static final String FILE_WITH_SEF_SLOW_MOTION = "mp4/sample_sef_slow_motion.mp4";
  public static final String FILE_AUDIO_AMR_WB = "amr/sample_wb.amr";
  public static final String FILE_AUDIO_AMR_NB = "amr/sample_nb.amr";
  public static final String FILE_AUDIO_AC3_UNSUPPORTED_BY_MUXER = "mp4/sample_ac3.mp4";
  public static final String FILE_UNKNOWN_DURATION = "mp4/sample_fragmented.mp4";
  public static final String FILE_AUDIO_ELST_SKIP_500MS = "mp4/long_edit_list_audioonly.mp4";
  public static final String FILE_VIDEO_ELST_TRIM_IDR_DURATION =
      "mp4/iibbibb_editlist_videoonly.mp4";

  private static final String DUMP_FILE_OUTPUT_DIRECTORY = "transformerdumps";
  private static final String DUMP_FILE_EXTENSION = "dump";

  private TestUtil() {}

  public static Transformer.Builder createTransformerBuilder(
      Muxer.Factory muxerFactory, boolean enableFallback) {
    Context context = ApplicationProvider.getApplicationContext();
    return new Transformer.Builder(context)
        .setClock(new FakeClock(/* isAutoAdvancing= */ true))
        .setMuxerFactory(muxerFactory)
        .setEncoderFactory(
            new DefaultEncoderFactory.Builder(context).setEnableFallback(enableFallback).build());
  }

  public static Effects createAudioEffects(AudioProcessor... audioProcessors) {
    return new Effects(
        ImmutableList.copyOf(audioProcessors), /* videoEffects= */ ImmutableList.of());
  }

  public static SonicAudioProcessor createPitchChangingAudioProcessor(float pitch) {
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setPitch(pitch);
    return sonicAudioProcessor;
  }

  public static ChannelMixingAudioProcessor createVolumeScalingAudioProcessor(float scale) {
    ChannelMixingAudioProcessor audioProcessor = new ChannelMixingAudioProcessor();
    for (int channel = 1; channel <= 6; channel++) {
      audioProcessor.putChannelMixingMatrix(
          ChannelMixingMatrix.create(
                  /* inputChannelCount= */ channel, /* outputChannelCount= */ channel)
              .scaleBy(scale));
    }
    return audioProcessor;
  }

  public static String getDumpFileName(String originalFileName, String... modifications) {
    String fileName = DUMP_FILE_OUTPUT_DIRECTORY + '/' + originalFileName + '/';
    if (modifications.length == 0) {
      fileName += "original";
    } else {
      fileName += String.join("_", modifications);
    }
    return fileName + '.' + DUMP_FILE_EXTENSION;
  }

  /**
   * Adds an audio decoder for each {@linkplain MimeTypes mime type}.
   *
   * <p>Input buffers are copied directly to the output.
   *
   * <p>When adding codecs, {@link #removeEncodersAndDecoders()} should be called in the test class
   * {@link org.junit.After @After} method.
   */
  public static void addAudioDecoders(String... mimeTypes) {
    for (String mimeType : mimeTypes) {
      addCodec(
          mimeType,
          new ShadowMediaCodec.CodecConfig(
              /* inputBufferSize= */ 100_000,
              /* outputBufferSize= */ 100_000,
              /* codec= */ (in, out) -> out.put(in)),
          /* colorFormats= */ ImmutableList.of(),
          /* isDecoder= */ true);
    }
  }

  /**
   * Adds an audio encoder for each {@linkplain MimeTypes mime type}.
   *
   * <p>Input buffers are copied directly to the output.
   *
   * <p>When adding codecs, {@link #removeEncodersAndDecoders()} should be called in the test class
   * {@link org.junit.After @After} method.
   */
  public static void addAudioEncoders(String... mimeTypes) {
    addAudioEncoders(
        new ShadowMediaCodec.CodecConfig(
            /* inputBufferSize= */ 100_000,
            /* outputBufferSize= */ 100_000,
            /* codec= */ (in, out) -> out.put(in)),
        mimeTypes);
  }

  /**
   * Adds an audio encoder for each {@linkplain MimeTypes mime type}.
   *
   * <p>Input buffers are handled according to the {@link
   * org.robolectric.shadows.ShadowMediaCodec.CodecConfig} provided.
   *
   * <p>When adding codecs, {@link #removeEncodersAndDecoders()} should be called in the test's
   * {@link org.junit.After @After} method.
   */
  public static void addAudioEncoders(
      ShadowMediaCodec.CodecConfig codecConfig, String... mimeTypes) {
    for (String mimeType : mimeTypes) {
      addCodec(
          mimeType, codecConfig, /* colorFormats= */ ImmutableList.of(), /* isDecoder= */ false);
    }
  }

  /** Clears all cached codecs. */
  public static void removeEncodersAndDecoders() {
    ShadowMediaCodec.clearCodecs();
    ShadowMediaCodecList.reset();
    EncoderUtil.clearCachedEncoders();
  }

  private static void addCodec(
      String mimeType,
      ShadowMediaCodec.CodecConfig codecConfig,
      List<Integer> colorFormats,
      boolean isDecoder) {
    String codecName =
        Util.formatInvariant(
            isDecoder ? "exo.%s.decoder" : "exo.%s.encoder", mimeType.replace('/', '-'));
    if (isDecoder) {
      ShadowMediaCodec.addDecoder(codecName, codecConfig);
    } else {
      ShadowMediaCodec.addEncoder(codecName, codecConfig);
    }

    MediaFormat mediaFormat = new MediaFormat();
    mediaFormat.setString(MediaFormat.KEY_MIME, mimeType);
    MediaCodecInfoBuilder.CodecCapabilitiesBuilder codecCapabilities =
        MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
            .setMediaFormat(mediaFormat)
            .setIsEncoder(!isDecoder);

    if (!colorFormats.isEmpty()) {
      codecCapabilities.setColorFormats(Ints.toArray(colorFormats));
    }

    ShadowMediaCodecList.addCodec(
        MediaCodecInfoBuilder.newBuilder()
            .setName(codecName)
            .setIsEncoder(!isDecoder)
            .setCapabilities(codecCapabilities.build())
            .build());
  }
}
