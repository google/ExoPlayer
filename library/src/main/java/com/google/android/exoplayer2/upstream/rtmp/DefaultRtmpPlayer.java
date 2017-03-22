package com.google.android.exoplayer2.upstream.rtmp;

import com.google.android.exoplayer2.upstream.rtmp.io.RtmpConnection;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Simple RTMP player, using vanilla Java networking (no NIO)
 * This was created primarily to address a NIO bug in Android 2.2 when
 * used with Apache Mina, but also to provide an easy-to-use way to access
 * RTMP streams
 *
 * @author leo
 */
public class DefaultRtmpPlayer implements RtmpPlayer {

  private RtmpConnection rtmpConnection = new RtmpConnection();
  private boolean connected = false;

  @Override
  public void connect(String url, int timeout) throws IOException {
    if (!connected) {
      rtmpConnection.connect(url, timeout);
      connected = true;
    }
  }

  @Override
  public ByteBuffer poll() {
    return connected ? rtmpConnection.poll() : null;
  }

  @Override
  public void close() throws IllegalStateException, IOException {
    rtmpConnection.close();
    connected = false;
  }

  @Override
  public final String getServerIpAddr() {
    return rtmpConnection.getServerIpAddr();
  }

  @Override
  public final int getServerPid() {
    return rtmpConnection.getServerPid();
  }

  @Override
  public final int getServerId() {
    return rtmpConnection.getServerId();
  }
}