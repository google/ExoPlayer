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

import static java.nio.charset.StandardCharsets.UTF_8;

import android.net.Uri;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.DataSourceContractTest;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.common.collect.ImmutableList;
import java.util.Random;
import org.junit.runner.RunWith;

/** {@link DataSource} contract tests for {@link ByteArrayDataSource}. */
@RunWith(AndroidJUnit4.class)
public class DataSchemeDataSourceContractTest extends DataSourceContractTest {

  private static final String DATA = TestUtil.buildTestString(20, new Random(0));
  private static final String BASE64_ENCODED_DATA =
      Base64.encodeToString(TestUtil.buildTestData(20), Base64.DEFAULT);

  @Override
  protected ImmutableList<TestResource> getTestResources() {
    return ImmutableList.of(
        new TestResource.Builder()
            .setName("plain text")
            .setUri("data:text/plain," + DATA)
            .setExpectedBytes(DATA.getBytes(UTF_8))
            .build(),
        new TestResource.Builder()
            .setName("base64 encoded text")
            .setUri("data:text/plain;base64," + BASE64_ENCODED_DATA)
            .setExpectedBytes(Base64.decode(BASE64_ENCODED_DATA, Base64.DEFAULT))
            .build());
  }

  @Override
  protected Uri getNotFoundUri() {
    return Uri.parse("data:");
  }

  @Override
  protected DataSource createDataSource() {
    return new DataSchemeDataSource();
  }
}
