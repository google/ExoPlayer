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

import android.support.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Contains the necessary parameters for a download or remove action. */
public abstract class DownloadAction {

  /**
   * Master version for all {@link DownloadAction} serialization/deserialization implementations. On
   * each change on any {@link DownloadAction} serialization format this version needs to be
   * increased.
   */
  public static final int MASTER_VERSION = 0;

  /** Used to deserialize {@link DownloadAction}s. */
  public abstract static class Deserializer {

    public String type;

    public Deserializer(String type) {
      this.type = type;
    }

    /**
     * Deserializes an action from the {@code input}.
     *
     * @param version Version of the data.
     * @param input DataInputStream to read data from.
     * @see DownloadAction#writeToStream(DataOutputStream)
     * @see DownloadAction#MASTER_VERSION
     */
    public abstract DownloadAction readFromStream(int version, DataInputStream input)
        throws IOException;
  }

  /**
   * Deserializes one action that was serialized with {@link #serializeToStream(DownloadAction,
   * OutputStream)} from the {@code input}, using the {@link Deserializer}s that supports the
   * action's type.
   *
   * <p>The caller is responsible for closing the given {@link InputStream}.
   *
   * @param deserializers {@link Deserializer}s for supported actions.
   * @param input Input stream to read serialized data.
   * @return The deserialized action.
   * @throws IOException If there is an IO error reading from {@code input}, or if the action type
   *     isn't supported by any of the {@code deserializers}.
   */
  public static DownloadAction deserializeFromStream(
      Deserializer[] deserializers, InputStream input) throws IOException {
    return deserializeFromStream(deserializers, input, MASTER_VERSION);
  }

  /**
   * Deserializes one {@code action} which was serialized by {@link
   * #serializeToStream(DownloadAction, OutputStream)} from the {@code input} using one of the
   * {@link Deserializer}s which supports the type of the action.
   *
   * <p>The caller is responsible for closing the given {@link InputStream}.
   *
   * @param deserializers {@link Deserializer}s for supported actions.
   * @param input Input stream to read serialized data.
   * @param version Master version of the serialization. See {@link DownloadAction#MASTER_VERSION}.
   * @return The deserialized action.
   * @throws IOException If there is an IO error from {@code input}.
   * @throws DownloadException If the action type isn't supported by any of the {@code
   *     deserializers}.
   */
  public static DownloadAction deserializeFromStream(
      Deserializer[] deserializers, InputStream input, int version) throws IOException {
    // Don't close the stream as it closes the underlying stream too.
    DataInputStream dataInputStream = new DataInputStream(input);
    String type = dataInputStream.readUTF();
    for (Deserializer deserializer : deserializers) {
      if (type.equals(deserializer.type)) {
        return deserializer.readFromStream(version, dataInputStream);
      }
    }
    throw new DownloadException("No Deserializer can be found to parse the data.");
  }

  /** Serializes {@code action} type and data into the {@code output}. */
  public static void serializeToStream(DownloadAction action, OutputStream output)
      throws IOException {
    // Don't close the stream as it closes the underlying stream too.
    DataOutputStream dataOutputStream = new DataOutputStream(output);
    dataOutputStream.writeUTF(action.type);
    action.writeToStream(dataOutputStream);
    dataOutputStream.flush();
  }

  /** The type of the action. */
  public final String type;
  /** Whether this is a remove action. If false, this is a download action. */
  public final boolean isRemoveAction;
  /** Custom data for this action. May be the empty string if no custom data was specified. */
  public final String data;

  /**
   * @param type The type of the action.
   * @param isRemoveAction Whether this is a remove action. If false, this is a download action.
   * @param data Optional custom data for this action. If null, an empty string is used.
   */
  protected DownloadAction(String type, boolean isRemoveAction, @Nullable String data) {
    this.type = type;
    this.isRemoveAction = isRemoveAction;
    this.data = data != null ? data : "";
  }

  /** Serializes itself into a byte array. */
  public final byte[] toByteArray() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      serializeToStream(this, output);
    } catch (IOException e) {
      // ByteArrayOutputStream shouldn't throw IOException.
      throw new IllegalStateException();
    }
    return output.toByteArray();
  }

  /** Serializes itself into the {@code output}. */
  protected abstract void writeToStream(DataOutputStream output) throws IOException;

  /** Returns whether this is an action for the same media as the {@code other}. */
  protected abstract boolean isSameMedia(DownloadAction other);

  /** Creates a {@link Downloader} with the given parameters. */
  protected abstract Downloader createDownloader(
      DownloaderConstructorHelper downloaderConstructorHelper);

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DownloadAction that = (DownloadAction) o;
    return data.equals(that.data) && isRemoveAction == that.isRemoveAction;
  }

  @Override
  public int hashCode() {
    int result = data.hashCode();
    result = 31 * result + (isRemoveAction ? 1 : 0);
    return result;
  }

}
