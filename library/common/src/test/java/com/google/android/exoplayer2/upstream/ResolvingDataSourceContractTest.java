/*
 * Copyright 2021 The Android Open Source Project
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
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.DataSourceContractTest;
import com.google.android.exoplayer2.testutil.FakeDataSet;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.ResolvingDataSource.Resolver;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Before;
import org.junit.runner.RunWith;

/** {@link DataSource} contract tests for {@link ResolvingDataSourceContractTest}. */
@RunWith(AndroidJUnit4.class)
public class ResolvingDataSourceContractTest extends DataSourceContractTest {

  private static final String URI = "test://simple.test";
  private static final String RESOLVED_URI = "resolved://simple.resolved";

  private byte[] simpleData;
  private FakeDataSet fakeDataSet;
  private FakeDataSource fakeDataSource;

  @Before
  public void setUp() {
    simpleData = TestUtil.buildTestData(/* length= */ 20);
    fakeDataSet = new FakeDataSet().newData(RESOLVED_URI).appendReadData(simpleData).endData();
  }

  @Override
  protected ImmutableList<TestResource> getTestResources() {
    return ImmutableList.of(
        new TestResource.Builder()
            .setName("simple")
            .setUri(URI)
            .setExpectedBytes(simpleData)
            .build());
  }

  @Override
  protected Uri getNotFoundUri() {
    return Uri.parse("test://not-found.test");
  }

  @Override
  protected DataSource createDataSource() {
    fakeDataSource = new FakeDataSource(fakeDataSet);
    return new ResolvingDataSource(
        fakeDataSource,
        new Resolver() {
          @Override
          public DataSpec resolveDataSpec(DataSpec dataSpec) throws IOException {
            return URI.equals(dataSpec.uri.toString())
                ? dataSpec.buildUpon().setUri(RESOLVED_URI).build()
                : dataSpec;
          }
        });
  }

  @Override
  @Nullable
  protected DataSource getTransferListenerDataSource() {
    return fakeDataSource;
  }
}
