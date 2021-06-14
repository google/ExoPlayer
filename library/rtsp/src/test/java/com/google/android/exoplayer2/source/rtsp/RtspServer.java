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

import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_DESCRIBE;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_OPTIONS;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableMap;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** The RTSP server. */
public final class RtspServer implements Closeable {

  private static final String PUBLIC_SUPPORTED_METHODS = "OPTIONS, DESCRIBE";

  /** RTSP error Method Not Allowed (RFC2326 Section 7.1.1). */
  private static final int STATUS_OK = 200;

  private static final int STATUS_METHOD_NOT_ALLOWED = 405;

  private static final String SESSION_DESCRIPTION =
      "v=0\r\n"
          + "o=- 1606776316530225 1 IN IP4 127.0.0.1\r\n"
          + "s=Exoplayer test\r\n"
          + "t=0 0\r\n"
          + "a=range:npt=0-50.46\r\n";

  private final Thread listenerThread;
  /** Runs on the thread on which the constructor was called. */
  private final Handler mainHandler;

  private final RtpPacketStreamDump rtpPacketStreamDump;

  private @MonotonicNonNull ServerSocket serverSocket;
  private @MonotonicNonNull RtspMessageChannel connectedClient;

  private volatile boolean isCanceled;

  /**
   * Creates a new instance.
   *
   * <p>The constructor must be called on a {@link Looper} thread.
   */
  public RtspServer(RtpPacketStreamDump rtpPacketStreamDump) {
    this.rtpPacketStreamDump = rtpPacketStreamDump;
    listenerThread =
        new Thread(this::listenToIncomingRtspConnection, "ExoPlayerTest:RtspConnectionMonitor");
    mainHandler = Util.createHandlerForCurrentLooper();
  }

  /**
   * Starts the server. The server starts listening to incoming RTSP connections.
   *
   * <p>The user must call {@link #close} if {@link IOException} is thrown. Closed instances must
   * not be started again.
   *
   * @return The server side port number for RTSP connections.
   */
  public int startAndGetPortNumber() throws IOException {
    // Auto assign port and allow only one client connection (backlog).
    serverSocket =
        new ServerSocket(/* port= */ 0, /* backlog= */ 1, InetAddress.getByName(/* host= */ null));
    listenerThread.start();
    return serverSocket.getLocalPort();
  }

  @Override
  public void close() throws IOException {
    isCanceled = true;
    if (serverSocket != null) {
      serverSocket.close();
    }
    if (connectedClient != null) {
      connectedClient.close();
    }
  }

  private void handleNewClientConnected(Socket socket) {
    try {
      connectedClient = new RtspMessageChannel(new MessageListener());
      connectedClient.open(socket);
    } catch (IOException e) {
      Util.closeQuietly(connectedClient);
      // Log the error.
      e.printStackTrace();
    }
  }

  private final class MessageListener implements RtspMessageChannel.MessageListener {
    @Override
    public void onRtspMessageReceived(List<String> message) {
      mainHandler.post(() -> handleRtspMessage(message));
    }

    private void handleRtspMessage(List<String> message) {
      RtspRequest request = RtspMessageUtil.parseRequest(message);
      String cSeq = checkNotNull(request.headers.get(RtspHeaders.CSEQ));
      switch (request.method) {
        case METHOD_OPTIONS:
          onOptionsRequestReceived(cSeq);
          break;

        case METHOD_DESCRIBE:
          onDescribeRequestReceived(request.uri, cSeq);
          break;

        default:
          sendErrorResponse(STATUS_METHOD_NOT_ALLOWED, cSeq);
      }
    }

    private void onOptionsRequestReceived(String cSeq) {
      sendResponseWithCommonHeaders(
          /* status= */ STATUS_OK,
          /* cSeq= */ cSeq,
          /* additionalHeaders= */ ImmutableMap.of(RtspHeaders.PUBLIC, PUBLIC_SUPPORTED_METHODS),
          /* messageBody= */ "");
    }

    private void onDescribeRequestReceived(Uri requestedUri, String cSeq) {
      String sdpMessage = SESSION_DESCRIPTION + rtpPacketStreamDump.mediaDescription + "\r\n";
      sendResponseWithCommonHeaders(
          /* status= */ STATUS_OK,
          /* cSeq= */ cSeq,
          /* additionalHeaders= */ ImmutableMap.of(
              RtspHeaders.CONTENT_BASE, requestedUri.toString(),
              RtspHeaders.CONTENT_TYPE, "application/sdp",
              RtspHeaders.CONTENT_LENGTH, String.valueOf(sdpMessage.length())),
          /* messageBody= */ sdpMessage);
    }

    private void sendErrorResponse(int status, String cSeq) {
      sendResponseWithCommonHeaders(
          status, cSeq, /* additionalHeaders= */ ImmutableMap.of(), /* messageBody= */ "");
    }

    private void sendResponseWithCommonHeaders(
        int status, String cSeq, Map<String, String> additionalHeaders, String messageBody) {
      RtspHeaders.Builder headerBuilder = new RtspHeaders.Builder();
      headerBuilder.add(RtspHeaders.CSEQ, cSeq);
      headerBuilder.addAll(additionalHeaders);
      connectedClient.send(
          RtspMessageUtil.serializeResponse(
              new RtspResponse(
                  /* status= */ status,
                  /* headers= */ headerBuilder.build(),
                  /* messageBody= */ messageBody)));
    }
  }

  private void listenToIncomingRtspConnection() {
    while (!isCanceled) {
      try {
        Socket acceptedClientSocket = serverSocket.accept();
        mainHandler.post(() -> handleNewClientConnected(acceptedClientSocket));
      } catch (SocketException e) {
        // SocketException is thrown when serverSocket is closed while running accept().
        if (!isCanceled) {
          isCanceled = true;
          e.printStackTrace();
        }
      } catch (IOException e) {
        isCanceled = true;
        // Log the error.
        e.printStackTrace();
      }
    }
  }
}
