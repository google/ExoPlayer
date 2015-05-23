/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.upstream;

import com.google.android.exoplayer.C;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * A multicast {@link DataSource}.
 */
public class MulticastDataSource implements UriDataSource {

  /**
   * Thrown when an error is encountered when trying to read from a {@link MulticastDataSource}.
   */
  public static final class MulticastDataSourceException extends IOException {

    public MulticastDataSourceException(String message) {
      super(message);
    }

    public MulticastDataSourceException(IOException cause) {
      super(cause);
    }

  }

  public static final int DEFAULT_MAX_PACKET_SIZE = 2000;

  public static final int TRANSFER_LISTENER_PACKET_INTERVAL = 1000;

  private final TransferListener transferListener;
  private final DatagramPacket packet;

  private DataSpec dataSpec;
  private MulticastSocket socket;
  private boolean opened;

  private int packetsReceived;
  private byte[] packetBuffer;
  private int packetRemaining;

  public MulticastDataSource(TransferListener transferListener) {
    this(transferListener, DEFAULT_MAX_PACKET_SIZE);
  }

  public MulticastDataSource(TransferListener transferListener, int maxPacketSize) {
    this.transferListener = transferListener;

    packetBuffer = new byte[maxPacketSize];
    packet = new DatagramPacket(packetBuffer, 0, maxPacketSize);
  }

  @Override
  public long open(DataSpec dataSpec) throws MulticastDataSourceException {
    this.dataSpec = dataSpec;
    String uri = dataSpec.uri.toString();
    String host = uri.substring(0, uri.indexOf(':'));
    int port = Integer.parseInt(uri.substring(uri.indexOf(':') + 1));

    try {
      socket = new MulticastSocket(port);
      socket.joinGroup(InetAddress.getByName(host));
    } catch (IOException e) {
      throw new MulticastDataSourceException(e);
    }

    opened = true;
    transferListener.onTransferStart();
    return C.LENGTH_UNBOUNDED;
  }

  @Override
  public void close() {
    if (opened) {
      socket.close();
      socket = null;
      transferListener.onTransferEnd();
      packetRemaining = 0;
      packetsReceived = 0;
      opened = false;
    }
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws MulticastDataSourceException {
    // if we've read all the data, get another packet
    if (packetRemaining == 0) {
      if (packetsReceived == TRANSFER_LISTENER_PACKET_INTERVAL) {
        transferListener.onTransferEnd();
        transferListener.onTransferStart();
        packetsReceived = 0;
      }

      try {
        socket.receive(packet);
      } catch (IOException e) {
        throw new MulticastDataSourceException(e);
      }

      packetRemaining = packet.getLength();
      transferListener.onBytesTransferred(packetRemaining);
      packetsReceived++;
    }

    // don't try to read too much
    if (packetRemaining < readLength) {
      readLength = packetRemaining;
    }

    int packetOffset = packet.getLength() - packetRemaining;
    System.arraycopy(packetBuffer, packetOffset, buffer, offset, readLength);
    packetRemaining -= readLength;

    return readLength;
  }

  @Override
  public String getUri() {
    return dataSpec == null ? null : dataSpec.uri.toString();
  }

}
