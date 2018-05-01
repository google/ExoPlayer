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
package com.google.android.exoplayer2.source.hls.offline;

import android.net.Uri;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.SegmentDownloadAction;
import com.google.android.exoplayer2.source.hls.playlist.RenditionKey;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** An action to download or remove downloaded HLS streams. */
public final class HlsDownloadAction extends SegmentDownloadAction<RenditionKey> {

  private static final String TYPE = "HlsDownloadAction";
  private static final int VERSION = 0;

  public static final Deserializer DESERIALIZER =
      new SegmentDownloadActionDeserializer<RenditionKey>(TYPE, VERSION) {

        @Override
        protected RenditionKey readKey(DataInputStream input) throws IOException {
          return new RenditionKey(input.readUTF());
        }

        @Override
        protected RenditionKey[] createKeyArray(int keyCount) {
          return new RenditionKey[keyCount];
        }

        @Override
        protected DownloadAction createDownloadAction(
            boolean isRemoveAction, String data, Uri manifestUri, RenditionKey[] keys) {
          return new HlsDownloadAction(isRemoveAction, data, manifestUri, keys);
        }
      };

  /**
   * @see SegmentDownloadAction#SegmentDownloadAction(String, int, boolean, String, Uri,
   *     Comparable[])
   */
  public HlsDownloadAction(
      boolean isRemoveAction, @Nullable String data, Uri manifestUri, RenditionKey... keys) {
    super(TYPE, VERSION, isRemoveAction, data, manifestUri, keys);
  }

  @Override
  protected HlsDownloader createDownloader(DownloaderConstructorHelper constructorHelper) {
    return new HlsDownloader(manifestUri, constructorHelper, keys);
  }

  @Override
  protected void writeKey(DataOutputStream output, RenditionKey key) throws IOException {
    output.writeUTF(key.url);
  }

}
