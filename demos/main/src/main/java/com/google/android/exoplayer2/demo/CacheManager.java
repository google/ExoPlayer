package com.google.android.exoplayer2.demo;

import android.content.Context;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;

public class CacheManager {

  public static CacheDataSource.Factory playbackDataSourceFactory(Context context,
      DataSource.Factory upstream) {
    Cache downloadCache = DemoUtil.getDownloadCache(context);
    CacheDataSource.Factory playbackCacheFactory = createPlaybackCacheFactory(context, upstream);
    return new CacheDataSource.Factory()
        .setCache(downloadCache)
        .setUpstreamDataSourceFactory(playbackCacheFactory)
        // No writing to download cache when doing playback
        .setCacheWriteDataSinkFactory(null)
        // But if we're reading, still attempt to read encrypted content, using the playback cache as upstream
        .setCacheReadDataSourceFactory(ExoplayerEncryption.dataSourceFactory(playbackCacheFactory));
  }

  private static CacheDataSource.Factory createPlaybackCacheFactory(Context context,
      DataSource.Factory upstream) {
    Cache playbackCache = DemoUtil.getPlaybackCache(context);
    return new CacheDataSource.Factory()
        .setCache(playbackCache)
        .setUpstreamDataSourceFactory(upstream)
        // Write encrypted to the playback cache
        .setCacheWriteDataSinkFactory(ExoplayerEncryption.dataSinkFactory(playbackCache))
        // Read encrypted from disk, using the upstream source as fallback
        .setCacheReadDataSourceFactory(ExoplayerEncryption.dataSourceFactory(upstream))
        .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE);
  }
}
