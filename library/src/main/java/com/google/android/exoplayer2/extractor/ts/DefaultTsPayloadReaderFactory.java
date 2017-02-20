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
import android.util.SparseArray;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.EsInfo;
import com.google.android.exoplayer2.util.MimeTypes;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation for {@link TsPayloadReader.Factory}.
 */
public final class DefaultTsPayloadReaderFactory implements TsPayloadReader.Factory {

  /**
   * Flags controlling elementary stream readers behaviour.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(flag = true, value = {FLAG_ALLOW_NON_IDR_KEYFRAMES, FLAG_IGNORE_AAC_STREAM,
      FLAG_IGNORE_H264_STREAM, FLAG_DETECT_ACCESS_UNITS, FLAG_IGNORE_SPLICE_INFO_STREAM})
  public @interface Flags {
  }
  public static final int FLAG_ALLOW_NON_IDR_KEYFRAMES = 1;
  public static final int FLAG_IGNORE_AAC_STREAM = 1 << 1;
  public static final int FLAG_IGNORE_H264_STREAM = 1 << 2;
  public static final int FLAG_DETECT_ACCESS_UNITS = 1 << 3;
  public static final int FLAG_IGNORE_SPLICE_INFO_STREAM = 1 << 4;

  @Flags private final int flags;
  private final List<Format> closedCaptionFormats;

  public DefaultTsPayloadReaderFactory() {
    this(0);
  }

  /**
   * @param flags A combination of {@code FLAG_*} values, which control the behavior of the created
   *     readers.
   */
  public DefaultTsPayloadReaderFactory(@Flags int flags) {
    this(flags, Collections.singletonList(Format.createTextSampleFormat(null,
        MimeTypes.APPLICATION_CEA608, null, Format.NO_VALUE, 0, null, null)));
  }

  /**
   * @param flags A combination of {@code FLAG_*} values, which control the behavior of the created
   *     readers.
   * @param closedCaptionFormats {@link Format}s to be exposed by elementary stream readers for
   *     streams with embedded closed captions.
   */
  public DefaultTsPayloadReaderFactory(@Flags int flags, List<Format> closedCaptionFormats) {
    this.flags = flags;
    this.closedCaptionFormats = closedCaptionFormats;
  }

  @Override
  public SparseArray<TsPayloadReader> createInitialPayloadReaders() {
    return new SparseArray<>();
  }

  @Override
  public TsPayloadReader createPayloadReader(int streamType, EsInfo esInfo) {
    switch (streamType) {
      case TsExtractor.TS_STREAM_TYPE_MPA:
      case TsExtractor.TS_STREAM_TYPE_MPA_LSF:
        return new PesReader(new MpegAudioReader(esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_AAC:
        return isSet(FLAG_IGNORE_AAC_STREAM)
            ? null : new PesReader(new AdtsReader(false, esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_AC3:
      case TsExtractor.TS_STREAM_TYPE_E_AC3:
        return new PesReader(new Ac3Reader(esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_DTS:
      case TsExtractor.TS_STREAM_TYPE_HDMV_DTS:
        return new PesReader(new DtsReader(esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_H262:
        return new PesReader(new H262Reader());
      case TsExtractor.TS_STREAM_TYPE_H264:
        return isSet(FLAG_IGNORE_H264_STREAM) ? null
            : new PesReader(new H264Reader(buildSeiReader(), isSet(FLAG_ALLOW_NON_IDR_KEYFRAMES),
                isSet(FLAG_DETECT_ACCESS_UNITS)));
      case TsExtractor.TS_STREAM_TYPE_H265:
        return new PesReader(new H265Reader(buildSeiReader()));
      case TsExtractor.TS_STREAM_TYPE_SPLICE_INFO:
        return isSet(FLAG_IGNORE_SPLICE_INFO_STREAM)
            ? null : new SectionReader(new SpliceInfoSectionReader());
      case TsExtractor.TS_STREAM_TYPE_ID3:
        return new PesReader(new Id3Reader());
      default:
        return null;
    }
  }

  private SeiReader buildSeiReader() {
    // TODO: Add descriptor parsing to detect channels automatically.
    return new SeiReader(closedCaptionFormats);
  }

  private boolean isSet(@Flags int flag) {
    return (flags & flag) != 0;
  }

}
