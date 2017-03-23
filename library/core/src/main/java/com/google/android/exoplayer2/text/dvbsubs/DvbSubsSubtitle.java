/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.text.dvbsubs;


import android.graphics.Bitmap;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;

import java.util.Collections;
import java.util.List;

final class DvbSubsSubtitle implements Subtitle {
    private final List<Cue> cues;

    public DvbSubsSubtitle(Bitmap data) {
        if (data == null) {
            this.cues = Collections.emptyList();
        } else {
            Cue cue = new Cue(data, 0, Cue.ANCHOR_TYPE_START, 0, Cue.ANCHOR_TYPE_START, 1, (float) data.getWidth()/data.getHeight());
            this.cues = Collections.singletonList(cue);
        }
    }

    @Override
    public int getNextEventTimeIndex(long timeUs) {
        return C.INDEX_UNSET;
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