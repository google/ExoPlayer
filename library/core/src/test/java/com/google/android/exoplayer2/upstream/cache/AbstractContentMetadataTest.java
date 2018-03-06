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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.cache.Cache.CacheException;
import com.google.android.exoplayer2.upstream.cache.ContentMetadata.Editor;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests {@link AbstractContentMetadata}. */
@RunWith(RobolectricTestRunner.class)
public class AbstractContentMetadataTest {

  private FakeAbstractContentMetadata contentMetadata;

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
  public void testEditReturnsAnEditor() throws Exception {
    assertThat(contentMetadata.edit()).isNotNull();
  }

  @Test
  public void testEditReturnsAnotherEditorEveryTime() throws Exception {
    assertThat(contentMetadata.edit()).isNotEqualTo(contentMetadata.edit());
  }

  @Test
  public void testCommitWithoutEditDoesNotFail() throws Exception {
    Editor editor = contentMetadata.edit();
    editor.commit();
  }

  @Test
  public void testAddNewMetadata() throws Exception {
    Editor editor = contentMetadata.edit();
    editor.set("metadata name", "value");
    editor.commit();
    assertThat(contentMetadata.get("metadata name", "default value")).isEqualTo("value");
  }

  @Test
  public void testAddNewIntMetadata() throws Exception {
    Editor editor = contentMetadata.edit();
    editor.set("metadata name", 5);
    editor.commit();
    assertThat(contentMetadata.get("metadata name", 0)).isEqualTo(5);
  }

  @Test
  public void testAddNewByteArrayMetadata() throws Exception {
    Editor editor = contentMetadata.edit();
    byte[] value = {1, 2, 3};
    editor.set("metadata name", value);
    editor.commit();
    assertThat(contentMetadata.get("metadata name", new byte[] {})).isEqualTo(value);
  }

  @Test
  public void testNewMetadataNotWrittenBeforeCommitted() throws Exception {
    Editor editor = contentMetadata.edit();
    editor.set("metadata name", "value");
    assertThat(contentMetadata.get("metadata name", "default value")).isEqualTo("default value");
  }

  @Test
  public void testEditMetadata() throws Exception {
    contentMetadata = createAbstractContentMetadata("metadata name", "value");
    Editor editor = contentMetadata.edit();
    editor.set("metadata name", "edited value");
    editor.commit();
    assertThat(contentMetadata.get("metadata name", "default value")).isEqualTo("edited value");
  }

  @Test
  public void testRemoveMetadata() throws Exception {
    contentMetadata = createAbstractContentMetadata("metadata name", "value");
    Editor editor = contentMetadata.edit();
    editor.remove("metadata name");
    editor.commit();
    assertThat(contentMetadata.get("metadata name", "default value")).isEqualTo("default value");
  }

  @Test
  public void testAddAndRemoveMetadata() throws Exception {
    Editor editor = contentMetadata.edit();
    editor.set("metadata name", "value");
    editor.remove("metadata name");
    editor.commit();
    assertThat(contentMetadata.get("metadata name", "default value")).isEqualTo("default value");
  }

  @Test
  public void testRemoveAndAddMetadata() throws Exception {
    Editor editor = contentMetadata.edit();
    editor.remove("metadata name");
    editor.set("metadata name", "value");
    editor.commit();
    assertThat(contentMetadata.get("metadata name", "default value")).isEqualTo("value");
  }

  @Test
  public void testOnChangeIsCalledWhenMetadataEdited() throws Exception {
    contentMetadata =
        createAbstractContentMetadata(
            "metadata name", "value", "metadata name2", "value2", "metadata name3", "value3");
    Editor editor = contentMetadata.edit();
    editor.set("metadata name", "edited value");
    editor.remove("metadata name2");
    editor.commit();
    assertThat(contentMetadata.remainingValues).containsExactly("metadata name", "metadata name3");
  }

  private FakeAbstractContentMetadata createAbstractContentMetadata(String... pairs) {
    assertThat(pairs.length % 2).isEqualTo(0);
    HashMap<String, byte[]> map = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put(pairs[i], getBytes(pairs[i + 1]));
    }
    return new FakeAbstractContentMetadata(Collections.unmodifiableMap(map));
  }

  private static byte[] getBytes(String value) {
    return value.getBytes(Charset.forName(C.UTF8_NAME));
  }

  private static class FakeAbstractContentMetadata extends AbstractContentMetadata {

    private ArrayList<String> remainingValues;

    private FakeAbstractContentMetadata(Map<String, byte[]> metadataValues) {
      super(metadataValues);
    }

    @Override
    protected void onChange(Map<String, byte[]> metadataValues) throws CacheException {
      remainingValues = new ArrayList<>(metadataValues.keySet());
    }
  }
}
