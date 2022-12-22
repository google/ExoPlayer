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
package androidx.media3.demo.transformer;

import android.graphics.Color;
import android.opengl.Matrix;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import androidx.media3.common.C;
import androidx.media3.common.util.GlUtil;
import androidx.media3.effect.OverlaySettings;
import androidx.media3.effect.TextOverlay;
import androidx.media3.effect.TextureOverlay;
import java.util.Locale;

/**
 * A {@link TextureOverlay} that displays a "time elapsed" timer in the bottom left corner of the
 * frame.
 */
/* package */ final class TimerOverlay extends TextOverlay {

  private final OverlaySettings overlaySettings;

  public TimerOverlay() {
    float[] positioningMatrix = GlUtil.create4x4IdentityMatrix();
    Matrix.translateM(
        positioningMatrix, /* mOffset= */ 0, /* x= */ -0.7f, /* y= */ -0.95f, /* z= */ 1);
    overlaySettings =
        new OverlaySettings.Builder()
            .setAnchor(/* x= */ -1f, /* y= */ -1f)
            .setMatrix(positioningMatrix)
            .build();
  }

  @Override
  public SpannableString getText(long presentationTimeUs) {
    SpannableString text =
        new SpannableString(
            String.format(Locale.US, "%.02f", presentationTimeUs / (float) C.MICROS_PER_SECOND));
    text.setSpan(
        new ForegroundColorSpan(Color.WHITE),
        /* start= */ 0,
        text.length(),
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return text;
  }

  @Override
  public OverlaySettings getOverlaySettings(long presentationTimeUs) {
    return overlaySettings;
  }
}
