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
package com.google.android.exoplayer2.transformerdemo;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.effect.SingleFrameGlTextureProcessor;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.android.exoplayer2.util.GlProgram;
import com.google.android.exoplayer2.util.GlUtil;
import java.io.IOException;
import java.util.Locale;

/**
 * A {@link SingleFrameGlTextureProcessor} that overlays a bitmap with a logo and timer on each
 * frame.
 *
 * <p>The bitmap is drawn using an Android {@link Canvas}.
 */
// TODO(b/227625365): Delete this class and use a texture processor from the Transformer library,
//  once overlaying a bitmap and text is supported in Transformer.
/* package */ final class BitmapOverlayProcessor extends SingleFrameGlTextureProcessor {

  private static final String VERTEX_SHADER_PATH = "vertex_shader_copy_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH = "fragment_shader_bitmap_overlay_es2.glsl";

  private static final int BITMAP_WIDTH_HEIGHT = 512;

  private final Paint paint;
  private final Bitmap overlayBitmap;
  private final Bitmap logoBitmap;
  private final Canvas overlayCanvas;
  private final GlProgram glProgram;

  private float bitmapScaleX;
  private float bitmapScaleY;
  private int bitmapTexId;

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @throws FrameProcessingException If a problem occurs while reading shader files.
   */
  public BitmapOverlayProcessor(Context context, boolean useHdr) throws FrameProcessingException {
    super(useHdr);
    checkArgument(!useHdr, "BitmapOverlayProcessor does not support HDR colors.");
    paint = new Paint();
    paint.setTextSize(64);
    paint.setAntiAlias(true);
    paint.setARGB(0xFF, 0xFF, 0xFF, 0xFF);
    paint.setColor(Color.GRAY);
    overlayBitmap =
        Bitmap.createBitmap(BITMAP_WIDTH_HEIGHT, BITMAP_WIDTH_HEIGHT, Bitmap.Config.ARGB_8888);
    overlayCanvas = new Canvas(overlayBitmap);

    try {
      logoBitmap =
          ((BitmapDrawable)
                  context.getPackageManager().getApplicationIcon(context.getPackageName()))
              .getBitmap();
    } catch (PackageManager.NameNotFoundException e) {
      throw new IllegalStateException(e);
    }
    try {
      bitmapTexId =
          GlUtil.createTexture(
              BITMAP_WIDTH_HEIGHT,
              BITMAP_WIDTH_HEIGHT,
              /* useHighPrecisionColorComponents= */ false);
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, /* level= */ 0, overlayBitmap, /* border= */ 0);

      glProgram = new GlProgram(context, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
    } catch (GlUtil.GlException | IOException e) {
      throw new FrameProcessingException(e);
    }
    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
    glProgram.setSamplerTexIdUniform("uTexSampler1", bitmapTexId, /* texUnitIndex= */ 1);
  }

  @Override
  public Pair<Integer, Integer> configure(int inputWidth, int inputHeight) {
    if (inputWidth > inputHeight) {
      bitmapScaleX = inputWidth / (float) inputHeight;
      bitmapScaleY = 1f;
    } else {
      bitmapScaleX = 1f;
      bitmapScaleY = inputHeight / (float) inputWidth;
    }

    glProgram.setFloatUniform("uScaleX", bitmapScaleX);
    glProgram.setFloatUniform("uScaleY", bitmapScaleY);

    return Pair.create(inputWidth, inputHeight);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs) throws FrameProcessingException {
    try {
      glProgram.use();

      // Draw to the canvas and store it in a texture.
      String text =
          String.format(Locale.US, "%.02f", presentationTimeUs / (float) C.MICROS_PER_SECOND);
      overlayBitmap.eraseColor(Color.TRANSPARENT);
      overlayCanvas.drawBitmap(checkStateNotNull(logoBitmap), /* left= */ 3, /* top= */ 378, paint);
      overlayCanvas.drawText(text, /* x= */ 160, /* y= */ 466, paint);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapTexId);
      GLUtils.texSubImage2D(
          GLES20.GL_TEXTURE_2D,
          /* level= */ 0,
          /* xoffset= */ 0,
          /* yoffset= */ 0,
          flipBitmapVertically(overlayBitmap));
      GlUtil.checkGlError();

      glProgram.setSamplerTexIdUniform("uTexSampler0", inputTexId, /* texUnitIndex= */ 0);
      glProgram.bindAttributesAndUniforms();
      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
      GlUtil.checkGlError();
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e, presentationTimeUs);
    }
  }

  @Override
  public void release() throws FrameProcessingException {
    super.release();
    try {
      glProgram.delete();
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e);
    }
  }

  private static Bitmap flipBitmapVertically(Bitmap bitmap) {
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
}
