/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.google.android.exoplayer2.upstream.rtmp.packets;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import com.google.android.exoplayer2.upstream.rtmp.Util;
import com.google.android.exoplayer2.upstream.rtmp.io.ChunkStreamInfo;
import com.google.android.exoplayer2.upstream.rtmp.io.RtmpSessionInfo;

/**
 * @author francois, leoma
 */
public class RtmpHeader {

  private static final String TAG = "RtmpHeader";

  /**
   * RTMP packet/message type definitions.
   * Note: docstrings are adapted from the official Adobe RTMP spec:
   * http://www.adobe.com/devnet/rtmp/
   */
  public enum MessageType {

    /**
     * Protocol control message 1
     * Set Chunk Size, is used to notify the peer a new maximum chunk size to use.
     */
    SET_CHUNK_SIZE(0x01),
    /**
     * Protocol control message 2
     * Abort Message, is used to notify the peer if it is waiting for chunks
     * to complete a message, then to discard the partially received message
     * over a chunk stream and abort processing of that message.
     */
    ABORT(0x02),
    /**
     * Protocol control message 3
     * The client or the server sends the acknowledgment to the peer after
     * receiving bytes equal to the window size. The window size is the
     * maximum number of bytes that the sender sends without receiving
     * acknowledgment from the receiver.
     */
    ACKNOWLEDGEMENT(0x03),
    /**
     * Protocol control message 4
     * The client or the server sends this message to notify the peer about
     * the user control events. This message carries Event type and Event
     * data.
     * Also known as a PING message in some RTMP implementations.
     */
    USER_CONTROL_MESSAGE(0x04),
    /**
     * Protocol control message 5
     * The client or the server sends this message to inform the peer which
     * window size to use when sending acknowledgment.
     * Also known as ServerBW ("server bandwidth") in some RTMP implementations.
     */
    WINDOW_ACKNOWLEDGEMENT_SIZE(0x05),
    /**
     * Protocol control message 6
     * The client or the server sends this message to update the output
     * bandwidth of the peer. The output bandwidth value is the same as the
     * window size for the peer.
     * Also known as ClientBW ("client bandwidth") in some RTMP implementations.
     */
    SET_PEER_BANDWIDTH(0x06),
    /**
     * RTMP audio packet (0x08)
     * The client or the server sends this message to send audio data to the peer.
     */
    AUDIO(0x08),
    /**
     * RTMP video packet (0x09)
     * The client or the server sends this message to send video data to the peer.
     */
    VIDEO(0x09),
    /**
     * RTMP message type 0x0F
     * The client or the server sends this message to send Metadata or any
     * user data to the peer. Metadata includes details about the data (audio, video etc.)
     * like creation time, duration, theme and so on.
     * This is the AMF3-encoded version.
     */
    DATA_AMF3(0x0F),
    /**
     * RTMP message type 0x10
     * A shared object is a Flash object (a collection of name value pairs)
     * that are in synchronization across multiple clients, instances, and
     * so on.
     * This is the AMF3 version: kMsgContainerEx=16 for AMF3.
     */
    SHARED_OBJECT_AMF3(0x10),
    /**
     * RTMP message type 0x11
     * Command messages carry the AMF-encoded commands between the client
     * and the server.
     * A command message consists of command name, transaction ID, and command object that
     * contains related parameters.
     * This is the AMF3-encoded version.
     */
    COMMAND_AMF3(0x11),
    /**
     * RTMP message type 0x12
     * The client or the server sends this message to send Metadata or any
     * user data to the peer. Metadata includes details about the data (audio, video etc.)
     * like creation time, duration, theme and so on.
     * This is the AMF0-encoded version.
     */
    DATA_AMF0(0x12),
    /**
     * RTMP message type 0x14
     * Command messages carry the AMF-encoded commands between the client
     * and the server.
     * A command message consists of command name, transaction ID, and command object that
     * contains related parameters.
     * This is the common AMF0 version, also known as INVOKE in some RTMP implementations.
     */
    COMMAND_AMF0(0x14),
    /**
     * RTMP message type 0x13
     * A shared object is a Flash object (a collection of name value pairs)
     * that are in synchronization across multiple clients, instances, and
     * so on.
     * This is the AMF0 version: kMsgContainer=19 for AMF0.
     */
    SHARED_OBJECT_AMF0(0x13),
    /**
     * RTMP message type 0x16
     * An aggregate message is a single message that contains a list of sub-messages.
     */
    AGGREGATE_MESSAGE(0x16);
    private byte value;
    private static final Map<Byte, MessageType> quickLookupMap = new HashMap<Byte, MessageType>();

    static {
      for (MessageType messageTypId : MessageType.values()) {
        quickLookupMap.put(messageTypId.getValue(), messageTypId);
      }
    }

    MessageType(int value) {
      this.value = (byte) value;
    }

    /**
     * Returns the value of this chunk type
     */
    public byte getValue() {
      return value;
    }

    public static MessageType valueOf(byte messageTypeId) {
      if (quickLookupMap.containsKey(messageTypeId)) {
        return quickLookupMap.get(messageTypeId);
      } else {
        throw new IllegalArgumentException("Unknown message type byte: " + Util.toHexString(messageTypeId));
      }
    }
  }

  public enum ChunkType {

    /**
     * Full 12-byte RTMP chunk header
     */
    TYPE_0_FULL(0x00),
    /**
     * Relative 8-byte RTMP chunk header (message stream ID is not included)
     */
    TYPE_1_RELATIVE_LARGE(0x01),
    /**
     * Relative 4-byte RTMP chunk header (only timestamp delta)
     */
    TYPE_2_RELATIVE_TIMESTAMP_ONLY(0x02),
    /**
     * Relative 1-byte RTMP chunk header (no "real" header, just the 1-byte indicating chunk header type & chunk stream ID)
     */
    TYPE_3_RELATIVE_SINGLE_BYTE(0x03);
    /**
     * The byte value of this chunk header type
     */
    private byte value;
    /**
     * The full size (in bytes) of this RTMP header (including the basic header byte)
     */
    private static final Map<Byte, ChunkType> quickLookupMap = new HashMap<Byte, ChunkType>();

    static {
      for (ChunkType messageTypId : ChunkType.values()) {
        quickLookupMap.put(messageTypId.getValue(), messageTypId);
      }
    }

    ChunkType(int byteValue) {
      this.value = (byte) byteValue;
    }

    /**
     * Returns the byte value of this chunk header type
     */
    public byte getValue() {
      return value;
    }

    public static ChunkType valueOf(byte chunkHeaderType) {
      if (quickLookupMap.containsKey(chunkHeaderType)) {
        return quickLookupMap.get(chunkHeaderType);
      } else {
        throw new IllegalArgumentException("Unknown chunk header type byte: " + Util.toHexString(chunkHeaderType));
      }
    }
  }

  private ChunkType chunkType;
  private int chunkStreamId;
  private int absoluteTimestamp;
  private int timestampDelta = -1;
  private int packetLength;
  private MessageType messageType;
  private int messageStreamId;
  private int extendedTimestamp;

  public RtmpHeader() {
  }

  public RtmpHeader(ChunkType chunkType, int chunkStreamId, MessageType messageType) {
    this.chunkType = chunkType;
    this.chunkStreamId = chunkStreamId;
    this.messageType = messageType;
  }

  public static RtmpHeader readHeader(InputStream in, RtmpSessionInfo rtmpSessionInfo) throws IOException {
    RtmpHeader rtmpHeader = new RtmpHeader();
    rtmpHeader.readHeaderImpl(in, rtmpSessionInfo);
    return rtmpHeader;
  }

  private void readHeaderImpl(InputStream in, RtmpSessionInfo rtmpSessionInfo) throws IOException {

    int basicHeaderByte = in.read();
    if (basicHeaderByte == -1) {
      throw new EOFException("Unexpected EOF while reading RTMP packet basic header");
    }
    // Read byte 0: chunk type and chunk stream ID
    parseBasicHeader((byte) basicHeaderByte);

    switch (chunkType) {
      case TYPE_0_FULL: { //  b00 = 12 byte header (full header)
        // Read bytes 1-3: Absolute timestamp
        absoluteTimestamp = Util.readUnsignedInt24(in);
        timestampDelta = 0;
        // Read bytes 4-6: Packet length
        packetLength = Util.readUnsignedInt24(in);
        // Read byte 7: Message type ID
        messageType = MessageType.valueOf((byte) in.read());
        // Read bytes 8-11: Message stream ID (apparently little-endian order)
        byte[] messageStreamIdBytes = new byte[4];
        Util.readBytesUntilFull(in, messageStreamIdBytes);
        messageStreamId = Util.toUnsignedInt32LittleEndian(messageStreamIdBytes);
        // Read bytes 1-4: Extended timestamp
        extendedTimestamp = absoluteTimestamp >= 0xffffff ? Util.readUnsignedInt32(in) : 0;
        if (extendedTimestamp != 0) {
          absoluteTimestamp = extendedTimestamp;
        }
        break;
      }
      case TYPE_1_RELATIVE_LARGE: { // b01 = 8 bytes - like type 0. not including message stream ID (4 last bytes)
        // Read bytes 1-3: Timestamp delta
        timestampDelta = Util.readUnsignedInt24(in);
        // Read bytes 4-6: Packet length
        packetLength = Util.readUnsignedInt24(in);
        // Read byte 7: Message type ID
        messageType = MessageType.valueOf((byte) in.read());
        // Read bytes 1-4: Extended timestamp delta
        extendedTimestamp = timestampDelta >= 0xffffff ? Util.readUnsignedInt32(in) : 0;
        RtmpHeader prevHeader = rtmpSessionInfo.getChunkStreamInfo(chunkStreamId).prevHeaderRx();
        if (prevHeader != null) {
          messageStreamId = prevHeader.messageStreamId;
          absoluteTimestamp = extendedTimestamp != 0 ? extendedTimestamp : prevHeader.absoluteTimestamp + timestampDelta;
        } else {
          messageStreamId = 0;
          absoluteTimestamp = extendedTimestamp != 0 ? extendedTimestamp : timestampDelta;
        }
        break;
      }
      case TYPE_2_RELATIVE_TIMESTAMP_ONLY: { // b10 = 4 bytes - Basic Header and timestamp (3 bytes) are included
        // Read bytes 1-3: Timestamp delta
        timestampDelta = Util.readUnsignedInt24(in);
        // Read bytes 1-4: Extended timestamp delta
        extendedTimestamp = timestampDelta >= 0xffffff ? Util.readUnsignedInt32(in) : 0;
        RtmpHeader prevHeader = rtmpSessionInfo.getChunkStreamInfo(chunkStreamId).prevHeaderRx();
        if (prevHeader == null) {
          throw new IllegalArgumentException("Unknown chunk stream id " + chunkStreamId);
        }
        packetLength = prevHeader.packetLength;
        messageType = prevHeader.messageType;
        messageStreamId = prevHeader.messageStreamId;
        absoluteTimestamp = extendedTimestamp != 0 ? extendedTimestamp : prevHeader.absoluteTimestamp + timestampDelta;
        break;
      }
      case TYPE_3_RELATIVE_SINGLE_BYTE: { // b11 = 1 byte: basic header only
        RtmpHeader prevHeader = rtmpSessionInfo.getChunkStreamInfo(chunkStreamId).prevHeaderRx();
        if (prevHeader == null) {
          throw new IllegalArgumentException("Unknown chunk stream id " + chunkStreamId);
        }
        // Read bytes 1-4: Extended timestamp
        extendedTimestamp = prevHeader.timestampDelta >= 0xffffff ? Util.readUnsignedInt32(in) : 0;
        timestampDelta = extendedTimestamp != 0 ? 0xffffff : prevHeader.timestampDelta;
        packetLength = prevHeader.packetLength;
        messageType = prevHeader.messageType;
        messageStreamId = prevHeader.messageStreamId;
        absoluteTimestamp = extendedTimestamp != 0 ? extendedTimestamp : prevHeader.absoluteTimestamp + timestampDelta;
        break;
      }
      default:
        throw new IOException("Invalid chunk type; basic header byte was: " + Util.toHexString((byte) basicHeaderByte));
    }
  }

  public void writeTo(OutputStream out, ChunkType chunkType, final ChunkStreamInfo chunkStreamInfo) throws IOException {
    // Write basic header byte
    out.write(((byte) (chunkType.getValue() << 6) | chunkStreamId));
    switch (chunkType) {
      case TYPE_0_FULL: { //  b00 = 12 byte header (full header)
        chunkStreamInfo.markDeltaTimestamp();
        Util.writeUnsignedInt24(out, absoluteTimestamp >= 0xffffff ? 0xffffff : absoluteTimestamp);
        Util.writeUnsignedInt24(out, packetLength);
        out.write(messageType.getValue());
        Util.writeUnsignedInt32LittleEndian(out, messageStreamId);
        if (absoluteTimestamp >= 0xffffff) {
          extendedTimestamp = absoluteTimestamp;
          Util.writeUnsignedInt32(out, extendedTimestamp);
        }
        break;
      }
      case TYPE_1_RELATIVE_LARGE: { // b01 = 8 bytes - like type 0. not including message ID (4 last bytes)
        timestampDelta = (int) chunkStreamInfo.markDeltaTimestamp();
        absoluteTimestamp = chunkStreamInfo.getPrevHeaderTx().getAbsoluteTimestamp() + timestampDelta;
        Util.writeUnsignedInt24(out, absoluteTimestamp >= 0xffffff ? 0xffffff : timestampDelta);
        Util.writeUnsignedInt24(out, packetLength);
        out.write(messageType.getValue());
        if (absoluteTimestamp >= 0xffffff) {
          extendedTimestamp = absoluteTimestamp;
          Util.writeUnsignedInt32(out, absoluteTimestamp);
        }
        break;
      }
      case TYPE_2_RELATIVE_TIMESTAMP_ONLY: { // b10 = 4 bytes - Basic Header and timestamp (3 bytes) are included
        timestampDelta = (int) chunkStreamInfo.markDeltaTimestamp();
        absoluteTimestamp = chunkStreamInfo.getPrevHeaderTx().getAbsoluteTimestamp() + timestampDelta;
        Util.writeUnsignedInt24(out, (absoluteTimestamp >= 0xffffff) ? 0xffffff : timestampDelta);
        if (absoluteTimestamp >= 0xffffff) {
          extendedTimestamp = absoluteTimestamp;
          Util.writeUnsignedInt32(out, extendedTimestamp);
        }
        break;
      }
      case TYPE_3_RELATIVE_SINGLE_BYTE: { // b11 = 1 byte: basic header only
        timestampDelta = (int) chunkStreamInfo.markDeltaTimestamp();
        absoluteTimestamp = chunkStreamInfo.getPrevHeaderTx().getAbsoluteTimestamp() + timestampDelta;
        if (absoluteTimestamp >= 0xffffff) {
          extendedTimestamp = absoluteTimestamp;
          Util.writeUnsignedInt32(out, extendedTimestamp);
        }
        break;
      }
      default:
        throw new IOException("Invalid chunk type: " + chunkType);
    }
  }

  private void parseBasicHeader(byte basicHeaderByte) {
    chunkType = ChunkType.valueOf((byte) ((0xff & basicHeaderByte) >>> 6)); // 2 most significant bits define the chunk type
    chunkStreamId = basicHeaderByte & 0x3F; // 6 least significant bits define chunk stream ID
  }

  /**
   * @return the RTMP chunk stream ID (channel ID) for this chunk
   */
  public int getChunkStreamId() {
    return chunkStreamId;
  }

  public ChunkType getChunkType() {
    return chunkType;
  }

  public int getPacketLength() {
    return packetLength;
  }

  public int getMessageStreamId() {
    return messageStreamId;
  }

  public MessageType getMessageType() {
    return messageType;
  }

  public int getAbsoluteTimestamp() {
    return absoluteTimestamp;
  }

  public void setAbsoluteTimestamp(int absoluteTimestamp) {
    this.absoluteTimestamp = absoluteTimestamp;
  }

  public int getTimestampDelta() {
    return timestampDelta;
  }

  public void setTimestampDelta(int timestampDelta) {
    this.timestampDelta = timestampDelta;
  }

  /**
   * Sets the RTMP chunk stream ID (channel ID) for this chunk
   */
  public void setChunkStreamId(int channelId) {
    this.chunkStreamId = channelId;
  }

  public void setChunkType(ChunkType chunkType) {
    this.chunkType = chunkType;
  }

  public void setMessageStreamId(int messageStreamId) {
    this.messageStreamId = messageStreamId;
  }

  public void setMessageType(MessageType messageType) {
    this.messageType = messageType;
  }

  public void setPacketLength(int packetLength) {
    this.packetLength = packetLength;
  }
}
