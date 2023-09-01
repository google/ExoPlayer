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
 *
 */

package androidx.media3.effect;

import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_BITMAP;
import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_SURFACE;
import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_TEXTURE_ID;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.contains;

import android.content.Context;
import android.util.SparseArray;
import android.view.Surface;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A switcher to switch between {@linkplain TextureManager texture managers} of different
 * {@linkplain VideoFrameProcessor.InputType input types}.
 */
/* package */ final class InputSwitcher {
  private final Context context;
  private final ColorInfo outputColorInfo;
  private final GlObjectsProvider glObjectsProvider;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  private final GlShaderProgram.ErrorListener samplingShaderProgramErrorListener;
  private final Executor errorListenerExecutor;
  private final SparseArray<Input> inputs;
  private final boolean enableColorTransfers;

  private @MonotonicNonNull GlShaderProgram downstreamShaderProgram;
  private @MonotonicNonNull TextureManager activeTextureManager;

  public InputSwitcher(
      Context context,
      ColorInfo outputColorInfo,
      GlObjectsProvider glObjectsProvider,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor,
      Executor errorListenerExecutor,
      GlShaderProgram.ErrorListener samplingShaderProgramErrorListener,
      boolean enableColorTransfers) {
    this.context = context;
    this.outputColorInfo = outputColorInfo;
    this.glObjectsProvider = glObjectsProvider;
    this.videoFrameProcessingTaskExecutor = videoFrameProcessingTaskExecutor;
    this.errorListenerExecutor = errorListenerExecutor;
    this.samplingShaderProgramErrorListener = samplingShaderProgramErrorListener;
    this.inputs = new SparseArray<>();
    this.enableColorTransfers = enableColorTransfers;
  }

  /**
   * Registers for a new {@link VideoFrameProcessor.InputType input}.
   *
   * <p>Can be called multiple times on the same {@link VideoFrameProcessor.InputType inputType},
   * with the new inputs overwriting the old ones. For example, a new instance of {@link
   * ExternalTextureManager} is created following each call to this method with {@link
   * VideoFrameProcessor#INPUT_TYPE_SURFACE}. Effectively, the {@code inputSwitcher} keeps exactly
   * one {@link TextureManager} per {@linkplain VideoFrameProcessor.InputType input type}.
   *
   * <p>Creates an {@link TextureManager} and an appropriate {@linkplain DefaultShaderProgram
   * sampler} to sample from the input.
   *
   * @param inputColorInfo The {@link ColorInfo} for the input frames.
   * @param inputType The {@linkplain VideoFrameProcessor.InputType type} of the input being
   *     registered.
   */
  public void registerInput(ColorInfo inputColorInfo, @VideoFrameProcessor.InputType int inputType)
      throws VideoFrameProcessingException {
    // TODO(b/274109008): Investigate lazy instantiating the texture managers.
    DefaultShaderProgram samplingShaderProgram;
    TextureManager textureManager;
    // TODO(b/274109008): Refactor DefaultShaderProgram to create a class just for sampling.
    switch (inputType) {
      case INPUT_TYPE_SURFACE:
        samplingShaderProgram =
            DefaultShaderProgram.createWithExternalSampler(
                context,
                /* matrixTransformations= */ ImmutableList.of(),
                /* rgbMatrices= */ ImmutableList.of(),
                inputColorInfo,
                outputColorInfo,
                enableColorTransfers);
        samplingShaderProgram.setErrorListener(
            errorListenerExecutor, samplingShaderProgramErrorListener);
        textureManager =
            new ExternalTextureManager(
                glObjectsProvider, samplingShaderProgram, videoFrameProcessingTaskExecutor);
        inputs.put(inputType, new Input(textureManager, samplingShaderProgram));
        break;
      case INPUT_TYPE_BITMAP:
        samplingShaderProgram =
            DefaultShaderProgram.createWithInternalSampler(
                context,
                /* matrixTransformations= */ ImmutableList.of(),
                /* rgbMatrices= */ ImmutableList.of(),
                inputColorInfo,
                outputColorInfo,
                enableColorTransfers,
                inputType);
        samplingShaderProgram.setErrorListener(
            errorListenerExecutor, samplingShaderProgramErrorListener);
        textureManager =
            new BitmapTextureManager(
                glObjectsProvider, samplingShaderProgram, videoFrameProcessingTaskExecutor);
        inputs.put(inputType, new Input(textureManager, samplingShaderProgram));
        break;
      case INPUT_TYPE_TEXTURE_ID:
        samplingShaderProgram =
            DefaultShaderProgram.createWithInternalSampler(
                context,
                /* matrixTransformations= */ ImmutableList.of(),
                /* rgbMatrices= */ ImmutableList.of(),
                inputColorInfo,
                outputColorInfo,
                enableColorTransfers,
                inputType);
        samplingShaderProgram.setErrorListener(
            errorListenerExecutor, samplingShaderProgramErrorListener);
        textureManager =
            new TexIdTextureManager(
                glObjectsProvider, samplingShaderProgram, videoFrameProcessingTaskExecutor);
        inputs.put(inputType, new Input(textureManager, samplingShaderProgram));
        break;
      default:
        throw new VideoFrameProcessingException("Unsupported input type " + inputType);
    }
  }

  /** Sets the {@link GlShaderProgram} that {@code InputSwitcher} outputs to. */
  public void setDownstreamShaderProgram(GlShaderProgram downstreamShaderProgram) {
    this.downstreamShaderProgram = downstreamShaderProgram;
  }

  /**
   * Switches to a new source of input.
   *
   * <p>Must be called after the corresponding {@code newInputType} is {@linkplain #registerInput
   * registered}.
   *
   * @param newInputType The new {@link VideoFrameProcessor.InputType} to switch to.
   * @param inputFrameInfo The {@link FrameInfo} associated with the new input.
   */
  public void switchToInput(
      @VideoFrameProcessor.InputType int newInputType, FrameInfo inputFrameInfo) {
    checkStateNotNull(downstreamShaderProgram);
    checkState(contains(inputs, newInputType), "Input type not registered: " + newInputType);

    for (int i = 0; i < inputs.size(); i++) {
      @VideoFrameProcessor.InputType int inputType = inputs.keyAt(i);
      Input input = inputs.get(inputType);
      if (inputType == newInputType) {
        input.setChainingListener(
            new GatedChainingListenerWrapper(
                glObjectsProvider,
                input.samplingGlShaderProgram,
                this.downstreamShaderProgram,
                videoFrameProcessingTaskExecutor));
        input.setActive(true);
        downstreamShaderProgram.setInputListener(checkNotNull(input.gatedChainingListenerWrapper));
        activeTextureManager = input.textureManager;
      } else {
        input.setActive(false);
      }
    }
    checkNotNull(activeTextureManager).setInputFrameInfo(inputFrameInfo);
  }

  /** Returns whether the {@code InputSwitcher} is connected to an active input. */
  public boolean hasActiveInput() {
    return activeTextureManager != null;
  }

  /**
   * Returns the {@link TextureManager} that is currently being used.
   *
   * @throws IllegalStateException If the {@code InputSwitcher} is not connected to an {@linkplain
   *     #hasActiveInput() input}.
   */
  public TextureManager activeTextureManager() {
    return checkStateNotNull(activeTextureManager);
  }

  /**
   * Invokes {@link TextureManager#signalEndOfCurrentInputStream} on the active {@link
   * TextureManager}.
   */
  public void signalEndOfInputStream() {
    checkNotNull(activeTextureManager).signalEndOfCurrentInputStream();
  }

  /**
   * Returns the input {@link Surface}.
   *
   * @return The input {@link Surface}, regardless if the current input is {@linkplain
   *     #switchToInput set} to {@link VideoFrameProcessor#INPUT_TYPE_SURFACE}.
   * @throws IllegalStateException If {@link VideoFrameProcessor#INPUT_TYPE_SURFACE} is not
   *     {@linkplain #registerInput registered}.
   */
  public Surface getInputSurface() {
    checkState(contains(inputs, INPUT_TYPE_SURFACE));
    return inputs.get(INPUT_TYPE_SURFACE).textureManager.getInputSurface();
  }

  /**
   * See {@link DefaultVideoFrameProcessor#setInputDefaultBufferSize}.
   *
   * @throws IllegalStateException If {@link VideoFrameProcessor#INPUT_TYPE_SURFACE} is not
   *     {@linkplain #registerInput registered}.
   */
  public void setInputDefaultBufferSize(int width, int height) {
    checkState(contains(inputs, INPUT_TYPE_SURFACE));
    inputs.get(INPUT_TYPE_SURFACE).textureManager.setDefaultBufferSize(width, height);
  }

  /**
   * Sets the {@link OnInputFrameProcessedListener}.
   *
   * @throws IllegalStateException If {@link VideoFrameProcessor#INPUT_TYPE_TEXTURE_ID} is not
   *     {@linkplain #registerInput registered}.
   */
  public void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener) {
    checkState(contains(inputs, INPUT_TYPE_TEXTURE_ID));
    inputs.get(INPUT_TYPE_TEXTURE_ID).textureManager.setOnInputFrameProcessedListener(listener);
  }

  /** Releases the resources. */
  public void release() throws VideoFrameProcessingException {
    for (int i = 0; i < inputs.size(); i++) {
      inputs.get(inputs.keyAt(i)).release();
    }
  }

  /**
   * Wraps a {@link TextureManager} and an appropriate {@linkplain GlShaderProgram sampling shader
   * program}.
   *
   * <p>The output is always an internal GL texture.
   */
  private static final class Input {
    public final TextureManager textureManager;
    public final GlShaderProgram samplingGlShaderProgram;

    private @MonotonicNonNull GatedChainingListenerWrapper gatedChainingListenerWrapper;

    public Input(TextureManager textureManager, GlShaderProgram samplingGlShaderProgram) {
      this.textureManager = textureManager;
      this.samplingGlShaderProgram = samplingGlShaderProgram;
      samplingGlShaderProgram.setInputListener(textureManager);
    }

    public void setChainingListener(GatedChainingListenerWrapper gatedChainingListenerWrapper) {
      this.gatedChainingListenerWrapper = gatedChainingListenerWrapper;
      samplingGlShaderProgram.setOutputListener(gatedChainingListenerWrapper);
    }

    public void setActive(boolean active) {
      if (gatedChainingListenerWrapper == null) {
        return;
      }
      gatedChainingListenerWrapper.setActive(active);
    }

    public void release() throws VideoFrameProcessingException {
      textureManager.release();
      samplingGlShaderProgram.release();
    }
  }

  /**
   * Wraps a {@link ChainingGlShaderProgramListener}, with the ability to turn off the event
   * listening.
   */
  private static final class GatedChainingListenerWrapper
      implements GlShaderProgram.OutputListener, GlShaderProgram.InputListener {

    private final ChainingGlShaderProgramListener chainingGlShaderProgramListener;

    private boolean isActive;

    public GatedChainingListenerWrapper(
        GlObjectsProvider glObjectsProvider,
        GlShaderProgram producingGlShaderProgram,
        GlShaderProgram consumingGlShaderProgram,
        VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor) {
      this.chainingGlShaderProgramListener =
          new ChainingGlShaderProgramListener(
              glObjectsProvider,
              producingGlShaderProgram,
              consumingGlShaderProgram,
              videoFrameProcessingTaskExecutor);
    }

    @Override
    public void onReadyToAcceptInputFrame() {
      if (isActive) {
        chainingGlShaderProgramListener.onReadyToAcceptInputFrame();
      }
    }

    @Override
    public void onInputFrameProcessed(GlTextureInfo inputTexture) {
      if (isActive) {
        chainingGlShaderProgramListener.onInputFrameProcessed(inputTexture);
      }
    }

    @Override
    public synchronized void onFlush() {
      if (isActive) {
        chainingGlShaderProgramListener.onFlush();
      }
    }

    @Override
    public synchronized void onOutputFrameAvailable(
        GlTextureInfo outputTexture, long presentationTimeUs) {
      if (isActive) {
        chainingGlShaderProgramListener.onOutputFrameAvailable(outputTexture, presentationTimeUs);
      }
    }

    @Override
    public synchronized void onCurrentOutputStreamEnded() {
      if (isActive) {
        chainingGlShaderProgramListener.onCurrentOutputStreamEnded();
      }
    }

    public void setActive(boolean isActive) {
      this.isActive = isActive;
    }
  }
}
