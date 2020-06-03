/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.extractor.amr.AmrExtractor;
import com.google.android.exoplayer2.extractor.flac.FlacExtractor;
import com.google.android.exoplayer2.extractor.flv.FlvExtractor;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.extractor.ogg.OggExtractor;
import com.google.android.exoplayer2.extractor.ts.Ac3Extractor;
import com.google.android.exoplayer2.extractor.ts.Ac4Extractor;
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer2.extractor.ts.PsExtractor;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.extractor.wav.WavExtractor;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DefaultExtractorsFactory}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultExtractorsFactoryTest {

  @Test
  public void createExtractors_withoutUri_optimizesSniffingOrder() {
    DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();

    Extractor[] extractors = defaultExtractorsFactory.createExtractors();

    List<Class<? extends Extractor>> extractorClasses = getExtractorClasses(extractors);
    assertThat(extractorClasses.subList(0, 3))
        .containsExactly(FlvExtractor.class, FlacExtractor.class, WavExtractor.class)
        .inOrder();
    assertThat(extractorClasses.subList(3, 5))
        .containsExactly(Mp4Extractor.class, FragmentedMp4Extractor.class);
    assertThat(extractorClasses.subList(5, extractors.length))
        .containsExactly(
            AmrExtractor.class,
            PsExtractor.class,
            OggExtractor.class,
            TsExtractor.class,
            MatroskaExtractor.class,
            AdtsExtractor.class,
            Ac3Extractor.class,
            Ac4Extractor.class,
            Mp3Extractor.class)
        .inOrder();
  }

  @Test
  public void createExtractors_withUri_startsWithExtractorsMatchingExtension() {
    DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();

    Extractor[] extractors = defaultExtractorsFactory.createExtractors(Uri.parse("test.mp4"));

    List<Class<? extends Extractor>> extractorClasses = getExtractorClasses(extractors);
    assertThat(extractorClasses.subList(0, 2))
        .containsExactly(Mp4Extractor.class, FragmentedMp4Extractor.class);
  }

  @Test
  public void createExtractors_withUri_optimizesSniffingOrder() {
    DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();

    Extractor[] extractors = defaultExtractorsFactory.createExtractors(Uri.parse("test.mp4"));

    List<Class<? extends Extractor>> extractorClasses = getExtractorClasses(extractors);
    assertThat(extractorClasses.subList(2, extractors.length))
        .containsExactly(
            FlvExtractor.class,
            FlacExtractor.class,
            WavExtractor.class,
            AmrExtractor.class,
            PsExtractor.class,
            OggExtractor.class,
            TsExtractor.class,
            MatroskaExtractor.class,
            AdtsExtractor.class,
            Ac3Extractor.class,
            Ac4Extractor.class,
            Mp3Extractor.class)
        .inOrder();
  }

  private static List<Class<? extends Extractor>> getExtractorClasses(Extractor[] extractors) {
    List<Class<? extends Extractor>> extractorClasses = new ArrayList<>();
    for (Extractor extractor : extractors) {
      extractorClasses.add(extractor.getClass());
    }
    return extractorClasses;
  }
}
