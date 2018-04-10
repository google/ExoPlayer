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

import android.os.Handler;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.rtsp.media.MediaSession;
import com.google.android.exoplayer2.source.rtsp.media.MediaTrack;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.TrackIdGenerator;

import java.io.IOException;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;

/* package */ final class RtspMediaPeriod implements MediaPeriod, RtspSampleStreamWrapper.EventListener,
        SequenceableLoader.Callback<RtspSampleStreamWrapper> {

    private final MediaSession session;
    private final int minLoadableRetryCount;
    private final Allocator allocator;

    private Callback callback;

    private boolean prepared;
    private int pendingPrepareCount;
    private long lastSeekPositionUs;

    private TrackGroupArray trackGroups;

    private final IdentityHashMap<SampleStream, Integer> streamWrapperIndices;

    private RtspSampleStreamWrapper[] sampleStreamWrappers;
    private RtspSampleStreamWrapper[] preparedSampleStreamWrappers;

    private final TrackIdGenerator trackIdGenerator;

    public RtspMediaPeriod(MediaSession session, int minLoadableRetryCount,
                           Allocator allocator) {
        this.session = session;
        this.minLoadableRetryCount = minLoadableRetryCount;
        this.allocator = allocator;

        trackIdGenerator = new TrackIdGenerator(1, 1);

        streamWrapperIndices = new IdentityHashMap<>();
        sampleStreamWrappers = new RtspSampleStreamWrapper[0];
        preparedSampleStreamWrappers = new RtspSampleStreamWrapper[0];

        lastSeekPositionUs = C.POSITION_UNSET;
    }

    public void release() {
        for (RtspSampleStreamWrapper sampleStreamWrapper : this.sampleStreamWrappers) {
            sampleStreamWrapper.release();
        }

        session.close();
    }

    @Override
    public void prepare(Callback callback, long positionUs) {
        this.callback = callback;

        buildAndPrepareMediaStreams(positionUs);
    }

    @Override
    public void maybeThrowPrepareError() throws IOException {
        for (RtspSampleStreamWrapper sampleStreamWrapper : this.sampleStreamWrappers) {
            sampleStreamWrapper.maybeThrowPrepareError();
        }
    }

    @Override
    public TrackGroupArray getTrackGroups() {
        return trackGroups;
    }

    @Override
    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
                             SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
        Assertions.checkState(prepared);

        // Map each selection and stream onto a child period index.
        int[] streamChildIndices = new int[selections.length];
        int[] selectionChildIndices = new int[selections.length];
        for (int i = 0; i < selections.length; i++) {
            streamChildIndices[i] = streams[i] == null ? C.INDEX_UNSET
                    : streamWrapperIndices.get(streams[i]);
            selectionChildIndices[i] = C.INDEX_UNSET;
            if (selections[i] != null) {
                TrackGroup trackGroup = selections[i].getTrackGroup();
                for (int j = 0; j < preparedSampleStreamWrappers.length; j++) {
                    if (preparedSampleStreamWrappers[j].getTrackGroups().indexOf(trackGroup)
                            != C.INDEX_UNSET) {
                        selectionChildIndices[i] = j;
                        break;
                    }
                }
            }
        }

        streamWrapperIndices.clear();

        // Select tracks for each child, copying the resulting streams back into a new streams array.
        SampleStream[] newStreams = new SampleStream[selections.length];
        SampleStream[] childStreams = new SampleStream[selections.length];
        TrackSelection[] childSelections = new TrackSelection[selections.length];

        for (int i = 0; i < preparedSampleStreamWrappers.length; i++) {
            for (int j = 0; j < selections.length; j++) {
                childStreams[j] = streamChildIndices[j] == i ? streams[j] : null;
                childSelections[j] = selectionChildIndices[j] == i ? selections[j] : null;
            }

            RtspSampleStreamWrapper sampleStreamWrapper = preparedSampleStreamWrappers[i];
            sampleStreamWrapper.selectTracks(childSelections, mayRetainStreamFlags,
                    childStreams, streamResetFlags, positionUs);

            for (int j = 0; j < selections.length; j++) {
                if (selectionChildIndices[j] == i) {
                    // Assert that the child provided a stream for the selection.
                    Assertions.checkState(childStreams[j] != null);
                    newStreams[j] = childStreams[j];
                    streamWrapperIndices.put(childStreams[j], i);

                } else if (streamChildIndices[j] == i) {
                    // Assert that the child cleared any previous stream.
                    Assertions.checkState(childStreams[j] == null);
                }
            }
        }

        // Copy the new streams back into the streams array.
        System.arraycopy(newStreams, 0, streams, 0, newStreams.length);

        return positionUs;
    }

    @Override
    public void discardBuffer(long positionUs, boolean toKeyframe) {
        for (RtspSampleStreamWrapper sampleStreamWrapper : preparedSampleStreamWrappers) {
            sampleStreamWrapper.discardBuffer(positionUs, toKeyframe);
        }
    }

    @Override
    public long readDiscontinuity() {
        return C.TIME_UNSET;
    }

    @Override
    public void pause() {
        if (session.getDuration() == C.TIME_UNSET) {
            for (RtspSampleStreamWrapper sampleStreamWrapper : preparedSampleStreamWrappers) {
                sampleStreamWrapper.discardBufferToEnd();
            }
        }

        session.pause();
    }

    @Override
    public void resume() {
        if (session.getDuration() == C.TIME_UNSET) {
            for (RtspSampleStreamWrapper sampleStreamWrapper : preparedSampleStreamWrappers) {
                sampleStreamWrapper.discardBufferToEnd();
            }
        }

        session.resume();
    }

    @Override
    public long seekToUs(long positionUs) {
        if (lastSeekPositionUs != positionUs) {
            boolean forceSeekTo = false;
            for (RtspSampleStreamWrapper sampleStreamWrapper : preparedSampleStreamWrappers) {
                forceSeekTo |= sampleStreamWrapper.seekToUs(positionUs);
            }

            if (forceSeekTo) {
                session.seekTo(positionUs / C.MICROS_PER_SECOND);
            }

            lastSeekPositionUs = positionUs;
        }

        return lastSeekPositionUs;
    }

    @Override
    public long getBufferedPositionUs() {
        long bufferedPositionUs = Long.MAX_VALUE;
        synchronized (preparedSampleStreamWrappers) {
            for (RtspSampleStreamWrapper sampleStreamWrapper : preparedSampleStreamWrappers) {
                long loaderBufferedPositionUs = sampleStreamWrapper.getBufferedPositionUs();
                if (loaderBufferedPositionUs != C.TIME_END_OF_SOURCE) {
                    bufferedPositionUs = Math.min(bufferedPositionUs, loaderBufferedPositionUs);
                }
            }
        }

        return bufferedPositionUs == Long.MAX_VALUE ? C.TIME_END_OF_SOURCE : bufferedPositionUs;
    }

    @Override
    public long getNextLoadPositionUs() {
        long nextLoadPositionUs = Long.MAX_VALUE;
        synchronized (preparedSampleStreamWrappers) {
            for (RtspSampleStreamWrapper sampleStreamWrapper : preparedSampleStreamWrappers) {
                long loaderNextLoadPositionUs = sampleStreamWrapper.getNextLoadPositionUs();
                if (loaderNextLoadPositionUs != C.TIME_END_OF_SOURCE) {
                    nextLoadPositionUs = Math.min(nextLoadPositionUs, loaderNextLoadPositionUs);
                }
            }
        }

        return nextLoadPositionUs == Long.MAX_VALUE ? C.TIME_END_OF_SOURCE : nextLoadPositionUs;
    }

    @Override
    public boolean continueLoading(long positionUs) {
        boolean continuedLoading = false;
        synchronized (preparedSampleStreamWrappers) {
            for (RtspSampleStreamWrapper sampleStreamWrapper : preparedSampleStreamWrappers) {
                continuedLoading |= sampleStreamWrapper.continueLoading(positionUs);
            }
        }

        return continuedLoading;
    }

    @Override
    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
        return positionUs;
    }

    @Override
    public void reevaluateBuffer(long positionUs) {
        // Do nothing.
    }

    // SequenceableLoader.Callback<RtspSampleStreamWrapper> implementation
    @Override
    public void onContinueLoadingRequested(RtspSampleStreamWrapper sampleStream) {
        callback.onContinueLoadingRequested(this);
    }


    // RtspSampleStreamWrapper.Callback implementation
    @Override
    public void onMediaStreamPrepareStarted(RtspSampleStreamWrapper sampleStream) {
        session.continuePrepareStream(sampleStream);
    }

    @Override
    public void onMediaStreamPrepareFailure(RtspSampleStreamWrapper sampleStream) {
        if (--pendingPrepareCount > 0) {
            return;
        }

        trackGroups = buildTrackGroups();
        prepared = true;
        callback.onPrepared(this);
    }

    @Override
    public void onMediaStreamPrepareSuccess(RtspSampleStreamWrapper sampleStream) {
        synchronized (preparedSampleStreamWrappers) {
            int streamCount = preparedSampleStreamWrappers.length;
            preparedSampleStreamWrappers = Arrays.copyOf(preparedSampleStreamWrappers, streamCount + 1);
            preparedSampleStreamWrappers[streamCount] = sampleStream;

            if (--pendingPrepareCount > 0) {
                return;
            }

            trackGroups = buildTrackGroups();
            prepared = true;
            callback.onPrepared(this);
        }
    }

    @Override
    public void onMediaStreamPlaybackFailure(RtspSampleStreamWrapper sampleStream) {
        synchronized (preparedSampleStreamWrappers) {

            List<RtspSampleStreamWrapper> preparedSamples = new LinkedList<>(
                    Arrays.asList(preparedSampleStreamWrappers));

            if (preparedSamples.contains(sampleStream)) {
                sampleStream.release();
                preparedSamples.remove(sampleStream);
            }

            preparedSampleStreamWrappers = new RtspSampleStreamWrapper[preparedSamples.size()];

            preparedSamples.toArray(preparedSampleStreamWrappers);

            if (preparedSampleStreamWrappers.length == 0) {
                session.close();
                prepared = false;
            }
        }
    }


    // Internal methods
    private void buildAndPrepareMediaStreams(long positionUs) {
        int mediaStreamCount = 0;

        // Build media video streams. Uniquely one media track is supported.
        List<MediaTrack> mediaVideoTracks = session.getMediaVideoTracks();
        if (mediaVideoTracks.size() > 0) {
            sampleStreamWrappers = Arrays.copyOf(sampleStreamWrappers, mediaStreamCount + 1);
            sampleStreamWrappers[mediaStreamCount++] = buildMediaSampleStream(
                    mediaVideoTracks.get(0), positionUs);
        }

        // Build media audio streams. Uniquely one media track is supported.
        List<MediaTrack> mediaAudioTracks = session.getMediaAudioTracks();
        if (mediaAudioTracks.size() > 0) {
            sampleStreamWrappers = Arrays.copyOf(sampleStreamWrappers, mediaStreamCount + 1);
            sampleStreamWrappers[mediaStreamCount++] = buildMediaSampleStream(
                    mediaAudioTracks.get(0), positionUs);
        }

        // Build media subtitle streams. Uniquely one media track is supported.
/*        List<MediaTrack> mediaTextTracks = session.getMediaTextTracks();
        if (mediaTextTracks.size() > 0) {
            sampleStreamWrappers = Arrays.copyOf(sampleStreamWrappers, mediaStreamCount + 1);
            sampleStreamWrappers[mediaStreamCount++] = buildMediaSampleStream(
                    mediaTextTracks.get(0), positionUs);
        }
*/
        pendingPrepareCount = mediaStreamCount;

        session.prepareStreams(sampleStreamWrappers);
    }

    private RtspSampleStreamWrapper buildMediaSampleStream(MediaTrack track, long positionUs) {
        return new RtspSampleStreamWrapper(session, track, trackIdGenerator, positionUs, this,
                allocator, minLoadableRetryCount);
    }

    private TrackGroupArray buildTrackGroups() {
        int totalTrackGroupCount = 0;

        for (RtspSampleStreamWrapper sampleStreamWrapper : preparedSampleStreamWrappers) {
            totalTrackGroupCount += sampleStreamWrapper.getTrackGroups().length;
        }

        TrackGroup[] trackGroupArray = new TrackGroup[totalTrackGroupCount];
        int trackGroupIndex = 0;
        for (RtspSampleStreamWrapper sampleStreamWrapper : preparedSampleStreamWrappers) {
            int wrapperTrackGroupCount = sampleStreamWrapper.getTrackGroups().length;
            for (int ndx = 0; ndx < wrapperTrackGroupCount; ndx++) {
                trackGroupArray[trackGroupIndex++] = sampleStreamWrapper.getTrackGroups().get(ndx);
            }
        }

        return new TrackGroupArray(trackGroupArray);
    }
}
