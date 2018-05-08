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
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.source.dash.manifest.RepresentationKey;
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

/**
 * Unit tests for {@link DashDownloadAction}.
 */
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
    DashDownloadAction action = newAction(uri1, /* isRemoveAction= */ false, /* data= */ null);
    assertThat(action.isRemoveAction).isFalse();
  }

  @Test
  public void testRemoveActionisRemoveAction() {
    DashDownloadAction action2 = newAction(uri1, /* isRemoveAction= */ true, /* data= */ null);
    assertThat(action2.isRemoveAction).isTrue();
  }

  @Test
  public void testCreateDownloader() {
    MockitoAnnotations.initMocks(this);
    DashDownloadAction action = newAction(uri1, /* isRemoveAction= */ false, /* data= */ null);
    DownloaderConstructorHelper constructorHelper = new DownloaderConstructorHelper(
        Mockito.mock(Cache.class), DummyDataSource.FACTORY);
    assertThat(action.createDownloader(constructorHelper)).isNotNull();
  }

  @Test
  public void testSameUriDifferentAction_IsSameMedia() {
    DashDownloadAction action1 = newAction(uri1, /* isRemoveAction= */ true, /* data= */ null);
    DashDownloadAction action2 = newAction(uri1, /* isRemoveAction= */ false, /* data= */ null);
    assertThat(action1.isSameMedia(action2)).isTrue();
  }

  @Test
  public void testDifferentUriAndAction_IsNotSameMedia() {
    DashDownloadAction action3 = newAction(uri2, /* isRemoveAction= */ true, /* data= */ null);
    DashDownloadAction action4 = newAction(uri1, /* isRemoveAction= */ false, /* data= */ null);
    assertThat(action3.isSameMedia(action4)).isFalse();
  }

  @SuppressWarnings("EqualsWithItself")
  @Test
  public void testEquals() {
    DashDownloadAction action1 = newAction(uri1, /* isRemoveAction= */ true, /* data= */ null);
    assertThat(action1.equals(action1)).isTrue();

    DashDownloadAction action2 = newAction(uri1, /* isRemoveAction= */ true, /* data= */ null);
    DashDownloadAction action3 = newAction(uri1, /* isRemoveAction= */ true, /* data= */ null);
    assertEqual(action2, action3);

    DashDownloadAction action4 = newAction(uri1, /* isRemoveAction= */ true, /* data= */ null);
    DashDownloadAction action5 = newAction(uri1, /* isRemoveAction= */ false, /* data= */ null);
    assertNotEqual(action4, action5);

    DashDownloadAction action6 = newAction(uri1, /* isRemoveAction= */ false, /* data= */ null);
    DashDownloadAction action7 =
        newAction(
            uri1, /* isRemoveAction= */ false, /* data= */ null, new RepresentationKey(0, 0, 0));
    assertNotEqual(action6, action7);

    DashDownloadAction action8 =
        newAction(
            uri1, /* isRemoveAction= */ false, /* data= */ null, new RepresentationKey(1, 1, 1));
    DashDownloadAction action9 =
        newAction(
            uri1, /* isRemoveAction= */ false, /* data= */ null, new RepresentationKey(0, 0, 0));
    assertNotEqual(action8, action9);

    DashDownloadAction action10 = newAction(uri1, /* isRemoveAction= */ true, /* data= */ null);
    DashDownloadAction action11 = newAction(uri2, /* isRemoveAction= */ true, /* data= */ null);
    assertNotEqual(action10, action11);

    DashDownloadAction action12 =
        newAction(
            uri1,
            /* isRemoveAction= */ false,
            /* data= */ null,
            new RepresentationKey(0, 0, 0),
            new RepresentationKey(1, 1, 1));
    DashDownloadAction action13 =
        newAction(
            uri1,
            /* isRemoveAction= */ false,
            /* data= */ null,
            new RepresentationKey(1, 1, 1),
            new RepresentationKey(0, 0, 0));
    assertEqual(action12, action13);

    DashDownloadAction action14 =
        newAction(
            uri1, /* isRemoveAction= */ false, /* data= */ null, new RepresentationKey(0, 0, 0));
    DashDownloadAction action15 =
        newAction(
            uri1,
            /* isRemoveAction= */ false,
            /* data= */ null,
            new RepresentationKey(1, 1, 1),
            new RepresentationKey(0, 0, 0));
    assertNotEqual(action14, action15);

    DashDownloadAction action16 = newAction(uri1, /* isRemoveAction= */ false, /* data= */ null);
    DashDownloadAction action17 = newAction(uri1, /* isRemoveAction= */ false, /* data= */ null);
    assertEqual(action16, action17);
  }

  @Test
  public void testSerializerGetType() {
    DashDownloadAction action = newAction(uri1, /* isRemoveAction= */ false, /* data= */ null);
    assertThat(action.type).isNotNull();
  }

  @Test
  public void testSerializerWriteRead() throws Exception {
    doTestSerializationRoundTrip(newAction(uri1, /* isRemoveAction= */ false, /* data= */ null));
    doTestSerializationRoundTrip(newAction(uri1, /* isRemoveAction= */ true, /* data= */ null));
    doTestSerializationRoundTrip(
        newAction(
            uri2,
            /* isRemoveAction= */ false,
            /* data= */ null,
            new RepresentationKey(0, 0, 0),
            new RepresentationKey(1, 1, 1)));
  }

  private static void assertNotEqual(DashDownloadAction action1, DashDownloadAction action2) {
    assertThat(action1).isNotEqualTo(action2);
    assertThat(action2).isNotEqualTo(action1);
  }

  private static void assertEqual(DashDownloadAction action1, DashDownloadAction action2) {
    assertThat(action1).isEqualTo(action2);
    assertThat(action2).isEqualTo(action1);
  }

  private static void doTestSerializationRoundTrip(DashDownloadAction action) throws IOException {
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

  private static DashDownloadAction newAction(
      Uri uri, boolean isRemoveAction, @Nullable byte[] data, RepresentationKey... keys) {
    ArrayList<RepresentationKey> keysList = new ArrayList<>();
    Collections.addAll(keysList, keys);
    return new DashDownloadAction(uri, isRemoveAction, data, keysList);
  }
}
