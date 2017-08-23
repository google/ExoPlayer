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
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.SegmentDownloadAction;
import com.google.android.exoplayer2.util.ClosedSource;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** An action to download or remove downloaded HLS streams. */
@ClosedSource(reason = "Not ready yet")
public final class HlsDownloadAction extends SegmentDownloadAction<String> {

  public static final Deserializer DESERIALIZER = new SegmentDownloadActionDeserializer<String>() {

    @Override
    public String getType() {
      return TYPE;
    }

    @Override
    protected String readKey(DataInputStream input) throws IOException {
      return input.readUTF();
    }

    @Override
    protected String[] createKeyArray(int keyCount) {
      return new String[0];
    }

    @Override
    protected DownloadAction createDownloadAction(Uri manifestUri, boolean removeAction,
        String[] keys) {
      return new HlsDownloadAction(manifestUri, removeAction, keys);
    }

  };

  private static final String TYPE = "HlsDownloadAction";

  /** @see SegmentDownloadAction#SegmentDownloadAction(Uri, boolean, Object[]) */
  public HlsDownloadAction(Uri manifestUri, boolean removeAction, String... keys) {
    super(manifestUri, removeAction, keys);
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public HlsDownloader createDownloader(DownloaderConstructorHelper constructorHelper)
      throws IOException {
    HlsDownloader downloader = new HlsDownloader(manifestUri, constructorHelper);
    if (!isRemoveAction()) {
      downloader.selectRepresentations(keys);
    }
    return downloader;
  }

  @Override
  protected void writeKey(DataOutputStream output, String key) throws IOException {
    output.writeUTF(key);
  }

}
