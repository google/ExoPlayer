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

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Contains the necessary parameters for a download action. */
public final class DownloadAction {

  /** Thrown when the encoded action data belongs to an unsupported DownloadAction type. */
  public static class UnsupportedActionException extends IOException {}

  /** Type for progressive downloads. */
  public static final String TYPE_PROGRESSIVE = "progressive";
  /** Type for DASH downloads. */
  public static final String TYPE_DASH = "dash";
  /** Type for HLS downloads. */
  public static final String TYPE_HLS = "hls";
  /** Type for SmoothStreaming downloads. */
  public static final String TYPE_SS = "ss";

  private static final int VERSION = 3;

  /**
   * Deserializes a download action from the {@code data}.
   *
   * @param data The action data to deserialize.
   * @return The deserialized action.
   * @throws IOException If the data could not be deserialized.
   * @throws UnsupportedActionException If the data belongs to an unsupported {@link DownloadAction}
   *     type. Input read position is set to the end of the data.
   */
  public static DownloadAction fromByteArray(byte[] data) throws IOException {
    ByteArrayInputStream input = new ByteArrayInputStream(data);
    return deserializeFromStream(input);
  }

  /**
   * Deserializes a single download action from {@code input}.
   *
   * @param input The stream from which to read.
   * @return The deserialized action.
   * @throws IOException If there is an IO error reading from {@code input}, or if the data could
   *     not be deserialized.
   * @throws UnsupportedActionException If the data belongs to an unsupported {@link DownloadAction}
   *     type. Input read position is set to the end of the data.
   */
  public static DownloadAction deserializeFromStream(InputStream input) throws IOException {
    return readFromStream(new DataInputStream(input));
  }

  /**
   * Creates a DASH download action.
   *
   * @param id The content id.
   * @param type The type of the action.
   * @param uri The URI of the media to be downloaded.
   * @param keys Keys of streams to be downloaded. If empty, all streams will be downloaded.
   * @param customCacheKey A custom key for cache indexing, or null.
   * @param data Optional custom data for this action. If {@code null} an empty array will be used.
   */
  public static DownloadAction createDownloadAction(
      String id,
      String type,
      Uri uri,
      List<StreamKey> keys,
      @Nullable String customCacheKey,
      @Nullable byte[] data) {
    return new DownloadAction(id, type, uri, keys, customCacheKey, data);
  }

  /** The unique content id. */
  public final String id;
  /** The type of the action. */
  public final String type;
  /** The uri being downloaded. */
  public final Uri uri;
  /** Stream keys to be downloaded. If empty, all streams will be downloaded. */
  public final List<StreamKey> streamKeys;
  /** Custom key for cache indexing, or null. */
  @Nullable public final String customCacheKey;
  /** Application defined data associated with the download. May be empty. */
  public final byte[] data;

  /**
   * @param id See {@link #id}.
   * @param type See {@link #type}.
   * @param uri See {@link #uri}.
   * @param streamKeys See {@link #streamKeys}.
   * @param customCacheKey See {@link #customCacheKey}.
   * @param data See {@link #data}.
   */
  private DownloadAction(
      String id,
      String type,
      Uri uri,
      List<StreamKey> streamKeys,
      @Nullable String customCacheKey,
      @Nullable byte[] data) {
    this.id = id;
    this.type = type;
    this.uri = uri;
    this.customCacheKey = customCacheKey;
    ArrayList<StreamKey> mutableKeys = new ArrayList<>(streamKeys);
    Collections.sort(mutableKeys);
    this.streamKeys = Collections.unmodifiableList(mutableKeys);
    this.data = data != null ? Arrays.copyOf(data, data.length) : Util.EMPTY_BYTE_ARRAY;
  }

  /**
   * Returns the result of merging {@code newAction} into this action.
   *
   * @param newAction The new action.
   * @return The merged result.
   */
  public DownloadAction copyWithMergedAction(DownloadAction newAction) {
    Assertions.checkState(id.equals(newAction.id));
    Assertions.checkState(type.equals(newAction.type));

    List<StreamKey> mergedKeys;
    if (streamKeys.isEmpty() || newAction.streamKeys.isEmpty()) {
      // If either streamKeys is empty then all streams should be downloaded.
      mergedKeys = Collections.emptyList();
    } else {
      mergedKeys = new ArrayList<>(streamKeys);
      for (int i = 0; i < newAction.streamKeys.size(); i++) {
        StreamKey newKey = newAction.streamKeys.get(i);
        if (!mergedKeys.contains(newKey)) {
          mergedKeys.add(newKey);
        }
      }
    }
    return new DownloadAction(
        id, type, newAction.uri, mergedKeys, newAction.customCacheKey, newAction.data);
  }

  /** Serializes itself into a byte array. */
  public byte[] toByteArray() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      serializeToStream(output);
    } catch (IOException e) {
      // ByteArrayOutputStream shouldn't throw IOException.
      throw new IllegalStateException();
    }
    return output.toByteArray();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof DownloadAction)) {
      return false;
    }
    DownloadAction that = (DownloadAction) o;
    return id.equals(that.id)
        && type.equals(that.type)
        && uri.equals(that.uri)
        && streamKeys.equals(that.streamKeys)
        && Util.areEqual(customCacheKey, that.customCacheKey)
        && Arrays.equals(data, that.data);
  }

  @Override
  public final int hashCode() {
    int result = type.hashCode();
    result = 31 * result + id.hashCode();
    result = 31 * result + uri.hashCode();
    result = 31 * result + streamKeys.hashCode();
    result = 31 * result + (customCacheKey != null ? customCacheKey.hashCode() : 0);
    result = 31 * result + Arrays.hashCode(data);
    return result;
  }

  // Serialization.

  /**
   * Serializes this action into an {@link OutputStream}.
   *
   * @param output The stream to write to.
   */
  public final void serializeToStream(OutputStream output) throws IOException {
    // Don't close the stream as it closes the underlying stream too.
    DataOutputStream dataOutputStream = new DataOutputStream(output);
    dataOutputStream.writeUTF(type);
    dataOutputStream.writeInt(VERSION);
    dataOutputStream.writeUTF(uri.toString());
    dataOutputStream.writeBoolean(false);
    dataOutputStream.writeInt(data.length);
    dataOutputStream.write(data);
    dataOutputStream.writeInt(streamKeys.size());
    for (int i = 0; i < streamKeys.size(); i++) {
      StreamKey key = streamKeys.get(i);
      dataOutputStream.writeInt(key.periodIndex);
      dataOutputStream.writeInt(key.groupIndex);
      dataOutputStream.writeInt(key.trackIndex);
    }
    dataOutputStream.writeBoolean(customCacheKey != null);
    if (customCacheKey != null) {
      dataOutputStream.writeUTF(customCacheKey);
    }
    dataOutputStream.writeUTF(id);
    dataOutputStream.flush();
  }

  private static DownloadAction readFromStream(DataInputStream input) throws IOException {
    String type = input.readUTF();
    int version = input.readInt();

    Uri uri = Uri.parse(input.readUTF());
    boolean isRemoveAction = input.readBoolean();

    int dataLength = input.readInt();
    byte[] data;
    if (dataLength != 0) {
      data = new byte[dataLength];
      input.readFully(data);
    } else {
      data = null;
    }

    // Serialized version 0 progressive actions did not contain keys.
    boolean isLegacyProgressive = version == 0 && TYPE_PROGRESSIVE.equals(type);
    List<StreamKey> keys = new ArrayList<>();
    if (!isLegacyProgressive) {
      int keyCount = input.readInt();
      for (int i = 0; i < keyCount; i++) {
        keys.add(readKey(type, version, input));
      }
    }

    // Serialized version 0 and 1 DASH/HLS/SS actions did not contain a custom cache key.
    boolean isLegacySegmented =
        version < 2 && (TYPE_DASH.equals(type) || TYPE_HLS.equals(type) || TYPE_SS.equals(type));
    String customCacheKey = null;
    if (!isLegacySegmented) {
      customCacheKey = input.readBoolean() ? input.readUTF() : null;
    }

    // Serialized version 0, 1 and 2 did not contain an id.
    String id = version < 3 ? generateId(uri, customCacheKey) : input.readUTF();

    if (isRemoveAction) {
      // Remove actions are not supported anymore.
      throw new UnsupportedActionException();
    }
    return new DownloadAction(id, type, uri, keys, customCacheKey, data);
  }

  /* package */ static String generateId(Uri uri, @Nullable String customCacheKey) {
    return customCacheKey != null ? customCacheKey : uri.toString();
  }

  private static StreamKey readKey(String type, int version, DataInputStream input)
      throws IOException {
    int periodIndex;
    int groupIndex;
    int trackIndex;

    // Serialized version 0 HLS/SS actions did not contain a period index.
    if ((TYPE_HLS.equals(type) || TYPE_SS.equals(type)) && version == 0) {
      periodIndex = 0;
      groupIndex = input.readInt();
      trackIndex = input.readInt();
    } else {
      periodIndex = input.readInt();
      groupIndex = input.readInt();
      trackIndex = input.readInt();
    }
    return new StreamKey(periodIndex, groupIndex, trackIndex);
  }
}
