/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.upstream;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;

/** A UDP {@link DataSource}. */
public class UdpDataSource extends BaseDataSource {

  /** The default datagram packet size, in bytes.
   * 1500 bytes (MTU) minus IP header (20 bytes) and UDP header (8 bytes)
   */
  public static final int DEFAULT_PACKET_SIZE = 1480;

  /** The maximum datagram packet size, in bytes.
   * 65535 bytes minus IP header (20 bytes) and UDP header (8 bytes)
   */
  public static final int MAX_PACKET_SIZE = 65507;

  /** The default maximum receive buffer size, in bytes. */
  public static final int DEFAULT_RECEIVE_BUFFER_SIZE = 200 * 1024;

  /** The default socket timeout, in milliseconds. */
  public static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 8 * 1000;

  private final int receiveBufferSize;
  private final int socketTimeoutMillis;
  private final byte[] packetBuffer;
  private final DatagramPacket packet;

  @Nullable private Uri uri;
  @Nullable DatagramSocket socket;
  @Nullable private MulticastSocket multicastSocket;
  @Nullable private InetAddress address;
  @Nullable private InetSocketAddress socketAddress;

  private boolean opened;

  private int packetRemaining;

  public UdpDataSource() {
    this(DEFAULT_PACKET_SIZE);
  }

  /**
   * Constructs a new instance.
   *
   * @param maxPacketSize The maximum datagram packet size, in bytes.
   */
  public UdpDataSource(int maxPacketSize) {
    this(maxPacketSize, DEFAULT_RECEIVE_BUFFER_SIZE, DEFAULT_SOCKET_TIMEOUT_MILLIS);
  }

  /**
   * Constructs a new instance.
   *
   * @param maxPacketSize The maximum datagram packet size, in bytes.
   * @param receiveBufferSize The maximum receive buffer size, in bytes.
   */
  public UdpDataSource(int maxPacketSize, int receiveBufferSize) {
    this(maxPacketSize, receiveBufferSize, DEFAULT_SOCKET_TIMEOUT_MILLIS);
  }

  /**
   * Constructs a new instance.
   *
   * @param maxPacketSize The maximum datagram packet size, in bytes.
   * @param receiveBufferSize The maximum receive buffer size, in bytes.
   * @param socketTimeoutMillis The socket timeout in milliseconds. A timeout of zero is interpreted
   *     as an infinite timeout.
   */
  public UdpDataSource(int maxPacketSize, int receiveBufferSize, int socketTimeoutMillis) {
    super(/* isNetwork= */ true);
    this.receiveBufferSize = receiveBufferSize;
    this.socketTimeoutMillis = socketTimeoutMillis;
    packetBuffer = new byte[maxPacketSize];
    packet = new DatagramPacket(packetBuffer, 0, maxPacketSize);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    uri = dataSpec.uri;
    String host = uri.getHost();
    int port = uri.getPort();
    transferInitializing(dataSpec);

    address = InetAddress.getByName(host);
    socketAddress = new InetSocketAddress(address, port);
    if (address.isMulticastAddress()) {
      multicastSocket = new MulticastSocket(socketAddress);
      multicastSocket.joinGroup(address);
      socket = multicastSocket;
    } else {
      if (dataSpec.isFlagSet(DataSpec.FLAG_FORCE_BOUND_LOCAL_ADDRESS)) {
        socket = new DatagramSocket(uri.getPort());
      } else {
        socket = new DatagramSocket();
        socket.connect(socketAddress);
      }
    }

    socket.setSoTimeout(socketTimeoutMillis);
    socket.setReceiveBufferSize(receiveBufferSize);

    opened = true;
    transferStarted(dataSpec);
    return C.LENGTH_UNSET;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    if (readLength == 0) {
      return 0;
    }

    if (packetRemaining == 0) {
      // We've read all of the data from the current packet. Get another.
      socket.receive(packet);
      packetRemaining = packet.getLength();
      bytesTransferred(packetRemaining);
    }

    int packetOffset = packet.getLength() - packetRemaining;
    int bytesToRead = Math.min(packetRemaining, readLength);
    System.arraycopy(packetBuffer, packetOffset, buffer, offset, bytesToRead);
    packetRemaining -= bytesToRead;
    return bytesToRead;
  }

  @Override
  @Nullable
  public Uri getUri() {
    return uri;
  }

  @Override
  public void close() {
    uri = null;
    if (multicastSocket != null) {
      try {
        multicastSocket.leaveGroup(address);
      } catch (IOException e) {
        // Do nothing.
      }
      multicastSocket = null;
    }
    if (socket != null) {
      socket.close();
      socket = null;
    }
    address = null;
    socketAddress = null;
    packetRemaining = 0;
    if (opened) {
      opened = false;
      transferEnded();
    }
  }

}
