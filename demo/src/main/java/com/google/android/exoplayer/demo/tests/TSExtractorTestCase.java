package com.google.android.exoplayer.demo.tests;

import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.hls.Extractor;
import com.google.android.exoplayer.hls.Packet;
import com.google.android.exoplayer.parser.ts.TSExtractor;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSourceStream;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.FileDataSource;

public class TSExtractorTestCase extends TestCase {
  @Override
  void run() throws Exception {
    Uri uri = Uri.parse("file:///sdcard/all.ts");
    DataSpec dataSpec = new DataSpec(uri, 0, DataSpec.LENGTH_UNBOUNDED, null);
    DataSource dataSource = new FileDataSource();
    DataSourceStream inputStream = new DataSourceStream(dataSource, dataSpec, new BufferPool(64*1024));
    inputStream.load();
    Extractor extractor = new TSExtractor(dataSource);
    int type = Packet.TYPE_AUDIO;
    int[] counter = new int[2];

    SampleHolder sampleHolder = new SampleHolder(false);
    long start = SystemClock.uptimeMillis();
    while (true) {
      Packet s = extractor.read();
      if (s == null) {
        break;
      }
      counter[s.type]++;
    }
    Log.d("TSExtractorTestCase", String.format("processed %d audio frames and %d video frames in %d ms", counter[Packet.TYPE_AUDIO],
            counter[Packet.TYPE_VIDEO], SystemClock.uptimeMillis() - start));

  }
}
