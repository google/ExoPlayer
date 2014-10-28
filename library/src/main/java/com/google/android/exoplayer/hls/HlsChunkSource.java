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
package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.Util;

import android.net.Uri;
import android.os.SystemClock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

/**
 * A temporary test source of HLS chunks.
 * <p>
 * TODO: Figure out whether this should merge with the chunk package, or whether the hls
 * implementation is going to naturally diverge.
 */
public class HlsChunkSource {

  private final DataSource dataSource;
  private final HlsMasterPlaylist masterPlaylist;
  private final HlsMediaPlaylistParser mediaPlaylistParser;

  private long liveStartTimeUs;
  /* package */ HlsMediaPlaylist mediaPlaylist;
  /* package */ boolean mediaPlaylistWasLive;
  /* package */ long lastMediaPlaylistLoadTimeMs;

  // TODO: Once proper m3u8 parsing is in place, actually use the url!
  public HlsChunkSource(DataSource dataSource, HlsMasterPlaylist masterPlaylist) {
    this.dataSource = dataSource;
    this.masterPlaylist = masterPlaylist;
    mediaPlaylistParser = new HlsMediaPlaylistParser();
  }

  public long getDurationUs() {
    return mediaPlaylistWasLive ? TrackRenderer.UNKNOWN_TIME_US : mediaPlaylist.durationUs;
  }

  /**
   * Adaptive implementations must set the maximum video dimensions on the supplied
   * {@link MediaFormat}. Other implementations do nothing.
   * <p>
   * Only called when the source is enabled.
   *
   * @param out The {@link MediaFormat} on which the maximum video dimensions should be set.
   */
  public void getMaxVideoDimensions(MediaFormat out) {
    // TODO: Implement this.
  }

  /**
   * Updates the provided {@link HlsChunkOperationHolder} to contain the next operation that should
   * be performed by the calling {@link HlsSampleSource}.
   * <p>
   * The next operation comprises of a possibly shortened queue length (shortened if the
   * implementation wishes for the caller to discard {@link TsChunk}s from the queue), together
   * with the next {@link HlsChunk} to load. The next chunk may be a {@link TsChunk} to be added to
   * the queue, or another {@link HlsChunk} type (e.g. to load initialization data), or null if the
   * source is not able to provide a chunk in its current state.
   *
   * @param queue A representation of the currently buffered {@link TsChunk}s.
   * @param seekPositionUs If the queue is empty, this parameter must specify the seek position. If
   *     the queue is non-empty then this parameter is ignored.
   * @param playbackPositionUs The current playback position.
   * @param out A holder for the next operation, whose {@link HlsChunkOperationHolder#queueSize} is
   *     initially equal to the length of the queue, and whose {@linkHls ChunkOperationHolder#chunk}
   *     is initially equal to null or a {@link TsChunk} previously supplied by the
   *     {@link HlsChunkSource} that the caller has not yet finished loading. In the latter case the
   *     chunk can either be replaced or left unchanged. Note that leaving the chunk unchanged is
   *     both preferred and more efficient than replacing it with a new but identical chunk.
   */
  public void getChunkOperation(List<TsChunk> queue, long seekPositionUs, long playbackPositionUs,
      HlsChunkOperationHolder out) {
    if (out.chunk != null) {
      // We already have a chunk. Keep it.
      return;
    }

    if (mediaPlaylist == null) {
      out.chunk = newMediaPlaylistChunk();
      return;
    }

    int chunkMediaSequence = 0;
    if (mediaPlaylistWasLive) {
      if (queue.isEmpty()) {
        chunkMediaSequence = getLiveStartChunkMediaSequence();
      } else {
        // For live nextChunkIndex contains chunk media sequence number.
        chunkMediaSequence = queue.get(queue.size() - 1).nextChunkIndex;
        // If the updated playlist is far ahead and doesn't even have the last chunk from the
        // queue, then try to catch up, skip a few chunks and start as if it was a new playlist.
        if (chunkMediaSequence < mediaPlaylist.mediaSequence) {
          // TODO: Trigger discontinuity in this case.
          chunkMediaSequence = getLiveStartChunkMediaSequence();
        }
      }
    } else {
      // Not live.
      if (queue.isEmpty()) {
        chunkMediaSequence = Util.binarySearchFloor(mediaPlaylist.segments, seekPositionUs, true,
            true) + mediaPlaylist.mediaSequence;
      } else {
        chunkMediaSequence = queue.get(queue.size() - 1).nextChunkIndex;
      }
    }

    if (chunkMediaSequence == -1) {
      out.chunk = null;
      return;
    }

    int chunkIndex = chunkMediaSequence - mediaPlaylist.mediaSequence;
    // If the end of the playlist is reached.
    if (chunkIndex >= mediaPlaylist.segments.size()) {
      if (mediaPlaylist.live && shouldRerequestMediaPlaylist()) {
        out.chunk = newMediaPlaylistChunk();
      } else {
        out.chunk = null;
      }
      return;
    }

    HlsMediaPlaylist.Segment segment = mediaPlaylist.segments.get(chunkIndex);

    Uri chunkUri = Util.getMergedUri(mediaPlaylist.baseUri, segment.url);
    DataSpec dataSpec = new DataSpec(chunkUri, 0, C.LENGTH_UNBOUNDED, null);

    long startTimeUs = segment.startTimeUs;
    long endTimeUs = startTimeUs + (long) (segment.durationSecs * 1000000);
    int nextChunkMediaSequence = chunkMediaSequence + 1;

    if (mediaPlaylistWasLive) {
      if (queue.isEmpty()) {
        liveStartTimeUs = startTimeUs;
        startTimeUs = 0;
        endTimeUs -= liveStartTimeUs;
      } else {
        startTimeUs -= liveStartTimeUs;
        endTimeUs -= liveStartTimeUs;
      }
    } else {
      // Not live.
      if (chunkIndex == mediaPlaylist.segments.size() - 1) {
        nextChunkMediaSequence = -1;
      }
    }

    out.chunk = new TsChunk(dataSource, dataSpec, 0, startTimeUs, endTimeUs, nextChunkMediaSequence,
        segment.discontinuity);
  }

  private boolean shouldRerequestMediaPlaylist() {
    // Don't re-request media playlist more often than one-half of the target duration.
    long timeSinceLastMediaPlaylistLoadMs =
        SystemClock.elapsedRealtime() - lastMediaPlaylistLoadTimeMs;
    return timeSinceLastMediaPlaylistLoadMs >= (mediaPlaylist.targetDurationSecs * 1000) / 2;
  }

  private int getLiveStartChunkMediaSequence() {
    // For live start playback from the third chunk from the end.
    int chunkIndex = mediaPlaylist.segments.size() > 3 ? mediaPlaylist.segments.size() - 3 : 0;
    return chunkIndex + mediaPlaylist.mediaSequence;
  }

  private MediaPlaylistChunk newMediaPlaylistChunk() {
    Uri mediaPlaylistUri = Util.getMergedUri(masterPlaylist.baseUri,
        masterPlaylist.variants.get(0).url);
    DataSpec dataSpec = new DataSpec(mediaPlaylistUri, 0, C.LENGTH_UNBOUNDED, null);
    Uri mediaPlaylistBaseUri = Util.parseBaseUri(mediaPlaylistUri.toString());
    return new MediaPlaylistChunk(dataSource, dataSpec, 0, mediaPlaylistBaseUri);
  }

  private class MediaPlaylistChunk extends HlsChunk {

    private final Uri baseUri;

    public MediaPlaylistChunk(DataSource dataSource, DataSpec dataSpec, int trigger, Uri baseUri) {
      super(dataSource, dataSpec, trigger);
      this.baseUri = baseUri;
    }

    @Override
    protected void consumeStream(NonBlockingInputStream stream) throws IOException {
      byte[] data = new byte[(int) stream.getAvailableByteCount()];
      stream.read(data, 0, data.length);
      lastMediaPlaylistLoadTimeMs = SystemClock.elapsedRealtime();
      mediaPlaylist = mediaPlaylistParser.parse(
          new ByteArrayInputStream(data), null, null, baseUri);
      mediaPlaylistWasLive |= mediaPlaylist.live;
    }

  }

}
