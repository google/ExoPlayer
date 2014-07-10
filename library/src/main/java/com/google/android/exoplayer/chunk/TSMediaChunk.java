package com.google.android.exoplayer.chunk;

import android.os.Environment;
import android.util.Log;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.parser.ts.TSExtractor;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.Assertions;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.Map;
import java.util.UUID;

public class TSMediaChunk extends MediaChunk {

    private final TSExtractor extractor;
    private MediaFormat mediaFormat;
    private FileOutputStream debugFile;

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
        this.extractor = new TSExtractor();
        File root = Environment.getExternalStorageDirectory();
        try {
            debugFile = new FileOutputStream(new File(root, "raw" + nextChunkIndex + ".h264"));
        } catch (Exception e) {
            Log.d("TSMediaChunk", "error");
        }
    }

    @Override
    public boolean seekTo(long positionUs, boolean allowNoop) {
        return false;
    }

    @Override
    public boolean read(SampleHolder holder) throws ParserException {

        NonBlockingInputStream inputStream = getNonBlockingInputStream();
        Assertions.checkState(inputStream != null);
        int result = extractor.read(inputStream, holder);
        if (result == TSExtractor.RESULT_READ_SAMPLE_FULL) {
            try {
                for (int i = 0; i < holder.data.position(); i++) {
                    debugFile.write(holder.data.get(i));
                }
            } catch (Exception e) {
                Log.d("TSMediaChunk", "error");
            }
        }
        return (result == TSExtractor.RESULT_READ_SAMPLE_FULL);
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
