package com.google.android.exoplayer2.demo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import com.google.android.exoplayer2.offline.Downloader;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.source.hls.offline.HlsDownloader;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSinkFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import java.io.File;
import java.io.IOException;

public class DownloadActivity extends Activity {

  private Thread downloaderThread;
  private HlsDownloader downloader;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_download);

    Intent intent = getIntent();
    Uri[] uris = new Uri[] { intent.getData() };
    File dir = new File(this.getFilesDir(), "dl");
    dir.mkdirs();
    Cache cache = new SimpleCache(dir, new NoOpCacheEvictor());

    DownloaderConstructorHelper constructor =
        new DownloaderConstructorHelper(cache,
            new DefaultHttpDataSourceFactory("ExoPlayer", null),
            null,
            new CacheDataSinkFactory(cache, 1024 * 5),
            null);
    downloader = new HlsDownloader(uris[0], constructor);
    downloaderThread = new Thread(new Runnable() {
      @Override public void run() {
        try {
          Log.d("downloader", "start Download");
          downloader.download(new Downloader.ProgressListener() {
            @Override
            public void onDownloadProgress(Downloader downloader, float downloadPercentage,
                long downloadedBytes) {
              Log.d("downloader", downloadPercentage + "%");
            }
          });
        } catch (IOException e) {
          e.printStackTrace();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    });
    downloaderThread.start();
  }
}
