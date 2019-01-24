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

import static com.google.android.exoplayer2.offline.DownloadAction.TYPE_DASH;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Unit tests for {@link DownloadIndexUtil}. */
@RunWith(RobolectricTestRunner.class)
public class DownloadIndexUtilTest {

  private File tempFile;
  private ExoDatabaseProvider databaseProvider;
  private DefaultDownloadIndex downloadIndex;

  @Before
  public void setUp() throws Exception {
    tempFile = Util.createTempFile(RuntimeEnvironment.application, "ExoPlayerTest");
    databaseProvider = new ExoDatabaseProvider(RuntimeEnvironment.application);
    downloadIndex = new DefaultDownloadIndex(databaseProvider);
  }

  @After
  public void tearDown() {
    databaseProvider.close();
    tempFile.delete();
  }

  @Test
  public void addAction_nonExistingDownloadState_createsNewDownloadState() {
    byte[] data = new byte[] {1, 2, 3, 4};
    DownloadAction action =
        DownloadAction.createDownloadAction(
            TYPE_DASH,
            Uri.parse("https://www.test.com/download"),
            asList(
                new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 1, /* trackIndex= */ 2),
                new StreamKey(/* periodIndex= */ 3, /* groupIndex= */ 4, /* trackIndex= */ 5)),
            /* customCacheKey= */ "key123",
            data);

    DownloadIndexUtil.addAction(downloadIndex, action.id, action);

    assertDownloadIndexContainsAction(action, DownloadState.STATE_QUEUED);
  }

  @Test
  public void addAction_existingDownloadState_createsMergedDownloadState() {
    StreamKey streamKey1 =
        new StreamKey(/* periodIndex= */ 3, /* groupIndex= */ 4, /* trackIndex= */ 5);
    StreamKey streamKey2 =
        new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 1, /* trackIndex= */ 2);
    DownloadAction action1 =
        DownloadAction.createDownloadAction(
            TYPE_DASH,
            Uri.parse("https://www.test.com/download1"),
            asList(streamKey1),
            /* customCacheKey= */ "key123",
            new byte[] {1, 2, 3, 4});
    DownloadAction action2 =
        DownloadAction.createDownloadAction(
            TYPE_DASH,
            Uri.parse("https://www.test.com/download2"),
            asList(streamKey2),
            /* customCacheKey= */ "key123",
            new byte[] {5, 4, 3, 2, 1});
    DownloadIndexUtil.addAction(downloadIndex, action1.id, action1);

    DownloadIndexUtil.addAction(downloadIndex, action2.id, action2);

    DownloadState downloadState = downloadIndex.getDownloadState(action2.id);
    assertThat(downloadState).isNotNull();
    assertThat(downloadState.type).isEqualTo(action2.type);
    assertThat(downloadState.cacheKey).isEqualTo(action2.customCacheKey);
    assertThat(downloadState.customMetadata).isEqualTo(action2.data);
    assertThat(downloadState.uri).isEqualTo(action2.uri);
    assertThat(Arrays.asList(downloadState.streamKeys)).containsExactly(streamKey1, streamKey2);
    assertThat(downloadState.state).isEqualTo(DownloadState.STATE_QUEUED);
  }

  @Test
  public void upgradeActionFile_createsDownloadStates() throws Exception {
    ActionFile actionFile = new ActionFile(tempFile);
    StreamKey streamKey1 =
        new StreamKey(/* periodIndex= */ 3, /* groupIndex= */ 4, /* trackIndex= */ 5);
    StreamKey streamKey2 =
        new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 1, /* trackIndex= */ 2);
    DownloadAction action1 =
        DownloadAction.createDownloadAction(
            TYPE_DASH,
            Uri.parse("https://www.test.com/download1"),
            asList(streamKey1),
            /* customCacheKey= */ "key123",
            new byte[] {1, 2, 3, 4});
    DownloadAction action2 =
        DownloadAction.createDownloadAction(
            TYPE_DASH,
            Uri.parse("https://www.test.com/download2"),
            asList(streamKey2),
            /* customCacheKey= */ "key234",
            new byte[] {5, 4, 3, 2, 1});
    actionFile.store(action1, action2);
    DownloadAction action3 =
        DownloadAction.createRemoveAction(
            TYPE_DASH, Uri.parse("https://www.test.com/download3"), /* customCacheKey= */ "key345");
    actionFile.store(action1, action2, action3);

    DownloadIndexUtil.upgradeActionFile(actionFile, downloadIndex, /* downloadIdProvider= */ null);

    assertDownloadIndexContainsAction(action1, DownloadState.STATE_QUEUED);
    assertDownloadIndexContainsAction(action2, DownloadState.STATE_QUEUED);
    assertDownloadIndexContainsAction(action3, DownloadState.STATE_REMOVING);
  }

  private void assertDownloadIndexContainsAction(DownloadAction action, int state) {
    DownloadState downloadState = downloadIndex.getDownloadState(action.id);
    assertThat(downloadState).isNotNull();
    assertThat(downloadState.type).isEqualTo(action.type);
    assertThat(downloadState.cacheKey).isEqualTo(action.customCacheKey);
    assertThat(downloadState.customMetadata).isEqualTo(action.data);
    assertThat(downloadState.uri).isEqualTo(action.uri);
    assertThat(Arrays.asList(downloadState.streamKeys)).containsExactlyElementsIn(action.keys);
    assertThat(downloadState.state).isEqualTo(state);
  }

  @SuppressWarnings("unchecked")
  private static List<StreamKey> asList(StreamKey... streamKeys) {
    return Arrays.asList(streamKeys);
  }
}
