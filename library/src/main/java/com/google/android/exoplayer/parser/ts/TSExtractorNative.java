package com.google.android.exoplayer.parser.ts;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.chunk.HLSExtractor;
import com.google.android.exoplayer.parser.aac.AACExtractor;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;

public class TSExtractorNative extends HLSExtractor {

    private NonBlockingInputStream inputStream;
    MediaFormat audioMediaFormat;

    static {
        System.loadLibrary("TSExtractorNative");
    }

    public TSExtractorNative(NonBlockingInputStream inputStream)
    {
        // needs to be done before the nativeInit()
        this.inputStream = inputStream;
        nativeInit();
    }

    @Override
    public int read(int type, SampleHolder out) throws ParserException {
        return nativeRead(type, out);
    }

    @Override
    public MediaFormat getAudioMediaFormat() {
        if (audioMediaFormat == null) {
            int sampleRateIndex = nativeGetSampleRateIndex();
            int channelConfigIndex = nativeGetChannelConfigIndex();

            audioMediaFormat = AACExtractor.ADTSHeader.createMediaFormat(sampleRateIndex, channelConfigIndex);
        }
        return audioMediaFormat;
    }

    @Override
    public boolean isReadFinished() {
        return nativeIsReadFinished();
    }

    // stores a pointer to the native state
    private long nativeHandle;

    private native void nativeInit();
    private native int nativeRead(int type, SampleHolder out);
    private native int nativeGetSampleRateIndex();
    private native int nativeGetChannelConfigIndex();
    private native boolean nativeIsReadFinished();
}
