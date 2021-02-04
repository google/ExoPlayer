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
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.DataSourceContractTest;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Before;
import org.junit.runner.RunWith;

/** {@link DataSource} contract tests for {@link AssetDataSource}. */
@RunWith(AndroidJUnit4.class)
public class AssetDataSourceContractTest extends DataSourceContractTest {

  // We pick an arbitrary file from the assets. The selected file has a convenient size of 1024
  // bytes.
  private static final String ASSET_PATH = "media/mp3/1024_incrementing_bytes.mp3";
  private static final Uri ASSET_URI = Uri.parse("asset:///" + ASSET_PATH);

  private byte[] data;

  @Before
  public void setUp() throws IOException {
    data = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), ASSET_PATH);
  }

  @Override
  protected ImmutableList<TestResource> getTestResources() {
    return ImmutableList.of(
        new TestResource.Builder()
            .setName("simple")
            .setUri(ASSET_URI)
            .setExpectedBytes(data)
            .build());
  }

  @Override
  protected Uri getNotFoundUri() {
    return Uri.parse("asset:///nonexistentdir/nonexistentfile");
  }

  @Override
  protected DataSource createDataSource() {
    return new AssetDataSource(ApplicationProvider.getApplicationContext());
  }
}
