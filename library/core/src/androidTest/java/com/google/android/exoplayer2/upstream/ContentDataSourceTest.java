package com.google.android.exoplayer2.upstream;

import android.content.Context;
import android.net.Uri;
import android.test.InstrumentationTestCase;

import static com.google.android.exoplayer2.upstream.AndroidDataSourceConstants.SAMPLE_MP4_BYTES;
import static com.google.android.exoplayer2.upstream.AndroidDataSourceConstants.SAMPLE_MP4_PATH;

public class ContentDataSourceTest extends InstrumentationTestCase {

  public void testContentDataSource() throws Exception {
    Context context = getInstrumentation().getContext();
    ContentDataSource dataSource = new ContentDataSource(context);
    Uri contentUri = Uri.parse("content://exoplayer" + SAMPLE_MP4_PATH);
    DataSpec dataSpec = new DataSpec(contentUri);
    long sourceLengthBytes = dataSource.open(dataSpec);

    assertEquals(SAMPLE_MP4_BYTES, sourceLengthBytes);
  }
}
