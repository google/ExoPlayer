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

package androidx.media3.transformer;

import static androidx.media3.transformer.Composition.HDR_MODE_KEEP_HDR;

import android.media.MediaCodec;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.extractor.metadata.mp4.SlowMotionData;
import com.google.common.collect.ImmutableList;

/** Utility methods for Transformer. */
/* package */ final class TransformerUtil {

  private TransformerUtil() {}

  /**
   * Returns the {@link C.TrackType track type} constant corresponding to how a specified MIME type
   * should be processed, which may be {@link C#TRACK_TYPE_UNKNOWN} if it could not be determined.
   *
   * <p>{@linkplain MimeTypes#isImage Image} MIME types are processed as {@link C#TRACK_TYPE_VIDEO}.
   *
   * <p>See {@link MimeTypes#getTrackType} for more details.
   */
  public static @C.TrackType int getProcessedTrackType(@Nullable String mimeType) {
    @C.TrackType int trackType = MimeTypes.getTrackType(mimeType);
    return trackType == C.TRACK_TYPE_IMAGE ? C.TRACK_TYPE_VIDEO : trackType;
  }

  /** Returns {@link MediaCodec} flags corresponding to {@link C.BufferFlags}. */
  public static int getMediaCodecFlags(@C.BufferFlags int flags) {
    int mediaCodecFlags = 0;
    if ((flags & C.BUFFER_FLAG_KEY_FRAME) == C.BUFFER_FLAG_KEY_FRAME) {
      mediaCodecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
    }
    if ((flags & C.BUFFER_FLAG_END_OF_STREAM) == C.BUFFER_FLAG_END_OF_STREAM) {
      mediaCodecFlags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
    }
    return mediaCodecFlags;
  }

  /** Returns whether the audio track should be transcoded. */
  public static boolean shouldTranscodeAudio(
      Format inputFormat,
      Composition composition,
      int sequenceIndex,
      TransformationRequest transformationRequest,
      Codec.EncoderFactory encoderFactory,
      MuxerWrapper muxerWrapper) {
    if (composition.sequences.size() > 1
        || composition.sequences.get(sequenceIndex).editedMediaItems.size() > 1) {
      return !composition.transmuxAudio;
    }
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
    EditedMediaItem firstEditedMediaItem =
        composition.sequences.get(sequenceIndex).editedMediaItems.get(0);
    if (firstEditedMediaItem.flattenForSlowMotion && containsSlowMotionData(inputFormat)) {
      return true;
    }
    if (!firstEditedMediaItem.effects.audioProcessors.isEmpty()) {
      return true;
    }
    return false;
  }

  /**
   * Returns whether the {@link Format} contains {@linkplain SlowMotionData slow motion metadata}.
   */
  private static boolean containsSlowMotionData(Format format) {
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

  /** Returns whether the video track should be transcoded. */
  public static boolean shouldTranscodeVideo(
      Format inputFormat,
      Composition composition,
      int sequenceIndex,
      TransformationRequest transformationRequest,
      Codec.EncoderFactory encoderFactory,
      MuxerWrapper muxerWrapper) {

    if (composition.sequences.size() > 1
        || composition.sequences.get(sequenceIndex).editedMediaItems.size() > 1) {
      return !composition.transmuxVideo;
    }
    EditedMediaItem firstEditedMediaItem =
        composition.sequences.get(sequenceIndex).editedMediaItems.get(0);
    if (encoderFactory.videoNeedsEncoding()) {
      return true;
    }
    if (transformationRequest.hdrMode != HDR_MODE_KEEP_HDR) {
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
    ImmutableList<Effect> videoEffects = firstEditedMediaItem.effects.videoEffects;
    return !videoEffects.isEmpty()
        && !areVideoEffectsAllNoOp(videoEffects, inputFormat)
        && !hasOnlyRegularRotationEffect(videoEffects, muxerWrapper);
  }

  /**
   * Returns whether the collection of {@code videoEffects} would be a {@linkplain
   * GlEffect#isNoOp(int, int) no-op}, if queued samples of this {@link Format}.
   */
  public static boolean areVideoEffectsAllNoOp(
      ImmutableList<Effect> videoEffects, Format inputFormat) {
    int decodedWidth =
        (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.width : inputFormat.height;
    int decodedHeight =
        (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.height : inputFormat.width;
    for (int i = 0; i < videoEffects.size(); i++) {
      Effect videoEffect = videoEffects.get(i);
      if (!(videoEffect instanceof GlEffect)) {
        // We cannot confirm whether Effect instances that are not GlEffect instances are
        // no-ops.
        return false;
      }
      GlEffect glEffect = (GlEffect) videoEffect;
      if (!glEffect.isNoOp(decodedWidth, decodedHeight)) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasOnlyRegularRotationEffect(
      ImmutableList<Effect> videoEffects, MuxerWrapper muxerWrapper) {
    if (videoEffects.size() != 1) {
      return false;
    }
    Effect videoEffect = videoEffects.get(0);
    if (!(videoEffect instanceof ScaleAndRotateTransformation)) {
      return false;
    }
    ScaleAndRotateTransformation scaleAndRotateTransformation =
        (ScaleAndRotateTransformation) videoEffect;
    if (scaleAndRotateTransformation.scaleX != 1f || scaleAndRotateTransformation.scaleY != 1f) {
      return false;
    }
    float rotationDegrees = scaleAndRotateTransformation.rotationDegrees;
    if (rotationDegrees == 90f || rotationDegrees == 180f || rotationDegrees == 270f) {
      // The MuxerWrapper rotation is clockwise while the ScaleAndRotateTransformation rotation
      // is counterclockwise.
      muxerWrapper.setAdditionalRotationDegrees(360 - Math.round(rotationDegrees));
      return true;
    }
    return false;
  }
}
