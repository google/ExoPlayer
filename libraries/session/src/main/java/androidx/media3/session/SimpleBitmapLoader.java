/*
 * Copyright 2022 The Android Open Source Project
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
 * limitations under the License
 */
package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.isBitmapFactorySupportedMimeType;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.UnstableApi;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @deprecated Use {@link androidx.media3.datasource.DataSourceBitmapLoader} instead.
 */
@Deprecated
@UnstableApi
public final class SimpleBitmapLoader implements BitmapLoader {

  private static final String FILE_URI_EXCEPTION_MESSAGE = "Could not read image from file";

  private static final Supplier<ListeningExecutorService> DEFAULT_EXECUTOR_SERVICE =
      Suppliers.memoize(
          () -> MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()));

  private final ListeningExecutorService executorService;

  /**
   * Creates an instance that delegates all load tasks to a single-thread executor service shared
   * between instances.
   */
  public SimpleBitmapLoader() {
    this(checkStateNotNull(DEFAULT_EXECUTOR_SERVICE.get()));
  }

  /** Creates an instance that delegates loading tasks to the {@code executorService}. */
  public SimpleBitmapLoader(ExecutorService executorService) {
    this.executorService = MoreExecutors.listeningDecorator(executorService);
  }

  @Override
  public boolean supportsMimeType(String mimeType) {
    return isBitmapFactorySupportedMimeType(mimeType);
  }

  @Override
  public ListenableFuture<Bitmap> decodeBitmap(byte[] data) {
    return executorService.submit(() -> decode(data));
  }

  @Override
  public ListenableFuture<Bitmap> loadBitmap(Uri uri) {
    return executorService.submit(() -> load(uri));
  }

  private static Bitmap decode(byte[] data) {
    @Nullable Bitmap bitmap = BitmapFactory.decodeByteArray(data, /* offset= */ 0, data.length);
    checkArgument(bitmap != null, "Could not decode image data");
    return bitmap;
  }

  private static Bitmap load(Uri uri) throws IOException {
    if ("file".equals(uri.getScheme())) {
      @Nullable String path = uri.getPath();
      if (path == null) {
        throw new IllegalArgumentException(FILE_URI_EXCEPTION_MESSAGE);
      }
      @Nullable Bitmap bitmap = BitmapFactory.decodeFile(path);
      if (bitmap == null) {
        throw new IllegalArgumentException(FILE_URI_EXCEPTION_MESSAGE);
      }
      return bitmap;
    }
    URLConnection connection = new URL(uri.toString()).openConnection();
    if (!(connection instanceof HttpURLConnection)) {
      throw new UnsupportedOperationException("Unsupported scheme: " + uri.getScheme());
    }
    HttpURLConnection httpConnection = (HttpURLConnection) connection;
    httpConnection.connect();
    int responseCode = httpConnection.getResponseCode();
    if (responseCode != HttpURLConnection.HTTP_OK) {
      throw new IOException("Invalid response status code: " + responseCode);
    }
    try (InputStream inputStream = httpConnection.getInputStream()) {
      return decode(ByteStreams.toByteArray(inputStream));
    }
  }
}
