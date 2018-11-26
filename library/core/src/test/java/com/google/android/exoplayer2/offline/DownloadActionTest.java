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
package com.google.android.exoplayer2.offline;

import static com.google.android.exoplayer2.offline.DownloadAction.TYPE_DASH;
import static com.google.android.exoplayer2.offline.DownloadAction.TYPE_HLS;
import static com.google.android.exoplayer2.offline.DownloadAction.TYPE_PROGRESSIVE;
import static com.google.android.exoplayer2.offline.DownloadAction.TYPE_SS;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Unit tests for {@link DownloadAction}. */
@RunWith(RobolectricTestRunner.class)
public class DownloadActionTest {

  private Uri uri1;
  private Uri uri2;
  private byte[] data;

  @Before
  public void setUp() {
    uri1 = Uri.parse("http://test/1.uri");
    uri2 = Uri.parse("http://test/2.uri");
    data = TestUtil.buildTestData(32);
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
  public void testSameUri_IsSameMedia() {
    DownloadAction action1 = createDownloadAction(uri1);
    DownloadAction action2 = createDownloadAction(uri1);
    assertThat(action1.isSameMedia(action2)).isTrue();
  }

  @Test
  public void testSameUriDifferentAction_IsSameMedia() {
    DownloadAction action1 = createDownloadAction(uri1);
    DownloadAction action2 = createRemoveAction(uri1);
    assertThat(action1.isSameMedia(action2)).isTrue();
  }

  @Test
  public void testDifferentUri_IsNotSameMedia() {
    DownloadAction action1 = createDownloadAction(uri1);
    DownloadAction action2 = createDownloadAction(uri2);
    assertThat(action1.isSameMedia(action2)).isFalse();
  }

  @Test
  public void testSameCacheKeyDifferentUri_IsSameMedia() {
    DownloadAction action1 = DownloadAction.createRemoveAction(TYPE_DASH, uri1, "key123");
    DownloadAction action2 = DownloadAction.createRemoveAction(TYPE_DASH, uri2, "key123");
    assertThat(action1.isSameMedia(action2)).isTrue();
  }

  @Test
  public void testDifferentCacheDifferentUri_IsNotSameMedia() {
    DownloadAction action1 = DownloadAction.createRemoveAction(TYPE_DASH, uri1, "key123");
    DownloadAction action2 = DownloadAction.createRemoveAction(TYPE_DASH, uri2, "key456");
    assertThat(action1.isSameMedia(action2)).isFalse();
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

    DownloadAction action8 = createDownloadAction(uri1, new StreamKey(0, 1, 1));
    DownloadAction action9 = createDownloadAction(uri1, new StreamKey(0, 0, 0));
    assertNotEqual(action8, action9);

    DownloadAction action10 = createRemoveAction(uri1);
    DownloadAction action11 = createRemoveAction(uri2);
    assertNotEqual(action10, action11);

    DownloadAction action12 =
        createDownloadAction(uri1, new StreamKey(0, 0, 0), new StreamKey(0, 1, 1));
    DownloadAction action13 =
        createDownloadAction(uri1, new StreamKey(0, 1, 1), new StreamKey(0, 0, 0));
    assertEqual(action12, action13);

    DownloadAction action14 = createDownloadAction(uri1, new StreamKey(0, 0, 0));
    DownloadAction action15 =
        createDownloadAction(uri1, new StreamKey(0, 1, 1), new StreamKey(0, 0, 0));
    assertNotEqual(action14, action15);

    DownloadAction action16 = createDownloadAction(uri1);
    DownloadAction action17 = createDownloadAction(uri1);
    assertEqual(action16, action17);
  }

  @Test
  public void testSerializerWriteRead() throws Exception {
    assertStreamSerializationRoundTrip(
        DownloadAction.createDownloadAction(
            TYPE_DASH,
            uri1,
            toList(new StreamKey(0, 1, 2), new StreamKey(3, 4, 5)),
            "key123",
            data));
    assertStreamSerializationRoundTrip(
        DownloadAction.createRemoveAction(TYPE_DASH, uri1, "key123"));
  }

  @Test
  public void testArraySerialization() throws Exception {
    assertArraySerializationRoundTrip(
        DownloadAction.createDownloadAction(
            TYPE_DASH,
            uri1,
            toList(new StreamKey(0, 1, 2), new StreamKey(3, 4, 5)),
            "key123",
            data));
    assertArraySerializationRoundTrip(DownloadAction.createRemoveAction(TYPE_DASH, uri1, "key123"));
  }

  @Test
  public void testSerializerProgressiveVersion0() throws Exception {
    assertDeserialization(
        "progressive-download-v0",
        DownloadAction.createDownloadAction(
            TYPE_PROGRESSIVE, uri1, Collections.emptyList(), "key123", data));
    assertDeserialization(
        "progressive-remove-v0",
        DownloadAction.createRemoveAction(TYPE_PROGRESSIVE, uri1, "key123"));
  }

  @Test
  public void testSerializerDashVersion0() throws Exception {
    assertDeserialization(
        "dash-download-v0",
        DownloadAction.createDownloadAction(
            TYPE_DASH,
            uri1,
            toList(new StreamKey(0, 1, 2), new StreamKey(3, 4, 5)),
            /* customCacheKey= */ null,
            data));
    assertDeserialization(
        "dash-remove-v0",
        DownloadAction.createRemoveAction(TYPE_DASH, uri1, /* customCacheKey= */ null));
  }

  @Test
  public void testSerializerHlsVersion0() throws Exception {
    assertDeserialization(
        "hls-download-v0",
        DownloadAction.createDownloadAction(
            TYPE_HLS,
            uri1,
            toList(new StreamKey(0, 1), new StreamKey(2, 3)),
            /* customCacheKey= */ null,
            data));
    assertDeserialization(
        "hls-remove-v0",
        DownloadAction.createRemoveAction(TYPE_HLS, uri1, /* customCacheKey= */ null));
  }

  @Test
  public void testSerializerHlsVersion1() throws Exception {
    assertDeserialization(
        "hls-download-v1",
        DownloadAction.createDownloadAction(
            TYPE_HLS,
            uri1,
            toList(new StreamKey(0, 1, 2), new StreamKey(3, 4, 5)),
            /* customCacheKey= */ null,
            data));
    assertDeserialization(
        "hls-remove-v1",
        DownloadAction.createRemoveAction(TYPE_HLS, uri1, /* customCacheKey= */ null));
  }

  @Test
  public void testSerializerSsVersion0() throws Exception {
    assertDeserialization(
        "ss-download-v0",
        DownloadAction.createDownloadAction(
            TYPE_SS,
            uri1,
            toList(new StreamKey(0, 1), new StreamKey(2, 3)),
            /* customCacheKey= */ null,
            data));
    assertDeserialization(
        "ss-remove-v0",
        DownloadAction.createRemoveAction(TYPE_SS, uri1, /* customCacheKey= */ null));
  }

  @Test
  public void testSerializerSsVersion1() throws Exception {
    assertDeserialization(
        "ss-download-v1",
        DownloadAction.createDownloadAction(
            TYPE_SS,
            uri1,
            toList(new StreamKey(0, 1, 2), new StreamKey(3, 4, 5)),
            /* customCacheKey= */ null,
            data));
    assertDeserialization(
        "ss-remove-v1",
        DownloadAction.createRemoveAction(TYPE_SS, uri1, /* customCacheKey= */ null));
  }

  private DownloadAction createDownloadAction(Uri uri, StreamKey... keys) {
    return DownloadAction.createDownloadAction(
        TYPE_DASH, uri, toList(keys), /* customCacheKey= */ null, data);
  }

  private DownloadAction createRemoveAction(Uri uri) {
    return DownloadAction.createRemoveAction(TYPE_DASH, uri, /* customCacheKey= */ null);
  }

  private static void assertNotEqual(DownloadAction action1, DownloadAction action2) {
    assertThat(action1).isNotEqualTo(action2);
    assertThat(action2).isNotEqualTo(action1);
  }

  private static void assertEqual(DownloadAction action1, DownloadAction action2) {
    assertThat(action1).isEqualTo(action2);
    assertThat(action2).isEqualTo(action1);
  }

  private static void assertStreamSerializationRoundTrip(DownloadAction action) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream(out);
    action.serializeToStream(output);

    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    DataInputStream input = new DataInputStream(in);
    DownloadAction deserializedAction = DownloadAction.deserializeFromStream(input);

    assertEqual(action, deserializedAction);
  }

  private static void assertArraySerializationRoundTrip(DownloadAction action) throws IOException {
    assertEqual(action, DownloadAction.fromByteArray(action.toByteArray()));
  }

  private static void assertDeserialization(String fileName, DownloadAction expectedAction)
      throws IOException {
    InputStream input =
        TestUtil.getInputStream(RuntimeEnvironment.application, "download-actions/" + fileName);
    DownloadAction deserializedAction = DownloadAction.deserializeFromStream(input);

    assertEqual(deserializedAction, expectedAction);
  }

  private static List<StreamKey> toList(StreamKey... keys) {
    ArrayList<StreamKey> keysList = new ArrayList<>();
    Collections.addAll(keysList, keys);
    return keysList;
  }
}
