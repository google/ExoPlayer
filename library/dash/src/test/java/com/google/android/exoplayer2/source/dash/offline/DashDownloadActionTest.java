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
package com.google.android.exoplayer2.source.dash.offline;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link DashDownloadAction}. */
@RunWith(RobolectricTestRunner.class)
public class DashDownloadActionTest {

  private Uri uri1;
  private Uri uri2;

  @Before
  public void setUp() {
    uri1 = Uri.parse("http://test1.uri");
    uri2 = Uri.parse("http://test2.uri");
  }

  @Test
  public void testDownloadActionIsNotRemoveAction() {
    DownloadAction action = createDownloadAction(uri1);
    assertThat(action.isRemoveAction).isFalse();
  }

  @Test
  public void testRemoveActionIsRemoveAction() {
    DownloadAction action2 = createRemoveAction(uri1);
    assertThat(action2.isRemoveAction).isTrue();
  }

  @Test
  public void testCreateDownloader() {
    MockitoAnnotations.initMocks(this);
    DownloadAction action = createDownloadAction(uri1);
    DownloaderConstructorHelper constructorHelper =
        new DownloaderConstructorHelper(Mockito.mock(Cache.class), DummyDataSource.FACTORY);
    assertThat(action.createDownloader(constructorHelper)).isNotNull();
  }

  @Test
  public void testSameUriDifferentAction_IsSameMedia() {
    DownloadAction action1 = createRemoveAction(uri1);
    DownloadAction action2 = createDownloadAction(uri1);
    assertThat(action1.isSameMedia(action2)).isTrue();
  }

  @Test
  public void testDifferentUriAndAction_IsNotSameMedia() {
    DownloadAction action3 = createRemoveAction(uri2);
    DownloadAction action4 = createDownloadAction(uri1);
    assertThat(action3.isSameMedia(action4)).isFalse();
  }

  @SuppressWarnings("EqualsWithItself")
  @Test
  public void testEquals() {
    DownloadAction action1 = createRemoveAction(uri1);
    assertThat(action1.equals(action1)).isTrue();

    DownloadAction action2 = createRemoveAction(uri1);
    DownloadAction action3 = createRemoveAction(uri1);
    assertEqual(action2, action3);

    DownloadAction action4 = createRemoveAction(uri1);
    DownloadAction action5 = createDownloadAction(uri1);
    assertNotEqual(action4, action5);

    DownloadAction action6 = createDownloadAction(uri1);
    DownloadAction action7 = createDownloadAction(uri1, new StreamKey(0, 0, 0));
    assertNotEqual(action6, action7);

    DownloadAction action8 = createDownloadAction(uri1, new StreamKey(1, 1, 1));
    DownloadAction action9 = createDownloadAction(uri1, new StreamKey(0, 0, 0));
    assertNotEqual(action8, action9);

    DownloadAction action10 = createRemoveAction(uri1);
    DownloadAction action11 = createRemoveAction(uri2);
    assertNotEqual(action10, action11);

    DownloadAction action12 =
        createDownloadAction(uri1, new StreamKey(0, 0, 0), new StreamKey(1, 1, 1));
    DownloadAction action13 =
        createDownloadAction(uri1, new StreamKey(1, 1, 1), new StreamKey(0, 0, 0));
    assertEqual(action12, action13);

    DownloadAction action14 = createDownloadAction(uri1, new StreamKey(0, 0, 0));
    DownloadAction action15 =
        createDownloadAction(uri1, new StreamKey(1, 1, 1), new StreamKey(0, 0, 0));
    assertNotEqual(action14, action15);

    DownloadAction action16 = createDownloadAction(uri1);
    DownloadAction action17 = createDownloadAction(uri1);
    assertEqual(action16, action17);
  }

  @Test
  public void testSerializerGetType() {
    DownloadAction action = createDownloadAction(uri1);
    assertThat(action.type).isNotNull();
  }

  @Test
  public void testSerializerWriteRead() throws Exception {
    doTestSerializationRoundTrip(createDownloadAction(uri1));
    doTestSerializationRoundTrip(createRemoveAction(uri1));
    doTestSerializationRoundTrip(
        createDownloadAction(uri2, new StreamKey(0, 0, 0), new StreamKey(1, 1, 1)));
  }

  private static void assertNotEqual(DownloadAction action1, DownloadAction action2) {
    assertThat(action1).isNotEqualTo(action2);
    assertThat(action2).isNotEqualTo(action1);
  }

  private static void assertEqual(DownloadAction action1, DownloadAction action2) {
    assertThat(action1).isEqualTo(action2);
    assertThat(action2).isEqualTo(action1);
  }

  private static void doTestSerializationRoundTrip(DownloadAction action) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream(out);
    DownloadAction.serializeToStream(action, output);

    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    DataInputStream input = new DataInputStream(in);
    DownloadAction action2 =
        DownloadAction.deserializeFromStream(
            new DownloadAction.Deserializer[] {DashDownloadAction.DESERIALIZER}, input);

    assertThat(action).isEqualTo(action2);
  }

  private static DownloadAction createDownloadAction(Uri uri, StreamKey... keys) {
    ArrayList<StreamKey> keysList = new ArrayList<>();
    Collections.addAll(keysList, keys);
    return DashDownloadAction.createDownloadAction(uri, null, keysList);
  }

  private static DownloadAction createRemoveAction(Uri uri) {
    return DashDownloadAction.createRemoveAction(uri, null);
  }
}
