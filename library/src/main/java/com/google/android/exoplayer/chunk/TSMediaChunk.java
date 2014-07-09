package com.google.android.exoplayer.chunk;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;

import java.util.Map;
import java.util.UUID;

public class TSMediaChunk extends MediaChunk {

    private MediaFormat mediaFormat;

    /**
     * Constructor for a chunk of media samples.
     *
     * @param dataSource     A {@link com.google.android.exoplayer.upstream.DataSource} for loading the data.
     * @param dataSpec       Defines the data to be loaded.
     * @param format         The format of the stream to which this chunk belongs.
     * @param nextChunkIndex The index of the next chunk, or -1 if this is the last chunk.
     */
    public TSMediaChunk(DataSource dataSource, MediaFormat mediaFormat, DataSpec dataSpec, Format format, int nextChunkIndex) {
        super(dataSource, dataSpec, format, 0, 0, 0, nextChunkIndex);
        this.mediaFormat = mediaFormat;
    }

    @Override
    public boolean seekTo(long positionUs, boolean allowNoop) {
        return false;
    }

    @Override
    public boolean read(SampleHolder holder) throws ParserException {
        return false;
    }

    @Override
    public MediaFormat getMediaFormat() {
        return mediaFormat;
    }

    @Override
    public Map<UUID, byte[]> getPsshInfo() {
        return null;
    }
}
