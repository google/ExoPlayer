/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;
import com.google.android.exoplayer2.C;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Utility methods for {@link DataSource}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class DataSourceUtil {
  public static final String BITMAP_DECODING_EXCEPTION_MESSAGE = "Could not decode image data";

  private DataSourceUtil() {}

  /**
   * Reads data from the specified opened {@link DataSource} until it ends, and returns a byte array
   * containing the read data.
   *
   * @param dataSource The source from which to read.
   * @return The concatenation of all read data.
   * @throws IOException If an error occurs reading from the source.
   */
  public static byte[] readToEnd(DataSource dataSource) throws IOException {
    byte[] data = new byte[1024];
    int position = 0;
    int bytesRead = 0;
    while (bytesRead != C.RESULT_END_OF_INPUT) {
      if (position == data.length) {
        data = Arrays.copyOf(data, data.length * 2);
      }
      bytesRead = dataSource.read(data, position, data.length - position);
      if (bytesRead != C.RESULT_END_OF_INPUT) {
        position += bytesRead;
      }
    }
    return Arrays.copyOf(data, position);
  }

  /**
   * Reads {@code length} bytes from the specified opened {@link DataSource}, and returns a byte
   * array containing the read data.
   *
   * @param dataSource The source from which to read.
   * @param length The number of bytes to read.
   * @return The read data.
   * @throws IOException If an error occurs reading from the source.
   * @throws IllegalStateException If the end of the source was reached before {@code length} bytes
   *     could be read.
   */
  public static byte[] readExactly(DataSource dataSource, int length) throws IOException {
    byte[] data = new byte[length];
    int position = 0;
    while (position < length) {
      int bytesRead = dataSource.read(data, position, data.length - position);
      if (bytesRead == C.RESULT_END_OF_INPUT) {
        throw new IllegalStateException(
            "Not enough data could be read: " + position + " < " + length);
      }
      position += bytesRead;
    }
    return data;
  }

  /**
   * Closes a {@link DataSource}, suppressing any {@link IOException} that may occur.
   *
   * @param dataSource The {@link DataSource} to close.
   */
  public static void closeQuietly(@Nullable DataSource dataSource) {
    try {
      if (dataSource != null) {
        dataSource.close();
      }
    } catch (IOException e) {
      // Ignore.
    }
  }

  /**
   * Decodes a {@link Bitmap} from a byte array using {@link BitmapFactory} and the {@link
   * ExifInterface}.
   */
  // BitmapFactory's options parameter is null-ok.
  @SuppressWarnings("nullness:argument.type.incompatible")
  public static Bitmap decode(byte[] data, int length, @Nullable BitmapFactory.Options options)
      throws IOException {
    @Nullable Bitmap bitmap = BitmapFactory.decodeByteArray(data, /* offset= */ 0, length, options);
    checkArgument(bitmap != null, BITMAP_DECODING_EXCEPTION_MESSAGE);
    // BitmapFactory doesn't read the exif header, so we use the ExifInterface to this do ensure the
    // bitmap is correctly orientated.
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
}
