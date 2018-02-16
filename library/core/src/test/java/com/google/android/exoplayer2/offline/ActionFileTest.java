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

import com.google.android.exoplayer2.offline.DownloadAction.Deserializer;
import com.google.android.exoplayer2.util.Util;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Unit tests for {@link ProgressiveDownloadAction}.
 */
@RunWith(RobolectricTestRunner.class)
public class ActionFileTest {

  private File tempFile;

  @Before
  public void setUp() throws Exception {
    tempFile = Util.createTempFile(RuntimeEnvironment.application, "ExoPlayerTest");
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
      loadActions(new Object[] {DownloadAction.MASTER_VERSION});
      Assert.fail();
    } catch (IOException e) {
      // Expected exception.
    }
  }

  @Test
  public void testLoadCompleteHeaderZeroAction() throws Exception {
    DownloadAction[] actions =
        loadActions(new Object[] {DownloadAction.MASTER_VERSION, /*action count*/0});
    assertThat(actions).isNotNull();
    assertThat(actions).hasLength(0);
  }

  @Test
  public void testLoadAction() throws Exception {
    DownloadAction[] actions = loadActions(
        new Object[] {DownloadAction.MASTER_VERSION, /*action count*/1, /*action 1*/"type2", 321},
        new FakeDeserializer("type2"));
    assertThat(actions).isNotNull();
    assertThat(actions).hasLength(1);
    assertAction(actions[0], "type2", DownloadAction.MASTER_VERSION, 321);
  }

  @Test
  public void testLoadActions() throws Exception {
    DownloadAction[] actions = loadActions(
        new Object[] {DownloadAction.MASTER_VERSION, /*action count*/2, /*action 1*/"type1", 123,
            /*action 2*/"type2", 321}, // Action 2
        new FakeDeserializer("type1"), new FakeDeserializer("type2"));
    assertThat(actions).isNotNull();
    assertThat(actions).hasLength(2);
    assertAction(actions[0], "type1", DownloadAction.MASTER_VERSION, 123);
    assertAction(actions[1], "type2", DownloadAction.MASTER_VERSION, 321);
  }

  @Test
  public void testLoadNotSupportedVersion() throws Exception {
    try {
      loadActions(new Object[] {DownloadAction.MASTER_VERSION + 1, /*action count*/1,
              /*action 1*/"type2", 321}, new FakeDeserializer("type2"));
      Assert.fail();
    } catch (IOException e) {
      // Expected exception.
    }
  }

  @Test
  public void testLoadNotSupportedType() throws Exception {
    try {
      loadActions(new Object[] {DownloadAction.MASTER_VERSION, /*action count*/1,
              /*action 1*/"type2", 321}, new FakeDeserializer("type1"));
      Assert.fail();
    } catch (DownloadException e) {
      // Expected exception.
    }
  }

  @Test
  public void testStoreAndLoadNoActions() throws Exception {
    doTestSerializationRoundTrip(new DownloadAction[0]);
  }

  @Test
  public void testStoreAndLoadActions() throws Exception {
    doTestSerializationRoundTrip(new DownloadAction[] {
        new FakeDownloadAction("type1", DownloadAction.MASTER_VERSION, 123),
        new FakeDownloadAction("type2", DownloadAction.MASTER_VERSION, 321),
    }, new FakeDeserializer("type1"), new FakeDeserializer("type2"));
  }

  private void doTestSerializationRoundTrip(DownloadAction[] actions,
      Deserializer... deserializers) throws IOException {
    ActionFile actionFile = new ActionFile(tempFile);
    actionFile.store(actions);
    assertThat(actionFile.load(deserializers)).isEqualTo(actions);
  }

  private DownloadAction[] loadActions(Object[] values, Deserializer... deserializers)
      throws IOException {
    FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
    DataOutputStream dataOutputStream = new DataOutputStream(fileOutputStream);
    try {
      for (Object value : values) {
        if (value instanceof Integer) {
          dataOutputStream.writeInt((Integer) value); // Action count
        } else if (value instanceof String) {
          dataOutputStream.writeUTF((String) value); // Action count
        } else {
          throw new IllegalArgumentException();
        }
      }
    } finally {
      dataOutputStream.close();
    }
    return new ActionFile(tempFile).load(deserializers);
  }

  private static void assertAction(DownloadAction action, String type, int version, int data) {
    assertThat(action).isInstanceOf(FakeDownloadAction.class);
    assertThat(action.getType()).isEqualTo(type);
    assertThat(((FakeDownloadAction) action).version).isEqualTo(version);
    assertThat(((FakeDownloadAction) action).data).isEqualTo(data);
  }

  private static class FakeDeserializer implements Deserializer {
    final String type;

    FakeDeserializer(String type) {
      this.type = type;
    }

    @Override
    public String getType() {
      return type;
    }

    @Override
    public DownloadAction readFromStream(int version, DataInputStream input) throws IOException {
      return new FakeDownloadAction(type, version, input.readInt());
    }
  }

  private static class FakeDownloadAction extends DownloadAction {
    final String type;
    final int version;
    final int data;

    private FakeDownloadAction(String type, int version, int data) {
      super(null);
      this.type = type;
      this.version = version;
      this.data = data;
    }

    @Override
    protected String getType() {
      return type;
    }

    @Override
    protected void writeToStream(DataOutputStream output) throws IOException {
      output.writeInt(data);
    }

    @Override
    public boolean isRemoveAction() {
      return false;
    }

    @Override
    protected boolean isSameMedia(DownloadAction other) {
      return false;
    }

    @Override
    protected Downloader createDownloader(DownloaderConstructorHelper downloaderConstructorHelper) {
      return null;
    }

    // auto generated code

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      FakeDownloadAction that = (FakeDownloadAction) o;
      return version == that.version && data == that.data && type.equals(that.type);
    }

    @Override
    public int hashCode() {
      int result = type.hashCode();
      result = 31 * result + version;
      result = 31 * result + data;
      return result;
    }
  }

}
