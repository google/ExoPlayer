/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.fail;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ContentDataSource}. */
@RunWith(AndroidJUnit4.class)
public final class ContentDataSourceTest {

  private static final String DATA_PATH = "media/mp3/1024_incrementing_bytes.mp3";

  @Test
  public void read() throws Exception {
    assertData(0, C.LENGTH_UNSET, false);
  }

  @Test
  public void readPipeMode() throws Exception {
    assertData(0, C.LENGTH_UNSET, true);
  }

  @Test
  public void readFixedLength() throws Exception {
    assertData(0, 100, false);
  }

  @Test
  public void readFromOffsetToEndOfInput() throws Exception {
    assertData(1, C.LENGTH_UNSET, false);
  }

  @Test
  public void readFromOffsetToEndOfInputPipeMode() throws Exception {
    assertData(1, C.LENGTH_UNSET, true);
  }

  @Test
  public void readFromOffsetFixedLength() throws Exception {
    assertData(1, 100, false);
  }

  @Test
  public void readInvalidUri() throws Exception {
    ContentDataSource dataSource =
        new ContentDataSource(ApplicationProvider.getApplicationContext());
    Uri contentUri = TestContentProvider.buildUri("does/not.exist", false);
    DataSpec dataSpec = new DataSpec(contentUri);
    try {
      dataSource.open(dataSpec);
      fail();
    } catch (ContentDataSource.ContentDataSourceException e) {
      // Expected.
      assertThat(e).hasCauseThat().isInstanceOf(FileNotFoundException.class);
    } finally {
      dataSource.close();
    }
  }

  private static void assertData(int offset, int length, boolean pipeMode) throws IOException {
    Uri contentUri = TestContentProvider.buildUri(DATA_PATH, pipeMode);
    ContentDataSource dataSource =
        new ContentDataSource(ApplicationProvider.getApplicationContext());
    try {
      DataSpec dataSpec = new DataSpec(contentUri, offset, length);
      byte[] completeData =
          TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), DATA_PATH);
      byte[] expectedData = Arrays.copyOfRange(completeData, offset,
          length == C.LENGTH_UNSET ? completeData.length : offset + length);
      TestUtil.assertDataSourceContent(dataSource, dataSpec, expectedData, !pipeMode);
    } finally {
      dataSource.close();
    }
  }
}
