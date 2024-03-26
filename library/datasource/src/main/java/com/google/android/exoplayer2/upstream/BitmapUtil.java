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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;
import com.google.android.exoplayer2.ParserException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility methods for {@link Bitmap} instances.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class BitmapUtil {

  private BitmapUtil() {}

  /**
   * Decodes a {@link Bitmap} from a byte array using {@link BitmapFactory} and the {@link
   * ExifInterface}.
   *
   * @param data Byte array of compressed image data.
   * @param length The number of bytes to parse.
   * @param options the {@link BitmapFactory.Options} to decode the {@code data} with.
   * @throws ParserException if the {@code data} could not be decoded.
   */
  // BitmapFactory's options parameter is null-ok.
  @SuppressWarnings("nullness:argument.type.incompatible")
  public static Bitmap decode(byte[] data, int length, @Nullable BitmapFactory.Options options)
      throws IOException {
    @Nullable Bitmap bitmap = BitmapFactory.decodeByteArray(data, /* offset= */ 0, length, options);
    if (bitmap == null) {
      throw ParserException.createForMalformedContainer(
          "Could not decode image data", new IllegalStateException());
    }
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
