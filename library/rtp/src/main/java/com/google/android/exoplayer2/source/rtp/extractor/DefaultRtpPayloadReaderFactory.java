/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtp.extractor;


import android.support.annotation.NonNull;

import com.google.android.exoplayer2.source.rtp.format.RtpAudioPayload;
import com.google.android.exoplayer2.source.rtp.format.RtpPayloadFormat;
import com.google.android.exoplayer2.source.rtp.format.RtpVideoPayload;
import com.google.android.exoplayer2.util.MimeTypes;

/**
 * Default {@link RtpPayloadReader.Factory} implementation.
 */
/*package*/ final class DefaultRtpPayloadReaderFactory implements RtpPayloadReader.Factory {

  @NonNull @Override
  public RtpPayloadReader createPayloadReader(RtpPayloadFormat format) {
    if (MimeTypes.VIDEO_H264.equals(format.sampleMimeType())) {
      return new RtpH264PayloadReader((RtpVideoPayload)format);
    } else if (MimeTypes.AUDIO_ALAW.equals(format.sampleMimeType()) ||
            MimeTypes.AUDIO_MLAW.equals(format.sampleMimeType())) {
      return new RtpG711PayloadReader((RtpAudioPayload)format);
    } else if (MimeTypes.AUDIO_AC3.equals(format.sampleMimeType())) {
      return new RtpAc3PayloadReader((RtpAudioPayload)format);
    } else if (MimeTypes.AUDIO_AAC.equals(format.sampleMimeType())) {
      return new RtpAacPayloadReader((RtpAudioPayload)format);
    }

    return null;
  }

}
