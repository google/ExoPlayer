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

/** Unit tests for {@link Download}. */
@RunWith(AndroidJUnit4.class)
public class DownloadTest {

  private Uri testUri;

  @Before
  public void setUp() throws Exception {
    testUri = Uri.parse("https://www.test.com/download1");
  }

  @Test
  public void mergeAction_actionHaveDifferentType_throwsException() {
    DownloadAction downloadAction = createDownloadAction();
    Download download =
        new DownloadBuilder(downloadAction)
            .setType(downloadAction.type + "_different")
            .setState(Download.STATE_QUEUED)
            .build();

    try {
      download.copyWithMergedAction(downloadAction, /* canStart= */ true);
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  @Test
  public void mergeAction_actionHaveDifferentId_throwsException() {
    DownloadAction downloadAction = createDownloadAction();
    Download download =
        new DownloadBuilder(downloadAction)
            .setId(downloadAction.id + "_different")
            .setState(Download.STATE_QUEUED)
            .build();

    try {
      download.copyWithMergedAction(downloadAction, /* canStart= */ true);
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  @Test
  public void mergeAction_actionsWithSameIdAndType_doesNotFail() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadAction).setState(Download.STATE_QUEUED);
    Download download = downloadBuilder.build();

    download.copyWithMergedAction(downloadAction, /* canStart= */ true);
  }

  @Test
  public void mergeAction_actionHaveDifferentUri_downloadUriIsUpdated() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadAction)
            .setUri(downloadAction.uri + "_different")
            .setState(Download.STATE_QUEUED);
    Download download = downloadBuilder.build();

    Download mergedDownload = download.copyWithMergedAction(downloadAction, /* canStart= */ true);

    Download expectedDownload = downloadBuilder.setUri(downloadAction.uri).build();
    assertEqual(mergedDownload, expectedDownload);
  }

  @Test
  public void mergeAction_actionHaveDifferentData_downloadDataIsUpdated() {
    DownloadAction downloadAction =
        new DownloadAction(
            "id",
            DownloadAction.TYPE_DASH,
            testUri,
            Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ new byte[] {1, 2, 3, 4});
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadAction)
            .setState(Download.STATE_QUEUED)
            .setCustomMetadata(new byte[0]);
    Download download = downloadBuilder.build();

    Download mergedDownload = download.copyWithMergedAction(downloadAction, /* canStart= */ true);

    Download expectedDownload = downloadBuilder.setCustomMetadata(downloadAction.data).build();
    assertEqual(mergedDownload, expectedDownload);
  }

  @Test
  public void mergeAction_removingDownloadDownloadAction_stateBecomesRestarting() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadAction).setState(Download.STATE_REMOVING);
    Download download = downloadBuilder.build();

    Download mergedDownload = download.copyWithMergedAction(downloadAction, /* canStart= */ true);

    Download expectedDownload = downloadBuilder.setState(Download.STATE_RESTARTING).build();
    assertEqual(mergedDownload, expectedDownload);
  }

  @Test
  public void mergeAction_failedDownloadDownloadAction_stateBecomesQueued() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadAction)
            .setState(Download.STATE_FAILED)
            .setFailureReason(Download.FAILURE_REASON_UNKNOWN);
    Download download = downloadBuilder.build();

    Download mergedDownload = download.copyWithMergedAction(downloadAction, /* canStart= */ true);

    Download expectedDownload =
        downloadBuilder
            .setState(Download.STATE_QUEUED)
            .setFailureReason(Download.FAILURE_REASON_NONE)
            .build();
    assertEqual(mergedDownload, expectedDownload);
  }

  @Test
  public void mergeAction_stoppedDownloadDownloadAction_stateStaysStopped() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadAction)
            .setState(Download.STATE_STOPPED)
            .setManualStopReason(/* manualStopReason= */ 1);
    Download download = downloadBuilder.build();

    Download mergedDownload = download.copyWithMergedAction(downloadAction, /* canStart= */ true);

    assertEqual(mergedDownload, download);
  }

  @Test
  public void mergeAction_manualStopReasonSetButNotInStoppedState_stateBecomesStopped() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadAction)
            .setState(Download.STATE_COMPLETED)
            .setManualStopReason(/* manualStopReason= */ 1);
    Download download = downloadBuilder.build();

    Download mergedDownload = download.copyWithMergedAction(downloadAction, /* canStart= */ true);

    Download expectedDownload = downloadBuilder.setState(Download.STATE_STOPPED).build();
    assertEqual(mergedDownload, expectedDownload);
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
        new DownloadAction(
            "id",
            DownloadAction.TYPE_DASH,
            testUri,
            Arrays.asList(keys2),
            /* customCacheKey= */ null,
            /* data= */ null);
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadAction).setState(Download.STATE_QUEUED).setStreamKeys(keys1);
    Download download = downloadBuilder.build();

    Download mergedDownload = download.copyWithMergedAction(downloadAction, /* canStart= */ true);

    Download expectedDownload = downloadBuilder.setStreamKeys(expectedKeys).build();
    assertEqual(mergedDownload, expectedDownload);
  }

  static void assertEqual(Download download, Download expected) {
    assertEqual(download, expected, false);
  }

  static void assertEqual(Download download, Download that, boolean compareTimeFields) {
    assertThat(download.action).isEqualTo(that.action);
    assertThat(download.state).isEqualTo(that.state);
    assertThat(download.getDownloadPercentage()).isEqualTo(that.getDownloadPercentage());
    assertThat(download.getDownloadedBytes()).isEqualTo(that.getDownloadedBytes());
    assertThat(download.getTotalBytes()).isEqualTo(that.getTotalBytes());
    if (compareTimeFields) {
      assertThat(download.startTimeMs).isEqualTo(that.startTimeMs);
      assertThat(download.updateTimeMs).isEqualTo(that.updateTimeMs);
    }
    assertThat(download.failureReason).isEqualTo(that.failureReason);
    assertThat(download.manualStopReason).isEqualTo(that.manualStopReason);
  }

  private DownloadAction createDownloadAction() {
    return new DownloadAction(
        "id",
        DownloadAction.TYPE_DASH,
        testUri,
        Collections.emptyList(),
        /* customCacheKey= */ null,
        /* data= */ null);
  }
}
