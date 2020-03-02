/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata.dvbsi;

import static com.google.android.exoplayer2.testutil.TestUtil.createByteArray;
import static com.google.android.exoplayer2.testutil.TestUtil.createMetadataInputBuffer;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataInputBuffer;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link AppInfoTableDecoder}. */
@RunWith(AndroidJUnit4.class)
public final class AppInfoTableDecoderTest {

  private static final String TYPICAL_FILE = "dvbsi/ait_typical.bin";
  private static final String NO_URL_BASE_FILE = "dvbsi/ait_no_url_base.bin";
  private static final String NO_URL_PATH_FILE = "dvbsi/ait_no_url_path.bin";

  @Test
  public void decode_typical() throws Exception {
    AppInfoTableDecoder decoder = new AppInfoTableDecoder();
    Metadata metadata = decoder.decode(createMetadataInputBuffer(readTestFile(TYPICAL_FILE)));

    assertThat(metadata.length()).isEqualTo(2);
    Metadata.Entry firstEntry = metadata.get(0);
    assertThat(firstEntry).isInstanceOf(AppInfoTable.class);
    assertThat(((AppInfoTable) firstEntry).controlCode)
        .isEqualTo(AppInfoTable.CONTROL_CODE_AUTOSTART);
    assertThat(((AppInfoTable) firstEntry).url).isEqualTo("http://example.com/path/foo");
    Metadata.Entry secondEntry = metadata.get(1);
    assertThat(secondEntry).isInstanceOf(AppInfoTable.class);
    assertThat(((AppInfoTable) secondEntry).controlCode)
        .isEqualTo(AppInfoTable.CONTROL_CODE_PRESENT);
    assertThat(((AppInfoTable) secondEntry).url).isEqualTo("http://google.com/path/bar");
  }

  @Test
  public void decode_noUrlBase() throws Exception {
    AppInfoTableDecoder decoder = new AppInfoTableDecoder();
    Metadata metadata = decoder.decode(createMetadataInputBuffer(readTestFile(NO_URL_BASE_FILE)));

    assertThat(metadata).isNull();
  }

  @Test
  public void decode_noUrlPath() throws Exception {
    AppInfoTableDecoder decoder = new AppInfoTableDecoder();
    Metadata metadata = decoder.decode(createMetadataInputBuffer(readTestFile(NO_URL_PATH_FILE)));

    assertThat(metadata).isNull();
  }

  @Test
  public void decode_failsIfPositionNonZero() {
    AppInfoTableDecoder decoder = new AppInfoTableDecoder();
    MetadataInputBuffer buffer = createMetadataInputBuffer(createByteArray(1, 2, 3));
    buffer.data.position(1);

    assertThrows(IllegalArgumentException.class, () -> decoder.decode(buffer));
  }

  @Test
  public void decode_failsIfBufferHasNoArray() {
    AppInfoTableDecoder decoder = new AppInfoTableDecoder();
    MetadataInputBuffer buffer = createMetadataInputBuffer(createByteArray(1, 2, 3));
    buffer.data = buffer.data.asReadOnlyBuffer();

    assertThrows(IllegalArgumentException.class, () -> decoder.decode(buffer));
  }

  @Test
  public void decode_failsIfArrayOffsetNonZero() {
    AppInfoTableDecoder decoder = new AppInfoTableDecoder();
    MetadataInputBuffer buffer = createMetadataInputBuffer(createByteArray(1, 2, 3));
    buffer.data.position(1);
    buffer.data = buffer.data.slice();

    assertThrows(IllegalArgumentException.class, () -> decoder.decode(buffer));
  }

  private static byte[] readTestFile(String name) throws IOException {
    return TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), name);
  }
}
