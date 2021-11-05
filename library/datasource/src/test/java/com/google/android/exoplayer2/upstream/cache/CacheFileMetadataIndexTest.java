/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.database.DatabaseIOException;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests {@link CacheFileMetadataIndex}. */
@RunWith(AndroidJUnit4.class)
public class CacheFileMetadataIndexTest {

  @Test
  public void initiallyEmpty() throws DatabaseIOException {
    CacheFileMetadataIndex index = newInitializedIndex();
    assertThat(index.getAll()).isEmpty();
  }

  @Test
  public void insert() throws DatabaseIOException {
    CacheFileMetadataIndex index = newInitializedIndex();

    index.set("name1", /* length= */ 123, /* lastTouchTimestamp= */ 456);
    index.set("name2", /* length= */ 789, /* lastTouchTimestamp= */ 123);

    Map<String, CacheFileMetadata> all = index.getAll();
    assertThat(all.size()).isEqualTo(2);

    CacheFileMetadata metadata = all.get("name1");
    assertThat(metadata).isNotNull();
    assertThat(metadata.length).isEqualTo(123);
    assertThat(metadata.lastTouchTimestamp).isEqualTo(456);

    metadata = all.get("name2");
    assertThat(metadata).isNotNull();
    assertThat(metadata.length).isEqualTo(789);
    assertThat(metadata.lastTouchTimestamp).isEqualTo(123);

    metadata = all.get("name3");
    assertThat(metadata).isNull();
  }

  @Test
  public void insertAndRemove() throws DatabaseIOException {
    CacheFileMetadataIndex index = newInitializedIndex();

    index.set("name1", /* length= */ 123, /* lastTouchTimestamp= */ 456);
    index.set("name2", /* length= */ 789, /* lastTouchTimestamp= */ 123);

    index.remove("name1");

    Map<String, CacheFileMetadata> all = index.getAll();
    assertThat(all.size()).isEqualTo(1);

    CacheFileMetadata metadata = all.get("name1");
    assertThat(metadata).isNull();

    metadata = all.get("name2");
    assertThat(metadata).isNotNull();
    assertThat(metadata.length).isEqualTo(789);
    assertThat(metadata.lastTouchTimestamp).isEqualTo(123);

    index.remove("name2");

    all = index.getAll();
    assertThat(all).isEmpty();

    metadata = all.get("name2");
    assertThat(metadata).isNull();
  }

  @Test
  public void insertAndRemoveAll() throws DatabaseIOException {
    CacheFileMetadataIndex index = newInitializedIndex();

    index.set("name1", /* length= */ 123, /* lastTouchTimestamp= */ 456);
    index.set("name2", /* length= */ 789, /* lastTouchTimestamp= */ 123);

    HashSet<String> namesToRemove = new HashSet<>();
    namesToRemove.add("name1");
    namesToRemove.add("name2");
    index.removeAll(namesToRemove);

    Map<String, CacheFileMetadata> all = index.getAll();
    assertThat(all.isEmpty()).isTrue();

    CacheFileMetadata metadata = all.get("name1");
    assertThat(metadata).isNull();

    metadata = all.get("name2");
    assertThat(metadata).isNull();
  }

  @Test
  public void insertAndReplace() throws DatabaseIOException {
    CacheFileMetadataIndex index = newInitializedIndex();

    index.set("name1", /* length= */ 123, /* lastTouchTimestamp= */ 456);
    index.set("name1", /* length= */ 789, /* lastTouchTimestamp= */ 123);

    Map<String, CacheFileMetadata> all = index.getAll();
    assertThat(all.size()).isEqualTo(1);

    CacheFileMetadata metadata = all.get("name1");
    assertThat(metadata).isNotNull();
    assertThat(metadata.length).isEqualTo(789);
    assertThat(metadata.lastTouchTimestamp).isEqualTo(123);
  }

  private static CacheFileMetadataIndex newInitializedIndex() throws DatabaseIOException {
    CacheFileMetadataIndex index =
        new CacheFileMetadataIndex(TestUtil.getInMemoryDatabaseProvider());
    index.initialize(/* uid= */ 1234);
    return index;
  }
}
