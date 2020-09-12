/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link ExtractorUtil}. */
@RunWith(AndroidJUnit4.class)
public class ExtractorUtilTest {

  private static final String TEST_URI = "http://www.google.com";
  private static final byte[] TEST_DATA = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

  @Test
  public void peekToLengthEndNotReached() throws Exception {
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource
        .getDataSet()
        .newDefaultData()
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 0, 3))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 3, 6))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 6, 9));
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    ExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNSET);
    byte[] target = new byte[TEST_DATA.length];
    int offset = 2;
    int length = 4;

    int bytesPeeked = ExtractorUtil.peekToLength(input, target, offset, length);

    assertThat(bytesPeeked).isEqualTo(length);
    assertThat(input.getPeekPosition()).isEqualTo(length);
    assertThat(Arrays.copyOfRange(target, offset, offset + length))
        .isEqualTo(Arrays.copyOf(TEST_DATA, length));
  }

  @Test
  public void peekToLengthEndReached() throws Exception {
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource
        .getDataSet()
        .newDefaultData()
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 0, 3))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 3, 6))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 6, 9));
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    ExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNSET);
    byte[] target = new byte[TEST_DATA.length];
    int offset = 0;
    int length = TEST_DATA.length + 1;

    int bytesPeeked = ExtractorUtil.peekToLength(input, target, offset, length);

    assertThat(bytesPeeked).isEqualTo(TEST_DATA.length);
    assertThat(input.getPeekPosition()).isEqualTo(TEST_DATA.length);
    assertThat(target).isEqualTo(TEST_DATA);
  }
}
