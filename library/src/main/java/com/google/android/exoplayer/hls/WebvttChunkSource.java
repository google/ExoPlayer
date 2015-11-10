/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.chunk.Chunk;
import com.google.android.exoplayer.chunk.ChunkOperationHolder;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.DataChunk;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.chunk.MediaChunk;
import com.google.android.exoplayer.chunk.SingleSampleMediaChunk;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.HttpDataSource;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.UriUtil;
import com.google.android.exoplayer.util.Util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.android.exoplayer.ExoPlayer.TRACK_DEFAULT;

/**
 * A {@link ChunkSource} for Webvtt playlist.
 * <p>
 * This implementation supports for both VOD and Live streaming subtitles.
 * <p>
 * Timestamp offset is required for Live and VOD with initial PTS > 0
 */
public class WebvttChunkSource implements ChunkSource, TimestampOffsetListener {

    private static final String TAG = "WebvttChunkSource";

    private final DataSource dataSource;
    private final HlsPlaylistParser playlistParser;

    private HlsMediaPlaylist mediaPlaylist;
    private boolean live;
    private long lastPlaylistLoadTimeMs;
    private int lastMediaSequence;
    private boolean stale;

    private ArrayList<ExposedTrack> tracks;
    private ExposedTrack enabledTrack;
    private HlsMasterPlaylist playlist;
    private boolean prepareCalled;
    private long timestampOffsetUs;

    public WebvttChunkSource(DataSource dataSource, HlsMasterPlaylist playlist) {
        this.dataSource = dataSource;
        this.playlist = playlist;
        lastMediaSequence = -1;
        tracks = new ArrayList<>();
        playlistParser = new HlsPlaylistParser();
    }

    @Override
    public void maybeThrowError() throws IOException {
        // Do nothing?
    }

    @Override
    public boolean prepare() {
        //select the default lang
        if (!prepareCalled) {
            prepareCalled = true;
            List<Subtitle> subtitles = playlist.subtitles;
            for(Subtitle subtitle: subtitles) {
                Format format = new Format(subtitle.name, MimeTypes.TEXT_VTT, MediaFormat.NO_VALUE,
                        MediaFormat.NO_VALUE, MediaFormat.NO_VALUE, MediaFormat.NO_VALUE,
                        MediaFormat.NO_VALUE, MediaFormat.NO_VALUE, subtitle.language);

                // timestampOffset value is unknown at this time, set 0 as default
                // subsampleOffset must be set to 0, if not it will be set to OFFSET_SAMPLE_RELATIVE
                // which will treated as relative time, as a result it won't work for WebVtt
                // see SubtitleParserHelper for details
                MediaFormat trackFormat = MediaFormat.createTextFormat(format.id, format.mimeType,
                        format.bitrate, C.MATCH_LONGEST_US, format.language, 0);
                if (subtitle.isDefault) {
                    tracks.add(TRACK_DEFAULT, new ExposedTrack(trackFormat, format, subtitle.uri));
                } else {
                    tracks.add(new ExposedTrack(trackFormat, format, subtitle.uri));
                }
            }
        }
        return true;
    }

    @Override
    public int getTrackCount() {
        return tracks.size();
    }

    @Override
    public MediaFormat getFormat(int track) {
        return tracks.get(track).trackFormat;
    }

    @Override
    public void enable(int track) {
        //by this time the timestampoffset has the right value
        if (timestampOffsetUs != 0) {
            // update the offset value if not 0.
            // this is required for Live streaming and PTS offset > 0
            for (ExposedTrack exposedTrack: tracks) {
                MediaFormat newMediaFormat =
                        exposedTrack.trackFormat.copyWithSubsampleOffsetUs(timestampOffsetUs);
                exposedTrack.trackFormat = newMediaFormat;
            }
        }
        enabledTrack = tracks.get(track);
        lastMediaSequence = -1;
    }

    @Override
    public void disable(List<? extends MediaChunk> queue) {
        //set mediaplaylist to null to force downloading the new mediaplaylist.
        mediaPlaylist = null;
    }

    @Override
    public void continueBuffering(long playbackPositionUs) {
        //Do nothing
    }

    @Override
    public void getChunkOperation(List<? extends MediaChunk> queue, long seekPositionUs,
                                  long playbackPositionUs, ChunkOperationHolder out) {

        if (mediaPlaylist == null) {
            out.chunk = newMediaPlaylistChunk();
            return;
        }

        //else load the vtt file
        MediaChunk previousChunk = null;
        if (queue.size() > 0) {
            previousChunk = queue.get(0);
        }

        int chunkMediaSequence;
        if (live) {
            if (previousChunk == null) {
                chunkMediaSequence = getLiveStartChunkMediaSequence();
            } else {
                chunkMediaSequence = previousChunk.chunkIndex + 1;
                if (chunkMediaSequence < mediaPlaylist.mediaSequence) {
                    // If the chunk is no longer in the playlist. Skip ahead and start again.
                    chunkMediaSequence = getLiveStartChunkMediaSequence();
                }
            }
        } else {
            if (previousChunk == null) {
                chunkMediaSequence = Util.binarySearchFloor(mediaPlaylist.segments, seekPositionUs, true,
                        true) + mediaPlaylist.mediaSequence;
            } else {
                chunkMediaSequence = previousChunk.chunkIndex + 1;
            }
        }

        int chunkIndex = chunkMediaSequence - mediaPlaylist.mediaSequence;
        if (queue.size() > 1) {
            //if the chunk is in the queue, don't query again
            //this happen mostly on Live use case
            MediaChunk mediaChunk = queue.get(1);  //usually there's only 2 chunks in the queue, never seen more
            if (mediaChunk.chunkIndex == chunkMediaSequence) {
                return;
            }
        }

        if (out.chunk != null && out.chunk instanceof MediaChunk) {
            //if the chunk is not downloaded yet, and it request the same, don't create new chunk
            //this happen mostly on VOD use case
            if (((MediaChunk)out.chunk).chunkIndex == chunkMediaSequence) {
                return;
            }
        }

        if (mediaPlaylist.live) {
            // refresh media playlist every segment duration
            // during stale, refresh every half of segment duration
            if (shouldRerequestMediaPlaylist()) {
                out.chunk = newMediaPlaylistChunk();
                return;
            }
        }

        if (chunkIndex >= mediaPlaylist.segments.size()) {
            // reaching the last chunk
            if (!mediaPlaylist.live) {
                out.endOfStream = true;
            }
            return;
        }

        HlsMediaPlaylist.Segment segment = mediaPlaylist.segments.get(chunkIndex);
        Uri chunkUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, segment.url);

        // Configure the data source and spec for the chunk.
        DataSpec dataSpec = new DataSpec(chunkUri, DataSpec.FLAG_ALLOW_GZIP);
        long startTimeUs;
        if (live) {
            if (previousChunk == null) {
                startTimeUs =  0;
            } else {
                startTimeUs = previousChunk.endTimeUs;
            }
        } else /* Not live */ {
            startTimeUs = segment.startTimeUs;
        }

        long endTimeUs = startTimeUs + (long) (segment.durationSecs * C.MICROS_PER_SECOND);
        out.chunk = newMediaChunk(dataSpec, startTimeUs, endTimeUs, chunkMediaSequence);
    }

    private boolean shouldRerequestMediaPlaylist() {
        // Don't re-request media playlist more often than half of the target duration.
        long timeSinceLastMediaPlaylistLoadMs =
                SystemClock.elapsedRealtime() - lastPlaylistLoadTimeMs;
        int targetDurationMs = mediaPlaylist.targetDurationSecs * 1000;
        int threshold = stale ? targetDurationMs / 2 : targetDurationMs;
        return timeSinceLastMediaPlaylistLoadMs >= threshold;
    }


    private MediaPlaylistChunk newMediaPlaylistChunk() {
        Uri mediaPlaylistUri = UriUtil.resolveToUri(playlist.baseUri, enabledTrack.uri);
        DataSpec dataSpec = new DataSpec(mediaPlaylistUri, 0, C.LENGTH_UNBOUNDED, null,
                DataSpec.FLAG_ALLOW_GZIP);
        return new MediaPlaylistChunk(dataSource, dataSpec, playlistParser,
                mediaPlaylistUri.toString());

    }

    private Chunk newMediaChunk(DataSpec dataSpec, long startTimeUs, long endTimeUs, int chunkIndex) {
        return new SingleSampleMediaChunk(dataSource, dataSpec, Chunk.TRIGGER_UNSPECIFIED,
                enabledTrack.representationFormat, startTimeUs, endTimeUs, chunkIndex, enabledTrack.trackFormat,
                null, Chunk.NO_PARENT_ID);
    }

    private int getLiveStartChunkMediaSequence() {
        int chunkIndex = mediaPlaylist.segments.size() > 3? mediaPlaylist.segments.size() - 3 : 0;
        return chunkIndex + mediaPlaylist.mediaSequence;
    }

    @Override
    public void onTimestampOffsetRead(long timestampOffsetUs) {
        this.timestampOffsetUs = timestampOffsetUs;
    }

    private static class MediaPlaylistChunk extends DataChunk {

        private final HlsPlaylistParser playlistParser;
        private String playlistUrl;
        private HlsMediaPlaylist result;

        public MediaPlaylistChunk(DataSource dataSource, DataSpec dataSpec,
                                  HlsPlaylistParser playlistParser, String playlistUrl) {
            super(dataSource, dataSpec, Chunk.TYPE_MANIFEST, Chunk.TRIGGER_UNSPECIFIED, null,
                    Chunk.NO_PARENT_ID, null);
            this.playlistParser = playlistParser;
            this.playlistUrl = playlistUrl;
        }

        @Override
        protected void consume(byte[] data, int limit) throws IOException {
            result = (HlsMediaPlaylist) playlistParser.parse(playlistUrl,
                    new ByteArrayInputStream(data, 0, limit));
        }

        public HlsMediaPlaylist getResult() {
            return result;
        }
    }

    @Override
    public void onChunkLoadCompleted(Chunk chunk) {
        if (chunk instanceof MediaPlaylistChunk) {
            lastPlaylistLoadTimeMs = SystemClock.elapsedRealtime();
            MediaPlaylistChunk mediaPlaylistChunk = (MediaPlaylistChunk) chunk;
            mediaPlaylist = mediaPlaylistChunk.getResult();
            live = mediaPlaylist.live;
            stale = lastMediaSequence >= mediaPlaylist.mediaSequence;
            if (stale) {
                Log.w(TAG, "WebVtt playlist stale detected! lastMediaSequence: " + lastMediaSequence
                        + "; mediaSequence: " + mediaPlaylist.mediaSequence);
            }
            lastMediaSequence = mediaPlaylist.mediaSequence;
        }
        //else the vtt chunk, no special handling to vtt chunk
    }

    @Override
    public void onChunkLoadError(Chunk chunk, Exception e) {
        if ((chunk.bytesLoaded() == 0)
                && ((chunk instanceof SingleSampleMediaChunk) || (chunk instanceof MediaPlaylistChunk))
                && (e instanceof HttpDataSource.InvalidResponseCodeException)) {
            HttpDataSource.InvalidResponseCodeException responseCodeException = (HttpDataSource.InvalidResponseCodeException) e;
            int responseCode = responseCodeException.responseCode;
            if (chunk instanceof SingleSampleMediaChunk) {
                Log.w(TAG, "Vtt chunk load failed (" + responseCode + "): " + chunk.dataSpec.uri);
            } else {
                Log.w(TAG, "Vtt playlist load failed (" + responseCode + "): " + chunk.dataSpec.uri);
            }
        } else {
            Log.w(TAG, "Chunk load failed: " + e.getMessage());
        }
    }

    private static final class ExposedTrack {
        private MediaFormat trackFormat;
        private final Format representationFormat;
        private final String uri;

        public ExposedTrack(MediaFormat trackFormat, Format representationFormat, String uri) {
            this.trackFormat = trackFormat;
            this.representationFormat = representationFormat;
            this.uri = uri;
        }
    }
}