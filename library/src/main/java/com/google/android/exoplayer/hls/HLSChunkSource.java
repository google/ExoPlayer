package com.google.android.exoplayer.hls;

import android.net.Uri;
import android.util.SparseArray;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.chunk.Chunk;
import com.google.android.exoplayer.chunk.ChunkOperationHolder;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.MediaChunk;
import com.google.android.exoplayer.chunk.TSMediaChunk;
import com.google.android.exoplayer.parser.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer.parser.mp4.Track;
import com.google.android.exoplayer.parser.mp4.TrackEncryptionBox;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class HLSChunkSource implements ChunkSource {
    private final String baseUrl;
    private final DataSource dataSource;
    private final FormatEvaluator formatEvaluator;
    private final FormatEvaluator.Evaluation evaluation;
    private final Format[] formats;
    private final MediaFormat[] mediaFormats;
    private TrackInfo trackInfo;
    private MainPlaylist mainPlaylist;
    private VariantPlaylist currentVariantPlaylist;

    public HLSChunkSource(String baseUrl, MainPlaylist mainPlaylist, DataSource dataSource, FormatEvaluator formatEvaluator) {
        this.baseUrl = baseUrl;
        this.dataSource = dataSource;
        this.formatEvaluator = formatEvaluator;
        this.evaluation = new FormatEvaluator.Evaluation();
        this.mainPlaylist = mainPlaylist;

        int trackCount = mainPlaylist.entries.size();
        formats = new Format[trackCount];

        for (int i = 0; i < trackCount; i++) {
            MainPlaylist.Entry entry = mainPlaylist.entries.get(i);
            formats[i] = new Format(i, "video/mp2t", entry.width, entry.height, 2, 44100, entry.bps);
        }

        mediaFormats = new MediaFormat[trackCount];

        for (int i =0; i < trackCount; i++) {
            MainPlaylist.Entry entry = mainPlaylist.entries.get(i);
            mediaFormats[i] = MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, MediaFormat.NO_VALUE,
                    entry.width, entry.height, null);
        }

        Arrays.sort(formats, new Format.DecreasingBandwidthComparator());
    }

    @Override
    public TrackInfo getTrackInfo() {
        if (trackInfo == null) {
            trackInfo = new TrackInfo("video/mp2t", (long)mainPlaylist.entries.get(0).variantPlaylist.duration * 1000000);
        }
        return trackInfo;
    }

    @Override
    public void getMaxVideoDimensions(MediaFormat out) {
        out.setMaxVideoDimensions(1920, 1080);
    }

    @Override
    public void enable() {

    }

    @Override
    public void disable(List<MediaChunk> queue) {

    }

    @Override
    public void continueBuffering(long playbackPositionUs) {

    }

    @Override
    public void getChunkOperation(List<? extends MediaChunk> queue, long seekPositionUs, long playbackPositionUs, ChunkOperationHolder out) {
        evaluation.queueSize = queue.size();
        formatEvaluator.evaluate(queue, playbackPositionUs, formats, evaluation);
        Format selectedFormat = evaluation.format;
        out.queueSize = evaluation.queueSize;

        if (selectedFormat == null) {
            out.chunk = null;
            return;
        } else if (out.queueSize == queue.size() && out.chunk != null
                && out.chunk.format.id == evaluation.format.id) {
            // We already have a chunk, and the evaluation hasn't changed either the format or the size
            // of the queue. Do nothing.
            return;
        }

        int nextChunkIndex;

        currentVariantPlaylist = mainPlaylist.entries.get(evaluation.format.id).variantPlaylist;

        if (queue.isEmpty()) {
            nextChunkIndex = currentVariantPlaylist.mediaSequence;
        } else {
            nextChunkIndex = queue.get(out.queueSize - 1).nextChunkIndex;
        }

        if (nextChunkIndex == -1) {
            out.chunk = null;
            return;
        }

        boolean isLastChunk = (nextChunkIndex == currentVariantPlaylist.mediaSequence + currentVariantPlaylist.entries.size() - 1);
        VariantPlaylist.Entry entry = currentVariantPlaylist.entries.get(nextChunkIndex - currentVariantPlaylist.mediaSequence);

        String chunkUrl = Util.makeAbsoluteUrl(currentVariantPlaylist.url, entry.url);
        Uri uri = Uri.parse(chunkUrl);
        long offset = 0;
        DataSpec dataSpec = new DataSpec(uri, offset, -1, null);
        Chunk mediaChunk = new TSMediaChunk(dataSource, mediaFormats[selectedFormat.id], dataSpec, selectedFormat,
                                            (long)(entry.startTime * 1000000), (long)((entry.startTime + entry.extinf) * 1000000),
                                            isLastChunk ? -1 : nextChunkIndex + 1);
        out.chunk = mediaChunk;
    }

    @Override
    public IOException getError() {
        return null;
    }
}
