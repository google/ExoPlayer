/*
 * Copyright 2023 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.BitmapLoader;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.concurrent.Executors;

/**
 * A {@link BitmapLoader} implementation that uses a {@link DataSource} to support fetching images
 * from URIs.
 *
 * <p>Loading tasks are delegated to a {@link ListeningExecutorService} defined during construction.
 * If no executor service is passed, all tasks are delegated to a single-thread executor service
 * that is shared between instances of this class.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class DataSourceBitmapLoader implements BitmapLoader {

  public static final Supplier<ListeningExecutorService> DEFAULT_EXECUTOR_SERVICE =
      Suppliers.memoize(
          () -> MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()));

  private final ListeningExecutorService listeningExecutorService;
  private final DataSource.Factory dataSourceFactory;

  /**
   * Creates an instance that uses a {@link DefaultHttpDataSource} for image loading and delegates
   * loading tasks to a {@link Executors#newSingleThreadExecutor()}.
   */
  public DataSourceBitmapLoader(Context context) {
    this(checkStateNotNull(DEFAULT_EXECUTOR_SERVICE.get()), new DefaultDataSource.Factory(context));
  }

  /**
   * Creates an instance that delegates loading tasks to the {@link ListeningExecutorService}.
   *
   * @param listeningExecutorService The {@link ListeningExecutorService}.
   * @param dataSourceFactory The {@link DataSource.Factory} that creates the {@link DataSource}
   *     used to load the image.
   */
  public DataSourceBitmapLoader(
      ListeningExecutorService listeningExecutorService, DataSource.Factory dataSourceFactory) {
    this.listeningExecutorService = listeningExecutorService;
    this.dataSourceFactory = dataSourceFactory;
  }

  @Override
  public ListenableFuture<Bitmap> decodeBitmap(byte[] data) {
    return listeningExecutorService.submit(() -> decode(data));
  }

  /** Loads an image from a {@link Uri}. */
  @Override
  public ListenableFuture<Bitmap> loadBitmap(Uri uri) {
    return listeningExecutorService.submit(() -> load(dataSourceFactory.createDataSource(), uri));
  }

  private static Bitmap decode(byte[] data) {
    @Nullable Bitmap bitmap = BitmapFactory.decodeByteArray(data, /* offset= */ 0, data.length);
    checkArgument(bitmap != null, "Could not decode image data");
    return bitmap;
  }

  private static Bitmap load(DataSource dataSource, Uri uri) throws IOException {
    DataSpec dataSpec = new DataSpec(uri);
    dataSource.open(dataSpec);
    byte[] readData = DataSourceUtil.readToEnd(dataSource);
    return decode(readData);
  }
}
