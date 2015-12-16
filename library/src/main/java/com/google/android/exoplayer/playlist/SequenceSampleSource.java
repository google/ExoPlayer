package com.google.android.exoplayer.playlist;

import android.util.SparseArray;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.extractor.DefaultTrackOutput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

/**
 * A source of media samples.
 * <p>
 * Wraps other {@link SampleSource}s to achieve smooth edge-to-edge multi-source playback.
 * Very useful to create a playlist, which not natively supported by Android.
 */
public class SequenceSampleSource implements SampleSource {

    private ArrayList<SampleSource> sources;
    private SequenceInfoListener listener;

    public SequenceSampleSource(Collection<SampleSource> playlist, SequenceInfoListener listener) {
        this.listener = listener;
        sources = new ArrayList<>(playlist);
    }

    @Override
    public SampleSourceReader register() {
        Collection<SampleSourceReader> readers = new ArrayList<>();
        for (SampleSource source : sources) {
            readers.add(source.register());
        }
        return new SequenceSampleSourceReader(readers, listener);
    }

    /**
     * Searches a sorted list for a given value.
     *
     * @param list  sorted list
     * @param value minimum value
     * @return the smallest index that corresponds to a item at least the value.
     */
    public static int indexOfItemAtLeast(long[] list, long value) {
        int index = Arrays.binarySearch(list, value);
        if (index >= 0) {
            return index;
        }

        // Negative index denotes where the value would be inserted negated and - 1
        // See documentation of {@link Arrays.binarySearch}
        int wouldInsertAt = -index - 1;
        if (wouldInsertAt == 0)
            return 0;
        // We need to now the item before the insertion index
        return wouldInsertAt - 1;
    }

    public interface SequenceInfoListener {
        void onTimesKnown(long[] durationsUs, long[] startTimesUs);
    }

    public class SequenceSampleSourceReader implements SampleSourceReader {

        boolean isPrepared;
        boolean hasRegisteredSources;
        private LinkedList<SampleSourceReader> readers;
        private final SparseArray<DefaultTrackOutput> sampleQueues;

        boolean hasLookup;
        private long[] durationsUs = null;
        private long[] startTimesUs = null;
        private SequenceInfoListener listener;

        private long lastSeekPositionUs;
        private boolean[] pendingDiscontinuities;

        public SequenceSampleSourceReader(Collection<SampleSourceReader> readers, SequenceInfoListener listener) {
            this.listener = listener;
            this.readers = new LinkedList<>(readers);
            sampleQueues = new SparseArray<>();
        }

        /**
         * Prepares time indexes to quickly access the SampleSourceReader for a given time.
         */
        private void createLookup() {
            durationsUs = new long[readers.size()];
            startTimesUs = new long[readers.size()];

            int i = 0;
            for (SampleSourceReader reader : readers) {
                durationsUs[i] = reader.getFormat(0).durationUs;
                startTimesUs[i] = i == 0 ? 0 : startTimesUs[i - 1] + durationsUs[i - 1];
                i++;
            }
            if (listener != null) {
                listener.onTimesKnown(durationsUs, startTimesUs);
            }
            hasLookup = true;
        }

        private int nestedIndex(long globalPositionUs) {
            if (!isPrepared)
                throw new AssertionError("All sub readers need to be prepared to this");

            return indexOfItemAtLeast(startTimesUs, globalPositionUs);
        }

        private SampleSourceReader nestedReader(long globalPositionUs) {
            return readers.get(nestedIndex(globalPositionUs));
        }

        private long nestedPositionUs(long globalPositionUs) {
            return globalPositionUs - startTimesUs[nestedIndex(globalPositionUs)];
        }

        private long getTotalDuration() {
            if (!isPrepared)
                throw new AssertionError("All sub readers need to be prepared to this");

            long total = 0;
            for (long d : durationsUs) {
                total += d;
            }
            return total;
        }

        @Override
        public boolean prepare(long positionUs) {
            if (isPrepared) {
                return true;
            }

            if (!hasRegisteredSources) {
                for (SampleSource s : sources) {
                    s.register();
                }
                hasRegisteredSources = true;
            }

            // Check if all sub sources are prepared too
            boolean prepped = true;
            for (int i = 0; prepped && i < sources.size(); i++) {
                prepped = readers.get(i).prepare(0);
            }

            if (prepped) {
                // Setup things depending on track count
                int trackCount = readers.get(0).getTrackCount();
                pendingDiscontinuities = new boolean[readers.get(0).getTrackCount()];
                createLookup();
            }

            isPrepared = prepped;

            return isPrepared;
        }

        @Override
        public int getTrackCount() {
            return nestedReader(0).getTrackCount();
        }

        @Override
        public MediaFormat getFormat(int track) {
            MediaFormat format = nestedReader(0).getFormat(track);
            format.copyWithDurationUs(getTotalDuration());
            return format;
        }

        @Override
        public void enable(int track, long positionUs) {
            for (SampleSourceReader s : readers) {
                s.enable(track, 0);
            }
            lastSeekPositionUs = positionUs;
        }

        @Override
        public boolean continueBuffering(int track, long positionUs) {
            return nestedReader(positionUs).continueBuffering(track, nestedPositionUs(positionUs));
        }


        @Override
        public int readData(int track, long positionUs, MediaFormatHolder formatHolder, SampleHolder sampleHolder, boolean onlyReadDiscontinuity) {

            // After seeking we need to emit a discontinuity
            if (pendingDiscontinuities[track]) {
                pendingDiscontinuities[track] = false;
                sampleHolder.timeUs = lastSeekPositionUs;
                return DISCONTINUITY_READ;
            }

            // When this flag is true we may only emit {@link SampleSource#NOTHING_READ}
            if (onlyReadDiscontinuity) {
                return NOTHING_READ;
            }

            // Perform normal read operation
            int readerIndex = nestedIndex(positionUs);
            int result = nestedReader(positionUs).readData(track, nestedPositionUs(positionUs), formatHolder, sampleHolder, false);

            // Prevent end of stream notices by reading ahead
            if (result == END_OF_STREAM && readers.get(readerIndex) != readers.getLast()) {
                readerIndex++;
                result = readers.get(readerIndex).readData(track, 0, formatHolder, sampleHolder, false);
            }

            // Fix timeUs to be global instead of local to the nested source
            if (result == SAMPLE_READ)
                sampleHolder.timeUs += startTimesUs[readerIndex];

            return result;
        }

        /**
         * Ensure that the transition is smooth and uses keyframes
         * Checks for keyframe property via {@link SampleHolder#isSyncFrame()}
         *
         * @param sampleSourceReader splice to this reader
         */
        private void configureSpliceTo(SampleSourceReader sampleSourceReader) {
            // TODO discard all samples from sampleSource which are not keyframes
        }

        @Override
        public void seekToUs(long positionUs) {
            nestedReader(positionUs).seekToUs(nestedPositionUs(positionUs));
            lastSeekPositionUs = positionUs;
            Arrays.fill(pendingDiscontinuities, true);
        }

        /**
         * Find maximum buffered positionUs. Traverse all sub sources to do so.
         *
         * @return An estimate of the absolute position in microseconds up to which the data is buffered,
         * or {@link TrackRenderer#END_OF_TRACK_US} if all sub sources are fully buffered,
         * or {@link TrackRenderer#UNKNOWN_TIME_US} if no estimate is available.
         */
        @Override
        public long getBufferedPositionUs() {
            long last = TrackRenderer.UNKNOWN_TIME_US;
            int i = 0;
            for (SampleSourceReader s : readers) {
                long buffered = s.getBufferedPositionUs();

                // Fully buffered, look further
                if (buffered == TrackRenderer.END_OF_TRACK_US)
                    last = TrackRenderer.END_OF_TRACK_US;

                    // Unknown, escalate
                else if (buffered == TrackRenderer.UNKNOWN_TIME_US)
                    return TrackRenderer.UNKNOWN_TIME_US;

                    // Found first sub source that is not yet fully buffered
                else {
                    return startTimesUs[i] + buffered;
                }
                i++;
            }
            return last;
        }

        @Override
        public void disable(int track) {
            for (SampleSourceReader s : readers) {
                s.disable(track);
            }
        }

        @Override
        public void release() {
            if (hasRegisteredSources) {
                for (SampleSourceReader s : readers) {
                    s.release();
                }
                hasRegisteredSources = false;
            }
        }

        @Override
        public void maybeThrowError() throws IOException {
            for (SampleSourceReader s : readers) {
                s.maybeThrowError();
            }
        }
    }
}
