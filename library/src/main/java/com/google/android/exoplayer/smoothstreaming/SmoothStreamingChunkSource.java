/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.smoothstreaming;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.chunk.Chunk;
import com.google.android.exoplayer.chunk.ChunkOperationHolder;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.chunk.Format.DecreasingBandwidthComparator;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.Evaluation;
import com.google.android.exoplayer.chunk.MediaChunk;
import com.google.android.exoplayer.chunk.Mp4MediaChunk;
import com.google.android.exoplayer.parser.Extractor;
import com.google.android.exoplayer.parser.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer.parser.mp4.Track;
import com.google.android.exoplayer.parser.mp4.TrackEncryptionBox;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.ProtectionElement;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.StreamElement;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.TrackElement;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.CodecSpecificDataUtil;

import android.net.Uri;
import android.util.Base64;
import android.util.SparseArray;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An {@link ChunkSource} for SmoothStreaming.
 */
public class SmoothStreamingChunkSource implements ChunkSource {

  private static final int INITIALIZATION_VECTOR_SIZE = 8;

  private final String baseUrl;
  private final StreamElement streamElement;
  private final TrackInfo trackInfo;
  private final DataSource dataSource;
  private final FormatEvaluator formatEvaluator;
  private final Evaluation evaluation;

  private final int maxWidth;
  private final int maxHeight;

  private final SparseArray<FragmentedMp4Extractor> extractors;
  private final SmoothStreamingFormat[] formats;

  /**
   * @param baseUrl The base URL for the streams.
   * @param manifest The manifest parsed from {@code baseUrl + "/Manifest"}.
   * @param streamElementIndex The index of the stream element in the manifest to be provided by
   *     the source.
   * @param trackIndices The indices of the tracks within the stream element to be considered by
   *     the source. May be null if all tracks within the element should be considered.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param formatEvaluator Selects from the available formats.
   */
  public SmoothStreamingChunkSource(String baseUrl, SmoothStreamingManifest manifest,
      int streamElementIndex, int[] trackIndices, DataSource dataSource,
      FormatEvaluator formatEvaluator) {
    this.baseUrl = baseUrl;
    this.streamElement = manifest.streamElements[streamElementIndex];
    this.trackInfo = new TrackInfo(streamElement.tracks[0].mimeType, manifest.getDurationUs());
    this.dataSource = dataSource;
    this.formatEvaluator = formatEvaluator;
    this.evaluation = new Evaluation();

    TrackEncryptionBox[] trackEncryptionBoxes = null;
    ProtectionElement protectionElement = manifest.protectionElement;
    if (protectionElement != null) {
      byte[] keyId = getKeyId(protectionElement.data);
      trackEncryptionBoxes = new TrackEncryptionBox[1];
      trackEncryptionBoxes[0] = new TrackEncryptionBox(true, INITIALIZATION_VECTOR_SIZE, keyId);
    }

    int trackCount = trackIndices != null ? trackIndices.length : streamElement.tracks.length;
    formats = new SmoothStreamingFormat[trackCount];
    extractors = new SparseArray<FragmentedMp4Extractor>();
    int maxWidth = 0;
    int maxHeight = 0;
    for (int i = 0; i < trackCount; i++) {
      int trackIndex = trackIndices != null ? trackIndices[i] : i;
      TrackElement trackElement = streamElement.tracks[trackIndex];
      formats[i] = new SmoothStreamingFormat(String.valueOf(trackIndex), trackElement.mimeType,
          trackElement.maxWidth, trackElement.maxHeight, trackElement.numChannels,
          trackElement.sampleRate, trackElement.bitrate, trackIndex);
      maxWidth = Math.max(maxWidth, trackElement.maxWidth);
      maxHeight = Math.max(maxHeight, trackElement.maxHeight);

      MediaFormat mediaFormat = getMediaFormat(streamElement, trackIndex);
      int trackType = streamElement.type == StreamElement.TYPE_VIDEO ? Track.TYPE_VIDEO
          : Track.TYPE_AUDIO;
      FragmentedMp4Extractor extractor = new FragmentedMp4Extractor(
          FragmentedMp4Extractor.WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME);
      extractor.setTrack(new Track(trackIndex, trackType, streamElement.timeScale, mediaFormat,
          trackEncryptionBoxes));
      if (protectionElement != null) {
        extractor.putPsshInfo(protectionElement.uuid, protectionElement.data);
      }
      extractors.put(trackIndex, extractor);
    }
    this.maxHeight = maxHeight;
    this.maxWidth = maxWidth;
    Arrays.sort(formats, new DecreasingBandwidthComparator());
  }

  @Override
  public final void getMaxVideoDimensions(MediaFormat out) {
    if (trackInfo.mimeType.startsWith("video")) {
      out.setMaxVideoDimensions(maxWidth, maxHeight);
    }
  }

  @Override
  public final TrackInfo getTrackInfo() {
    return trackInfo;
  }

  @Override
  public void enable() {
    // Do nothing.
  }

  @Override
  public void disable(List<? extends MediaChunk> queue) {
    // Do nothing.
  }

  @Override
  public void continueBuffering(long playbackPositionUs) {
    // Do nothing
  }

  @Override
  public final void getChunkOperation(List<? extends MediaChunk> queue, long seekPositionUs,
      long playbackPositionUs, ChunkOperationHolder out) {
    evaluation.queueSize = queue.size();
    formatEvaluator.evaluate(queue, playbackPositionUs, formats, evaluation);
    SmoothStreamingFormat selectedFormat = (SmoothStreamingFormat) evaluation.format;
    out.queueSize = evaluation.queueSize;

    if (selectedFormat == null) {
      out.chunk = null;
      return;
    } else if (out.queueSize == queue.size() && out.chunk != null
        && out.chunk.format.id.equals(evaluation.format.id)) {
      // We already have a chunk, and the evaluation hasn't changed either the format or the size
      // of the queue. Do nothing.
      return;
    }

    int nextChunkIndex;
    if (queue.isEmpty()) {
      nextChunkIndex = streamElement.getChunkIndex(seekPositionUs);
    } else {
      nextChunkIndex = queue.get(out.queueSize - 1).nextChunkIndex;
    }

    if (nextChunkIndex == -1) {
      out.chunk = null;
      return;
    }

    boolean isLastChunk = nextChunkIndex == streamElement.chunkCount - 1;
    String requestUrl = streamElement.buildRequestUrl(selectedFormat.trackIndex,
        nextChunkIndex);
    Uri uri = Uri.parse(baseUrl + '/' + requestUrl);
    Chunk mediaChunk = newMediaChunk(selectedFormat, uri, null,
        extractors.get(Integer.parseInt(selectedFormat.id)), dataSource, nextChunkIndex,
        isLastChunk, streamElement.getStartTimeUs(nextChunkIndex),
        isLastChunk ? -1 : streamElement.getStartTimeUs(nextChunkIndex + 1), 0);
    out.chunk = mediaChunk;
  }

  @Override
  public IOException getError() {
    return null;
  }

  @Override
  public void onChunkLoadError(Chunk chunk, Exception e) {
    // Do nothing.
  }

  private static MediaFormat getMediaFormat(StreamElement streamElement, int trackIndex) {
    TrackElement trackElement = streamElement.tracks[trackIndex];
    String mimeType = trackElement.mimeType;
    if (streamElement.type == StreamElement.TYPE_VIDEO) {
      MediaFormat format = MediaFormat.createVideoFormat(mimeType, -1, trackElement.maxWidth,
          trackElement.maxHeight, Arrays.asList(trackElement.csd));
      format.setMaxVideoDimensions(streamElement.maxWidth, streamElement.maxHeight);
      return format;
    } else if (streamElement.type == StreamElement.TYPE_AUDIO) {
      List<byte[]> csd;
      if (trackElement.csd != null) {
        csd = Arrays.asList(trackElement.csd);
      } else {
        csd = Collections.singletonList(CodecSpecificDataUtil.buildAudioSpecificConfig(
            trackElement.sampleRate, trackElement.numChannels));
      }
      MediaFormat format = MediaFormat.createAudioFormat(mimeType, -1, trackElement.numChannels,
          trackElement.sampleRate, csd);
      return format;
    }
    // TODO: Do subtitles need a format? MediaFormat supports KEY_LANGUAGE.
    return null;
  }

  private static MediaChunk newMediaChunk(Format formatInfo, Uri uri, String cacheKey,
      Extractor extractor, DataSource dataSource, int chunkIndex,
      boolean isLast, long chunkStartTimeUs, long nextChunkStartTimeUs, int trigger) {
    int nextChunkIndex = isLast ? -1 : chunkIndex + 1;
    long nextStartTimeUs = isLast ? -1 : nextChunkStartTimeUs;
    long offset = 0;
    DataSpec dataSpec = new DataSpec(uri, offset, -1, cacheKey);
    // In SmoothStreaming each chunk contains sample timestamps relative to the start of the chunk.
    // To convert them the absolute timestamps, we need to set sampleOffsetUs to -chunkStartTimeUs.
    return new Mp4MediaChunk(dataSource, dataSpec, formatInfo, trigger, chunkStartTimeUs,
        nextStartTimeUs, nextChunkIndex, extractor, false, -chunkStartTimeUs);
  }

  private static byte[] getKeyId(byte[] initData) {
    StringBuilder initDataStringBuilder = new StringBuilder();
    for (int i = 0; i < initData.length; i += 2) {
      initDataStringBuilder.append((char) initData[i]);
    }
    String initDataString = initDataStringBuilder.toString();
    String keyIdString = initDataString.substring(
        initDataString.indexOf("<KID>") + 5, initDataString.indexOf("</KID>"));
    byte[] keyId = Base64.decode(keyIdString, Base64.DEFAULT);
    swap(keyId, 0, 3);
    swap(keyId, 1, 2);
    swap(keyId, 4, 5);
    swap(keyId, 6, 7);
    return keyId;
  }

  private static void swap(byte[] data, int firstPosition, int secondPosition) {
    byte temp = data[firstPosition];
    data[firstPosition] = data[secondPosition];
    data[secondPosition] = temp;
  }

  private static final class SmoothStreamingFormat extends Format {

    public final int trackIndex;

    public SmoothStreamingFormat(String id, String mimeType, int width, int height,
        int numChannels, int audioSamplingRate, int bitrate, int trackIndex) {
      super(id, mimeType, width, height, numChannels, audioSamplingRate, bitrate);
      this.trackIndex = trackIndex;
    }

  }

}
