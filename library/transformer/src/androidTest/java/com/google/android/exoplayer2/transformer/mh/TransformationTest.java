/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.transformer.mh;

import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_SEF_URI_STRING;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_URI_STRING;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_REMOTE_4K60_PORTRAIT_URI_STRING;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.recordTestSkipped;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.transformer.AndroidTestUtil;
import com.google.android.exoplayer2.transformer.DefaultEncoderFactory;
import com.google.android.exoplayer2.transformer.EncoderSelector;
import com.google.android.exoplayer2.transformer.TransformationRequest;
import com.google.android.exoplayer2.transformer.Transformer;
import com.google.android.exoplayer2.transformer.TransformerAndroidTestRunner;
import com.google.android.exoplayer2.transformer.VideoEncoderSettings;
import com.google.android.exoplayer2.util.Util;
import org.junit.Test;
import org.junit.runner.RunWith;

/** {@link Transformer} instrumentation tests. */
@RunWith(AndroidJUnit4.class)
public class TransformationTest {

  private static final String TAG = "TransformationTest";

  @Test
  public void transform() throws Exception {
    String testId = TAG + "_transform";
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer = new Transformer.Builder(context).build();
    new TransformerAndroidTestRunner.Builder(context, transformer)
        .setCalculateSsim(true)
        .build()
        .run(testId, MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING);
  }

  @Test
  public void transformWithDecodeEncode() throws Exception {
    String testId = TAG + "_transformForceCodecUse";
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(AndroidTestUtil.FORCE_ENCODE_ENCODER_FACTORY)
            .build();
    new TransformerAndroidTestRunner.Builder(context, transformer)
        .setCalculateSsim(true)
        .build()
        .run(testId, MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING);
  }

  @Test
  public void transformToSpecificBitrate() throws Exception {
    String testId = TAG + "_transformWithSpecificBitrate";
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer =
        new Transformer.Builder(context)
            .setRemoveAudio(true)
            .setEncoderFactory(
                new DefaultEncoderFactory(
                    EncoderSelector.DEFAULT,
                    new VideoEncoderSettings.Builder().setBitrate(5_000_000).build(),
                    /* enableFallback= */ true))
            .build();
    new TransformerAndroidTestRunner.Builder(context, transformer)
        .setCalculateSsim(true)
        .build()
        .run(testId, MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING);
  }

  @Test
  public void transform4K60() throws Exception {
    String testId = TAG + "_transform4K60";
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer = new Transformer.Builder(context).build();
    new TransformerAndroidTestRunner.Builder(context, transformer)
        .setCalculateSsim(true)
        .build()
        .run(testId, MP4_REMOTE_4K60_PORTRAIT_URI_STRING);
  }

  @Test
  public void transformNoAudio() throws Exception {
    String testId = TAG + "_transformNoAudio";
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer = new Transformer.Builder(context).setRemoveAudio(true).build();
    new TransformerAndroidTestRunner.Builder(context, transformer)
        .setCalculateSsim(true)
        .build()
        .run(testId, MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING);
  }

  @Test
  public void transformNoVideo() throws Exception {
    String testId = TAG + "_transformNoVideo";
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer = new Transformer.Builder(context).setRemoveVideo(true).build();
    new TransformerAndroidTestRunner.Builder(context, transformer)
        .build()
        .run(testId, MP4_ASSET_URI_STRING);
  }

  @Test
  public void transformSef() throws Exception {
    String testId = TAG + "_transformSef";
    Context context = ApplicationProvider.getApplicationContext();

    if (Util.SDK_INT < 25) {
      // TODO(b/210593256): Remove test skipping after removing the MediaMuxer dependency.
      recordTestSkipped(
          context,
          testId,
          /* reason= */ "Skipping on this API version due to lack of muxing support");
      return;
    }

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder().setFlattenForSlowMotion(true).build())
            .build();
    new TransformerAndroidTestRunner.Builder(context, transformer)
        .build()
        .run(testId, MP4_ASSET_SEF_URI_STRING);
  }
}
