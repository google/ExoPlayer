/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.DataSourceContractTest;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.common.collect.ImmutableList;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/** {@link DataSource} contract tests for {@link ByteArrayDataSource}. */
@RunWith(AndroidJUnit4.class)
public class ByteArrayDataSourceContractTest extends DataSourceContractTest {

  private static final byte[] DATA = TestUtil.buildTestData(20);

  @Override
  protected ImmutableList<TestResource> getTestResources() {
    return ImmutableList.of(
        new TestResource.Builder()
            .setName("simple")
            .setUri(Uri.EMPTY)
            .setExpectedBytes(DATA)
            .build());
  }

  @Override
  protected Uri getNotFoundUri() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected DataSource createDataSource() {
    return new ByteArrayDataSource(DATA);
  }

  @Override
  @Test
  @Ignore
  public void resourceNotFound() {}

  @Override
  @Test
  @Ignore
  public void resourceNotFound_transferListenerCallbacks() {}

  @Override
  @Test
  @Ignore
  public void getUri_resourceNotFound_returnsNullIfNotOpened() throws Exception {}

  @Override
  @Test
  @Ignore
  public void getResponseHeaders_resourceNotFound_isEmptyWhileNotOpen() throws Exception {}
}
