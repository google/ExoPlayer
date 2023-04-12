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

import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_URI_STRING;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * End-to-end instrumentation test for {@link Transformer} audio test cases that cannot be tested
 * using robolectric.
 */
@RunWith(AndroidJUnit4.class)
public class TransformerAudioEndToEndTest {
  private final Context context = ApplicationProvider.getApplicationContext();

  @Test
  public void defaultTransformer_processesInInt16Pcm() throws Exception {
    String testId = "defaultTransformer_processesInInt16Pcm";
    FormatTrackingAudioBufferSink audioFormatTracker = new FormatTrackingAudioBufferSink();

    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(new AndroidTestUtil.ForceEncodeEncoderFactory(context))
            .build();

    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING)))
            .setEffects(createForAudioProcessors(new TeeAudioProcessor(audioFormatTracker)))
            .setRemoveVideo(true)
            .build();

    new TransformerAndroidTestRunner.Builder(context, transformer)
        .build()
        .run(testId, editedMediaItem);

    ImmutableList<AudioFormat> audioFormats = audioFormatTracker.getFlushedAudioFormats().asList();
    assertThat(audioFormats).hasSize(1);
    assertThat(audioFormats.get(0).encoding).isEqualTo(C.ENCODING_PCM_16BIT);
  }

  @Test
  public void mixMonoToStereo_outputsStereo() throws Exception {
    String testId = "mixMonoToStereo_outputsStereo";

    ChannelMixingAudioProcessor channelMixingAudioProcessor = new ChannelMixingAudioProcessor();
    channelMixingAudioProcessor.putChannelMixingMatrix(
        ChannelMixingMatrix.create(/* inputChannelCount= */ 1, /* outputChannelCount= */ 2));
    Effects effects = createForAudioProcessors(channelMixingAudioProcessor);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING)))
            .setRemoveVideo(true)
            .setEffects(effects)
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.channelCount).isEqualTo(2);
  }

  private static Effects createForAudioProcessors(AudioProcessor... audioProcessors) {
    return new Effects(ImmutableList.copyOf(audioProcessors), ImmutableList.of());
  }

  private static final class FormatTrackingAudioBufferSink
      implements TeeAudioProcessor.AudioBufferSink {
    private final ImmutableSet.Builder<AudioFormat> flushedAudioFormats;

    public FormatTrackingAudioBufferSink() {
      this.flushedAudioFormats = new ImmutableSet.Builder<>();
    }

    @Override
    public void flush(int sampleRateHz, int channelCount, @C.PcmEncoding int encoding) {
      flushedAudioFormats.add(new AudioFormat(sampleRateHz, channelCount, encoding));
    }

    @Override
    public void handleBuffer(ByteBuffer buffer) {
      // Do nothing.
    }

    public ImmutableSet<AudioFormat> getFlushedAudioFormats() {
      return flushedAudioFormats.build();
    }
  }
}
