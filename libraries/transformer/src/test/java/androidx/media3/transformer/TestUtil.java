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
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Looper;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.test.utils.FakeClock;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.robolectric.shadows.MediaCodecInfoBuilder;
import org.robolectric.shadows.ShadowMediaCodec;
import org.robolectric.shadows.ShadowMediaCodecList;

/** Utility class for {@link Transformer} unit tests */
@UnstableApi
public final class TestUtil {

  public static final class TestMuxerFactory implements Muxer.Factory {

    public static final class TestMuxerHolder {
      @Nullable public TestMuxer testMuxer;
    }

    private final TestMuxerHolder testMuxerHolder;
    private final Muxer.Factory defaultMuxerFactory;

    public TestMuxerFactory(TestMuxerHolder testMuxerHolder) {
      this(testMuxerHolder, /* maxDelayBetweenSamplesMs= */ C.TIME_UNSET);
    }

    public TestMuxerFactory(TestMuxerHolder testMuxerHolder, long maxDelayBetweenSamplesMs) {
      this.testMuxerHolder = testMuxerHolder;
      defaultMuxerFactory = new DefaultMuxer.Factory(maxDelayBetweenSamplesMs);
    }

    @Override
    public Muxer create(String path) throws Muxer.MuxerException {
      testMuxerHolder.testMuxer = new TestMuxer(path, defaultMuxerFactory);
      return testMuxerHolder.testMuxer;
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
      return defaultMuxerFactory.getSupportedSampleMimeTypes(trackType);
    }
  }

  public static final class FakeAssetLoader implements AssetLoader {

    public static final class Factory implements AssetLoader.Factory {

      private final @SupportedOutputTypes int supportedOutputTypes;
      @Nullable private final AtomicReference<SampleConsumer> sampleConsumerRef;

      public Factory(
          @SupportedOutputTypes int supportedOutputTypes,
          @Nullable AtomicReference<SampleConsumer> sampleConsumerRef) {
        this.supportedOutputTypes = supportedOutputTypes;
        this.sampleConsumerRef = sampleConsumerRef;
      }

      @Override
      public AssetLoader createAssetLoader(
          EditedMediaItem editedMediaItem, Looper looper, Listener listener) {
        return new FakeAssetLoader(listener, supportedOutputTypes, sampleConsumerRef);
      }
    }

    private final AssetLoader.Listener listener;
    private final @SupportedOutputTypes int supportedOutputTypes;
    @Nullable private final AtomicReference<SampleConsumer> sampleConsumerRef;

    public FakeAssetLoader(
        Listener listener,
        @SupportedOutputTypes int supportedOutputTypes,
        @Nullable AtomicReference<SampleConsumer> sampleConsumerRef) {
      this.listener = listener;
      this.supportedOutputTypes = supportedOutputTypes;
      this.sampleConsumerRef = sampleConsumerRef;
    }

    @Override
    public void start() {
      listener.onDurationUs(10_000_000);
      listener.onTrackCount(1);
      Format format =
          new Format.Builder()
              .setSampleMimeType(MimeTypes.AUDIO_AAC)
              .setSampleRate(44100)
              .setChannelCount(2)
              .build();
      try {
        if (listener.onTrackAdded(format, supportedOutputTypes)) {
          format = format.buildUpon().setPcmEncoding(C.ENCODING_PCM_16BIT).build();
        }

        SampleConsumer sampleConsumer = listener.onOutputFormat(format);
        if (sampleConsumerRef != null) {
          sampleConsumerRef.set(sampleConsumer);
        }
      } catch (ExportException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
      return 0;
    }

    @Override
    public ImmutableMap<Integer, String> getDecoderNames() {
      return ImmutableMap.of();
    }

    @Override
    public void release() {}
  }

  public static final String ASSET_URI_PREFIX = "asset:///media/";
  public static final String FILE_VIDEO_ONLY = "mp4/sample_18byte_nclx_colr.mp4";
  public static final String FILE_AUDIO_ONLY = "mp3/test.mp3";
  public static final String FILE_AUDIO_VIDEO = "mp4/sample.mp4";
  public static final String FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S =
      "mp4/sample_with_increasing_timestamps_320w_240h.mp4";
  public static final String FILE_AUDIO_RAW = "wav/sample.wav";
  public static final String FILE_WITH_SUBTITLES = "mkv/sample_with_srt.mkv";
  public static final String FILE_WITH_SEF_SLOW_MOTION = "mp4/sample_sef_slow_motion.mp4";
  public static final String FILE_AUDIO_UNSUPPORTED_BY_DECODER = "amr/sample_wb.amr";
  public static final String FILE_AUDIO_UNSUPPORTED_BY_ENCODER = "amr/sample_nb.amr";
  public static final String FILE_AUDIO_UNSUPPORTED_BY_MUXER = "mp4/sample_ac3.mp4";
  public static final String FILE_UNKNOWN_DURATION = "mp4/sample_fragmented.mp4";

  private static final String DUMP_FILE_OUTPUT_DIRECTORY = "transformerdumps";
  private static final String DUMP_FILE_EXTENSION = "dump";

  private TestUtil() {}

  public static void createEncodersAndDecoders() {
    ShadowMediaCodec.CodecConfig codecConfig =
        new ShadowMediaCodec.CodecConfig(
            /* inputBufferSize= */ 10_000,
            /* outputBufferSize= */ 10_000,
            /* codec= */ (in, out) -> out.put(in));
    addCodec(
        MimeTypes.AUDIO_AAC,
        codecConfig,
        /* colorFormats= */ ImmutableList.of(),
        /* isDecoder= */ true);
    addCodec(
        MimeTypes.AUDIO_AC3,
        codecConfig,
        /* colorFormats= */ ImmutableList.of(),
        /* isDecoder= */ true);
    addCodec(
        MimeTypes.AUDIO_RAW,
        codecConfig,
        /* colorFormats= */ ImmutableList.of(),
        /* isDecoder= */ true);
    addCodec(
        MimeTypes.AUDIO_AAC,
        codecConfig,
        /* colorFormats= */ ImmutableList.of(),
        /* isDecoder= */ false);

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

    addCodec(
        MimeTypes.AUDIO_AMR_WB,
        throwingCodecConfig,
        /* colorFormats= */ ImmutableList.of(),
        /* isDecoder= */ true);
    addCodec(
        MimeTypes.AUDIO_AMR_NB,
        throwingCodecConfig,
        /* colorFormats= */ ImmutableList.of(),
        /* isDecoder= */ false);
  }

  public static void removeEncodersAndDecoders() {
    ShadowMediaCodec.clearCodecs();
    ShadowMediaCodecList.reset();
    EncoderUtil.clearCachedEncoders();
  }

  public static Transformer.Builder createTransformerBuilder(
      TestMuxerFactory.TestMuxerHolder testMuxerHolder, boolean enableFallback) {
    Context context = ApplicationProvider.getApplicationContext();
    return new Transformer.Builder(context)
        .setClock(new FakeClock(/* isAutoAdvancing= */ true))
        .setMuxerFactory(new TestMuxerFactory(testMuxerHolder))
        .setEncoderFactory(
            new DefaultEncoderFactory.Builder(context).setEnableFallback(enableFallback).build());
  }

  public static String getDumpFileName(String originalFileName) {
    return DUMP_FILE_OUTPUT_DIRECTORY + '/' + originalFileName + '.' + DUMP_FILE_EXTENSION;
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
