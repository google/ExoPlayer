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

package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.util.SparseArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlObjectsProvider;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import com.google.android.exoplayer2.util.VideoFrameProcessor;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A switcher to switch between {@linkplain TextureManager texture managers} of different
 * {@linkplain VideoFrameProcessor.InputType input types}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class InputSwitcher {
  private final Context context;
  private final ColorInfo outputColorInfo;
  private final GlObjectsProvider glObjectsProvider;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  private final SparseArray<Input> inputs;
  private final boolean enableColorTransfers;

  private @MonotonicNonNull GlShaderProgram downstreamShaderProgram;
  private @MonotonicNonNull TextureManager activeTextureManager;
  private boolean inputEnded;
  private int activeInputType;

  public InputSwitcher(
      Context context,
      ColorInfo outputColorInfo,
      GlObjectsProvider glObjectsProvider,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor,
      boolean enableColorTransfers) {
    this.context = context;
    this.outputColorInfo = outputColorInfo;
    this.glObjectsProvider = glObjectsProvider;
    this.videoFrameProcessingTaskExecutor = videoFrameProcessingTaskExecutor;
    this.inputs = new SparseArray<>();
    this.enableColorTransfers = enableColorTransfers;
    activeInputType = C.INDEX_UNSET;
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
      case VideoFrameProcessor.INPUT_TYPE_SURFACE:
        samplingShaderProgram =
            DefaultShaderProgram.createWithExternalSampler(
                context,
                /* matrixTransformations= */ ImmutableList.of(),
                /* rgbMatrices= */ ImmutableList.of(),
                inputColorInfo,
                outputColorInfo,
                enableColorTransfers);
        samplingShaderProgram.setGlObjectsProvider(glObjectsProvider);
        textureManager =
            new ExternalTextureManager(samplingShaderProgram, videoFrameProcessingTaskExecutor);
        inputs.put(inputType, new Input(textureManager, samplingShaderProgram));
        break;
      case VideoFrameProcessor.INPUT_TYPE_BITMAP:
        samplingShaderProgram =
            DefaultShaderProgram.createWithInternalSampler(
                context,
                /* matrixTransformations= */ ImmutableList.of(),
                /* rgbMatrices= */ ImmutableList.of(),
                inputColorInfo,
                outputColorInfo,
                enableColorTransfers,
                inputType);
        samplingShaderProgram.setGlObjectsProvider(glObjectsProvider);
        textureManager =
            new BitmapTextureManager(samplingShaderProgram, videoFrameProcessingTaskExecutor);
        inputs.put(inputType, new Input(textureManager, samplingShaderProgram));
        break;
      case VideoFrameProcessor.INPUT_TYPE_TEXTURE_ID:
        samplingShaderProgram =
            DefaultShaderProgram.createWithInternalSampler(
                context,
                /* matrixTransformations= */ ImmutableList.of(),
                /* rgbMatrices= */ ImmutableList.of(),
                inputColorInfo,
                outputColorInfo,
                enableColorTransfers,
                inputType);
        samplingShaderProgram.setGlObjectsProvider(glObjectsProvider);
        textureManager =
            new TexIdTextureManager(samplingShaderProgram, videoFrameProcessingTaskExecutor);
        inputs.put(inputType, new Input(textureManager, samplingShaderProgram));
        break;
      default:
        throw new VideoFrameProcessingException("Unsupported input type " + inputType);
    }
  }

  public void setDownstreamShaderProgram(GlShaderProgram downstreamShaderProgram) {
    this.downstreamShaderProgram = downstreamShaderProgram;

    for (int i = 0; i < inputs.size(); i++) {
      @VideoFrameProcessor.InputType int inputType = inputs.keyAt(i);
      Input input = inputs.get(inputType);
      input.setChainingListener(
          new GatedChainingListenerWrapper(
              input.samplingGlShaderProgram,
              this.downstreamShaderProgram,
              videoFrameProcessingTaskExecutor));
    }
  }

  /**
   * Switches to a new source of input.
   *
   * <p>Blocks until the current input stream is processed.
   *
   * <p>Must be called after the corresponding {@code newInputType} is {@linkplain #registerInput
   * registered}.
   *
   * @param newInputType The new {@link VideoFrameProcessor.InputType} to switch to.
   */
  public void switchToInput(@VideoFrameProcessor.InputType int newInputType) {
    checkStateNotNull(downstreamShaderProgram);
    checkState(inputs.indexOfKey(newInputType) >= 0, "Input type not registered: " + newInputType);

    if (newInputType == activeInputType) {
      activeTextureManager = inputs.get(activeInputType).textureManager;
    }

    for (int i = 0; i < inputs.size(); i++) {
      @VideoFrameProcessor.InputType int inputType = inputs.keyAt(i);
      Input input = inputs.get(inputType);
      if (inputType == newInputType) {
        input.setActive(true);
        downstreamShaderProgram.setInputListener(checkNotNull(input.gatedChainingListenerWrapper));
        activeTextureManager = input.textureManager;
      } else {
        input.setActive(false);
      }
    }
    activeInputType = newInputType;
  }

  /**
   * Returns the {@link TextureManager} that is currently being used.
   *
   * <p>Must call {@link #switchToInput} before calling this method.
   */
  public TextureManager activeTextureManager() {
    return checkNotNull(activeTextureManager);
  }

  /**
   * Invokes {@link TextureManager#signalEndOfCurrentInputStream} on the active {@link
   * TextureManager}.
   */
  public void signalEndOfCurrentInputStream() {
    checkNotNull(activeTextureManager).signalEndOfCurrentInputStream();
  }

  /** Signals end of input to all {@linkplain #registerInput registered inputs}. */
  public void signalEndOfInput() {
    checkState(!inputEnded);
    inputEnded = true;
    for (int i = 0; i < inputs.size(); i++) {
      @VideoFrameProcessor.InputType int inputType = inputs.keyAt(i);
      inputs.get(inputType).signalEndOfInput();
    }
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
      checkStateNotNull(gatedChainingListenerWrapper);
      gatedChainingListenerWrapper.setActive(active);
    }

    public void signalEndOfInput() {
      textureManager.signalEndOfInput();
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
    private boolean isActive = false;

    public GatedChainingListenerWrapper(
        GlShaderProgram producingGlShaderProgram,
        GlShaderProgram consumingGlShaderProgram,
        VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor) {
      this.chainingGlShaderProgramListener =
          new ChainingGlShaderProgramListener(
              producingGlShaderProgram, consumingGlShaderProgram, videoFrameProcessingTaskExecutor);
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
