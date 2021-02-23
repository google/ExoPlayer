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
package com.google.android.exoplayer2.testutil;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.runner.RunWith;

/** {@link DataSource} contract tests for {@link FakeDataSource}. */
@RunWith(AndroidJUnit4.class)
public class FakeDataSourceContractTest extends DataSourceContractTest {

  private Uri simpleUri;
  private Uri unknownLengthUri;

  private byte[] simpleData;
  private byte[] unknownLengthData;
  private FakeDataSet fakeDataSet;

  @Before
  public void setUp() {
    simpleUri = Uri.parse("test://simple.test");
    unknownLengthUri = Uri.parse("test://unknown-length.test");
    simpleData = TestUtil.buildTestData(/* length= */ 20);
    unknownLengthData = TestUtil.buildTestData(/* length= */ 40);
    fakeDataSet =
        new FakeDataSet()
            .newData(simpleUri)
            .appendReadData(simpleData)
            .endData()
            .newData(unknownLengthUri)
            .setSimulateUnknownLength(true)
            .appendReadData(unknownLengthData)
            .endData();
  }

  @Override
  protected ImmutableList<TestResource> getTestResources() {
    return ImmutableList.of(
        new TestResource.Builder()
            .setName("simple")
            .setUri(simpleUri)
            .setExpectedBytes(simpleData)
            .build(),
        new TestResource.Builder()
            .setName("unknown length")
            .setUri(unknownLengthUri)
            .setExpectedBytes(unknownLengthData)
            .build());
  }

  @Override
  protected Uri getNotFoundUri() {
    return Uri.parse("test://not-found.test");
  }

  @Override
  protected DataSource createDataSource() {
    return new FakeDataSource(fakeDataSet);
  }
}
