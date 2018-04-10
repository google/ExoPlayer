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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;

/**
 * A UDP Sink {@link UdpDataSource}.
 */
public class UdpDataSinkSource extends UdpDataSource {
    /**
     * @param listener An optional listener.
     */
    public UdpDataSinkSource(TransferListener<? super UdpDataSource> listener) {
        this(listener, DEFAULT_MAX_PACKET_SIZE);
    }

    /**
     * @param listener An optional listener.
     * @param maxPacketSize The maximum datagram packet size, in bytes.
     */
    public UdpDataSinkSource(TransferListener<? super UdpDataSource> listener, int maxPacketSize) {
        this(listener, maxPacketSize, DEAFULT_SOCKET_TIMEOUT_MILLIS);
    }

    /**
     * @param listener An optional listener.
     * @param maxPacketSize The maximum datagram packet size, in bytes.
     * @param socketTimeoutMillis The socket timeout in milliseconds. A timeout of zero is interpreted
     *     as an infinite timeout.
     */
    public UdpDataSinkSource(TransferListener<? super UdpDataSource> listener, int maxPacketSize,
                             int socketTimeoutMillis) {
        super(listener, maxPacketSize, socketTimeoutMillis);
    }

    public void write(byte[] buffer, int offset, int length) throws UdpDataSourceException {
        if (buffer == null || (offset >= length) || (buffer.length < (length - offset))) {
            return;
        }

        try {

            if (socket.isConnected()) {
                byte[] data = Arrays.copyOfRange(buffer, offset, offset + length);
                DatagramPacket packet = new DatagramPacket(data, data.length);
                socket.send(packet);
            }

        } catch (IOException e) {
            throw new UdpDataSourceException(e);
        }
    }

    public void writeTo(byte[] buffer, int offset, int length, InetAddress address, int port)
            throws UdpDataSourceException {
        if (buffer == null || (offset >= length) || (buffer.length < (length - offset))) {
            return;
        }

        try {

            if (socket.isBound()) {
                byte[] data = Arrays.copyOfRange(buffer, offset, offset + length);
                DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                socket.send(packet);
            }

        } catch (IOException e) {
            throw new UdpDataSourceException(e);
        }
    }

    public int getLocalPort() {
        SocketAddress socketAddress = socket.getLocalSocketAddress();
        return ((InetSocketAddress) socketAddress).getPort();
    }
}
