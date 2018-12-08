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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link DownloadActionUtil} class. */
@RunWith(RobolectricTestRunner.class)
public class DownloadActionUtilTest {
  private Uri uri1;
  private Uri uri2;

  @Before
  public void setUp() throws Exception {
    uri1 = Uri.parse("http://abc.com/media1");
    uri2 = Uri.parse("http://abc.com/media2");
  }

  @Test
  public void mergeActions_ifQueueEmpty_throwsException() {
    try {
      DownloadActionUtil.mergeActions(toActionQueue());
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  @Test
  public void mergeActions_ifOneActionInQueue_returnsTheSameAction() {
    DownloadAction action = createDownloadAction(uri1);

    assertThat(DownloadActionUtil.mergeActions(toActionQueue(action))).isEqualTo(action);
  }

  @Test
  public void mergeActions_ifActionsHaveDifferentType_throwsException() {
    DownloadAction downloadAction1 =
        DownloadAction.createDownloadAction(
            DownloadAction.TYPE_PROGRESSIVE,
            uri1,
            Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ null);
    DownloadAction downloadAction2 =
        DownloadAction.createDownloadAction(
            DownloadAction.TYPE_DASH,
            uri1,
            Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ null);
    ArrayDeque<DownloadAction> actionQueue = toActionQueue(downloadAction1, downloadAction2);

    try {
      DownloadActionUtil.mergeActions(actionQueue);
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  @Test
  public void mergeActions_ifActionsHaveDifferentCacheKeys_throwsException() {
    DownloadAction downloadAction1 =
        DownloadAction.createDownloadAction(
            DownloadAction.TYPE_PROGRESSIVE,
            uri1,
            Collections.emptyList(),
            /* customCacheKey= */ "cacheKey1",
            /* data= */ null);
    DownloadAction downloadAction2 =
        DownloadAction.createDownloadAction(
            DownloadAction.TYPE_PROGRESSIVE,
            uri1,
            Collections.emptyList(),
            /* customCacheKey= */ "cacheKey2",
            /* data= */ null);
    ArrayDeque<DownloadAction> actionQueue = toActionQueue(downloadAction1, downloadAction2);

    try {
      DownloadActionUtil.mergeActions(actionQueue);
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  @Test
  public void mergeActions_nullCacheKeyAndDifferentUrl_throwsException() {
    DownloadAction downloadAction1 =
        DownloadAction.createDownloadAction(
            DownloadAction.TYPE_PROGRESSIVE,
            uri1,
            Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ null);
    DownloadAction downloadAction2 =
        DownloadAction.createDownloadAction(
            DownloadAction.TYPE_PROGRESSIVE,
            uri2,
            Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ null);
    ArrayDeque<DownloadAction> actionQueue = toActionQueue(downloadAction1, downloadAction2);

    try {
      DownloadActionUtil.mergeActions(actionQueue);
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  @Test
  public void mergeActions_sameCacheKeyAndDifferentUrl_latterUrlUsed() {
    DownloadAction downloadAction1 =
        DownloadAction.createDownloadAction(
            DownloadAction.TYPE_PROGRESSIVE,
            uri1,
            Collections.emptyList(),
            /* customCacheKey= */ "cacheKey1",
            /* data= */ null);
    DownloadAction downloadAction2 =
        DownloadAction.createDownloadAction(
            DownloadAction.TYPE_PROGRESSIVE,
            uri2,
            Collections.emptyList(),
            /* customCacheKey= */ "cacheKey1",
            /* data= */ null);
    ArrayDeque<DownloadAction> actionQueue = toActionQueue(downloadAction1, downloadAction2);

    DownloadActionUtil.mergeActions(actionQueue);

    DownloadAction mergedAction = DownloadActionUtil.mergeActions(actionQueue);

    assertThat(mergedAction.uri).isEqualTo(uri2);
  }

  @Test
  public void mergeActions_differentData_latterDataUsed() {
    byte[] data1 = "data1".getBytes();
    DownloadAction downloadAction1 =
        DownloadAction.createDownloadAction(
            DownloadAction.TYPE_PROGRESSIVE,
            uri1,
            Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ data1);
    byte[] data2 = "data2".getBytes();
    DownloadAction downloadAction2 =
        DownloadAction.createDownloadAction(
            DownloadAction.TYPE_PROGRESSIVE,
            uri1,
            Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ data2);
    ArrayDeque<DownloadAction> actionQueue = toActionQueue(downloadAction1, downloadAction2);

    DownloadActionUtil.mergeActions(actionQueue);

    DownloadAction mergedAction = DownloadActionUtil.mergeActions(actionQueue);

    assertThat(mergedAction.data).isEqualTo(data2);
  }

  @Test
  public void mergeActions_ifRemoveActionLast_returnsRemoveAction() {
    DownloadAction downloadAction = createDownloadAction(uri1);
    DownloadAction removeAction = createRemoveAction(uri1);
    ArrayDeque<DownloadAction> actionQueue = toActionQueue(downloadAction, removeAction);

    DownloadAction action = DownloadActionUtil.mergeActions(actionQueue);

    assertThat(action).isEqualTo(removeAction);
    assertThat(actionQueue).containsExactly(removeAction);
  }

  @Test
  public void mergeActions_downloadActionAfterRemove_returnsRemoveKeepsDownload() {
    DownloadAction removeAction = createRemoveAction(uri1);
    DownloadAction downloadAction = createDownloadAction(uri1);
    ArrayDeque<DownloadAction> actionQueue = toActionQueue(removeAction, downloadAction);

    DownloadAction action = DownloadActionUtil.mergeActions(actionQueue);

    assertThat(action).isEqualTo(removeAction);
    assertThat(actionQueue).containsExactly(removeAction, downloadAction);
  }

  @Test
  public void mergeActions_downloadActionsAfterRemove_returnsRemoveMergesDownloads() {
    DownloadAction removeAction = createRemoveAction(uri1);
    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    DownloadAction downloadAction1 =
        createDownloadAction(uri1, Collections.singletonList(streamKey1));
    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);
    DownloadAction downloadAction2 =
        createDownloadAction(uri1, Collections.singletonList(streamKey2));
    ArrayDeque<DownloadAction> actionQueue =
        toActionQueue(removeAction, downloadAction1, downloadAction2);
    DownloadAction mergedDownloadAction =
        createDownloadAction(uri1, Arrays.asList(streamKey1, streamKey2));

    DownloadAction action = DownloadActionUtil.mergeActions(actionQueue);

    assertThat(action).isEqualTo(removeAction);
    assertThat(actionQueue).containsExactly(removeAction, mergedDownloadAction);
  }

  @Test
  public void mergeActions_actionBeforeRemove_ignoresActionBeforeRemove() {
    DownloadAction removeAction = createRemoveAction(uri1);
    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    DownloadAction downloadAction1 =
        createDownloadAction(uri1, Collections.singletonList(streamKey1));
    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);
    DownloadAction downloadAction2 =
        createDownloadAction(uri1, Collections.singletonList(streamKey2));
    StreamKey streamKey3 = new StreamKey(/* groupIndex= */ 2, /* trackIndex= */ 2);
    DownloadAction downloadAction3 =
        createDownloadAction(uri1, Collections.singletonList(streamKey3));
    ArrayDeque<DownloadAction> actionQueue =
        toActionQueue(downloadAction1, removeAction, downloadAction2, downloadAction3);
    DownloadAction mergedDownloadAction =
        createDownloadAction(uri1, Arrays.asList(streamKey2, streamKey3));

    DownloadAction action = DownloadActionUtil.mergeActions(actionQueue);

    assertThat(action).isEqualTo(removeAction);
    assertThat(actionQueue).containsExactly(removeAction, mergedDownloadAction);
  }

  @Test
  public void mergeActions_returnsMergedAction() {
    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);
    StreamKey[] keys1 = new StreamKey[] {streamKey1};
    StreamKey[] keys2 = new StreamKey[] {streamKey2};
    StreamKey[] expectedKeys = new StreamKey[] {streamKey1, streamKey2};

    doTestMergeActionsReturnsMergedKeys(keys1, keys2, expectedKeys);
  }

  @Test
  public void mergeActions_returnsUniqueKeys() {
    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    StreamKey streamKey1Copy = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);
    StreamKey[] keys1 = new StreamKey[] {streamKey1};
    StreamKey[] keys2 = new StreamKey[] {streamKey2, streamKey1Copy};
    StreamKey[] expectedKeys = new StreamKey[] {streamKey1, streamKey2};

    doTestMergeActionsReturnsMergedKeys(keys1, keys2, expectedKeys);
  }

  @Test
  public void mergeActions_ifFirstActionKeysEmpty_returnsEmptyKeys() {
    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);
    StreamKey[] keys1 = new StreamKey[] {};
    StreamKey[] keys2 = new StreamKey[] {streamKey2, streamKey1};
    StreamKey[] expectedKeys = new StreamKey[] {};

    doTestMergeActionsReturnsMergedKeys(keys1, keys2, expectedKeys);
  }

  @Test
  public void mergeActions_ifNotFirstActionKeysEmpty_returnsEmptyKeys() {
    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);
    StreamKey[] keys1 = new StreamKey[] {streamKey2, streamKey1};
    StreamKey[] keys2 = new StreamKey[] {};
    StreamKey[] expectedKeys = new StreamKey[] {};

    doTestMergeActionsReturnsMergedKeys(keys1, keys2, expectedKeys);
  }

  private void doTestMergeActionsReturnsMergedKeys(
      StreamKey[] keys1, StreamKey[] keys2, StreamKey[] expectedKeys) {
    DownloadAction action1 = createDownloadAction(uri1, Arrays.asList(keys1));
    DownloadAction action2 = createDownloadAction(uri1, Arrays.asList(keys2));
    ArrayDeque<DownloadAction> actionQueue = toActionQueue(action1, action2);

    DownloadAction mergedAction = DownloadActionUtil.mergeActions(actionQueue);

    assertThat(mergedAction.type).isEqualTo(action1.type);
    assertThat(mergedAction.uri).isEqualTo(action1.uri);
    assertThat(mergedAction.customCacheKey).isEqualTo(action1.customCacheKey);
    assertThat(mergedAction.isRemoveAction).isEqualTo(action1.isRemoveAction);
    assertThat(mergedAction.keys).containsExactly((Object[]) expectedKeys);
    assertThat(actionQueue).containsExactly(mergedAction);
  }

  private ArrayDeque<DownloadAction> toActionQueue(DownloadAction... actions) {
    return new ArrayDeque<>(Arrays.asList(actions));
  }

  private static DownloadAction createDownloadAction(Uri uri) {
    return createDownloadAction(uri, Collections.emptyList());
  }

  private static DownloadAction createDownloadAction(Uri uri, List<StreamKey> keys) {
    return DownloadAction.createDownloadAction(
        DownloadAction.TYPE_PROGRESSIVE, uri, keys, /* customCacheKey= */ null, /* data= */ null);
  }

  private static DownloadAction createRemoveAction(Uri uri) {
    return DownloadAction.createRemoveAction(
        DownloadAction.TYPE_PROGRESSIVE, uri, /* customCacheKey= */ null);
  }
}
