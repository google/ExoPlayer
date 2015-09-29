package com.google.android.exoplayer.extractor.flv;

/**
 * Created by joliva on 9/26/15.
 */
final class TagHeader {
  public static final int TAG_TYPE_AUDIO = 8;
  public static final int TAG_TYPE_VIDEO = 9;
  public static final int TAG_TYPE_SCRIPT_DATA = 18;

  public int type;
  public int dataSize;
  public long timestamp;
  public int streamId;
}
