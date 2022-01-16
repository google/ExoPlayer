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
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_PLAY;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_SETUP;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_TEARDOWN;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.google.android.exoplayer2.util.Util;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** The RTSP server. */
public final class RtspServer implements Closeable {

  /** Provides RTSP response. */
  public interface ResponseProvider {

    /** Returns an RTSP OPTIONS {@link RtspResponse response}. */
    RtspResponse getOptionsResponse();

    /** Returns an RTSP DESCRIBE {@link RtspResponse response}. */
    default RtspResponse getDescribeResponse(Uri requestedUri, RtspHeaders headers) {
      return RtspTestUtils.RTSP_ERROR_METHOD_NOT_ALLOWED;
    }

    /** Returns an RTSP SETUP {@link RtspResponse response}. */
    default RtspResponse getSetupResponse(Uri requestedUri, RtspHeaders headers) {
      return RtspTestUtils.RTSP_ERROR_METHOD_NOT_ALLOWED;
    }

    /** Returns an RTSP PLAY {@link RtspResponse response}. */
    default RtspResponse getPlayResponse() {
      return RtspTestUtils.RTSP_ERROR_METHOD_NOT_ALLOWED;
    }

    /** Returns an RTSP TEARDOWN {@link RtspResponse response}. */
    default RtspResponse getTearDownResponse() {
      return new RtspResponse(/* status= */ 200, RtspHeaders.EMPTY);
    }
  }

  private final Thread listenerThread;
  /** Runs on the thread on which the constructor was called. */
  private final Handler mainHandler;

  private final ResponseProvider responseProvider;

  private @MonotonicNonNull ServerSocket serverSocket;
  private @MonotonicNonNull RtspMessageChannel connectedClient;

  private volatile boolean isCanceled;

  /**
   * Creates a new instance.
   *
   * <p>The constructor must be called on a {@link Looper} thread.
   *
   * @param responseProvider A {@link ResponseProvider}.
   */
  public RtspServer(ResponseProvider responseProvider) {
    listenerThread =
        new Thread(this::listenToIncomingRtspConnection, "ExoPlayerTest:RtspConnectionMonitor");
    mainHandler = Util.createHandlerForCurrentLooper();
    this.responseProvider = responseProvider;
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
          sendResponse(responseProvider.getOptionsResponse(), cSeq);
          break;

        case METHOD_DESCRIBE:
          sendResponse(responseProvider.getDescribeResponse(request.uri, request.headers), cSeq);
          break;

        case METHOD_SETUP:
          sendResponse(responseProvider.getSetupResponse(request.uri, request.headers), cSeq);
          break;

        case METHOD_PLAY:
          sendResponse(responseProvider.getPlayResponse(), cSeq);
          break;

        case METHOD_TEARDOWN:
          sendResponse(responseProvider.getTearDownResponse(), cSeq);
          break;

        default:
          sendResponse(RtspTestUtils.RTSP_ERROR_METHOD_NOT_ALLOWED, cSeq);
      }
    }

    private void sendResponse(RtspResponse response, String cSeq) {
      connectedClient.send(
          RtspMessageUtil.serializeResponse(
              new RtspResponse(
                  response.status,
                  response.headers.buildUpon().add(RtspHeaders.CSEQ, cSeq).build(),
                  response.messageBody)));
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
