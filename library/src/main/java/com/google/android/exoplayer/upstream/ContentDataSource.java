/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.upstream;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;

import com.google.android.exoplayer.C;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * This calll is support content uri and file uri (e.q. content:// , file://). 
 * {@link DataSource}.
 */
public final class ContentDataSource implements UriDataSource {
  static private final String TAG = "ContentDataSource";
  /**
   * Thrown when IOException is encountered during local asset read operation.
   */
  public static class ContentDataSourceException extends IOException {

    public ContentDataSourceException(IOException cause) {
      super(cause);
    }

  }

  private final TransferListener listener;

  private InputStream inputStream;
  private Context context;
  private String uri;
  private long bytesRemaining;
  private boolean opened;

  /**
   * Constructs a new {@link DataSource} that retrieves data from a content provider.
   */
  public ContentDataSource(Context context) {
    this(context, null);
  }

  /**
   * Constructs a new {@link DataSource} that retrieves data from a content provider.
   *
   * @param listener An optional listener. Specify {@code null} for no listener.
   */
  public ContentDataSource(Context context, TransferListener listener) {
    this.context = context;
    this.listener = listener;
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    try {
      uri = dataSpec.uri.toString();
      inputStream = new FileInputStream(getFileDescriptor(context, dataSpec));
      inputStream.skip(dataSpec.position);
      bytesRemaining = dataSpec.length == C.LENGTH_UNBOUNDED ? inputStream.available()
          : dataSpec.length;
      if (bytesRemaining < 0) {
        throw new IOException();
      }
    } catch (IOException e) {
      throw new IOException(e);
    }

    opened = true;
    if (listener != null) {
      listener.onTransferStart();
    }

    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    if (bytesRemaining == 0) {
      return -1;
    } else {
      int bytesRead = 0;
      try {
        bytesRead = inputStream.read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
      } catch (IOException e) {
        throw new IOException(e);
      }

      if (bytesRead > 0) {
        bytesRemaining -= bytesRead;
        if (listener != null) {
          listener.onBytesTransferred(bytesRead);
        }
      }

      return bytesRead;
    }
  }

  @Override
  public String getUri() {
    return uri;
  }

  @Override
  public void close() throws ContentDataSourceException {
    if (inputStream != null) {
      try {
        inputStream.close();
      } catch (IOException e) {
        throw new ContentDataSourceException(e);
      } finally {
        inputStream = null;
        uri = null;

        if (opened) {
          opened = false;
          if (listener != null) {
            listener.onTransferEnd();
          }
        }
      }
    }
  }

  /**
   * query the fileDescriptor from conten resolver.
   *
   */
  private static FileDescriptor getFileDescriptor(Context context, DataSpec dataSpec) throws IOException {
    try {
      ContentResolver resolver = context.getContentResolver();
      AssetFileDescriptor fd = resolver.openAssetFileDescriptor(dataSpec.uri, "r");
      return fd.getFileDescriptor();
    } catch (IOException e) {
      throw new IOException(e);
    }
  }
}
