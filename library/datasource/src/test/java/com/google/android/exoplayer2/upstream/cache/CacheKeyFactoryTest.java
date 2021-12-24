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
package com.google.android.exoplayer2.upstream.cache;

import static com.google.android.exoplayer2.upstream.cache.CacheKeyFactory.DEFAULT;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.upstream.DataSpec;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests {@link CacheKeyFactoryTest}. */
@RunWith(AndroidJUnit4.class)
public class CacheKeyFactoryTest {

  @Test
  public void default_dataSpecWithKey_returnsKey() {
    Uri testUri = Uri.parse("test");
    String key = "key";
    DataSpec dataSpec = new DataSpec.Builder().setUri(testUri).setKey(key).build();
    assertThat(DEFAULT.buildCacheKey(dataSpec)).isEqualTo(key);
  }

  @Test
  public void default_dataSpecWithoutKey_returnsUri() {
    Uri testUri = Uri.parse("test");
    DataSpec dataSpec = new DataSpec.Builder().setUri(testUri).build();
    assertThat(DEFAULT.buildCacheKey(dataSpec)).isEqualTo(testUri.toString());
  }
}
