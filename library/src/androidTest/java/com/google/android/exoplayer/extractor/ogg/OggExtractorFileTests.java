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
package com.google.android.exoplayer.extractor.ogg;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.testutil.FakeExtractorInput;
import com.google.android.exoplayer.testutil.FakeExtractorOutput;
import com.google.android.exoplayer.testutil.FakeTrackOutput;
import com.google.android.exoplayer.testutil.TestUtil;
import com.google.android.exoplayer.util.MimeTypes;

import android.test.InstrumentationTestCase;

/**
 * Unit test for {@link OpusReader}.
 */
public final class OggExtractorFileTests extends InstrumentationTestCase {

  public static final String OPUS_TEST_FILE = "ogg/bear.opus";
  public static final String FLAC_TEST_FILE = "ogg/bear_flac.ogg";
  public static final String FLAC_NS_TEST_FILE = "ogg/bear_flac_noseektable.ogg";
  public static final String VORBIS_TEST_FILE = "ogg/bear_vorbis.ogg";

  public void testOpus() throws Exception {
    parseFile(OPUS_TEST_FILE, false, false, false, MimeTypes.AUDIO_OPUS, 2747500, 275);
    parseFile(OPUS_TEST_FILE, false, true, false, MimeTypes.AUDIO_OPUS, C.UNSET_TIME_US, 275);
    parseFile(OPUS_TEST_FILE, true, false, true, MimeTypes.AUDIO_OPUS, 2747500, 275);
    parseFile(OPUS_TEST_FILE, true, true, true, MimeTypes.AUDIO_OPUS, C.UNSET_TIME_US, 275);
  }

  public void testFlac() throws Exception {
    testFlac(false, false, false);
    testFlac(false, true, false);
    testFlac(true, false, true);
    testFlac(true, true, true);
  }

  private void testFlac(boolean simulateIOErrors, boolean simulateUnknownLength,
      boolean simulatePartialReads) throws Exception {
    FakeTrackOutput trackOutput = parseFile(FLAC_TEST_FILE, simulateIOErrors, simulateUnknownLength,
        simulatePartialReads, MimeTypes.AUDIO_FLAC, 2741000, 33);
    for (int i = 0; i < 33; i++) {
      byte[] sampleData = trackOutput.getSampleData(i);
      assertTrue(FlacReader.isAudioPacket(sampleData));
    }
  }

  public void testFlacNoSeektable() throws Exception {
    parseFile(FLAC_NS_TEST_FILE, false, false, false, MimeTypes.AUDIO_FLAC, 2741000, 33);
    parseFile(FLAC_NS_TEST_FILE, false, true, false, MimeTypes.AUDIO_FLAC, C.UNSET_TIME_US, 33);
    parseFile(FLAC_NS_TEST_FILE, true, false, true, MimeTypes.AUDIO_FLAC, 2741000, 33);
    parseFile(FLAC_NS_TEST_FILE, true, true, true, MimeTypes.AUDIO_FLAC, C.UNSET_TIME_US, 33);
  }

  public void testVorbis() throws Exception {
    parseFile(VORBIS_TEST_FILE, false, false, false, MimeTypes.AUDIO_VORBIS, 2741000, 180);
    parseFile(VORBIS_TEST_FILE, false, true, false, MimeTypes.AUDIO_VORBIS, C.UNSET_TIME_US, 180);
    parseFile(VORBIS_TEST_FILE, true, false, true, MimeTypes.AUDIO_VORBIS, 2741000, 180);
    parseFile(VORBIS_TEST_FILE, true, true, true, MimeTypes.AUDIO_VORBIS, C.UNSET_TIME_US, 180);
  }

  private FakeTrackOutput parseFile(String testFile, boolean simulateIOErrors,
      boolean simulateUnknownLength, boolean simulatePartialReads, String expectedMimeType,
      long expectedDuration, int expectedSampleCount) throws Exception {
    byte[] fileData = TestUtil.getByteArray(getInstrumentation(), testFile);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(fileData)
        .setSimulateIOErrors(simulateIOErrors)
        .setSimulateUnknownLength(simulateUnknownLength)
        .setSimulatePartialReads(simulatePartialReads).build();

    OggExtractor extractor = new OggExtractor();
    assertTrue(TestUtil.sniffTestData(extractor, input));
    input.resetPeekPosition();
    FakeExtractorOutput extractorOutput = TestUtil.consumeTestData(extractor, input, true);

    assertEquals(1, extractorOutput.trackOutputs.size());
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);
    assertNotNull(trackOutput);

    Format format = trackOutput.format;
    assertNotNull(format);
    assertEquals(expectedMimeType, format.sampleMimeType);
    assertEquals(48000, format.sampleRate);
    assertEquals(2, format.channelCount);

    SeekMap seekMap = extractorOutput.seekMap;
    assertNotNull(seekMap);
    assertEquals(expectedDuration, seekMap.getDurationUs());
    assertEquals(expectedDuration != C.UNSET_TIME_US, seekMap.isSeekable());

    trackOutput.assertSampleCount(expectedSampleCount);
    return trackOutput;
  }

}
