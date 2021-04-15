/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.gldemo;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import javax.microedition.khronos.opengles.GL10;

/**
 * Video processor that demonstrates how to overlay a bitmap on video output using a GL shader. The
 * bitmap is drawn using an Android {@link Canvas}.
 */
/* package */ final class BitmapOverlayVideoProcessor
    implements VideoProcessingGLSurfaceView.VideoProcessor {

  private static final int OVERLAY_WIDTH = 512;
  private static final int OVERLAY_HEIGHT = 256;

  private final Context context;
  private final Paint paint;
  private final int[] textures;
  private final Bitmap overlayBitmap;
  private final Bitmap logoBitmap;
  private final Canvas overlayCanvas;

  private int program;
  @Nullable private GlUtil.Attribute[] attributes;
  @Nullable private GlUtil.Uniform[] uniforms;

  private float bitmapScaleX;
  private float bitmapScaleY;

  public BitmapOverlayVideoProcessor(Context context) {
    this.context = context.getApplicationContext();
    paint = new Paint();
    paint.setTextSize(64);
    paint.setAntiAlias(true);
    paint.setARGB(0xFF, 0xFF, 0xFF, 0xFF);
    textures = new int[1];
    overlayBitmap = Bitmap.createBitmap(OVERLAY_WIDTH, OVERLAY_HEIGHT, Bitmap.Config.ARGB_8888);
    overlayCanvas = new Canvas(overlayBitmap);
    try {
      logoBitmap =
          ((BitmapDrawable)
                  context.getPackageManager().getApplicationIcon(context.getPackageName()))
              .getBitmap();
    } catch (PackageManager.NameNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void initialize() {
    String vertexShaderCode =
        loadAssetAsString(context, "bitmap_overlay_video_processor_vertex.glsl");
    String fragmentShaderCode =
        loadAssetAsString(context, "bitmap_overlay_video_processor_fragment.glsl");
    program = GlUtil.compileProgram(vertexShaderCode, fragmentShaderCode);
    GlUtil.Attribute[] attributes = GlUtil.getAttributes(program);
    GlUtil.Uniform[] uniforms = GlUtil.getUniforms(program);
    for (GlUtil.Attribute attribute : attributes) {
      if (attribute.name.equals("a_position")) {
        attribute.setBuffer(new float[] {-1, -1, 1, -1, -1, 1, 1, 1}, 2);
      } else if (attribute.name.equals("a_texcoord")) {
        attribute.setBuffer(new float[] {0, 1, 1, 1, 0, 0, 1, 0}, 2);
      }
    }
    this.attributes = attributes;
    this.uniforms = uniforms;
    GLES20.glGenTextures(1, textures, 0);
    GLES20.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
    GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
    GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
    GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_REPEAT);
    GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_REPEAT);
    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, /* level= */ 0, overlayBitmap, /* border= */ 0);
  }

  @Override
  public void setSurfaceSize(int width, int height) {
    bitmapScaleX = (float) width / OVERLAY_WIDTH;
    bitmapScaleY = (float) height / OVERLAY_HEIGHT;
  }

  @Override
  public void draw(int frameTexture, long frameTimestampUs) {
    // Draw to the canvas and store it in a texture.
    String text = String.format(Locale.US, "%.02f", frameTimestampUs / (float) C.MICROS_PER_SECOND);
    overlayBitmap.eraseColor(Color.TRANSPARENT);
    overlayCanvas.drawBitmap(logoBitmap, /* left= */ 32, /* top= */ 32, paint);
    overlayCanvas.drawText(text, /* x= */ 200, /* y= */ 130, paint);
    GLES20.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
    GLUtils.texSubImage2D(
        GL10.GL_TEXTURE_2D, /* level= */ 0, /* xoffset= */ 0, /* yoffset= */ 0, overlayBitmap);
    GlUtil.checkGlError();

    // Run the shader program.
    GlUtil.Uniform[] uniforms = Assertions.checkNotNull(this.uniforms);
    GlUtil.Attribute[] attributes = Assertions.checkNotNull(this.attributes);
    GLES20.glUseProgram(program);
    for (GlUtil.Uniform uniform : uniforms) {
      switch (uniform.name) {
        case "tex_sampler_0":
          uniform.setSamplerTexId(frameTexture, /* unit= */ 0);
          break;
        case "tex_sampler_1":
          uniform.setSamplerTexId(textures[0], /* unit= */ 1);
          break;
        case "scaleX":
          uniform.setFloat(bitmapScaleX);
          break;
        case "scaleY":
          uniform.setFloat(bitmapScaleY);
          break;
        default: // fall out
      }
    }
    for (GlUtil.Attribute copyExternalAttribute : attributes) {
      copyExternalAttribute.bind();
    }
    for (GlUtil.Uniform copyExternalUniform : uniforms) {
      copyExternalUniform.bind();
    }
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
    GlUtil.checkGlError();
  }

  private static String loadAssetAsString(Context context, String assetFileName) {
    @Nullable InputStream inputStream = null;
    try {
      inputStream = context.getAssets().open(assetFileName);
      return Util.fromUtf8Bytes(Util.toByteArray(inputStream));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } finally {
      Util.closeQuietly(inputStream);
    }
  }
}
