/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ui.spherical;

import static android.opengl.GLU.gluErrorString;

import android.annotation.TargetApi;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** GL utility methods. */
/*package*/ final class GlUtil {
  private static final String TAG = "Spherical.Utils";

  /** Class only contains static methods. */
  private GlUtil() {}

  /**
   * If there is an OpenGl error, logs the error and if {@link
   * ExoPlayerLibraryInfo#GL_ASSERTIONS_ENABLED} is true throws a {@link RuntimeException}.
   */
  public static void checkGlError() {
    int error = GLES20.glGetError();
    int lastError;
    if (error != GLES20.GL_NO_ERROR) {
      do {
        lastError = error;
        Log.e(TAG, "glError " + gluErrorString(lastError));
        error = GLES20.glGetError();
      } while (error != GLES20.GL_NO_ERROR);

      if (ExoPlayerLibraryInfo.GL_ASSERTIONS_ENABLED) {
        throw new RuntimeException("glError " + gluErrorString(lastError));
      }
    }
  }

  /**
   * Builds a GL shader program from vertex & fragment shader code. The vertex and fragment shaders
   * are passed as arrays of strings in order to make debugging compilation issues easier.
   *
   * @param vertexCode GLES20 vertex shader program.
   * @param fragmentCode GLES20 fragment shader program.
   * @return GLES20 program id.
   */
  public static int compileProgram(String[] vertexCode, String[] fragmentCode) {
    checkGlError();
    // prepare shaders and OpenGL program
    int vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
    GLES20.glShaderSource(vertexShader, TextUtils.join("\n", vertexCode));
    GLES20.glCompileShader(vertexShader);
    checkGlError();

    int fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
    GLES20.glShaderSource(fragmentShader, TextUtils.join("\n", fragmentCode));
    GLES20.glCompileShader(fragmentShader);
    checkGlError();

    int program = GLES20.glCreateProgram();
    GLES20.glAttachShader(program, vertexShader);
    GLES20.glAttachShader(program, fragmentShader);

    // Link and check for errors.
    GLES20.glLinkProgram(program);
    int[] linkStatus = new int[1];
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
    if (linkStatus[0] != GLES20.GL_TRUE) {
      String errorMsg = "Unable to link shader program: \n" + GLES20.glGetProgramInfoLog(program);
      Log.e(TAG, errorMsg);
      if (ExoPlayerLibraryInfo.GL_ASSERTIONS_ENABLED) {
        throw new RuntimeException(errorMsg);
      }
    }
    checkGlError();

    return program;
  }

  /** Allocates a FloatBuffer with the given data. */
  public static FloatBuffer createBuffer(float[] data) {
    ByteBuffer bb = ByteBuffer.allocateDirect(data.length * C.BYTES_PER_FLOAT);
    bb.order(ByteOrder.nativeOrder());
    FloatBuffer buffer = bb.asFloatBuffer();
    buffer.put(data);
    buffer.position(0);

    return buffer;
  }

  /**
   * Creates a GL_TEXTURE_EXTERNAL_OES with default configuration of GL_LINEAR filtering and
   * GL_CLAMP_TO_EDGE wrapping.
   */
  @TargetApi(15)
  public static int createExternalTexture() {
    int[] texId = new int[1];
    GLES20.glGenTextures(1, IntBuffer.wrap(texId));
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId[0]);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    checkGlError();
    return texId[0];
  }
}
