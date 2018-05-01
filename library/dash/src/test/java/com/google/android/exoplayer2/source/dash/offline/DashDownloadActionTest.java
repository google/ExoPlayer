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
import com.google.android.exoplayer2.source.dash.manifest.RepresentationKey;
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
  public void testDownloadActionIsNotRemoveAction() throws Exception {
    DashDownloadAction action =
        new DashDownloadAction(/* isRemoveAction= */ false, /* data= */ null, uri1);
    assertThat(action.isRemoveAction).isFalse();
  }

  @Test
  public void testRemoveActionisRemoveAction() throws Exception {
    DashDownloadAction action2 =
        new DashDownloadAction(/* isRemoveAction= */ true, /* data= */ null, uri1);
    assertThat(action2.isRemoveAction).isTrue();
  }

  @Test
  public void testCreateDownloader() throws Exception {
    MockitoAnnotations.initMocks(this);
    DashDownloadAction action =
        new DashDownloadAction(/* isRemoveAction= */ false, /* data= */ null, uri1);
    DownloaderConstructorHelper constructorHelper = new DownloaderConstructorHelper(
        Mockito.mock(Cache.class), DummyDataSource.FACTORY);
    assertThat(action.createDownloader(constructorHelper)).isNotNull();
  }

  @Test
  public void testSameUriDifferentAction_IsSameMedia() throws Exception {
    DashDownloadAction action1 =
        new DashDownloadAction(/* isRemoveAction= */ true, /* data= */ null, uri1);
    DashDownloadAction action2 =
        new DashDownloadAction(/* isRemoveAction= */ false, /* data= */ null, uri1);
    assertThat(action1.isSameMedia(action2)).isTrue();
  }

  @Test
  public void testDifferentUriAndAction_IsNotSameMedia() throws Exception {
    DashDownloadAction action3 =
        new DashDownloadAction(/* isRemoveAction= */ true, /* data= */ null, uri2);
    DashDownloadAction action4 =
        new DashDownloadAction(/* isRemoveAction= */ false, /* data= */ null, uri1);
    assertThat(action3.isSameMedia(action4)).isFalse();
  }

  @SuppressWarnings("EqualsWithItself")
  @Test
  public void testEquals() throws Exception {
    DashDownloadAction action1 =
        new DashDownloadAction(/* isRemoveAction= */ true, /* data= */ null, uri1);
    assertThat(action1.equals(action1)).isTrue();

    DashDownloadAction action2 =
        new DashDownloadAction(/* isRemoveAction= */ true, /* data= */ null, uri1);
    DashDownloadAction action3 =
        new DashDownloadAction(/* isRemoveAction= */ true, /* data= */ null, uri1);
    assertEqual(action2, action3);

    DashDownloadAction action4 =
        new DashDownloadAction(/* isRemoveAction= */ true, /* data= */ null, uri1);
    DashDownloadAction action5 =
        new DashDownloadAction(/* isRemoveAction= */ false, /* data= */ null, uri1);
    assertNotEqual(action4, action5);

    DashDownloadAction action6 =
        new DashDownloadAction(/* isRemoveAction= */ false, /* data= */ null, uri1);
    DashDownloadAction action7 =
        new DashDownloadAction(
            /* isRemoveAction= */ false, /* data= */ null, uri1, new RepresentationKey(0, 0, 0));
    assertNotEqual(action6, action7);

    DashDownloadAction action8 =
        new DashDownloadAction(
            /* isRemoveAction= */ false, /* data= */ null, uri1, new RepresentationKey(1, 1, 1));
    DashDownloadAction action9 =
        new DashDownloadAction(
            /* isRemoveAction= */ false, /* data= */ null, uri1, new RepresentationKey(0, 0, 0));
    assertNotEqual(action8, action9);

    DashDownloadAction action10 =
        new DashDownloadAction(/* isRemoveAction= */ true, /* data= */ null, uri1);
    DashDownloadAction action11 =
        new DashDownloadAction(/* isRemoveAction= */ true, /* data= */ null, uri2);
    assertNotEqual(action10, action11);

    DashDownloadAction action12 =
        new DashDownloadAction(
            /* isRemoveAction= */ false,
            /* data= */ null,
            uri1,
            new RepresentationKey(0, 0, 0),
            new RepresentationKey(1, 1, 1));
    DashDownloadAction action13 =
        new DashDownloadAction(
            /* isRemoveAction= */ false,
            /* data= */ null,
            uri1,
            new RepresentationKey(1, 1, 1),
            new RepresentationKey(0, 0, 0));
    assertEqual(action12, action13);

    DashDownloadAction action14 =
        new DashDownloadAction(
            /* isRemoveAction= */ false, /* data= */ null, uri1, new RepresentationKey(0, 0, 0));
    DashDownloadAction action15 =
        new DashDownloadAction(
            /* isRemoveAction= */ false,
            /* data= */ null,
            uri1,
            new RepresentationKey(1, 1, 1),
            new RepresentationKey(0, 0, 0));
    assertNotEqual(action14, action15);

    DashDownloadAction action16 =
        new DashDownloadAction(/* isRemoveAction= */ false, /* data= */ null, uri1);
    DashDownloadAction action17 =
        new DashDownloadAction(
            /* isRemoveAction= */ false, /* data= */ null, uri1, new RepresentationKey[0]);
    assertEqual(action16, action17);
  }

  @Test
  public void testSerializerGetType() throws Exception {
    DashDownloadAction action =
        new DashDownloadAction(/* isRemoveAction= */ false, /* data= */ null, uri1);
    assertThat(action.type).isNotNull();
  }

  @Test
  public void testSerializerWriteRead() throws Exception {
    doTestSerializationRoundTrip(
        new DashDownloadAction(/* isRemoveAction= */ false, /* data= */ null, uri1));
    doTestSerializationRoundTrip(
        new DashDownloadAction(/* isRemoveAction= */ true, /* data= */ null, uri1));
    doTestSerializationRoundTrip(
        new DashDownloadAction(
            /* isRemoveAction= */ false,
            /* data= */ null,
            uri2,
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

}
