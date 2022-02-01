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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.graphics.Matrix;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableSet;

/** A media transformation request. */
public final class TransformationRequest {

  /** A builder for {@link TransformationRequest} instances. */
  public static final class Builder {

    private static final ImmutableSet<Integer> SUPPORTED_OUTPUT_HEIGHTS =
        ImmutableSet.of(144, 240, 360, 480, 720, 1080, 1440, 2160);

    private Matrix transformationMatrix;
    private boolean flattenForSlowMotion;
    private int outputHeight;
    @Nullable private String audioMimeType;
    @Nullable private String videoMimeType;
    private boolean enableHdrEditing;

    /**
     * Creates a new instance with default values.
     *
     * <p>Use {@link TransformationRequest#buildUpon()} to obtain a builder representing an existing
     * {@link TransformationRequest}.
     */
    public Builder() {
      transformationMatrix = new Matrix();
      outputHeight = C.LENGTH_UNSET;
    }

    private Builder(TransformationRequest transformationRequest) {
      this.transformationMatrix = new Matrix(transformationRequest.transformationMatrix);
      this.flattenForSlowMotion = transformationRequest.flattenForSlowMotion;
      this.outputHeight = transformationRequest.outputHeight;
      this.audioMimeType = transformationRequest.audioMimeType;
      this.videoMimeType = transformationRequest.videoMimeType;
      this.enableHdrEditing = transformationRequest.enableHdrEditing;
    }

    /**
     * Sets the transformation matrix. The default value is to apply no change.
     *
     * <p>This can be used to perform operations supported by {@link Matrix}, like scaling and
     * rotating the video.
     *
     * <p>The video dimensions will be on the x axis, from -aspectRatio to aspectRatio, and on the y
     * axis, from -1 to 1.
     *
     * <p>For now, resolution will not be affected by this method.
     *
     * @param transformationMatrix The transformation to apply to video frames.
     * @return This builder.
     */
    public Builder setTransformationMatrix(Matrix transformationMatrix) {
      // TODO(b/201293185): After {@link #setResolution} supports arbitrary resolutions,
      // allow transformations to change the resolution, by scaling to the appropriate min/max
      // values. This will also be required to create the VertexTransformation class, in order to
      // have aspect ratio helper methods (which require resolution to change).

      // TODO(b/213198690): Consider changing how transformationMatrix is applied, so that
      // dimensions will be from -1 to 1 on both x and y axes, but transformations will be applied
      // in a predictable manner.
      this.transformationMatrix = new Matrix(transformationMatrix);
      return this;
    }

    /**
     * Sets whether the input should be flattened for media containing slow motion markers. The
     * transformed output is obtained by removing the slow motion metadata and by actually slowing
     * down the parts of the video and audio streams defined in this metadata. The default value for
     * {@code flattenForSlowMotion} is {@code false}.
     *
     * <p>Only Samsung Extension Format (SEF) slow motion metadata type is supported. The
     * transformation has no effect if the input does not contain this metadata type.
     *
     * <p>For SEF slow motion media, the following assumptions are made on the input:
     *
     * <ul>
     *   <li>The input container format is (unfragmented) MP4.
     *   <li>The input contains an AVC video elementary stream with temporal SVC.
     *   <li>The recording frame rate of the video is 120 or 240 fps.
     * </ul>
     *
     * <p>If specifying a {@link MediaSource.Factory} using {@link
     * Transformer.Builder#setMediaSourceFactory(MediaSource.Factory)}, make sure that {@link
     * Mp4Extractor#FLAG_READ_SEF_DATA} is set on the {@link Mp4Extractor} used. Otherwise, the slow
     * motion metadata will be ignored and the input won't be flattened.
     *
     * @param flattenForSlowMotion Whether to flatten for slow motion.
     * @return This builder.
     */
    public Builder setFlattenForSlowMotion(boolean flattenForSlowMotion) {
      this.flattenForSlowMotion = flattenForSlowMotion;
      return this;
    }

    /**
     * Sets the output resolution using the output height. The default value {@link C#LENGTH_UNSET}
     * corresponds to using the same height as the input. Output width will scale to preserve the
     * input video's aspect ratio.
     *
     * <p>For now, only "popular" heights like 144, 240, 360, 480, 720, 1080, 1440, or 2160 are
     * supported, to ensure compatibility on different devices.
     *
     * <p>For example, a 1920x1440 video can be scaled to 640x480 by calling setResolution(480).
     *
     * @param outputHeight The output height in pixels.
     * @return This builder.
     * @throws IllegalArgumentException If the {@code outputHeight} is not supported.
     */
    public Builder setResolution(int outputHeight) {
      // TODO(b/209781577): Define outputHeight in the javadoc as height can be ambiguous for videos
      // where rotationDegrees is set in the Format.
      // TODO(b/201293185): Restructure to input a Presentation class.
      // TODO(b/201293185): Check encoder codec capabilities in order to allow arbitrary
      // resolutions and reasonable fallbacks.
      checkArgument(
          outputHeight == C.LENGTH_UNSET || SUPPORTED_OUTPUT_HEIGHTS.contains(outputHeight),
          "Unsupported outputHeight: " + outputHeight);
      this.outputHeight = outputHeight;
      return this;
    }

    /**
     * Sets the video MIME type of the output. The default value is {@code null} which corresponds
     * to using the same MIME type as the input. Supported MIME types are:
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
     *     {@link MimeTypes MIME type}.
     */
    public Builder setVideoMimeType(@Nullable String videoMimeType) {
      checkArgument(
          videoMimeType == null || MimeTypes.isVideo(videoMimeType),
          "Not a video MIME type: " + videoMimeType);
      this.videoMimeType = videoMimeType;
      return this;
    }

    /**
     * Sets the audio MIME type of the output. The default value is {@code null} which corresponds
     * to using the same MIME type as the input. Supported MIME types are:
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
     *     {@link MimeTypes MIME type}.
     */
    public Builder setAudioMimeType(@Nullable String audioMimeType) {
      checkArgument(
          audioMimeType == null || MimeTypes.isAudio(audioMimeType),
          "Not an audio MIME type: " + audioMimeType);
      this.audioMimeType = audioMimeType;
      return this;
    }

    /**
     * Sets whether to attempt to process any input video stream as a high dynamic range (HDR)
     * signal.
     *
     * <p>This method is experimental, and will be renamed or removed in a future release. The HDR
     * editing feature is under development and is intended for developing/testing HDR processing
     * and encoding support.
     *
     * @param enableHdrEditing Whether to attempt to process any input video stream as a high
     *     dynamic range (HDR) signal.
     * @return This builder.
     */
    public Builder experimental_setEnableHdrEditing(boolean enableHdrEditing) {
      this.enableHdrEditing = enableHdrEditing;
      return this;
    }

    /** Builds a {@link TransformationRequest} instance. */
    public TransformationRequest build() {
      return new TransformationRequest(
          transformationMatrix,
          flattenForSlowMotion,
          outputHeight,
          audioMimeType,
          videoMimeType,
          enableHdrEditing);
    }
  }

  /**
   * A {@link Matrix transformation matrix} to apply to video frames.
   *
   * @see Builder#setTransformationMatrix(Matrix)
   */
  public final Matrix transformationMatrix;
  /**
   * Whether the input should be flattened for media containing slow motion markers.
   *
   * @see Builder#setFlattenForSlowMotion(boolean)
   */
  public final boolean flattenForSlowMotion;
  /**
   * The requested height of the output video, or {@link C#LENGTH_UNSET} if inferred from the input.
   *
   * @see Builder#setResolution(int)
   */
  public final int outputHeight;
  /**
   * The requested output audio sample {@link MimeTypes MIME type}, or {@code null} if inferred from
   * the input.
   *
   * @see Builder#setAudioMimeType(String)
   */
  @Nullable public final String audioMimeType;
  /**
   * The requested output video sample {@link MimeTypes MIME type}, or {@code null} if inferred from
   * the input.
   *
   * @see Builder#setVideoMimeType(String)
   */
  @Nullable public final String videoMimeType;
  /**
   * Whether to attempt to process any input video stream as a high dynamic range (HDR) signal.
   *
   * @see Builder#experimental_setEnableHdrEditing(boolean)
   */
  public final boolean enableHdrEditing;

  private TransformationRequest(
      Matrix transformationMatrix,
      boolean flattenForSlowMotion,
      int outputHeight,
      @Nullable String audioMimeType,
      @Nullable String videoMimeType,
      boolean enableHdrEditing) {
    this.transformationMatrix = transformationMatrix;
    this.flattenForSlowMotion = flattenForSlowMotion;
    this.outputHeight = outputHeight;
    this.audioMimeType = audioMimeType;
    this.videoMimeType = videoMimeType;
    this.enableHdrEditing = enableHdrEditing;
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
    return transformationMatrix.equals(that.transformationMatrix)
        && flattenForSlowMotion == that.flattenForSlowMotion
        && outputHeight == that.outputHeight
        && Util.areEqual(audioMimeType, that.audioMimeType)
        && Util.areEqual(videoMimeType, that.videoMimeType)
        && enableHdrEditing == that.enableHdrEditing;
  }

  @Override
  public int hashCode() {
    int result = transformationMatrix.hashCode();
    result = 31 * result + (flattenForSlowMotion ? 1 : 0);
    result = 31 * result + outputHeight;
    result = 31 * result + (audioMimeType != null ? audioMimeType.hashCode() : 0);
    result = 31 * result + (videoMimeType != null ? videoMimeType.hashCode() : 0);
    result = 31 * result + (enableHdrEditing ? 1 : 0);
    return result;
  }

  /**
   * Returns a new {@link TransformationRequest.Builder} initialized with the values of this
   * instance.
   */
  public Builder buildUpon() {
    return new Builder(this);
  }
}
