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

import android.support.annotation.IntDef;

import com.google.android.exoplayer2.source.rtsp.core.Header;
import com.google.android.exoplayer2.source.rtsp.core.Message;
import com.google.android.exoplayer2.source.rtsp.core.Method;
import com.google.android.exoplayer2.source.rtsp.core.Request;
import com.google.android.exoplayer2.source.rtsp.core.Response;
import com.google.android.exoplayer2.source.rtsp.core.Status;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.net.SocketFactory;

/* package */ final class Dispatcher implements Sender.EventListener, Receiver.EventListener {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {NONE, CONNECTED, FAILURE, DISCONNECTED})
    protected @interface State {}

    protected final static int NONE = 0;
    protected final static int CONNECTED = 1;
    protected final static int FAILURE = 2;
    protected final static int DISCONNECTED = 3;

    private final static int DEFAULT_TIMEOUT_MILLIS = 5000;
    private final static int DEFAULT_PORT = 554;

    private final RequestMonitor requestMonitor;

    Socket socket;
    InetAddress address;

    Receiver receiver;
    Sender sender;

    final Client client;

    final Map<Integer, Request> outstanding;
    final Map<Integer, Request> requests;

    Dispatcher(Builder builder) {
        client = builder.client;
        outstanding =  Collections.synchronizedMap(new LinkedHashMap());
        requests = Collections.synchronizedMap(new LinkedHashMap());

        requestMonitor = new RequestMonitor();
    }

    public void connect() throws IOException {
        socket = SocketFactory.getDefault().createSocket();

        address = InetAddress.getByName(client.session.uri().getHost());

        int port = client.session.uri().getPort();

        socket.connect(new InetSocketAddress(address, (port > 0) ? port : DEFAULT_PORT),
                DEFAULT_TIMEOUT_MILLIS);

        sender = new Sender(socket.getOutputStream(), this);
        receiver = new Receiver(socket.getInputStream(), this);
    }

    public void close() {
        sender.cancel();
        receiver.cancel();
        requestMonitor.stop();

        requests.clear();
        outstanding.clear();

        closeQuietly();
    }

    private void closeQuietly() {
        try {

            if (socket != null) {
                socket.close();
            }

        } catch (IOException e) {
            // Ignore.
        }
    }

    public void execute(Message message) {
        synchronized (this) {
            if (message.getType() == Message.REQUEST) {
                if (requests.isEmpty()) {
                    sender.send(message);
                }

                Integer cSeq = Integer.parseInt(message.getHeaders().value(Header.CSeq));
                requests.put(cSeq, (Request)message);
            }
        }
    }


    // Sender.EventListener implementation
    @Override
    public void onSendSuccess(Message message) {
        if (message.getType() == Message.REQUEST) {
            Integer cSeq = Integer.parseInt(message.getHeaders().value(Header.CSeq));
            outstanding.put(cSeq, (Request)message);
            requestMonitor.wait(message);
        }
    }

    @Override
    public void onSendFailure(Message message) {
        if (message.getType() == Message.REQUEST) {
            Integer cSeq = Integer.parseInt(message.getHeaders().value(Header.CSeq));
            requests.remove(cSeq);
        }
        client.onIOError();
    }


    // Receiver.EventListener implementation
    @Override
    public void onReceiveSuccess(Request request) {
        if (client.isProtocolSupported(request.getProtocol())) {

            Method method = request.getMethod();

            if (client.isMethodSupported(method)) {

                if (request.getHeaders().value(Header.CSeq) == null) {
                    Response.Builder builder = new Response.Builder().status(Status.BadRequest);
                    builder.header(Header.UserAgent, client.userAgent());

                    execute(builder.build());

                } else {

                    String require = request.getHeaders().value(Header.Require);

                    if (require == null) {

                        if (method.equals(Method.ANNOUNCE) ||
                                method.equals(Method.GET_PARAMETER) ||
                                method.equals(Method.SET_PARAMETER) ||
                                method.equals(Method.REDIRECT)) {

                            if (request.getHeaders().value(Header.Session) == null) {

                                Response.Builder builder = new Response.Builder().
                                        status(Status.SessionNotFound);
                                builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
                                builder.header(Header.UserAgent, client.userAgent());

                                execute(builder.build());

                            } else {

                                if (method.equals(Method.ANNOUNCE)) {
                                    client.onAnnounceRequest(request);

                                } else if (method.equals(Method.GET_PARAMETER)) {
                                    client.onGetParameterRequest(request);

                                } else if (method.equals(Method.SET_PARAMETER)) {
                                    client.onSetParameterRequest(request);

                                } else {
                                    client.onRedirectRequest(request);
                                }
                            }

                        } else if (method.equals(Method.OPTIONS)) {
                            client.onOptionsRequest(request);

                        } else {

                            Response.Builder builder = new Response.Builder().
                                    status(Status.MethodNotAllowed);
                            builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
                            builder.header(Header.UserAgent, client.userAgent());

                            execute(builder.build());
                        }

                    } else {

                        Response.Builder builder = new Response.Builder().status(
                                Status.OptionNotSupported);
                        builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
                        builder.header(Header.UserAgent, client.userAgent());
                        builder.header(Header.Unsupported, require);

                        execute(builder.build());
                    }
                }

            } else {

                Response.Builder builder = new Response.Builder().status(Status.NotImplemented);
                builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
                builder.header(Header.UserAgent, client.userAgent());

                execute(builder.build());
            }

        } else {
            Response.Builder builder = new Response.Builder().status(Status.RtspVersionNotSupported);
            builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
            builder.header(Header.UserAgent, client.userAgent());

            execute(builder.build());
        }
    }

    @Override
    public void onReceiveSuccess(Response response) {
        if (client.isProtocolSupported(response.getProtocol())) {

            if (response.getHeaders() != null && response.getHeaders().contains(Header.CSeq)) {
                Integer cSeq = Integer.parseInt(response.getHeaders().value(Header.CSeq));

                if (outstanding.containsKey(cSeq)) {

                    if (response.isSuccess()) {

                        Request request = outstanding.remove(cSeq);
                        Method method = request.getMethod();

                        requestMonitor.cancel(request);

                        if (method.equals(Method.ANNOUNCE)) {
                            client.onAnnounceResponse(response);
                        } else if (method.equals(Method.DESCRIBE)) {
                            client.onDescribeResponse(response);
                        } else if (method.equals(Method.GET_PARAMETER)) {
                            client.onGetParameterResponse(response);
                        } else if (method.equals(Method.OPTIONS)) {
                            client.onOptionsResponse(response);
                        } else if (method.equals(Method.PAUSE)) {
                            client.onPauseResponse(response);
                        } else if (method.equals(Method.PLAY)) {
                            client.onPlayResponse(response);
                        } else if (method.equals(Method.RECORD)) {
                            client.onRecordResponse(response);
                        } else if (method.equals(Method.SET_PARAMETER)) {
                            client.onSetParameterResponse(response);
                        } else if (method.equals(Method.SETUP)) {
                            client.onSetupResponse(response);
                        } else if (method.equals(Method.TEARDOWN)) {
                            client.onTeardownResponse(response);
                        }

                    } else {

                        Request request = outstanding.remove(cSeq);
                        requestMonitor.cancel(request);

                        if (response.getStatus().equals(Status.Unauthorized)) {
                            client.onUnauthorized(request, response);

                        } else {
                            client.onUnSuccess(request, response);
                        }
                    }

                    dispatchNextRequestInQueue(cSeq);

                } else {

                    if (response.getStatus().equals(Status.RequestTimeOut)) {
                        client.onTimeOut();

                    } else {

                        Response.Builder builder = new Response.Builder().status(Status.BadRequest);
                        builder.header(Header.UserAgent, client.userAgent());

                        execute(builder.build());
                    }
                }
            } else {
                client.onBadRequest();
            }

        } else {

            Response.Builder builder = new Response.Builder().status(Status.RtspVersionNotSupported);
            builder.header(Header.CSeq, response.getHeaders().value(Header.CSeq));
            builder.header(Header.UserAgent, client.userAgent());

            execute(builder.build());
        }
    }

    @Override
    public void onReceiveFailure(@Receiver.ErrorCode int errorCode) {
        if (errorCode == Receiver.PARSE_ERROR) {
            Response.Builder builder = new Response.Builder().status(Status.BadRequest);
            builder.header(Header.UserAgent, client.userAgent());

            execute(builder.build());

        } else if (errorCode == Receiver.IO_ERROR) {
            client.onIOError();
        }
    }

    private void dispatchNextRequestInQueue(Integer CSeq) {
        synchronized (this) {
            requests.remove(CSeq);

            if (requests.containsKey(CSeq + 1)) {
                Request request = requests.get(CSeq + 1);
                sender.send(request);
            }
        }
    }

    /**
     * Monitor the request/reply message.
     */
    /* package */ final class RequestMonitor {
        private final long DEFAULT_TIMEOUT_REQUEST = 3000;

        private final ExecutorService executorService = Executors.newSingleThreadExecutor();
        private final Map<Integer, Future<?>> tasks;

        public RequestMonitor() {
            tasks = Collections.synchronizedMap(new LinkedHashMap());
        }

        public void cancel(Message message) {
            Integer cSeq = Integer.parseInt(message.getHeaders().value(Header.CSeq));
            if (tasks.containsKey(cSeq)) {
                Future<?> task = tasks.remove(cSeq);
                task.cancel(true);
            }
        }

        public void stop() {
            executorService.shutdown();
        }

        public synchronized void wait(Message message) {
            final Integer cSeq = Integer.parseInt(message.getHeaders().value(Header.CSeq));

            if (!Thread.currentThread().isInterrupted()) {
                tasks.put(cSeq, executorService.submit(new Runnable() {
                    @Override
                    public void run() {

                        try {

                            if (!Thread.currentThread().isInterrupted()) {
                                Thread.sleep(DEFAULT_TIMEOUT_REQUEST);
                            }

                            if (outstanding.containsKey(cSeq)) {
                                client.onTimeOut();
                            }

                        } catch (InterruptedException ex) {
                        }
                    }
                }));
            }
        }
    }

    public static class Builder {
        Client client;

        public Builder client(Client client) {
            if (client == null) throw new NullPointerException("client == null");

            this.client = client;
            return this;
        }

        public Dispatcher build() {
            if (client == null) throw new IllegalArgumentException("client == null");

            return new Dispatcher(this);
        }
    }
}
