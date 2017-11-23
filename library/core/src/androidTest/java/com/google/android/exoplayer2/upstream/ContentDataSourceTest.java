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
package com.google.android.exoplayer2.upstream;

import android.app.Instrumentation;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.test.InstrumentationTestCase;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Unit tests for {@link ContentDataSource}.
 */
public final class ContentDataSourceTest extends InstrumentationTestCase {

  private static final String AUTHORITY = "com.google.android.exoplayer2.core.test";
  private static final String DATA_PATH = "binary/1024_incrementing_bytes.mp3";

  public void testRead() throws Exception {
    assertData(getInstrumentation(), 0, C.LENGTH_UNSET, false);
  }

  public void testReadPipeMode() throws Exception {
    assertData(getInstrumentation(), 0, C.LENGTH_UNSET, true);
  }

  public void testReadFixedLength() throws Exception {
    assertData(getInstrumentation(), 0, 100, false);
  }

  public void testReadFromOffsetToEndOfInput() throws Exception {
    assertData(getInstrumentation(), 1, C.LENGTH_UNSET, false);
  }

  public void testReadFromOffsetToEndOfInputPipeMode() throws Exception {
    assertData(getInstrumentation(), 1, C.LENGTH_UNSET, true);
  }

  public void testReadFromOffsetFixedLength() throws Exception {
    assertData(getInstrumentation(), 1, 100, false);
  }

  public void testReadInvalidUri() throws Exception {
    ContentDataSource dataSource = new ContentDataSource(getInstrumentation().getContext());
    Uri contentUri = TestContentProvider.buildUri("does/not.exist", false);
    DataSpec dataSpec = new DataSpec(contentUri);
    try {
      dataSource.open(dataSpec);
      fail();
    } catch (ContentDataSource.ContentDataSourceException e) {
      // Expected.
      assertTrue(e.getCause() instanceof FileNotFoundException);
    } finally {
      dataSource.close();
    }
  }

  private static void assertData(Instrumentation instrumentation, int offset, int length,
      boolean pipeMode) throws IOException {
    Uri contentUri = TestContentProvider.buildUri(DATA_PATH, pipeMode);
    ContentDataSource dataSource = new ContentDataSource(instrumentation.getContext());
    try {
      DataSpec dataSpec = new DataSpec(contentUri, offset, length, null);
      byte[] completeData = TestUtil.getByteArray(instrumentation, DATA_PATH);
      byte[] expectedData = Arrays.copyOfRange(completeData, offset,
          length == C.LENGTH_UNSET ? completeData.length : offset + length);
      TestUtil.assertDataSourceContent(dataSource, dataSpec, expectedData, !pipeMode);
    } finally {
      dataSource.close();
    }
  }

  /**
   * A {@link ContentProvider} for the test.
   */
  public static final class TestContentProvider extends ContentProvider
      implements ContentProvider.PipeDataWriter<Object> {

    private static final String PARAM_PIPE_MODE = "pipe-mode";

    public static Uri buildUri(String filePath, boolean pipeMode) {
      Uri.Builder builder = new Uri.Builder()
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
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
        String[] selectionArgs, String sortOrder) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode)
        throws FileNotFoundException {
      if (uri.getPath() == null) {
        return null;
      }
      try {
        String fileName = getFileName(uri);
        boolean pipeMode = uri.getQueryParameter(PARAM_PIPE_MODE) != null;
        if (pipeMode) {
          ParcelFileDescriptor fileDescriptor = openPipeHelper(uri, null, null, null, this);
          return new AssetFileDescriptor(fileDescriptor, 0, C.LENGTH_UNSET);
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
    public String getType(@NonNull Uri uri) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection,
        String[] selectionArgs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void writeDataToPipe(@NonNull ParcelFileDescriptor output, @NonNull Uri uri,
        @NonNull String mimeType, @Nullable Bundle opts, @Nullable Object args) {
      try {
        byte[] data = TestUtil.getByteArray(getContext(), getFileName(uri));
        FileOutputStream outputStream = new FileOutputStream(output.getFileDescriptor());
        outputStream.write(data);
        outputStream.close();
      } catch (IOException e) {
        throw new RuntimeException("Error writing to pipe", e);
      }
    }

    private static String getFileName(Uri uri) {
      return uri.getPath().replaceFirst("/", "");
    }

  }

}
