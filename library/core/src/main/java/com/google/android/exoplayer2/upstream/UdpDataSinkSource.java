/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.google.android.exoplayer2.C;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;

/**
 * A UDP Sink {@link UdpDataSource}.
 */
public class UdpDataSinkSource extends UdpDataSource implements UdpDataSink {

    public UdpDataSinkSource() {
        this(UdpDataSource.DEFAULT_PACKET_SIZE, DEFAULT_SOCKET_TIMEOUT_MILLIS);
    }

    /**
     * @param maxPacketSize The maximum datagram packet size, in bytes.
     */
    public UdpDataSinkSource(int maxPacketSize) {
        this(maxPacketSize, DEFAULT_RECEIVE_BUFFER_SIZE, DEFAULT_SOCKET_TIMEOUT_MILLIS);
    }

    /**
     * @param maxPacketSize The maximum datagram packet size, in bytes.
     * @param receiveBufferSize The maximum receive buffer size, in bytes.
     */
    public UdpDataSinkSource(int maxPacketSize, int receiveBufferSize) {
        this(maxPacketSize, receiveBufferSize, DEFAULT_SOCKET_TIMEOUT_MILLIS);
    }

    /**
     * @param maxPacketSize The maximum datagram packet size, in bytes.
     * @param receiveBufferSize The maximum receive buffer size, in bytes.
     * @param socketTimeoutMillis The socket timeout in milliseconds. A timeout of zero is interpreted
     *     as an infinite timeout.
     */
    public UdpDataSinkSource(int maxPacketSize, int receiveBufferSize, int socketTimeoutMillis) {
        super(maxPacketSize, receiveBufferSize, socketTimeoutMillis);
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        if (buffer == null || (offset >= length) || (buffer.length < (length - offset))) {
            return;
        }

        if (socket != null && socket.isConnected()) {
            byte[] data = Arrays.copyOfRange(buffer, offset, offset + length);
            DatagramPacket packet = new DatagramPacket(data, data.length);
            socket.send(packet);
        }
    }

    @Override
    public void writeTo(byte[] buffer, int offset, int length, InetAddress address, int port)
            throws IOException {
        if (buffer == null || (offset >= length) || (buffer.length < (length - offset))) {
            return;
        }

        if (socket != null && socket.isBound()) {
            byte[] data = Arrays.copyOfRange(buffer, offset, offset + length);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);
        }
    }

    @Override
    public int getLocalPort() {
        if (socket != null) {
            SocketAddress socketAddress = socket.getLocalSocketAddress();
            return ((InetSocketAddress) socketAddress).getPort();
        }

        return C.PORT_UNSET;
    }
}
