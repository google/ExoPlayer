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
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW_VIDEO;
import static androidx.media3.transformer.TestUtil.addAudioDecoders;
import static androidx.media3.transformer.TestUtil.addAudioEncoders;
import static androidx.media3.transformer.TestUtil.createPitchChangingAudioProcessor;
import static androidx.media3.transformer.TestUtil.createTransformerBuilder;
import static androidx.media3.transformer.TestUtil.removeEncodersAndDecoders;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Parameterized audio end-to-end export test. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class ParameterizedAudioExportTest {
  public static final String AUDIO_44100_MONO = ASSET_URI_PREFIX + FILE_AUDIO_RAW;
  public static final String AUDIO_48000_STEREO = ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO;
  public static final ImmutableSet<ItemConfig> EDITED_MEDIA_ITEMS =
      ImmutableSet.of(
          new ItemConfig(AUDIO_44100_MONO, /* withEffects= */ false),
          new ItemConfig(AUDIO_44100_MONO, /* withEffects= */ true),
          new ItemConfig(AUDIO_48000_STEREO, /* withEffects= */ false),
          new ItemConfig(AUDIO_48000_STEREO, /* withEffects= */ true));

  @Parameters(name = "{0}")
  public static List<SequenceConfig> params() {
    // All permutations of all combinations.
    return Sets.powerSet(EDITED_MEDIA_ITEMS).stream()
        .filter(s -> !s.isEmpty())
        .flatMap(s -> Collections2.permutations(s).stream())
        .map(SequenceConfig::new)
        .collect(toList());
  }

  @Rule public final TemporaryFolder outputDir = new TemporaryFolder();
  @Parameter public SequenceConfig sequence;
  private final CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory();

  @Before
  public void setUp() {
    addAudioDecoders(MimeTypes.AUDIO_RAW);
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

    transformer.start(sequence.asComposition(), outputDir.newFile().getPath());

    ExportResult result = TransformerTestRunner.runLooper(transformer);
    assertThat(result.processedInputs).hasSize(sequence.getSize());
  }

  private static class SequenceConfig {
    private final List<ItemConfig> itemConfigs;

    public SequenceConfig(List<ItemConfig> itemConfigs) {
      this.itemConfigs = itemConfigs;
    }

    public Composition asComposition() {
      ImmutableList.Builder<EditedMediaItem> items = new ImmutableList.Builder<>();
      for (ItemConfig itemConfig : itemConfigs) {
        items.add(itemConfig.asItem());
      }

      return new Composition.Builder(new EditedMediaItemSequence(items.build())).build();
    }

    public int getSize() {
      return itemConfigs.size();
    }

    @Override
    public String toString() {
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append("Seq{");
      for (ItemConfig itemConfig : itemConfigs) {
        stringBuilder.append(itemConfig).append(", ");
      }
      stringBuilder.replace(stringBuilder.length() - 2, stringBuilder.length(), "}");
      return stringBuilder.toString();
    }
  }

  private static class ItemConfig {
    private final String uri;
    private final boolean withEffects;

    public ItemConfig(String uri, boolean withEffects) {
      this.uri = uri;
      this.withEffects = withEffects;
    }

    public EditedMediaItem asItem() {
      EditedMediaItem.Builder editedMediaItem =
          new EditedMediaItem.Builder(MediaItem.fromUri(uri)).setRemoveVideo(true);
      if (withEffects) {
        editedMediaItem.setEffects(
            new Effects(
                ImmutableList.of(createPitchChangingAudioProcessor(0.6f)), ImmutableList.of()));
      }

      return editedMediaItem.build();
    }

    @Override
    public String toString() {
      String itemName = uri;
      if (uri.equals(AUDIO_44100_MONO)) {
        itemName = "mono_44.1kHz";
      } else if (uri.equals(AUDIO_48000_STEREO)) {
        itemName = "stereo_48kHz";
      }

      return itemName + (withEffects ? "_effects" : "");
    }
  }
}
