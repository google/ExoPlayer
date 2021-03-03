package com.google.android.exoplayer2.demo;

import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.crypto.AesCipherDataSink;
import com.google.android.exoplayer2.upstream.crypto.AesCipherDataSource;
import com.google.android.exoplayer2.util.Util;

public class ExoplayerEncryption {

  private static final byte[] KEY = Util.getUtf8Bytes("testKey:12345678");

  public static DataSink.Factory dataSinkFactory(Cache downloadCache) {
    return () -> new AesCipherDataSink(KEY, new CacheDataSink(downloadCache, Long.MAX_VALUE),
        new byte[1024 * 32]);
  }

  public static DataSource.Factory dataSourceFactory(DataSource.Factory upstream) {
    return () -> new AesCipherDataSource(KEY, upstream.createDataSource());
  }
}
