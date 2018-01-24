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
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** An action to download or remove downloaded progressive streams. */
public final class ProgressiveDownloadAction extends DownloadAction {

  public static final Deserializer DESERIALIZER = new Deserializer() {

    @Override
    public String getType() {
      return TYPE;
    }

    @Override
    public ProgressiveDownloadAction readFromStream(int version, DataInputStream input)
        throws IOException {
      return new ProgressiveDownloadAction(input.readUTF(),
          input.readBoolean() ? input.readUTF() : null, input.readBoolean(), input.readUTF());
    }

  };

  private static final String TYPE = "ProgressiveDownloadAction";

  private final String uri;
  private final @Nullable String customCacheKey;
  private final boolean removeAction;

  /**
   * @param uri Uri of the data to be downloaded.
   * @param customCacheKey A custom key that uniquely identifies the original stream. Used for cache
   *     indexing. May be null.
   * @param removeAction Whether the data should be downloaded or removed.
   * @param data Optional custom data for this action. If null, an empty string is used.
   */
  public ProgressiveDownloadAction(String uri, @Nullable String customCacheKey,
      boolean removeAction, String data) {
    super(data);
    this.uri = Assertions.checkNotNull(uri);
    this.customCacheKey = customCacheKey;
    this.removeAction = removeAction;
  }

  @Override
  public boolean isRemoveAction() {
    return removeAction;
  }

  @Override
  protected ProgressiveDownloader createDownloader(DownloaderConstructorHelper constructorHelper) {
    return new ProgressiveDownloader(uri, customCacheKey, constructorHelper);
  }

  @Override
  protected String getType() {
    return TYPE;
  }

  @Override
  protected void writeToStream(DataOutputStream output) throws IOException {
    output.writeUTF(uri);
    boolean customCacheKeyAvailable = customCacheKey != null;
    output.writeBoolean(customCacheKeyAvailable);
    if (customCacheKeyAvailable) {
      output.writeUTF(customCacheKey);
    }
    output.writeBoolean(isRemoveAction());
    output.writeUTF(getData());
  }

  @Override
  protected boolean isSameMedia(DownloadAction other) {
    if (!(other instanceof ProgressiveDownloadAction)) {
      return false;
    }
    ProgressiveDownloadAction action = (ProgressiveDownloadAction) other;
    return customCacheKey != null ? customCacheKey.equals(action.customCacheKey)
        : uri.equals(action.uri);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    ProgressiveDownloadAction that = (ProgressiveDownloadAction) o;
    return uri.equals(that.uri) && Util.areEqual(customCacheKey, that.customCacheKey);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + uri.hashCode();
    result = 31 * result + (customCacheKey != null ? customCacheKey.hashCode() : 0);
    return result;
  }

}
