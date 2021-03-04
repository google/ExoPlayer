package com.google.android.exoplayer2.demo;

import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.crypto.AesCipherDataSink;
import com.google.android.exoplayer2.upstream.crypto.AesCipherDataSource;
import com.google.android.exoplayer2.util.Util;

public class ExoplayerEncryption {

  // Toggle this variable to enable / disable decryption. You may need to uninstall app after toggling
  final private static boolean enableEncryption = true;

  private static final byte[] KEY = Util.getUtf8Bytes("testKey:12345678");

  public static DataSink.Factory dataSinkFactory(Cache downloadCache) {
    return () -> {
      if (enableEncryption) {
        return new AesCipherDataSink(KEY, new CacheDataSink(downloadCache, Long.MAX_VALUE),
            new byte[1024 * 32]);
      } else {
        return new CacheDataSink(downloadCache, Long.MAX_VALUE);
      }
    };

  }

  public static DataSource.Factory dataSourceFactory(DataSource.Factory upstream) {
    return () -> {
      if (enableEncryption) {
        return new AesCipherDataSource(KEY, upstream.createDataSource());
      } else {
        return upstream.createDataSource();
      }
    };
  }
}
