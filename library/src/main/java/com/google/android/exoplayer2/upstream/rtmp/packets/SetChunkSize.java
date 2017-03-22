package com.google.android.exoplayer2.upstream.rtmp.packets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.google.android.exoplayer2.upstream.rtmp.Util;
import com.google.android.exoplayer2.upstream.rtmp.io.ChunkStreamInfo;

/**
 * A "Set chunk size" RTMP message, received on chunk stream ID 2 (control channel)
 *
 * @author francois
 */
public class SetChunkSize extends RtmpPacket {

  private int chunkSize;

  public SetChunkSize(RtmpHeader header) {
    super(header);
  }

  public SetChunkSize(int chunkSize) {
    super(new RtmpHeader(RtmpHeader.ChunkType.TYPE_1_RELATIVE_LARGE, ChunkStreamInfo.RTMP_CID_PROTOCOL_CONTROL, RtmpHeader.MessageType.SET_CHUNK_SIZE));
    this.chunkSize = chunkSize;
  }

  public int getChunkSize() {
    return chunkSize;
  }

  public void setChunkSize(int chunkSize) {
    this.chunkSize = chunkSize;
  }

  @Override
  public void readBody(InputStream in) throws IOException {
    // Value is received in the 4 bytes of the body
    chunkSize = Util.readUnsignedInt32(in);
  }

  @Override
  protected void writeBody(OutputStream out) throws IOException {
    Util.writeUnsignedInt32(out, chunkSize);
  }
}
