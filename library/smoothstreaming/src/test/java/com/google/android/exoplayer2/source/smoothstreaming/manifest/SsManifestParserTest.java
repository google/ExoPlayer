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
package com.google.android.exoplayer2.source.smoothstreaming.manifest;

import android.net.Uri;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Unit tests for {@link SsManifestParser}. */
@RunWith(RobolectricTestRunner.class)
public final class SsManifestParserTest {

  private static final String SAMPLE_ISMC_1 = "sample_ismc_1";
  private static final String SAMPLE_ISMC_2 = "sample_ismc_2";

  /** Simple test to ensure the sample manifests parse without any exceptions being thrown. */
  @Test
  public void testParseSmoothStreamingManifest() throws IOException {
    SsManifestParser parser = new SsManifestParser();
    parser.parse(
        Uri.parse("https://example.com/test.ismc"),
        TestUtil.getInputStream(RuntimeEnvironment.application, SAMPLE_ISMC_1));
    parser.parse(
        Uri.parse("https://example.com/test.ismc"),
        TestUtil.getInputStream(RuntimeEnvironment.application, SAMPLE_ISMC_2));
  }
}
