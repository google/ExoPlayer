package com.google.android.exoplayer.metadata.frame;

/**
 * Binary ID3 frame
 */
public class BinaryFrame extends Id3Frame {

  public final byte[] data;

  public BinaryFrame(String type, byte[] data) {
    super(type);
    this.data = data;
  }
}
