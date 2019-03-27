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
import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.offline.DownloadAction.UnsupportedActionException;
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

/** Unit tests for {@link DownloadAction}. */
@RunWith(AndroidJUnit4.class)
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
  public void testSameUri_hasSameId() {
    DownloadAction action1 = createAction(uri1);
    DownloadAction action2 = createAction(uri1);
    assertThat(action1.id.equals(action2.id)).isTrue();
  }

  @Test
  public void testSameUriDifferentAction_hasSameId() {
    DownloadAction action1 = createAction(uri1);
    DownloadAction action2 = createAction(uri1);
    assertThat(action1.id.equals(action2.id)).isTrue();
  }

  @Test
  public void testDifferentUri_IsNotSameMedia() {
    DownloadAction action1 = createAction(uri1);
    DownloadAction action2 = createAction(uri2);
    assertThat(action1.id.equals(action2.id)).isFalse();
  }

  @Test
  public void testSameCacheKeyDifferentUri_hasSameId() {
    DownloadAction action1 = createAction(uri1, "key123");
    DownloadAction action2 = createAction(uri2, "key123");
    assertThat(action1.id.equals(action2.id)).isTrue();
  }

  @Test
  public void testDifferentCacheKeyDifferentUri_hasDifferentId() {
    DownloadAction action1 = createAction(uri1, "key123");
    DownloadAction action2 = createAction(uri2, "key456");
    assertThat(action1.id.equals(action2.id)).isFalse();
  }

  @SuppressWarnings("EqualsWithItself")
  @Test
  public void testEquals() {
    DownloadAction action1 = createAction(uri1);
    assertThat(action1.equals(action1)).isTrue();

    DownloadAction action2 = createAction(uri1);
    DownloadAction action3 = createAction(uri1);
    assertEqual(action2, action3);

    DownloadAction action6 = createAction(uri1);
    DownloadAction action7 = createAction(uri1, new StreamKey(0, 0, 0));
    assertNotEqual(action6, action7);

    DownloadAction action8 = createAction(uri1, new StreamKey(0, 1, 1));
    DownloadAction action9 = createAction(uri1, new StreamKey(0, 0, 0));
    assertNotEqual(action8, action9);

    DownloadAction action10 = createAction(uri1);
    DownloadAction action11 = createAction(uri2);
    assertNotEqual(action10, action11);

    DownloadAction action12 = createAction(uri1, new StreamKey(0, 0, 0), new StreamKey(0, 1, 1));
    DownloadAction action13 = createAction(uri1, new StreamKey(0, 1, 1), new StreamKey(0, 0, 0));
    assertEqual(action12, action13);

    DownloadAction action14 = createAction(uri1, new StreamKey(0, 0, 0));
    DownloadAction action15 = createAction(uri1, new StreamKey(0, 1, 1), new StreamKey(0, 0, 0));
    assertNotEqual(action14, action15);

    DownloadAction action16 = createAction(uri1);
    DownloadAction action17 = createAction(uri1);
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
    assertStreamSerializationRoundTrip(createAction(uri1, "key123"));
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
    assertArraySerializationRoundTrip(createAction(uri1, "key123"));
  }

  @Test
  public void testSerializerProgressiveVersion0() throws Exception {
    assertDeserialization(
        "progressive-download-v0",
        DownloadAction.createDownloadAction(
            TYPE_PROGRESSIVE, uri1, Collections.emptyList(), "key123", data));
    assertUnsupportedAction("progressive-remove-v0");
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
    assertUnsupportedAction("dash-remove-v0");
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
    assertUnsupportedAction("hls-remove-v0");
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
    assertUnsupportedAction("hls-remove-v1");
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
    assertUnsupportedAction("ss-remove-v0");
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
    assertUnsupportedAction("ss-remove-v1");
  }

  private DownloadAction createAction(Uri uri, StreamKey... keys) {
    return DownloadAction.createDownloadAction(
        TYPE_DASH, uri, toList(keys), /* customCacheKey= */ null, data);
  }

  private DownloadAction createAction(Uri uri, @Nullable String customCacheKey) {
    return DownloadAction.createDownloadAction(
        DownloadAction.TYPE_DASH, uri, Collections.emptyList(), customCacheKey, /* data= */ null);
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
        TestUtil.getInputStream(
            ApplicationProvider.getApplicationContext(), "download-actions/" + fileName);
    DownloadAction deserializedAction = DownloadAction.deserializeFromStream(input);

    assertEqual(deserializedAction, expectedAction);
  }

  private static void assertUnsupportedAction(String fileName) throws IOException {
    InputStream input =
        TestUtil.getInputStream(
            ApplicationProvider.getApplicationContext(), "download-actions/" + fileName);
    try {
      DownloadAction.deserializeFromStream(input);
      fail();
    } catch (UnsupportedActionException e) {
      // Expected exception.
    }
  }

  private static List<StreamKey> toList(StreamKey... keys) {
    ArrayList<StreamKey> keysList = new ArrayList<>();
    Collections.addAll(keysList, keys);
    return keysList;
  }
}
