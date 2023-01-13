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
 * limitations under the License.
 */
package androidx.media3.effect;

import android.graphics.Bitmap;
import android.graphics.Matrix;

/** Utility functions for working with {@link Bitmap}. */
/* package */ final class BitmapUtil {
  public static Bitmap flipBitmapVertically(Bitmap bitmap) {
    Matrix flip = new Matrix();
    flip.postScale(1f, -1f);
    return Bitmap.createBitmap(
        bitmap,
        /* x= */ 0,
        /* y= */ 0,
        bitmap.getWidth(),
        bitmap.getHeight(),
        flip,
        /* filter= */ true);
  }

  /** Class only contains static methods. */
  private BitmapUtil() {}
}
