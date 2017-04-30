/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.rtmp;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import net.butterflytv.rtmp_client.RtmpClient;

import java.io.IOException;

/**
 * A Real-Time Messaging Protocol (RTMP) {@link DataSource}.
 */
public final class RtmpDataSource implements DataSource {
  private final RtmpClient rtmpClient;
  private Uri uri;

  public RtmpDataSource() {
    rtmpClient = new RtmpClient();
  }

  @Override
  public Uri getUri() {
    return uri;
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    uri = dataSpec.uri;
    int result = rtmpClient.open(dataSpec.uri.toString(), false);
    if (result < 0) {
      return 0;
    }
    return C.LENGTH_UNSET;
  }

  @Override
  public void close() throws IOException {
    rtmpClient.close();
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    return rtmpClient.read(buffer, offset, readLength);
  }

  public final static class RtmpDataSourceFactory implements DataSource.Factory {
    @Override
    public DataSource createDataSource() {
      return new RtmpDataSource();
    }
  }
}
