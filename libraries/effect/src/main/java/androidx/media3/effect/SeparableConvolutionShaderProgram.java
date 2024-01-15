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
package androidx.media3.effect;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import androidx.annotation.CallSuper;
import androidx.annotation.RequiresApi;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.ShortBuffer;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link GlShaderProgram} for performing separable convolutions.
 *
 * <p>A single {@link ConvolutionFunction1D} is applied horizontally on a first pass and vertically
 * on a second pass.
 */
@RequiresApi(26) // Uses Bitmap.Config.RGBA_F16.
@UnstableApi
public class SeparableConvolutionShaderProgram implements GlShaderProgram {
  private static final String VERTEX_SHADER_PATH = "shaders/vertex_shader_transformation_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH =
      "shaders/fragment_shader_separable_convolution_es2.glsl";

  // Constants specifically for fp16FromFloat().
  // TODO (b/282767994): Fix TAP hanging issue and update samples per texel.
  private static final int RASTER_SAMPLES_PER_TEXEL = 5;
  // Apply some padding in the function LUT to avoid any issues from GL sampling off the texture.
  private static final int FUNCTION_LUT_PADDING = RASTER_SAMPLES_PER_TEXEL;

  // BEGIN COPIED FP16 code.
  // Source: libcore/luni/src/main/java/libcore/util/FP16.java
  private static final int FP16_EXPONENT_BIAS = 15;
  private static final int FP16_SIGN_SHIFT = 15;
  private static final int FP16_EXPONENT_SHIFT = 10;
  private static final int FP32_SIGN_SHIFT = 31;
  private static final int FP32_EXPONENT_SHIFT = 23;
  private static final int FP32_SHIFTED_EXPONENT_MASK = 0xff;
  private static final int FP32_SIGNIFICAND_MASK = 0x7fffff;
  private static final int FP32_EXPONENT_BIAS = 127;
  // END FP16 copied code.

  private final GlProgram glProgram;
  private final boolean useHdr;
  private final SeparableConvolution convolution;
  private final float scaleWidth;
  private final float scaleHeight;

  private GlShaderProgram.InputListener inputListener;
  private GlShaderProgram.OutputListener outputListener;
  private GlShaderProgram.ErrorListener errorListener;
  private Executor errorListenerExecutor;
  private boolean outputTextureInUse;
  private GlTextureInfo outputTexture;
  private GlTextureInfo intermediateTexture;
  private GlTextureInfo functionLutTexture; // Values for the function LUT as a texture.
  private float functionLutTexelStep;
  private float functionLutCenterX;
  private float functionLutDomainStart;
  private float functionLutWidth;
  private Size outputSize;
  private Size lastInputSize;
  private Size intermediateSize;
  private @MonotonicNonNull ConvolutionFunction1D lastConvolutionFunction;

  /**
   * Creates an instance.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @param convolution The {@link SeparableConvolution} to apply in each direction.
   * @param scaleWidth The scaling factor used to determine the width of the output relative to the
   *     input.
   * @param scaleHeight The scaling factor used to determine the height of the output relative to
   *     the input.
   * @throws VideoFrameProcessingException If a problem occurs while reading shader files.
   */
  public SeparableConvolutionShaderProgram(
      Context context,
      boolean useHdr,
      SeparableConvolution convolution,
      float scaleWidth,
      float scaleHeight)
      throws VideoFrameProcessingException {
    this.useHdr = useHdr;
    this.convolution = convolution;
    this.scaleWidth = scaleWidth;
    this.scaleHeight = scaleHeight;
    inputListener = new InputListener() {};
    outputListener = new OutputListener() {};
    errorListener = (frameProcessingException) -> {};
    errorListenerExecutor = MoreExecutors.directExecutor();
    functionLutTexture = GlTextureInfo.UNSET;
    intermediateTexture = GlTextureInfo.UNSET;
    outputTexture = GlTextureInfo.UNSET;
    lastInputSize = Size.ZERO;
    intermediateSize = Size.ZERO;
    outputSize = Size.ZERO;
    lastConvolutionFunction = null;

    try {
      glProgram = new GlProgram(context, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
    } catch (IOException | GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  @Override
  public final void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    if (!outputTextureInUse) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public final void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
  }

  @Override
  public final void setErrorListener(Executor errorListenerExecutor, ErrorListener errorListener) {
    this.errorListenerExecutor = errorListenerExecutor;
    this.errorListener = errorListener;
  }

  @Override
  public final void queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs) {
    Assertions.checkState(
        !outputTextureInUse,
        "The shader program does not currently accept input frames. Release prior output frames"
            + " first.");
    try {
      ensureTexturesAreConfigured(
          glObjectsProvider, new Size(inputTexture.width, inputTexture.height), presentationTimeUs);
      outputTextureInUse = true;
      renderHorizontal(inputTexture);
      renderVertical();

      onBlurRendered(inputTexture);

      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* i1= */ 0, /* i2= */ 4);
      GlUtil.checkGlError();
      inputListener.onInputFrameProcessed(inputTexture);
      outputListener.onOutputFrameAvailable(outputTexture, presentationTimeUs);
    } catch (GlUtil.GlException e) {
      errorListenerExecutor.execute(
          () -> errorListener.onError(VideoFrameProcessingException.from(e, presentationTimeUs)));
    }
  }

  @Override
  public final void releaseOutputFrame(GlTextureInfo outputTexture) {
    outputTextureInUse = false;
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public final void signalEndOfCurrentInputStream() {
    outputListener.onCurrentOutputStreamEnded();
  }

  @Override
  public final void flush() {
    outputTextureInUse = false;
    inputListener.onFlush();
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  @CallSuper
  public void release() throws VideoFrameProcessingException {
    try {
      outputTexture.release();
      intermediateTexture.release();
      functionLutTexture.release();
      glProgram.delete();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  /**
   * Called when the blur has been rendered onto the frame.
   *
   * <p>The default implementation is a no-op.
   *
   * @param inputTexture The input texture.
   * @throws GlUtil.GlException If an error occurs.
   */
  protected void onBlurRendered(GlTextureInfo inputTexture) throws GlUtil.GlException {
    // Do nothing.
  }

  private void renderOnePass(int inputTexId, boolean isHorizontal) throws GlUtil.GlException {
    int size = isHorizontal ? lastInputSize.getWidth() : intermediateSize.getHeight();
    glProgram.use();
    glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
    glProgram.setIntUniform("uIsHorizontal", isHorizontal ? 1 : 0);
    glProgram.setFloatUniform("uSourceTexelSize", 1.0f / size);
    glProgram.setFloatUniform("uSourceFullSize", (float) size);
    glProgram.setFloatUniform("uConvStartTexels", functionLutDomainStart);
    glProgram.setFloatUniform("uConvWidthTexels", functionLutWidth);
    glProgram.setFloatUniform("uFunctionLookupStepSize", functionLutTexelStep);
    glProgram.setFloatsUniform("uFunctionLookupCenter", new float[] {functionLutCenterX, 0.5f});
    glProgram.setSamplerTexIdUniform(
        "uFunctionLookupSampler", functionLutTexture.texId, /* texUnitIndex= */ 1);
    glProgram.bindAttributesAndUniforms();

    // The four-vertex triangle strip forms a quad.
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    GlUtil.checkGlError();
  }

  private Size configure(Size inputSize) {
    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
    float[] identityMatrix = GlUtil.create4x4IdentityMatrix();
    glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix);
    glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);

    return new Size(
        (int) (inputSize.getWidth() * scaleWidth), (int) (inputSize.getHeight() * scaleHeight));
  }

  private void renderHorizontal(GlTextureInfo inputTexture) throws GlUtil.GlException {
    // Render horizontal reads from the input texture and renders to the intermediate texture.
    GlUtil.focusFramebufferUsingCurrentContext(
        intermediateTexture.fboId, intermediateTexture.width, intermediateTexture.height);
    GlUtil.clearFocusedBuffers();
    renderOnePass(inputTexture.texId, /* isHorizontal= */ true);
  }

  private void renderVertical() throws GlUtil.GlException {
    // Render vertical reads from the intermediate and renders to the output texture.
    GlUtil.focusFramebufferUsingCurrentContext(
        outputTexture.fboId, outputTexture.width, outputTexture.height);
    GlUtil.clearFocusedBuffers();
    renderOnePass(intermediateTexture.texId, /* isHorizontal= */ false);
  }

  private void ensureTexturesAreConfigured(
      GlObjectsProvider glObjectsProvider, Size inputSize, long presentationTimeUs)
      throws GlUtil.GlException {
    ConvolutionFunction1D currentConvolutionFunction =
        convolution.getConvolution(presentationTimeUs);
    if (!currentConvolutionFunction.equals(lastConvolutionFunction)) {
      updateFunctionTexture(glObjectsProvider, currentConvolutionFunction);
      lastConvolutionFunction = currentConvolutionFunction;
    }

    // Only update intermediate and output textures if the size changes.
    if (inputSize.equals(lastInputSize)) {
      return;
    }

    outputSize = configure(inputSize);
    // If there is a size change with the filtering (for example, a scaling operation), the first
    // pass is applied horizontally.  As a result, width of the intermediate texture will match the
    // output size, while the height will be unchanged from the input
    intermediateSize = new Size(outputSize.getWidth(), inputSize.getHeight());
    intermediateTexture =
        configurePixelTexture(glObjectsProvider, intermediateTexture, intermediateSize);
    outputTexture = configurePixelTexture(glObjectsProvider, outputTexture, outputSize);

    this.lastInputSize = inputSize;
  }

  /**
   * Creates a function lookup table for the convolution, and stores it in a 16b floating point
   * texture for GPU access.
   */
  private void updateFunctionTexture(
      GlObjectsProvider glObjectsProvider, ConvolutionFunction1D convolutionFunction)
      throws GlUtil.GlException {

    int lutRasterSize =
        (int)
            Math.ceil(
                convolutionFunction.width() * RASTER_SAMPLES_PER_TEXEL + 2 * FUNCTION_LUT_PADDING);

    // The function LUT is mapped to [0, 1] texture coords. We need to calculate what change
    // in texture coordinated corresponds exactly with a size of 1 texel (or pixel) in the function.
    // This is basically 1 / function_width, but due to the ceil() call above, it needs to be
    // calculated based on the actual raster size.
    this.functionLutTexelStep = 1.0f / ((float) lutRasterSize / RASTER_SAMPLES_PER_TEXEL);

    // The function values are stored in an FP16 texture. Setting FP16 values in a Bitmap requires
    // multiple steps. For each step, calculate the function value as a Float, and then use the
    // Half class to convert to FP16 and then read the value as a Short int
    ShortBuffer functionShortBuffer = ShortBuffer.allocate(lutRasterSize * 4);
    float rasterSampleStep = 1.0f / RASTER_SAMPLES_PER_TEXEL;
    float functionDomainStart = convolutionFunction.domainStart();
    int index = 0;

    for (int i = 0; i < lutRasterSize; i++) {
      float sampleValue = 0.0f;
      int unpaddedI = i - FUNCTION_LUT_PADDING;
      float samplePosition = functionDomainStart + unpaddedI * rasterSampleStep;

      if (unpaddedI >= 0 && i <= lutRasterSize - FUNCTION_LUT_PADDING) {
        sampleValue = convolutionFunction.value(samplePosition);
      }

      // Convert float to half (fp16) and read out the bits as a short.
      // Texture for Bitmap is RGBA_F16, so we store the function value in RGB channels and 1.0
      // in A.
      short shortEncodedValue = fp16FromFloat(sampleValue);

      // Set RGB
      functionShortBuffer.put(index++, shortEncodedValue);
      functionShortBuffer.put(index++, shortEncodedValue);
      functionShortBuffer.put(index++, shortEncodedValue);

      // Set Alpha
      functionShortBuffer.put(index++, fp16FromFloat(1.0f));
    }

    // Calculate the center of the function in the raster.  The formula below is a slight
    // adjustment on (value - min) / (max - min), where value = 0 at center and
    // rasterSampleStep * lutRasterSize is equal to (max - min) over the range of the raster
    // samples, which may be slightly different than the difference between the function's max
    // and min domain values.
    // To find the value associated at position 0 in the texture, is the value corresponding with
    // the leading edge position of the first sample.  This needs to account for the padding and
    // the 1/2 texel offsets used in texture lookups (index 0 is centered at 0.5 / numTexels).
    float minValueWithPadding =
        functionDomainStart - rasterSampleStep * (FUNCTION_LUT_PADDING + 0.5f);
    this.functionLutCenterX = -minValueWithPadding / (rasterSampleStep * lutRasterSize);
    this.functionLutDomainStart = convolutionFunction.domainStart();
    this.functionLutWidth = convolutionFunction.width();

    // TODO(b/276982847): Use alternative to Bitmap to create function LUT texture.
    Bitmap functionLookupBitmap =
        Bitmap.createBitmap(lutRasterSize, /* height= */ 1, Bitmap.Config.RGBA_F16);
    functionLookupBitmap.copyPixelsFromBuffer(functionShortBuffer);

    // Create new GL texture if needed.
    if (functionLutTexture == GlTextureInfo.UNSET || functionLutTexture.width != lutRasterSize) {
      functionLutTexture.release();

      // Need to use high precision to force 16FP color.
      int functionLutTextureId =
          GlUtil.createTexture(
              lutRasterSize, /* height= */ 1, /* useHighPrecisionColorComponents= */ true);

      functionLutTexture =
          glObjectsProvider.createBuffersForTexture(
              functionLutTextureId, lutRasterSize, /* height= */ 1);
    }
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, /* level= */ 0, functionLookupBitmap, /* border= */ 0);
    GlUtil.checkGlError();
  }

  private GlTextureInfo configurePixelTexture(
      GlObjectsProvider glObjectsProvider, GlTextureInfo existingTexture, Size size)
      throws GlUtil.GlException {
    if (size.getWidth() == existingTexture.width && size.getHeight() == existingTexture.height) {
      return existingTexture;
    }

    existingTexture.release();
    int texId = GlUtil.createTexture(size.getWidth(), size.getHeight(), useHdr);

    return glObjectsProvider.createBuffersForTexture(texId, size.getWidth(), size.getHeight());
  }

  // BEGIN COPIED FP16 code.
  // Source: libcore/luni/src/main/java/libcore/util/FP16.java
  // Float to half float conversion, copied from FP16.  This code is introduced in API26, so the
  // one required method is copied here.
  private static short fp16FromFloat(float f) {
    int bits = Float.floatToRawIntBits(f);
    int s = bits >>> FP32_SIGN_SHIFT;
    int e = (bits >>> FP32_EXPONENT_SHIFT) & FP32_SHIFTED_EXPONENT_MASK;
    int m = bits & FP32_SIGNIFICAND_MASK;
    int outE = 0;
    int outM = 0;
    if (e == 0xff) { // Infinite or NaN
      outE = 0x1f;
      outM = (m != 0) ? 0x200 : 0;
    } else {
      e = e - FP32_EXPONENT_BIAS + FP16_EXPONENT_BIAS;
      if (e >= 0x1f) { // Overflow
        outE = 0x1f;
      } else if (e <= 0) { // Underflow
        if (e >= -10) {
          // The fp32 value is a normalized float less than MIN_NORMAL,
          // we convert to a denorm fp16
          m |= 0x800000;
          int shift = 14 - e;
          outM = m >>> shift;
          int lowm = m & ((1 << shift) - 1);
          int hway = 1 << (shift - 1);
          // if above halfway or exactly halfway and outM is odd
          if (lowm + (outM & 1) > hway) {
            // Round to nearest even
            // Can overflow into exponent bit, which surprisingly is OK.
            // This increment relies on the +outM in the return statement below
            outM++;
          }
        }
      } else {
        outE = e;
        outM = m >>> 13;
        // if above halfway or exactly halfway and outM is odd
        if ((m & 0x1fff) + (outM & 0x1) > 0x1000) {
          // Round to nearest even
          // Can overflow into exponent bit, which surprisingly is OK.
          // This increment relies on the +outM in the return statement below
          outM++;
        }
      }
    }
    // The outM is added here as the +1 increments for outM above can
    // cause an overflow in the exponent bit which is OK.
    return (short) ((s << FP16_SIGN_SHIFT) | ((outE << FP16_EXPONENT_SHIFT) + outM));
  }
  // END FP16 copied code.
}
