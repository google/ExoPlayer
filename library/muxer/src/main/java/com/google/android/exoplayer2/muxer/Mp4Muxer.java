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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.MediaCodec.BufferInfo;
import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
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
    @Nullable private AnnexBToAvccConverter annexBToAvccConverter;

    /**
     * Creates a {@link Builder} instance with default values.
     *
     * @param fileOutputStream The {@link FileOutputStream} to write the media data to.
     */
    public Builder(FileOutputStream fileOutputStream) {
      this.fileOutputStream = checkNotNull(fileOutputStream);
      lastFrameDurationBehavior = LAST_FRAME_DURATION_BEHAVIOR_INSERT_SHORT_FRAME;
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

    /** Builds an {@link Mp4Muxer} instance. */
    public Mp4Muxer build() {
      MetadataCollector metadataCollector = new MetadataCollector();
      Mp4MoovStructure moovStructure =
          new Mp4MoovStructure(metadataCollector, lastFrameDurationBehavior);
      Mp4Writer mp4Writer =
          new Mp4Writer(
              fileOutputStream,
              moovStructure,
              annexBToAvccConverter == null
                  ? AnnexBToAvccConverter.DEFAULT
                  : annexBToAvccConverter);

      return new Mp4Muxer(mp4Writer, metadataCollector);
    }
  }

  /** A list of supported video sample mime types. */
  public static final ImmutableList<String> SUPPORTED_VIDEO_SAMPLE_MIME_TYPES =
      ImmutableList.of(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265, MimeTypes.VIDEO_AV1);

  /** A list of supported audio sample mime types. */
  public static final ImmutableList<String> SUPPORTED_AUDIO_SAMPLE_MIME_TYPES =
      ImmutableList.of(MimeTypes.AUDIO_AAC);

  private final Mp4Writer mp4Writer;
  private final MetadataCollector metadataCollector;

  private Mp4Muxer(Mp4Writer mp4Writer, MetadataCollector metadataCollector) {
    this.mp4Writer = mp4Writer;
    this.metadataCollector = metadataCollector;
  }

  /**
   * Sets the orientation hint for the video playback.
   *
   * @param orientation The orientation, in degrees.
   */
  public void setOrientation(int orientation) {
    metadataCollector.setOrientation(orientation);
  }

  /**
   * Sets the location.
   *
   * @param latitude The latitude, in degrees. Its value must be in the range [-90, 90].
   * @param longitude The longitude, in degrees. Its value must be in the range [-180, 180].
   */
  public void setLocation(
      @FloatRange(from = -90.0, to = 90.0) float latitude,
      @FloatRange(from = -180.0, to = 180.0) float longitude) {
    metadataCollector.setLocation(latitude, longitude);
  }

  /**
   * Sets the capture frame rate.
   *
   * @param captureFps The frame rate.
   */
  public void setCaptureFps(float captureFps) {
    metadataCollector.setCaptureFps(captureFps);
  }

  /**
   * Sets the file modification time.
   *
   * @param timestampMs The modification time UTC in milliseconds since the Unix epoch.
   */
  public void setModificationTime(long timestampMs) {
    metadataCollector.setModificationTime(timestampMs);
  }

  /**
   * Adds custom metadata.
   *
   * @param key The metadata key in {@link String} format.
   * @param value The metadata value in {@link String} or {@link Float} format.
   */
  public void addMetadata(String key, Object value) {
    metadataCollector.addMetadata(key, value);
  }

  /**
   * Adds xmp data.
   *
   * @param xmp The xmp {@link ByteBuffer}.
   */
  public void addXmp(ByteBuffer xmp) {
    metadataCollector.addXmp(xmp);
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
}
