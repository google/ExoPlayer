package com.google.android.exoplayer2.upstream.rtmp.io;

import java.io.IOException;
import java.io.InputStream;

import android.util.Log;

import com.google.android.exoplayer2.upstream.rtmp.packets.Abort;
import com.google.android.exoplayer2.upstream.rtmp.packets.Audio;
import com.google.android.exoplayer2.upstream.rtmp.packets.Command;
import com.google.android.exoplayer2.upstream.rtmp.packets.Data;
import com.google.android.exoplayer2.upstream.rtmp.packets.RtmpHeader;
import com.google.android.exoplayer2.upstream.rtmp.packets.RtmpPacket;
import com.google.android.exoplayer2.upstream.rtmp.packets.SetChunkSize;
import com.google.android.exoplayer2.upstream.rtmp.packets.SetPeerBandwidth;
import com.google.android.exoplayer2.upstream.rtmp.packets.UserControl;
import com.google.android.exoplayer2.upstream.rtmp.packets.Video;
import com.google.android.exoplayer2.upstream.rtmp.packets.WindowAckSize;
import com.google.android.exoplayer2.upstream.rtmp.packets.Acknowledgement;

/**
 * @author francois
 */
public class RtmpDecoder {

  private static final String TAG = "RtmpDecoder";

  private RtmpSessionInfo rtmpSessionInfo;

  public RtmpDecoder(RtmpSessionInfo rtmpSessionInfo) {
    this.rtmpSessionInfo = rtmpSessionInfo;
  }

  public RtmpPacket readPacket(InputStream in) throws IOException {

    RtmpHeader header = RtmpHeader.readHeader(in, rtmpSessionInfo);
    //Log.d(TAG, "readPacket(): header.messageType: " + header.getMessageType());

    ChunkStreamInfo chunkStreamInfo = rtmpSessionInfo.getChunkStreamInfo(header.getChunkStreamId());
    chunkStreamInfo.setPrevHeaderRx(header);

    if (header.getPacketLength() > rtmpSessionInfo.getRxChunkSize()) {
      // If the packet consists of more than one chunk,
      // store the chunks in the chunk stream until everything is read
      if (!chunkStreamInfo.storePacketChunk(in, rtmpSessionInfo.getRxChunkSize())) {
        // return null because of incomplete packet
        return null;
      } else {
        // stored chunks complete packet, get the input stream of the chunk stream;
        in = chunkStreamInfo.getStoredPacketInputStream();
      }
    }

    RtmpPacket rtmpPacket;
    switch (header.getMessageType()) {
      case SET_CHUNK_SIZE:
        SetChunkSize setChunkSize = new SetChunkSize(header);
        setChunkSize.readBody(in);
        Log.d(TAG, "readPacket(): Setting chunk size to: " + setChunkSize.getChunkSize());
        rtmpSessionInfo.setRxChunkSize(setChunkSize.getChunkSize());
        return null;
      case ABORT:
        rtmpPacket = new Abort(header);
        break;
      case USER_CONTROL_MESSAGE:
        rtmpPacket = new UserControl(header);
        break;
      case WINDOW_ACKNOWLEDGEMENT_SIZE:
        rtmpPacket = new WindowAckSize(header);
        break;
      case SET_PEER_BANDWIDTH:
        rtmpPacket = new SetPeerBandwidth(header);
        break;
      case AUDIO:
        rtmpPacket = new Audio(header);
        break;
      case VIDEO:
        rtmpPacket = new Video(header);
        break;
      case COMMAND_AMF0:
        rtmpPacket = new Command(header);
        break;
      case DATA_AMF0:
        rtmpPacket = new Data(header);
        break;
      case ACKNOWLEDGEMENT:
        rtmpPacket = new Acknowledgement(header);
        break;
      default:
        throw new IOException("No packet body implementation for message type: " + header.getMessageType());
    }
    rtmpPacket.readBody(in);
    return rtmpPacket;
  }
}
