package com.google.android.exoplayer2.upstream;

import android.content.Context;
import android.net.Uri;
import android.test.InstrumentationTestCase;

public class AndroidDataSourceTest extends InstrumentationTestCase {

  private static final long SAMPLE_MP4_BYTES = 101597;
  private static final String SAMPLE_MP4_PATH = "/mp4/sample.mp4";

  public void testAssetDataSource() throws Exception {
    final Context context = getInstrumentation().getContext();
    AssetDataSource dataSource = new AssetDataSource(context);
    Uri assetUri = Uri.parse("file:///android_asset" + SAMPLE_MP4_PATH);
    DataSpec dataSpec = new DataSpec(assetUri);
    long sourceLengthBytes = dataSource.open(dataSpec);

    assertEquals(SAMPLE_MP4_BYTES, sourceLengthBytes);
  }

  public void testContentDataSource() throws Exception {
    Context context = getInstrumentation().getContext();
    ContentDataSource dataSource = new ContentDataSource(context);
    Uri contentUri = Uri.parse("content://exoplayer" + SAMPLE_MP4_PATH);
    DataSpec dataSpec = new DataSpec(contentUri);
    long sourceLengthBytes = dataSource.open(dataSpec);

    assertEquals(SAMPLE_MP4_BYTES, sourceLengthBytes);
  }
}
