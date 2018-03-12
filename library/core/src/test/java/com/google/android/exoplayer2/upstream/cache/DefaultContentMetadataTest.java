/*
 * Copyright (C) 2018 The Android Open Source Project
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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests {@link DefaultContentMetadata}. */
@RunWith(RobolectricTestRunner.class)
public class DefaultContentMetadataTest {

  private DefaultContentMetadata contentMetadata;

  @Before
  public void setUp() throws Exception {
    contentMetadata = createAbstractContentMetadata();
  }

  @Test
  public void testContainsReturnsFalseWhenEmpty() throws Exception {
    assertThat(contentMetadata.contains("test metadata")).isFalse();
  }

  @Test
  public void testContainsReturnsTrueForInitialValue() throws Exception {
    contentMetadata = createAbstractContentMetadata("metadata name", "value");
    assertThat(contentMetadata.contains("metadata name")).isTrue();
  }

  @Test
  public void testGetReturnsDefaultValueWhenValueIsNotAvailable() throws Exception {
    assertThat(contentMetadata.get("metadata name", "default value")).isEqualTo("default value");
  }

  @Test
  public void testGetReturnsInitialValue() throws Exception {
    contentMetadata = createAbstractContentMetadata("metadata name", "value");
    assertThat(contentMetadata.get("metadata name", "default value")).isEqualTo("value");
  }

  @Test
  public void testEmptyMutationDoesNotFail() throws Exception {
    ContentMetadataMutations mutations = new ContentMetadataMutations();
    new DefaultContentMetadata(new DefaultContentMetadata(), mutations);
  }

  @Test
  public void testAddNewMetadata() throws Exception {
    ContentMetadataMutations mutations = new ContentMetadataMutations();
    mutations.set("metadata name", "value");
    contentMetadata = new DefaultContentMetadata(contentMetadata, mutations);
    assertThat(contentMetadata.get("metadata name", "default value")).isEqualTo("value");
  }

  @Test
  public void testAddNewIntMetadata() throws Exception {
    ContentMetadataMutations mutations = new ContentMetadataMutations();
    mutations.set("metadata name", 5);
    contentMetadata = new DefaultContentMetadata(contentMetadata, mutations);
    assertThat(contentMetadata.get("metadata name", 0)).isEqualTo(5);
  }

  @Test
  public void testAddNewByteArrayMetadata() throws Exception {
    ContentMetadataMutations mutations = new ContentMetadataMutations();
    byte[] value = {1, 2, 3};
    mutations.set("metadata name", value);
    contentMetadata = new DefaultContentMetadata(contentMetadata, mutations);
    assertThat(contentMetadata.get("metadata name", new byte[] {})).isEqualTo(value);
  }

  @Test
  public void testNewMetadataNotWrittenBeforeCommitted() throws Exception {
    ContentMetadataMutations mutations = new ContentMetadataMutations();
    mutations.set("metadata name", "value");
    assertThat(contentMetadata.get("metadata name", "default value")).isEqualTo("default value");
  }

  @Test
  public void testEditMetadata() throws Exception {
    contentMetadata = createAbstractContentMetadata("metadata name", "value");
    ContentMetadataMutations mutations = new ContentMetadataMutations();
    mutations.set("metadata name", "edited value");
    contentMetadata = new DefaultContentMetadata(contentMetadata, mutations);
    assertThat(contentMetadata.get("metadata name", "default value")).isEqualTo("edited value");
  }

  @Test
  public void testRemoveMetadata() throws Exception {
    contentMetadata = createAbstractContentMetadata("metadata name", "value");
    ContentMetadataMutations mutations = new ContentMetadataMutations();
    mutations.remove("metadata name");
    contentMetadata = new DefaultContentMetadata(contentMetadata, mutations);
    assertThat(contentMetadata.get("metadata name", "default value")).isEqualTo("default value");
  }

  @Test
  public void testAddAndRemoveMetadata() throws Exception {
    ContentMetadataMutations mutations = new ContentMetadataMutations();
    mutations.set("metadata name", "value");
    mutations.remove("metadata name");
    contentMetadata = new DefaultContentMetadata(contentMetadata, mutations);
    assertThat(contentMetadata.get("metadata name", "default value")).isEqualTo("default value");
  }

  @Test
  public void testRemoveAndAddMetadata() throws Exception {
    ContentMetadataMutations mutations = new ContentMetadataMutations();
    mutations.remove("metadata name");
    mutations.set("metadata name", "value");
    contentMetadata = new DefaultContentMetadata(contentMetadata, mutations);
    assertThat(contentMetadata.get("metadata name", "default value")).isEqualTo("value");
  }

  private DefaultContentMetadata createAbstractContentMetadata(String... pairs) {
    assertThat(pairs.length % 2).isEqualTo(0);
    ContentMetadataMutations mutations = new ContentMetadataMutations();
    for (int i = 0; i < pairs.length; i += 2) {
      mutations.set(pairs[i], pairs[i + 1]);
    }
    return new DefaultContentMetadata(new DefaultContentMetadata(), mutations);
  }
}
