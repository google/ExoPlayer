/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.media3.extractor.mp4;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.extractor.SniffFailure;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.test.utils.FakeExtractorInput;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Non-parameterized tests for {@link FragmentedMp4Extractor}. */
@RunWith(AndroidJUnit4.class)
public final class FragmentedMp4ExtractorNonParameterizedTest {

  @Test
  public void sniff_reportsUnsupportedBrandsFailure() throws Exception {
    FragmentedMp4Extractor extractor =
        new FragmentedMp4Extractor(SubtitleParser.Factory.UNSUPPORTED);
    FakeExtractorInput input = createInputForSample("sample_fragmented_unsupported_brands.mp4");

    boolean sniffResult = extractor.sniff(input);
    ImmutableList<SniffFailure> sniffFailures = extractor.getSniffFailureDetails();

    assertThat(sniffResult).isFalse();
    SniffFailure sniffFailure = Iterables.getOnlyElement(sniffFailures);
    assertThat(sniffFailure).isInstanceOf(UnsupportedBrandsSniffFailure.class);
    UnsupportedBrandsSniffFailure unsupportedBrandsSniffFailure =
        (UnsupportedBrandsSniffFailure) sniffFailure;
    assertThat(unsupportedBrandsSniffFailure.majorBrand).isEqualTo(1767992930);
    assertThat(unsupportedBrandsSniffFailure.compatibleBrands.asList())
        .containsExactly(1919903851, 1835102819, 1953459817, 1801548922)
        .inOrder();
  }

  @Test
  public void sniff_reportsWrongFragmentationFailure() throws Exception {
    FragmentedMp4Extractor extractor =
        new FragmentedMp4Extractor(SubtitleParser.Factory.UNSUPPORTED);
    FakeExtractorInput input = createInputForSample("sample.mp4");

    boolean sniffResult = extractor.sniff(input);
    ImmutableList<SniffFailure> sniffFailures = extractor.getSniffFailureDetails();

    assertThat(sniffResult).isFalse();
    SniffFailure sniffFailure = Iterables.getOnlyElement(sniffFailures);
    assertThat(sniffFailure).isInstanceOf(IncorrectFragmentationSniffFailure.class);
    IncorrectFragmentationSniffFailure incorrectFragmentationSniffFailure =
        (IncorrectFragmentationSniffFailure) sniffFailure;
    assertThat(incorrectFragmentationSniffFailure.fileIsFragmented).isFalse();
  }

  private static FakeExtractorInput createInputForSample(String sample) throws IOException {
    return new FakeExtractorInput.Builder()
        .setData(
            TestUtil.getByteArray(
                ApplicationProvider.getApplicationContext(), "media/mp4/" + sample))
        .build();
  }
}
