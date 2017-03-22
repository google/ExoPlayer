package com.google.android.exoplayer2.upstream.rtmp.packets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import com.google.android.exoplayer2.upstream.rtmp.Util;
import com.google.android.exoplayer2.upstream.rtmp.io.ChunkStreamInfo;

/**
 * Set Peer Bandwidth
 * <p>
 * Also known as ClientrBW ("client bandwidth") in some RTMP implementations.
 *
 * @author francois
 */
public class SetPeerBandwidth extends RtmpPacket {

  /**
   * Bandwidth limiting type
   */
  public static enum LimitType {

    /**
     * In a hard (0) request, the peer must send the data in the provided bandwidth.
     */
    HARD(0),
    /**
     * In a soft (1) request, the bandwidth is at the discretion of the peer
     * and the sender can limit the bandwidth.
     */
    SOFT(1),
    /**
     * In a dynamic (2) request, the bandwidth can be hard or soft.
     */
    DYNAMIC(2);
    private int intValue;
    private static final Map<Integer, LimitType> quickLookupMap = new HashMap<Integer, LimitType>();

    static {
      for (LimitType type : LimitType.values()) {
        quickLookupMap.put(type.getIntValue(), type);
      }
    }

    LimitType(int intValue) {
      this.intValue = intValue;
    }

    public int getIntValue() {
      return intValue;
    }

    public static LimitType valueOf(int intValue) {
      return quickLookupMap.get(intValue);
    }
  }

  private int acknowledgementWindowSize;
  private LimitType limitType;

  public SetPeerBandwidth(RtmpHeader header) {
    super(header);
  }

  public SetPeerBandwidth(int acknowledgementWindowSize, LimitType limitType, ChunkStreamInfo channelInfo) {
    super(new RtmpHeader(channelInfo.canReusePrevHeaderTx(RtmpHeader.MessageType.SET_PEER_BANDWIDTH) ? RtmpHeader.ChunkType.TYPE_2_RELATIVE_TIMESTAMP_ONLY : RtmpHeader.ChunkType.TYPE_0_FULL, ChunkStreamInfo.RTMP_CID_PROTOCOL_CONTROL, RtmpHeader.MessageType.WINDOW_ACKNOWLEDGEMENT_SIZE));
    this.acknowledgementWindowSize = acknowledgementWindowSize;
    this.limitType = limitType;
  }

  public int getAcknowledgementWindowSize() {
    return acknowledgementWindowSize;
  }

  public void setAcknowledgementWindowSize(int acknowledgementWindowSize) {
    this.acknowledgementWindowSize = acknowledgementWindowSize;
  }

  public LimitType getLimitType() {
    return limitType;
  }

  public void setLimitType(LimitType limitType) {
    this.limitType = limitType;
  }

  @Override
  public void readBody(InputStream in) throws IOException {
    acknowledgementWindowSize = Util.readUnsignedInt32(in);
    limitType = LimitType.valueOf(in.read());
  }

  @Override
  protected void writeBody(OutputStream out) throws IOException {
    Util.writeUnsignedInt32(out, acknowledgementWindowSize);
    out.write(limitType.getIntValue());
  }

  @Override
  public String toString() {
    return "RTMP Set Peer Bandwidth";
  }
}
