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
package com.google.android.exoplayer2.muxer;

import static com.google.android.exoplayer2.muxer.Mp4Utils.UNSIGNED_INT_MAX_VALUE;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.MediaCodec.BufferInfo;
import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.container.Mp4LocationData;
import com.google.android.exoplayer2.container.Mp4OrientationData;
import com.google.android.exoplayer2.container.Mp4TimestampData;
import com.google.android.exoplayer2.container.XmpData;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.mp4.MdtaMetadataEntry;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;

/**
 * A muxer for creating an MP4 container file.
 *
 * <p>The muxer supports writing H264, H265 and AV1 video, AAC audio and metadata.
 *
 * <p>All the operations are performed on the caller thread.
 *
 * <p>To create an MP4 container file, the caller must:
 *
 * <ul>
 *   <li>Add tracks using {@link #addTrack(int, Format)} which will return a {@link TrackToken}.
 *   <li>Use the associated {@link TrackToken} when {@linkplain #writeSampleData(TrackToken,
 *       ByteBuffer, BufferInfo) writing samples} for that track.
 *   <li>{@link #close} the muxer when all data has been written.
 * </ul>
 *
 * <p>Some key points:
 *
 * <ul>
 *   <li>Tracks can be added at any point, even after writing some samples to other tracks.
 *   <li>The caller is responsible for ensuring that samples of different track types are well
 *       interleaved by calling {@link #writeSampleData(TrackToken, ByteBuffer, BufferInfo)} in an
 *       order that interleaves samples from different tracks.
 *   <li>When writing a file, if an error occurs and the muxer is not closed, then the output MP4
 *       file may still have some partial data.
 * </ul>
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class Mp4Muxer {
  /** A token representing an added track. */
  public interface TrackToken {}

  /** Behavior for the last sample duration. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    LAST_FRAME_DURATION_BEHAVIOR_DUPLICATE_PREV_DURATION,
    LAST_FRAME_DURATION_BEHAVIOR_INSERT_SHORT_FRAME
  })
  public @interface LastFrameDurationBehavior {}

  /** Insert a zero-length last sample. */
  public static final int LAST_FRAME_DURATION_BEHAVIOR_INSERT_SHORT_FRAME = 0;

  /**
   * Use the difference between the last timestamp and the one before that as the duration of the
   * last sample.
   */
  public static final int LAST_FRAME_DURATION_BEHAVIOR_DUPLICATE_PREV_DURATION = 1;

  /** A builder for {@link Mp4Muxer} instances. */
  public static final class Builder {
    private final FileOutputStream fileOutputStream;

    private @LastFrameDurationBehavior int lastFrameDurationBehavior;
    private boolean fragmentedMp4Enabled;
    private int fragmentDurationUs;
    @Nullable private AnnexBToAvccConverter annexBToAvccConverter;

    /**
     * Creates a {@link Builder} instance with default values.
     *
     * @param fileOutputStream The {@link FileOutputStream} to write the media data to.
     */
    public Builder(FileOutputStream fileOutputStream) {
      this.fileOutputStream = checkNotNull(fileOutputStream);
      lastFrameDurationBehavior = LAST_FRAME_DURATION_BEHAVIOR_INSERT_SHORT_FRAME;
      fragmentDurationUs = DEFAULT_FRAGMENT_DURATION_US;
    }

    /**
     * Sets the {@link LastFrameDurationBehavior} for the video track.
     *
     * <p>The default value is {@link #LAST_FRAME_DURATION_BEHAVIOR_INSERT_SHORT_FRAME}.
     */
    @CanIgnoreReturnValue
    public Mp4Muxer.Builder setLastFrameDurationBehavior(
        @LastFrameDurationBehavior int lastFrameDurationBehavior) {
      this.lastFrameDurationBehavior = lastFrameDurationBehavior;
      return this;
    }

    /**
     * Sets the {@link AnnexBToAvccConverter} to be used by the muxer to convert H.264 and H.265 NAL
     * units from the Annex-B format (using start codes to delineate NAL units) to the AVCC format
     * (which uses length prefixes).
     *
     * <p>The default value is {@link AnnexBToAvccConverter#DEFAULT}.
     */
    @CanIgnoreReturnValue
    public Mp4Muxer.Builder setAnnexBToAvccConverter(AnnexBToAvccConverter annexBToAvccConverter) {
      this.annexBToAvccConverter = annexBToAvccConverter;
      return this;
    }

    /**
     * Sets whether to enable writing a fragmented MP4.
     *
     * <p>The default value is {@code false}.
     */
    @CanIgnoreReturnValue
    public Mp4Muxer.Builder setFragmentedMp4Enabled(boolean enabled) {
      fragmentedMp4Enabled = enabled;
      return this;
    }

    /**
     * Sets fragment duration for the {@linkplain #setFragmentedMp4Enabled(boolean) fragmented MP4}.
     *
     * <p>Muxer will attempt to create fragments of the given duration but the actual duration might
     * be greater depending upon the frequency of sync samples.
     *
     * <p>The duration is ignored for {@linkplain #setFragmentedMp4Enabled(boolean) non fragmented
     * MP4}.
     *
     * <p>The default value is {@link #DEFAULT_FRAGMENT_DURATION_US}.
     *
     * @param fragmentDurationUs The fragment duration in microseconds.
     * @return The {@link Mp4Muxer.Builder}.
     */
    @CanIgnoreReturnValue
    public Mp4Muxer.Builder setFragmentDurationUs(int fragmentDurationUs) {
      this.fragmentDurationUs = fragmentDurationUs;
      return this;
    }

    /** Builds an {@link Mp4Muxer} instance. */
    public Mp4Muxer build() {
      MetadataCollector metadataCollector = new MetadataCollector();
      Mp4MoovStructure moovStructure =
          new Mp4MoovStructure(metadataCollector, lastFrameDurationBehavior);
      AnnexBToAvccConverter avccConverter =
          annexBToAvccConverter == null ? AnnexBToAvccConverter.DEFAULT : annexBToAvccConverter;
      Mp4Writer mp4Writer =
          fragmentedMp4Enabled
              ? new FragmentedMp4Writer(
                  fileOutputStream, moovStructure, avccConverter, fragmentDurationUs)
              : new BasicMp4Writer(fileOutputStream, moovStructure, avccConverter);

      return new Mp4Muxer(mp4Writer, metadataCollector);
    }
  }

  /** A list of supported video sample mime types. */
  public static final ImmutableList<String> SUPPORTED_VIDEO_SAMPLE_MIME_TYPES =
      ImmutableList.of(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265, MimeTypes.VIDEO_AV1);

  /** A list of supported audio sample mime types. */
  public static final ImmutableList<String> SUPPORTED_AUDIO_SAMPLE_MIME_TYPES =
      ImmutableList.of(MimeTypes.AUDIO_AAC);

  /**
   * The default fragment duration for the {@linkplain Builder#setFragmentedMp4Enabled(boolean)
   * fragmented MP4}.
   */
  public static final int DEFAULT_FRAGMENT_DURATION_US = 2_000_000;

  private final Mp4Writer mp4Writer;
  private final MetadataCollector metadataCollector;

  private Mp4Muxer(Mp4Writer mp4Writer, MetadataCollector metadataCollector) {
    this.mp4Writer = mp4Writer;
    this.metadataCollector = metadataCollector;
  }

  /**
   * Returns whether a given {@link Metadata.Entry metadata} is supported.
   *
   * <p>For the list of supported metadata refer to {@link Mp4Muxer#addMetadata(Metadata.Entry)}.
   */
  public static boolean isMetadataSupported(Metadata.Entry metadata) {
    return metadata instanceof Mp4OrientationData
        || metadata instanceof Mp4LocationData
        || (metadata instanceof Mp4TimestampData
            && isMp4TimestampDataSupported((Mp4TimestampData) metadata))
        || (metadata instanceof MdtaMetadataEntry
            && isMdtaMetadataEntrySupported((MdtaMetadataEntry) metadata))
        || metadata instanceof XmpData;
  }

  /**
   * @deprecated Use {@link #addMetadata(Metadata.Entry)} with {@link Mp4OrientationData} instead.
   */
  @Deprecated
  public void setOrientation(int orientation) {
    addMetadata(new Mp4OrientationData(orientation));
  }

  /**
   * @deprecated Use {@link #addMetadata(Metadata.Entry)} with {@link Mp4LocationData} instead.
   */
  @Deprecated
  public void setLocation(
      @FloatRange(from = -90.0, to = 90.0) float latitude,
      @FloatRange(from = -180.0, to = 180.0) float longitude) {
    addMetadata(new Mp4LocationData(latitude, longitude));
  }

  /**
   * @deprecated Use {@link #addMetadata(Metadata.Entry)} with {@link MdtaMetadataEntry} instead.
   */
  @Deprecated
  public void setCaptureFps(float captureFps) {
    addMetadata(
        new MdtaMetadataEntry(
            MdtaMetadataEntry.KEY_ANDROID_CAPTURE_FPS,
            Util.toByteArray(captureFps),
            MdtaMetadataEntry.TYPE_INDICATOR_FLOAT32));
  }

  /**
   * @deprecated Use {@link #addMetadata(Metadata.Entry)} with {@link Mp4TimestampData} instead.
   */
  @Deprecated
  public void setTimestampData(Mp4TimestampData timestampData) {
    addMetadata(timestampData);
  }

  /**
   * @deprecated Use {@link #addMetadata(Metadata.Entry)} with {@link MdtaMetadataEntry} instead.
   */
  @Deprecated
  public void addMetadata(String key, Object value) {
    MdtaMetadataEntry mdtaMetadataEntry = null;
    if (value instanceof String) {
      mdtaMetadataEntry =
          new MdtaMetadataEntry(
              key, Util.getUtf8Bytes((String) value), MdtaMetadataEntry.TYPE_INDICATOR_STRING);
    } else if (value instanceof Float) {
      mdtaMetadataEntry =
          new MdtaMetadataEntry(
              key, Util.toByteArray((Float) value), MdtaMetadataEntry.TYPE_INDICATOR_FLOAT32);
    } else {
      throw new IllegalArgumentException("Unsupported metadata");
    }
    addMetadata(mdtaMetadataEntry);
  }

  /**
   * Adds metadata for the output file.
   *
   * <p>List of supported {@linkplain Metadata.Entry metadata entries}:
   *
   * <ul>
   *   <li>{@link Mp4OrientationData}
   *   <li>{@link Mp4LocationData}
   *   <li>{@link Mp4TimestampData}
   *   <li>{@link MdtaMetadataEntry}: Only {@linkplain MdtaMetadataEntry#TYPE_INDICATOR_STRING
   *       string type} or {@linkplain MdtaMetadataEntry#TYPE_INDICATOR_FLOAT32 float type} value is
   *       supported.
   *   <li>{@link XmpData}
   * </ul>
   *
   * @param metadata The {@linkplain Metadata.Entry metadata}. An {@link IllegalArgumentException}
   *     is throw if the {@linkplain Metadata.Entry metadata} is not supported.
   */
  public void addMetadata(Metadata.Entry metadata) {
    checkArgument(isMetadataSupported(metadata), "Unsupported metadata");
    metadataCollector.addMetadata(metadata);
  }

  /**
   * @deprecated Use {@link #addMetadata(Metadata.Entry)} with {@link XmpData} instead.
   */
  @Deprecated
  public void addXmp(ByteBuffer xmp) {
    byte[] xmpData = new byte[xmp.remaining()];
    xmp.get(xmpData, 0, xmpData.length);
    addMetadata(new XmpData(xmpData));
  }

  /**
   * Adds a track of the given media format.
   *
   * <p>Tracks can be added at any point before the muxer is closed, even after writing samples to
   * other tracks.
   *
   * <p>The final order of tracks is determined by the provided sort key. Tracks with a lower sort
   * key will always have a lower track id than tracks with a higher sort key. Ordering between
   * tracks with the same sort key is not specified.
   *
   * @param sortKey The key used for sorting the track list.
   * @param format The {@link Format} for the track.
   * @return A unique {@link TrackToken}. It should be used in {@link #writeSampleData}.
   */
  public TrackToken addTrack(int sortKey, Format format) {
    return mp4Writer.addTrack(sortKey, format);
  }

  /**
   * Writes encoded sample data.
   *
   * <p>The samples are cached and are written in batches so the caller must not change/release the
   * {@link ByteBuffer} and the {@link BufferInfo} after calling this method.
   *
   * <p>Note: Out of order B-frames are currently not supported.
   *
   * @param trackToken The {@link TrackToken} for which this sample is being written.
   * @param byteBuffer The encoded sample.
   * @param bufferInfo The {@link BufferInfo} related to this sample.
   * @throws IOException If there is any error while writing data to the disk.
   */
  public void writeSampleData(TrackToken trackToken, ByteBuffer byteBuffer, BufferInfo bufferInfo)
      throws IOException {
    mp4Writer.writeSampleData(trackToken, byteBuffer, bufferInfo);
  }

  /** Closes the MP4 file. */
  public void close() throws IOException {
    mp4Writer.close();
  }

  private static boolean isMdtaMetadataEntrySupported(MdtaMetadataEntry mdtaMetadataEntry) {
    return mdtaMetadataEntry.typeIndicator == MdtaMetadataEntry.TYPE_INDICATOR_STRING
        || mdtaMetadataEntry.typeIndicator == MdtaMetadataEntry.TYPE_INDICATOR_FLOAT32;
  }

  private static boolean isMp4TimestampDataSupported(Mp4TimestampData timestampData) {
    return timestampData.creationTimestampSeconds <= UNSIGNED_INT_MAX_VALUE
        && timestampData.modificationTimestampSeconds <= UNSIGNED_INT_MAX_VALUE;
  }
}
