package com.google.android.exoplayer2.upstream.rtmp.io;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.android.exoplayer2.upstream.rtmp.packets.RtmpPacket;

/**
 * @author francois
 */
public class RtmpSessionInfo {

  /**
   * The (total) number of bytes read for this window (resets to 0 if the agreed-upon RTMP window acknowledgement size is reached)
   */
  private int windowBytesRead;
  /**
   * The window acknowledgement size for this RTMP session, in bytes; default to max to avoid unnecessary "Acknowledgment" messages from being sent
   */
  private int acknowledgementWindowSize = Integer.MAX_VALUE;
  /**
   * Used internally to store the total number of bytes read (used when sending Acknowledgement messages)
   */
  private int totalBytesRead = 0;

  /**
   * Default chunk size is 128 bytes
   */
  private int rxChunkSize = 128;
  private int txChunkSize = 128;
  private Map<Integer, ChunkStreamInfo> chunkChannels = new HashMap<Integer, ChunkStreamInfo>();
  private Map<Integer, String> invokedMethods = new ConcurrentHashMap<Integer, String>();

  public ChunkStreamInfo getChunkStreamInfo(int chunkStreamId) {
    ChunkStreamInfo chunkStreamInfo = chunkChannels.get(chunkStreamId);
    if (chunkStreamInfo == null) {
      chunkStreamInfo = new ChunkStreamInfo();
      chunkChannels.put(chunkStreamId, chunkStreamInfo);
    }
    return chunkStreamInfo;
  }

  public String takeInvokedCommand(int transactionId) {
    return invokedMethods.remove(transactionId);
  }

  public String addInvokedCommand(int transactionId, String commandName) {
    return invokedMethods.put(transactionId, commandName);
  }

  public int getRxChunkSize() {
    return rxChunkSize;
  }

  public void setRxChunkSize(int chunkSize) {
    this.rxChunkSize = chunkSize;
  }

  public int getTxChunkSize() {
    return txChunkSize;
  }

  public void setTxChunkSize(int chunkSize) {
    this.txChunkSize = chunkSize;
  }

  public int getAcknowledgementWindowSize() {
    return acknowledgementWindowSize;
  }

  public void setAcknowledgmentWindowSize(int acknowledgementWindowSize) {
    this.acknowledgementWindowSize = acknowledgementWindowSize;
  }

  /**
   * Add the specified amount of bytes to the total number of bytes read for this RTMP window;
   *
   * @param numBytes the number of bytes to add
   * @return <code>true</code> if an "acknowledgement" packet should be sent, <code>false</code> otherwise
   */
  public final void addToWindowBytesRead(final int numBytes, final RtmpPacket packet) throws WindowAckRequired {
    windowBytesRead += numBytes;
    totalBytesRead += numBytes;
    if (windowBytesRead >= acknowledgementWindowSize) {
      windowBytesRead -= acknowledgementWindowSize;
      throw new WindowAckRequired(totalBytesRead, packet);
    }
  }
}
