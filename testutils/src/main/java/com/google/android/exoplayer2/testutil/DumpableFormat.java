/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.base.Function;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** Wraps a {@link Format} to allow dumping it. */
public final class DumpableFormat implements Dumper.Dumpable {
  private final Format format;
  public final int index;

  private static final Format DEFAULT_FORMAT = new Format.Builder().build();

  public DumpableFormat(Format format, int index) {
    this.format = format;
    this.index = index;
  }

  @Override
  public void dump(Dumper dumper) {
    dumper.startBlock("format " + index);
    addIfNonDefault(dumper, "averageBitrate", format -> format.averageBitrate);
    addIfNonDefault(dumper, "peakBitrate", format -> format.peakBitrate);
    addIfNonDefault(dumper, "id", format -> format.id);
    addIfNonDefault(dumper, "containerMimeType", format -> format.containerMimeType);
    addIfNonDefault(dumper, "sampleMimeType", format -> format.sampleMimeType);
    addIfNonDefault(dumper, "codecs", format -> format.codecs);
    addIfNonDefault(dumper, "maxInputSize", format -> format.maxInputSize);
    addIfNonDefault(dumper, "width", format -> format.width);
    addIfNonDefault(dumper, "height", format -> format.height);
    addIfNonDefault(dumper, "frameRate", format -> format.frameRate);
    addIfNonDefault(dumper, "rotationDegrees", format -> format.rotationDegrees);
    addIfNonDefault(dumper, "pixelWidthHeightRatio", format -> format.pixelWidthHeightRatio);
    @Nullable ColorInfo colorInfo = format.colorInfo;
    if (colorInfo != null) {
      dumper.startBlock("colorInfo");
      dumper.add("colorSpace", colorInfo.colorSpace);
      dumper.add("colorRange", colorInfo.colorRange);
      dumper.add("colorTransfer", colorInfo.colorTransfer);
      dumper.add("hdrStaticInfo", colorInfo.hdrStaticInfo);
      dumper.endBlock();
    }
    addIfNonDefault(dumper, "channelCount", format -> format.channelCount);
    addIfNonDefault(dumper, "sampleRate", format -> format.sampleRate);
    addIfNonDefault(dumper, "pcmEncoding", format -> format.pcmEncoding);
    addIfNonDefault(dumper, "encoderDelay", format -> format.encoderDelay);
    addIfNonDefault(dumper, "encoderPadding", format -> format.encoderPadding);
    addIfNonDefault(dumper, "subsampleOffsetUs", format -> format.subsampleOffsetUs);
    addIfNonDefault(dumper, "selectionFlags", format -> format.selectionFlags);
    addIfNonDefault(dumper, "language", format -> format.language);
    addIfNonDefault(dumper, "label", format -> format.label);
    if (format.drmInitData != null) {
      dumper.add("drmInitData", format.drmInitData.hashCode());
    }
    addIfNonDefault(dumper, "metadata", format -> format.metadata);
    if (!format.initializationData.isEmpty()) {
      dumper.startBlock("initializationData");
      for (int i = 0; i < format.initializationData.size(); i++) {
        dumper.add("data", format.initializationData.get(i));
      }
      dumper.endBlock();
    }
    dumper.endBlock();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DumpableFormat that = (DumpableFormat) o;
    return index == that.index && format.equals(that.format);
  }

  @Override
  public int hashCode() {
    int result = format.hashCode();
    result = 31 * result + index;
    return result;
  }

  private void addIfNonDefault(
      Dumper dumper, String field, Function<Format, @NullableType Object> getFieldFunction) {
    @Nullable Object thisValue = getFieldFunction.apply(format);
    @Nullable Object defaultValue = getFieldFunction.apply(DEFAULT_FORMAT);
    if (!Util.areEqual(thisValue, defaultValue)) {
      dumper.add(field, thisValue);
    }
  }
}
