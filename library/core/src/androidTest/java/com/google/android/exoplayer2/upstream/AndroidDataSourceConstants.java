package com.google.android.exoplayer2.upstream;

import android.content.ContentResolver;
import android.net.Uri;

final class AndroidDataSourceConstants {
  static final long SAMPLE_MP4_BYTES = 101597;
  static final String SAMPLE_MP4_PATH = "/mp4/sample.mp4";
  static final String TEST_DATA_PROVIDER_AUTHORITY = "exoplayer";
  static final Uri NULL_DESCRIPTOR_URI = new Uri.Builder()
          .scheme(ContentResolver.SCHEME_CONTENT)
          .authority(TEST_DATA_PROVIDER_AUTHORITY)
          .build();

  private AndroidDataSourceConstants() {}
}
