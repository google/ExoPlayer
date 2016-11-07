/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.google.android.exoplayer.util;

import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Exoplayer internal version of the framework's {@link android.util.AtomicFile},
 * a helper class for performing atomic operations on a file by creating a
 * backup file until a write has successfully completed.
 * <p>
 * Atomic file guarantees file integrity by ensuring that a file has
 * been completely written and sync'd to disk before removing its backup.
 * As long as the backup file exists, the original file is considered
 * to be invalid (left over from a previous attempt to write the file).
 * </p><p>
 * Atomic file does not confer any file locking semantics.
 * Do not use this class when the file may be accessed or modified concurrently
 * by multiple threads or processes.  The caller is responsible for ensuring
 * appropriate mutual exclusion invariants whenever it accesses the file.
 * </p>
 */
public class AtomicFile {
  private final File mBaseName;
  private final File mBackupName;

  /**
   * Create a new AtomicFile for a file located at the given File path.
   * The secondary backup file will be the same file path with ".bak" appended.
   */
  public AtomicFile(File baseName) {
    mBaseName = baseName;
    mBackupName = new File(baseName.getPath() + ".bak");
  }

  /**
   * Return the path to the base file.  You should not generally use this,
   * as the data at that path may not be valid.
   */
  public File getBaseFile() {
    return mBaseName;
  }

  /**
   * Delete the atomic file.  This deletes both the base and backup files.
   */
  public void delete() {
    mBaseName.delete();
    mBackupName.delete();
  }

  /**
   * Start a new write operation on the file.  This returns a FileOutputStream
   * to which you can write the new file data.  The existing file is replaced
   * with the new data.  You <em>must not</em> directly close the given
   * FileOutputStream; instead call either {@link #finishWrite(FileOutputStream)}
   * or {@link #failWrite(FileOutputStream)}.
   *
   * <p>Note that if another thread is currently performing
   * a write, this will simply replace whatever that thread is writing
   * with the new file being written by this thread, and when the other
   * thread finishes the write the new write operation will no longer be
   * safe (or will be lost).  You must do your own threading protection for
   * access to AtomicFile.
   */
  public FileOutputStream startWrite() throws IOException {
    // Rename the current file so it may be used as a backup during the next read
    if (mBaseName.exists()) {
      if (!mBackupName.exists()) {
        if (!mBaseName.renameTo(mBackupName)) {
          Log.w("AtomicFile", "Couldn't rename file " + mBaseName
              + " to backup file " + mBackupName);
        }
      } else {
        mBaseName.delete();
      }
    }
    FileOutputStream str = null;
    try {
      str = new FileOutputStream(mBaseName);
    } catch (FileNotFoundException e) {
      File parent = mBaseName.getParentFile();
      if (!parent.mkdirs()) {
        throw new IOException("Couldn't create directory " + mBaseName);
      }
      try {
        str = new FileOutputStream(mBaseName);
      } catch (FileNotFoundException e2) {
        throw new IOException("Couldn't create " + mBaseName);
      }
    }
    return str;
  }

  /**
   * Call when you have successfully finished writing to the stream
   * returned by {@link #startWrite()}.  This will close, sync, and
   * commit the new data.  The next attempt to read the atomic file
   * will return the new file stream.
   */
  public void finishWrite(FileOutputStream str) {
    if (str != null) {
      sync(str);
      try {
        str.close();
        mBackupName.delete();
      } catch (IOException e) {
        Log.w("AtomicFile", "finishWrite: Got exception:", e);
      }
    }
  }

  /**
   * Call when you have failed for some reason at writing to the stream
   * returned by {@link #startWrite()}.  This will close the current
   * write stream, and roll back to the previous state of the file.
   */
  public void failWrite(FileOutputStream str) {
    if (str != null) {
      sync(str);
      try {
        str.close();
        mBaseName.delete();
        mBackupName.renameTo(mBaseName);
      } catch (IOException e) {
        Log.w("AtomicFile", "failWrite: Got exception:", e);
      }
    }
  }

  /**
   * Open the atomic file for reading.  If there previously was an
   * incomplete write, this will roll back to the last good data before
   * opening for read.  You should call close() on the FileInputStream when
   * you are done reading from it.
   *
   * <p>Note that if another thread is currently performing
   * a write, this will incorrectly consider it to be in the state of a bad
   * write and roll back, causing the new data currently being written to
   * be dropped.  You must do your own threading protection for access to
   * AtomicFile.
   */
  public FileInputStream openRead() throws FileNotFoundException {
    if (mBackupName.exists()) {
      mBaseName.delete();
      mBackupName.renameTo(mBaseName);
    }
    return new FileInputStream(mBaseName);
  }

  /**
   * A convenience for {@link #openRead()} that also reads all of the
   * file contents into a byte array which is returned.
   */
  public byte[] readFully() throws IOException {
    FileInputStream stream = openRead();
    try {
      int pos = 0;
      int avail = stream.available();
      byte[] data = new byte[avail];
      while (true) {
        int amt = stream.read(data, pos, data.length - pos);
        //Log.i("foo", "Read " + amt + " bytes at " + pos
        //        + " of avail " + data.length);
        if (amt <= 0) {
          //Log.i("foo", "**** FINISHED READING: pos=" + pos
          //        + " len=" + data.length);
          return data;
        }
        pos += amt;
        avail = stream.available();
        if (avail > data.length - pos) {
          byte[] newData = new byte[pos + avail];
          System.arraycopy(data, 0, newData, 0, pos);
          data = newData;
        }
      }
    } finally {
      stream.close();
    }
  }

  private static boolean sync(FileOutputStream stream) {
    try {
      if (stream != null) {
        stream.getFD().sync();
      }
      return true;
    } catch (IOException e) {
      // do nothing
    }
    return false;
  }
}
