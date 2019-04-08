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
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DownloadState}. */
@RunWith(AndroidJUnit4.class)
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
      downloadState.copyWithMergedAction(downloadAction);
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
      downloadState.copyWithMergedAction(downloadAction);
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

    downloadState.copyWithMergedAction(downloadAction);
  }

  @Test
  public void mergeAction_actionHaveDifferentUri_downloadStateUriIsUpdated() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(downloadAction)
            .setUri(downloadAction.uri + "_different")
            .setState(DownloadState.STATE_QUEUED);
    DownloadState downloadState = downloadStateBuilder.build();

    DownloadState mergedDownloadState = downloadState.copyWithMergedAction(downloadAction);

    DownloadState expectedDownloadState = downloadStateBuilder.setUri(downloadAction.uri).build();
    assertEqual(mergedDownloadState, expectedDownloadState);
  }

  @Test
  public void mergeAction_actionHaveDifferentData_downloadStateDataIsUpdated() {
    DownloadAction downloadAction =
        DownloadAction.createDownloadAction(
            "id",
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

    DownloadState mergedDownloadState = downloadState.copyWithMergedAction(downloadAction);

    DownloadState expectedDownloadState =
        downloadStateBuilder.setCustomMetadata(downloadAction.data).build();
    assertEqual(mergedDownloadState, expectedDownloadState);
  }

  @Test
  public void mergeAction_removingDownloadDownloadAction_stateBecomesRestarting() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(downloadAction).setState(DownloadState.STATE_REMOVING);
    DownloadState downloadState = downloadStateBuilder.build();

    DownloadState mergedDownloadState = downloadState.copyWithMergedAction(downloadAction);

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

    DownloadState mergedDownloadState = downloadState.copyWithMergedAction(downloadAction);

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
            .setManualStopReason(DownloadState.MANUAL_STOP_REASON_UNDEFINED);
    DownloadState downloadState = downloadStateBuilder.build();

    DownloadState mergedDownloadState = downloadState.copyWithMergedAction(downloadAction);

    assertEqual(mergedDownloadState, downloadState);
  }

  @Test
  public void mergeAction_manualStopReasonSetButNotInStoppedState_stateBecomesStopped() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(downloadAction)
            .setState(DownloadState.STATE_COMPLETED)
            .setManualStopReason(DownloadState.MANUAL_STOP_REASON_UNDEFINED);
    DownloadState downloadState = downloadStateBuilder.build();

    DownloadState mergedDownloadState = downloadState.copyWithMergedAction(downloadAction);

    DownloadState expectedDownloadState =
        downloadStateBuilder.setState(DownloadState.STATE_STOPPED).build();
    assertEqual(mergedDownloadState, expectedDownloadState);
  }

  @Test
  public void mergeAction_notMetRequirementsSetButNotInStoppedState_stateBecomesStopped() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(downloadAction)
            .setState(DownloadState.STATE_COMPLETED)
            .setNotMetRequirements(0x12345678);
    DownloadState downloadState = downloadStateBuilder.build();

    DownloadState mergedDownloadState = downloadState.copyWithMergedAction(downloadAction);

    DownloadState expectedDownloadState =
        downloadStateBuilder.setState(DownloadState.STATE_STOPPED).build();
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
            "id",
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

    DownloadState mergedDownloadState = downloadState.copyWithMergedAction(downloadAction);

    DownloadState expectedDownloadState = downloadStateBuilder.setStreamKeys(expectedKeys).build();
    assertEqual(mergedDownloadState, expectedDownloadState);
  }

  static void assertEqual(DownloadState downloadState, DownloadState expected) {
    assertEqual(downloadState, expected, false);
  }

  static void assertEqual(
      DownloadState downloadState, DownloadState that, boolean compareTimeFields) {
    assertThat(downloadState.action).isEqualTo(that.action);
    assertThat(downloadState.state).isEqualTo(that.state);
    assertThat(downloadState.getDownloadPercentage()).isEqualTo(that.getDownloadPercentage());
    assertThat(downloadState.getDownloadedBytes()).isEqualTo(that.getDownloadedBytes());
    assertThat(downloadState.getTotalBytes()).isEqualTo(that.getTotalBytes());
    if (compareTimeFields) {
      assertThat(downloadState.startTimeMs).isEqualTo(that.startTimeMs);
      assertThat(downloadState.updateTimeMs).isEqualTo(that.updateTimeMs);
    }
    assertThat(downloadState.failureReason).isEqualTo(that.failureReason);
    assertThat(downloadState.manualStopReason).isEqualTo(that.manualStopReason);
    assertThat(downloadState.notMetRequirements).isEqualTo(that.notMetRequirements);
  }

  private DownloadAction createDownloadAction() {
    return DownloadAction.createDownloadAction(
        "id",
        DownloadAction.TYPE_DASH,
        testUri,
        Collections.emptyList(),
        /* customCacheKey= */ null,
        /* data= */ null);
  }
}
