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

  public static final Deserializer DESERIALIZER =
      new SegmentDownloadActionDeserializer<RenditionKey>() {

        @Override
        public String getType() {
          return TYPE;
        }

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
            Uri manifestUri, boolean removeAction, String data, RenditionKey[] keys) {
          return new HlsDownloadAction(manifestUri, removeAction, data, keys);
        }
      };

  private static final String TYPE = "HlsDownloadAction";

  /** @see SegmentDownloadAction#SegmentDownloadAction(Uri, boolean, String, Object[]) */
  public HlsDownloadAction(
      Uri manifestUri, boolean removeAction, @Nullable String data, RenditionKey... keys) {
    super(manifestUri, removeAction, data, keys);
  }

  @Override
  protected String getType() {
    return TYPE;
  }

  @Override
  protected HlsDownloader createDownloader(DownloaderConstructorHelper constructorHelper) {
    HlsDownloader downloader = new HlsDownloader(manifestUri, constructorHelper);
    if (!isRemoveAction()) {
      downloader.selectRepresentations(keys);
    }
    return downloader;
  }

  @Override
  protected void writeKey(DataOutputStream output, RenditionKey key) throws IOException {
    output.writeUTF(key.url);
  }

}
