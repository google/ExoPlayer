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
package androidx.media3.datasource;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.isBitmapFactorySupportedMimeType;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.UnstableApi;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;

/**
 * A {@link BitmapLoader} implementation that uses a {@link DataSource} to support fetching images
 * from URIs and {@link BitmapFactory} to load them into {@link Bitmap}.
 *
 * <p>Loading tasks are delegated to a {@link ListeningExecutorService} defined during construction.
 * If no executor service is passed, all tasks are delegated to a single-thread executor service
 * that is shared between instances of this class.
 */
@UnstableApi
public final class DataSourceBitmapLoader implements BitmapLoader {

  public static final Supplier<ListeningExecutorService> DEFAULT_EXECUTOR_SERVICE =
      Suppliers.memoize(
          () -> MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()));

  private final ListeningExecutorService listeningExecutorService;
  private final DataSource.Factory dataSourceFactory;
  @Nullable private final BitmapFactory.Options options;

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
    this(listeningExecutorService, dataSourceFactory, /* options= */ null);
  }

  /**
   * Creates an instance that delegates loading tasks to the {@link ListeningExecutorService}.
   *
   * @param listeningExecutorService The {@link ListeningExecutorService}.
   * @param dataSourceFactory The {@link DataSource.Factory} that creates the {@link DataSource}
   *     used to load the image.
   * @param options The {@link BitmapFactory.Options} the image should be loaded with.
   */
  public DataSourceBitmapLoader(
      ListeningExecutorService listeningExecutorService,
      DataSource.Factory dataSourceFactory,
      @Nullable BitmapFactory.Options options) {
    this.listeningExecutorService = listeningExecutorService;
    this.dataSourceFactory = dataSourceFactory;
    this.options = options;
  }

  @Override
  public boolean supportsMimeType(String mimeType) {
    return isBitmapFactorySupportedMimeType(mimeType);
  }

  @Override
  public ListenableFuture<Bitmap> decodeBitmap(byte[] data) {
    return listeningExecutorService.submit(() -> decode(data, options));
  }

  @Override
  public ListenableFuture<Bitmap> loadBitmap(Uri uri) {
    return listeningExecutorService.submit(
        () -> load(dataSourceFactory.createDataSource(), uri, options));
  }

  // BitmapFactory's options parameter is null-ok.
  @SuppressWarnings("nullness:argument.type.incompatible")
  private static Bitmap decode(byte[] data, @Nullable BitmapFactory.Options options)
      throws IOException {
    @Nullable
    Bitmap bitmap = BitmapFactory.decodeByteArray(data, /* offset= */ 0, data.length, options);
    checkArgument(bitmap != null, "Could not decode image data");
    ExifInterface exifInterface;
    try (InputStream inputStream = new ByteArrayInputStream(data)) {
      exifInterface = new ExifInterface(inputStream);
    }
    int rotationDegrees = exifInterface.getRotationDegrees();
    if (rotationDegrees != 0) {
      Matrix matrix = new Matrix();
      matrix.postRotate(rotationDegrees);
      bitmap =
          Bitmap.createBitmap(
              bitmap,
              /* x= */ 0,
              /* y= */ 0,
              bitmap.getWidth(),
              bitmap.getHeight(),
              matrix,
              /* filter= */ false);
    }
    return bitmap;
  }

  private static Bitmap load(
      DataSource dataSource, Uri uri, @Nullable BitmapFactory.Options options) throws IOException {
    try {
      DataSpec dataSpec = new DataSpec(uri);
      dataSource.open(dataSpec);
      byte[] readData = DataSourceUtil.readToEnd(dataSource);
      return decode(readData, options);
    } finally {
      dataSource.close();
    }
  }
}
