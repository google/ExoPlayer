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

import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Sends and receives RTSP messages. */
/* package */ final class RtspMessageChannel implements Closeable {

  private static final String TAG = "RtspMessageChannel";
  private static final boolean LOG_RTSP_MESSAGES = false;

  /** A listener for received RTSP messages and possible failures. */
  public interface MessageListener {

    /**
     * Called when an RTSP message is received.
     *
     * @param message The non-empty list of received lines, with line terminators removed.
     */
    void onRtspMessageReceived(List<String> message);

    /**
     * Called when failed to send an RTSP message.
     *
     * @param message The list of lines making up the RTSP message that is failed to send.
     * @param e The thrown {@link Exception}.
     */
    default void onSendingFailed(List<String> message, Exception e) {}

    /**
     * Called when failed to receive an RTSP message.
     *
     * @param e The thrown {@link Exception}.
     */
    default void onReceivingFailed(Exception e) {}
  }

  /**
   * The IANA-registered default port for RTSP. See <a
   * href="https://www.iana.org/assignments/service-names-port-numbers/service-names-port-numbers.xhtml">here</a>
   */
  public static final int DEFAULT_RTSP_PORT = 554;

  /**
   * The handler for all {@code messageListener} interactions. Backed by the thread on which this
   * class is constructed.
   */
  private final Handler messageListenerHandler;

  private final MessageListener messageListener;
  private final Loader receiverLoader;
  private @MonotonicNonNull Sender sender;
  private @MonotonicNonNull Socket socket;

  private boolean closed;

  /**
   * Constructs a new instance.
   *
   * <p>The constructor must be called on a {@link Looper} thread. The thread is also where {@link
   * MessageListener} events are sent. User must construct a socket for RTSP and call {@link
   * #openSocket} to open the connection before being able to send and receive, and {@link #close}
   * it when done.
   *
   * <p>Note: all method invocations must be made from the thread on which this class is created.
   *
   * @param messageListener The {@link MessageListener} to receive events.
   */
  public RtspMessageChannel(MessageListener messageListener) {
    this.messageListenerHandler = Util.createHandlerForCurrentLooper();
    this.messageListener = messageListener;
    this.receiverLoader = new Loader("ExoPlayer:RtspMessageChannel:ReceiverLoader");
  }

  /**
   * Opens the message channel to send and receive RTSP messages.
   *
   * <p>Note: If an {@link IOException} is thrown, callers must still call {@link #close()} to
   * ensure that any partial effects of the invocation are cleaned up.
   *
   * @param socket An accepted {@link Socket}.
   */
  public void openSocket(Socket socket) throws IOException {
    this.socket = socket;
    sender = new Sender(socket.getOutputStream());

    receiverLoader.startLoading(
        new Receiver(socket.getInputStream()),
        new LoaderCallbackImpl(),
        /* defaultMinRetryCount= */ 0);
  }

  /**
   * Closes the RTSP message channel.
   *
   * <p>The closed instance must not be re-opened again. The {@link MessageListener} will not
   * receive further messages after closing.
   *
   * @throws IOException If an error occurs closing the message channel.
   */
  @Override
  public void close() throws IOException {
    if (sender != null) {
      sender.close();
    }
    receiverLoader.release();

    if (socket != null) {
      socket.close();
    }

    messageListenerHandler.removeCallbacksAndMessages(/* token= */ null);
    closed = true;
  }

  /**
   * Sends a serialized RTSP message.
   *
   * @param message The list of strings representing the serialized RTSP message.
   */
  public void send(List<String> message) {
    checkStateNotNull(sender);
    sender.send(message);
  }

  private static void logMessage(List<String> rtspMessage) {
    if (LOG_RTSP_MESSAGES) {
      Log.d(TAG, Joiner.on('\n').join(rtspMessage));
    }
  }

  private final class Sender implements Closeable {

    private final OutputStream outputStream;
    private final HandlerThread senderThread;
    private final Handler senderThreadHandler;

    /**
     * Creates a new instance.
     *
     * @param outputStream The {@link OutputStream} of the opened RTSP {@link Socket}, to which the
     *     request is sent. The caller needs to close the {@link OutputStream}.
     */
    public Sender(OutputStream outputStream) {
      this.outputStream = outputStream;
      this.senderThread = new HandlerThread("ExoPlayer:RtspMessageChannel:Sender");
      this.senderThread.start();
      this.senderThreadHandler = new Handler(this.senderThread.getLooper());
    }

    /**
     * Sends out RTSP messages that are in the forms of lists of strings.
     *
     * <p>If {@link Exception} is thrown while sending, the message {@link
     * MessageListener#onSendingFailed} is dispatched to the thread that created the {@link
     * RtspMessageChannel}.
     *
     * @param message The must of strings representing the serialized RTSP message.
     */
    public void send(List<String> message) {
      logMessage(message);
      byte[] data = RtspMessageUtil.convertMessageToByteArray(message);
      senderThreadHandler.post(
          () -> {
            try {
              outputStream.write(data);
            } catch (Exception e) {
              messageListenerHandler.post(
                  () -> {
                    if (!closed) {
                      messageListener.onSendingFailed(message, e);
                    }
                  });
            }
          });
    }

    @Override
    public void close() {
      senderThreadHandler.post(senderThread::quit);
      try {
        // Waits until all the messages posted to the sender thread are handled.
        senderThread.join();
      } catch (InterruptedException e) {
        senderThread.interrupt();
      }
    }
  }

  /** A {@link Loadable} for receiving RTSP responses. */
  private final class Receiver implements Loadable {
    private final BufferedReader inputStreamReader;

    private volatile boolean loadCanceled;

    /**
     * Creates a new instance.
     *
     * @param inputStream The {@link InputStream} of the opened RTSP {@link Socket}, from which the
     *     {@link RtspResponse RtspResponses} are received. The caller needs to close the {@link
     *     InputStream}.
     */
    public Receiver(InputStream inputStream) {
      inputStreamReader = new BufferedReader(new InputStreamReader(inputStream, Charsets.UTF_8));
    }

    @Override
    public void cancelLoad() {
      loadCanceled = true;
    }

    @Override
    public void load() throws IOException {
      List<String> messageLines = new ArrayList<>();
      while (!loadCanceled) {
        String line;
        while (inputStreamReader.ready() && (line = inputStreamReader.readLine()) != null) {
          messageLines.add(line);
        }

        if (!messageLines.isEmpty()) {
          List<String> message = new ArrayList<>(messageLines);
          logMessage(message);
          messageListenerHandler.post(
              () -> {
                if (!closed) {
                  messageListener.onRtspMessageReceived(message);
                }
              });
          // Resets for the next response.
          messageLines.clear();
        }
      }
    }
  }

  private final class LoaderCallbackImpl implements Loader.Callback<Receiver> {
    @Override
    public void onLoadCompleted(Receiver loadable, long elapsedRealtimeMs, long loadDurationMs) {}

    @Override
    public void onLoadCanceled(
        Receiver loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {}

    @Override
    public LoadErrorAction onLoadError(
        Receiver loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      messageListener.onReceivingFailed(error);
      return Loader.DONT_RETRY;
    }
  }
}
