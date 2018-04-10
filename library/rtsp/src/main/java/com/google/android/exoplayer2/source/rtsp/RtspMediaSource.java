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
package com.google.android.exoplayer2.source.rtsp;

import android.net.Uri;
import android.os.Handler;
import android.support.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.rtsp.api.Client;
import com.google.android.exoplayer2.source.rtsp.core.MediaType;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;

import java.io.IOException;

public final class RtspMediaSource extends BaseMediaSource implements Client.EventListener {

    static {
        ExoPlayerLibraryInfo.registerModule("goog.exo.rtsp");
    }

    /** Factory for {@link RtspMediaSource}. */
    public static final class Factory {
        private final Client.Factory<? extends Client> factory;

        private int minLoadableRetryCount;
        private boolean treatLoadErrorsAsEndOfStream;
        private boolean isCreateCalled;

        /**
         * Creates a factory for {@link RtspMediaSource}s.
         *
         * @param factory The factory from which read the media will
         *     be obtained.
         */
        public Factory(Client.Factory<? extends Client> factory) {
            this.factory = Assertions.checkNotNull(factory);
            this.minLoadableRetryCount = DEFAULT_MIN_LOADABLE_RETRY_COUNT;
        }

        /**
         * Sets the minimum number of times to retry if a loading error occurs. The default value is
         * {@link #DEFAULT_MIN_LOADABLE_RETRY_COUNT}.
         *
         * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
         * @return This factory, for convenience.
         * @throws IllegalStateException If one of the {@code create} methods has already been called.
         */
        public Factory setMinLoadableRetryCount(int minLoadableRetryCount) {
            Assertions.checkState(!isCreateCalled);
            this.minLoadableRetryCount = minLoadableRetryCount;
            return this;
        }

        /**
         * Sets whether load errors will be treated as end-of-stream signal (load errors will not be
         * propagated). The default value is false.
         *
         * @param treatLoadErrorsAsEndOfStream If true, load errors will not be propagated by sample
         *     streams, treating them as ended instead. If false, load errors will be propagated
         *     normally by {@link RtspSampleStream#maybeThrowError()}.
         * @return This factory, for convenience.
         * @throws IllegalStateException If one of the {@code create} methods has already been called.
         */
        public Factory setTreatLoadErrorsAsEndOfStream(boolean treatLoadErrorsAsEndOfStream) {
            Assertions.checkState(!isCreateCalled);
            this.treatLoadErrorsAsEndOfStream = treatLoadErrorsAsEndOfStream;
            return this;
        }

        /**
         * Returns a new {@link RtspMediaSource} using the current parameters. Media source events
         * will not be delivered.
         *
         * @param uri The {@link Uri}.
         * @return The new {@link RtspMediaSource}.
         */
        public RtspMediaSource createMediaSource(Uri uri) {
            isCreateCalled = true;
            return new RtspMediaSource(
                    uri,
                    factory,
                    minLoadableRetryCount,
                    treatLoadErrorsAsEndOfStream);
        }

        /**
         * Returns a new {@link RtspMediaSource} using the current parameters. Media source events
         * will not be delivered.
         *
         * @param uri The {@link Uri}.
         * @param eventHandler A handler for events.
         * @param eventListener A listener of events.
         * @return The new {@link RtspMediaSource}.
         */
        public RtspMediaSource createMediaSource(Uri uri,
                                                 @Nullable Handler eventHandler,
                                                 @Nullable MediaSourceEventListener eventListener) {
            RtspMediaSource mediaSource = createMediaSource(uri);
            if (eventHandler != null && eventListener != null) {
                mediaSource.addEventListener(eventHandler, eventListener);
            }
            return mediaSource;
        }

    }


    /**
     * The default minimum number of times to retry loading data prior to failing.
     */
    private static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

    private final Uri uri;
    private final Client.Factory<? extends Client> factory;
    private final int minLoadableRetryCount;
    private final boolean treatLoadErrorsAsEndOfStream;
    private EventDispatcher eventDispatcher;

    private Client client;

    private RtspMediaSource(Uri uri, Client.Factory<? extends Client> factory,
                           int minLoadableRetryCount,
                           boolean treatLoadErrorsAsEndOfStream) {
        this.uri = uri;
        this.factory = factory;
        this.minLoadableRetryCount = minLoadableRetryCount;
        this.treatLoadErrorsAsEndOfStream = treatLoadErrorsAsEndOfStream;
    }

    // MediaTrackSource implementation

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        if (client == null) {
            throw new IOException();
        }
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
        Assertions.checkArgument(id.periodIndex == 0);
        eventDispatcher = createEventDispatcher(id);
        return new RtspMediaPeriod(client.session(), minLoadableRetryCount, allocator);
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
        ((RtspMediaPeriod) mediaPeriod).release();
    }


    @Override
    public void prepareSourceInternal(ExoPlayer player, boolean isTopLevelSource) {
        client = new Client.Builder(factory).uri(uri).listener(this).build();

        try {

            player.getVideoComponent().addVideoListener(client.session());
            client.open();

        } catch (NullPointerException e) {

        } catch (IOException e) {
            client = null;
        }
    }

    @Override
    public void releaseSourceInternal() {
        if (client != null) {
            client.close();
            client = null;
        }
    }


    // Client.EventListener implementation

    @Override
    public void onMediaDescriptionInfoRefreshed(long durationUs) {
        refreshSourceInfo(new SinglePeriodTimeline(durationUs,
                (durationUs != C.TIME_UNSET) ? true : false, false), null);
    }

    @Override
    public void onMediaDescriptionTypeUnSupported(MediaType mediaType) {
        if (eventDispatcher != null) {
            eventDispatcher.loadError(new DataSpec(uri), C.DATA_TYPE_MEDIA, 0, 0,
                    0, new IOException("Media Description Type [" + mediaType +
                            "] is not supported"), false);
        }
    }

    @Override
    public void onClientError(Throwable throwable) {
        if (eventDispatcher != null) {
            eventDispatcher.loadError(new DataSpec(uri), C.DATA_TYPE_MEDIA, 0, 0,
                    0, null, false);
        }
    }
}
