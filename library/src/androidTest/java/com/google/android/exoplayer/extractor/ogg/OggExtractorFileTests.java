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

import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.testutil.TestUtil;
import com.google.android.exoplayer.testutil.TestUtil.ExtractorFactory;

import android.test.InstrumentationTestCase;

/**
 * Unit test for {@link OpusReader}.
 */
public final class OggExtractorFileTests extends InstrumentationTestCase {

  private static final ExtractorFactory OGG_EXTRACTOR_FACTORY = new ExtractorFactory() {
    @Override
    public Extractor create() {
      return new OggExtractor();
    }
  };

  public void testOpus() throws Exception {
    TestUtil.assertOutput(OGG_EXTRACTOR_FACTORY, "ogg/bear.opus", getInstrumentation());
  }

  public void testFlac() throws Exception {
    TestUtil.assertOutput(OGG_EXTRACTOR_FACTORY, "ogg/bear_flac.ogg", getInstrumentation());
  }

  public void testFlacNoSeektable() throws Exception {
    TestUtil.assertOutput(OGG_EXTRACTOR_FACTORY, "ogg/bear_flac_noseektable.ogg",
        getInstrumentation());
  }

  public void testVorbis() throws Exception {
    TestUtil.assertOutput(OGG_EXTRACTOR_FACTORY, "ogg/bear_vorbis.ogg", getInstrumentation());
  }

}
