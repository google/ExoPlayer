package com.google.android.exoplayer2.extractor.ts;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import android.util.Log;


public class DvbSubtitlesReader extends ElementaryStreamReader {

    private static final String TAG= "DVBSubsReader";

    private long sampleTimeUs;
    private int totalBytesWritten;
    private boolean writingSample;


    public DvbSubtitlesReader(TrackOutput output) {
        this(output, null);
    }

    public DvbSubtitlesReader(TrackOutput output, String language) {
        super(output);
        output.format(Format.createSampleFormat(null, MimeTypes.APPLICATION_DVBSUBS, null, Format.NO_VALUE,
                null, language));
    }

    @Override
    public void seek() {
        writingSample = false;
    }

    @Override
    public void packetStarted(long pesTimeUs, boolean dataAlignmentIndicator) {
        if (!dataAlignmentIndicator) {
            return;
        }
        writingSample = true;
        sampleTimeUs = pesTimeUs;
        totalBytesWritten = 0;
    }

    @Override
    public void packetFinished() {
        output.sampleMetadata(sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME, totalBytesWritten, 0, null);
        writingSample = false;
    }

    @Override
    public void consume(ParsableByteArray data) {
        if (writingSample) {
            totalBytesWritten += data.bytesLeft();
            output.sampleData(data, data.bytesLeft());
            //Log.d(TAG, "bytesWritten=" + totalBytesWritten);
        }
    }
}
