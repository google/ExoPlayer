/*
 * Copyright 2024 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.media.MediaCodec;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.container.Mp4LocationData;
import com.google.android.exoplayer2.container.Mp4OrientationData;
import com.google.android.exoplayer2.container.Mp4TimestampData;
import com.google.android.exoplayer2.container.XmpData;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.mp4.MdtaMetadataEntry;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A muxer for creating a fragmented MP4 file.
 *
 * <p>The muxer supports writing H264, H265 and AV1 video, AAC audio and metadata.
 *
 * <p>All the operations are performed on the caller thread.
 *
 * <p>To create a fragmented MP4 file, the caller must:
 *
 * <ul>
 *   <li>Add tracks using {@link #addTrack(Format)} which will return a {@link Mp4Muxer.TrackToken}.
 *   <li>Use the associated {@link Mp4Muxer.TrackToken} when {@linkplain
 *       #writeSampleData(Mp4Muxer.TrackToken, ByteBuffer, MediaCodec.BufferInfo) writing samples}
 *       for that track.
 *   <li>{@link #close} the muxer when all data has been written.
 * </ul>
 *
 * <p>Some key points:
 *
 * <ul>
 *   <li>All tracks must be added before writing any samples.
 *   <li>The caller is responsible for ensuring that samples of different track types are well
 *       interleaved by calling {@link #writeSampleData(Mp4Muxer.TrackToken, ByteBuffer,
 *       MediaCodec.BufferInfo)} in an order that interleaves samples from different tracks.
 * </ul>
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class FragmentedMp4Muxer implements Muxer {
  private static final int DEFAULT_FRAGMENT_DURATION_US = 2_000_000;

  private final FragmentedMp4Writer fragmentedMp4Writer;
  private final MetadataCollector metadataCollector;

  /** Creates an instance with default fragment duration. */
  public FragmentedMp4Muxer(FileOutputStream fileOutputStream) {
    this(fileOutputStream, DEFAULT_FRAGMENT_DURATION_US);
  }

  /**
   * Creates an instance.
   *
   * @param fileOutputStream The {@link FileOutputStream} to write the media data to.
   * @param fragmentDurationUs The fragment duration (in microseconds). The muxer will attempt to
   *     create fragments of the given duration but the actual duration might be greater depending
   *     upon the frequency of sync samples.
   */
  public FragmentedMp4Muxer(FileOutputStream fileOutputStream, int fragmentDurationUs) {
    checkNotNull(fileOutputStream);
    metadataCollector = new MetadataCollector();
    Mp4MoovStructure moovStructure =
        new Mp4MoovStructure(
            metadataCollector, Mp4Muxer.LAST_FRAME_DURATION_BEHAVIOR_DUPLICATE_PREV_DURATION);
    fragmentedMp4Writer =
        new FragmentedMp4Writer(
            fileOutputStream, moovStructure, AnnexBToAvccConverter.DEFAULT, fragmentDurationUs);
  }

  @Override
  public TrackToken addTrack(Format format) {
    return fragmentedMp4Writer.addTrack(/* sortKey= */ 1, format);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The samples are cached and are written in batches so the caller must not change the {@link
   * ByteBuffer} and the {@link MediaCodec.BufferInfo} after calling this method.
   *
   * <p>Note: Out of order B-frames are currently not supported.
   *
   * @param trackToken The {@link TrackToken} for which this sample is being written.
   * @param byteBuffer The encoded sample.
   * @param bufferInfo The {@link MediaCodec.BufferInfo} related to this sample.
   * @throws IOException If there is any error while writing data to the disk.
   */
  @Override
  public void writeSampleData(
      TrackToken trackToken, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo)
      throws IOException {
    fragmentedMp4Writer.writeSampleData(trackToken, byteBuffer, bufferInfo);
  }

  /**
   * {@inheritDoc}
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
   *     is thrown if the {@linkplain Metadata.Entry metadata} is not supported.
   */
  @Override
  public void addMetadata(Metadata.Entry metadata) {
    checkArgument(Mp4Utils.isMetadataSupported(metadata), "Unsupported metadata");
    metadataCollector.addMetadata(metadata);
  }

  @Override
  public void close() throws IOException {
    fragmentedMp4Writer.close();
  }
}
