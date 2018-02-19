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
import com.google.android.exoplayer2.util.Assertions;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * {@link DownloadAction} for {@link SegmentDownloader}s.
 *
 * @param <K> The type of the representation key object.
 */
public abstract class SegmentDownloadAction<K> extends DownloadAction {

  /**
   * Base class for {@link SegmentDownloadAction} {@link Deserializer}s.
   *
   * @param <K> The type of the representation key object.
   */
  protected abstract static class SegmentDownloadActionDeserializer<K> implements Deserializer {

    @Override
    public DownloadAction readFromStream(int version, DataInputStream input) throws IOException {
      Uri manifestUri = Uri.parse(input.readUTF());
      String data = input.readUTF();
      boolean removeAction = input.readBoolean();
      int keyCount = input.readInt();
      K[] keys = createKeyArray(keyCount);
      for (int i = 0; i < keyCount; i++) {
        keys[i] = readKey(input);
      }
      return createDownloadAction(manifestUri, removeAction, data, keys);
    }

    /** Deserializes a key from the {@code input}. */
    protected abstract K readKey(DataInputStream input) throws IOException;

    /** Returns a key array. */
    protected abstract K[] createKeyArray(int keyCount);

    /** Returns a {@link DownloadAction}. */
    protected abstract DownloadAction createDownloadAction(Uri manifestUri, boolean removeAction,
        String data, K[] keys);

  }

  protected final Uri manifestUri;
  protected final K[] keys;
  private final boolean removeAction;

  /**
   * @param manifestUri The {@link Uri} of the manifest to be downloaded.
   * @param removeAction Whether the data will be removed. If {@code false} it will be downloaded.
   * @param data Optional custom data for this action. If null, an empty string is used.
   * @param keys Keys of representations to be downloaded. If empty, all representations are
   *     downloaded. If {@code removeAction} is true, {@code keys} should be an empty array.
   */
  protected SegmentDownloadAction(Uri manifestUri, boolean removeAction, String data, K[] keys) {
    super(data);
    Assertions.checkNotNull(keys);
    if (removeAction) {
      Assertions.checkArgument(keys.length == 0);
    }
    this.manifestUri = manifestUri;
    this.keys = keys;
    this.removeAction = removeAction;
  }

  @Override
  public final boolean isRemoveAction() {
    return removeAction;
  }

  @Override
  public final void writeToStream(DataOutputStream output) throws IOException {
    output.writeUTF(manifestUri.toString());
    output.writeUTF(getData());
    output.writeBoolean(removeAction);
    output.writeInt(keys.length);
    for (K key : keys) {
      writeKey(output, key);
    }
  }

  /** Serializes the {@code key} into the {@code output}. */
  protected abstract void writeKey(DataOutputStream output, K key) throws IOException;


  @Override
  public boolean isSameMedia(DownloadAction other) {
    return other instanceof SegmentDownloadAction
        && manifestUri.equals(((SegmentDownloadAction<?>) other).manifestUri);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    SegmentDownloadAction<?> that = (SegmentDownloadAction<?>) o;
    return manifestUri.equals(that.manifestUri)
        && removeAction == that.removeAction
        && keys.length == that.keys.length
        && Arrays.asList(keys).containsAll(Arrays.asList(that.keys));
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + manifestUri.hashCode();
    result = 31 * result + Arrays.hashCode(keys);
    return result;
  }

}
