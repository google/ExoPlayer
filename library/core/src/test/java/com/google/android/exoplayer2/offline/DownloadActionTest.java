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
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
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

  @Before
  public void setUp() {
    uri1 = Uri.parse("http://test/1.uri");
    uri2 = Uri.parse("http://test/2.uri");
  }

  @Test
  public void testMergeActions_withDifferentIds_fails() {
    DownloadAction action1 =
        new DownloadAction(
            "id1",
            TYPE_DASH,
            uri1,
            /* streamKeys= */ Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ null);
    DownloadAction action2 =
        new DownloadAction(
            "id2",
            TYPE_DASH,
            uri2,
            /* streamKeys= */ Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ null);
    try {
      action1.copyWithMergedAction(action2);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void testMergeActions_withDifferentTypes_fails() {
    DownloadAction action1 =
        new DownloadAction(
            "id1",
            TYPE_DASH,
            uri1,
            /* streamKeys= */ Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ null);
    DownloadAction action2 =
        new DownloadAction(
            "id1",
            TYPE_HLS,
            uri1,
            /* streamKeys= */ Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ null);
    try {
      action1.copyWithMergedAction(action2);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void testMergeAction_withSameAction() {
    DownloadAction action1 = createAction(uri1, new StreamKey(0, 0, 0));

    DownloadAction mergedAction = action1.copyWithMergedAction(action1);
    assertEqual(action1, mergedAction);
  }

  @Test
  public void testMergeActions_withEmptyStreamKeys() {
    DownloadAction action1 = createAction(uri1, new StreamKey(0, 0, 0));
    DownloadAction action2 = createAction(uri1);

    // If either of the actions have empty streamKeys, the merge should have empty streamKeys.
    DownloadAction mergedAction = action1.copyWithMergedAction(action2);
    assertThat(mergedAction.streamKeys).isEmpty();

    mergedAction = action2.copyWithMergedAction(action1);
    assertThat(mergedAction.streamKeys).isEmpty();
  }

  @Test
  public void testMergeActions_withOverlappingStreamKeys() {
    StreamKey streamKey1 = new StreamKey(0, 1, 2);
    StreamKey streamKey2 = new StreamKey(3, 4, 5);
    StreamKey streamKey3 = new StreamKey(6, 7, 8);
    DownloadAction action1 = createAction(uri1, streamKey1, streamKey2);
    DownloadAction action2 = createAction(uri1, streamKey2, streamKey3);

    // Merged streamKeys should be in their original order without duplicates.
    DownloadAction mergedAction = action1.copyWithMergedAction(action2);
    assertThat(mergedAction.streamKeys).containsExactly(streamKey1, streamKey2, streamKey3);

    mergedAction = action2.copyWithMergedAction(action1);
    assertThat(mergedAction.streamKeys).containsExactly(streamKey2, streamKey3, streamKey1);
  }

  @Test
  public void testMergeActions_withDifferentFields() {
    byte[] data1 = new byte[] {0, 1, 2};
    byte[] data2 = new byte[] {3, 4, 5};
    DownloadAction action1 =
        new DownloadAction(
            "id1",
            TYPE_DASH,
            uri1,
            /* streamKeys= */ Collections.emptyList(),
            "key1",
            /* data= */ data1);
    DownloadAction action2 =
        new DownloadAction(
            "id1",
            TYPE_DASH,
            uri2,
            /* streamKeys= */ Collections.emptyList(),
            "key2",
            /* data= */ data2);

    // uri, customCacheKey and data should be from the action being merged.
    DownloadAction mergedAction = action1.copyWithMergedAction(action2);
    assertThat(mergedAction.uri).isEqualTo(uri2);
    assertThat(mergedAction.customCacheKey).isEqualTo("key2");
    assertThat(mergedAction.data).isEqualTo(data2);

    mergedAction = action2.copyWithMergedAction(action1);
    assertThat(mergedAction.uri).isEqualTo(uri1);
    assertThat(mergedAction.customCacheKey).isEqualTo("key1");
    assertThat(mergedAction.data).isEqualTo(data1);
  }

  @Test
  public void testParcelable() {
    ArrayList<StreamKey> streamKeys = new ArrayList<>();
    streamKeys.add(new StreamKey(1, 2, 3));
    streamKeys.add(new StreamKey(4, 5, 6));
    DownloadAction actionToParcel =
        new DownloadAction(
            "id",
            "type",
            Uri.parse("https://abc.def/ghi"),
            streamKeys,
            "key",
            new byte[] {1, 2, 3, 4, 5});
    Parcel parcel = Parcel.obtain();
    actionToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    DownloadAction actionFromParcel = DownloadAction.CREATOR.createFromParcel(parcel);
    assertThat(actionFromParcel).isEqualTo(actionToParcel);

    parcel.recycle();
  }

  @SuppressWarnings("EqualsWithItself")
  @Test
  public void testEquals() {
    DownloadAction action1 = createAction(uri1);
    assertThat(action1.equals(action1)).isTrue();

    DownloadAction action2 = createAction(uri1);
    DownloadAction action3 = createAction(uri1);
    assertEqual(action2, action3);

    DownloadAction action4 = createAction(uri1);
    DownloadAction action5 = createAction(uri1, new StreamKey(0, 0, 0));
    assertNotEqual(action4, action5);

    DownloadAction action6 = createAction(uri1, new StreamKey(0, 1, 1));
    DownloadAction action7 = createAction(uri1, new StreamKey(0, 0, 0));
    assertNotEqual(action6, action7);

    DownloadAction action8 = createAction(uri1);
    DownloadAction action9 = createAction(uri2);
    assertNotEqual(action8, action9);

    DownloadAction action10 = createAction(uri1, new StreamKey(0, 0, 0), new StreamKey(0, 1, 1));
    DownloadAction action11 = createAction(uri1, new StreamKey(0, 1, 1), new StreamKey(0, 0, 0));
    assertEqual(action10, action11);

    DownloadAction action12 = createAction(uri1, new StreamKey(0, 0, 0));
    DownloadAction action13 = createAction(uri1, new StreamKey(0, 1, 1), new StreamKey(0, 0, 0));
    assertNotEqual(action12, action13);

    DownloadAction action14 = createAction(uri1);
    DownloadAction action15 = createAction(uri1);
    assertEqual(action14, action15);
  }

  private static void assertNotEqual(DownloadAction action1, DownloadAction action2) {
    assertThat(action1).isNotEqualTo(action2);
    assertThat(action2).isNotEqualTo(action1);
  }

  private static void assertEqual(DownloadAction action1, DownloadAction action2) {
    assertThat(action1).isEqualTo(action2);
    assertThat(action2).isEqualTo(action1);
  }

  private static DownloadAction createAction(Uri uri, StreamKey... keys) {
    return new DownloadAction(
        uri.toString(), TYPE_DASH, uri, toList(keys), /* customCacheKey= */ null, /* data= */ null);
  }

  private static List<StreamKey> toList(StreamKey... keys) {
    ArrayList<StreamKey> keysList = new ArrayList<>();
    Collections.addAll(keysList, keys);
    return keysList;
  }
}
