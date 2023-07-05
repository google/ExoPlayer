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

import android.media.MediaCodec;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.effect.GlEffect;
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

  /**
   * Returns whether the {@link Format} contains {@linkplain SlowMotionData slow motion metadata}.
   */
  public static boolean containsSlowMotionData(Format format) {
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
}
