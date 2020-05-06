/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents a media format.
 *
 * <p>When building formats, populate all fields whose values are known and relevant to the type of
 * format being constructed. For information about different types of format, see ExoPlayer's <a
 * href="https://exoplayer.dev/supported-formats.html">Supported formats page</a>.
 *
 * <h3>Fields commonly relevant to all formats</h3>
 *
 * <ul>
 *   <li>{@link #id}
 *   <li>{@link #label}
 *   <li>{@link #language}
 *   <li>{@link #selectionFlags}
 *   <li>{@link #roleFlags}
 *   <li>{@link #averageBitrate}
 *   <li>{@link #peakBitrate}
 *   <li>{@link #codecs}
 *   <li>{@link #metadata}
 * </ul>
 *
 * <h3 id="container-formats">Fields relevant to container formats</h3>
 *
 * <ul>
 *   <li>{@link #containerMimeType}
 *   <li>If the container only contains a single media track, <a href="#sample-formats">fields
 *       relevant to sample formats</a> can are also be relevant and can be set to describe the
 *       sample format of that track.
 *   <li>If the container only contains one track of a given type (possibly alongside tracks of
 *       other types), then fields relevant to that track type can be set to describe the properties
 *       of the track. See the sections below for <a href="#video-formats">video</a>, <a
 *       href="#audio-formats">audio</a> and <a href="#text-formats">text</a> formats.
 * </ul>
 *
 * <h3 id="sample-formats">Fields relevant to sample formats</h3>
 *
 * <ul>
 *   <li>{@link #sampleMimeType}
 *   <li>{@link #maxInputSize}
 *   <li>{@link #initializationData}
 *   <li>{@link #drmInitData}
 *   <li>{@link #subsampleOffsetUs}
 *   <li>Fields relevant to the sample format's track type are also relevant. See the sections below
 *       for <a href="#video-formats">video</a>, <a href="#audio-formats">audio</a> and <a
 *       href="#text-formats">text</a> formats.
 * </ul>
 *
 * <h3 id="video-formats">Fields relevant to video formats</h3>
 *
 * <ul>
 *   <li>{@link #width}
 *   <li>{@link #height}
 *   <li>{@link #frameRate}
 *   <li>{@link #rotationDegrees}
 *   <li>{@link #pixelWidthHeightRatio}
 *   <li>{@link #projectionData}
 *   <li>{@link #stereoMode}
 *   <li>{@link #colorInfo}
 * </ul>
 *
 * <h3 id="audio-formats">Fields relevant to audio formats</h3>
 *
 * <ul>
 *   <li>{@link #channelCount}
 *   <li>{@link #sampleRate}
 *   <li>{@link #pcmEncoding}
 *   <li>{@link #encoderDelay}
 *   <li>{@link #encoderPadding}
 * </ul>
 *
 * <h3 id="text-formats">Fields relevant to text formats</h3>
 *
 * <ul>
 *   <li>{@link #accessibilityChannel}
 * </ul>
 */
public final class Format implements Parcelable {

  /**
   * Builds {@link Format} instances.
   *
   * <p>Use Format#buildUpon() to obtain a builder representing an existing {@link Format}.
   *
   * <p>When building formats, populate all fields whose values are known and relevant to the type
   * of format being constructed. See the {@link Format} Javadoc for information about which fields
   * should be set for different types of format.
   */
  public static final class Builder {

    @Nullable private String id;
    @Nullable private String label;
    @Nullable private String language;
    @C.SelectionFlags private int selectionFlags;
    @C.RoleFlags private int roleFlags;
    private int averageBitrate;
    private int peakBitrate;
    @Nullable private String codecs;
    @Nullable private Metadata metadata;

    // Container specific.

    @Nullable private String containerMimeType;

    // Sample specific.

    @Nullable private String sampleMimeType;
    private int maxInputSize;
    @Nullable private List<byte[]> initializationData;
    @Nullable private DrmInitData drmInitData;
    private long subsampleOffsetUs;

    // Video specific.

    private int width;
    private int height;
    private float frameRate;
    private int rotationDegrees;
    private float pixelWidthHeightRatio;
    @Nullable private byte[] projectionData;
    @C.StereoMode private int stereoMode;
    @Nullable private ColorInfo colorInfo;

    // Audio specific.

    private int channelCount;
    private int sampleRate;
    @C.PcmEncoding private int pcmEncoding;
    private int encoderDelay;
    private int encoderPadding;

    // Text specific.

    private int accessibilityChannel;

    // Provided by source.

    @Nullable private Class<? extends ExoMediaCrypto> exoMediaCryptoType;

    /** Creates a new instance with default values. */
    public Builder() {
      averageBitrate = NO_VALUE;
      peakBitrate = NO_VALUE;
      // Sample specific.
      maxInputSize = NO_VALUE;
      subsampleOffsetUs = OFFSET_SAMPLE_RELATIVE;
      // Video specific.
      width = NO_VALUE;
      height = NO_VALUE;
      frameRate = NO_VALUE;
      pixelWidthHeightRatio = 1.0f;
      stereoMode = NO_VALUE;
      // Audio specific.
      channelCount = NO_VALUE;
      sampleRate = NO_VALUE;
      pcmEncoding = NO_VALUE;
      // Text specific.
      accessibilityChannel = NO_VALUE;
    }

    /**
     * Creates a new instance to build upon the provided {@link Format}.
     *
     * @param format The {@link Format} to build upon.
     */
    private Builder(Format format) {
      this.id = format.id;
      this.label = format.label;
      this.language = format.language;
      this.selectionFlags = format.selectionFlags;
      this.roleFlags = format.roleFlags;
      this.averageBitrate = format.averageBitrate;
      this.peakBitrate = format.peakBitrate;
      this.codecs = format.codecs;
      this.metadata = format.metadata;
      // Container specific.
      this.containerMimeType = format.containerMimeType;
      // Sample specific.
      this.sampleMimeType = format.sampleMimeType;
      this.maxInputSize = format.maxInputSize;
      this.initializationData = format.initializationData;
      this.drmInitData = format.drmInitData;
      this.subsampleOffsetUs = format.subsampleOffsetUs;
      // Video specific.
      this.width = format.width;
      this.height = format.height;
      this.frameRate = format.frameRate;
      this.rotationDegrees = format.rotationDegrees;
      this.pixelWidthHeightRatio = format.pixelWidthHeightRatio;
      this.projectionData = format.projectionData;
      this.stereoMode = format.stereoMode;
      this.colorInfo = format.colorInfo;
      // Audio specific.
      this.channelCount = format.channelCount;
      this.sampleRate = format.sampleRate;
      this.pcmEncoding = format.pcmEncoding;
      this.encoderDelay = format.encoderDelay;
      this.encoderPadding = format.encoderPadding;
      // Text specific.
      this.accessibilityChannel = format.accessibilityChannel;
      // Provided by source.
      this.exoMediaCryptoType = format.exoMediaCryptoType;
    }

    /**
     * Sets {@link Format#id}. The default value is {@code null}.
     *
     * @param id The {@link Format#id}.
     * @return The builder.
     */
    public Builder setId(@Nullable String id) {
      this.id = id;
      return this;
    }

    /**
     * Sets {@link Format#id} to {@link Integer#toString() Integer.toString(id)}. The default value
     * is {@code null}.
     *
     * @param id The {@link Format#id}.
     * @return The builder.
     */
    public Builder setId(int id) {
      this.id = Integer.toString(id);
      return this;
    }

    /**
     * Sets {@link Format#label}. The default value is {@code null}.
     *
     * @param label The {@link Format#label}.
     * @return The builder.
     */
    public Builder setLabel(@Nullable String label) {
      this.label = label;
      return this;
    }

    /**
     * Sets {@link Format#language}. The default value is {@code null}.
     *
     * @param language The {@link Format#language}.
     * @return The builder.
     */
    public Builder setLanguage(@Nullable String language) {
      this.language = language;
      return this;
    }

    /**
     * Sets {@link Format#selectionFlags}. The default value is 0.
     *
     * @param selectionFlags The {@link Format#selectionFlags}.
     * @return The builder.
     */
    public Builder setSelectionFlags(@C.SelectionFlags int selectionFlags) {
      this.selectionFlags = selectionFlags;
      return this;
    }

    /**
     * Sets {@link Format#roleFlags}. The default value is 0.
     *
     * @param roleFlags The {@link Format#roleFlags}.
     * @return The builder.
     */
    public Builder setRoleFlags(@C.RoleFlags int roleFlags) {
      this.roleFlags = roleFlags;
      return this;
    }

    /**
     * Sets {@link Format#averageBitrate}. The default value is {@link #NO_VALUE}.
     *
     * @param averageBitrate The {@link Format#averageBitrate}.
     * @return The builder.
     */
    public Builder setAverageBitrate(int averageBitrate) {
      this.averageBitrate = averageBitrate;
      return this;
    }

    /**
     * Sets {@link Format#peakBitrate}. The default value is {@link #NO_VALUE}.
     *
     * @param peakBitrate The {@link Format#peakBitrate}.
     * @return The builder.
     */
    public Builder setPeakBitrate(int peakBitrate) {
      this.peakBitrate = peakBitrate;
      return this;
    }

    /**
     * Sets {@link Format#codecs}. The default value is {@code null}.
     *
     * @param codecs The {@link Format#codecs}.
     * @return The builder.
     */
    public Builder setCodecs(@Nullable String codecs) {
      this.codecs = codecs;
      return this;
    }

    /**
     * Sets {@link Format#metadata}. The default value is {@code null}.
     *
     * @param metadata The {@link Format#metadata}.
     * @return The builder.
     */
    public Builder setMetadata(@Nullable Metadata metadata) {
      this.metadata = metadata;
      return this;
    }

    // Container specific.

    /**
     * Sets {@link Format#containerMimeType}. The default value is {@code null}.
     *
     * @param containerMimeType The {@link Format#containerMimeType}.
     * @return The builder.
     */
    public Builder setContainerMimeType(@Nullable String containerMimeType) {
      this.containerMimeType = containerMimeType;
      return this;
    }

    // Sample specific.

    /**
     * Sets {@link Format#sampleMimeType}. The default value is {@code null}.
     *
     * @param sampleMimeType {@link Format#sampleMimeType}.
     * @return The builder.
     */
    public Builder setSampleMimeType(@Nullable String sampleMimeType) {
      this.sampleMimeType = sampleMimeType;
      return this;
    }

    /**
     * Sets {@link Format#maxInputSize}. The default value is {@link #NO_VALUE}.
     *
     * @param maxInputSize The {@link Format#maxInputSize}.
     * @return The builder.
     */
    public Builder setMaxInputSize(int maxInputSize) {
      this.maxInputSize = maxInputSize;
      return this;
    }

    /**
     * Sets {@link Format#initializationData}. The default value is {@code null}.
     *
     * @param initializationData The {@link Format#initializationData}.
     * @return The builder.
     */
    public Builder setInitializationData(@Nullable List<byte[]> initializationData) {
      this.initializationData = initializationData;
      return this;
    }

    /**
     * Sets {@link Format#drmInitData}. The default value is {@code null}.
     *
     * @param drmInitData The {@link Format#drmInitData}.
     * @return The builder.
     */
    public Builder setDrmInitData(@Nullable DrmInitData drmInitData) {
      this.drmInitData = drmInitData;
      return this;
    }

    /**
     * Sets {@link Format#subsampleOffsetUs}. The default value is {@link #OFFSET_SAMPLE_RELATIVE}.
     *
     * @param subsampleOffsetUs The {@link Format#subsampleOffsetUs}.
     * @return The builder.
     */
    public Builder setSubsampleOffsetUs(long subsampleOffsetUs) {
      this.subsampleOffsetUs = subsampleOffsetUs;
      return this;
    }

    // Video specific.

    /**
     * Sets {@link Format#width}. The default value is {@link #NO_VALUE}.
     *
     * @param width The {@link Format#width}.
     * @return The builder.
     */
    public Builder setWidth(int width) {
      this.width = width;
      return this;
    }

    /**
     * Sets {@link Format#height}. The default value is {@link #NO_VALUE}.
     *
     * @param height The {@link Format#height}.
     * @return The builder.
     */
    public Builder setHeight(int height) {
      this.height = height;
      return this;
    }

    /**
     * Sets {@link Format#frameRate}. The default value is {@link #NO_VALUE}.
     *
     * @param frameRate The {@link Format#frameRate}.
     * @return The builder.
     */
    public Builder setFrameRate(float frameRate) {
      this.frameRate = frameRate;
      return this;
    }

    /**
     * Sets {@link Format#rotationDegrees}. The default value is 0.
     *
     * @param rotationDegrees The {@link Format#rotationDegrees}.
     * @return The builder.
     */
    public Builder setRotationDegrees(int rotationDegrees) {
      this.rotationDegrees = rotationDegrees;
      return this;
    }

    /**
     * Sets {@link Format#pixelWidthHeightRatio}. The default value is 1.0f.
     *
     * @param pixelWidthHeightRatio The {@link Format#pixelWidthHeightRatio}.
     * @return The builder.
     */
    public Builder setPixelWidthHeightRatio(float pixelWidthHeightRatio) {
      this.pixelWidthHeightRatio = pixelWidthHeightRatio;
      return this;
    }

    /**
     * Sets {@link Format#projectionData}. The default value is {@code null}.
     *
     * @param projectionData The {@link Format#projectionData}.
     * @return The builder.
     */
    public Builder setProjectionData(@Nullable byte[] projectionData) {
      this.projectionData = projectionData;
      return this;
    }

    /**
     * Sets {@link Format#stereoMode}. The default value is {@link #NO_VALUE}.
     *
     * @param stereoMode The {@link Format#stereoMode}.
     * @return The builder.
     */
    public Builder setStereoMode(@C.StereoMode int stereoMode) {
      this.stereoMode = stereoMode;
      return this;
    }

    /**
     * Sets {@link Format#colorInfo}. The default value is {@code null}.
     *
     * @param colorInfo The {@link Format#colorInfo}.
     * @return The builder.
     */
    public Builder setColorInfo(@Nullable ColorInfo colorInfo) {
      this.colorInfo = colorInfo;
      return this;
    }

    // Audio specific.

    /**
     * Sets {@link Format#channelCount}. The default value is {@link #NO_VALUE}.
     *
     * @param channelCount The {@link Format#channelCount}.
     * @return The builder.
     */
    public Builder setChannelCount(int channelCount) {
      this.channelCount = channelCount;
      return this;
    }

    /**
     * Sets {@link Format#sampleRate}. The default value is {@link #NO_VALUE}.
     *
     * @param sampleRate The {@link Format#sampleRate}.
     * @return The builder.
     */
    public Builder setSampleRate(int sampleRate) {
      this.sampleRate = sampleRate;
      return this;
    }

    /**
     * Sets {@link Format#pcmEncoding}. The default value is {@link #NO_VALUE}.
     *
     * @param pcmEncoding The {@link Format#pcmEncoding}.
     * @return The builder.
     */
    public Builder setPcmEncoding(@C.PcmEncoding int pcmEncoding) {
      this.pcmEncoding = pcmEncoding;
      return this;
    }

    /**
     * Sets {@link Format#encoderDelay}. The default value is 0.
     *
     * @param encoderDelay The {@link Format#encoderDelay}.
     * @return The builder.
     */
    public Builder setEncoderDelay(int encoderDelay) {
      this.encoderDelay = encoderDelay;
      return this;
    }

    /**
     * Sets {@link Format#encoderPadding}. The default value is 0.
     *
     * @param encoderPadding The {@link Format#encoderPadding}.
     * @return The builder.
     */
    public Builder setEncoderPadding(int encoderPadding) {
      this.encoderPadding = encoderPadding;
      return this;
    }

    // Text specific.

    /**
     * Sets {@link Format#accessibilityChannel}. The default value is {@link #NO_VALUE}.
     *
     * @param accessibilityChannel The {@link Format#accessibilityChannel}.
     * @return The builder.
     */
    public Builder setAccessibilityChannel(int accessibilityChannel) {
      this.accessibilityChannel = accessibilityChannel;
      return this;
    }

    // Provided by source.

    /**
     * Sets {@link Format#exoMediaCryptoType}. The default value is {@code null}.
     *
     * @param exoMediaCryptoType The {@link Format#exoMediaCryptoType}.
     * @return The builder.
     */
    public Builder setExoMediaCryptoType(
        @Nullable Class<? extends ExoMediaCrypto> exoMediaCryptoType) {
      this.exoMediaCryptoType = exoMediaCryptoType;
      return this;
    }

    // Build.

    public Format build() {
      return new Format(
          id,
          label,
          language,
          selectionFlags,
          roleFlags,
          averageBitrate,
          peakBitrate,
          codecs,
          metadata,
          containerMimeType,
          sampleMimeType,
          maxInputSize,
          initializationData,
          drmInitData,
          subsampleOffsetUs,
          width,
          height,
          frameRate,
          rotationDegrees,
          pixelWidthHeightRatio,
          projectionData,
          stereoMode,
          colorInfo,
          channelCount,
          sampleRate,
          pcmEncoding,
          encoderDelay,
          encoderPadding,
          accessibilityChannel,
          exoMediaCryptoType);
    }
  }

  /** A value for various fields to indicate that the field's value is unknown or not applicable. */
  public static final int NO_VALUE = -1;

  /**
   * A value for {@link #subsampleOffsetUs} to indicate that subsample timestamps are relative to
   * the timestamps of their parent samples.
   */
  public static final long OFFSET_SAMPLE_RELATIVE = Long.MAX_VALUE;

  /** An identifier for the format, or null if unknown or not applicable. */
  @Nullable public final String id;
  /** The human readable label, or null if unknown or not applicable. */
  @Nullable public final String label;
  /** The language as an IETF BCP 47 conformant tag, or null if unknown or not applicable. */
  @Nullable public final String language;
  /** Track selection flags. */
  @C.SelectionFlags public final int selectionFlags;
  /** Track role flags. */
  @C.RoleFlags public final int roleFlags;
  /**
   * The average bitrate in bits per second, or {@link #NO_VALUE} if unknown or not applicable. The
   * way in which this field is populated depends on the type of media to which the format
   * corresponds:
   *
   * <ul>
   *   <li>DASH representations: Always {@link Format#NO_VALUE}.
   *   <li>HLS variants: The {@code AVERAGE-BANDWIDTH} attribute defined on the corresponding {@code
   *       EXT-X-STREAM-INF} tag in the master playlist, or {@link Format#NO_VALUE} if not present.
   *   <li>SmoothStreaming track elements: The {@code Bitrate} attribute defined on the
   *       corresponding {@code TrackElement} in the manifest, or {@link Format#NO_VALUE} if not
   *       present.
   *   <li>Progressive container formats: Often {@link Format#NO_VALUE}, but may be populated with
   *       the average bitrate of the container if known.
   *   <li>Sample formats: Often {@link Format#NO_VALUE}, but may be populated with the average
   *       bitrate of the stream of samples with type {@link #sampleMimeType} if known. Note that if
   *       {@link #sampleMimeType} is a compressed format (e.g., {@link MimeTypes#AUDIO_AAC}), then
   *       this bitrate is for the stream of still compressed samples.
   * </ul>
   */
  public final int averageBitrate;
  /**
   * The peak bitrate in bits per second, or {@link #NO_VALUE} if unknown or not applicable. The way
   * in which this field is populated depends on the type of media to which the format corresponds:
   *
   * <ul>
   *   <li>DASH representations: The {@code @bandwidth} attribute of the corresponding {@code
   *       Representation} element in the manifest.
   *   <li>HLS variants: The {@code BANDWIDTH} attribute defined on the corresponding {@code
   *       EXT-X-STREAM-INF} tag.
   *   <li>SmoothStreaming track elements: Always {@link Format#NO_VALUE}.
   *   <li>Progressive container formats: Often {@link Format#NO_VALUE}, but may be populated with
   *       the peak bitrate of the container if known.
   *   <li>Sample formats: Often {@link Format#NO_VALUE}, but may be populated with the peak bitrate
   *       of the stream of samples with type {@link #sampleMimeType} if known. Note that if {@link
   *       #sampleMimeType} is a compressed format (e.g., {@link MimeTypes#AUDIO_AAC}), then this
   *       bitrate is for the stream of still compressed samples.
   * </ul>
   */
  public final int peakBitrate;
  /**
   * The bitrate in bits per second. This is the peak bitrate if known, or else the average bitrate
   * if known, or else {@link Format#NO_VALUE}. Equivalent to: {@code peakBitrate != NO_VALUE ?
   * peakBitrate : averageBitrate}.
   */
  public final int bitrate;
  /** Codecs of the format as described in RFC 6381, or null if unknown or not applicable. */
  @Nullable public final String codecs;
  /** Metadata, or null if unknown or not applicable. */
  @Nullable public final Metadata metadata;

  // Container specific.

  /** The mime type of the container, or null if unknown or not applicable. */
  @Nullable public final String containerMimeType;

  // Sample specific.

  /** The sample mime type, or null if unknown or not applicable. */
  @Nullable public final String sampleMimeType;
  /**
   * The maximum size of a buffer of data (typically one sample), or {@link #NO_VALUE} if unknown or
   * not applicable.
   */
  public final int maxInputSize;
  /**
   * Initialization data that must be provided to the decoder. Will not be null, but may be empty
   * if initialization data is not required.
   */
  public final List<byte[]> initializationData;
  /** DRM initialization data if the stream is protected, or null otherwise. */
  @Nullable public final DrmInitData drmInitData;

  /**
   * For samples that contain subsamples, this is an offset that should be added to subsample
   * timestamps. A value of {@link #OFFSET_SAMPLE_RELATIVE} indicates that subsample timestamps are
   * relative to the timestamps of their parent samples.
   */
  public final long subsampleOffsetUs;

  // Video specific.

  /**
   * The width of the video in pixels, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int width;
  /**
   * The height of the video in pixels, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int height;
  /**
   * The frame rate in frames per second, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final float frameRate;
  /**
   * The clockwise rotation that should be applied to the video for it to be rendered in the correct
   * orientation, or 0 if unknown or not applicable. Only 0, 90, 180 and 270 are supported.
   */
  public final int rotationDegrees;
  /** The width to height ratio of pixels in the video, or 1.0 if unknown or not applicable. */
  public final float pixelWidthHeightRatio;
  /** The projection data for 360/VR video, or null if not applicable. */
  @Nullable public final byte[] projectionData;
  /**
   * The stereo layout for 360/3D/VR video, or {@link #NO_VALUE} if not applicable. Valid stereo
   * modes are {@link C#STEREO_MODE_MONO}, {@link C#STEREO_MODE_TOP_BOTTOM}, {@link
   * C#STEREO_MODE_LEFT_RIGHT}, {@link C#STEREO_MODE_STEREO_MESH}.
   */
  @C.StereoMode public final int stereoMode;
  /** The color metadata associated with the video, or null if not applicable. */
  @Nullable public final ColorInfo colorInfo;

  // Audio specific.

  /**
   * The number of audio channels, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int channelCount;
  /**
   * The audio sampling rate in Hz, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int sampleRate;
  /** The {@link C.PcmEncoding} for PCM audio. Set to {@link #NO_VALUE} for other media types. */
  @C.PcmEncoding public final int pcmEncoding;
  /**
   * The number of frames to trim from the start of the decoded audio stream, or 0 if not
   * applicable.
   */
  public final int encoderDelay;
  /**
   * The number of frames to trim from the end of the decoded audio stream, or 0 if not applicable.
   */
  public final int encoderPadding;

  // Text specific.

  /** The Accessibility channel, or {@link #NO_VALUE} if not known or applicable. */
  public final int accessibilityChannel;

  // Provided by source.

  /**
   * The type of the {@link ExoMediaCrypto} provided by the media source, if the media source can
   * acquire a DRM session for {@link #drmInitData}. Null if the media source cannot acquire a
   * session for {@link #drmInitData}, or if not applicable.
   */
  @Nullable public final Class<? extends ExoMediaCrypto> exoMediaCryptoType;

  // Lazily initialized hashcode.
  private int hashCode;

  // Video.

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createVideoContainerFormat(
      @Nullable String id,
      @Nullable String label,
      @Nullable String containerMimeType,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      @Nullable Metadata metadata,
      int bitrate,
      int width,
      int height,
      float frameRate,
      @Nullable List<byte[]> initializationData,
      @C.SelectionFlags int selectionFlags,
      @C.RoleFlags int roleFlags) {
    return new Builder()
        .setId(id)
        .setLabel(label)
        .setSelectionFlags(selectionFlags)
        .setRoleFlags(roleFlags)
        .setAverageBitrate(bitrate)
        .setPeakBitrate(bitrate)
        .setCodecs(codecs)
        .setMetadata(metadata)
        .setContainerMimeType(containerMimeType)
        .setSampleMimeType(sampleMimeType)
        .setInitializationData(initializationData)
        .setWidth(width)
        .setHeight(height)
        .setFrameRate(frameRate)
        .build();
  }

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createVideoSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      int maxInputSize,
      int width,
      int height,
      float frameRate,
      @Nullable List<byte[]> initializationData,
      @Nullable DrmInitData drmInitData) {
    return new Builder()
        .setId(id)
        .setAverageBitrate(bitrate)
        .setPeakBitrate(bitrate)
        .setCodecs(codecs)
        .setSampleMimeType(sampleMimeType)
        .setMaxInputSize(maxInputSize)
        .setInitializationData(initializationData)
        .setDrmInitData(drmInitData)
        .setWidth(width)
        .setHeight(height)
        .setFrameRate(frameRate)
        .build();
  }

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createVideoSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      int maxInputSize,
      int width,
      int height,
      float frameRate,
      @Nullable List<byte[]> initializationData,
      int rotationDegrees,
      float pixelWidthHeightRatio,
      @Nullable DrmInitData drmInitData) {
    return new Builder()
        .setId(id)
        .setAverageBitrate(bitrate)
        .setPeakBitrate(bitrate)
        .setCodecs(codecs)
        .setSampleMimeType(sampleMimeType)
        .setMaxInputSize(maxInputSize)
        .setInitializationData(initializationData)
        .setDrmInitData(drmInitData)
        .setWidth(width)
        .setHeight(height)
        .setFrameRate(frameRate)
        .setRotationDegrees(rotationDegrees)
        .setPixelWidthHeightRatio(pixelWidthHeightRatio)
        .build();
  }

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createVideoSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      int maxInputSize,
      int width,
      int height,
      float frameRate,
      @Nullable List<byte[]> initializationData,
      int rotationDegrees,
      float pixelWidthHeightRatio,
      @Nullable byte[] projectionData,
      @C.StereoMode int stereoMode,
      @Nullable ColorInfo colorInfo,
      @Nullable DrmInitData drmInitData) {
    return new Builder()
        .setId(id)
        .setAverageBitrate(bitrate)
        .setPeakBitrate(bitrate)
        .setCodecs(codecs)
        .setSampleMimeType(sampleMimeType)
        .setMaxInputSize(maxInputSize)
        .setInitializationData(initializationData)
        .setDrmInitData(drmInitData)
        .setWidth(width)
        .setHeight(height)
        .setFrameRate(frameRate)
        .setRotationDegrees(rotationDegrees)
        .setPixelWidthHeightRatio(pixelWidthHeightRatio)
        .setProjectionData(projectionData)
        .setStereoMode(stereoMode)
        .setColorInfo(colorInfo)
        .build();
  }

  // Audio.

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createAudioContainerFormat(
      @Nullable String id,
      @Nullable String label,
      @Nullable String containerMimeType,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      @Nullable Metadata metadata,
      int bitrate,
      int channelCount,
      int sampleRate,
      @Nullable List<byte[]> initializationData,
      @C.SelectionFlags int selectionFlags,
      @C.RoleFlags int roleFlags,
      @Nullable String language) {
    return new Builder()
        .setId(id)
        .setLabel(label)
        .setLanguage(language)
        .setSelectionFlags(selectionFlags)
        .setRoleFlags(roleFlags)
        .setAverageBitrate(bitrate)
        .setPeakBitrate(bitrate)
        .setCodecs(codecs)
        .setMetadata(metadata)
        .setContainerMimeType(containerMimeType)
        .setSampleMimeType(sampleMimeType)
        .setInitializationData(initializationData)
        .setChannelCount(channelCount)
        .setSampleRate(sampleRate)
        .build();
  }

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createAudioSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      int maxInputSize,
      int channelCount,
      int sampleRate,
      @Nullable List<byte[]> initializationData,
      @Nullable DrmInitData drmInitData,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language) {
    return new Builder()
        .setId(id)
        .setLanguage(language)
        .setSelectionFlags(selectionFlags)
        .setAverageBitrate(bitrate)
        .setPeakBitrate(bitrate)
        .setCodecs(codecs)
        .setSampleMimeType(sampleMimeType)
        .setMaxInputSize(maxInputSize)
        .setInitializationData(initializationData)
        .setDrmInitData(drmInitData)
        .setChannelCount(channelCount)
        .setSampleRate(sampleRate)
        .build();
  }

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createAudioSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      int maxInputSize,
      int channelCount,
      int sampleRate,
      @C.PcmEncoding int pcmEncoding,
      @Nullable List<byte[]> initializationData,
      @Nullable DrmInitData drmInitData,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language) {
    return new Builder()
        .setId(id)
        .setLanguage(language)
        .setSelectionFlags(selectionFlags)
        .setAverageBitrate(bitrate)
        .setPeakBitrate(bitrate)
        .setCodecs(codecs)
        .setSampleMimeType(sampleMimeType)
        .setMaxInputSize(maxInputSize)
        .setInitializationData(initializationData)
        .setDrmInitData(drmInitData)
        .setChannelCount(channelCount)
        .setSampleRate(sampleRate)
        .setPcmEncoding(pcmEncoding)
        .build();
  }

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createAudioSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      int maxInputSize,
      int channelCount,
      int sampleRate,
      @C.PcmEncoding int pcmEncoding,
      int encoderDelay,
      int encoderPadding,
      @Nullable List<byte[]> initializationData,
      @Nullable DrmInitData drmInitData,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language,
      @Nullable Metadata metadata) {
    return new Builder()
        .setId(id)
        .setLanguage(language)
        .setSelectionFlags(selectionFlags)
        .setAverageBitrate(bitrate)
        .setPeakBitrate(bitrate)
        .setCodecs(codecs)
        .setMetadata(metadata)
        .setSampleMimeType(sampleMimeType)
        .setMaxInputSize(maxInputSize)
        .setInitializationData(initializationData)
        .setDrmInitData(drmInitData)
        .setChannelCount(channelCount)
        .setSampleRate(sampleRate)
        .setPcmEncoding(pcmEncoding)
        .setEncoderDelay(encoderDelay)
        .setEncoderPadding(encoderPadding)
        .build();
  }

  // Text.

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createTextContainerFormat(
      @Nullable String id,
      @Nullable String label,
      @Nullable String containerMimeType,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      @C.SelectionFlags int selectionFlags,
      @C.RoleFlags int roleFlags,
      @Nullable String language) {
    return new Builder()
        .setId(id)
        .setLabel(label)
        .setLanguage(language)
        .setSelectionFlags(selectionFlags)
        .setRoleFlags(roleFlags)
        .setAverageBitrate(bitrate)
        .setPeakBitrate(bitrate)
        .setCodecs(codecs)
        .setContainerMimeType(containerMimeType)
        .setSampleMimeType(sampleMimeType)
        .build();
  }

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createTextContainerFormat(
      @Nullable String id,
      @Nullable String label,
      @Nullable String containerMimeType,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      @C.SelectionFlags int selectionFlags,
      @C.RoleFlags int roleFlags,
      @Nullable String language,
      int accessibilityChannel) {
    return new Builder()
        .setId(id)
        .setLabel(label)
        .setLanguage(language)
        .setSelectionFlags(selectionFlags)
        .setRoleFlags(roleFlags)
        .setAverageBitrate(bitrate)
        .setPeakBitrate(bitrate)
        .setCodecs(codecs)
        .setContainerMimeType(containerMimeType)
        .setSampleMimeType(sampleMimeType)
        .setAccessibilityChannel(accessibilityChannel)
        .build();
  }

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createTextSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language) {
    return new Builder()
        .setId(id)
        .setLanguage(language)
        .setSelectionFlags(selectionFlags)
        .setSampleMimeType(sampleMimeType)
        .build();
  }

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createTextSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language,
      int accessibilityChannel,
      long subsampleOffsetUs,
      @Nullable List<byte[]> initializationData) {
    return new Builder()
        .setId(id)
        .setLanguage(language)
        .setSelectionFlags(selectionFlags)
        .setSampleMimeType(sampleMimeType)
        .setInitializationData(initializationData)
        .setSubsampleOffsetUs(subsampleOffsetUs)
        .setAccessibilityChannel(accessibilityChannel)
        .build();
  }

  // Image.

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createImageSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @C.SelectionFlags int selectionFlags,
      @Nullable List<byte[]> initializationData,
      @Nullable String language) {
    return new Builder()
        .setId(id)
        .setLanguage(language)
        .setSelectionFlags(selectionFlags)
        .setSampleMimeType(sampleMimeType)
        .setInitializationData(initializationData)
        .build();
  }

  // Generic.

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createContainerFormat(
      @Nullable String id,
      @Nullable String label,
      @Nullable String containerMimeType,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      @C.SelectionFlags int selectionFlags,
      @C.RoleFlags int roleFlags,
      @Nullable String language) {
    return new Builder()
        .setId(id)
        .setLabel(label)
        .setLanguage(language)
        .setSelectionFlags(selectionFlags)
        .setRoleFlags(roleFlags)
        .setAverageBitrate(bitrate)
        .setPeakBitrate(bitrate)
        .setCodecs(codecs)
        .setContainerMimeType(containerMimeType)
        .setSampleMimeType(sampleMimeType)
        .build();
  }

  /** @deprecated Use {@link Format.Builder}. */
  @Deprecated
  public static Format createSampleFormat(@Nullable String id, @Nullable String sampleMimeType) {
    return new Builder().setId(id).setSampleMimeType(sampleMimeType).build();
  }

  /* package */ Format(
      @Nullable String id,
      @Nullable String label,
      @Nullable String language,
      @C.SelectionFlags int selectionFlags,
      @C.RoleFlags int roleFlags,
      int averageBitrate,
      int peakBitrate,
      @Nullable String codecs,
      @Nullable Metadata metadata,
      // Container specific.
      @Nullable String containerMimeType,
      // Sample specific.
      @Nullable String sampleMimeType,
      int maxInputSize,
      @Nullable List<byte[]> initializationData,
      @Nullable DrmInitData drmInitData,
      long subsampleOffsetUs,
      // Video specific.
      int width,
      int height,
      float frameRate,
      int rotationDegrees,
      float pixelWidthHeightRatio,
      @Nullable byte[] projectionData,
      @C.StereoMode int stereoMode,
      @Nullable ColorInfo colorInfo,
      // Audio specific.
      int channelCount,
      int sampleRate,
      @C.PcmEncoding int pcmEncoding,
      int encoderDelay,
      int encoderPadding,
      // Text specific.
      int accessibilityChannel,
      // Provided by source.
      @Nullable Class<? extends ExoMediaCrypto> exoMediaCryptoType) {
    this.id = id;
    this.label = label;
    this.language = Util.normalizeLanguageCode(language);
    this.selectionFlags = selectionFlags;
    this.roleFlags = roleFlags;
    this.averageBitrate = averageBitrate;
    this.peakBitrate = peakBitrate;
    this.bitrate = peakBitrate != NO_VALUE ? peakBitrate : averageBitrate;
    this.codecs = codecs;
    this.metadata = metadata;
    // Container specific.
    this.containerMimeType = containerMimeType;
    // Sample specific.
    this.sampleMimeType = sampleMimeType;
    this.maxInputSize = maxInputSize;
    this.initializationData =
        initializationData == null ? Collections.emptyList() : initializationData;
    this.drmInitData = drmInitData;
    this.subsampleOffsetUs = subsampleOffsetUs;
    // Video specific.
    this.width = width;
    this.height = height;
    this.frameRate = frameRate;
    this.rotationDegrees = rotationDegrees == NO_VALUE ? 0 : rotationDegrees;
    this.pixelWidthHeightRatio = pixelWidthHeightRatio == NO_VALUE ? 1 : pixelWidthHeightRatio;
    this.projectionData = projectionData;
    this.stereoMode = stereoMode;
    this.colorInfo = colorInfo;
    // Audio specific.
    this.channelCount = channelCount;
    this.sampleRate = sampleRate;
    this.pcmEncoding = pcmEncoding;
    this.encoderDelay = encoderDelay == NO_VALUE ? 0 : encoderDelay;
    this.encoderPadding = encoderPadding == NO_VALUE ? 0 : encoderPadding;
    // Text specific.
    this.accessibilityChannel = accessibilityChannel;
    // Provided by source.
    this.exoMediaCryptoType = exoMediaCryptoType;
  }

  @SuppressWarnings("ResourceType")
  /* package */ Format(Parcel in) {
    id = in.readString();
    label = in.readString();
    language = in.readString();
    selectionFlags = in.readInt();
    roleFlags = in.readInt();
    averageBitrate = in.readInt();
    peakBitrate = in.readInt();
    bitrate = peakBitrate != NO_VALUE ? peakBitrate : averageBitrate;
    codecs = in.readString();
    metadata = in.readParcelable(Metadata.class.getClassLoader());
    // Container specific.
    containerMimeType = in.readString();
    // Sample specific.
    sampleMimeType = in.readString();
    maxInputSize = in.readInt();
    int initializationDataSize = in.readInt();
    initializationData = new ArrayList<>(initializationDataSize);
    for (int i = 0; i < initializationDataSize; i++) {
      initializationData.add(in.createByteArray());
    }
    drmInitData = in.readParcelable(DrmInitData.class.getClassLoader());
    subsampleOffsetUs = in.readLong();
    // Video specific.
    width = in.readInt();
    height = in.readInt();
    frameRate = in.readFloat();
    rotationDegrees = in.readInt();
    pixelWidthHeightRatio = in.readFloat();
    boolean hasProjectionData = Util.readBoolean(in);
    projectionData = hasProjectionData ? in.createByteArray() : null;
    stereoMode = in.readInt();
    colorInfo = in.readParcelable(ColorInfo.class.getClassLoader());
    // Audio specific.
    channelCount = in.readInt();
    sampleRate = in.readInt();
    pcmEncoding = in.readInt();
    encoderDelay = in.readInt();
    encoderPadding = in.readInt();
    // Text specific.
    accessibilityChannel = in.readInt();
    // Provided by source.
    exoMediaCryptoType = null;
  }

  /** Returns a {@link Format.Builder} initialized with the values of this instance. */
  public Builder buildUpon() {
    return new Builder(this);
  }

  /** @deprecated Use {@link #buildUpon()} and {@link Builder#setMaxInputSize(int)}. */
  @Deprecated
  public Format copyWithMaxInputSize(int maxInputSize) {
    return buildUpon().setMaxInputSize(maxInputSize).build();
  }

  /** @deprecated Use {@link #buildUpon()} and {@link Builder#setSubsampleOffsetUs(long)}. */
  @Deprecated
  public Format copyWithSubsampleOffsetUs(long subsampleOffsetUs) {
    return buildUpon().setSubsampleOffsetUs(subsampleOffsetUs).build();
  }

  /** @deprecated Use {@link #buildUpon()} and {@link Builder#setLabel(String)} . */
  @Deprecated
  public Format copyWithLabel(@Nullable String label) {
    return buildUpon().setLabel(label).build();
  }

  /** @deprecated Use {@link #withManifestFormatInfo(Format)}. */
  @Deprecated
  public Format copyWithManifestFormatInfo(Format manifestFormat) {
    return withManifestFormatInfo(manifestFormat);
  }

  @SuppressWarnings("ReferenceEquality")
  public Format withManifestFormatInfo(Format manifestFormat) {
    if (this == manifestFormat) {
      // No need to copy from ourselves.
      return this;
    }

    int trackType = MimeTypes.getTrackType(sampleMimeType);

    // Use manifest value only.
    @Nullable String id = manifestFormat.id;

    // Prefer manifest values, but fill in from sample format if missing.
    @Nullable String label = manifestFormat.label != null ? manifestFormat.label : this.label;
    @Nullable String language = this.language;
    if ((trackType == C.TRACK_TYPE_TEXT || trackType == C.TRACK_TYPE_AUDIO)
        && manifestFormat.language != null) {
      language = manifestFormat.language;
    }

    // Prefer sample format values, but fill in from manifest if missing.
    int averageBitrate =
        this.averageBitrate == NO_VALUE ? manifestFormat.averageBitrate : this.averageBitrate;
    int peakBitrate = this.peakBitrate == NO_VALUE ? manifestFormat.peakBitrate : this.peakBitrate;
    @Nullable String codecs = this.codecs;
    if (codecs == null) {
      // The manifest format may be muxed, so filter only codecs of this format's type. If we still
      // have more than one codec then we're unable to uniquely identify which codec to fill in.
      @Nullable String codecsOfType = Util.getCodecsOfType(manifestFormat.codecs, trackType);
      if (Util.splitCodecs(codecsOfType).length == 1) {
        codecs = codecsOfType;
      }
    }

    @Nullable
    Metadata metadata =
        this.metadata == null
            ? manifestFormat.metadata
            : this.metadata.copyWithAppendedEntriesFrom(manifestFormat.metadata);

    float frameRate = this.frameRate;
    if (frameRate == NO_VALUE && trackType == C.TRACK_TYPE_VIDEO) {
      frameRate = manifestFormat.frameRate;
    }

    // Merge manifest and sample format values.
    @C.SelectionFlags int selectionFlags = this.selectionFlags | manifestFormat.selectionFlags;
    @C.RoleFlags int roleFlags = this.roleFlags | manifestFormat.roleFlags;
    @Nullable
    DrmInitData drmInitData =
        DrmInitData.createSessionCreationData(manifestFormat.drmInitData, this.drmInitData);

    return buildUpon()
        .setId(id)
        .setLabel(label)
        .setLanguage(language)
        .setSelectionFlags(selectionFlags)
        .setRoleFlags(roleFlags)
        .setAverageBitrate(averageBitrate)
        .setPeakBitrate(peakBitrate)
        .setCodecs(codecs)
        .setMetadata(metadata)
        .setDrmInitData(drmInitData)
        .setFrameRate(frameRate)
        .build();
  }

  /**
   * @deprecated Use {@link #buildUpon()}, {@link Builder#setEncoderDelay(int)} and {@link
   *     Builder#setEncoderPadding(int)}.
   */
  @Deprecated
  public Format copyWithGaplessInfo(int encoderDelay, int encoderPadding) {
    return buildUpon().setEncoderDelay(encoderDelay).setEncoderPadding(encoderPadding).build();
  }

  /** @deprecated Use {@link #buildUpon()} and {@link Builder#setFrameRate(float)}. */
  @Deprecated
  public Format copyWithFrameRate(float frameRate) {
    return buildUpon().setFrameRate(frameRate).build();
  }

  /** @deprecated Use {@link #buildUpon()} and {@link Builder#setDrmInitData(DrmInitData)}. */
  @Deprecated
  public Format copyWithDrmInitData(@Nullable DrmInitData drmInitData) {
    return buildUpon().setDrmInitData(drmInitData).build();
  }

  /** @deprecated Use {@link #buildUpon()} and {@link Builder#setMetadata(Metadata)}. */
  @Deprecated
  public Format copyWithMetadata(@Nullable Metadata metadata) {
    return buildUpon().setMetadata(metadata).build();
  }

  /**
   * @deprecated Use {@link #buildUpon()} and {@link Builder#setAverageBitrate(int)} and {@link
   *     Builder#setPeakBitrate(int)}.
   */
  @Deprecated
  public Format copyWithBitrate(int bitrate) {
    return buildUpon().setAverageBitrate(bitrate).setPeakBitrate(bitrate).build();
  }

  /**
   * @deprecated Use {@link #buildUpon()}, {@link Builder#setWidth(int)} and {@link
   *     Builder#setHeight(int)}.
   */
  @Deprecated
  public Format copyWithVideoSize(int width, int height) {
    return buildUpon().setWidth(width).setHeight(height).build();
  }

  /** Returns a copy of this format with the specified {@link #exoMediaCryptoType}. */
  public Format copyWithExoMediaCryptoType(
      @Nullable Class<? extends ExoMediaCrypto> exoMediaCryptoType) {
    return buildUpon().setExoMediaCryptoType(exoMediaCryptoType).build();
  }

  /**
   * Returns the number of pixels if this is a video format whose {@link #width} and {@link #height}
   * are known, or {@link #NO_VALUE} otherwise
   */
  public int getPixelCount() {
    return width == NO_VALUE || height == NO_VALUE ? NO_VALUE : (width * height);
  }

  @Override
  public String toString() {
    return "Format("
        + id
        + ", "
        + label
        + ", "
        + containerMimeType
        + ", "
        + sampleMimeType
        + ", "
        + codecs
        + ", "
        + bitrate
        + ", "
        + language
        + ", ["
        + width
        + ", "
        + height
        + ", "
        + frameRate
        + "]"
        + ", ["
        + channelCount
        + ", "
        + sampleRate
        + "])";
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      // Some fields for which hashing is expensive are deliberately omitted.
      int result = 17;
      result = 31 * result + (id == null ? 0 : id.hashCode());
      result = 31 * result + (label != null ? label.hashCode() : 0);
      result = 31 * result + (language == null ? 0 : language.hashCode());
      result = 31 * result + selectionFlags;
      result = 31 * result + roleFlags;
      result = 31 * result + averageBitrate;
      result = 31 * result + peakBitrate;
      result = 31 * result + (codecs == null ? 0 : codecs.hashCode());
      result = 31 * result + (metadata == null ? 0 : metadata.hashCode());
      // Container specific.
      result = 31 * result + (containerMimeType == null ? 0 : containerMimeType.hashCode());
      // Sample specific.
      result = 31 * result + (sampleMimeType == null ? 0 : sampleMimeType.hashCode());
      result = 31 * result + maxInputSize;
      // [Omitted] initializationData.
      // [Omitted] drmInitData.
      result = 31 * result + (int) subsampleOffsetUs;
      // Video specific.
      result = 31 * result + width;
      result = 31 * result + height;
      result = 31 * result + Float.floatToIntBits(frameRate);
      result = 31 * result + rotationDegrees;
      result = 31 * result + Float.floatToIntBits(pixelWidthHeightRatio);
      // [Omitted] projectionData.
      result = 31 * result + stereoMode;
      // [Omitted] colorInfo.
      // Audio specific.
      result = 31 * result + channelCount;
      result = 31 * result + sampleRate;
      result = 31 * result + pcmEncoding;
      result = 31 * result + encoderDelay;
      result = 31 * result + encoderPadding;
      // Text specific.
      result = 31 * result + accessibilityChannel;
      // Provided by source.
      result = 31 * result + (exoMediaCryptoType == null ? 0 : exoMediaCryptoType.hashCode());
      hashCode = result;
    }
    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Format other = (Format) obj;
    if (hashCode != 0 && other.hashCode != 0 && hashCode != other.hashCode) {
      return false;
    }
    // Field equality checks ordered by type, with the cheapest checks first.
    return selectionFlags == other.selectionFlags
        && roleFlags == other.roleFlags
        && averageBitrate == other.averageBitrate
        && peakBitrate == other.peakBitrate
        && maxInputSize == other.maxInputSize
        && subsampleOffsetUs == other.subsampleOffsetUs
        && width == other.width
        && height == other.height
        && rotationDegrees == other.rotationDegrees
        && stereoMode == other.stereoMode
        && channelCount == other.channelCount
        && sampleRate == other.sampleRate
        && pcmEncoding == other.pcmEncoding
        && encoderDelay == other.encoderDelay
        && encoderPadding == other.encoderPadding
        && accessibilityChannel == other.accessibilityChannel
        && Float.compare(frameRate, other.frameRate) == 0
        && Float.compare(pixelWidthHeightRatio, other.pixelWidthHeightRatio) == 0
        && Util.areEqual(exoMediaCryptoType, other.exoMediaCryptoType)
        && Util.areEqual(id, other.id)
        && Util.areEqual(label, other.label)
        && Util.areEqual(codecs, other.codecs)
        && Util.areEqual(containerMimeType, other.containerMimeType)
        && Util.areEqual(sampleMimeType, other.sampleMimeType)
        && Util.areEqual(language, other.language)
        && Arrays.equals(projectionData, other.projectionData)
        && Util.areEqual(metadata, other.metadata)
        && Util.areEqual(colorInfo, other.colorInfo)
        && Util.areEqual(drmInitData, other.drmInitData)
        && initializationDataEquals(other);
  }

  /**
   * Returns whether the {@link #initializationData}s belonging to this format and {@code other} are
   * equal.
   *
   * @param other The other format whose {@link #initializationData} is being compared.
   * @return Whether the {@link #initializationData}s belonging to this format and {@code other} are
   *     equal.
   */
  public boolean initializationDataEquals(Format other) {
    if (initializationData.size() != other.initializationData.size()) {
      return false;
    }
    for (int i = 0; i < initializationData.size(); i++) {
      if (!Arrays.equals(initializationData.get(i), other.initializationData.get(i))) {
        return false;
      }
    }
    return true;
  }

  // Utility methods

  /** Returns a prettier {@link String} than {@link #toString()}, intended for logging. */
  public static String toLogString(@Nullable Format format) {
    if (format == null) {
      return "null";
    }
    StringBuilder builder = new StringBuilder();
    builder.append("id=").append(format.id).append(", mimeType=").append(format.sampleMimeType);
    if (format.bitrate != NO_VALUE) {
      builder.append(", bitrate=").append(format.bitrate);
    }
    if (format.codecs != null) {
      builder.append(", codecs=").append(format.codecs);
    }
    if (format.width != NO_VALUE && format.height != NO_VALUE) {
      builder.append(", res=").append(format.width).append("x").append(format.height);
    }
    if (format.frameRate != NO_VALUE) {
      builder.append(", fps=").append(format.frameRate);
    }
    if (format.channelCount != NO_VALUE) {
      builder.append(", channels=").append(format.channelCount);
    }
    if (format.sampleRate != NO_VALUE) {
      builder.append(", sample_rate=").append(format.sampleRate);
    }
    if (format.language != null) {
      builder.append(", language=").append(format.language);
    }
    if (format.label != null) {
      builder.append(", label=").append(format.label);
    }
    return builder.toString();
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeString(label);
    dest.writeString(language);
    dest.writeInt(selectionFlags);
    dest.writeInt(roleFlags);
    dest.writeInt(averageBitrate);
    dest.writeInt(peakBitrate);
    dest.writeString(codecs);
    dest.writeParcelable(metadata, 0);
    // Container specific.
    dest.writeString(containerMimeType);
    // Sample specific.
    dest.writeString(sampleMimeType);
    dest.writeInt(maxInputSize);
    int initializationDataSize = initializationData.size();
    dest.writeInt(initializationDataSize);
    for (int i = 0; i < initializationDataSize; i++) {
      dest.writeByteArray(initializationData.get(i));
    }
    dest.writeParcelable(drmInitData, 0);
    dest.writeLong(subsampleOffsetUs);
    // Video specific.
    dest.writeInt(width);
    dest.writeInt(height);
    dest.writeFloat(frameRate);
    dest.writeInt(rotationDegrees);
    dest.writeFloat(pixelWidthHeightRatio);
    Util.writeBoolean(dest, projectionData != null);
    if (projectionData != null) {
      dest.writeByteArray(projectionData);
    }
    dest.writeInt(stereoMode);
    dest.writeParcelable(colorInfo, flags);
    // Audio specific.
    dest.writeInt(channelCount);
    dest.writeInt(sampleRate);
    dest.writeInt(pcmEncoding);
    dest.writeInt(encoderDelay);
    dest.writeInt(encoderPadding);
    // Text specific.
    dest.writeInt(accessibilityChannel);
  }

  public static final Creator<Format> CREATOR = new Creator<Format>() {

    @Override
    public Format createFromParcel(Parcel in) {
      return new Format(in);
    }

    @Override
    public Format[] newArray(int size) {
      return new Format[size];
    }

  };
}
