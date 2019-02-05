/*
 * Copyright (C) 2019 The Android Open Source Project
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
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link DownloadState}. */
@RunWith(RobolectricTestRunner.class)
public class DownloadStateTest {

  private Uri testUri;

  @Before
  public void setUp() throws Exception {
    testUri = Uri.parse("https://www.test.com/download1");
  }

  @Test
  public void mergeAction_actionHaveDifferentType_throwsException() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadState downloadState =
        new DownloadStateBuilder(downloadAction)
            .setType(downloadAction.type + "_different")
            .setState(DownloadState.STATE_QUEUED)
            .build();

    try {
      downloadState.mergeAction(downloadAction);
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  @Test
  public void mergeAction_actionHaveDifferentId_throwsException() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadState downloadState =
        new DownloadStateBuilder(downloadAction)
            .setId(downloadAction.id + "_different")
            .setState(DownloadState.STATE_QUEUED)
            .build();

    try {
      downloadState.mergeAction(downloadAction);
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  @Test
  public void mergeAction_actionsWithSameIdAndType_doesNotFail() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(downloadAction).setState(DownloadState.STATE_QUEUED);
    DownloadState downloadState = downloadStateBuilder.build();

    downloadState.mergeAction(downloadAction);
  }

  @Test
  public void mergeAction_actionHaveDifferentUri_downloadStateUriIsUpdated() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(downloadAction)
            .setUri(downloadAction.uri + "_different")
            .setState(DownloadState.STATE_QUEUED);
    DownloadState downloadState = downloadStateBuilder.build();

    DownloadState mergedDownloadState = downloadState.mergeAction(downloadAction);

    DownloadState expectedDownloadState = downloadStateBuilder.setUri(downloadAction.uri).build();
    assertEqual(mergedDownloadState, expectedDownloadState);
  }

  @Test
  public void mergeAction_actionHaveDifferentData_downloadStateDataIsUpdated() {
    DownloadAction downloadAction =
        DownloadAction.createDownloadAction(
            DownloadAction.TYPE_DASH,
            testUri,
            Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ new byte[] {1, 2, 3, 4});
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(downloadAction)
            .setState(DownloadState.STATE_QUEUED)
            .setCustomMetadata(new byte[0]);
    DownloadState downloadState = downloadStateBuilder.build();

    DownloadState mergedDownloadState = downloadState.mergeAction(downloadAction);

    DownloadState expectedDownloadState =
        downloadStateBuilder.setCustomMetadata(downloadAction.data).build();
    assertEqual(mergedDownloadState, expectedDownloadState);
  }

  @Test
  public void mergeAction_queuedDownloadRemoveAction_stateBecomesRemoving() {
    DownloadAction downloadAction = createRemoveAction();
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(downloadAction).setState(DownloadState.STATE_QUEUED);
    DownloadState downloadState = downloadStateBuilder.build();

    DownloadState mergedDownloadState = downloadState.mergeAction(downloadAction);

    DownloadState expectedDownloadState =
        downloadStateBuilder.setState(DownloadState.STATE_REMOVING).build();
    assertEqual(mergedDownloadState, expectedDownloadState);
  }

  @Test
  public void mergeAction_removingDownloadDownloadAction_stateBecomesRestarting() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(downloadAction).setState(DownloadState.STATE_REMOVING);
    DownloadState downloadState = downloadStateBuilder.build();

    DownloadState mergedDownloadState = downloadState.mergeAction(downloadAction);

    DownloadState expectedDownloadState =
        downloadStateBuilder.setState(DownloadState.STATE_RESTARTING).build();
    assertEqual(mergedDownloadState, expectedDownloadState);
  }

  @Test
  public void mergeAction_failedDownloadDownloadAction_stateBecomesQueued() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(downloadAction)
            .setState(DownloadState.STATE_FAILED)
            .setFailureReason(DownloadState.FAILURE_REASON_UNKNOWN);
    DownloadState downloadState = downloadStateBuilder.build();

    DownloadState mergedDownloadState = downloadState.mergeAction(downloadAction);

    DownloadState expectedDownloadState =
        downloadStateBuilder
            .setState(DownloadState.STATE_QUEUED)
            .setFailureReason(DownloadState.FAILURE_REASON_NONE)
            .build();
    assertEqual(mergedDownloadState, expectedDownloadState);
  }

  @Test
  public void mergeAction_stoppedDownloadDownloadAction_stateStaysStopped() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(downloadAction)
            .setState(DownloadState.STATE_STOPPED)
            .setStopFlags(DownloadState.STOP_FLAG_STOPPED);
    DownloadState downloadState = downloadStateBuilder.build();

    DownloadState mergedDownloadState = downloadState.mergeAction(downloadAction);

    assertEqual(mergedDownloadState, downloadState);
  }

  @Test
  public void mergeAction_stoppedDownloadRemoveAction_stateBecomesRemoving() {
    DownloadAction downloadAction = createRemoveAction();
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(downloadAction)
            .setState(DownloadState.STATE_STOPPED)
            .setStopFlags(DownloadState.STOP_FLAG_STOPPED);
    DownloadState downloadState = downloadStateBuilder.build();

    DownloadState mergedDownloadState = downloadState.mergeAction(downloadAction);

    DownloadState expectedDownloadState =
        downloadStateBuilder.setState(DownloadState.STATE_REMOVING).build();
    assertEqual(mergedDownloadState, expectedDownloadState);
  }

  @Test
  public void mergeAction_restartingDownloadRemoveAction_stateBecomesRemoving() {
    DownloadAction downloadAction = createRemoveAction();
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(downloadAction).setState(DownloadState.STATE_RESTARTING);
    DownloadState downloadState = downloadStateBuilder.build();

    DownloadState mergedDownloadState = downloadState.mergeAction(downloadAction);

    DownloadState expectedDownloadState =
        downloadStateBuilder.setState(DownloadState.STATE_REMOVING).build();
    assertEqual(mergedDownloadState, expectedDownloadState);
  }

  @Test
  public void mergeAction_returnsMergedKeys() {
    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);
    StreamKey[] keys1 = new StreamKey[] {streamKey1};
    StreamKey[] keys2 = new StreamKey[] {streamKey2};
    StreamKey[] expectedKeys = new StreamKey[] {streamKey1, streamKey2};

    doTestMergeActionReturnsMergedKeys(keys1, keys2, expectedKeys);
  }

  @Test
  public void mergeAction_returnsUniqueKeys() {
    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    StreamKey streamKey1Copy = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);
    StreamKey[] keys1 = new StreamKey[] {streamKey1};
    StreamKey[] keys2 = new StreamKey[] {streamKey2, streamKey1Copy};
    StreamKey[] expectedKeys = new StreamKey[] {streamKey1, streamKey2};

    doTestMergeActionReturnsMergedKeys(keys1, keys2, expectedKeys);
  }

  @Test
  public void mergeAction_ifFirstActionKeysEmpty_returnsEmptyKeys() {
    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);
    StreamKey[] keys1 = new StreamKey[] {};
    StreamKey[] keys2 = new StreamKey[] {streamKey2, streamKey1};
    StreamKey[] expectedKeys = new StreamKey[] {};

    doTestMergeActionReturnsMergedKeys(keys1, keys2, expectedKeys);
  }

  @Test
  public void mergeAction_ifNotFirstActionKeysEmpty_returnsEmptyKeys() {
    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);
    StreamKey[] keys1 = new StreamKey[] {streamKey2, streamKey1};
    StreamKey[] keys2 = new StreamKey[] {};
    StreamKey[] expectedKeys = new StreamKey[] {};

    doTestMergeActionReturnsMergedKeys(keys1, keys2, expectedKeys);
  }

  private void doTestMergeActionReturnsMergedKeys(
      StreamKey[] keys1, StreamKey[] keys2, StreamKey[] expectedKeys) {
    DownloadAction downloadAction =
        DownloadAction.createDownloadAction(
            DownloadAction.TYPE_DASH,
            testUri,
            Arrays.asList(keys2),
            /* customCacheKey= */ null,
            /* data= */ null);
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(downloadAction)
            .setState(DownloadState.STATE_QUEUED)
            .setStreamKeys(keys1);
    DownloadState downloadState = downloadStateBuilder.build();

    DownloadState mergedDownloadState = downloadState.mergeAction(downloadAction);

    DownloadState expectedDownloadState = downloadStateBuilder.setStreamKeys(expectedKeys).build();
    assertEqual(mergedDownloadState, expectedDownloadState);
  }

  static void assertEqual(DownloadState downloadState, DownloadState expected) {
    assertThat(areEqual(downloadState, expected)).isTrue();
  }

  private static boolean areEqual(DownloadState downloadState, DownloadState that) {
    if (downloadState.state != that.state) {
      return false;
    }
    if (Float.compare(that.downloadPercentage, downloadState.downloadPercentage) != 0) {
      return false;
    }
    if (downloadState.downloadedBytes != that.downloadedBytes) {
      return false;
    }
    if (downloadState.totalBytes != that.totalBytes) {
      return false;
    }
    if (downloadState.startTimeMs != that.startTimeMs) {
      return false;
    }
    if (downloadState.updateTimeMs != that.updateTimeMs) {
      return false;
    }
    if (downloadState.failureReason != that.failureReason) {
      return false;
    }
    if (downloadState.stopFlags != that.stopFlags) {
      return false;
    }
    if (downloadState.notMetRequirements != that.notMetRequirements) {
      return false;
    }
    if (!downloadState.id.equals(that.id)) {
      return false;
    }
    if (!downloadState.type.equals(that.type)) {
      return false;
    }
    if (!downloadState.uri.equals(that.uri)) {
      return false;
    }
    if (downloadState.cacheKey != null
        ? !downloadState.cacheKey.equals(that.cacheKey)
        : that.cacheKey != null) {
      return false;
    }
    if (downloadState.streamKeys.length != that.streamKeys.length
        || !Arrays.asList(downloadState.streamKeys).containsAll(Arrays.asList(that.streamKeys))) {
      return false;
    }
    return Arrays.equals(downloadState.customMetadata, that.customMetadata);
  }

  private DownloadAction createDownloadAction() {
    return DownloadAction.createDownloadAction(
        DownloadAction.TYPE_DASH,
        testUri,
        Collections.emptyList(),
        /* customCacheKey= */ null,
        /* data= */ null);
  }

  private DownloadAction createRemoveAction() {
    return DownloadAction.createRemoveAction(
        DownloadAction.TYPE_DASH, testUri, /* customCacheKey= */ null);
  }
}
