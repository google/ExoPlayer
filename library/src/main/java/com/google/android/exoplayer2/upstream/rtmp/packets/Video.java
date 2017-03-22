package com.google.android.exoplayer2.upstream.rtmp.packets;

import com.google.android.exoplayer2.upstream.rtmp.io.ChunkStreamInfo;

/**
 * Video data packet
 *
 * @author francois
 */
public class Video extends ContentData {

  public Video(RtmpHeader header) {
    super(header);
  }

  public Video() {
    super(new RtmpHeader(RtmpHeader.ChunkType.TYPE_0_FULL, ChunkStreamInfo.RTMP_CID_VIDEO, RtmpHeader.MessageType.VIDEO));
  }

  @Override
  public String toString() {
    return "RTMP Video";
  }
}
