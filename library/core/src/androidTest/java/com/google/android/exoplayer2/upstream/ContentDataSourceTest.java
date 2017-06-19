package com.google.android.exoplayer2.upstream;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.test.InstrumentationTestCase;

import static com.google.android.exoplayer2.upstream.AndroidDataSourceConstants.NULL_DESCRIPTOR_URI;
import static com.google.android.exoplayer2.upstream.AndroidDataSourceConstants.SAMPLE_MP4_BYTES;
import static com.google.android.exoplayer2.upstream.AndroidDataSourceConstants.SAMPLE_MP4_PATH;
import static com.google.android.exoplayer2.upstream.AndroidDataSourceConstants.TEST_DATA_PROVIDER_AUTHORITY;

public class ContentDataSourceTest extends InstrumentationTestCase {

  public void testValidContentDataSource() throws Exception {
    Context context = getInstrumentation().getContext();
    ContentDataSource dataSource = new ContentDataSource(context);
    Uri contentUri = new Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(TEST_DATA_PROVIDER_AUTHORITY)
            .path(SAMPLE_MP4_PATH).build();
    DataSpec dataSpec = new DataSpec(contentUri);
    long sourceLengthBytes = dataSource.open(dataSpec);

    assertEquals(SAMPLE_MP4_BYTES, sourceLengthBytes);
  }

  public void testNullContentDataSource() throws Exception {
    Context context = getInstrumentation().getContext();
    ContentDataSource dataSource = new ContentDataSource(context);
    DataSpec dataSpec = new DataSpec(NULL_DESCRIPTOR_URI);

    try {
      dataSource.open(dataSpec);
      fail("Expected exception not thrown.");
    } catch (ContentDataSource.ContentDataSourceException e) {
      // Expected.
    }
  }
}
