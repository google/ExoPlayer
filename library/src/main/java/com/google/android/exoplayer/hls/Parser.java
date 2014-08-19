package com.google.android.exoplayer.hls;

/**
 * Created by martin on 18/08/14.
 */
public abstract class Parser {
  // returns null if no more packet can be output
  public abstract Packet read();
  public abstract void pushPacket(Packet packet);
}
