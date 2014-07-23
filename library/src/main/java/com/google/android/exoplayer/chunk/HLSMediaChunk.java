package com.google.android.exoplayer.chunk;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.parser.ts.TSExtractor;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSourceStream;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.Assertions;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public class HLSMediaChunk extends MediaChunk {

    private TSExtractor extractor;
    private MediaFormat videoMediaFormat;
    private ArrayList<Integer> trackList;

    /**
     * Constructor for a chunk of media samples.
     * @param dataSource     A {@link com.google.android.exoplayer.upstream.DataSource} for loading the data.
     * @param trackList
     * @param videoMediaFormat
     * @param dataSpec       Defines the data to be loaded.
     * @param format         The format of the stream to which this chunk belongs.
     * @param nextChunkIndex The index of the next chunk, or -1 if this is the last chunk.
     */
    public HLSMediaChunk(DataSource dataSource, ArrayList<Integer> trackList, MediaFormat videoMediaFormat, DataSpec dataSpec, Format format, long startTimeUs, long endTimeUs, int nextChunkIndex) {
        super(dataSource, dataSpec, format, 0, startTimeUs, startTimeUs, nextChunkIndex);
        this.videoMediaFormat = videoMediaFormat;
        this.trackList = trackList;
    }

    @Override
    public final void init(Allocator allocator) {
        super.init(allocator);
        NonBlockingInputStream inputStream = getNonBlockingInputStream();
        Assertions.checkState(inputStream != null);
        this.extractor = new TSExtractor(inputStream);
    }

    @Override
    public boolean seekTo(long positionUs, boolean allowNoop) {
        return false;
    }

    @Override
    public boolean read(int track, SampleHolder holder) throws ParserException {

        int result = extractor.read(trackList.get(track), holder);
        return (result == TSExtractor.RESULT_READ_SAMPLE_FULL);
    }

    @Override
    public MediaFormat getMediaFormat(int track) {
        int type = trackList.get(track);
        if (type == TSExtractor.TYPE_VIDEO) {
            return videoMediaFormat;
        } else {
            return extractor.getAudioMediaFormat();
        }
    }

    @Override
    public Map<UUID, byte[]> getPsshInfo() {
        return null;
    }

    public boolean isReadFinished() {

        return extractor.isReadFinished();
    }
}
