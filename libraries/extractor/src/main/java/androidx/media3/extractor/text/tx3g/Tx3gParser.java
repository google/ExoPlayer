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
package androidx.media3.extractor.text.tx3g;

import static androidx.media3.common.text.Cue.ANCHOR_TYPE_START;
import static androidx.media3.common.text.Cue.LINE_TYPE_FRACTION;
import static androidx.media3.common.util.Assertions.checkArgument;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Format.CueReplacementBehavior;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import java.nio.charset.Charset;
import java.util.List;

/**
 * A {@link SubtitleParser} for tx3g.
 *
 * <p>Currently supports parsing of a single text track with embedded styles.
 */
@UnstableApi
public final class Tx3gParser implements SubtitleParser {

  /**
   * The {@link CueReplacementBehavior} for consecutive {@link CuesWithTiming} emitted by this
   * implementation.
   */
  public static final @CueReplacementBehavior int CUE_REPLACEMENT_BEHAVIOR =
      Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE;

  private static final String TAG = "Tx3gParser";

  private static final int TYPE_STYL = 0x7374796c;
  private static final int TYPE_TBOX = 0x74626f78;
  private static final String TX3G_SERIF = "Serif";

  private static final int SIZE_ATOM_HEADER = 8;
  private static final int SIZE_SHORT = 2;
  private static final int SIZE_STYLE_RECORD = 12;

  private static final int FONT_FACE_BOLD = 0x0001;
  private static final int FONT_FACE_ITALIC = 0x0002;
  private static final int FONT_FACE_UNDERLINE = 0x0004;

  private static final int SPAN_PRIORITY_LOW = 0xFF << Spanned.SPAN_PRIORITY_SHIFT;
  private static final int SPAN_PRIORITY_HIGH = 0;

  private static final int DEFAULT_FONT_FACE = 0;
  private static final int DEFAULT_COLOR = Color.WHITE;
  private static final String DEFAULT_FONT_FAMILY = C.SANS_SERIF_NAME;
  private static final float DEFAULT_VERTICAL_PLACEMENT = 0.85f;

  private final ParsableByteArray parsableByteArray;

  private final boolean customVerticalPlacement;
  private final int defaultFontFace;
  private final int defaultColorRgba;
  private final String defaultFontFamily;
  private final float defaultVerticalPlacement;
  private final int calculatedVideoTrackHeight;

  /**
   * Sets up a new {@link Tx3gParser} with default values.
   *
   * @param initializationData Sample description atom ('stsd') data with default subtitle styles.
   */
  public Tx3gParser(List<byte[]> initializationData) {
    parsableByteArray = new ParsableByteArray();

    if (initializationData.size() == 1
        && (initializationData.get(0).length == 48 || initializationData.get(0).length == 53)) {
      byte[] initializationBytes = initializationData.get(0);
      defaultFontFace = initializationBytes[24];
      defaultColorRgba =
          ((initializationBytes[26] & 0xFF) << 24)
              | ((initializationBytes[27] & 0xFF) << 16)
              | ((initializationBytes[28] & 0xFF) << 8)
              | (initializationBytes[29] & 0xFF);
      String fontFamily =
          Util.fromUtf8Bytes(initializationBytes, 43, initializationBytes.length - 43);
      defaultFontFamily = TX3G_SERIF.equals(fontFamily) ? C.SERIF_NAME : C.SANS_SERIF_NAME;
      // font size (initializationBytes[25]) is 5% of video height
      calculatedVideoTrackHeight = 20 * initializationBytes[25];
      customVerticalPlacement = (initializationBytes[0] & 0x20) != 0;
      if (customVerticalPlacement) {
        int requestedVerticalPlacement =
            ((initializationBytes[10] & 0xFF) << 8) | (initializationBytes[11] & 0xFF);
        defaultVerticalPlacement =
            Util.constrainValue(
                (float) requestedVerticalPlacement / calculatedVideoTrackHeight, 0.0f, 0.95f);
      } else {
        defaultVerticalPlacement = DEFAULT_VERTICAL_PLACEMENT;
      }
    } else {
      defaultFontFace = DEFAULT_FONT_FACE;
      defaultColorRgba = DEFAULT_COLOR;
      defaultFontFamily = DEFAULT_FONT_FAMILY;
      customVerticalPlacement = false;
      defaultVerticalPlacement = DEFAULT_VERTICAL_PLACEMENT;
      calculatedVideoTrackHeight = C.LENGTH_UNSET;
    }
  }

  @Override
  public @CueReplacementBehavior int getCueReplacementBehavior() {
    return CUE_REPLACEMENT_BEHAVIOR;
  }

  @Override
  public void parse(
      byte[] data,
      int offset,
      int length,
      OutputOptions outputOptions,
      Consumer<CuesWithTiming> output) {
    parsableByteArray.reset(data, /* limit= */ offset + length);
    parsableByteArray.setPosition(offset);
    String cueTextString = readSubtitleText(parsableByteArray);
    if (cueTextString.isEmpty()) {
      output.accept(
          new CuesWithTiming(
              /* cues= */ ImmutableList.of(),
              /* startTimeUs= */ C.TIME_UNSET,
              /* durationUs= */ C.TIME_UNSET));
      return;
    }
    // Attach default styles.
    SpannableStringBuilder cueText = new SpannableStringBuilder(cueTextString);
    attachFontFace(
        cueText, defaultFontFace, DEFAULT_FONT_FACE, 0, cueText.length(), SPAN_PRIORITY_LOW);
    attachColor(cueText, defaultColorRgba, DEFAULT_COLOR, 0, cueText.length(), SPAN_PRIORITY_LOW);
    attachFontFamily(cueText, defaultFontFamily, 0, cueText.length());
    float verticalPlacement = defaultVerticalPlacement;
    // Find and attach additional styles.
    while (parsableByteArray.bytesLeft() >= SIZE_ATOM_HEADER) {
      int position = parsableByteArray.getPosition();
      int atomSize = parsableByteArray.readInt();
      int atomType = parsableByteArray.readInt();
      if (atomType == TYPE_STYL) {
        checkArgument(parsableByteArray.bytesLeft() >= SIZE_SHORT);
        int styleRecordCount = parsableByteArray.readUnsignedShort();
        for (int i = 0; i < styleRecordCount; i++) {
          applyStyleRecord(parsableByteArray, cueText);
        }
      } else if (atomType == TYPE_TBOX && customVerticalPlacement) {
        checkArgument(parsableByteArray.bytesLeft() >= SIZE_SHORT);
        int requestedVerticalPlacement = parsableByteArray.readUnsignedShort();
        verticalPlacement = (float) requestedVerticalPlacement / calculatedVideoTrackHeight;
        verticalPlacement = Util.constrainValue(verticalPlacement, 0.0f, 0.95f);
      }
      parsableByteArray.setPosition(position + atomSize);
    }
    Cue cue =
        new Cue.Builder()
            .setText(cueText)
            .setLine(verticalPlacement, LINE_TYPE_FRACTION)
            .setLineAnchor(ANCHOR_TYPE_START)
            .build();
    output.accept(
        new CuesWithTiming(
            ImmutableList.of(cue),
            /* startTimeUs= */ C.TIME_UNSET,
            /* durationUs= */ C.TIME_UNSET));
  }

  private static String readSubtitleText(ParsableByteArray parsableByteArray) {
    checkArgument(parsableByteArray.bytesLeft() >= SIZE_SHORT);
    int textLength = parsableByteArray.readUnsignedShort();
    if (textLength == 0) {
      return "";
    }
    int textStartPosition = parsableByteArray.getPosition();
    @Nullable Charset charset = parsableByteArray.readUtfCharsetFromBom();
    int bomSize = parsableByteArray.getPosition() - textStartPosition;
    return parsableByteArray.readString(
        textLength - bomSize, charset != null ? charset : Charsets.UTF_8);
  }

  private void applyStyleRecord(
      ParsableByteArray parsableByteArray, SpannableStringBuilder cueText) {
    checkArgument(parsableByteArray.bytesLeft() >= SIZE_STYLE_RECORD);
    int start = parsableByteArray.readUnsignedShort();
    int end = parsableByteArray.readUnsignedShort();
    parsableByteArray.skipBytes(2); // font identifier
    int fontFace = parsableByteArray.readUnsignedByte();
    parsableByteArray.skipBytes(1); // font size
    int colorRgba = parsableByteArray.readInt();

    if (end > cueText.length()) {
      Log.w(
          TAG, "Truncating styl end (" + end + ") to cueText.length() (" + cueText.length() + ").");
      end = cueText.length();
    }
    if (start >= end) {
      Log.w(TAG, "Ignoring styl with start (" + start + ") >= end (" + end + ").");
      return;
    }
    attachFontFace(cueText, fontFace, defaultFontFace, start, end, SPAN_PRIORITY_HIGH);
    attachColor(cueText, colorRgba, defaultColorRgba, start, end, SPAN_PRIORITY_HIGH);
  }

  private static void attachFontFace(
      SpannableStringBuilder cueText,
      int fontFace,
      int defaultFontFace,
      int start,
      int end,
      int spanPriority) {
    if (fontFace != defaultFontFace) {
      final int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | spanPriority;
      boolean isBold = (fontFace & FONT_FACE_BOLD) != 0;
      boolean isItalic = (fontFace & FONT_FACE_ITALIC) != 0;
      if (isBold) {
        if (isItalic) {
          cueText.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, end, flags);
        } else {
          cueText.setSpan(new StyleSpan(Typeface.BOLD), start, end, flags);
        }
      } else if (isItalic) {
        cueText.setSpan(new StyleSpan(Typeface.ITALIC), start, end, flags);
      }
      boolean isUnderlined = (fontFace & FONT_FACE_UNDERLINE) != 0;
      if (isUnderlined) {
        cueText.setSpan(new UnderlineSpan(), start, end, flags);
      }
      if (!isUnderlined && !isBold && !isItalic) {
        cueText.setSpan(new StyleSpan(Typeface.NORMAL), start, end, flags);
      }
    }
  }

  private static void attachColor(
      SpannableStringBuilder cueText,
      int colorRgba,
      int defaultColorRgba,
      int start,
      int end,
      int spanPriority) {
    if (colorRgba != defaultColorRgba) {
      int colorArgb = ((colorRgba & 0xFF) << 24) | (colorRgba >>> 8);
      cueText.setSpan(
          new ForegroundColorSpan(colorArgb),
          start,
          end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | spanPriority);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private static void attachFontFamily(
      SpannableStringBuilder cueText, String fontFamily, int start, int end) {
    if (fontFamily != Tx3gParser.DEFAULT_FONT_FAMILY) {
      cueText.setSpan(
          new TypefaceSpan(fontFamily),
          start,
          end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | Tx3gParser.SPAN_PRIORITY_LOW);
    }
  }
}
