/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.util.rtp;

public class DefaultRtpDistributionFeedbackFactory implements RtpDistributionFeedback.Factory {

    private final long ssrc;
    private final String cname;

    public DefaultRtpDistributionFeedbackFactory(long ssrc, String cname) {
        this.ssrc = ssrc;
        this.cname = cname;
    }

    @Override
    public RtpDistributionFeedback createDistributionFeedback() {
        return new DefaultRtpDistributionFeedback(ssrc, cname);
    }
}
