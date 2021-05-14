/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtsp;

import static com.google.android.exoplayer2.source.rtsp.RtspMessageUtil.convertMessageToByteArray;
import static com.google.android.exoplayer2.source.rtsp.RtspMessageUtil.serializeResponse;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.robolectric.RobolectricUtil;
import com.google.android.exoplayer2.source.rtsp.RtspMessageChannel.MessageListener;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Bytes;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.SocketFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link RtspMessageChannel}. */
@RunWith(AndroidJUnit4.class)
public final class RtspMessageChannelTest {

  @Test
  public void rtspMessageChannelReceive_threeRtspMessagesAndTwoInterleavedBinary_postsToListener()
      throws Exception {
    RtspResponse optionsResponse =
        new RtspResponse(
            200,
            new RtspHeaders.Builder()
                .add(RtspHeaders.CSEQ, "2")
                .add(RtspHeaders.PUBLIC, "OPTIONS")
                .build(),
            "");

    RtspResponse describeResponse =
        new RtspResponse(
            200,
            new RtspHeaders.Builder()
                .add(RtspHeaders.CSEQ, "3")
                .add(RtspHeaders.CONTENT_TYPE, "application/sdp")
                .add(RtspHeaders.CONTENT_LENGTH, "28")
                .build(),
            "v=安卓アンドロイド\r\n");

    RtspResponse setupResponse =
        new RtspResponse(
            200,
            new RtspHeaders.Builder()
                .add(RtspHeaders.CSEQ, "3")
                .add(RtspHeaders.TRANSPORT, "RTP/AVP/TCP;unicast;interleaved=0-1")
                .build(),
            "");

    // Channel: 0, size: 5, data: 01 02 03 04 05.
    byte[] interleavedData1 = Util.getBytesFromHexString("0000050102030405");
    // Channel: 1, size: 4, data: AA BB CC DD.
    byte[] interleavedData2 = Util.getBytesFromHexString("010004AABBCCDD");

    AtomicBoolean receivingFinished = new AtomicBoolean();
    AtomicReference<Exception> sendingException = new AtomicReference<>();
    List<List<String>> receivedRtspResponses = new ArrayList<>(/* initialCapacity= */ 3);
    Multimap<Integer, List<Byte>> receivedInterleavedData = LinkedListMultimap.create();
    ServerSocket serverSocket =
        new ServerSocket(/* port= */ 0, /* backlog= */ 1, InetAddress.getByName(/* host= */ null));
    Thread serverListenThread =
        new Thread(
            () -> {
              try {
                Socket socket = serverSocket.accept();
                OutputStream serverOutputStream = socket.getOutputStream();
                serverOutputStream.write(
                    convertMessageToByteArray(serializeResponse(optionsResponse)));
                serverOutputStream.write(
                    convertMessageToByteArray(serializeResponse(describeResponse)));
                serverOutputStream.write(Bytes.concat(new byte[] {'$'}, interleavedData1));
                serverOutputStream.write(Bytes.concat(new byte[] {'$'}, interleavedData2));
                serverOutputStream.write(
                    convertMessageToByteArray(serializeResponse(setupResponse)));
              } catch (IOException e) {
                sendingException.set(e);
              }
            },
            "RtspMessageChannelTest:ServerListenThread");
    serverListenThread.start();

    int serverRtspPortNumber = serverSocket.getLocalPort();
    Uri connectionUri =
        Uri.parse(Util.formatInvariant("rtsp://localhost:%d/test", serverRtspPortNumber));
    Socket clientSideSocket =
        SocketFactory.getDefault().createSocket(connectionUri.getHost(), connectionUri.getPort());

    RtspMessageChannel rtspMessageChannel =
        new RtspMessageChannel(
            new MessageListener() {
              @Override
              public void onRtspMessageReceived(List<String> message) {
                receivedRtspResponses.add(message);
                if (receivedRtspResponses.size() == 3 && receivedInterleavedData.size() == 2) {
                  receivingFinished.set(true);
                }
              }

              @Override
              public void onInterleavedBinaryDataReceived(byte[] data, int channel) {
                receivedInterleavedData.put(channel, Bytes.asList(data));
              }
            });
    rtspMessageChannel.openSocket(clientSideSocket);

    RobolectricUtil.runMainLooperUntil(receivingFinished::get);
    Util.closeQuietly(rtspMessageChannel);
    serverListenThread.join();
    serverSocket.close();

    assertThat(sendingException.get()).isNull();
    assertThat(receivedRtspResponses)
        .containsExactly(
            /* optionsResponse */
            ImmutableList.of("RTSP/1.0 200 OK", "CSeq: 2", "Public: OPTIONS", ""),
            /* describeResponse */
            ImmutableList.of(
                "RTSP/1.0 200 OK",
                "CSeq: 3",
                "Content-Type: application/sdp",
                "Content-Length: 28",
                "",
                "v=安卓アンドロイド"),
            /* setupResponse */
            ImmutableList.of(
                "RTSP/1.0 200 OK", "CSeq: 3", "Transport: RTP/AVP/TCP;unicast;interleaved=0-1", ""))
        .inOrder();
    assertThat(receivedInterleavedData)
        .containsExactly(
            /* channel */ 0,
            Bytes.asList(Util.getBytesFromHexString("0102030405")),
            /* channel */ 1,
            Bytes.asList(Util.getBytesFromHexString("AABBCCDD")));
  }
}
