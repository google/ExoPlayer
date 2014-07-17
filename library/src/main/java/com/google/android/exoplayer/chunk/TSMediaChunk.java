package com.google.android.exoplayer.chunk;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.parser.ts.TSExtractor;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.Assertions;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public class TSMediaChunk extends MediaChunk {

    private final TSExtractor extractor;
    private ArrayList<MediaFormat> mediaFormatList;
    private ArrayList<Integer> trackList;
    private FileOutputStream debugFile;

    /**
     * Constructor for a chunk of media samples.
     * @param dataSource     A {@link com.google.android.exoplayer.upstream.DataSource} for loading the data.
     * @param trackList
     * @param mediaFormatList
     * @param dataSpec       Defines the data to be loaded.
     * @param format         The format of the stream to which this chunk belongs.
     * @param nextChunkIndex The index of the next chunk, or -1 if this is the last chunk.
     */
    public TSMediaChunk(DataSource dataSource, ArrayList<Integer> trackList, ArrayList<MediaFormat> mediaFormatList, DataSpec dataSpec, Format format, long startTimeUs, long endTimeUs, int nextChunkIndex) {
        super(dataSource, dataSpec, format, 0, startTimeUs, startTimeUs, nextChunkIndex);
        this.mediaFormatList = mediaFormatList;
        this.extractor = new TSExtractor();
        this.trackList = trackList;
    }

    @Override
    public boolean seekTo(long positionUs, boolean allowNoop) {
        return false;
    }

    @Override
    public boolean read(int track, SampleHolder holder) throws ParserException {

        NonBlockingInputStream inputStream = getNonBlockingInputStream();
        Assertions.checkState(inputStream != null);
        int result = extractor.read(trackList.get(track), inputStream, holder);
        return (result == TSExtractor.RESULT_READ_SAMPLE_FULL);
    }

    @Override
    public MediaFormat getMediaFormat(int track) {
        return mediaFormatList.get(track);
    }

    @Override
    public Map<UUID, byte[]> getPsshInfo() {
        return null;
    }
}
