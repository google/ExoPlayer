package com.google.android.exoplayer2.text.dvbsubs;

import android.graphics.Bitmap;

import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;

import java.util.Collections;
import java.util.List;

/**
 * Created by opatino on 8/24/16.
 */
final class DvbSubsSubtitle implements Subtitle {
    private final List<Cue> cues;

    public DvbSubsSubtitle(Bitmap data) {
        if (data == null) {
            this.cues = Collections.emptyList();
        } else {
            Cue cue = new Cue(data);
            this.cues = Collections.singletonList(cue);
        }
    }

    @Override
    public int getNextEventTimeIndex(long timeUs) {
        return 0;
    }

    @Override
    public int getEventTimeCount() {
        return 1;
    }

    @Override
    public long getEventTime(int index) {
        return 0;
    }

    @Override
    public List<Cue> getCues(long timeUs) {
        return cues;
    }
}
