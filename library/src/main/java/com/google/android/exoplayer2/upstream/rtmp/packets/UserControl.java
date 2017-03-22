package com.google.android.exoplayer2.upstream.rtmp.packets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import com.google.android.exoplayer2.upstream.rtmp.Util;
import com.google.android.exoplayer2.upstream.rtmp.io.ChunkStreamInfo;

/**
 * User Control message, such as ping
 *
 * @author francois
 */
public class UserControl extends RtmpPacket {

  /**
   * Control message type
   * Docstring adapted from the official Adobe RTMP spec, section 3.7
   */
  public static enum Type {

    /**
     * Type: 0
     * The server sends this event to notify the client that a stream has become
     * functional and can be used for communication. By default, this event
     * is sent on ID 0 after the application connect command is successfully
     * received from the client.
     * <p>
     * Event Data:
     * eventData[0] (int) the stream ID of the stream that became functional
     */
    STREAM_BEGIN(0),
    /**
     * Type: 1
     * The server sends this event to notify the client that the playback of
     * data is over as requested on this stream. No more data is sent without
     * issuing additional commands. The client discards the messages received
     * for the stream.
     * <p>
     * Event Data:
     * eventData[0]: the ID of the stream on which playback has ended.
     */
    STREAM_EOF(1),
    /**
     * Type: 2
     * The server sends this event to notify the client that there is no
     * more data on the stream. If the server does not detect any message for
     * a time period, it can notify the subscribed clients that the stream is
     * dry.
     * <p>
     * Event Data:
     * eventData[0]: the stream ID of the dry stream.
     */
    STREAM_DRY(2),
    /**
     * Type: 3
     * The client sends this event to inform the server of the buffer size
     * (in milliseconds) that is used to buffer any data coming over a stream.
     * This event is sent before the server starts processing the stream.
     * <p>
     * Event Data:
     * eventData[0]: the stream ID and
     * eventData[1]: the buffer length, in milliseconds.
     */
    SET_BUFFER_LENGTH(3),
    /**
     * Type: 4
     * The server sends this event to notify the client that the stream is a
     * recorded stream.
     * <p>
     * Event Data:
     * eventData[0]: the stream ID of the recorded stream.
     */
    STREAM_IS_RECORDED(4),
    /**
     * Type: 6
     * The server sends this event to test whether the client is reachable.
     * <p>
     * Event Data:
     * eventData[0]: a timestamp representing the local server time when the server dispatched the command.
     * <p>
     * The client responds with PING_RESPONSE on receiving PING_REQUEST.
     */
    PING_REQUEST(6),
    /**
     * Type: 7
     * The client sends this event to the server in response to the ping request.
     * <p>
     * Event Data:
     * eventData[0]: the 4-byte timestamp which was received with the PING_REQUEST.
     */
    PONG_REPLY(7),
    /**
     * Type: 31 (0x1F)
     * <p>
     * This user control type is not specified in any official documentation, but
     * is sent by Flash Media Server 3.5. Thanks to the rtmpdump devs for their
     * explanation:
     * <p>
     * Buffer Empty (unofficial name): After the server has sent a complete buffer, and
     * sends this Buffer Empty message, it will wait until the play
     * duration of that buffer has passed before sending a new buffer.
     * The Buffer Ready message will be sent when the new buffer starts.
     * <p>
     * (see also: http://repo.or.cz/w/rtmpdump.git/blob/8880d1456b282ee79979adbe7b6a6eb8ad371081:/librtmp/rtmp.c#l2787)
     */
    BUFFER_EMPTY(31),
    /**
     * Type: 32 (0x20)
     * <p>
     * This user control type is not specified in any official documentation, but
     * is sent by Flash Media Server 3.5. Thanks to the rtmpdump devs for their
     * explanation:
     * <p>
     * Buffer Ready (unofficial name): After the server has sent a complete buffer, and
     * sends a Buffer Empty message, it will wait until the play
     * duration of that buffer has passed before sending a new buffer.
     * The Buffer Ready message will be sent when the new buffer starts.
     * (There is no BufferReady message for the very first buffer;
     * presumably the Stream Begin message is sufficient for that
     * purpose.)
     * <p>
     * (see also: http://repo.or.cz/w/rtmpdump.git/blob/8880d1456b282ee79979adbe7b6a6eb8ad371081:/librtmp/rtmp.c#l2787)
     */
    BUFFER_READY(32);

    private int intValue;
    private static final Map<Integer, Type> quickLookupMap = new HashMap<Integer, Type>();

    static {
      for (Type type : Type.values()) {
        quickLookupMap.put(type.getIntValue(), type);
      }
    }

    Type(int intValue) {
      this.intValue = intValue;
    }

    public int getIntValue() {
      return intValue;
    }

    public static Type valueOf(int intValue) {
      return quickLookupMap.get(intValue);
    }
  }

  private Type type;
  private int[] eventData;

  public UserControl(RtmpHeader header) {
    super(header);
  }

  public UserControl(ChunkStreamInfo channelInfo) {
    super(new RtmpHeader(channelInfo.canReusePrevHeaderTx(RtmpHeader.MessageType.USER_CONTROL_MESSAGE) ?
        RtmpHeader.ChunkType.TYPE_2_RELATIVE_TIMESTAMP_ONLY : RtmpHeader.ChunkType.TYPE_0_FULL,
        ChunkStreamInfo.RTMP_CID_PROTOCOL_CONTROL, RtmpHeader.MessageType.USER_CONTROL_MESSAGE));
  }

  /**
   * Convenience construtor that creates a "pong" message for the specified ping
   */
  public UserControl(UserControl replyToPing, ChunkStreamInfo channelInfo) {
    this(Type.PONG_REPLY, channelInfo);
    this.eventData = replyToPing.eventData;
  }

  public UserControl(Type type, ChunkStreamInfo channelInfo) {
    this(channelInfo);
    this.type = type;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  /**
   * Convenience method for getting the first event data item, as most user control
   * message types only have one event data item anyway
   * This is equivalent to calling <code>getEventData()[0]</code>
   */
  public int getFirstEventData() {
    return eventData[0];
  }

  public int[] getEventData() {
    return eventData;
  }

  /**
   * Used to set (a single) event data for most user control message types
   */
  public void setEventData(int eventData) {
    if (type == Type.SET_BUFFER_LENGTH) {
      throw new IllegalStateException("SET_BUFFER_LENGTH requires two event data values; use setEventData(int, int) instead");
    }
    this.eventData = new int[]{eventData};
  }

  /**
   * Used to set event data for the SET_BUFFER_LENGTH user control message types
   */
  public void setEventData(int streamId, int bufferLength) {
    if (type != Type.SET_BUFFER_LENGTH) {
      throw new IllegalStateException("User control type " + type + " requires only one event data value; use setEventData(int) instead");
    }
    this.eventData = new int[]{streamId, bufferLength};
  }

  @Override
  public void readBody(InputStream in) throws IOException {
    // Bytes 0-1: first parameter: ping type (mandatory)
    type = Type.valueOf(Util.readUnsignedInt16(in));
    int bytesRead = 2;
    // Event data (1 for most types, 2 for SET_BUFFER_LENGTH)
    if (type == Type.SET_BUFFER_LENGTH) {
      setEventData(Util.readUnsignedInt32(in), Util.readUnsignedInt32(in));
      bytesRead += 8;
    } else {
      setEventData(Util.readUnsignedInt32(in));
      bytesRead += 4;
    }
    // To ensure some strange non-specified UserControl/ping message does not slip through
    assert header.getPacketLength() == bytesRead;
  }

  @Override
  protected void writeBody(OutputStream out) throws IOException {
    // Write the user control message type
    Util.writeUnsignedInt16(out, type.getIntValue());
    // Now write the event data
    Util.writeUnsignedInt32(out, eventData[0]);
    if (type == Type.SET_BUFFER_LENGTH) {
      Util.writeUnsignedInt32(out, eventData[1]);
    }
  }

  @Override
  public String toString() {
    return "RTMP User Control (type: " + type + ", event data: " + eventData + ")";
  }
}
