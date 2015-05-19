package com.google.android.exoplayer.upstream.supercache;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CacheDataSource implements DataSource {

    private DataSource fallbackDataSource;
    private SuperCache superCache;

    private InputStream inputStream;
    private OutputStream outputStream;
    private boolean fromCache;
    private int bytesReadTotalCount;
    private long bytesTotal;
    private SuperCache.MediaUnit mediaUnit;

    public CacheDataSource(DataSource fallbackDataSource, SuperCache superCache) {
        this.fallbackDataSource = fallbackDataSource;
        this.superCache = superCache;
    }

    //INTERFACE DataSource

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        String key = GenerateKey(dataSpec.uri.toString());
        mediaUnit = superCache.get(key);
        if (mediaUnit.isFinished()) {
            inputStream = mediaUnit.getInputStream();
            inputStream.skip(dataSpec.position);
            fromCache = true;
            bytesTotal = dataSpec.length == C.LENGTH_UNBOUNDED ? inputStream.available() : dataSpec.length;
            return bytesTotal;
        } else {
            outputStream = mediaUnit.getOutputStream();
            fromCache = false;
            return fallbackDataSource.open(dataSpec);
        }
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
        outputStream.close();
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (fromCache) {
            int read = inputStream.read(buffer, offset, (int) Math.min(readLength, bytesTotal - bytesReadTotalCount));
            bytesReadTotalCount += read;
            return read;
        } else {
            int read = fallbackDataSource.read(buffer, offset, readLength);
            bytesReadTotalCount += read;
            outputStream.write(buffer);
            if (bytesReadTotalCount == bytesTotal) {
                mediaUnit.setFinished();
            }
            return read;
        }
    }

    //Config
    //TODO: improve customizability
    private String GenerateKey(String source) {
        return source;
    }
}
