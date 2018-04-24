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
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** An action to download or remove downloaded progressive streams. */
public final class ProgressiveDownloadAction extends DownloadAction {

  private static final String TYPE = "ProgressiveDownloadAction";

  public static final Deserializer DESERIALIZER =
      new Deserializer(TYPE) {
        @Override
        public ProgressiveDownloadAction readFromStream(int version, DataInputStream input)
            throws IOException {
          boolean isRemoveAction = input.readBoolean();
          String data = input.readUTF();
          Uri uri = Uri.parse(input.readUTF());
          String customCacheKey = input.readBoolean() ? input.readUTF() : null;
          return new ProgressiveDownloadAction(isRemoveAction, data, uri, customCacheKey);
        }
      };

  private final Uri uri;
  private final @Nullable String customCacheKey;

  /**
   * @param isRemoveAction Whether this is a remove action. If false, this is a download action.
   * @param data Optional custom data for this action. If null, an empty string is used.
   * @param uri Uri of the data to be downloaded.
   * @param customCacheKey A custom key that uniquely identifies the original stream. If not null it
   *     is used for cache indexing.
   */
  public ProgressiveDownloadAction(
      boolean isRemoveAction, @Nullable String data, Uri uri, @Nullable String customCacheKey) {
    super(TYPE, isRemoveAction, data);
    this.uri = Assertions.checkNotNull(uri);
    this.customCacheKey = customCacheKey;
  }

  @Override
  protected ProgressiveDownloader createDownloader(DownloaderConstructorHelper constructorHelper) {
    return new ProgressiveDownloader(uri, customCacheKey, constructorHelper);
  }

  @Override
  protected void writeToStream(DataOutputStream output) throws IOException {
    output.writeBoolean(isRemoveAction);
    output.writeUTF(data);
    output.writeUTF(uri.toString());
    boolean customCacheKeySet = customCacheKey != null;
    output.writeBoolean(customCacheKeySet);
    if (customCacheKeySet) {
      output.writeUTF(customCacheKey);
    }
  }

  @Override
  protected boolean isSameMedia(DownloadAction other) {
    if (!(other instanceof ProgressiveDownloadAction)) {
      return false;
    }
    return getCacheKey().equals(((ProgressiveDownloadAction) other).getCacheKey());
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

  private String getCacheKey() {
    return customCacheKey != null ? customCacheKey : CacheUtil.generateKey(uri);
  }
}
