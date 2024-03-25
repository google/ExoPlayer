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
package androidx.media3.test.utils;

import androidx.annotation.Nullable;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Function;

/** Wraps a {@link Format} to allow dumping it. */
@UnstableApi
public final class DumpableFormat implements Dumper.Dumpable {
  private final Format format;
  private final String tag;

  private static final Format DEFAULT_FORMAT = new Format.Builder().build();
  private static final ColorInfo DEFAULT_COLOR_INFO = new ColorInfo.Builder().build();

  public DumpableFormat(Format format, int index) {
    this(format, Integer.toString(index));
  }

  public DumpableFormat(Format format, String tag) {
    this.format = format;
    this.tag = tag;
  }

  @Override
  public void dump(Dumper dumper) {
    dumper.startBlock("format " + tag);
    addIfNonDefault(
        dumper, "averageBitrate", format, DEFAULT_FORMAT, format -> format.averageBitrate);
    addIfNonDefault(dumper, "peakBitrate", format, DEFAULT_FORMAT, format -> format.peakBitrate);
    addIfNonDefault(dumper, "id", format, DEFAULT_FORMAT, format -> format.id);
    addIfNonDefault(
        dumper, "containerMimeType", format, DEFAULT_FORMAT, format -> format.containerMimeType);
    addIfNonDefault(
        dumper, "sampleMimeType", format, DEFAULT_FORMAT, format -> format.sampleMimeType);
    addIfNonDefault(dumper, "codecs", format, DEFAULT_FORMAT, format -> format.codecs);
    addIfNonDefault(dumper, "maxInputSize", format, DEFAULT_FORMAT, format -> format.maxInputSize);
    addIfNonDefault(dumper, "width", format, DEFAULT_FORMAT, format -> format.width);
    addIfNonDefault(dumper, "height", format, DEFAULT_FORMAT, format -> format.height);
    addIfNonDefault(dumper, "frameRate", format, DEFAULT_FORMAT, format -> format.frameRate);
    addIfNonDefault(
        dumper, "rotationDegrees", format, DEFAULT_FORMAT, format -> format.rotationDegrees);
    addIfNonDefault(
        dumper,
        "pixelWidthHeightRatio",
        format,
        DEFAULT_FORMAT,
        format -> format.pixelWidthHeightRatio);
    @Nullable ColorInfo colorInfo = format.colorInfo;
    if (colorInfo != null) {
      dumper.startBlock("colorInfo");
      addIfNonDefault(dumper, "colorSpace", colorInfo, DEFAULT_COLOR_INFO, c -> c.colorSpace);
      addIfNonDefault(dumper, "colorRange", colorInfo, DEFAULT_COLOR_INFO, c -> c.colorRange);
      addIfNonDefault(dumper, "colorTransfer", colorInfo, DEFAULT_COLOR_INFO, c -> c.colorTransfer);
      if (colorInfo.hdrStaticInfo != null) {
        dumper.add("hdrStaticInfo", colorInfo.hdrStaticInfo);
      }
      addIfNonDefault(dumper, "lumaBitdepth", colorInfo, DEFAULT_COLOR_INFO, c -> c.lumaBitdepth);
      addIfNonDefault(
          dumper, "chromaBitdepth", colorInfo, DEFAULT_COLOR_INFO, c -> c.chromaBitdepth);
      dumper.endBlock();
    }
    addIfNonDefault(dumper, "channelCount", format, DEFAULT_FORMAT, format -> format.channelCount);
    addIfNonDefault(dumper, "sampleRate", format, DEFAULT_FORMAT, format -> format.sampleRate);
    addIfNonDefault(dumper, "pcmEncoding", format, DEFAULT_FORMAT, format -> format.pcmEncoding);
    addIfNonDefault(dumper, "encoderDelay", format, DEFAULT_FORMAT, format -> format.encoderDelay);
    addIfNonDefault(
        dumper, "encoderPadding", format, DEFAULT_FORMAT, format -> format.encoderPadding);
    addIfNonDefault(
        dumper, "subsampleOffsetUs", format, DEFAULT_FORMAT, format -> format.subsampleOffsetUs);
    addIfNonDefault(
        dumper,
        "selectionFlags",
        format,
        DEFAULT_FORMAT,
        format -> Util.getSelectionFlagStrings(format.selectionFlags));
    addIfNonDefault(
        dumper,
        "roleFlags",
        format,
        DEFAULT_FORMAT,
        format -> Util.getRoleFlagStrings(format.roleFlags));
    addIfNonDefault(dumper, "language", format, DEFAULT_FORMAT, format -> format.language);
    addIfNonDefault(dumper, "label", format, DEFAULT_FORMAT, format -> format.label);
    if (!format.labels.isEmpty()) {
      dumper.startBlock("labels");
      for (int i = 0; i < format.labels.size(); i++) {
        String lang = format.labels.get(i).language;
        if (lang != null) {
          dumper.add("lang", lang);
        }
        dumper.add("value", format.labels.get(i).value);
      }
      dumper.endBlock();
    }
    if (format.drmInitData != null) {
      dumper.add("drmInitData", format.drmInitData.hashCode());
    }
    addIfNonDefault(dumper, "metadata", format, DEFAULT_FORMAT, format -> format.metadata);
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
    return tag.equals(that.tag) && format.equals(that.format);
  }

  @Override
  public int hashCode() {
    int result = format.hashCode();
    result = 31 * result + tag.hashCode();
    return result;
  }

  private <T> void addIfNonDefault(
      Dumper dumper,
      String field,
      T value,
      T defaultValue,
      Function<T, @NullableType Object> getFieldFunction) {
    @Nullable Object fieldValue = getFieldFunction.apply(value);
    @Nullable Object defaultFieldValue = getFieldFunction.apply(defaultValue);
    if (!Util.areEqual(fieldValue, defaultFieldValue)) {
      dumper.add(field, fieldValue);
    }
  }
}
