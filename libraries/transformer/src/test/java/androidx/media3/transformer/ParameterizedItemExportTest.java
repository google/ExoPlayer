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

import static androidx.media3.transformer.TestUtil.ASSET_URI_PREFIX;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_AMR_NB;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW_STEREO_48000KHZ;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW_VIDEO;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_VIDEO;
import static androidx.media3.transformer.TestUtil.FILE_VIDEO_ONLY;
import static androidx.media3.transformer.TestUtil.addAudioDecoders;
import static androidx.media3.transformer.TestUtil.addAudioEncoders;
import static androidx.media3.transformer.TestUtil.createTransformerBuilder;
import static androidx.media3.transformer.TestUtil.getDumpFileName;
import static androidx.media3.transformer.TestUtil.removeEncodersAndDecoders;
import static org.junit.Assume.assumeFalse;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/**
 * Parameterized end-to-end test for exporting a single {@link MediaItem} or {@link EditedMediaItem}
 * and asserting on the dump (golden) files.
 *
 * <ul>
 *   <li>Video can not be transcoded, due to OpenGL not being supported with Robolectric.
 *   <li>Non RAW audio can not be trancoded, because AudioGraph requires decoded data but
 *       Robolectric decoders do not decode.
 *   <li>RAW audio will always be transcoded, because the muxer does not support RAW audio as input.
 * </ul>
 */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class ParameterizedItemExportTest {

  private static final ImmutableList<String> AUDIO_ONLY_ASSETS =
      ImmutableList.of(
          FILE_AUDIO_RAW,
          FILE_AUDIO_RAW_STEREO_48000KHZ,
          "wav/sample_ima_adpcm.wav",
          FILE_AUDIO_AMR_NB);

  private static final ImmutableList<String> AUDIO_VIDEO_ASSETS =
      ImmutableList.of(FILE_AUDIO_RAW_VIDEO, "mp4/sample_twos_pcm.mp4", FILE_AUDIO_VIDEO);

  private static final ImmutableList<String> VIDEO_ONLY_ASSETS = ImmutableList.of(FILE_VIDEO_ONLY);

  @Parameters(name = "{0}")
  public static ImmutableList<String> params() {
    return new ImmutableList.Builder<String>()
        .addAll(AUDIO_ONLY_ASSETS)
        .addAll(VIDEO_ONLY_ASSETS)
        .addAll(AUDIO_VIDEO_ASSETS)
        .build();
  }

  @Rule public final TemporaryFolder outputDir = new TemporaryFolder();

  @Parameter public String assetFile;

  private final CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory();

  @Before
  public void setUp() {
    // Only add RAW decoder, so non-RAW audio has no options for decoding.
    addAudioDecoders(MimeTypes.AUDIO_RAW);
    // Use an AAC encoder because muxer supports AAC.
    addAudioEncoders(MimeTypes.AUDIO_AAC);
  }

  @After
  public void tearDown() {
    removeEncodersAndDecoders();
  }

  @Test
  public void export() throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();

    transformer.start(
        MediaItem.fromUri(ASSET_URI_PREFIX + assetFile), outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        ApplicationProvider.getApplicationContext(),
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(assetFile));
  }

  @Test
  public void generateSilence() throws Exception {
    assumeFalse(AUDIO_ONLY_ASSETS.contains(assetFile));
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();

    EditedMediaItem item =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + assetFile))
            .setRemoveAudio(true)
            .build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence(item))
            .experimentalSetForceAudioTrack(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        ApplicationProvider.getApplicationContext(),
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(assetFile, /* modifications...= */ "silence"));
  }
}
