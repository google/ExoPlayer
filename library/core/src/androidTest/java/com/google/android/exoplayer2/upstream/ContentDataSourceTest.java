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

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.test.InstrumentationTestCase;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Unit tests for {@link ContentDataSource}.
 */
public final class ContentDataSourceTest extends InstrumentationTestCase {

  private static final String AUTHORITY = "com.google.android.exoplayer2.core.test";
  private static final String DATA_PATH = "binary/1024_incrementing_bytes.mp3";

  public void testReadValidUri() throws Exception {
    ContentDataSource dataSource = new ContentDataSource(getInstrumentation().getContext());
    Uri contentUri = new Uri.Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority(AUTHORITY)
        .path(DATA_PATH).build();
    DataSpec dataSpec = new DataSpec(contentUri);
    TestUtil.assertDataSourceContent(dataSource, dataSpec,
        TestUtil.getByteArray(getInstrumentation(), DATA_PATH));
  }

  public void testReadInvalidUri() throws Exception {
    ContentDataSource dataSource = new ContentDataSource(getInstrumentation().getContext());
    Uri contentUri = new Uri.Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority(AUTHORITY)
        .build();
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

  /**
   * A {@link ContentProvider} for the test.
   */
  public static final class TestContentProvider extends ContentProvider {

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
        return getContext().getAssets().openFd(uri.getPath().replaceFirst("/", ""));
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
    public int delete(@NonNull Uri uri, String selection,
        String[] selectionArgs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values,
        String selection, String[] selectionArgs) {
      throw new UnsupportedOperationException();
    }

  }

}
