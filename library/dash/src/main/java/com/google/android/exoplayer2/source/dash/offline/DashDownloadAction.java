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
package com.google.android.exoplayer2.source.dash.offline;

import android.net.Uri;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.SegmentDownloadAction;
import com.google.android.exoplayer2.source.dash.manifest.RepresentationKey;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** An action to download or remove downloaded DASH streams. */
public final class DashDownloadAction extends SegmentDownloadAction<RepresentationKey> {

  public static final Deserializer DESERIALIZER =
      new SegmentDownloadActionDeserializer<RepresentationKey>() {

    @Override
    public String getType() {
      return TYPE;
    }

    @Override
    protected RepresentationKey readKey(DataInputStream input) throws IOException {
      return new RepresentationKey(input.readInt(), input.readInt(), input.readInt());
    }

    @Override
    protected RepresentationKey[] createKeyArray(int keyCount) {
      return new RepresentationKey[keyCount];
    }

    @Override
    protected DownloadAction createDownloadAction(Uri manifestUri, boolean removeAction,
        String data, RepresentationKey[] keys) {
      return new DashDownloadAction(manifestUri, removeAction, data, keys);
    }

  };

  private static final String TYPE = "DashDownloadAction";

  /** @see SegmentDownloadAction#SegmentDownloadAction(Uri, boolean, String, Object[]) */
  public DashDownloadAction(Uri manifestUri, boolean removeAction, String data,
      RepresentationKey... keys) {
    super(manifestUri, removeAction, data, keys);
  }

  @Override
  protected String getType() {
    return TYPE;
  }

  @Override
  protected DashDownloader createDownloader(DownloaderConstructorHelper constructorHelper) {
    DashDownloader downloader = new DashDownloader(manifestUri, constructorHelper);
    if (!isRemoveAction()) {
      downloader.selectRepresentations(keys);
    }
    return downloader;
  }

  @Override
  protected void writeKey(DataOutputStream output, RepresentationKey key) throws IOException {
    output.writeInt(key.periodIndex);
    output.writeInt(key.adaptationSetIndex);
    output.writeInt(key.representationIndex);
  }

}
