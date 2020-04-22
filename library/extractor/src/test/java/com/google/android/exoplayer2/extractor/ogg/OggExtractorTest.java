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
package com.google.android.exoplayer2.extractor.ogg;

import static com.google.android.exoplayer2.testutil.TestUtil.getByteArray;

import androidx.test.core.app.ApplicationProvider;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Unit test for {@link OggExtractor}. */
// TODO(ibaker): Split this into OggExtractorSniffTest and OggExtractorTest after parameterization,
// otherwise we'll be running all the sniff tests multiple times.
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class OggExtractorTest {

  @Parameters(name = "{0}")
  public static List<Object[]> params() {
    return ExtractorAsserts.configs();
  }

  @Parameter(0)
  public ExtractorAsserts.Config assertionConfig;

  @Test
  public void opus() throws Exception {
    ExtractorAsserts.assertBehavior(OggExtractor::new, "ogg/bear.opus", assertionConfig);
  }

  @Test
  public void flac() throws Exception {
    ExtractorAsserts.assertBehavior(OggExtractor::new, "ogg/bear_flac.ogg", assertionConfig);
  }

  @Test
  public void flacNoSeektable() throws Exception {
    ExtractorAsserts.assertBehavior(
        OggExtractor::new, "ogg/bear_flac_noseektable.ogg", assertionConfig);
  }

  @Test
  public void vorbis() throws Exception {
    ExtractorAsserts.assertBehavior(OggExtractor::new, "ogg/bear_vorbis.ogg", assertionConfig);
  }

  @Test
  public void sniffVorbis() throws Exception {
    byte[] data = getByteArray(ApplicationProvider.getApplicationContext(), "ogg/vorbis_header");
    assertSniff(data, /* expectedResult= */ true);
  }

  @Test
  public void sniffFlac() throws Exception {
    byte[] data = getByteArray(ApplicationProvider.getApplicationContext(), "ogg/flac_header");
    assertSniff(data, /* expectedResult= */ true);
  }

  @Test
  public void sniffFailsOpusFile() throws Exception {
    byte[] data = getByteArray(ApplicationProvider.getApplicationContext(), "ogg/opus_header");
    assertSniff(data, /* expectedResult= */ false);
  }

  @Test
  public void sniffFailsInvalidOggHeader() throws Exception {
    byte[] data =
        getByteArray(ApplicationProvider.getApplicationContext(), "ogg/invalid_ogg_header");
    assertSniff(data, /* expectedResult= */ false);
  }

  @Test
  public void sniffInvalidHeader() throws Exception {
    byte[] data = getByteArray(ApplicationProvider.getApplicationContext(), "ogg/invalid_header");
    assertSniff(data, /* expectedResult= */ false);
  }

  @Test
  public void sniffFailsEOF() throws Exception {
    byte[] data = getByteArray(ApplicationProvider.getApplicationContext(), "ogg/eof_header");
    assertSniff(data, /* expectedResult= */ false);
  }

  private void assertSniff(byte[] data, boolean expectedResult) throws IOException {
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(data)
            .setSimulateIOErrors(true)
            .setSimulateUnknownLength(true)
            .setSimulatePartialReads(true)
            .build();
    ExtractorAsserts.assertSniff(new OggExtractor(), input, expectedResult);
  }
}
