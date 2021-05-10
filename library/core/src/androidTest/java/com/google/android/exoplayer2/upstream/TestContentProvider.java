/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.OsConstants;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/** A {@link ContentProvider} for tests of {@link ContentDataSource}. */
public final class TestContentProvider extends ContentProvider
    implements ContentProvider.PipeDataWriter<Object> {

  private static final String AUTHORITY = "com.google.android.exoplayer2.core.test";
  private static final String PARAM_PIPE_MODE = "pipe-mode";

  public static Uri buildUri(String filePath, boolean pipeMode) {
    Uri.Builder builder =
        new Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(AUTHORITY)
            .path(filePath);
    if (pipeMode) {
      builder.appendQueryParameter(TestContentProvider.PARAM_PIPE_MODE, "1");
    }
    return builder.build();
  }

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public Cursor query(
      Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
    if (uri.getPath() == null) {
      return null;
    }
    try {
      String fileName = getFileName(uri);
      boolean pipeMode = uri.getQueryParameter(PARAM_PIPE_MODE) != null;
      if (pipeMode) {
        ParcelFileDescriptor fileDescriptor =
            openPipeHelper(
                uri, /* mimeType= */ null, /* opts= */ null, /* args= */ null, /* func= */ this);
        return new AssetFileDescriptor(
            fileDescriptor, /* startOffset= */ 0, AssetFileDescriptor.UNKNOWN_LENGTH);
      } else {
        return getContext().getAssets().openFd(fileName);
      }
    } catch (IOException e) {
      FileNotFoundException exception = new FileNotFoundException(e.getMessage());
      exception.initCause(e);
      throw exception;
    }
  }

  @Override
  public String getType(Uri uri) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void writeDataToPipe(
      ParcelFileDescriptor output,
      Uri uri,
      String mimeType,
      @Nullable Bundle opts,
      @Nullable Object args) {
    try (FileOutputStream outputStream = new FileOutputStream(output.getFileDescriptor())) {
      byte[] data = TestUtil.getByteArray(getContext(), getFileName(uri));
      outputStream.write(data);
    } catch (IOException e) {
      if (e.getCause() instanceof ErrnoException
          && ((ErrnoException) e.getCause()).errno == OsConstants.EPIPE) {
        // Swallow the exception if it's caused by a broken pipe - this indicates the reader has
        // closed the pipe and is therefore no longer interested in the data being written.
        // [See internal b/186728171].
        return;
      }
      throw new RuntimeException("Error writing to pipe", e);
    }
  }

  private static String getFileName(Uri uri) {
    return uri.getPath().replaceFirst("/", "");
  }

}
