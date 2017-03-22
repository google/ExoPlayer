package com.google.android.exoplayer2.upstream.rtmp.packets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.google.android.exoplayer2.upstream.rtmp.amf.AmfNumber;
import com.google.android.exoplayer2.upstream.rtmp.amf.AmfString;
import com.google.android.exoplayer2.upstream.rtmp.io.ChunkStreamInfo;

/**
 * Encapsulates an command/"invoke" RTMP packet
 * <p>
 * Invoke/command packet structure (AMF encoded):
 * (String) <commmand name>
 * (Number) <Transaction ID>
 * (Mixed) <Argument> ex. Null, String, Object: {key1:value1, key2:value2 ... }
 *
 * @author francois
 */
public class Command extends VariableBodyRtmpPacket {

  private static final String TAG = "Command";

  private String commandName;
  private int transactionId;

  public Command(RtmpHeader header) {
    super(header);
  }

  public Command(String commandName, int transactionId, ChunkStreamInfo channelInfo) {
    super(new RtmpHeader((channelInfo.canReusePrevHeaderTx(RtmpHeader.MessageType.COMMAND_AMF0) ?
        RtmpHeader.ChunkType.TYPE_1_RELATIVE_LARGE : RtmpHeader.ChunkType.TYPE_0_FULL),
        ChunkStreamInfo.RTMP_CID_OVER_CONNECTION, RtmpHeader.MessageType.COMMAND_AMF0));
    this.commandName = commandName;
    this.transactionId = transactionId;
  }

  public Command(String commandName, int transactionId) {
    super(new RtmpHeader(RtmpHeader.ChunkType.TYPE_0_FULL, ChunkStreamInfo.RTMP_CID_OVER_CONNECTION,
        RtmpHeader.MessageType.COMMAND_AMF0));
    this.commandName = commandName;
    this.transactionId = transactionId;
  }

  public String getCommandName() {
    return commandName;
  }

  public void setCommandName(String commandName) {
    this.commandName = commandName;
  }

  public int getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(int transactionId) {
    this.transactionId = transactionId;
  }

  @Override
  public void readBody(InputStream in) throws IOException {
    // The command name and transaction ID are always present (AMF string followed by number)
    commandName = AmfString.readStringFrom(in, false);
    transactionId = (int) AmfNumber.readNumberFrom(in);
    int bytesRead = AmfString.sizeOf(commandName, false) + AmfNumber.SIZE;
    readVariableData(in, bytesRead);
  }

  @Override
  protected void writeBody(OutputStream out) throws IOException {
    AmfString.writeStringTo(out, commandName, false);
    AmfNumber.writeNumberTo(out, transactionId);
    // Write body data
    writeVariableData(out);
  }

  @Override
  public String toString() {
    return "RTMP Command (command: " + commandName + ", transaction ID: " + transactionId + ")";
  }
}
