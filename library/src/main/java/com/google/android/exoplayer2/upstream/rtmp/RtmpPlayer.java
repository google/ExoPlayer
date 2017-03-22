package com.google.android.exoplayer2.upstream.rtmp;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Simple RTMP player, using vanilla Java networking
 * This was created primarily to address a NIO bug in Android 2.2 when
 * used with Apache Mina, but also to provide an easy-to-use way to access
 * RTMP streams
 *
 * @author francois, leo
 */
public interface RtmpPlayer {

  /**
   * Connect to the RTMP server and create the stream and wait for the meta data.
   */
  void connect(String url, int timeout) throws IOException;

  /**
   * Poll RTMP media cache and return the first byte buffer.
   */
  ByteBuffer poll();

  /**
   * Stops and closes the current RTMP stream
   */
  void close() throws IllegalStateException, IOException;

  /**
   * obtain the IP address of the peer if any
   */
  String getServerIpAddr();

  /**
   * obtain the PID of the peer if any
   */
  int getServerPid();

  /**
   * obtain the ID of the peer if any
   */
  int getServerId();
}
