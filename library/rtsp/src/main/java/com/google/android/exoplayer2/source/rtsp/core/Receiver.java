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
package com.google.android.exoplayer2.source.rtsp.core;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import androidx.annotation.IntDef;
import android.util.Log;

import com.google.android.exoplayer2.source.rtsp.message.InterleavedFrame;
import com.google.android.exoplayer2.source.rtsp.message.Header;
import com.google.android.exoplayer2.source.rtsp.media.MediaType;
import com.google.android.exoplayer2.source.rtsp.message.Message;
import com.google.android.exoplayer2.source.rtsp.message.MessageBody;
import com.google.android.exoplayer2.source.rtsp.message.Method;
import com.google.android.exoplayer2.source.rtsp.message.Protocol;
import com.google.android.exoplayer2.source.rtsp.message.Request;
import com.google.android.exoplayer2.source.rtsp.message.Response;
import com.google.android.exoplayer2.source.rtsp.message.Status;
import com.google.android.exoplayer2.util.Util;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* package */ final class Receiver {

  public interface EventListener {
      void onReceiveSuccess(Request request);
      void onReceiveSuccess(Response response);
      void onReceiveSuccess(InterleavedFrame interleavedFrame);
      void onReceiveFailure(@ErrorCode int errorCode);
  }

  private static final Pattern regexStatus = Pattern.compile(
          "(RTSP/\\d.\\d) (\\d+) (\\w+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern regexRequest = Pattern.compile(
          "([A-Z_]+) rtsp://(.+) RTSP/(\\d.\\d)", Pattern.CASE_INSENSITIVE);
  private static final Pattern rexegHeader = Pattern.compile(
          "(\\S+):\\s+(.+)", Pattern.CASE_INSENSITIVE);

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(flag = true, value = {PARSING_START_LINE, PARSING_HEADER_LINE, PARSING_BODY_LINE})
  private @interface State {
  }

  private final static int PARSING_START_LINE = 1;
  private final static int PARSING_HEADER_LINE = 2;
  private final static int PARSING_BODY_LINE = 3;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(flag = true, value = {IO_ERROR, PARSE_ERROR})
  public @interface ErrorCode {
  }

  public final static int IO_ERROR = 1;
  public final static int PARSE_ERROR = 2;

  private final Handler handler;
  private final HandlerThread thread;

  private final InputStream inputStream;
  private final EventListener eventListener;

  private volatile boolean canceled;

  private @State int state;

  public Receiver(InputStream inputStream, EventListener eventListener) {
    this.inputStream = inputStream;
    this.eventListener = eventListener;

    state = PARSING_START_LINE;

    thread = new HandlerThread("Receiver:Handler", Process.THREAD_PRIORITY_AUDIO);
    thread.start();

    handler = new Handler(thread.getLooper());
    handler.post(loader);
  }

  public void cancel() {
    if (!canceled) {
        canceled = true;
        thread.quit();
    }
  }

  private void handleMessage(Message message) {
    Log.v("Receiver", message.toString());
    switch (message.getType()) {
        case Message.REQUEST:
            eventListener.onReceiveSuccess((Request) message);
            break;

        case Message.RESPONSE:
            eventListener.onReceiveSuccess((Response) message);
            break;
    }
  }

  private void handleInterleavedFrame(InterleavedFrame interleavedFrame) {
      eventListener.onReceiveSuccess(interleavedFrame);
  }

  private Runnable loader = new Runnable() {

    private Message.Builder builder;
    private BufferedReader reader;

    private void parseMessageBody(MediaType mediaType, int mediaLength)
            throws NullPointerException, IOException {
      byte[] body = new byte[mediaLength];
      reader.readFully(body, 0, mediaLength);
      StringBuilder stringBuilder = new StringBuilder(new String(body, 0, mediaLength));

      MessageBody messageBody = new MessageBody(mediaType, stringBuilder.toString());
      handleMessage(builder.body(messageBody).build());
    }

    @Override
    public void run() {
      String line;
      Matcher matcher;
      int mediaLength = 0;
      MediaType mediaType = null;
      boolean error = false;
      byte[] firstFourBytes = new byte[4];

      reader = new BufferedReader(new DataInputStream(inputStream));

      try {

        while (!Thread.currentThread().isInterrupted() && !canceled && !error) {

          try {

            byte firstByte = reader.peekByte();

            if ((firstByte & 0xFF) == 0x24) {

              reader.readFully(firstFourBytes, 0, 4);

              int channel = firstFourBytes[1];
              int length = ((firstFourBytes[2] & 0xFF) << 8) |
                  (firstFourBytes[3] & 0xFF);

              byte[] data = new byte[length];

              reader.readFully(data, 0, length);

              handleInterleavedFrame(new InterleavedFrame(channel, data));

            } else {

              line = reader.readLine();

              switch (state) {
                case PARSING_START_LINE:
                  // Parses a request or status line
                  matcher = regexRequest.matcher(line);
                  if (matcher.find()) {
                    Method method = Method.parse(matcher.group(1));
                    String url = matcher.group(2);
                    Protocol protocol = Protocol.parse(matcher.group(3));

                    builder = new Request.Builder().method(method).url(url).protocol(protocol);
                    state = PARSING_HEADER_LINE;

                  } else {
                    matcher = regexStatus.matcher(line);
                    if (matcher.find()) {

                      Protocol protocol = Protocol.parse(matcher.group(1));
                      Status status = Status.parse(Integer.parseInt(matcher.group(2)));

                      builder = new Response.Builder().protocol(protocol).status(status);
                      state = PARSING_HEADER_LINE;
                    }
                  }

                  break;

                case PARSING_HEADER_LINE:
                  // Parses a general, request, response or entity header
                  if (line.length() > 0) {
                    matcher = rexegHeader.matcher(line);
                    if (matcher.find()) {
                      Header header = Header.parse(matcher.group(1));

                      if (header == Header.ContentType) {
                        mediaType = MediaType.parse(matcher.group(2).trim());

                      } else if (header == Header.ContentLength) {
                        mediaLength = Integer.parseInt(matcher.group(2).trim());

                      } else if (header != null) {
                        builder.header(header, matcher.group(2).trim());
                      }
                    }

                  } else {
                    if (mediaLength > 0) {
                      parseMessageBody(mediaType, mediaLength);
                      mediaLength = 0;
                      state = PARSING_START_LINE;

                    } else {
                      handleMessage(builder.build());
                      state = PARSING_START_LINE;
                    }

                    builder = null;
                  }

                  break;
              }
            }

          } catch (NullPointerException | IllegalArgumentException ex) {
            builder = null;
            state = PARSING_START_LINE;
            eventListener.onReceiveFailure(PARSE_ERROR);

          } catch (IOException ex) {
            error = true;
            builder = null;
            state = PARSING_START_LINE;
            if (!canceled) {
              eventListener.onReceiveFailure(IO_ERROR);
            }
          }
        }
      } finally {

      }
    }
  };

  /* package */ final static class BufferedReader {
    private static final int CR = 13;
    private static final int LF = 10;

    private static final int PEEK_MIN_FREE_SPACE_AFTER_RESIZE = 1024;
    private static final int PEEK_MAX_FREE_SPACE = 8 * 1024;

    private int peekBufferOffset;
    private int peekBufferLength;
    private int peekBufferPosition;

    private byte[] peekBuffer;

    private final DataInputStream inputStream;

    public BufferedReader(DataInputStream inputStream) {
      this.inputStream = inputStream;
      peekBuffer = new byte[PEEK_MIN_FREE_SPACE_AFTER_RESIZE];
    }

    private void readFullyFromInputStream(byte[] target, int offset, int length) throws IOException {
      int bytesLeft = length;
      inputStream.readFully(target, offset, bytesLeft);
    }

    private boolean ready() {
      try {
        return inputStream.available() > 0;
      } catch (IOException x) {
        return false;
      }
    }

    private void ensureSpaceForPeek(int length) {
      int requiredLength = peekBufferPosition + length;
      if (requiredLength > peekBuffer.length) {
        int newPeekCapacity = Util.constrainValue(peekBuffer.length * 2,
                requiredLength + PEEK_MIN_FREE_SPACE_AFTER_RESIZE,
                requiredLength + PEEK_MAX_FREE_SPACE);
        peekBuffer = Arrays.copyOf(peekBuffer, newPeekCapacity);
      }
    }

    private void updatePeekBuffer(int bytesConsumed) {
      peekBufferLength -= bytesConsumed;
      peekBufferPosition -= bytesConsumed;
      peekBufferOffset = 0;
      byte[] newPeekBuffer = peekBuffer;
      if (peekBufferLength < peekBuffer.length - PEEK_MAX_FREE_SPACE) {
        newPeekBuffer = new byte[peekBufferLength + PEEK_MIN_FREE_SPACE_AFTER_RESIZE];
      }
      System.arraycopy(peekBuffer, bytesConsumed, newPeekBuffer, 0, peekBufferLength);
      peekBuffer = newPeekBuffer;
    }

    private int readFromPeekBuffer(byte[] target, int offset, int length) {
      if (peekBufferLength == 0) {
        return 0;
      }
      int peekBytes = Math.min(peekBufferLength, length);
      System.arraycopy(peekBuffer, 0, target, offset, peekBytes);
      updatePeekBuffer(peekBytes);
      return peekBytes;
    }

    public void readFully(byte[] target, int offset, int length) throws IOException {
      int bytesRead = readFromPeekBuffer(target, offset, length);
      if (bytesRead < length) {
        readFullyFromInputStream(target, offset + bytesRead, length - bytesRead);
      }
    }

    public byte peekByte() throws IOException {
      if (peekBufferLength == 0) {
        ensureSpaceForPeek(1);
        readFullyFromInputStream(peekBuffer, peekBufferPosition, 1);

        peekBufferPosition++;
        peekBufferLength = Math.max(peekBufferLength, peekBufferPosition);
      }

      return peekBuffer[peekBufferOffset++];
    }

    private int getPeekUnsignedByte() {
      return peekBuffer[peekBufferOffset++] & 0xFF;
    }

    public String readLine() throws IOException {
      boolean foundCr = false;
      boolean notFoundCrLf = true;

      StringBuilder builder = new StringBuilder();

      peekBufferOffset = 0;

      while (notFoundCrLf) {
        if (peekBufferLength == 0) {
          if (ready()) {
            int available = inputStream.available();
            if (available > 0) {
              ensureSpaceForPeek(available);
              readFullyFromInputStream(peekBuffer, peekBufferPosition, available);

              peekBufferPosition += available;
              peekBufferLength = Math.max(peekBufferLength, peekBufferPosition);
            }
          }
        } else {
          while (notFoundCrLf && peekBufferOffset < peekBufferPosition) {
            int character = getPeekUnsignedByte();

            if (character == CR) {
              foundCr = true;

            } else if (foundCr && character == LF) {
              notFoundCrLf = false;

            } else {
              foundCr = false;
              builder.append((char) character);
            }
          }

          updatePeekBuffer(peekBufferOffset);
        }
      }

      return builder.toString();
    }

  }
}
