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

import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.util.ClosedSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for {@link ProgressiveDownloadAction}.
 */
@ClosedSource(reason = "Not ready yet")
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.TARGET_SDK, manifest = Config.NONE)
public class ProgressiveDownloadActionTest {

  @Test
  public void testDownloadActionIsNotRemoveAction() throws Exception {
    ProgressiveDownloadAction action = new ProgressiveDownloadAction("uri", null, false);
    assertThat(action.isRemoveAction()).isFalse();
  }

  @Test
  public void testRemoveActionIsRemoveAction() throws Exception {
    ProgressiveDownloadAction action2 = new ProgressiveDownloadAction("uri", null, true);
    assertThat(action2.isRemoveAction()).isTrue();
  }

  @Test
  public void testCreateDownloader() throws Exception {
    MockitoAnnotations.initMocks(this);
    ProgressiveDownloadAction action = new ProgressiveDownloadAction("uri", null, false);
    DownloaderConstructorHelper constructorHelper = new DownloaderConstructorHelper(
        Mockito.mock(Cache.class), DummyDataSource.FACTORY);
    assertThat(action.createDownloader(constructorHelper)).isNotNull();
  }

  @Test
  public void testSameUriCacheKeyDifferentAction_IsSameMedia() throws Exception {
    ProgressiveDownloadAction action1 = new ProgressiveDownloadAction("uri", null, true);
    ProgressiveDownloadAction action2 = new ProgressiveDownloadAction("uri", null, false);
    assertThat(action1.isSameMedia(action2)).isTrue();
  }

  @Test
  public void testNullCacheKeyDifferentUriAction_IsNotSameMedia() throws Exception {
    ProgressiveDownloadAction action3 = new ProgressiveDownloadAction("uri2", null, true);
    ProgressiveDownloadAction action4 = new ProgressiveDownloadAction("uri", null, false);
    assertThat(action3.isSameMedia(action4)).isFalse();
  }

  @Test
  public void testSameCacheKeyDifferentUriAction_IsSameMedia() throws Exception {
    ProgressiveDownloadAction action5 = new ProgressiveDownloadAction("uri2", "key", true);
    ProgressiveDownloadAction action6 = new ProgressiveDownloadAction("uri", "key", false);
    assertThat(action5.isSameMedia(action6)).isTrue();
  }

  @Test
  public void testSameUriDifferentCacheKeyAction_IsNotSameMedia() throws Exception {
    ProgressiveDownloadAction action7 = new ProgressiveDownloadAction("uri", "key", true);
    ProgressiveDownloadAction action8 = new ProgressiveDownloadAction("uri", "key2", false);
    assertThat(action7.isSameMedia(action8)).isFalse();
  }

  @Test
  public void testEquals() throws Exception {
    ProgressiveDownloadAction action1 = new ProgressiveDownloadAction("uri", null, true);
    assertThat(action1.equals(action1)).isTrue();

    ProgressiveDownloadAction action2 = new ProgressiveDownloadAction("uri", null, true);
    ProgressiveDownloadAction action3 = new ProgressiveDownloadAction("uri", null, true);
    assertThat(action2.equals(action3)).isTrue();

    ProgressiveDownloadAction action4 = new ProgressiveDownloadAction("uri", null, true);
    ProgressiveDownloadAction action5 = new ProgressiveDownloadAction("uri", null, false);
    assertThat(action4.equals(action5)).isFalse();

    ProgressiveDownloadAction action6 = new ProgressiveDownloadAction("uri", null, true);
    ProgressiveDownloadAction action7 = new ProgressiveDownloadAction("uri", "key", true);
    assertThat(action6.equals(action7)).isFalse();

    ProgressiveDownloadAction action8 = new ProgressiveDownloadAction("uri", "key2", true);
    ProgressiveDownloadAction action9 = new ProgressiveDownloadAction("uri", "key", true);
    assertThat(action8.equals(action9)).isFalse();

    ProgressiveDownloadAction action10 = new ProgressiveDownloadAction("uri", null, true);
    ProgressiveDownloadAction action11 = new ProgressiveDownloadAction("uri2", null, true);
    assertThat(action10.equals(action11)).isFalse();
  }

  @Test
  public void testSerializerGetType() throws Exception {
    ProgressiveDownloadAction action = new ProgressiveDownloadAction("uri", null, false);
    assertThat(action.getType()).isNotNull();
  }

  @Test
  public void testSerializerWriteRead() throws Exception {
    doTestSerializationRoundTrip(new ProgressiveDownloadAction("uri1", null, false));
    doTestSerializationRoundTrip(new ProgressiveDownloadAction("uri2", "key", true));
  }

  private static void doTestSerializationRoundTrip(ProgressiveDownloadAction action1)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream(out);
    action1.writeToStream(output);

    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    DataInputStream input = new DataInputStream(in);
    DownloadAction action2 =
        ProgressiveDownloadAction.DESERIALIZER.readFromStream(action1.getVersion(), input);

    assertThat(action2).isEqualTo(action1);
  }

}
