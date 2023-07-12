/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.transformer;

import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_BITMAP;
import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_SURFACE;
import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_TEXTURE_ID;
import static androidx.media3.common.util.Assertions.checkNotNull;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.Size;
import androidx.media3.effect.Presentation;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/** Processes decoded video frames from one single input. */
/* package */ final class SingleInputVideoGraph implements GraphInput {

  /**
   * Listener for video frame processing events.
   *
   * <p>The methods are called from the GL thread.
   */
  public interface Listener {
    /**
     * Called when the output size changes.
     *
     * @param width The new output width in pixels.
     * @param height The new output width in pixels.
     * @return A {@link SurfaceInfo} to which {@link SingleInputVideoGraph} renders to, or {@code
     *     null} if the output is not needed.
     */
    @Nullable
    SurfaceInfo onOutputSizeChanged(int width, int height);

    /** Called after the {@link SingleInputVideoGraph} has rendered its final output frame. */
    void onEnded(long finalFramePresentationTimeUs);
  }

  private final VideoFrameProcessor videoFrameProcessor;
  private final AtomicLong mediaItemOffsetUs;
  private final ColorInfo inputColorInfo;

  @Nullable final Presentation presentation;

  private volatile boolean hasProducedFrameWithTimestampZero;

  /**
   * Creates a new instance.
   *
   * @param context A {@link Context}.
   * @param videoFrameProcessorFactory A {@link VideoFrameProcessor.Factory}.
   * @param inputColorInfo The {@link ColorInfo} for the input frames.
   * @param outputColorInfo The {@link ColorInfo} for the output frames.
   * @param listener A {@link Listener}.
   * @param errorConsumer A {@link Consumer} of {@link ExportException}.
   * @param debugViewProvider A {@link DebugViewProvider}.
   * @param listenerExecutor An {@link Executor} on which {@link VideoFrameProcessor.Listener}
   *     methods are called.
   * @param renderFramesAutomatically Whether to automatically render output frames. Use {@code
   *     false} when controlling the presentation of output frames.
   * @param presentation A {@link Presentation} to apply to processed frames.
   * @throws VideoFrameProcessingException When video frame processing fails.
   */
  public SingleInputVideoGraph(
      Context context,
      VideoFrameProcessor.Factory videoFrameProcessorFactory,
      ColorInfo inputColorInfo,
      ColorInfo outputColorInfo,
      Listener listener,
      Consumer<ExportException> errorConsumer,
      DebugViewProvider debugViewProvider,
      Executor listenerExecutor,
      boolean renderFramesAutomatically,
      @Nullable Presentation presentation)
      throws VideoFrameProcessingException {
    this.mediaItemOffsetUs = new AtomicLong();
    this.inputColorInfo = inputColorInfo;
    this.presentation = presentation;

    videoFrameProcessor =
        videoFrameProcessorFactory.create(
            context,
            debugViewProvider,
            inputColorInfo,
            outputColorInfo,
            renderFramesAutomatically,
            listenerExecutor,
            new VideoFrameProcessor.Listener() {
              private long lastProcessedFramePresentationTimeUs;

              @Override
              public void onOutputSizeChanged(int width, int height) {
                // TODO: b/289986435 - Allow setting output surface info on VideoGraph.
                checkNotNull(videoFrameProcessor)
                    .setOutputSurfaceInfo(listener.onOutputSizeChanged(width, height));
              }

              @Override
              public void onOutputFrameAvailableForRendering(long presentationTimeUs) {
                // Frames are rendered automatically.
                if (presentationTimeUs == 0) {
                  hasProducedFrameWithTimestampZero = true;
                }
                lastProcessedFramePresentationTimeUs = presentationTimeUs;
              }

              @Override
              public void onError(VideoFrameProcessingException exception) {
                errorConsumer.accept(
                    ExportException.createForVideoFrameProcessingException(exception));
              }

              @Override
              public void onEnded() {
                listener.onEnded(lastProcessedFramePresentationTimeUs);
              }
            });
  }

  @Override
  public void onMediaItemChanged(
      EditedMediaItem editedMediaItem,
      long durationUs,
      @Nullable Format trackFormat,
      boolean isLast) {
    if (trackFormat != null) {
      Size decodedSize = getDecodedSize(trackFormat);
      videoFrameProcessor.registerInputStream(
          getInputType(checkNotNull(trackFormat.sampleMimeType)),
          createEffectListWithPresentation(editedMediaItem.effects.videoEffects, presentation));
      videoFrameProcessor.setInputFrameInfo(
          new FrameInfo.Builder(decodedSize.getWidth(), decodedSize.getHeight())
              .setPixelWidthHeightRatio(trackFormat.pixelWidthHeightRatio)
              .setOffsetToAddUs(mediaItemOffsetUs.get())
              .build());
    }
    mediaItemOffsetUs.addAndGet(durationUs);
  }

  @Override
  public boolean queueInputBitmap(Bitmap inputBitmap, long durationUs, int frameRate) {
    videoFrameProcessor.queueInputBitmap(inputBitmap, durationUs, frameRate);
    return true;
  }

  @Override
  public void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener) {
    videoFrameProcessor.setOnInputFrameProcessedListener(listener);
  }

  @Override
  public boolean queueInputTexture(int texId, long presentationTimeUs) {
    videoFrameProcessor.queueInputTexture(texId, presentationTimeUs);
    return true;
  }

  @Override
  public Surface getInputSurface() {
    return videoFrameProcessor.getInputSurface();
  }

  @Override
  public ColorInfo getExpectedInputColorInfo() {
    return inputColorInfo;
  }

  @Override
  public int getPendingVideoFrameCount() {
    return videoFrameProcessor.getPendingInputFrameCount();
  }

  @Override
  public boolean registerVideoFrame(long presentationTimeUs) {
    videoFrameProcessor.registerInputFrame();
    return true;
  }

  @Override
  public void signalEndOfVideoInput() {
    videoFrameProcessor.signalEndOfInput();
  }

  /* package */ boolean hasProducedFrameWithTimestampZero() {
    return hasProducedFrameWithTimestampZero;
  }

  public void release() {
    videoFrameProcessor.release();
  }

  private static @VideoFrameProcessor.InputType int getInputType(String sampleMimeType) {
    if (MimeTypes.isImage(sampleMimeType)) {
      return INPUT_TYPE_BITMAP;
    }
    if (sampleMimeType.equals(MimeTypes.VIDEO_RAW)) {
      return INPUT_TYPE_TEXTURE_ID;
    }
    if (MimeTypes.isVideo(sampleMimeType)) {
      return INPUT_TYPE_SURFACE;
    }
    throw new IllegalArgumentException("MIME type not supported " + sampleMimeType);
  }

  private static Size getDecodedSize(Format format) {
    // The decoder rotates encoded frames for display by firstInputFormat.rotationDegrees.
    int decodedWidth = (format.rotationDegrees % 180 == 0) ? format.width : format.height;
    int decodedHeight = (format.rotationDegrees % 180 == 0) ? format.height : format.width;
    return new Size(decodedWidth, decodedHeight);
  }

  private static ImmutableList<Effect> createEffectListWithPresentation(
      List<Effect> effects, @Nullable Presentation presentation) {
    if (presentation == null) {
      return ImmutableList.copyOf(effects);
    }
    ImmutableList.Builder<Effect> effectsWithPresentationBuilder = new ImmutableList.Builder<>();
    effectsWithPresentationBuilder.addAll(effects).add(presentation);
    return effectsWithPresentationBuilder.build();
  }
}
