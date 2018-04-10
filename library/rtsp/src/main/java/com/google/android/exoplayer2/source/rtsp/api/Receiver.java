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
package com.google.android.exoplayer2.source.rtsp.api;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.IntDef;
import android.util.Log;

import com.google.android.exoplayer2.source.rtsp.core.Header;
import com.google.android.exoplayer2.source.rtsp.core.MediaType;
import com.google.android.exoplayer2.source.rtsp.core.Message;
import com.google.android.exoplayer2.source.rtsp.core.MessageBody;
import com.google.android.exoplayer2.source.rtsp.core.Method;
import com.google.android.exoplayer2.source.rtsp.core.Protocol;
import com.google.android.exoplayer2.source.rtsp.core.Request;
import com.google.android.exoplayer2.source.rtsp.core.Response;
import com.google.android.exoplayer2.source.rtsp.core.Status;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* package */ final class Receiver {

    public interface EventListener {
        void onReceiveSuccess(Request request);
        void onReceiveSuccess(Response response);
        void onReceiveFailure(@ErrorCode int errorCode);
    }

    private static final int MAX_SDP_LENGTH = 2048;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {PARSING_START_LINE, PARSING_HEADER_LINE, PARSING_BODY_LINE})
    private @interface State {}

    private final static int PARSING_START_LINE = 1;
    private final static int PARSING_HEADER_LINE = 2;
    private final static int PARSING_BODY_LINE = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {IO_ERROR, PARSE_ERROR})
    public @interface ErrorCode {}

    public final static int IO_ERROR = 1;
    public final static int PARSE_ERROR = 2;

    private final Handler handler;
    private final HandlerThread thread;

    private static final Pattern regexStatus = Pattern.compile("(RTSP/\\d.\\d) (\\d+) (\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern regexRequest = Pattern.compile("([A-Z_]+) rtsp://(.+) RTSP/(\\d.\\d)", Pattern.CASE_INSENSITIVE);
    private static final Pattern rexegHeader = Pattern.compile("(\\S+):\\s+(.+)",Pattern.CASE_INSENSITIVE);

    private final BufferedReader reader;
    private final char[] body;

    private final EventListener eventListener;

    private boolean canceled;

    private @State int state;

    public Receiver(InputStream inputStream, EventListener eventListener) {
        this.eventListener = eventListener;

        body = new char[MAX_SDP_LENGTH];

        state = PARSING_START_LINE;
        reader = new BufferedReader(new InputStreamReader(inputStream));

        thread = new HandlerThread("Receiver:Handler", Process.THREAD_PRIORITY_AUDIO);
        thread.start();

        handler = new Handler(thread.getLooper());

        handler.post(loader);
    }

    public void cancel() {
        if (!canceled) {
            thread.quit();
            canceled = true;
        }
    }

    private void close() {
        try {

            if (reader != null) {
                reader.close();
            }

        } catch (IOException e) {
            // Ignore.
        }
    }

    private void handleMessage(Message message) {
        Log.v("Receiver", message.toString());
        switch (message.getType()) {
            case Message.REQUEST:
                eventListener.onReceiveSuccess((Request)message);
                break;

            case Message.RESPONSE:
                eventListener.onReceiveSuccess((Response)message);
                break;
        }
    }

    private Runnable loader = new Runnable() {

        private Message.Builder builder;

        private void parseMessageBody(MediaType mediaType, int mediaLength) throws NullPointerException, IOException {
            int receivedLength = reader.read(body, 0, MAX_SDP_LENGTH);
            StringBuilder stringBuilder = new StringBuilder(new String(body, 0, receivedLength));

            if (receivedLength < mediaLength) {
                Log.w("Receiver", "Premature end of Content-Length " +
                        "delimited message body (expected: " + mediaLength +
                        ", received: " + receivedLength);
            }

            MessageBody messageBody = new MessageBody(mediaType, stringBuilder.toString());
            handleMessage(builder.body(messageBody).build());
        }

        @Override
        public void run() {
            String line;
            Matcher matcher;
            int mediaLength = 0;
            MediaType mediaType = null;

            try {

                while (!Thread.currentThread().isInterrupted() && !canceled) {

                    try {

                        if ((line = reader.readLine()) == null) {
                            continue;
                        }

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

                                        } else {
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

                    } catch (NullPointerException ex) {
                        state = PARSING_START_LINE;
                        builder = null;
                        eventListener.onReceiveFailure(PARSE_ERROR);

                    } catch (IllegalArgumentException ex) {
                        state = PARSING_START_LINE;
                        builder = null;
                        eventListener.onReceiveFailure(PARSE_ERROR);

                    } catch (IOException ex) {
                        state = PARSING_START_LINE;
                        builder = null;
                        eventListener.onReceiveFailure(IO_ERROR);
                    }
                }

            } finally {
                close();
            }
        }
    };

}