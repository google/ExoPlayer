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
package com.google.android.exoplayer2.extractor.ts;

import android.support.annotation.IntDef;
import android.util.SparseBooleanArray;
import com.google.android.exoplayer2.extractor.DummyTrackOutput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Default implementation for {@link ElementaryStreamReader.Factory}.
 */
public final class DefaultStreamReaderFactory implements ElementaryStreamReader.Factory {

  /**
   * Flags controlling what workarounds are enabled for elementary stream readers.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(flag = true, value = {WORKAROUND_ALLOW_NON_IDR_KEYFRAMES, WORKAROUND_IGNORE_AAC_STREAM,
      WORKAROUND_IGNORE_H264_STREAM, WORKAROUND_DETECT_ACCESS_UNITS, WORKAROUND_MAP_BY_TYPE})
  public @interface WorkaroundFlags {
  }
  public static final int WORKAROUND_ALLOW_NON_IDR_KEYFRAMES = 1;
  public static final int WORKAROUND_IGNORE_AAC_STREAM = 2;
  public static final int WORKAROUND_IGNORE_H264_STREAM = 4;
  public static final int WORKAROUND_DETECT_ACCESS_UNITS = 8;
  public static final int WORKAROUND_MAP_BY_TYPE = 16;

  private static final int BASE_EMBEDDED_TRACK_ID = 0x2000; // 0xFF + 1.

  private final SparseBooleanArray trackIds;
  @WorkaroundFlags
  private final int workaroundFlags;
  private Id3Reader id3Reader;
  private int nextEmbeddedTrackId = BASE_EMBEDDED_TRACK_ID;

  public DefaultStreamReaderFactory() {
    this(0);
  }

  public DefaultStreamReaderFactory(int workaroundFlags) {
    trackIds = new SparseBooleanArray();
    this.workaroundFlags = workaroundFlags;
  }

  @Override
  public ElementaryStreamReader onPmtEntry(int pid, int streamType,
      ElementaryStreamReader.EsInfo esInfo, ExtractorOutput output) {

    if ((workaroundFlags & WORKAROUND_MAP_BY_TYPE) != 0 && id3Reader == null) {
      // Setup an ID3 track regardless of whether there's a corresponding entry, in case one
      // appears intermittently during playback. See b/20261500.
      id3Reader = new Id3Reader(output.track(TsExtractor.TS_STREAM_TYPE_ID3));
    }
    int trackId = (workaroundFlags & WORKAROUND_MAP_BY_TYPE) != 0 ? streamType : pid;
    if (trackIds.get(trackId)) {
      return null;
    }
    trackIds.put(trackId, true);
    switch (streamType) {
      case TsExtractor.TS_STREAM_TYPE_MPA:
      case TsExtractor.TS_STREAM_TYPE_MPA_LSF:
        return new MpegAudioReader(output.track(trackId), esInfo.language);
      case TsExtractor.TS_STREAM_TYPE_AAC:
        return (workaroundFlags & WORKAROUND_IGNORE_AAC_STREAM) != 0 ? null
            : new AdtsReader(output.track(trackId), new DummyTrackOutput(), esInfo.language);
      case TsExtractor.TS_STREAM_TYPE_AC3:
      case TsExtractor.TS_STREAM_TYPE_E_AC3:
        return new Ac3Reader(output.track(trackId), esInfo.language);
      case TsExtractor.TS_STREAM_TYPE_DTS:
      case TsExtractor.TS_STREAM_TYPE_HDMV_DTS:
        return new DtsReader(output.track(trackId), esInfo.language);
      case TsExtractor.TS_STREAM_TYPE_H262:
        return new H262Reader(output.track(trackId));
      case TsExtractor.TS_STREAM_TYPE_H264:
        return (workaroundFlags & WORKAROUND_IGNORE_H264_STREAM) != 0
            ? null : new H264Reader(output.track(trackId),
                new SeiReader(output.track(nextEmbeddedTrackId++)),
                (workaroundFlags & WORKAROUND_ALLOW_NON_IDR_KEYFRAMES) != 0,
                (workaroundFlags & WORKAROUND_DETECT_ACCESS_UNITS) != 0);
      case TsExtractor.TS_STREAM_TYPE_H265:
        return new H265Reader(output.track(trackId),
            new SeiReader(output.track(nextEmbeddedTrackId++)));
      case TsExtractor.TS_STREAM_TYPE_ID3:
        if ((workaroundFlags & WORKAROUND_MAP_BY_TYPE) != 0) {
          return id3Reader;
        } else {
          return new Id3Reader(output.track(nextEmbeddedTrackId++));
        }
      default:
        return null;
    }
  }

}
