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
package com.google.android.exoplayer2.text.tx3g;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.nio.charset.Charset;

/**
 * A {@link SimpleSubtitleDecoder} for tx3g.
 * <p>
 * Currently only supports parsing of a single text track.
 */
public final class Tx3gDecoder extends SimpleSubtitleDecoder {

  private static final char BOM_UTF16_BE = '\uFEFF';
  private static final char BOM_UTF16_LE = '\uFFFE';

  private static final int TYPE_STYL = Util.getIntegerCodeForString("styl");

  private static final int SIZE_ATOM_HEADER = 8;
  private static final int SIZE_SHORT = 2;
  private static final int SIZE_BOM_UTF16 = 2;
  private static final int SIZE_STYLE_RECORD = 12;

  private static final int FONT_FACE_BOLD = 0x0001;
  private static final int FONT_FACE_ITALIC = 0x0002;
  private static final int FONT_FACE_UNDERLINE = 0x0004;

  private final ParsableByteArray parsableByteArray;

  public Tx3gDecoder() {
    super("Tx3gDecoder");
    parsableByteArray = new ParsableByteArray();
  }

  @Override
  protected Subtitle decode(byte[] bytes, int length, boolean reset)
      throws SubtitleDecoderException {
    try {
      parsableByteArray.reset(bytes, length);
      String cueTextString = readSubtitleText(parsableByteArray);
      if (cueTextString.isEmpty()) {
        return Tx3gSubtitle.EMPTY;
      }
      SpannableStringBuilder cueText = new SpannableStringBuilder(cueTextString);
      while (parsableByteArray.bytesLeft() >= SIZE_ATOM_HEADER) {
        int atomSize = parsableByteArray.readInt();
        int atomType = parsableByteArray.readInt();
        if (atomType == TYPE_STYL) {
          Assertions.checkArgument(parsableByteArray.bytesLeft() >= SIZE_SHORT);
          int styleRecordCount = parsableByteArray.readUnsignedShort();
          for (int i = 0; i < styleRecordCount; i++) {
            applyStyleRecord(parsableByteArray, cueText);
          }
        } else {
          parsableByteArray.skipBytes(atomSize - SIZE_ATOM_HEADER);
        }
      }
      return new Tx3gSubtitle(new Cue(cueText));
    } catch (IllegalArgumentException e) {
      throw new SubtitleDecoderException("Unexpected subtitle format.", e);
    }
  }

  private static String readSubtitleText(ParsableByteArray parsableByteArray) {
    Assertions.checkArgument(parsableByteArray.bytesLeft() >= SIZE_SHORT);
    int textLength = parsableByteArray.readUnsignedShort();
    if (textLength == 0) {
      return "";
    }
    if (parsableByteArray.bytesLeft() >= SIZE_BOM_UTF16) {
      char firstChar = parsableByteArray.peekChar();
      if (firstChar == BOM_UTF16_BE || firstChar == BOM_UTF16_LE) {
        return parsableByteArray.readString(textLength, Charset.forName(C.UTF16_NAME));
      }
    }
    return parsableByteArray.readString(textLength, Charset.forName(C.UTF8_NAME));
  }

  private static void applyStyleRecord(ParsableByteArray parsableByteArray,
      SpannableStringBuilder cueText) {
    Assertions.checkArgument(parsableByteArray.bytesLeft() >= SIZE_STYLE_RECORD);
    int start = parsableByteArray.readUnsignedShort();
    int end = parsableByteArray.readUnsignedShort();
    parsableByteArray.skipBytes(2); // font identifier
    int fontFace = parsableByteArray.readUnsignedByte();
    parsableByteArray.skipBytes(1); // font size
    int colorRgba = parsableByteArray.readInt();

    if (fontFace != 0) {
      attachFontFace(cueText, fontFace, start, end);
    }
    attachColor(cueText, colorRgba, start, end);
  }

  private static void attachFontFace(SpannableStringBuilder cueText, int fontFace, int start,
      int end) {
    boolean isBold = (fontFace & FONT_FACE_BOLD) != 0;
    boolean isItalic = (fontFace & FONT_FACE_ITALIC) != 0;
    if (isBold) {
      if (isItalic) {
        cueText.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      } else {
        cueText.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    } else if (isItalic) {
      cueText.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    boolean isUnderlined = (fontFace & FONT_FACE_UNDERLINE) != 0;
    if (isUnderlined) {
      cueText.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
  }

  private static void attachColor(SpannableStringBuilder cueText, int colorRgba, int start,
      int end) {
    int colorArgb = ((colorRgba & 0xFF) << 24) | (colorRgba >>> 8);
    cueText.setSpan(new ForegroundColorSpan(colorArgb), start, end,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }
}
