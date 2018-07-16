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
package com.google.android.exoplayer2.offline;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link ProgressiveDownloadAction}. */
@RunWith(RobolectricTestRunner.class)
public class ProgressiveDownloadActionTest {

  private Uri uri1;
  private Uri uri2;

  @Before
  public void setUp() {
    uri1 = Uri.parse("http://test1.uri");
    uri2 = Uri.parse("http://test2.uri");
  }

  @Test
  public void testDownloadActionIsNotRemoveAction() throws Exception {
    DownloadAction action = createDownloadAction(uri1, null);
    assertThat(action.isRemoveAction).isFalse();
  }

  @Test
  public void testRemoveActionisRemoveAction() throws Exception {
    DownloadAction action2 = createRemoveAction(uri1, null);
    assertThat(action2.isRemoveAction).isTrue();
  }

  @Test
  public void testCreateDownloader() throws Exception {
    MockitoAnnotations.initMocks(this);
    DownloadAction action = createDownloadAction(uri1, null);
    DownloaderConstructorHelper constructorHelper =
        new DownloaderConstructorHelper(Mockito.mock(Cache.class), DummyDataSource.FACTORY);
    assertThat(action.createDownloader(constructorHelper)).isNotNull();
  }

  @Test
  public void testSameUriCacheKeyDifferentAction_IsSameMedia() throws Exception {
    DownloadAction action1 = createRemoveAction(uri1, null);
    DownloadAction action2 = createDownloadAction(uri1, null);
    assertSameMedia(action1, action2);
  }

  @Test
  public void testNullCacheKeyDifferentUriAction_IsNotSameMedia() throws Exception {
    DownloadAction action3 = createRemoveAction(uri2, null);
    DownloadAction action4 = createDownloadAction(uri1, null);
    assertNotSameMedia(action3, action4);
  }

  @Test
  public void testSameCacheKeyDifferentUriAction_IsSameMedia() throws Exception {
    DownloadAction action5 = createRemoveAction(uri2, "key");
    DownloadAction action6 = createDownloadAction(uri1, "key");
    assertSameMedia(action5, action6);
  }

  @Test
  public void testSameUriDifferentCacheKeyAction_IsNotSameMedia() throws Exception {
    DownloadAction action7 = createRemoveAction(uri1, "key");
    DownloadAction action8 = createDownloadAction(uri1, "key2");
    assertNotSameMedia(action7, action8);
  }

  @Test
  public void testSameUriNullCacheKeyAction_IsNotSameMedia() throws Exception {
    DownloadAction action1 = createRemoveAction(uri1, "key");
    DownloadAction action2 = createDownloadAction(uri1, null);
    assertNotSameMedia(action1, action2);
  }

  @Test
  public void testEquals() throws Exception {
    DownloadAction action1 = createRemoveAction(uri1, null);
    assertThat(action1.equals(action1)).isTrue();

    DownloadAction action2 = createRemoveAction(uri1, null);
    DownloadAction action3 = createRemoveAction(uri1, null);
    assertThat(action2.equals(action3)).isTrue();

    DownloadAction action4 = createRemoveAction(uri1, null);
    DownloadAction action5 = createDownloadAction(uri1, null);
    assertThat(action4.equals(action5)).isFalse();

    DownloadAction action6 = createRemoveAction(uri1, null);
    DownloadAction action7 = createRemoveAction(uri1, "key");
    assertThat(action6.equals(action7)).isFalse();

    DownloadAction action8 = createRemoveAction(uri1, "key2");
    DownloadAction action9 = createRemoveAction(uri1, "key");
    assertThat(action8.equals(action9)).isFalse();

    DownloadAction action10 = createRemoveAction(uri1, null);
    DownloadAction action11 = createRemoveAction(uri2, null);
    assertThat(action10.equals(action11)).isFalse();
  }

  @Test
  public void testSerializerGetType() throws Exception {
    DownloadAction action = createDownloadAction(uri1, null);
    assertThat(action.type).isNotNull();
  }

  @Test
  public void testSerializerWriteRead() throws Exception {
    doTestSerializationRoundTrip(createDownloadAction(uri1, null));
    doTestSerializationRoundTrip(createRemoveAction(uri2, "key"));
  }

  private void assertSameMedia(DownloadAction action1, DownloadAction action2) {
    assertThat(action1.isSameMedia(action2)).isTrue();
    assertThat(action2.isSameMedia(action1)).isTrue();
  }

  private void assertNotSameMedia(DownloadAction action1, DownloadAction action2) {
    assertThat(action1.isSameMedia(action2)).isFalse();
    assertThat(action2.isSameMedia(action1)).isFalse();
  }

  private static void doTestSerializationRoundTrip(DownloadAction action) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream(out);
    DownloadAction.serializeToStream(action, output);

    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    DataInputStream input = new DataInputStream(in);
    DownloadAction action2 =
        DownloadAction.deserializeFromStream(
            new DownloadAction.Deserializer[] {ProgressiveDownloadAction.DESERIALIZER}, input);

    assertThat(action2).isEqualTo(action);
  }

  private static DownloadAction createDownloadAction(Uri uri1, String customCacheKey) {
    return ProgressiveDownloadAction.createDownloadAction(uri1, null, customCacheKey);
  }

  private static DownloadAction createRemoveAction(Uri uri1, String customCacheKey) {
    return ProgressiveDownloadAction.createRemoveAction(uri1, null, customCacheKey);
  }
}
