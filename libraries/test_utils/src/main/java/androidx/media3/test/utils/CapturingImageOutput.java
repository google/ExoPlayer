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
package androidx.media3.test.utils;

import android.graphics.Bitmap;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.image.ImageOutput;
import androidx.media3.test.utils.Dumper.Dumpable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A {@link ImageOutput} that captures image availability events. */
@UnstableApi
public final class CapturingImageOutput implements Dumpable, ImageOutput {

  private final List<Dumpable> renderedBitmaps;

  private int imageCount;

  public CapturingImageOutput() {
    renderedBitmaps = new ArrayList<>();
  }

  @Override
  public void onImageAvailable(long presentationTimeUs, Bitmap bitmap) {
    imageCount++;
    int currentImageCount = imageCount;
    int[] bitmapPixels = new int[bitmap.getWidth() * bitmap.getHeight()];
    bitmap.getPixels(
        bitmapPixels,
        /* offset= */ 0,
        /* stride= */ bitmap.getWidth(),
        /* x= */ 0,
        /* y= */ 0,
        bitmap.getWidth(),
        bitmap.getHeight());
    renderedBitmaps.add(
        dumper -> {
          dumper.startBlock("image output #" + currentImageCount);
          dumper.add("presentationTimeUs", presentationTimeUs);
          dumper.add("bitmap hash", Arrays.hashCode(bitmapPixels));
          dumper.endBlock();
        });
  }

  @Override
  public void onDisabled() {
    // Do nothing.
  }

  @Override
  public void dump(Dumper dumper) {
    if (imageCount > 0) {
      dumper.startBlock("ImageOutput");
      dumper.add("rendered image count", imageCount);
      for (Dumpable dumpable : renderedBitmaps) {
        dumpable.dump(dumper);
      }
      dumper.endBlock();
    }
  }
}
