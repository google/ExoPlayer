package com.google.android.exoplayer.demo.player;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.UriDataSource;

import net.butterflytv.rtmp_client.RtmpClient;

import java.io.IOException;

/**
 * Created by faraklit on 08.01.2016.
 */
public class RtmpDataSource implements UriDataSource {


    private final RtmpClient rtmpClient;
    private String uri;

    public RtmpDataSource() {
        rtmpClient = new RtmpClient();
    }
    @Override
    public String getUri() {
        return uri;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        rtmpClient.open(dataSpec.uri.toString(), false);
        return C.LENGTH_UNBOUNDED;
    }

    @Override
    public void close() throws IOException {
        rtmpClient.close();
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        return rtmpClient.read(buffer, offset, readLength);

    }
}
