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

import android.graphics.Bitmap;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.TimestampIterator;
import androidx.media3.effect.Presentation;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** A wrapper for {@link VideoFrameProcessor} that handles {@link GraphInput} events. */
/* package */ final class VideoFrameProcessingWrapper implements GraphInput {
  private final VideoFrameProcessor videoFrameProcessor;
  private final AtomicLong mediaItemOffsetUs;
  private final ColorInfo inputColorInfo;
  private final long initialTimestampOffsetUs;
  @Nullable final Presentation presentation;

  public VideoFrameProcessingWrapper(
      VideoFrameProcessor videoFrameProcessor,
      ColorInfo inputColorInfo,
      @Nullable Presentation presentation,
      long initialTimestampOffsetUs) {
    this.videoFrameProcessor = videoFrameProcessor;
    this.mediaItemOffsetUs = new AtomicLong();
    // TODO: b/307952514 - Remove inputColorInfo reference.
    this.inputColorInfo = inputColorInfo;
    this.initialTimestampOffsetUs = initialTimestampOffsetUs;
    this.presentation = presentation;
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
          createEffectListWithPresentation(editedMediaItem.effects.videoEffects, presentation),
          new FrameInfo.Builder(inputColorInfo, decodedSize.getWidth(), decodedSize.getHeight())
              .setPixelWidthHeightRatio(trackFormat.pixelWidthHeightRatio)
              .setOffsetToAddUs(initialTimestampOffsetUs + mediaItemOffsetUs.get())
              .build());
    }
    mediaItemOffsetUs.addAndGet(durationUs);
  }

  @Override
  public @InputResult int queueInputBitmap(
      Bitmap inputBitmap, TimestampIterator timestampIterator) {
    return videoFrameProcessor.queueInputBitmap(inputBitmap, timestampIterator)
        ? INPUT_RESULT_SUCCESS
        : INPUT_RESULT_TRY_AGAIN_LATER;
  }

  @Override
  public void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener) {
    videoFrameProcessor.setOnInputFrameProcessedListener(listener);
  }

  @Override
  public @InputResult int queueInputTexture(int texId, long presentationTimeUs) {
    return videoFrameProcessor.queueInputTexture(texId, presentationTimeUs)
        ? INPUT_RESULT_SUCCESS
        : INPUT_RESULT_TRY_AGAIN_LATER;
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
    return videoFrameProcessor.registerInputFrame();
  }

  @Override
  public void signalEndOfVideoInput() {
    videoFrameProcessor.signalEndOfInput();
  }

  public void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    videoFrameProcessor.setOutputSurfaceInfo(outputSurfaceInfo);
  }

  public void release() {
    videoFrameProcessor.release();
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
}
