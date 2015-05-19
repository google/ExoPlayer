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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.util.Assertions;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A content URI {@link UriDataSource}.
 */
public final class ContentDataSource implements UriDataSource {

  /**
   * Thrown when an {@link IOException} is encountered reading from a content URI.
   */
  public static class ContentDataSourceException extends IOException {

    public ContentDataSourceException(IOException cause) {
      super(cause);
    }

  }

  private final ContentResolver resolver;
  private final TransferListener listener;

  private InputStream inputStream;
  private String uriString;
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
    this.resolver = context.getContentResolver();
    this.listener = listener;
  }

  @Override
  public long open(DataSpec dataSpec) throws ContentDataSourceException {
    try {
      uriString = dataSpec.uri.toString();
      AssetFileDescriptor assetFd = resolver.openAssetFileDescriptor(dataSpec.uri, "r");
      inputStream = new FileInputStream(assetFd.getFileDescriptor());
      long skipped = inputStream.skip(dataSpec.position);
      Assertions.checkState(skipped == dataSpec.position);
      bytesRemaining = dataSpec.length == C.LENGTH_UNBOUNDED ? inputStream.available()
          : dataSpec.length;
      if (bytesRemaining < 0) {
        throw new EOFException();
      }
    } catch (IOException e) {
      throw new ContentDataSourceException(e);
    }

    opened = true;
    if (listener != null) {
      listener.onTransferStart();
    }

    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws ContentDataSourceException {
    if (bytesRemaining == 0) {
      return -1;
    } else {
      int bytesRead = 0;
      try {
        bytesRead = inputStream.read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
      } catch (IOException e) {
        throw new ContentDataSourceException(e);
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
    return uriString;
  }

  @Override
  public void close() throws ContentDataSourceException {
    uriString = null;
    if (inputStream != null) {
      try {
        inputStream.close();
      } catch (IOException e) {
        throw new ContentDataSourceException(e);
      } finally {
        inputStream = null;
        if (opened) {
          opened = false;
          if (listener != null) {
            listener.onTransferEnd();
          }
        }
      }
    }
  }

}
