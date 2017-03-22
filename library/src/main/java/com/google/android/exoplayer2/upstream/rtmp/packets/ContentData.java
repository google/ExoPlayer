package com.google.android.exoplayer2.upstream.rtmp.packets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.google.android.exoplayer2.upstream.rtmp.Util;

/**
 * Content (audio/video) data packet base
 *
 * @author francois
 */
public abstract class ContentData extends RtmpPacket {

  protected byte[] data;

  public ContentData(RtmpHeader header) {
    super(header);
  }

  public byte[] getData() {
    return data;
  }

  public void setData(byte[] data) {
    this.data = data;
  }

  @Override
  public void readBody(InputStream in) throws IOException {
    data = new byte[this.header.getPacketLength()];
    Util.readBytesUntilFull(in, data);
  }

  /**
   * Method is public for content (audio/video)
   * Write this packet body without chunking;
   * useful for dumping audio/video streams
   */
  @Override
  public void writeBody(OutputStream out) throws IOException {
    out.write(data);
  }
}
