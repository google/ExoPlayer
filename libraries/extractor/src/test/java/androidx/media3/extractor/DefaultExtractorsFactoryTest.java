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
package androidx.media3.extractor;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.media3.common.MimeTypes;
import androidx.media3.extractor.amr.AmrExtractor;
import androidx.media3.extractor.avi.AviExtractor;
import androidx.media3.extractor.bmp.BmpExtractor;
import androidx.media3.extractor.flac.FlacExtractor;
import androidx.media3.extractor.flv.FlvExtractor;
import androidx.media3.extractor.heif.HeifExtractor;
import androidx.media3.extractor.jpeg.JpegExtractor;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.mp3.Mp3Extractor;
import androidx.media3.extractor.mp4.FragmentedMp4Extractor;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.extractor.ogg.OggExtractor;
import androidx.media3.extractor.png.PngExtractor;
import androidx.media3.extractor.text.SubtitleTranscodingExtractor;
import androidx.media3.extractor.ts.Ac3Extractor;
import androidx.media3.extractor.ts.Ac4Extractor;
import androidx.media3.extractor.ts.AdtsExtractor;
import androidx.media3.extractor.ts.PsExtractor;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.extractor.wav.WavExtractor;
import androidx.media3.extractor.webp.WebpExtractor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DefaultExtractorsFactory}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultExtractorsFactoryTest {

  @Test
  public void createExtractors_withoutMediaInfo_optimizesSniffingOrder() {
    DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();

    Extractor[] extractors = defaultExtractorsFactory.createExtractors();

    List<Class<? extends Extractor>> extractorClasses = getUnderlyingExtractorClasses(extractors);
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
            Mp3Extractor.class,
            AviExtractor.class,
            JpegExtractor.class,
            PngExtractor.class,
            WebpExtractor.class,
            BmpExtractor.class,
            HeifExtractor.class)
        .inOrder();
  }

  @Test
  public void createExtractors_withMediaInfo_startsWithExtractorsMatchingHeadersAndThenUri() {
    DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();
    Uri uri = Uri.parse("test-cbr-info-header.mp3");
    Map<String, List<String>> responseHeaders = new HashMap<>();
    responseHeaders.put("Content-Type", Collections.singletonList(MimeTypes.VIDEO_MP4));

    Extractor[] extractors = defaultExtractorsFactory.createExtractors(uri, responseHeaders);

    List<Class<? extends Extractor>> extractorClasses = getUnderlyingExtractorClasses(extractors);
    assertThat(extractorClasses.subList(0, 2))
        .containsExactly(Mp4Extractor.class, FragmentedMp4Extractor.class);
    assertThat(extractorClasses.get(2)).isEqualTo(Mp3Extractor.class);
  }

  @Test
  public void createExtractors_withMediaInfo_optimizesSniffingOrder() {
    DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();
    Uri uri = Uri.parse("test-cbr-info-header.mp3");
    Map<String, List<String>> responseHeaders = new HashMap<>();
    responseHeaders.put("Content-Type", Collections.singletonList(MimeTypes.VIDEO_MP4));

    Extractor[] extractors = defaultExtractorsFactory.createExtractors(uri, responseHeaders);

    List<Class<? extends Extractor>> extractorClasses = getUnderlyingExtractorClasses(extractors);
    assertThat(extractorClasses.subList(3, extractors.length))
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
            AviExtractor.class,
            JpegExtractor.class,
            PngExtractor.class,
            WebpExtractor.class,
            BmpExtractor.class,
            HeifExtractor.class)
        .inOrder();
  }

  @Test
  public void subtitleTranscoding_notEnabledByDefault() {
    DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();

    Extractor[] extractors = defaultExtractorsFactory.createExtractors();

    for (Extractor extractor : extractors) {
      assertThat(extractor.getClass()).isNotEqualTo(SubtitleTranscodingExtractor.class);
    }
  }

  private static List<Class<? extends Extractor>> getUnderlyingExtractorClasses(
      Extractor[] extractors) {
    List<Class<? extends Extractor>> extractorClasses = new ArrayList<>();
    for (Extractor extractor : extractors) {
      extractorClasses.add(extractor.getUnderlyingImplementation().getClass());
    }
    return extractorClasses;
  }
}
