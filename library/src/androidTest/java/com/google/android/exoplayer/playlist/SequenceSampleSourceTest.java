package com.google.android.exoplayer.playlist;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.util.MimeTypes;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Test for {@link SequenceSampleSource}
 */
public class SequenceSampleSourceTest extends TestCase {

    public void testSequencePlaylist() {
        final long[] duration = {0};

        SequenceSampleSource.SequenceInfoListener listener = new SequenceSampleSource.SequenceInfoListener() {
            @Override
            public void onTimesKnown(long[] durationsUs, long[] startTimesUs) {
                duration[0] = durationsUs[0];
            }
        };

        SequenceSampleSource sequenceSampleSource = new SequenceSampleSource(buildPlaylist(), listener);
        SampleSource.SampleSourceReader sourceReader = sequenceSampleSource.register();
        sourceReader.prepare(0);

        assertEquals("Check that duration is extracted from sub sources", (long) 10e6, duration[0]);
    }


    private Collection<SampleSource> buildPlaylist() {
        ArrayList<SampleSource> list = new ArrayList<>();

        list.add(new DummySource());
        list.add(new DummySource());

        return list;
    }

    class DummySource implements SampleSource {

        @Override
        public SampleSourceReader register() {
            return new DummyReader();
        }
    }

    class DummyReader implements SampleSource.SampleSourceReader {
        @Override
        public void maybeThrowError() throws IOException {

        }

        @Override
        public boolean prepare(long positionUs) {
            return true;
        }

        @Override
        public int getTrackCount() {
            return 2;
        }

        @Override
        public MediaFormat getFormat(int track) {
            if(track == 0)
                return MediaFormat.createFormatForMimeType(track, MimeTypes.VIDEO_MP4, MediaFormat.NO_VALUE, (long) 10e6);
            return MediaFormat.createFormatForMimeType(track, MimeTypes.AUDIO_AAC, MediaFormat.NO_VALUE, (long) 10e6);
        }

        @Override
        public void enable(int track, long positionUs) {

        }

        @Override
        public boolean continueBuffering(int track, long positionUs) {
            return true;
        }

        @Override
        public int readData(int track, long positionUs, MediaFormatHolder formatHolder, SampleHolder sampleHolder, boolean onlyReadDiscontinuity) {
            return 0;
        }

        @Override
        public void seekToUs(long positionUs) {

        }

        @Override
        public long getBufferedPositionUs() {
            return 0;
        }

        @Override
        public void disable(int track) {

        }

        @Override
        public void release() {

        }
    }

}