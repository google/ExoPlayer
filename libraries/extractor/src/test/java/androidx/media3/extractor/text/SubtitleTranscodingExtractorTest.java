/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.extractor.text;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Non-parameterized tests for {@link SubtitleTranscodingExtractor}.
 *
 * <p>Parameterized tests are in {@link SubtitleTranscodingExtractorParameterizedTest}.
 */
@RunWith(AndroidJUnit4.class)
public class SubtitleTranscodingExtractorTest {

  @Test
  public void underlyingImplementationReturnsDelegate() {
    Extractor matroskaExtractor = new MatroskaExtractor();
    Extractor subtitleTranscodingExtractor =
        new SubtitleTranscodingExtractor(matroskaExtractor, new DefaultSubtitleParserFactory());

    assertThat(subtitleTranscodingExtractor.getUnderlyingImplementation())
        .isSameInstanceAs(matroskaExtractor);
  }
}
