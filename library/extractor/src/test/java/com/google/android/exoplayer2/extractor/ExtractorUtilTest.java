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
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.io.EOFException;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link ExtractorUtil}. */
@RunWith(AndroidJUnit4.class)
public class ExtractorUtilTest {

  private static final String TEST_URI = "http://www.google.com";
  private static final byte[] TEST_DATA = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

  @Test
  public void peekToLength_endNotReached() throws Exception {
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource
        .getDataSet()
        .newDefaultData()
        .appendReadData(Arrays.copyOf(TEST_DATA, 3))
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
  public void peekToLength_endReached() throws Exception {
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource
        .getDataSet()
        .newDefaultData()
        .appendReadData(Arrays.copyOf(TEST_DATA, 3))
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

  @Test
  public void readFullyQuietly_endNotReached_isTrueAndReadsData() throws Exception {
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource
        .getDataSet()
        .newDefaultData()
        .appendReadData(Arrays.copyOf(TEST_DATA, 3))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 3, 6))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 6, 9));
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    ExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNSET);
    byte[] target = new byte[TEST_DATA.length];
    int offset = 2;
    int length = 4;

    boolean hasRead = ExtractorUtil.readFullyQuietly(input, target, offset, length);

    assertThat(hasRead).isTrue();
    assertThat(input.getPosition()).isEqualTo(length);
    assertThat(Arrays.copyOfRange(target, offset, offset + length))
        .isEqualTo(Arrays.copyOf(TEST_DATA, length));
  }

  @Test
  public void readFullyQuietly_endReached_isFalse() throws Exception {
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource.getDataSet().newDefaultData().appendReadData(Arrays.copyOf(TEST_DATA, 3));
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    ExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNSET);
    byte[] target = new byte[TEST_DATA.length];
    int offset = 0;
    int length = TEST_DATA.length + 1;

    boolean hasRead = ExtractorUtil.readFullyQuietly(input, target, offset, length);

    assertThat(hasRead).isFalse();
    assertThat(input.getPosition()).isEqualTo(0);
  }

  @Test
  public void skipFullyQuietly_endNotReached_isTrueAndSkipsData() throws Exception {
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource
        .getDataSet()
        .newDefaultData()
        .appendReadData(Arrays.copyOf(TEST_DATA, 3))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 3, 6))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 6, 9));
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    ExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNSET);
    int length = 4;

    boolean hasRead = ExtractorUtil.skipFullyQuietly(input, length);

    assertThat(hasRead).isTrue();
    assertThat(input.getPosition()).isEqualTo(length);
  }

  @Test
  public void skipFullyQuietly_endReached_isFalse() throws Exception {
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource.getDataSet().newDefaultData().appendReadData(Arrays.copyOf(TEST_DATA, 3));
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    ExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNSET);
    int length = TEST_DATA.length + 1;

    boolean hasRead = ExtractorUtil.skipFullyQuietly(input, length);

    assertThat(hasRead).isFalse();
    assertThat(input.getPosition()).isEqualTo(0);
  }

  @Test
  public void peekFullyQuietly_endNotReached_isTrueAndPeeksData() throws Exception {
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource
        .getDataSet()
        .newDefaultData()
        .appendReadData(Arrays.copyOf(TEST_DATA, 3))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 3, 6))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 6, 9));
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    ExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNSET);
    byte[] target = new byte[TEST_DATA.length];
    int offset = 2;
    int length = 4;

    boolean hasRead =
        ExtractorUtil.peekFullyQuietly(input, target, offset, length, /* allowEndOfInput= */ false);

    assertThat(hasRead).isTrue();
    assertThat(input.getPeekPosition()).isEqualTo(length);
    assertThat(Arrays.copyOfRange(target, offset, offset + length))
        .isEqualTo(Arrays.copyOf(TEST_DATA, length));
  }

  @Test
  public void peekFullyQuietly_endReachedWithEndOfInputAllowed_isFalse() throws Exception {
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource.getDataSet().newDefaultData().appendReadData(Arrays.copyOf(TEST_DATA, 3));
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    ExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNSET);
    byte[] target = new byte[TEST_DATA.length];
    int offset = 0;
    int length = TEST_DATA.length + 1;

    boolean hasRead =
        ExtractorUtil.peekFullyQuietly(input, target, offset, length, /* allowEndOfInput= */ true);

    assertThat(hasRead).isFalse();
    assertThat(input.getPeekPosition()).isEqualTo(0);
  }

  @Test
  public void peekFullyQuietly_endReachedWithoutEndOfInputAllowed_throws() throws Exception {
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource.getDataSet().newDefaultData().appendReadData(Arrays.copyOf(TEST_DATA, 3));
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    ExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNSET);
    byte[] target = new byte[TEST_DATA.length];
    int offset = 0;
    int length = TEST_DATA.length + 1;

    assertThrows(
        EOFException.class,
        () ->
            ExtractorUtil.peekFullyQuietly(
                input, target, offset, length, /* allowEndOfInput= */ false));
    assertThat(input.getPeekPosition()).isEqualTo(0);
  }
}
