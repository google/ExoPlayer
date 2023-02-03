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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_URI_STRING;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.ToInt16PcmAudioProcessor;
import com.google.common.collect.ImmutableList;
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
  public void mixMonoToStereo_outputsStereo() throws Exception {
    String testId = "mixMonoToStereo_outputsStereo";

    Effects effects =
        createForAudioProcessors(
            new ChannelMixingAudioProcessor(
                ChannelMixingMatrix.create(
                    /* inputChannelCount= */ 1, /* outputChannelCount= */ 2)));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING)))
            .setRemoveVideo(true)
            .setEffects(effects)
            .build();

    TransformationTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.transformationResult.channelCount).isEqualTo(2);
  }

  @Test
  public void mixingChannels_outputsFloatPcm() throws Exception {
    final String testId = "mixingChannels_outputsFloatPcm";

    Effects effects =
        createForAudioProcessors(
            new ChannelMixingAudioProcessor(
                ChannelMixingMatrix.create(
                    /* inputChannelCount= */ 1, /* outputChannelCount= */ 2)));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING)))
            .setRemoveVideo(true)
            .setEffects(effects)
            .build();

    TransformationTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.transformationResult.pcmEncoding).isEqualTo(C.ENCODING_PCM_FLOAT);
  }

  @Test
  public void mixChannelsThenToInt16Pcm_outputsInt16Pcm() throws Exception {
    final String testId = "mixChannelsThenToInt16Pcm_outputsInt16Pcm";

    Effects effects =
        createForAudioProcessors(
            new ChannelMixingAudioProcessor(
                ChannelMixingMatrix.create(
                    /* inputChannelCount= */ 1, /* outputChannelCount= */ 2)),
            new ToInt16PcmAudioProcessor());
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING)))
            .setRemoveVideo(true)
            .setEffects(effects)
            .build();

    TransformationTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.transformationResult.pcmEncoding).isEqualTo(C.ENCODING_PCM_16BIT);
  }

  private static Effects createForAudioProcessors(AudioProcessor... audioProcessors) {
    return new Effects(ImmutableList.copyOf(audioProcessors), ImmutableList.of());
  }
}
