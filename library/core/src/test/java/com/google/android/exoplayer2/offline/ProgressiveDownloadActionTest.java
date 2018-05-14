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

/**
 * Unit tests for {@link ProgressiveDownloadAction}.
 */
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
    ProgressiveDownloadAction action = new ProgressiveDownloadAction(uri1, false, null, null);
    assertThat(action.isRemoveAction).isFalse();
  }

  @Test
  public void testRemoveActionisRemoveAction() throws Exception {
    ProgressiveDownloadAction action2 = new ProgressiveDownloadAction(uri1, true, null, null);
    assertThat(action2.isRemoveAction).isTrue();
  }

  @Test
  public void testCreateDownloader() throws Exception {
    MockitoAnnotations.initMocks(this);
    ProgressiveDownloadAction action = new ProgressiveDownloadAction(uri1, false, null, null);
    DownloaderConstructorHelper constructorHelper = new DownloaderConstructorHelper(
        Mockito.mock(Cache.class), DummyDataSource.FACTORY);
    assertThat(action.createDownloader(constructorHelper)).isNotNull();
  }

  @Test
  public void testSameUriCacheKeyDifferentAction_IsSameMedia() throws Exception {
    ProgressiveDownloadAction action1 = new ProgressiveDownloadAction(uri1, true, null, null);
    ProgressiveDownloadAction action2 = new ProgressiveDownloadAction(uri1, false, null, null);
    assertSameMedia(action1, action2);
  }

  @Test
  public void testNullCacheKeyDifferentUriAction_IsNotSameMedia() throws Exception {
    ProgressiveDownloadAction action3 = new ProgressiveDownloadAction(uri2, true, null, null);
    ProgressiveDownloadAction action4 = new ProgressiveDownloadAction(uri1, false, null, null);
    assertNotSameMedia(action3, action4);
  }

  @Test
  public void testSameCacheKeyDifferentUriAction_IsSameMedia() throws Exception {
    ProgressiveDownloadAction action5 = new ProgressiveDownloadAction(uri2, true, null, "key");
    ProgressiveDownloadAction action6 = new ProgressiveDownloadAction(uri1, false, null, "key");
    assertSameMedia(action5, action6);
  }

  @Test
  public void testSameUriDifferentCacheKeyAction_IsNotSameMedia() throws Exception {
    ProgressiveDownloadAction action7 = new ProgressiveDownloadAction(uri1, true, null, "key");
    ProgressiveDownloadAction action8 = new ProgressiveDownloadAction(uri1, false, null, "key2");
    assertNotSameMedia(action7, action8);
  }

  @Test
  public void testSameUriNullCacheKeyAction_IsNotSameMedia() throws Exception {
    ProgressiveDownloadAction action1 = new ProgressiveDownloadAction(uri1, true, null, "key");
    ProgressiveDownloadAction action2 = new ProgressiveDownloadAction(uri1, false, null, null);
    assertNotSameMedia(action1, action2);
  }

  @Test
  public void testEquals() throws Exception {
    ProgressiveDownloadAction action1 = new ProgressiveDownloadAction(uri1, true, null, null);
    assertThat(action1.equals(action1)).isTrue();

    ProgressiveDownloadAction action2 = new ProgressiveDownloadAction(uri1, true, null, null);
    ProgressiveDownloadAction action3 = new ProgressiveDownloadAction(uri1, true, null, null);
    assertThat(action2.equals(action3)).isTrue();

    ProgressiveDownloadAction action4 = new ProgressiveDownloadAction(uri1, true, null, null);
    ProgressiveDownloadAction action5 = new ProgressiveDownloadAction(uri1, false, null, null);
    assertThat(action4.equals(action5)).isFalse();

    ProgressiveDownloadAction action6 = new ProgressiveDownloadAction(uri1, true, null, null);
    ProgressiveDownloadAction action7 = new ProgressiveDownloadAction(uri1, true, null, "key");
    assertThat(action6.equals(action7)).isFalse();

    ProgressiveDownloadAction action8 = new ProgressiveDownloadAction(uri1, true, null, "key2");
    ProgressiveDownloadAction action9 = new ProgressiveDownloadAction(uri1, true, null, "key");
    assertThat(action8.equals(action9)).isFalse();

    ProgressiveDownloadAction action10 = new ProgressiveDownloadAction(uri1, true, null, null);
    ProgressiveDownloadAction action11 = new ProgressiveDownloadAction(uri2, true, null, null);
    assertThat(action10.equals(action11)).isFalse();
  }

  @Test
  public void testSerializerGetType() throws Exception {
    ProgressiveDownloadAction action = new ProgressiveDownloadAction(uri1, false, null, null);
    assertThat(action.type).isNotNull();
  }

  @Test
  public void testSerializerWriteRead() throws Exception {
    doTestSerializationRoundTrip(new ProgressiveDownloadAction(uri1, false, null, null));
    doTestSerializationRoundTrip(new ProgressiveDownloadAction(uri2, true, null, "key"));
  }

  private void assertSameMedia(
      ProgressiveDownloadAction action1, ProgressiveDownloadAction action2) {
    assertThat(action1.isSameMedia(action2)).isTrue();
    assertThat(action2.isSameMedia(action1)).isTrue();
  }

  private void assertNotSameMedia(
      ProgressiveDownloadAction action1, ProgressiveDownloadAction action2) {
    assertThat(action1.isSameMedia(action2)).isFalse();
    assertThat(action2.isSameMedia(action1)).isFalse();
  }

  private static void doTestSerializationRoundTrip(ProgressiveDownloadAction action)
      throws IOException {
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

}
