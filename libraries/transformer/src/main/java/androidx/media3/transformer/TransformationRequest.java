/*
 * Copyright 2020 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkArgument;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** A media transformation request. */
@UnstableApi
public final class TransformationRequest {

  /** A builder for {@link TransformationRequest} instances. */
  public static final class Builder {

    private int outputHeight;
    @Nullable private String audioMimeType;
    @Nullable private String videoMimeType;
    private @Composition.HdrMode int hdrMode;

    /**
     * Creates a new instance with default values.
     *
     * <p>Use {@link TransformationRequest#buildUpon()} to obtain a builder representing an existing
     * {@link TransformationRequest}.
     */
    public Builder() {
      outputHeight = C.LENGTH_UNSET;
    }

    private Builder(TransformationRequest transformationRequest) {
      this.outputHeight = transformationRequest.outputHeight;
      this.audioMimeType = transformationRequest.audioMimeType;
      this.videoMimeType = transformationRequest.videoMimeType;
      this.hdrMode = transformationRequest.hdrMode;
    }

    /**
     * Sets the video MIME type of the output.
     *
     * <p>The default value is {@code null} which corresponds to using the same MIME type as the
     * input. Supported MIME types are:
     *
     * <ul>
     *   <li>{@link MimeTypes#VIDEO_H263}
     *   <li>{@link MimeTypes#VIDEO_H264}
     *   <li>{@link MimeTypes#VIDEO_H265} from API level 24
     *   <li>{@link MimeTypes#VIDEO_MP4V}
     * </ul>
     *
     * @param videoMimeType The MIME type of the video samples in the output.
     * @return This builder.
     * @throws IllegalArgumentException If the {@code videoMimeType} is non-null but not a video
     *     {@linkplain MimeTypes MIME type}.
     */
    @CanIgnoreReturnValue
    public Builder setVideoMimeType(@Nullable String videoMimeType) {
      videoMimeType = MimeTypes.normalizeMimeType(videoMimeType);
      checkArgument(
          videoMimeType == null || MimeTypes.isVideo(videoMimeType),
          "Not a video MIME type: " + videoMimeType);
      this.videoMimeType = videoMimeType;
      return this;
    }

    /**
     * Sets the audio MIME type of the output.
     *
     * <p>The default value is {@code null} which corresponds to using the same MIME type as the
     * input. Supported MIME types are:
     *
     * <ul>
     *   <li>{@link MimeTypes#AUDIO_AAC}
     *   <li>{@link MimeTypes#AUDIO_AMR_NB}
     *   <li>{@link MimeTypes#AUDIO_AMR_WB}
     * </ul>
     *
     * @param audioMimeType The MIME type of the audio samples in the output.
     * @return This builder.
     * @throws IllegalArgumentException If the {@code audioMimeType} is non-null but not an audio
     *     {@linkplain MimeTypes MIME type}.
     */
    @CanIgnoreReturnValue
    public Builder setAudioMimeType(@Nullable String audioMimeType) {
      audioMimeType = MimeTypes.normalizeMimeType(audioMimeType);
      checkArgument(
          audioMimeType == null || MimeTypes.isAudio(audioMimeType),
          "Not an audio MIME type: " + audioMimeType);
      this.audioMimeType = audioMimeType;
      return this;
    }

    /**
     * Sets the {@link Composition.HdrMode} for HDR video input.
     *
     * <p>The default value is {@link Composition#HDR_MODE_KEEP_HDR}.
     *
     * @param hdrMode The {@link Composition.HdrMode} used.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setHdrMode(@Composition.HdrMode int hdrMode) {
      this.hdrMode = hdrMode;
      return this;
    }

    @CanIgnoreReturnValue
    // TODO(b/255953153): remove this method once fallback has been refactored.
    /* package */ Builder setResolution(int outputHeight) {
      this.outputHeight = outputHeight;
      return this;
    }

    /** Builds a {@link TransformationRequest} instance. */
    public TransformationRequest build() {
      return new TransformationRequest(outputHeight, audioMimeType, videoMimeType, hdrMode);
    }
  }

  /**
   * The requested height of the output video.
   *
   * <p>This field is
   *
   * <ul>
   *   <li>Always set to {@link C#LENGTH_UNSET} in the {@code originalTransformationRequest}
   *       parameter of {@link Transformer.Listener#onFallbackApplied(Composition,
   *       TransformationRequest, TransformationRequest)}.
   *   <li>Set to {@link C#LENGTH_UNSET} in the {@code fallbackTransformationRequest} parameter of
   *       {@link Transformer.Listener#onFallbackApplied(Composition, TransformationRequest,
   *       TransformationRequest)} to indicate that it is inferred from the input.
   * </ul>
   */
  public final int outputHeight;

  /**
   * The requested output audio sample {@linkplain MimeTypes MIME type}, or {@code null} if inferred
   * from the input.
   *
   * @see Builder#setAudioMimeType(String)
   */
  @Nullable public final String audioMimeType;

  /**
   * The requested output video sample {@linkplain MimeTypes MIME type}, or {@code null} if inferred
   * from the input.
   *
   * @see Builder#setVideoMimeType(String)
   */
  @Nullable public final String videoMimeType;

  /**
   * The {@link Composition.HdrMode} specifying how to handle HDR input video.
   *
   * @see Builder#setHdrMode(int)
   */
  public final @Composition.HdrMode int hdrMode;

  private TransformationRequest(
      int outputHeight,
      @Nullable String audioMimeType,
      @Nullable String videoMimeType,
      @Composition.HdrMode int hdrMode) {
    this.outputHeight = outputHeight;
    this.audioMimeType = audioMimeType;
    this.videoMimeType = videoMimeType;
    this.hdrMode = hdrMode;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TransformationRequest)) {
      return false;
    }
    TransformationRequest that = (TransformationRequest) o;
    return outputHeight == that.outputHeight
        && Util.areEqual(audioMimeType, that.audioMimeType)
        && Util.areEqual(videoMimeType, that.videoMimeType)
        && hdrMode == that.hdrMode;
  }

  @Override
  public int hashCode() {
    int result = outputHeight;
    result = 31 * result + (audioMimeType != null ? audioMimeType.hashCode() : 0);
    result = 31 * result + (videoMimeType != null ? videoMimeType.hashCode() : 0);
    result = 31 * result + hdrMode;
    return result;
  }

  @Override
  public String toString() {
    return "TransformationRequest{"
        + "outputHeight="
        + outputHeight
        + ", audioMimeType='"
        + audioMimeType
        + '\''
        + ", videoMimeType='"
        + videoMimeType
        + '\''
        + ", hdrMode="
        + hdrMode
        + '}';
  }

  /**
   * Returns a new {@link TransformationRequest.Builder} initialized with the values of this
   * instance.
   */
  public Builder buildUpon() {
    return new Builder(this);
  }
}
