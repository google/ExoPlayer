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

package androidx.media3.transformer;

import android.content.Context;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.FrameProcessor;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Clock;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.extractor.metadata.mp4.SlowMotionData;
import com.google.common.collect.ImmutableList;

/* package */ final class TransformerInternal {

  public interface Listener {

    void onTransformationCompleted();

    void onTransformationError(TransformationException exception);
  }

  private final Context context;
  private final TransformationRequest transformationRequest;
  private final ImmutableList<Effect> videoEffects;
  private final Codec.DecoderFactory decoderFactory;
  private final Codec.EncoderFactory encoderFactory;
  private final FrameProcessor.Factory frameProcessorFactory;
  private final DebugViewProvider debugViewProvider;
  private final ExoPlayerAssetLoader exoPlayerAssetLoader;

  public TransformerInternal(
      Context context,
      TransformationRequest transformationRequest,
      ImmutableList<Effect> videoEffects,
      boolean removeAudio,
      boolean removeVideo,
      MediaSource.Factory mediaSourceFactory,
      Codec.DecoderFactory decoderFactory,
      Codec.EncoderFactory encoderFactory,
      FrameProcessor.Factory frameProcessorFactory,
      Looper looper,
      DebugViewProvider debugViewProvider,
      Clock clock) {
    this.context = context;
    this.transformationRequest = transformationRequest;
    this.videoEffects = videoEffects;
    this.decoderFactory = decoderFactory;
    this.encoderFactory = encoderFactory;
    this.frameProcessorFactory = frameProcessorFactory;
    this.debugViewProvider = debugViewProvider;
    exoPlayerAssetLoader =
        new ExoPlayerAssetLoader(
            context, removeAudio, removeVideo, mediaSourceFactory, looper, clock);
  }

  public void start(
      MediaItem mediaItem,
      MuxerWrapper muxerWrapper,
      Listener listener,
      FallbackListener fallbackListener,
      Transformer.AsyncErrorListener asyncErrorListener) {
    ComponentListener componentListener =
        new ComponentListener(
            mediaItem, muxerWrapper, listener, fallbackListener, asyncErrorListener);
    exoPlayerAssetLoader.start(mediaItem, componentListener, asyncErrorListener);
  }

  public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
    return exoPlayerAssetLoader.getProgress(progressHolder);
  }

  public void release() {
    exoPlayerAssetLoader.release();
  }

  private class ComponentListener implements ExoPlayerAssetLoader.Listener {

    private final MediaItem mediaItem;
    private final MuxerWrapper muxerWrapper;
    private final TransformerInternal.Listener listener;
    private final FallbackListener fallbackListener;
    private final Transformer.AsyncErrorListener asyncErrorListener;

    private volatile boolean trackRegistered;

    public ComponentListener(
        MediaItem mediaItem,
        MuxerWrapper muxerWrapper,
        Listener listener,
        FallbackListener fallbackListener,
        Transformer.AsyncErrorListener asyncErrorListener) {
      this.mediaItem = mediaItem;
      this.muxerWrapper = muxerWrapper;
      this.listener = listener;
      this.fallbackListener = fallbackListener;
      this.asyncErrorListener = asyncErrorListener;
    }

    @Override
    public void onTrackRegistered() {
      trackRegistered = true;
      muxerWrapper.registerTrack();
      fallbackListener.registerTrack();
    }

    @Override
    public void onAllTracksRegistered() {
      if (!trackRegistered) {
        onError(new IllegalStateException("The output does not contain any tracks."));
      }
    }

    @Override
    public SamplePipeline onTrackAdded(
        Format format, long streamStartPositionUs, long streamOffsetUs)
        throws TransformationException {
      return getSamplePipeline(format, streamStartPositionUs, streamOffsetUs);
    }

    @Override
    public void onError(Exception e) {
      TransformationException transformationException =
          e instanceof PlaybackException
              ? TransformationException.createForPlaybackException((PlaybackException) e)
              : TransformationException.createForUnexpected(e);
      listener.onTransformationError(transformationException);
    }

    @Override
    public void onEnded() {
      listener.onTransformationCompleted();
    }

    private SamplePipeline getSamplePipeline(
        Format inputFormat, long streamStartPositionUs, long streamOffsetUs)
        throws TransformationException {
      if (MimeTypes.isAudio(inputFormat.sampleMimeType) && shouldTranscodeAudio(inputFormat)) {
        return new AudioTranscodingSamplePipeline(
            inputFormat,
            streamStartPositionUs,
            streamOffsetUs,
            transformationRequest,
            decoderFactory,
            encoderFactory,
            muxerWrapper,
            fallbackListener);
      } else if (MimeTypes.isVideo(inputFormat.sampleMimeType)
          && shouldTranscodeVideo(inputFormat, streamStartPositionUs, streamOffsetUs)) {
        return new VideoTranscodingSamplePipeline(
            context,
            inputFormat,
            streamStartPositionUs,
            streamOffsetUs,
            transformationRequest,
            videoEffects,
            frameProcessorFactory,
            decoderFactory,
            encoderFactory,
            muxerWrapper,
            fallbackListener,
            asyncErrorListener,
            debugViewProvider);
      } else {
        return new PassthroughSamplePipeline(
            inputFormat,
            streamStartPositionUs,
            streamOffsetUs,
            transformationRequest,
            muxerWrapper,
            fallbackListener);
      }
    }

    private boolean shouldTranscodeAudio(Format inputFormat) {
      if (encoderFactory.audioNeedsEncoding()) {
        return true;
      }
      if (transformationRequest.audioMimeType != null
          && !transformationRequest.audioMimeType.equals(inputFormat.sampleMimeType)) {
        return true;
      }
      if (transformationRequest.audioMimeType == null
          && !muxerWrapper.supportsSampleMimeType(inputFormat.sampleMimeType)) {
        return true;
      }
      if (transformationRequest.flattenForSlowMotion && isSlowMotion(inputFormat)) {
        return true;
      }
      return false;
    }

    private boolean isSlowMotion(Format format) {
      @Nullable Metadata metadata = format.metadata;
      if (metadata == null) {
        return false;
      }
      for (int i = 0; i < metadata.length(); i++) {
        if (metadata.get(i) instanceof SlowMotionData) {
          return true;
        }
      }
      return false;
    }

    private boolean shouldTranscodeVideo(
        Format inputFormat, long streamStartPositionUs, long streamOffsetUs) {
      if ((streamStartPositionUs - streamOffsetUs) != 0
          && !mediaItem.clippingConfiguration.startsAtKeyFrame) {
        return true;
      }
      if (encoderFactory.videoNeedsEncoding()) {
        return true;
      }
      if (transformationRequest.enableRequestSdrToneMapping) {
        return true;
      }
      if (transformationRequest.videoMimeType != null
          && !transformationRequest.videoMimeType.equals(inputFormat.sampleMimeType)) {
        return true;
      }
      if (transformationRequest.videoMimeType == null
          && !muxerWrapper.supportsSampleMimeType(inputFormat.sampleMimeType)) {
        return true;
      }
      if (inputFormat.pixelWidthHeightRatio != 1f) {
        return true;
      }
      if (transformationRequest.rotationDegrees != 0f) {
        return true;
      }
      if (transformationRequest.scaleX != 1f) {
        return true;
      }
      if (transformationRequest.scaleY != 1f) {
        return true;
      }
      // The decoder rotates encoded frames for display by inputFormat.rotationDegrees.
      int decodedHeight =
          (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.height : inputFormat.width;
      if (transformationRequest.outputHeight != C.LENGTH_UNSET
          && transformationRequest.outputHeight != decodedHeight) {
        return true;
      }
      if (!videoEffects.isEmpty()) {
        return true;
      }
      return false;
    }
  }
}
