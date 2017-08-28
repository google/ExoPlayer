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

import android.test.InstrumentationTestCase;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.mockito.Mockito;

/**
 * Unit tests for {@link ProgressiveDownloadAction}.
 */
public class ProgressiveDownloadActionTest extends InstrumentationTestCase {

  public void testDownloadActionIsNotRemoveAction() throws Exception {
    ProgressiveDownloadAction action = new ProgressiveDownloadAction("uri", null, false);
    assertFalse(action.isRemoveAction());
  }

  public void testRemoveActionIsRemoveAction() throws Exception {
    ProgressiveDownloadAction action2 = new ProgressiveDownloadAction("uri", null, true);
    assertTrue(action2.isRemoveAction());
  }

  public void testCreateDownloader() throws Exception {
    TestUtil.setUpMockito(this);
    ProgressiveDownloadAction action = new ProgressiveDownloadAction("uri", null, false);
    DownloaderConstructorHelper constructorHelper = new DownloaderConstructorHelper(
        Mockito.mock(Cache.class), DummyDataSource.FACTORY);
    assertNotNull(action.createDownloader(constructorHelper));
  }

  public void testSameUriCacheKeyDifferentAction_IsSameMedia() throws Exception {
    ProgressiveDownloadAction action1 = new ProgressiveDownloadAction("uri", null, true);
    ProgressiveDownloadAction action2 = new ProgressiveDownloadAction("uri", null, false);
    assertTrue(action1.isSameMedia(action2));
  }

  public void testNullCacheKeyDifferentUriAction_IsNotSameMedia() throws Exception {
    ProgressiveDownloadAction action3 = new ProgressiveDownloadAction("uri2", null, true);
    ProgressiveDownloadAction action4 = new ProgressiveDownloadAction("uri", null, false);
    assertFalse(action3.isSameMedia(action4));
  }

  public void testSameCacheKeyDifferentUriAction_IsSameMedia() throws Exception {
    ProgressiveDownloadAction action5 = new ProgressiveDownloadAction("uri2", "key", true);
    ProgressiveDownloadAction action6 = new ProgressiveDownloadAction("uri", "key", false);
    assertTrue(action5.isSameMedia(action6));
  }

  public void testSameUriDifferentCacheKeyAction_IsNotSameMedia() throws Exception {
    ProgressiveDownloadAction action7 = new ProgressiveDownloadAction("uri", "key", true);
    ProgressiveDownloadAction action8 = new ProgressiveDownloadAction("uri", "key2", false);
    assertFalse(action7.isSameMedia(action8));
  }

  public void testEquals() throws Exception {
    ProgressiveDownloadAction action1 = new ProgressiveDownloadAction("uri", null, true);
    assertTrue(action1.equals(action1));

    ProgressiveDownloadAction action2 = new ProgressiveDownloadAction("uri", null, true);
    ProgressiveDownloadAction action3 = new ProgressiveDownloadAction("uri", null, true);
    assertTrue(action2.equals(action3));

    ProgressiveDownloadAction action4 = new ProgressiveDownloadAction("uri", null, true);
    ProgressiveDownloadAction action5 = new ProgressiveDownloadAction("uri", null, false);
    assertFalse(action4.equals(action5));

    ProgressiveDownloadAction action6 = new ProgressiveDownloadAction("uri", null, true);
    ProgressiveDownloadAction action7 = new ProgressiveDownloadAction("uri", "key", true);
    assertFalse(action6.equals(action7));

    ProgressiveDownloadAction action8 = new ProgressiveDownloadAction("uri", "key2", true);
    ProgressiveDownloadAction action9 = new ProgressiveDownloadAction("uri", "key", true);
    assertFalse(action8.equals(action9));

    ProgressiveDownloadAction action10 = new ProgressiveDownloadAction("uri", null, true);
    ProgressiveDownloadAction action11 = new ProgressiveDownloadAction("uri2", null, true);
    assertFalse(action10.equals(action11));
  }

  public void testSerializerGetType() throws Exception {
    ProgressiveDownloadAction action = new ProgressiveDownloadAction("uri", null, false);
    assertNotNull(action.getType());
  }

  public void testSerializerWriteRead() throws Exception {
    doTestSerializationRoundTrip(new ProgressiveDownloadAction("uri1", null, false));
    doTestSerializationRoundTrip(new ProgressiveDownloadAction("uri2", "key", true));
  }

  private void doTestSerializationRoundTrip(ProgressiveDownloadAction action1) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream(out);
    action1.writeToStream(output);

    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    DataInputStream input = new DataInputStream(in);
    DownloadAction action2 = ProgressiveDownloadAction.DESERIALIZER.readFromStream(input);

    assertEquals(action1, action2);
  }

}
