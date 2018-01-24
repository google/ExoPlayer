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
package com.google.android.exoplayer2.source.smoothstreaming.offline;

import android.net.Uri;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.SegmentDownloadAction;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.TrackKey;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** An action to download or remove downloaded SmoothStreaming streams. */
public final class SsDownloadAction extends SegmentDownloadAction<TrackKey> {

  public static final Deserializer DESERIALIZER =
      new SegmentDownloadActionDeserializer<TrackKey>() {

    @Override
    public String getType() {
      return TYPE;
    }

    @Override
    protected TrackKey readKey(DataInputStream input) throws IOException {
      return new TrackKey(input.readInt(), input.readInt());
    }

    @Override
    protected TrackKey[] createKeyArray(int keyCount) {
      return new TrackKey[keyCount];
    }

    @Override
    protected DownloadAction createDownloadAction(Uri manifestUri, boolean removeAction,
        String data, TrackKey[] keys) {
      return new SsDownloadAction(manifestUri, removeAction, data, keys);
    }

  };

  private static final String TYPE = "SsDownloadAction";

  /** @see SegmentDownloadAction#SegmentDownloadAction(Uri, boolean, String, Object[]) */
  public SsDownloadAction(Uri manifestUri, boolean removeAction, String data, TrackKey... keys) {
    super(manifestUri, removeAction, data, keys);
  }

  @Override
  protected String getType() {
    return TYPE;
  }

  @Override
  protected SsDownloader createDownloader(DownloaderConstructorHelper constructorHelper) {
    SsDownloader downloader = new SsDownloader(manifestUri, constructorHelper);
    if (!isRemoveAction()) {
      downloader.selectRepresentations(keys);
    }
    return downloader;
  }

  @Override
  protected void writeKey(DataOutputStream output, TrackKey key) throws IOException {
    output.writeInt(key.streamElementIndex);
    output.writeInt(key.trackIndex);
  }

}
