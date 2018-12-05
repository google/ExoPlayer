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
package com.google.android.exoplayer2.offline;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Unit tests for {@link ActionFile}. */
@RunWith(RobolectricTestRunner.class)
public class ActionFileTest {

  private File tempFile;
  private DownloadAction action1;
  private DownloadAction action2;

  @Before
  public void setUp() throws Exception {
    tempFile = Util.createTempFile(RuntimeEnvironment.application, "ExoPlayerTest");
    action1 =
        buildAction(
            DownloadAction.TYPE_PROGRESSIVE,
            Uri.parse("http://test1.uri"),
            TestUtil.buildTestData(16));
    action2 =
        buildAction(
            DownloadAction.TYPE_PROGRESSIVE,
            Uri.parse("http://test2.uri"),
            TestUtil.buildTestData(32));
  }

  @After
  public void tearDown() throws Exception {
    tempFile.delete();
  }

  @Test
  public void testLoadNoDataThrowsIOException() throws Exception {
    try {
      loadActions(new Object[] {});
      Assert.fail();
    } catch (IOException e) {
      // Expected exception.
    }
  }

  @Test
  public void testLoadIncompleteHeaderThrowsIOException() throws Exception {
    try {
      loadActions(new Object[] {ActionFile.VERSION});
      Assert.fail();
    } catch (IOException e) {
      // Expected exception.
    }
  }

  @Test
  public void testLoadCompleteHeaderZeroAction() throws Exception {
    DownloadAction[] actions = loadActions(new Object[] {ActionFile.VERSION, 0});
    assertThat(actions).isNotNull();
    assertThat(actions).hasLength(0);
  }

  @Test
  public void testLoadAction() throws Exception {
    DownloadAction[] actions =
        loadActions(
            new Object[] {
              ActionFile.VERSION,
              1, // Action count
              action1
            });
    assertThat(actions).isNotNull();
    assertThat(actions).hasLength(1);
    assertThat(actions[0]).isEqualTo(action1);
  }

  @Test
  public void testLoadActions() throws Exception {
    DownloadAction[] actions =
        loadActions(
            new Object[] {
              ActionFile.VERSION,
              2, // Action count
              action1,
              action2,
            });
    assertThat(actions).isNotNull();
    assertThat(actions).hasLength(2);
    assertThat(actions[0]).isEqualTo(action1);
    assertThat(actions[1]).isEqualTo(action2);
  }

  @Test
  public void testLoadNotSupportedVersion() throws Exception {
    try {
      loadActions(
          new Object[] {
            ActionFile.VERSION + 1,
            1, // Action count
            action1,
          });
      Assert.fail();
    } catch (IOException e) {
      // Expected exception.
    }
  }

  @Test
  public void testStoreAndLoadNoActions() throws Exception {
    doTestSerializationRoundTrip();
  }

  @Test
  public void testStoreAndLoadActions() throws Exception {
    doTestSerializationRoundTrip(action1, action2);
  }

  private void doTestSerializationRoundTrip(DownloadAction... actions) throws IOException {
    ActionFile actionFile = new ActionFile(tempFile);
    actionFile.store(actions);
    assertThat(actionFile.load()).isEqualTo(actions);
  }

  // TODO: Remove this method and add assets for invalid and legacy serialized action files.
  private DownloadAction[] loadActions(Object[] values) throws IOException {
    FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
    DataOutputStream dataOutputStream = new DataOutputStream(fileOutputStream);
    try {
      for (Object value : values) {
        if (value instanceof Integer) {
          dataOutputStream.writeInt((Integer) value);
        } else if (value instanceof DownloadAction) {
          ((DownloadAction) value).serializeToStream(dataOutputStream);
        } else {
          throw new IllegalArgumentException();
        }
      }
    } finally {
      dataOutputStream.close();
    }
    return new ActionFile(tempFile).load();
  }

  private static DownloadAction buildAction(String type, Uri uri, byte[] data) {
    return DownloadAction.createDownloadAction(
        type, uri, /* keys= */ Collections.emptyList(), /* customCacheKey= */ null, data);
  }
}
