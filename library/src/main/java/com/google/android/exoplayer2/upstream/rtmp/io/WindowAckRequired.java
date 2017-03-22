package com.google.android.exoplayer2.upstream.rtmp.io;

import com.google.android.exoplayer2.upstream.rtmp.packets.RtmpPacket;

/**
 * Thrown by RTMP read thread when an Acknowledgement packet needs to be sent
 * to acknowledge the RTMP window size. It contains the RTMP packet that was
 * read when this event occurred (if any).
 *
 * @author francois
 */
public class WindowAckRequired extends Exception {

  private RtmpPacket rtmpPacket;
  private int bytesRead;

  /**
   * Used when the window acknowledgement size was reached, whilst fully reading
   * an RTMP packet or not. If a packet is present, it should still be handled as if it was returned
   * by the RTMP decoder.
   *
   * @param bytesReadThusFar The (total) number of bytes received so far
   * @param rtmpPacket       The packet that was read (and thus should be handled), can be <code>null</code>
   */
  public WindowAckRequired(int bytesReadThusFar, RtmpPacket rtmpPacket) {
    this.rtmpPacket = rtmpPacket;
    this.bytesRead = bytesReadThusFar;
  }

  /**
   * @return The RTMP packet that should be handled, or <code>null</code> if no full packet is available
   */
  public RtmpPacket getRtmpPacket() {
    return rtmpPacket;
  }

  public int getBytesRead() {
    return bytesRead;
  }
}
