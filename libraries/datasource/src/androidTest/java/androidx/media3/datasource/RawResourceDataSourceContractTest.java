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
package androidx.media3.datasource;

import android.content.res.Resources;
import android.net.Uri;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.test.R;
import androidx.media3.test.utils.DataSourceContractTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.runner.RunWith;

/** {@link DataSource} contract tests for {@link RawResourceDataSource}. */
@RunWith(AndroidJUnit4.class)
public final class RawResourceDataSourceContractTest extends DataSourceContractTest {

  private static final byte[] RESOURCE_1_DATA = Util.getUtf8Bytes("resource1 abc\n");
  private static final byte[] RESOURCE_2_DATA = Util.getUtf8Bytes("resource2 abcdef\n");
  private static final byte[] FONT_DATA = Util.getUtf8Bytes("test font data\n");

  @Override
  protected DataSource createDataSource() {
    return new RawResourceDataSource(ApplicationProvider.getApplicationContext());
  }

  @SuppressWarnings("deprecation") // Testing deprecated buildRawResourceUri method
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
            .setName("android.resource:// with package, 'raw' type, and name")
            .setUri(
                Uri.parse(
                    "android.resource://"
                        + ApplicationProvider.getApplicationContext().getPackageName()
                        + "/raw/resource1"))
            .setExpectedBytes(RESOURCE_1_DATA)
            .build(),
        new TestResource.Builder()
            .setName("android.resource:// with package, 'font' type, and name")
            .setUri(
                Uri.parse(
                    "android.resource://"
                        + ApplicationProvider.getApplicationContext().getPackageName()
                        + "/font/test_font"))
            .setExpectedBytes(FONT_DATA)
            .build(),
        new TestResource.Builder()
            .setName("android.resource:// with type and name only")
            .setUri(Uri.parse("android.resource:///raw/resource1"))
            .setExpectedBytes(RESOURCE_1_DATA)
            .build(),
        new TestResource.Builder()
            .setName("android.resource:// with name only")
            .setUri(Uri.parse("android.resource:///resource1"))
            .setExpectedBytes(RESOURCE_1_DATA)
            .build(),
        new TestResource.Builder()
            .setName("android.resource:// with ID")
            .setUri(Uri.parse("android.resource:///" + R.raw.resource1))
            .setExpectedBytes(RESOURCE_1_DATA)
            .build(),
        new TestResource.Builder()
            .setName("android.resource:// with package and ID")
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
    return Uri.parse("android.resource://" + Resources.ID_NULL);
  }
}
