package com.google.android.exoplayer2.upstream;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;

import static junit.framework.Assert.assertNotNull;

public class TestDataProvider extends ContentProvider {
  @Override
  public boolean onCreate() {
    return true;
  }

  @Nullable
  @Override
  public Cursor query(@NonNull final Uri uri, @Nullable final String[] projection, @Nullable final String selection, @Nullable final String[] selectionArgs, @Nullable final String sortOrder) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Nullable
  @Override
  public AssetFileDescriptor openAssetFile(@NonNull final Uri uri, @NonNull final String mode) throws FileNotFoundException {
    try {
      Context context = getContext();
      assertNotNull(context);
      return context.getAssets().openFd(uri.getPath().replaceFirst("/", ""));
    } catch (IOException e) {
      FileNotFoundException exception = new FileNotFoundException(e.getMessage());
      exception.initCause(e);
      throw exception;
    }
  }

  @Nullable
  @Override
  public String getType(@NonNull final Uri uri) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Nullable
  @Override
  public Uri insert(@NonNull final Uri uri, @Nullable final ContentValues values) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public int delete(@NonNull final Uri uri, @Nullable final String selection, @Nullable final String[] selectionArgs) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public int update(@NonNull final Uri uri, @Nullable final ContentValues values, @Nullable final String selection, @Nullable final String[] selectionArgs) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
