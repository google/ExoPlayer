package com.google.android.exoplayer2.upstream;

import android.content.Context;
import android.net.Uri;
import android.test.InstrumentationTestCase;

import static com.google.android.exoplayer2.upstream.AndroidDataSourceConstants.SAMPLE_MP4_BYTES;
import static com.google.android.exoplayer2.upstream.AndroidDataSourceConstants.SAMPLE_MP4_PATH;

public class AssetDataSourceTest extends InstrumentationTestCase {

  public void testAssetDataSource() throws Exception {
    final Context context = getInstrumentation().getContext();
    AssetDataSource dataSource = new AssetDataSource(context);
    Uri assetUri = Uri.parse("file:///android_asset" + SAMPLE_MP4_PATH);
    DataSpec dataSpec = new DataSpec(assetUri);
    long sourceLengthBytes = dataSource.open(dataSpec);

    assertEquals(SAMPLE_MP4_BYTES, sourceLengthBytes);
  }
}
