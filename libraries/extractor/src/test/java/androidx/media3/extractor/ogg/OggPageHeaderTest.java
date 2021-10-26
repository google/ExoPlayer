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
package androidx.media3.extractor.ogg;

import static androidx.media3.test.utils.TestUtil.getByteArray;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.test.utils.FakeExtractorInput;
import androidx.media3.test.utils.FakeExtractorInput.SimulatedIOException;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.primitives.Bytes;
import java.io.IOException;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link OggPageHeader}. */
@RunWith(AndroidJUnit4.class)
public final class OggPageHeaderTest {

  private final Random random;

  public OggPageHeaderTest() {
    this.random = new Random(/* seed= */ 0);
  }

  @Test
  public void skipToNextPage_success() throws Exception {
    FakeExtractorInput input =
        createInput(
            Bytes.concat(
                TestUtil.buildTestData(20, random),
                new byte[] {'O', 'g', 'g', 'S'},
                TestUtil.buildTestData(20, random)),
            /* simulateUnknownLength= */ false);
    OggPageHeader oggHeader = new OggPageHeader();

    boolean result = retrySimulatedIOException(() -> oggHeader.skipToNextPage(input));

    assertThat(result).isTrue();
    assertThat(input.getPosition()).isEqualTo(20);
  }

  @Test
  public void skipToNextPage_noPage_returnsFalse() throws Exception {
    FakeExtractorInput input =
        createInput(
            Bytes.concat(TestUtil.buildTestData(20, random)), /* simulateUnknownLength= */ false);
    OggPageHeader oggHeader = new OggPageHeader();

    boolean result = retrySimulatedIOException(() -> oggHeader.skipToNextPage(input));

    assertThat(result).isFalse();
    assertThat(input.getPosition()).isEqualTo(20);
  }

  @Test
  public void skipToNextPage_respectsLimit() throws Exception {
    FakeExtractorInput input =
        createInput(
            Bytes.concat(
                TestUtil.buildTestData(20, random),
                new byte[] {'O', 'g', 'g', 'S'},
                TestUtil.buildTestData(20, random)),
            /* simulateUnknownLength= */ false);
    OggPageHeader oggHeader = new OggPageHeader();

    boolean result = retrySimulatedIOException(() -> oggHeader.skipToNextPage(input, 10));

    assertThat(result).isFalse();
    assertThat(input.getPosition()).isEqualTo(10);
  }

  @Test
  public void populatePageHeader_success() throws Exception {
    byte[] data =
        getByteArray(ApplicationProvider.getApplicationContext(), "media/ogg/page_header");

    FakeExtractorInput input = createInput(data, /* simulateUnknownLength= */ true);
    OggPageHeader header = new OggPageHeader();

    boolean result = retrySimulatedIOException(() -> header.populate(input, /* quiet= */ false));

    assertThat(result).isTrue();
    assertThat(header.type).isEqualTo(0x01);
    assertThat(header.headerSize).isEqualTo(27 + 2);
    assertThat(header.bodySize).isEqualTo(4);
    assertThat(header.pageSegmentCount).isEqualTo(2);
    assertThat(header.granulePosition).isEqualTo(123456);
    assertThat(header.pageSequenceNumber).isEqualTo(4);
    assertThat(header.streamSerialNumber).isEqualTo(0x1000);
    assertThat(header.pageChecksum).isEqualTo(0x100000);
    assertThat(header.revision).isEqualTo(0);
  }

  @Test
  public void populatePageHeader_withLessThan27Bytes_returnFalseWithoutException()
      throws Exception {
    FakeExtractorInput input =
        createInput(TestUtil.createByteArray(2, 2), /* simulateUnknownLength= */ false);
    OggPageHeader header = new OggPageHeader();

    boolean result = retrySimulatedIOException(() -> header.populate(input, /* quiet= */ true));

    assertThat(result).isFalse();
  }

  @Test
  public void populatePageHeader_withNotOgg_returnFalseWithoutException() throws Exception {
    byte[] data =
        getByteArray(ApplicationProvider.getApplicationContext(), "media/ogg/page_header");
    // change from 'O' to 'o'
    data[0] = 'o';
    FakeExtractorInput input = createInput(data, /* simulateUnknownLength= */ false);
    OggPageHeader header = new OggPageHeader();

    boolean result = retrySimulatedIOException(() -> header.populate(input, /* quiet= */ true));

    assertThat(result).isFalse();
  }

  @Test
  public void populatePageHeader_withWrongRevision_returnFalseWithoutException() throws Exception {
    byte[] data =
        getByteArray(ApplicationProvider.getApplicationContext(), "media/ogg/page_header");
    // change revision from 0 to 1
    data[4] = 0x01;
    FakeExtractorInput input = createInput(data, /* simulateUnknownLength= */ false);
    OggPageHeader header = new OggPageHeader();

    boolean result = retrySimulatedIOException(() -> header.populate(input, /* quiet= */ true));

    assertThat(result).isFalse();
  }

  private static FakeExtractorInput createInput(byte[] data, boolean simulateUnknownLength) {
    return new FakeExtractorInput.Builder()
        .setData(data)
        .setSimulateIOErrors(true)
        .setSimulateUnknownLength(simulateUnknownLength)
        .setSimulatePartialReads(true)
        .build();
  }

  private static <T> T retrySimulatedIOException(ThrowingSupplier<T, IOException> supplier)
      throws IOException {
    while (true) {
      try {
        return supplier.get();
      } catch (SimulatedIOException e) {
        // ignored
      }
    }
  }

  private interface ThrowingSupplier<S, E extends Throwable> {
    S get() throws E;
  }
}
