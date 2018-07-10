/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.net.Uri;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit test for {@link BaseDataSource}. */
@RunWith(RobolectricTestRunner.class)
public class BaseDataSourceTest {

  @Test
  public void dataTransfer_withLocalSource_isReported() throws IOException {
    TestSource testSource = new TestSource(/* isNetwork= */ false);
    TestTransferListener transferListener = new TestTransferListener();
    testSource.addTransferListener(transferListener);

    DataSpec dataSpec = new DataSpec(Uri.EMPTY);
    testSource.open(dataSpec);
    testSource.read(/* buffer= */ null, /* offset= */ 0, /* readLength= */ 100);
    testSource.close();

    assertThat(transferListener.lastTransferStartSource).isSameAs(testSource);
    assertThat(transferListener.lastBytesTransferredSource).isSameAs(testSource);
    assertThat(transferListener.lastTransferEndSource).isSameAs(testSource);
    assertThat(transferListener.lastTransferStartDataSpec).isEqualTo(dataSpec);
    assertThat(transferListener.lastBytesTransferred).isEqualTo(100);
  }

  @Test
  public void dataTransfer_withRemoteSource_isReported() throws IOException {
    TestSource testSource = new TestSource(/* isNetwork= */ true);
    TestTransferListener transferListener = new TestTransferListener();
    testSource.addTransferListener(transferListener);

    DataSpec dataSpec = new DataSpec(Uri.EMPTY);
    testSource.open(dataSpec);
    testSource.read(/* buffer= */ null, /* offset= */ 0, /* readLength= */ 100);
    testSource.close();

    assertThat(transferListener.lastTransferStartSource).isSameAs(testSource);
    assertThat(transferListener.lastBytesTransferredSource).isSameAs(testSource);
    assertThat(transferListener.lastTransferEndSource).isSameAs(testSource);
    assertThat(transferListener.lastTransferStartDataSpec).isEqualTo(dataSpec);
    assertThat(transferListener.lastBytesTransferred).isEqualTo(100);
  }

  private static final class TestSource extends BaseDataSource {

    public TestSource(boolean isNetwork) {
      super(isNetwork);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
      transferStarted(dataSpec);
      return C.LENGTH_UNSET;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
      bytesTransferred(readLength);
      return readLength;
    }

    @Override
    public @Nullable Uri getUri() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
      transferEnded();
    }
  }

  private static final class TestTransferListener implements TransferListener<DataSource> {

    public Object lastTransferStartSource;
    public DataSpec lastTransferStartDataSpec;
    public Object lastBytesTransferredSource;
    public int lastBytesTransferred;
    public Object lastTransferEndSource;

    @Override
    public void onTransferStart(DataSource source, DataSpec dataSpec) {
      lastTransferStartSource = source;
      lastTransferStartDataSpec = dataSpec;
    }

    @Override
    public void onBytesTransferred(DataSource source, int bytesTransferred) {
      lastBytesTransferredSource = source;
      lastBytesTransferred = bytesTransferred;
    }

    @Override
    public void onTransferEnd(DataSource source) {
      lastTransferEndSource = source;
    }
  }
}
