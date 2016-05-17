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
import com.google.android.exoplayer.extractor.DefaultExtractorInput;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.testutil.FakeExtractorOutput;
import com.google.android.exoplayer.testutil.FakeTrackOutput;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.DefaultDataSource;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import android.content.Context;
import android.net.Uri;
import android.test.InstrumentationTestCase;

import java.io.IOException;

/**
 * Unit test for {@link OpusReader}.
 */
public final class OpusReaderTest extends InstrumentationTestCase {

  private static final String TEST_FILE = "asset:///ogg/bear.opus";

  private OggExtractor extractor;
  private FakeExtractorOutput extractorOutput;
  private DefaultExtractorInput extractorInput;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    Context context = getInstrumentation().getContext();
    DataSource dataSource = new DefaultDataSource(context, null, Util
        .getUserAgent(context, "ExoPlayerExtFlacTest"), false);
    Uri uri = Uri.parse(TEST_FILE);
    long length = dataSource.open(new DataSpec(uri, 0, C.LENGTH_UNBOUNDED, null));
    extractorInput = new DefaultExtractorInput(dataSource, 0, length);

    extractor = new OggExtractor();
    assertTrue(extractor.sniff(extractorInput));
    extractorInput.resetPeekPosition();

    extractorOutput = new FakeExtractorOutput();
    extractor.init(extractorOutput);
  }

  public void testSniffOpus() throws Exception {
    // Do nothing. All assertions are in setUp()
  }

  public void testParseHeader() throws Exception {
    FakeTrackOutput trackOutput = parseFile(false);

    trackOutput.assertSampleCount(0);

    Format format = trackOutput.format;
    assertNotNull(format);
    assertEquals(MimeTypes.AUDIO_OPUS, format.sampleMimeType);
    assertEquals(48000, format.sampleRate);
    assertEquals(2, format.channelCount);
  }

  public void testParseWholeFile() throws Exception {
    FakeTrackOutput trackOutput = parseFile(true);

    trackOutput.assertSampleCount(275);
  }

  private FakeTrackOutput parseFile(boolean parseAll) throws IOException, InterruptedException {
    PositionHolder seekPositionHolder = new PositionHolder();
    int readResult = Extractor.RESULT_CONTINUE;
    do {
      readResult = extractor.read(extractorInput, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        fail("There should be no seek");
      }
    } while (readResult != Extractor.RESULT_END_OF_INPUT && parseAll);

    assertEquals(1, extractorOutput.trackOutputs.size());
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);
    assertNotNull(trackOutput);
    return trackOutput;
  }
}
