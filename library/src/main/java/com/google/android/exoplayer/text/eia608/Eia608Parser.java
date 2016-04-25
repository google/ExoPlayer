/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.text.eia608;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.text.SubtitleInputBuffer;
import com.google.android.exoplayer.text.SubtitleOutputBuffer;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.extensions.Decoder;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.TreeSet;

/**
 * Facilitates the extraction and parsing of EIA-608 (a.k.a. "line 21 captions" and "CEA-608")
 * Closed Captions from the SEI data block from H.264.
 */
public final class Eia608Parser implements
    Decoder<SubtitleInputBuffer, SubtitleOutputBuffer, ParserException> {

  private static final int NUM_INPUT_BUFFERS = 10;
  private static final int NUM_OUTPUT_BUFFERS = 2;

  private static final int PAYLOAD_TYPE_CC = 4;
  private static final int COUNTRY_CODE = 0xB5;
  private static final int PROVIDER_CODE = 0x31;
  private static final int USER_ID = 0x47413934; // "GA94"
  private static final int USER_DATA_TYPE_CODE = 0x3;

  private static final int CC_MODE_UNKNOWN = 0;
  private static final int CC_MODE_ROLL_UP = 1;
  private static final int CC_MODE_POP_ON = 2;
  private static final int CC_MODE_PAINT_ON = 3;

  // The default number of rows to display in roll-up captions mode.
  private static final int DEFAULT_CAPTIONS_ROW_COUNT = 4;

  // Basic North American 608 CC char set, mostly ASCII. Indexed by (char-0x20).
  private static final int[] BASIC_CHARACTER_SET = new int[] {
    0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,     //   ! " # $ % & '
    0x28, 0x29,                                         // ( )
    0xE1,       // 2A: 225 'á' "Latin small letter A with acute"
    0x2B, 0x2C, 0x2D, 0x2E, 0x2F,                       //       + , - . /
    0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,     // 0 1 2 3 4 5 6 7
    0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F,     // 8 9 : ; < = > ?
    0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47,     // @ A B C D E F G
    0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F,     // H I J K L M N O
    0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57,     // P Q R S T U V W
    0x58, 0x59, 0x5A, 0x5B,                             // X Y Z [
    0xE9,       // 5C: 233 'é' "Latin small letter E with acute"
    0x5D,                                               //           ]
    0xED,       // 5E: 237 'í' "Latin small letter I with acute"
    0xF3,       // 5F: 243 'ó' "Latin small letter O with acute"
    0xFA,       // 60: 250 'ú' "Latin small letter U with acute"
    0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67,           //   a b c d e f g
    0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6E, 0x6F,     // h i j k l m n o
    0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77,     // p q r s t u v w
    0x78, 0x79, 0x7A,                                   // x y z
    0xE7,       // 7B: 231 'ç' "Latin small letter C with cedilla"
    0xF7,       // 7C: 247 '÷' "Division sign"
    0xD1,       // 7D: 209 'Ñ' "Latin capital letter N with tilde"
    0xF1,       // 7E: 241 'ñ' "Latin small letter N with tilde"
    0x25A0      // 7F:         "Black Square" (NB: 2588 = Full Block)
  };

  // Special North American 608 CC char set.
  private static final int[] SPECIAL_CHARACTER_SET = new int[] {
    0xAE,    // 30: 174 '®' "Registered Sign" - registered trademark symbol
    0xB0,    // 31: 176 '°' "Degree Sign"
    0xBD,    // 32: 189 '½' "Vulgar Fraction One Half" (1/2 symbol)
    0xBF,    // 33: 191 '¿' "Inverted Question Mark"
    0x2122,  // 34:         "Trade Mark Sign" (tm superscript)
    0xA2,    // 35: 162 '¢' "Cent Sign"
    0xA3,    // 36: 163 '£' "Pound Sign" - pounds sterling
    0x266A,  // 37:         "Eighth Note" - music note
    0xE0,    // 38: 224 'à' "Latin small letter A with grave"
    0x20,    // 39:         TRANSPARENT SPACE - for now use ordinary space
    0xE8,    // 3A: 232 'è' "Latin small letter E with grave"
    0xE2,    // 3B: 226 'â' "Latin small letter A with circumflex"
    0xEA,    // 3C: 234 'ê' "Latin small letter E with circumflex"
    0xEE,    // 3D: 238 'î' "Latin small letter I with circumflex"
    0xF4,    // 3E: 244 'ô' "Latin small letter O with circumflex"
    0xFB     // 3F: 251 'û' "Latin small letter U with circumflex"
  };

  // Extended Spanish/Miscellaneous and French char set.
  private static final int[] SPECIAL_ES_FR_CHARACTER_SET = new int[] {
    // Spanish and misc.
    0xC1, 0xC9, 0xD3, 0xDA, 0xDC, 0xFC, 0x2018, 0xA1,
    0x2A, 0x27, 0x2014, 0xA9, 0x2120, 0x2022, 0x201C, 0x201D,
    // French.
    0xC0, 0xC2, 0xC7, 0xC8, 0xCA, 0xCB, 0xEB, 0xCE,
    0xCF, 0xEF, 0xD4, 0xD9, 0xF9, 0xDB, 0xAB, 0xBB
  };

  //Extended Portuguese and German/Danish char set.
  private static final int[] SPECIAL_PT_DE_CHARACTER_SET = new int[] {
    // Portuguese.
    0xC3, 0xE3, 0xCD, 0xCC, 0xEC, 0xD2, 0xF2, 0xD5,
    0xF5, 0x7B, 0x7D, 0x5C, 0x5E, 0x5F, 0x7C, 0x7E,
    // German/Danish.
    0xC4, 0xE4, 0xD6, 0xF6, 0xDF, 0xA5, 0xA4, 0x2502,
    0xC5, 0xE5, 0xD8, 0xF8, 0x250C, 0x2510, 0x2514, 0x2518
  };

  private final LinkedList<SubtitleInputBuffer> availableInputBuffers;
  private final LinkedList<SubtitleOutputBuffer> availableOutputBuffers;
  private final TreeSet<SubtitleInputBuffer> queuedInputBuffers;

  private final ParsableBitArray seiBuffer;
  private final StringBuilder textStringBuilder;
  private final ArrayList<ClosedCaption> captions;

  private final StringBuilder captionStringBuilder;

  private SubtitleInputBuffer dequeuedInputBuffer;

  private int captionMode;
  private int captionRowCount;
  private String captionString;
  private ClosedCaptionCtrl repeatableControl;

  public Eia608Parser() {
    availableInputBuffers = new LinkedList<>();
    for (int i = 0; i < NUM_INPUT_BUFFERS; i++) {
      availableInputBuffers.add(new SubtitleInputBuffer());
    }
    availableOutputBuffers = new LinkedList<>();
    for (int i = 0; i < NUM_OUTPUT_BUFFERS; i++) {
      availableOutputBuffers.add(new Eia608SubtitleOutputBuffer(this));
    }
    queuedInputBuffers = new TreeSet<>();

    seiBuffer = new ParsableBitArray();
    textStringBuilder = new StringBuilder();
    captions = new ArrayList<>();

    captionStringBuilder = new StringBuilder();

    setCaptionMode(CC_MODE_UNKNOWN);
    captionRowCount = DEFAULT_CAPTIONS_ROW_COUNT;
  }

  @Override
  public SubtitleInputBuffer dequeueInputBuffer() throws ParserException {
    Assertions.checkState(dequeuedInputBuffer == null);
    if (availableInputBuffers.isEmpty()) {
      return null;
    }
    dequeuedInputBuffer = availableInputBuffers.pollFirst();
    return dequeuedInputBuffer;
  }

  @Override
  public void queueInputBuffer(SubtitleInputBuffer inputBuffer) throws ParserException {
    Assertions.checkArgument(inputBuffer != null);
    Assertions.checkArgument(inputBuffer == dequeuedInputBuffer);
    queuedInputBuffers.add(inputBuffer);
    dequeuedInputBuffer = null;
  }

  @Override
  public SubtitleOutputBuffer dequeueOutputBuffer() throws ParserException {
    if (queuedInputBuffers.isEmpty() || availableOutputBuffers.isEmpty()) {
      return null;
    }

    SubtitleOutputBuffer outputBuffer = availableOutputBuffers.pollFirst();
    SubtitleInputBuffer inputBuffer = queuedInputBuffers.pollFirst();

    // TODO: investigate ways of batching multiple SubtitleInputBuffers into a single
    // SubtitleOutputBuffer
    if (inputBuffer.isEndOfStream()) {
      outputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
      return outputBuffer;
    }
    ClosedCaptionList captionList = decode(inputBuffer);
    Eia608Subtitle subtitle = generateSubtitle(captionList);
    outputBuffer.setOutput(inputBuffer.timeUs, subtitle, 0);
    if (inputBuffer.isDecodeOnly()) {
      outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
    }
    releaseInputBuffer(inputBuffer);
    return outputBuffer;
  }

  private void releaseInputBuffer(SubtitleInputBuffer inputBuffer) {
    inputBuffer.clear();
    availableInputBuffers.add(inputBuffer);
  }

  protected void releaseOutputBuffer(SubtitleOutputBuffer outputBuffer) {
    outputBuffer.clear();
    availableOutputBuffers.add(outputBuffer);
  }

  @Override
  public void flush() {
    setCaptionMode(CC_MODE_UNKNOWN);
    captionRowCount = DEFAULT_CAPTIONS_ROW_COUNT;
    captionStringBuilder.setLength(0);
    captionString = null;
    repeatableControl = null;
    while (!queuedInputBuffers.isEmpty()) {
      releaseInputBuffer(queuedInputBuffers.pollFirst());
    }
    if (dequeuedInputBuffer != null) {
      releaseInputBuffer(dequeuedInputBuffer);
      dequeuedInputBuffer = null;
    }
  }

  @Override
  public void release() {
    // Do nothing
  }

  private ClosedCaptionList decode(SubtitleInputBuffer inputBuffer) {
    if (inputBuffer.size < 10) {
      return null;
    }

    captions.clear();
    textStringBuilder.setLength(0);
    seiBuffer.reset(inputBuffer.data.array());

    // country_code (8) + provider_code (16) + user_identifier (32) + user_data_type_code (8) +
    // reserved (1) + process_cc_data_flag (1) + zero_bit (1)
    seiBuffer.skipBits(67);
    int ccCount = seiBuffer.readBits(5);
    seiBuffer.skipBits(8);

    for (int i = 0; i < ccCount; i++) {
      seiBuffer.skipBits(5); // one_bit + reserved
      boolean ccValid = seiBuffer.readBit();
      if (!ccValid) {
        seiBuffer.skipBits(18);
        continue;
      }
      int ccType = seiBuffer.readBits(2);
      if (ccType != 0) {
        seiBuffer.skipBits(16);
        continue;
      }
      seiBuffer.skipBits(1);
      byte ccData1 = (byte) seiBuffer.readBits(7);
      seiBuffer.skipBits(1);
      byte ccData2 = (byte) seiBuffer.readBits(7);

      // Ignore empty captions.
      if (ccData1 == 0 && ccData2 == 0) {
        continue;
      }

      // Special North American character set.
      // ccData2 - P|0|1|1|X|X|X|X
      if ((ccData1 == 0x11 || ccData1 == 0x19)
          && ((ccData2 & 0x70) == 0x30)) {
        textStringBuilder.append(getSpecialChar(ccData2));
        continue;
      }

      // Extended Spanish/Miscellaneous and French character set.
      // ccData2 - P|0|1|X|X|X|X|X
      if ((ccData1 == 0x12 || ccData1 == 0x1A)
          && ((ccData2 & 0x60) == 0x20)) {
        backspace(); // Remove standard equivalent of the special extended char.
        textStringBuilder.append(getExtendedEsFrChar(ccData2));
        continue;
      }

      // Extended Portuguese and German/Danish character set.
      // ccData2 - P|0|1|X|X|X|X|X
      if ((ccData1 == 0x13 || ccData1 == 0x1B)
          && ((ccData2 & 0x60) == 0x20)) {
        backspace(); // Remove standard equivalent of the special extended char.
        textStringBuilder.append(getExtendedPtDeChar(ccData2));
        continue;
      }

      // Control character.
      if (ccData1 < 0x20) {
        addCtrl(ccData1, ccData2);
        continue;
      }

      // Basic North American character set.
      textStringBuilder.append(getChar(ccData1));
      if (ccData2 >= 0x20) {
        textStringBuilder.append(getChar(ccData2));
      }
    }

    addBufferedText();

    if (captions.isEmpty()) {
      return null;
    }

    ClosedCaption[] captionArray = new ClosedCaption[captions.size()];
    captions.toArray(captionArray);
    return new ClosedCaptionList(inputBuffer.timeUs, captionArray);
  }

  public Eia608Subtitle generateSubtitle(ClosedCaptionList captionList) {
    int captionBufferSize = (captionList == null) ? 0 : captionList.captions.length;
    if (captionBufferSize != 0) {
      boolean isRepeatableControl = false;
      for (ClosedCaption caption : captionList.captions) {
        if (caption.type == ClosedCaption.TYPE_CTRL) {
          ClosedCaptionCtrl captionCtrl = (ClosedCaptionCtrl) caption;
          isRepeatableControl = captionBufferSize == 1 && captionCtrl.isRepeatable();
          if (isRepeatableControl && repeatableControl != null
              && repeatableControl.cc1 == captionCtrl.cc1
              && repeatableControl.cc2 == captionCtrl.cc2) {
            repeatableControl = null;
            continue;
          } else if (isRepeatableControl) {
            repeatableControl = captionCtrl;
          }
          if (captionCtrl.isMiscCode()) {
            handleMiscCode(captionCtrl);
          } else if (captionCtrl.isPreambleAddressCode()) {
            handlePreambleAddressCode();
          }
        } else {
          handleText((ClosedCaptionText) caption);
        }
      }

      if (!isRepeatableControl) {
        repeatableControl = null;
      }
      if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_PAINT_ON) {
        captionString = getDisplayCaption();
      }
    }

    return new Eia608Subtitle(captionString);
  }

  private void handleMiscCode(ClosedCaptionCtrl caption) {
    switch (caption.cc2) {
      case ClosedCaptionCtrl.ROLL_UP_CAPTIONS_2_ROWS:
        captionRowCount = 2;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case ClosedCaptionCtrl.ROLL_UP_CAPTIONS_3_ROWS:
        captionRowCount = 3;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case ClosedCaptionCtrl.ROLL_UP_CAPTIONS_4_ROWS:
        captionRowCount = 4;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case ClosedCaptionCtrl.RESUME_CAPTION_LOADING:
        setCaptionMode(CC_MODE_POP_ON);
        return;
      case ClosedCaptionCtrl.RESUME_DIRECT_CAPTIONING:
        setCaptionMode(CC_MODE_PAINT_ON);
        return;
    }

    if (captionMode == CC_MODE_UNKNOWN) {
      return;
    }

    switch (caption.cc2) {
      case ClosedCaptionCtrl.ERASE_DISPLAYED_MEMORY:
        captionString = null;
        if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_PAINT_ON) {
          captionStringBuilder.setLength(0);
        }
        return;
      case ClosedCaptionCtrl.ERASE_NON_DISPLAYED_MEMORY:
        captionStringBuilder.setLength(0);
        return;
      case ClosedCaptionCtrl.END_OF_CAPTION:
        captionString = getDisplayCaption();
        captionStringBuilder.setLength(0);
        return;
      case ClosedCaptionCtrl.CARRIAGE_RETURN:
        maybeAppendNewline();
        return;
      case ClosedCaptionCtrl.BACKSPACE:
        if (captionStringBuilder.length() > 0) {
          captionStringBuilder.setLength(captionStringBuilder.length() - 1);
        }
        return;
    }
  }

  private void handlePreambleAddressCode() {
    // TODO: Add better handling of this with specific positioning.
    maybeAppendNewline();
  }

  private void handleText(ClosedCaptionText caption) {
    captionStringBuilder.append(caption.text);
  }

  private void maybeAppendNewline() {
    int buildLength = captionStringBuilder.length();
    if (buildLength > 0 && captionStringBuilder.charAt(buildLength - 1) != '\n') {
      captionStringBuilder.append('\n');
    }
  }

  private String getDisplayCaption() {
    int buildLength = captionStringBuilder.length();
    if (buildLength == 0) {
      return null;
    }

    boolean endsWithNewline = captionStringBuilder.charAt(buildLength - 1) == '\n';
    if (buildLength == 1 && endsWithNewline) {
      return null;
    }

    int endIndex = endsWithNewline ? buildLength - 1 : buildLength;
    if (captionMode != CC_MODE_ROLL_UP) {
      return captionStringBuilder.substring(0, endIndex);
    }

    int startIndex = 0;
    int searchBackwardFromIndex = endIndex;
    for (int i = 0; i < captionRowCount && searchBackwardFromIndex != -1; i++) {
      searchBackwardFromIndex = captionStringBuilder.lastIndexOf("\n", searchBackwardFromIndex - 1);
    }
    if (searchBackwardFromIndex != -1) {
      startIndex = searchBackwardFromIndex + 1;
    }
    captionStringBuilder.delete(0, startIndex);
    return captionStringBuilder.substring(0, endIndex - startIndex);
  }

  private void setCaptionMode(int captionMode) {
    if (this.captionMode == captionMode) {
      return;
    }

    this.captionMode = captionMode;
    // Clear the working memory.
    captionStringBuilder.setLength(0);
    if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_UNKNOWN) {
      // When switching to roll-up or unknown, we also need to clear the caption.
      captionString = null;
    }
  }

  private static char getChar(byte ccData) {
    int index = (ccData & 0x7F) - 0x20;
    return (char) BASIC_CHARACTER_SET[index];
  }

  private static char getSpecialChar(byte ccData) {
    int index = ccData & 0xF;
    return (char) SPECIAL_CHARACTER_SET[index];
  }

  private static char getExtendedEsFrChar(byte ccData) {
    int index = ccData & 0x1F;
    return (char) SPECIAL_ES_FR_CHARACTER_SET[index];
  }

  private static char getExtendedPtDeChar(byte ccData) {
    int index = ccData & 0x1F;
    return (char) SPECIAL_PT_DE_CHARACTER_SET[index];
  }

  private void addBufferedText() {
    if (textStringBuilder.length() > 0) {
      String textSnippet = textStringBuilder.toString();
      captions.add(new ClosedCaptionText(textSnippet));
      textStringBuilder.setLength(0);
    }
  }

  private void addCtrl(byte ccData1, byte ccData2) {
    addBufferedText();
    captions.add(new ClosedCaptionCtrl(ccData1, ccData2));
  }

  private void backspace() {
    addCtrl((byte) 0x14, ClosedCaptionCtrl.BACKSPACE);
  }

  /**
   * Inspects an sei message to determine whether it contains EIA-608.
   * <p>
   * The position of {@code payload} is left unchanged.
   *
   * @param payloadType The payload type of the message.
   * @param payloadLength The length of the payload.
   * @param payload A {@link ParsableByteArray} containing the payload.
   * @return True if the sei message contains EIA-608. False otherwise.
   */
  public static boolean isSeiMessageEia608(int payloadType, int payloadLength,
      ParsableByteArray payload) {
    if (payloadType != PAYLOAD_TYPE_CC || payloadLength < 8) {
      return false;
    }
    int startPosition = payload.getPosition();
    int countryCode = payload.readUnsignedByte();
    int providerCode = payload.readUnsignedShort();
    int userIdentifier = payload.readInt();
    int userDataTypeCode = payload.readUnsignedByte();
    payload.setPosition(startPosition);
    return countryCode == COUNTRY_CODE && providerCode == PROVIDER_CODE
        && userIdentifier == USER_ID && userDataTypeCode == USER_DATA_TYPE_CODE;
  }

}
