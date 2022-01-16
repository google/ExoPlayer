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
package com.google.android.exoplayer2.util;

import static android.opengl.GLU.gluErrorString;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Context;
import android.content.pm.PackageManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import javax.microedition.khronos.egl.EGL10;

/** OpenGL ES 2.0 utilities. */
public final class GlUtil {

  /** Thrown when an OpenGL error occurs and {@link #glAssertionsEnabled} is {@code true}. */
  public static final class GlException extends RuntimeException {
    /** Creates an instance with the specified error message. */
    public GlException(String message) {
      super(message);
    }
  }

  /**
   * Represents a GLSL shader program.
   *
   * <p>After constructing a program, keep a reference for its lifetime and call {@link #delete()}
   * (or release the current GL context) when it's no longer needed.
   */
  public static final class Program {
    /** The identifier of a compiled and linked GLSL shader program. */
    private final int programId;

    private final Attribute[] attributes;
    private final Uniform[] uniforms;
    private final Map<String, Attribute> attributeByName;
    private final Map<String, Uniform> uniformByName;

    /**
     * Compiles a GL shader program from vertex and fragment shader GLSL GLES20 code.
     *
     * @param context The {@link Context}.
     * @param vertexShaderFilePath The path to a vertex shader program.
     * @param fragmentShaderFilePath The path to a fragment shader program.
     * @throws IOException When failing to read shader files.
     */
    public Program(Context context, String vertexShaderFilePath, String fragmentShaderFilePath)
        throws IOException {
      this(loadAsset(context, vertexShaderFilePath), loadAsset(context, fragmentShaderFilePath));
    }

    /**
     * Creates a GL shader program from vertex and fragment shader GLSL GLES20 code.
     *
     * <p>This involves slow steps, like compiling, linking, and switching the GL program, so do not
     * call this in fast rendering loops.
     *
     * @param vertexShaderGlsl The vertex shader program.
     * @param fragmentShaderGlsl The fragment shader program.
     */
    public Program(String vertexShaderGlsl, String fragmentShaderGlsl) {
      programId = GLES20.glCreateProgram();
      checkGlError();

      // Add the vertex and fragment shaders.
      addShader(programId, GLES20.GL_VERTEX_SHADER, vertexShaderGlsl);
      addShader(programId, GLES20.GL_FRAGMENT_SHADER, fragmentShaderGlsl);

      // Link and use the program, and enumerate attributes/uniforms.
      GLES20.glLinkProgram(programId);
      int[] linkStatus = new int[] {GLES20.GL_FALSE};
      GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, /* offset= */ 0);
      if (linkStatus[0] != GLES20.GL_TRUE) {
        throwGlException(
            "Unable to link shader program: \n" + GLES20.glGetProgramInfoLog(programId));
      }
      GLES20.glUseProgram(programId);
      attributeByName = new HashMap<>();
      int[] attributeCount = new int[1];
      GLES20.glGetProgramiv(
          programId, GLES20.GL_ACTIVE_ATTRIBUTES, attributeCount, /* offset= */ 0);
      attributes = new Attribute[attributeCount[0]];
      for (int i = 0; i < attributeCount[0]; i++) {
        Attribute attribute = Attribute.create(programId, i);
        attributes[i] = attribute;
        attributeByName.put(attribute.name, attribute);
      }
      uniformByName = new HashMap<>();
      int[] uniformCount = new int[1];
      GLES20.glGetProgramiv(programId, GLES20.GL_ACTIVE_UNIFORMS, uniformCount, /* offset= */ 0);
      uniforms = new Uniform[uniformCount[0]];
      for (int i = 0; i < uniformCount[0]; i++) {
        Uniform uniform = Uniform.create(programId, i);
        uniforms[i] = uniform;
        uniformByName.put(uniform.name, uniform);
      }
      checkGlError();
    }

    /**
     * Uses the program.
     *
     * <p>Call this in the rendering loop to switch between different programs.
     */
    public void use() {
      // TODO(http://b/205002913): When multiple GL programs are supported by Transformer, make sure
      // to call use() to switch between programs.
      GLES20.glUseProgram(programId);
      checkGlError();
    }

    /** Deletes the program. Deleted programs cannot be used again. */
    public void delete() {
      GLES20.glDeleteProgram(programId);
      checkGlError();
    }

    /**
     * Returns the location of an {@link Attribute}, which has been enabled as a vertex attribute
     * array.
     */
    public int getAttributeArrayLocationAndEnable(String attributeName) {
      int location = getAttributeLocation(attributeName);
      GLES20.glEnableVertexAttribArray(location);
      checkGlError();
      return location;
    }

    /** Returns the location of an {@link Attribute}. */
    private int getAttributeLocation(String attributeName) {
      return GlUtil.getAttributeLocation(programId, attributeName);
    }

    /** Returns the location of a {@link Uniform}. */
    public int getUniformLocation(String uniformName) {
      return GlUtil.getUniformLocation(programId, uniformName);
    }

    /** Sets a float buffer type attribute. */
    public void setBufferAttribute(String name, float[] values, int size) {
      checkNotNull(attributeByName.get(name)).setBuffer(values, size);
    }

    /** Sets a texture sampler type uniform. */
    public void setSamplerTexIdUniform(String name, int texId, int unit) {
      checkNotNull(uniformByName.get(name)).setSamplerTexId(texId, unit);
    }

    /** Sets a float type uniform. */
    public void setFloatUniform(String name, float value) {
      checkNotNull(uniformByName.get(name)).setFloat(value);
    }

    /** Sets a float array type uniform. */
    public void setFloatsUniform(String name, float[] value) {
      checkNotNull(uniformByName.get(name)).setFloats(value);
    }

    /** Binds all attributes and uniforms in the program. */
    public void bindAttributesAndUniforms() {
      for (Attribute attribute : attributes) {
        attribute.bind();
      }
      for (Uniform uniform : uniforms) {
        uniform.bind();
      }
    }
  }

  /** Whether to throw a {@link GlException} in case of an OpenGL error. */
  public static boolean glAssertionsEnabled = false;

  private static final String TAG = "GlUtil";

  private static final String EXTENSION_PROTECTED_CONTENT = "EGL_EXT_protected_content";
  private static final String EXTENSION_SURFACELESS_CONTEXT = "EGL_KHR_surfaceless_context";

  /** Class only contains static methods. */
  private GlUtil() {}

  /**
   * Returns whether creating a GL context with {@value #EXTENSION_PROTECTED_CONTENT} is possible.
   * If {@code true}, the device supports a protected output path for DRM content when using GL.
   */
  public static boolean isProtectedContentExtensionSupported(Context context) {
    if (Util.SDK_INT < 24) {
      return false;
    }
    if (Util.SDK_INT < 26 && ("samsung".equals(Util.MANUFACTURER) || "XT1650".equals(Util.MODEL))) {
      // Samsung devices running Nougat are known to be broken. See
      // https://github.com/google/ExoPlayer/issues/3373 and [Internal: b/37197802].
      // Moto Z XT1650 is also affected. See
      // https://github.com/google/ExoPlayer/issues/3215.
      return false;
    }
    if (Util.SDK_INT < 26
        && !context
            .getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE)) {
      // Pre API level 26 devices were not well tested unless they supported VR mode.
      return false;
    }

    EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    @Nullable String eglExtensions = EGL14.eglQueryString(display, EGL10.EGL_EXTENSIONS);
    return eglExtensions != null && eglExtensions.contains(EXTENSION_PROTECTED_CONTENT);
  }

  /**
   * Returns whether creating a GL context with {@value #EXTENSION_SURFACELESS_CONTEXT} is possible.
   */
  public static boolean isSurfacelessContextExtensionSupported() {
    if (Util.SDK_INT < 17) {
      return false;
    }
    EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    @Nullable String eglExtensions = EGL14.eglQueryString(display, EGL10.EGL_EXTENSIONS);
    return eglExtensions != null && eglExtensions.contains(EXTENSION_SURFACELESS_CONTEXT);
  }

  /** Returns an initialized default {@link EGLDisplay}. */
  @RequiresApi(17)
  public static EGLDisplay createEglDisplay() {
    return Api17.createEglDisplay();
  }

  /** Returns a new {@link EGLContext} for the specified {@link EGLDisplay}. */
  @RequiresApi(17)
  public static EGLContext createEglContext(EGLDisplay eglDisplay) {
    return Api17.createEglContext(eglDisplay);
  }

  /**
   * Returns a new {@link EGLSurface} wrapping the specified {@code surface}.
   *
   * @param eglDisplay The {@link EGLDisplay} to attach the surface to.
   * @param surface The surface to wrap; must be a surface, surface texture or surface holder.
   */
  @RequiresApi(17)
  public static EGLSurface getEglSurface(EGLDisplay eglDisplay, Object surface) {
    return Api17.getEglSurface(eglDisplay, surface);
  }

  /**
   * If there is an OpenGl error, logs the error and if {@link #glAssertionsEnabled} is true throws
   * a {@link GlException}.
   */
  public static void checkGlError() {
    int lastError = GLES20.GL_NO_ERROR;
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      Log.e(TAG, "glError: " + gluErrorString(error));
      lastError = error;
    }
    if (lastError != GLES20.GL_NO_ERROR) {
      throwGlException("glError: " + gluErrorString(lastError));
    }
  }

  /**
   * Makes the specified {@code surface} the render target, using a viewport of {@code width} by
   * {@code height} pixels.
   */
  @RequiresApi(17)
  public static void focusSurface(
      EGLDisplay eglDisplay, EGLContext eglContext, EGLSurface surface, int width, int height) {
    Api17.focusSurface(eglDisplay, eglContext, surface, width, height);
  }

  /**
   * Deletes a GL texture.
   *
   * @param textureId The ID of the texture to delete.
   */
  public static void deleteTexture(int textureId) {
    GLES20.glDeleteTextures(/* n= */ 1, new int[] {textureId}, /* offset= */ 0);
    checkGlError();
  }

  /**
   * Destroys the {@link EGLContext} identified by the provided {@link EGLDisplay} and {@link
   * EGLContext}.
   */
  @RequiresApi(17)
  public static void destroyEglContext(
      @Nullable EGLDisplay eglDisplay, @Nullable EGLContext eglContext) {
    Api17.destroyEglContext(eglDisplay, eglContext);
  }

  /**
   * Allocates a FloatBuffer with the given data.
   *
   * @param data Used to initialize the new buffer.
   */
  public static FloatBuffer createBuffer(float[] data) {
    return (FloatBuffer) createBuffer(data.length).put(data).flip();
  }

  /**
   * Allocates a FloatBuffer.
   *
   * @param capacity The new buffer's capacity, in floats.
   */
  public static FloatBuffer createBuffer(int capacity) {
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(capacity * C.BYTES_PER_FLOAT);
    return byteBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer();
  }

  /**
   * Loads a file from the assets folder.
   *
   * @param context The {@link Context}.
   * @param assetPath The path to the file to load, from the assets folder.
   * @return The content of the file to load.
   * @throws IOException If the file couldn't be read.
   */
  public static String loadAsset(Context context, String assetPath) throws IOException {
    @Nullable InputStream inputStream = null;
    try {
      inputStream = context.getAssets().open(assetPath);
      return Util.fromUtf8Bytes(Util.toByteArray(inputStream));
    } finally {
      Util.closeQuietly(inputStream);
    }
  }

  /**
   * Creates a GL_TEXTURE_EXTERNAL_OES with default configuration of GL_LINEAR filtering and
   * GL_CLAMP_TO_EDGE wrapping.
   */
  public static int createExternalTexture() {
    int[] texId = new int[1];
    GLES20.glGenTextures(/* n= */ 1, IntBuffer.wrap(texId));
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

  private static void addShader(int programId, int type, String glsl) {
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, glsl);
    GLES20.glCompileShader(shader);

    int[] result = new int[] {GLES20.GL_FALSE};
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, /* offset= */ 0);
    if (result[0] != GLES20.GL_TRUE) {
      throwGlException(GLES20.glGetShaderInfoLog(shader) + ", source: " + glsl);
    }

    GLES20.glAttachShader(programId, shader);
    GLES20.glDeleteShader(shader);
    checkGlError();
  }

  private static int getAttributeLocation(int programId, String attributeName) {
    return GLES20.glGetAttribLocation(programId, attributeName);
  }

  private static int getUniformLocation(int programId, String uniformName) {
    return GLES20.glGetUniformLocation(programId, uniformName);
  }

  private static void throwGlException(String errorMsg) {
    Log.e(TAG, errorMsg);
    if (glAssertionsEnabled) {
      throw new GlException(errorMsg);
    }
  }

  private static void checkEglException(boolean expression, String errorMessage) {
    if (!expression) {
      throwGlException(errorMessage);
    }
  }

  /** Returns the length of the null-terminated string in {@code strVal}. */
  private static int strlen(byte[] strVal) {
    for (int i = 0; i < strVal.length; ++i) {
      if (strVal[i] == '\0') {
        return i;
      }
    }
    return strVal.length;
  }

  /**
   * GL attribute, which can be attached to a buffer with {@link Attribute#setBuffer(float[], int)}.
   */
  private static final class Attribute {

    /* Returns the attribute at the given index in the program. */
    public static Attribute create(int programId, int index) {
      int[] length = new int[1];
      GLES20.glGetProgramiv(
          programId, GLES20.GL_ACTIVE_ATTRIBUTE_MAX_LENGTH, length, /* offset= */ 0);
      byte[] nameBytes = new byte[length[0]];

      GLES20.glGetActiveAttrib(
          programId,
          index,
          length[0],
          /* unusedLength */ new int[1],
          /* lengthOffset= */ 0,
          /* unusedSize */ new int[1],
          /* sizeOffset= */ 0,
          /* unusedType */ new int[1],
          /* typeOffset= */ 0,
          nameBytes,
          /* nameOffset= */ 0);
      String name = new String(nameBytes, /* offset= */ 0, strlen(nameBytes));
      int location = getAttributeLocation(programId, name);

      return new Attribute(name, index, location);
    }

    /** The name of the attribute in the GLSL sources. */
    public final String name;

    private final int index;
    private final int location;

    @Nullable private Buffer buffer;
    private int size;

    private Attribute(String name, int index, int location) {
      this.name = name;
      this.index = index;
      this.location = location;
    }

    /**
     * Configures {@link #bind()} to attach vertices in {@code buffer} (each of size {@code size}
     * elements) to this {@link Attribute}.
     *
     * @param buffer Buffer to bind to this attribute.
     * @param size Number of elements per vertex.
     */
    public void setBuffer(float[] buffer, int size) {
      this.buffer = createBuffer(buffer);
      this.size = size;
    }

    /**
     * Sets the vertex attribute to whatever was attached via {@link #setBuffer(float[], int)}.
     *
     * <p>Should be called before each drawing call.
     */
    public void bind() {
      Buffer buffer = checkNotNull(this.buffer, "call setBuffer before bind");
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, /* buffer= */ 0);
      GLES20.glVertexAttribPointer(
          location, size, GLES20.GL_FLOAT, /* normalized= */ false, /* stride= */ 0, buffer);
      GLES20.glEnableVertexAttribArray(index);
      checkGlError();
    }
  }

  /**
   * GL uniform, which can be attached to a sampler using {@link Uniform#setSamplerTexId(int, int)}.
   */
  private static final class Uniform {

    /** Returns the uniform at the given index in the program. */
    public static Uniform create(int programId, int index) {
      int[] length = new int[1];
      GLES20.glGetProgramiv(
          programId, GLES20.GL_ACTIVE_UNIFORM_MAX_LENGTH, length, /* offset= */ 0);

      int[] type = new int[1];
      byte[] nameBytes = new byte[length[0]];

      GLES20.glGetActiveUniform(
          programId,
          index,
          length[0],
          /* unusedLength */ new int[1],
          /* lengthOffset= */ 0,
          /* unusedSize */ new int[1],
          /*sizeOffset= */ 0,
          type,
          /* typeOffset= */ 0,
          nameBytes,
          /* nameOffset= */ 0);
      String name = new String(nameBytes, /* offset= */ 0, strlen(nameBytes));
      int location = getUniformLocation(programId, name);

      return new Uniform(name, location, type[0]);
    }

    /** The name of the uniform in the GLSL sources. */
    public final String name;

    private final int location;
    private final int type;
    private final float[] value;

    private int texId;
    private int unit;

    private Uniform(String name, int location, int type) {
      this.name = name;
      this.location = location;
      this.type = type;
      this.value = new float[16];
    }

    /**
     * Configures {@link #bind()} to use the specified {@code texId} for this sampler uniform.
     *
     * @param texId The GL texture identifier from which to sample.
     * @param unit The GL texture unit index.
     */
    public void setSamplerTexId(int texId, int unit) {
      this.texId = texId;
      this.unit = unit;
    }

    /** Configures {@link #bind()} to use the specified float {@code value} for this uniform. */
    public void setFloat(float value) {
      this.value[0] = value;
    }

    /** Configures {@link #bind()} to use the specified float[] {@code value} for this uniform. */
    public void setFloats(float[] value) {
      System.arraycopy(value, /* srcPos= */ 0, this.value, /* destPos= */ 0, value.length);
    }

    /**
     * Sets the uniform to whatever value was passed via {@link #setSamplerTexId(int, int)}, {@link
     * #setFloat(float)} or {@link #setFloats(float[])}.
     *
     * <p>Should be called before each drawing call.
     */
    public void bind() {
      if (type == GLES20.GL_FLOAT) {
        GLES20.glUniform1fv(location, /* count= */ 1, value, /* offset= */ 0);
        checkGlError();
        return;
      }

      if (type == GLES20.GL_FLOAT_MAT4) {
        GLES20.glUniformMatrix4fv(
            location, /* count= */ 1, /* transpose= */ false, value, /* offset= */ 0);
        checkGlError();
        return;
      }

      if (texId == 0) {
        throw new IllegalStateException("No call to setSamplerTexId() before bind.");
      }
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
      if (type == GLES11Ext.GL_SAMPLER_EXTERNAL_OES) {
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
      } else if (type == GLES20.GL_SAMPLER_2D) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
      } else {
        throw new IllegalStateException("Unexpected uniform type: " + type);
      }
      GLES20.glUniform1i(location, unit);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(
          GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(
          GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
      checkGlError();
    }
  }

  @RequiresApi(17)
  private static final class Api17 {
    private Api17() {}

    @DoNotInline
    public static EGLDisplay createEglDisplay() {
      EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
      checkEglException(!eglDisplay.equals(EGL14.EGL_NO_DISPLAY), "No EGL display.");
      if (!EGL14.eglInitialize(
          eglDisplay,
          /* unusedMajor */ new int[1],
          /* majorOffset= */ 0,
          /* unusedMinor */ new int[1],
          /* minorOffset= */ 0)) {
        throwGlException("Error in eglInitialize.");
      }
      checkGlError();
      return eglDisplay;
    }

    @DoNotInline
    public static EGLContext createEglContext(EGLDisplay eglDisplay) {
      int[] contextAttributes = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
      EGLContext eglContext =
          EGL14.eglCreateContext(
              eglDisplay,
              getEglConfig(eglDisplay),
              EGL14.EGL_NO_CONTEXT,
              contextAttributes,
              /* offset= */ 0);
      if (eglContext == null) {
        EGL14.eglTerminate(eglDisplay);
        throwGlException(
            "eglCreateContext() failed to create a valid context. The device may not support EGL"
                + " version 2");
      }
      checkGlError();
      return eglContext;
    }

    @DoNotInline
    public static EGLSurface getEglSurface(EGLDisplay eglDisplay, Object surface) {
      return EGL14.eglCreateWindowSurface(
          eglDisplay,
          getEglConfig(eglDisplay),
          surface,
          new int[] {EGL14.EGL_NONE},
          /* offset= */ 0);
    }

    @DoNotInline
    public static void focusSurface(
        EGLDisplay eglDisplay, EGLContext eglContext, EGLSurface surface, int width, int height) {
      int[] boundFrameBuffer = new int[1];
      GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, boundFrameBuffer, /* offset= */ 0);
      int defaultFrameBuffer = 0;
      if (boundFrameBuffer[0] != defaultFrameBuffer) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, defaultFrameBuffer);
      }
      EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext);
      GLES20.glViewport(/* x= */ 0, /* y= */ 0, width, height);
    }

    @DoNotInline
    public static void destroyEglContext(
        @Nullable EGLDisplay eglDisplay, @Nullable EGLContext eglContext) {
      if (eglDisplay == null) {
        return;
      }
      EGL14.eglMakeCurrent(
          eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
      int error = EGL14.eglGetError();
      checkEglException(error == EGL14.EGL_SUCCESS, "Error releasing context: " + error);
      if (eglContext != null) {
        EGL14.eglDestroyContext(eglDisplay, eglContext);
        error = EGL14.eglGetError();
        checkEglException(error == EGL14.EGL_SUCCESS, "Error destroying context: " + error);
      }
      EGL14.eglReleaseThread();
      error = EGL14.eglGetError();
      checkEglException(error == EGL14.EGL_SUCCESS, "Error releasing thread: " + error);
      EGL14.eglTerminate(eglDisplay);
      error = EGL14.eglGetError();
      checkEglException(error == EGL14.EGL_SUCCESS, "Error terminating display: " + error);
    }

    @DoNotInline
    private static EGLConfig getEglConfig(EGLDisplay eglDisplay) {
      int[] defaultConfiguration =
          new int[] {
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, /* redSize= */ 8,
            EGL14.EGL_GREEN_SIZE, /* greenSize= */ 8,
            EGL14.EGL_BLUE_SIZE, /* blueSize= */ 8,
            EGL14.EGL_ALPHA_SIZE, /* alphaSize= */ 8,
            EGL14.EGL_DEPTH_SIZE, /* depthSize= */ 0,
            EGL14.EGL_STENCIL_SIZE, /* stencilSize= */ 0,
            EGL14.EGL_NONE
          };
      EGLConfig[] eglConfigs = new EGLConfig[1];
      if (!EGL14.eglChooseConfig(
          eglDisplay,
          defaultConfiguration,
          /* attrib_listOffset= */ 0,
          eglConfigs,
          /* configsOffset= */ 0,
          /* config_size= */ 1,
          /* unusedNumConfig */ new int[1],
          /* num_configOffset= */ 0)) {
        throwGlException("eglChooseConfig failed.");
      }
      return eglConfigs[0];
    }
  }
}
