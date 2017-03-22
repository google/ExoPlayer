package com.google.android.exoplayer2.upstream.rtmp.packets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.google.android.exoplayer2.upstream.rtmp.Util;
import com.google.android.exoplayer2.upstream.rtmp.io.ChunkStreamInfo;

/**
 * A "Abort" RTMP control message, received on chunk stream ID 2 (control channel)
 *
 * @author francois
 */
public class Abort extends RtmpPacket {

  private int chunkStreamId;

  public Abort(RtmpHeader header) {
    super(header);
  }

  public Abort(int chunkStreamId) {
    super(new RtmpHeader(RtmpHeader.ChunkType.TYPE_1_RELATIVE_LARGE, ChunkStreamInfo.RTMP_CID_PROTOCOL_CONTROL, RtmpHeader.MessageType.SET_CHUNK_SIZE));
    this.chunkStreamId = chunkStreamId;
  }

  /**
   * @return the ID of the chunk stream to be aborted
   */
  public int getChunkStreamId() {
    return chunkStreamId;
  }

  /**
   * Sets the ID of the chunk stream to be aborted
   */
  public void setChunkStreamId(int chunkStreamId) {
    this.chunkStreamId = chunkStreamId;
  }

  @Override
  public void readBody(InputStream in) throws IOException {
    // Value is received in the 4 bytes of the body
    chunkStreamId = Util.readUnsignedInt32(in);
  }

  @Override
  protected void writeBody(OutputStream out) throws IOException {
    Util.writeUnsignedInt32(out, chunkStreamId);
  }
}
