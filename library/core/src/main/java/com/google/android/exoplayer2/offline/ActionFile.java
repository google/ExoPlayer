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

import com.google.android.exoplayer2.offline.DownloadAction.UnsupportedActionException;
import com.google.android.exoplayer2.util.AtomicFile;
import com.google.android.exoplayer2.util.Util;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Stores and loads {@link DownloadAction}s to/from a file.
 */
public final class ActionFile {

  private static final String TAG = "ActionFile";
  /* package */ static final int VERSION = 0;

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
   * @return Loaded DownloadActions. If the action file doesn't exists returns an empty array.
   * @throws IOException If there is an error during loading.
   */
  public DownloadAction[] load() throws IOException {
    if (!exists()) {
      return new DownloadAction[0];
    }
    InputStream inputStream = null;
    try {
      inputStream = atomicFile.openRead();
      DataInputStream dataInputStream = new DataInputStream(inputStream);
      int version = dataInputStream.readInt();
      if (version > VERSION) {
        throw new IOException("Unsupported action file version: " + version);
      }
      int actionCount = dataInputStream.readInt();
      ArrayList<DownloadAction> actions = new ArrayList<>();
      for (int i = 0; i < actionCount; i++) {
        try {
          actions.add(DownloadAction.deserializeFromStream(dataInputStream));
        } catch (UnsupportedActionException e) {
          // remove DownloadAction is not supported. Ignore the exception and continue loading rest.
        }
      }
      return actions.toArray(new DownloadAction[0]);
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
    DataOutputStream output = null;
    try {
      output = new DataOutputStream(atomicFile.startWrite());
      output.writeInt(VERSION);
      output.writeInt(downloadActions.length);
      for (DownloadAction action : downloadActions) {
        action.serializeToStream(output);
      }
      atomicFile.endWrite(output);
      // Avoid calling close twice.
      output = null;
    } finally {
      Util.closeQuietly(output);
    }
  }

  /** Returns whether the file or its backup exists. */
  public boolean exists() {
    return atomicFile.exists();
  }

  /** Delete the action file and its backup. */
  public void delete() {
    atomicFile.delete();
  }
}
