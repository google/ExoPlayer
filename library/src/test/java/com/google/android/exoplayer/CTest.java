package com.google.android.exoplayer;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaExtractor;

import junit.framework.TestCase;

/**
 * Unit test for {@link C}.
 */
public class CTest extends TestCase {

  @SuppressLint("InlinedApi")
  public static final void testContants() {
    // Sanity check that constant values match those defined by the platform.
    assertEquals(MediaExtractor.SAMPLE_FLAG_SYNC, C.SAMPLE_FLAG_SYNC);
    assertEquals(MediaExtractor.SAMPLE_FLAG_ENCRYPTED, C.SAMPLE_FLAG_ENCRYPTED);
    assertEquals(MediaCodec.CRYPTO_MODE_AES_CTR, C.CRYPTO_MODE_AES_CTR);
  }

}
