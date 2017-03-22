package com.google.android.exoplayer2.upstream.rtmp.packets;

import com.google.android.exoplayer2.upstream.rtmp.io.ChunkStreamInfo;

/**
 * Audio data packet
 *
 * @author francois
 */
public class Audio extends ContentData {

  public Audio(RtmpHeader header) {
    super(header);
  }

  public Audio() {
    super(new RtmpHeader(RtmpHeader.ChunkType.TYPE_0_FULL, ChunkStreamInfo.RTMP_CID_AUDIO, RtmpHeader.MessageType.AUDIO));
  }

  @Override
  public String toString() {
    return "RTMP Audio";
  }
}
