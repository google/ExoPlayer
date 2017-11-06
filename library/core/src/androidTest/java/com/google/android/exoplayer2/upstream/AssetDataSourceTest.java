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

import android.net.Uri;
import android.test.InstrumentationTestCase;
import com.google.android.exoplayer2.testutil.TestUtil;

/**
 * Unit tests for {@link AssetDataSource}.
 */
public final class AssetDataSourceTest extends InstrumentationTestCase {

  private static final String DATA_PATH = "binary/1024_incrementing_bytes.mp3";

  public void testReadFileUri() throws Exception {
    AssetDataSource dataSource = new AssetDataSource(getInstrumentation().getContext());
    DataSpec dataSpec = new DataSpec(Uri.parse("file:///android_asset/" + DATA_PATH));
    TestUtil.assertDataSourceContent(dataSource, dataSpec,
        TestUtil.getByteArray(getInstrumentation(), DATA_PATH), true);
  }

  public void testReadAssetUri() throws Exception {
    AssetDataSource dataSource = new AssetDataSource(getInstrumentation().getContext());
    DataSpec dataSpec = new DataSpec(Uri.parse("asset:///" + DATA_PATH));
    TestUtil.assertDataSourceContent(dataSource, dataSpec,
        TestUtil.getByteArray(getInstrumentation(), DATA_PATH), true);
  }

}
