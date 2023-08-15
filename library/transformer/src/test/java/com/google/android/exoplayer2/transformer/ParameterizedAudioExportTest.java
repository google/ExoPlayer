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

import static com.google.android.exoplayer2.transformer.TestUtil.ASSET_URI_PREFIX;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_RAW;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_RAW_VIDEO;
import static com.google.android.exoplayer2.transformer.TestUtil.addAudioDecoders;
import static com.google.android.exoplayer2.transformer.TestUtil.addAudioEncoders;
import static com.google.android.exoplayer2.transformer.TestUtil.createPitchChangingAudioProcessor;
import static com.google.android.exoplayer2.transformer.TestUtil.createTransformerBuilder;
import static com.google.android.exoplayer2.transformer.TestUtil.removeEncodersAndDecoders;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.dataflow.qual.Pure;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Parameterized audio end-to-end export test. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class ParameterizedAudioExportTest {
  public static final String AUDIO_44100_MONO = ASSET_URI_PREFIX + FILE_AUDIO_RAW;
  public static final String AUDIO_48000_STEREO = ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO;
  public static final ImmutableList<ItemConfig> EDITED_MEDIA_ITEMS =
      ImmutableList.of(
          new ItemConfig(AUDIO_44100_MONO, /* withEffects= */ false),
          new ItemConfig(AUDIO_44100_MONO, /* withEffects= */ true),
          new ItemConfig(AUDIO_48000_STEREO, /* withEffects= */ false),
          new ItemConfig(AUDIO_48000_STEREO, /* withEffects= */ true));

  @Parameters(name = "{0}")
  public static List<SequenceConfig> params() {
    ImmutableList<ImmutableList<ItemConfig>> itemsList =
        generateAllPermutationsOfAllCombinations(EDITED_MEDIA_ITEMS);

    ArrayList<SequenceConfig> sequences = new ArrayList<>();
    for (List<ItemConfig> itemConfigs : itemsList) {
      sequences.add(new SequenceConfig(itemConfigs));
    }
    return sequences;
  }

  private final Context context = ApplicationProvider.getApplicationContext();

  @Parameter public SequenceConfig sequence;

  private String outputPath;

  private CapturingMuxer.Factory muxerFactory;

  @Before
  public void setUp() throws Exception {
    outputPath = Util.createTempFile(context, "TransformerTest").getPath();
    muxerFactory = new CapturingMuxer.Factory();
    addAudioDecoders(MimeTypes.AUDIO_RAW);
    addAudioEncoders(MimeTypes.AUDIO_AAC);
  }

  @After
  public void tearDown() throws Exception {
    Files.delete(Paths.get(outputPath));
    removeEncodersAndDecoders();
  }

  @Test
  public void export() throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    transformer.start(sequence.asComposition(), outputPath);
    ExportResult result = TransformerTestRunner.runLooper(transformer);

    assertThat(result.processedInputs).hasSize(sequence.getSize());
  }

  @Pure
  private static <T> ImmutableList<ImmutableList<T>> generateAllPermutationsOfAllCombinations(
      List<T> items) {
    if (items.size() == 1) {
      return ImmutableList.of(ImmutableList.of(items.get(0)));
    }

    ImmutableList.Builder<ImmutableList<T>> permutations = new ImmutableList.Builder<>();
    for (T mainItem : items) {
      ArrayList<T> otherItems = new ArrayList<>(items);
      otherItems.remove(mainItem);
      ImmutableList<ImmutableList<T>> subLists =
          generateAllPermutationsOfAllCombinations(otherItems);
      for (ImmutableList<T> sublist : subLists) {
        permutations.add(sublist);
        permutations.add(new ImmutableList.Builder<T>().add(mainItem).addAll(sublist).build());
      }
    }

    return ImmutableSet.copyOf(permutations.build()).asList();
  }

  private static class SequenceConfig {
    private final List<ItemConfig> itemConfigs;

    public SequenceConfig(List<ItemConfig> itemConfigs) {
      this.itemConfigs = itemConfigs;
    }

    public EditedMediaItemSequence asSequence() {
      ImmutableList.Builder<EditedMediaItem> items = new ImmutableList.Builder<>();
      for (ItemConfig itemConfig : itemConfigs) {
        items.add(itemConfig.asItem());
      }
      return new EditedMediaItemSequence(items.build());
    }

    public Composition asComposition() {
      return new Composition.Builder(ImmutableList.of(asSequence())).build();
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
