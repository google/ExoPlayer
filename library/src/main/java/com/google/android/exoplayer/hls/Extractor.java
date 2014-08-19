package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.ParserException;

import java.util.ArrayList;
import java.util.LinkedList;

public abstract class Extractor {

  public static final int STREAM_TYPE_NONE = -1;
  public static final int STREAM_TYPE_AAC_ADTS = 0xf;
  public static final int STREAM_TYPE_H264 = 0x1b;
  public static final int STREAM_TYPE_MPEG_AUDIO = 0x3;

  /*
   * return null if end of stream
   */
  abstract public Packet read()
          throws ParserException;

  abstract public int getStreamType(int type);

  public void release() {};
}