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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.transformer.Composition.HDR_MODE_KEEP_HDR;
import static java.lang.Math.round;

import android.media.MediaCodec;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.effect.GlEffect;
import com.google.android.exoplayer2.effect.ScaleAndRotateTransformation;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.mp4.SlowMotionData;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;

/**
 * Utility methods for Transformer.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
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
        && maybeCalculateTotalRotationDegreesAppliedInEffects(videoEffects, inputFormat) == -1;
  }

  /**
   * Returns the total rotation degrees of all the rotations in {@code videoEffects}, or {@code -1}
   * if {@code videoEffects} contains any effects that are not no-ops or regular rotations.
   *
   * <p>If all the {@code videoEffects} are either noOps or regular rotations, then the rotations
   * can be applied in the {@linkplain #maybeSetMuxerWrapperAdditionalRotationDegrees(MuxerWrapper,
   * ImmutableList, Format) MuxerWrapper}.
   */
  private static float maybeCalculateTotalRotationDegreesAppliedInEffects(
      ImmutableList<Effect> videoEffects, Format inputFormat) {
    int width = (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.width : inputFormat.height;
    int height = (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.height : inputFormat.width;
    float totalRotationDegrees = 0;
    for (int i = 0; i < videoEffects.size(); i++) {
      Effect videoEffect = videoEffects.get(i);
      if (!(videoEffect instanceof GlEffect)) {
        // We cannot confirm whether Effect instances that are not GlEffect instances are
        // no-ops.
        return -1;
      }
      GlEffect glEffect = (GlEffect) videoEffect;
      if (videoEffect instanceof ScaleAndRotateTransformation) {
        ScaleAndRotateTransformation scaleAndRotateTransformation =
            (ScaleAndRotateTransformation) videoEffect;
        if (scaleAndRotateTransformation.scaleX != 1f
            || scaleAndRotateTransformation.scaleY != 1f) {
          return -1;
        }
        float rotationDegrees = scaleAndRotateTransformation.rotationDegrees;
        if (rotationDegrees % 90f != 0) {
          return -1;
        }
        totalRotationDegrees += rotationDegrees;
        width = (totalRotationDegrees % 180 == 0) ? inputFormat.width : inputFormat.height;
        height = (totalRotationDegrees % 180 == 0) ? inputFormat.height : inputFormat.width;
        continue;
      }
      if (!glEffect.isNoOp(width, height)) {
        return -1;
      }
    }
    totalRotationDegrees %= 360;
    return totalRotationDegrees % 90 == 0 ? totalRotationDegrees : -1;
  }

  /**
   * Sets {@linkplain MuxerWrapper#setAdditionalRotationDegrees(int) the additionalRotationDegrees}
   * on the given {@link MuxerWrapper} if the given {@code videoEffects} only contains a mix of
   * regular rotations and no-ops. A regular rotation is a rotation divisible by 90 degrees.
   */
  public static void maybeSetMuxerWrapperAdditionalRotationDegrees(
      MuxerWrapper muxerWrapper, ImmutableList<Effect> videoEffects, Format inputFormat) {
    float rotationDegrees =
        maybeCalculateTotalRotationDegreesAppliedInEffects(videoEffects, inputFormat);
    if (rotationDegrees == 90f || rotationDegrees == 180f || rotationDegrees == 270f) {
      // The MuxerWrapper rotation is clockwise while the ScaleAndRotateTransformation rotation
      // is counterclockwise.
      muxerWrapper.setAdditionalRotationDegrees(360 - round(rotationDegrees));
    }
  }
}
