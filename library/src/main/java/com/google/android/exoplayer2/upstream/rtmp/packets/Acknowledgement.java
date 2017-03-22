package com.google.android.exoplayer2.upstream.rtmp.packets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.google.android.exoplayer2.upstream.rtmp.Util;
import com.google.android.exoplayer2.upstream.rtmp.io.ChunkStreamInfo;

/**
 * (Window) Acknowledgement
 * <p>
 * The client or the server sends the acknowledgment to the peer after
 * receiving bytes equal to the window size. The window size is the
 * maximum number of bytes that the sender sends without receiving
 * acknowledgment from the receiver. The server sends the window size to
 * the client after application connects. This message specifies the
 * sequence number, which is the number of the bytes received so far.
 *
 * @author francois
 */
public class Acknowledgement extends RtmpPacket {

  private int sequenceNumber;

  public Acknowledgement(RtmpHeader header) {
    super(header);
  }

  public Acknowledgement(int numBytesReadThusFar) {
    super(new RtmpHeader(RtmpHeader.ChunkType.TYPE_0_FULL, ChunkStreamInfo.RTMP_CID_PROTOCOL_CONTROL, RtmpHeader.MessageType.ACKNOWLEDGEMENT));
    this.sequenceNumber = numBytesReadThusFar;
  }

  public int getAcknowledgementWindowSize() {
    return sequenceNumber;
  }

  /**
   * @return the sequence number, which is the number of the bytes received so far
   */
  public int getSequenceNumber() {
    return sequenceNumber;
  }

  /**
   * Sets the sequence number, which is the number of the bytes received so far
   */
  public void setSequenceNumber(int numBytesRead) {
    this.sequenceNumber = numBytesRead;
  }

  @Override
  public void readBody(InputStream in) throws IOException {
    sequenceNumber = Util.readUnsignedInt32(in);
  }

  @Override
  protected void writeBody(OutputStream out) throws IOException {
    Util.writeUnsignedInt32(out, sequenceNumber);
  }

  @Override
  public String toString() {
    return "RTMP Acknowledgment (sequence number: " + sequenceNumber + ")";
  }
}
