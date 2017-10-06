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

import com.google.android.exoplayer2.offline.DownloadAction.Deserializer;
import com.google.android.exoplayer2.util.AtomicFile;
import com.google.android.exoplayer2.util.ClosedSource;
import com.google.android.exoplayer2.util.Util;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Stores and loads {@link DownloadAction}s to/from a file.
 */
@ClosedSource(reason = "Not ready yet")
public final class ActionFile {

  private final AtomicFile atomicFile;

  /**
   * @param actionFile File to be used to store and load {@link DownloadAction}s.
   */
  public ActionFile(File actionFile) {
    atomicFile = new AtomicFile(actionFile);
  }

  /**
   * Loads {@link DownloadAction}s from file.
   *
   * @param deserializers {@link Deserializer}s to deserialize DownloadActions.
   * @return Loaded DownloadActions.
   * @throws IOException If there is an error during loading.
   */
  public DownloadAction[] load(Deserializer... deserializers) throws IOException {
    InputStream inputStream = null;
    try {
      inputStream = atomicFile.openRead();
      DataInputStream dataInputStream = new DataInputStream(inputStream);
      int version = dataInputStream.readInt();
      if (version > DownloadAction.MASTER_VERSION) {
        throw new IOException("Not supported action file version: " + version);
      }
      int actionCount = dataInputStream.readInt();
      DownloadAction[] actions = new DownloadAction[actionCount];
      for (int i = 0; i < actionCount; i++) {
        actions[i] = DownloadAction.deserializeFromStream(deserializers, dataInputStream, version);
      }
      return actions;
    } finally {
      Util.closeQuietly(inputStream);
    }
  }

  /**
   * Stores {@link DownloadAction}s to file.
   *
   * @param downloadActions DownloadActions to store to file.
   * @throws IOException If there is an error during storing.
   */
  public void store(DownloadAction... downloadActions) throws IOException {
    OutputStream outputStream = null;
    try {
      outputStream = atomicFile.startWrite();
      DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
      dataOutputStream.writeInt(DownloadAction.MASTER_VERSION);
      dataOutputStream.writeInt(downloadActions.length);
      for (DownloadAction action : downloadActions) {
        DownloadAction.serializeToStream(action, dataOutputStream);
      }
      atomicFile.endWrite(outputStream);
      // Avoid calling close twice.
      outputStream = null;
    } finally {
      Util.closeQuietly(outputStream);
    }
  }

}
