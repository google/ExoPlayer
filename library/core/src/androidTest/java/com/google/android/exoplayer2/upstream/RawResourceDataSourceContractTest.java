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
package com.google.android.exoplayer2.upstream;

import android.content.res.Resources;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.core.test.R;
import com.google.android.exoplayer2.testutil.DataSourceContractTest;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import org.junit.runner.RunWith;

/** {@link DataSource} contract tests for {@link RawResourceDataSource}. */
@RunWith(AndroidJUnit4.class)
public final class RawResourceDataSourceContractTest extends DataSourceContractTest {

  private static final byte[] RESOURCE_1_DATA = Util.getUtf8Bytes("resource1 abc\n");
  private static final byte[] RESOURCE_2_DATA = Util.getUtf8Bytes("resource2 abcdef\n");

  @Override
  protected DataSource createDataSource() {
    return new RawResourceDataSource(ApplicationProvider.getApplicationContext());
  }

  @Override
  protected ImmutableList<TestResource> getTestResources() {
    // Android packages raw resources into a single file. When reading a resource other than the
    // last one, Android does not prevent accidentally reading beyond the end of the resource and
    // into the next one. We use two resources in this test to ensure that when packaged, at least
    // one of them has a subsequent resource. This allows the contract test to enforce that the
    // RawResourceDataSource implementation doesn't erroneously read into the second resource when
    // opened to read the first.
    return ImmutableList.of(
        new TestResource.Builder()
            .setName("resource 1")
            .setUri(RawResourceDataSource.buildRawResourceUri(R.raw.resource1))
            .setExpectedBytes(RESOURCE_1_DATA)
            .build(),
        new TestResource.Builder()
            .setName("resource 2")
            .setUri(RawResourceDataSource.buildRawResourceUri(R.raw.resource2))
            .setExpectedBytes(RESOURCE_2_DATA)
            .build(),
        // Additional resources using different URI schemes.
        new TestResource.Builder()
            .setName("android.resource:// with path")
            .setUri(
                Uri.parse(
                    "android.resource://"
                        + ApplicationProvider.getApplicationContext().getPackageName()
                        + "/raw/resource1"))
            .setExpectedBytes(RESOURCE_1_DATA)
            .build(),
        new TestResource.Builder()
            .setName("android.resource:// with ID")
            .setUri(
                Uri.parse(
                    "android.resource://"
                        + ApplicationProvider.getApplicationContext().getPackageName()
                        + "/"
                        + R.raw.resource1))
            .setExpectedBytes(RESOURCE_1_DATA)
            .build());
  }

  @Override
  protected Uri getNotFoundUri() {
    return RawResourceDataSource.buildRawResourceUri(Resources.ID_NULL);
  }
}
