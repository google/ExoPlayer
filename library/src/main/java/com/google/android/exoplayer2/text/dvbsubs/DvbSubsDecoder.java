package com.google.android.exoplayer2.text.dvbsubs;

import android.graphics.Bitmap;

import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;

/**
 * Created by opatino on 8/17/16.
 */
public final class DvbSubsDecoder extends SimpleSubtitleDecoder {
    private final String TAG = "DVBSubs Decoder";

    DvbSubtitlesParser parser;

    public DvbSubsDecoder() {
        super("dvbsubs");
        parser = new DvbSubtitlesParser(null);
    }

    @Override
    protected DvbSubsSubtitle decode(byte[] data, int length) {
        return new DvbSubsSubtitle(parser.dvbSubsDecode(data, length));
    }
}
