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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import android.content.res.AssetManager;
import android.util.Log;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.upstream.FileDataSource.FileDataSourceException;

/**
 * A local asset {@link DataSource}.
 */
public final class AssetDataSource implements DataSource {

  /**
   * Thrown when IOException is encountered during local asset read operation.
   */
  public static class AssetDataSourceException extends IOException {

    public AssetDataSourceException(IOException cause) {
      super(cause);
    }

  }

  private final TransferListener listener;

  private InputStream assetInputStream;
  private AssetManager assetManager;
  private long bytesRemaining;
  private boolean opened;

  /**
   * Constructs a new {@link DataSource} that retrieves data from a local asset.
   */
  public AssetDataSource(AssetManager assetManager) {
	  this(assetManager, null);
  }

  /**
   * Constructs a new {@link DataSource} that retrieves data from a local asset.
   *
   * @param listener An optional listener. Specify {@code null} for no listener.
   */
  public AssetDataSource(AssetManager assetManager, TransferListener listener) {
    this.assetManager = assetManager;
    this.listener = listener;
  }

  @Override
  public long open(DataSpec dataSpec) throws AssetDataSourceException {
    try {
      // Lose the '/' prefix in the path or else AssetManager won't find our file
      assetInputStream = assetManager.open(dataSpec.uri.getPath().substring(1), AssetManager.ACCESS_RANDOM);
      assetInputStream.skip(dataSpec.position);
      bytesRemaining = dataSpec.length == C.LENGTH_UNBOUNDED ? assetInputStream.available()
          : dataSpec.length;
      if (bytesRemaining < 0) {
        throw new EOFException();
      }
    } catch (IOException e) {
      throw new AssetDataSourceException(e);
    }

    opened = true;
    if (listener != null) {
      listener.onTransferStart();
    }
    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws AssetDataSourceException {
    if (bytesRemaining == 0) {
      return -1;
    } else {
      int bytesRead = 0;
      try {
        bytesRead = assetInputStream.read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
      } catch (IOException e) {
        throw new AssetDataSourceException(e);
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
  public void close() throws AssetDataSourceException {
    if (assetInputStream != null) {
      try {
    	  assetInputStream.close();
      } catch (IOException e) {
        throw new AssetDataSourceException(e);
      } finally {
    	  assetInputStream = null;

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
