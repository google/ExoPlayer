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
package com.google.android.exoplayer2.text.eia608;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SubtitleDecoder;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.text.SubtitleOutputBuffer;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import java.util.Collections;
import java.util.LinkedList;
import java.util.TreeSet;

/**
 * A {@link SubtitleDecoder} for EIA-608 (also known as "line 21 captions" and "CEA-608").
 */
public final class Eia608Decoder implements SubtitleDecoder {

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

  /**
   * Command initiating pop-on style captioning. Subsequent data should be loaded into a
   * non-displayed memory and held there until the {@link #CTRL_END_OF_CAPTION} command is received,
   * at which point the non-displayed memory becomes the displayed memory (and vice versa).
   */
  private static final byte CTRL_RESUME_CAPTION_LOADING = 0x20;
  /**
   * Command initiating roll-up style captioning, with the maximum of 2 rows displayed
   * simultaneously.
   */
  private static final byte CTRL_ROLL_UP_CAPTIONS_2_ROWS = 0x25;
  /**
   * Command initiating roll-up style captioning, with the maximum of 3 rows displayed
   * simultaneously.
   */
  private static final byte CTRL_ROLL_UP_CAPTIONS_3_ROWS = 0x26;
  /**
   * Command initiating roll-up style captioning, with the maximum of 4 rows displayed
   * simultaneously.
   */
  private static final byte CTRL_ROLL_UP_CAPTIONS_4_ROWS = 0x27;
  /**
   * Command initiating paint-on style captioning. Subsequent data should be addressed immediately
   * to displayed memory without need for the {@link #CTRL_RESUME_CAPTION_LOADING} command.
   */
  private static final byte CTRL_RESUME_DIRECT_CAPTIONING = 0x29;
  /**
   * Command indicating the end of a pop-on style caption. At this point the caption loaded in
   * non-displayed memory should be swapped with the one in displayed memory. If no
   * {@link #CTRL_RESUME_CAPTION_LOADING} command has been received, this command forces the
   * receiver into pop-on style.
   */
  private static final byte CTRL_END_OF_CAPTION = 0x2F;

  private static final byte CTRL_ERASE_DISPLAYED_MEMORY = 0x2C;
  private static final byte CTRL_CARRIAGE_RETURN = 0x2D;
  private static final byte CTRL_ERASE_NON_DISPLAYED_MEMORY = 0x2E;
  private static final byte CTRL_DELETE_TO_END_OF_ROW = 0x24;

  private static final byte CTRL_TAB_OFFSET_CHAN_1 = 0x17;
  private static final byte CTRL_TAB_OFFSET_CHAN_2 = 0x1F;

  private static final byte CTRL_BACKSPACE = 0x21;

  private static final byte CTRL_MISC_CHAN_1 = 0x14;
  private static final byte CTRL_MISC_CHAN_2 = 0x1C;

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

  // Maps EIA-608 PAC row bits to rows.
  private static final int[] ROW_INDICES = new int[] { 11, 1, 3, 12, 14, 5, 7, 9 };

  // Maps EIA-608 PAC cursor bits to indents.
  // Note that these indents may not give the intended result, unless the font size is set
  // to allow for a maximum of 32 (or 41) characters per line.
  private static final int[] CURSOR_INDICES = new int[] { 0, 4, 8, 12, 16, 20, 24, 28 };

  private static final int[] COLOR_MAP = new int[] {
    Color.WHITE,
    Color.GREEN,
    Color.BLUE,
    Color.CYAN,
    Color.RED,
    Color.YELLOW,
    Color.MAGENTA,
    Color.BLACK // Only used by Mid Row style changes, for PAC an value of 0x7 means italics.
  };

  // Transparency is defined in the first byte of an integer.
  private static final int TRANSPARENCY_MASK = 0x80FFFFFF;
  private static final int STYLE_ITALIC = Typeface.ITALIC;

  private final LinkedList<SubtitleInputBuffer> availableInputBuffers;
  private final LinkedList<SubtitleOutputBuffer> availableOutputBuffers;
  private final TreeSet<SubtitleInputBuffer> queuedInputBuffers;

  private final ParsableByteArray ccData;
  private final LinkedList<Eia608CueBuilder> cues;

  private long playbackPositionUs;

  private SubtitleInputBuffer dequeuedInputBuffer;

  private int captionMode;
  private int captionRowCount;
  boolean nextRowDown;

  // The Cue that's currently being built and decoded.
  private Eia608CueBuilder currentCue;

  private boolean repeatableControlSet;
  private byte repeatableControlCc1;
  private byte repeatableControlCc2;

  private Eia608Subtitle subtitle;

  public Eia608Decoder() {
    availableInputBuffers = new LinkedList<>();
    for (int i = 0; i < NUM_INPUT_BUFFERS; i++) {
      availableInputBuffers.add(new SubtitleInputBuffer());
    }
    availableOutputBuffers = new LinkedList<>();
    for (int i = 0; i < NUM_OUTPUT_BUFFERS; i++) {
      availableOutputBuffers.add(new Eia608SubtitleOutputBuffer(this));
    }
    queuedInputBuffers = new TreeSet<>();

    ccData = new ParsableByteArray();
    subtitle = new Eia608Subtitle();
    cues = new LinkedList<>();
    nextRowDown = false;

    setCaptionMode(CC_MODE_UNKNOWN);
    captionRowCount = DEFAULT_CAPTIONS_ROW_COUNT;
  }

  @Override
  public String getName() {
    return "Eia608Decoder";
  }

  @Override
  public void setPositionUs(long positionUs) {
    playbackPositionUs = positionUs;
  }

  @Override
  public SubtitleInputBuffer dequeueInputBuffer() throws SubtitleDecoderException {
    Assertions.checkState(dequeuedInputBuffer == null);
    if (availableInputBuffers.isEmpty()) {
      return null;
    }
    dequeuedInputBuffer = availableInputBuffers.pollFirst();
    return dequeuedInputBuffer;
  }

  @Override
  public void queueInputBuffer(SubtitleInputBuffer inputBuffer) throws SubtitleDecoderException {
    Assertions.checkArgument(inputBuffer != null);
    Assertions.checkArgument(inputBuffer == dequeuedInputBuffer);
    queuedInputBuffers.add(inputBuffer);
    dequeuedInputBuffer = null;
  }

  @Override
  public SubtitleOutputBuffer dequeueOutputBuffer() throws SubtitleDecoderException {
    if (availableOutputBuffers.isEmpty()) {
      return null;
    }

    // iterate through all available input buffers whose timestamps are less than or equal
    // to the current playback position; processing input buffers for future content should
    // be deferred until they would be applicable
    while (!queuedInputBuffers.isEmpty()
        && queuedInputBuffers.first().timeUs <= playbackPositionUs) {
      SubtitleInputBuffer inputBuffer = queuedInputBuffers.pollFirst();

      // If the input buffer indicates we've reached the end of the stream, we can
      // return immediately with an output buffer propagating that
      if (inputBuffer.isEndOfStream()) {
        SubtitleOutputBuffer outputBuffer = availableOutputBuffers.pollFirst();
        outputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
        releaseInputBuffer(inputBuffer);
        return outputBuffer;
      }

      decode(inputBuffer);

      // check if we have any caption updates to report
      if (!inputBuffer.isDecodeOnly()) {
        SubtitleOutputBuffer outputBuffer = availableOutputBuffers.pollFirst();
        outputBuffer.setContent(inputBuffer.timeUs, subtitle, 0);
        releaseInputBuffer(inputBuffer);
        return outputBuffer;
      }

      releaseInputBuffer(inputBuffer);
    }

    return null;
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
    playbackPositionUs = 0;
    currentCue = new Eia608CueBuilder();
    cues.clear();
    nextRowDown = false;
    repeatableControlSet = false;
    repeatableControlCc1 = 0;
    repeatableControlCc2 = 0;
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

  private void decode(SubtitleInputBuffer inputBuffer) {
    ccData.reset(inputBuffer.data.array(), inputBuffer.data.limit());
    boolean captionDataProcessed = false;
    boolean isRepeatableControl = false;
    while (ccData.bytesLeft() > 0) {
      byte ccData1 = (byte) (ccData.readUnsignedByte() & 0x7F);
      byte ccData2 = (byte) (ccData.readUnsignedByte() & 0x7F);

      // Ignore empty captions.
      if (ccData1 == 0 && ccData2 == 0) {
        continue;
      }
      // If we've reached this point then there is data to process; flag that work has been done.
      captionDataProcessed = true;

      // Special North American character set.
      // ccData2 - P|0|1|1|X|X|X|X
      if ((ccData1 == 0x11 || ccData1 == 0x19) && ((ccData2 & 0x70) == 0x30)) {
        currentCue.append(getSpecialChar(ccData2));
        continue;
      }

      // Extended Spanish/Miscellaneous and French character set.
      // ccData2 - P|0|1|X|X|X|X|X
      if ((ccData1 == 0x12 || ccData1 == 0x1A) && ((ccData2 & 0x60) == 0x20)) {
        currentCue.backspace(); // Remove standard equivalent of the special extended char.
        currentCue.append(getExtendedEsFrChar(ccData2));
        continue;
      }

      // Extended Portuguese and German/Danish character set.
      // ccData2 - P|0|1|X|X|X|X|X
      if ((ccData1 == 0x13 || ccData1 == 0x1B) && ((ccData2 & 0x60) == 0x20)) {
        currentCue.backspace(); // Remove standard equivalent of the special extended char.
        currentCue.append(getExtendedPtDeChar(ccData2));
        continue;
      }

      // Mid row changes.
      if ((ccData1 == 0x11 || ccData1 == 0x19) && ccData2 >= 0x20 && ccData2 <= 0x2F) {
        handleMidrowCode(ccData1, ccData2);
      }

      // Control character.
      if (ccData1 < 0x20) {
        isRepeatableControl = handleCtrl(ccData1, ccData2);
        continue;
      }

      // Basic North American character set.
      currentCue.append(getChar(ccData1));
      if (ccData2 >= 0x20) {
        currentCue.append(getChar(ccData2));
      }
    }

    if (captionDataProcessed) {
      if (!isRepeatableControl) {
        repeatableControlSet = false;
      }
      if (captionMode == CC_MODE_PAINT_ON || captionMode == CC_MODE_ROLL_UP) {
        renderCues();
      }
    }
  }

  private boolean handleCtrl(byte cc1, byte cc2) {
    boolean isRepeatableControl = isRepeatable(cc1);
    if (isRepeatableControl && repeatableControlSet
        && repeatableControlCc1 == cc1
        && repeatableControlCc2 == cc2) {
      repeatableControlSet = false;
      return true;
    } else if (isRepeatableControl) {
      repeatableControlSet = true;
      repeatableControlCc1 = cc1;
      repeatableControlCc2 = cc2;
    }
    if (isMiscCode(cc1, cc2)) {
      handleMiscCode(cc2);
    } else if (isPreambleAddressCode(cc1, cc2)) {
      handlePreambleAddressCode(cc1, cc2);
    } else if (isTabOffset(cc1, cc2)) {
      handleTabOffset(cc2);
    }
    return isRepeatableControl;
  }

  private void handleMiscCode(byte cc2) {
    switch (cc2) {
      case CTRL_ROLL_UP_CAPTIONS_2_ROWS:
        captionRowCount = 2;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case CTRL_ROLL_UP_CAPTIONS_3_ROWS:
        captionRowCount = 3;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case CTRL_ROLL_UP_CAPTIONS_4_ROWS:
        captionRowCount = 4;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case CTRL_RESUME_CAPTION_LOADING:
        setCaptionMode(CC_MODE_POP_ON);
        return;
      case CTRL_RESUME_DIRECT_CAPTIONING:
        setCaptionMode(CC_MODE_PAINT_ON);
        return;
    }

    if (captionMode == CC_MODE_UNKNOWN) {
      return;
    }

    switch (cc2) {
      case CTRL_ERASE_DISPLAYED_MEMORY:
        if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_PAINT_ON) {
          currentCue = new Eia608CueBuilder();
          cues.clear();
        }
        subtitle.setCues(Collections.<Cue>emptyList());
        return;
      case CTRL_ERASE_NON_DISPLAYED_MEMORY:
        currentCue = new Eia608CueBuilder();
        cues.clear();
        return;
      case CTRL_END_OF_CAPTION:
        renderCues();
        cues.clear();
        return;
      case CTRL_CARRIAGE_RETURN:
        // Each time a Carriage Return is received, the text in the top row of the window is erased
        // from memory and from the display. The remaining rows of text are each rolled up into the
        // next highest row in the window, leaving the base row blank and ready to accept new text.
        if (captionMode == CC_MODE_ROLL_UP) {
          for (Eia608CueBuilder cue : cues) {
            // Roll up all the other rows.
            if (!cue.rollUp()) {
              cues.remove(cue);
            }
          }
          currentCue = new Eia608CueBuilder();
          cues.add(currentCue);
          while (cues.size() > captionRowCount) {
            cues.pollFirst();
          }
        }
        return;
      case CTRL_BACKSPACE:
        currentCue.backspace();
        return;
      case CTRL_DELETE_TO_END_OF_ROW:
        // TODO: Clear currentCue's captionText.
        return;
    }
  }

  private void handlePreambleAddressCode(byte cc1, byte cc2) {
    // For PAC layout see: https://en.wikipedia.org/wiki/EIA-608#Control_commands

    // Parse the "next row down" toggle.
    nextRowDown = (cc2 & 0x20) != 0;

    int row = ROW_INDICES[cc1 & 0x7];
    if (row != currentCue.getRow() || nextRowDown) {
      currentCue = new Eia608CueBuilder();
      cues.add(currentCue);
    }
    currentCue.setRow(nextRowDown ? ++row : row);

    boolean underline = (cc2 & 0x1) != 0;
    if (underline) {
      currentCue.setCharacterStyle(new UnderlineSpan());
    }

    int attribute = cc2 >> 1 & 0xF;
    if (attribute >= 0x0 && attribute < 0x7) {
      // Attribute is a foreground color
      currentCue.setCharacterStyle(new ForegroundColorSpan(COLOR_MAP[attribute]));
    } else if (attribute == 0x7) {
      // Attribute is "italics"
      currentCue.setCharacterStyle(new StyleSpan(STYLE_ITALIC));
    } else if (attribute >= 0x8 && attribute <= 0xF) {
      // Attribute is an indent
      currentCue.setIndent(CURSOR_INDICES[attribute & 0x7]);
    }
  }

  private void handleMidrowCode(byte cc1, byte cc2) {
    boolean transparentOrUnderline = (cc2 & 0x1) != 0;
    int attribute = cc2 >> 1 & 0xF;
    if ((cc1 & 0x1) != 0) {
      // Background Color
      currentCue.setCharacterStyle(new BackgroundColorSpan(transparentOrUnderline ?
        COLOR_MAP[attribute] & TRANSPARENCY_MASK : COLOR_MAP[attribute]));
    } else {
      // Foreground color
      currentCue.setCharacterStyle(new ForegroundColorSpan(COLOR_MAP[attribute]));
      if (transparentOrUnderline) {
        // Text should be underlined
        currentCue.setCharacterStyle(new UnderlineSpan());
      }
    }
  }

  private void handleTabOffset(byte cc2) {
    currentCue.tab(cc2 - 0x20);
  }

  private void setCaptionMode(int captionMode) {
    if (this.captionMode == captionMode) {
      return;
    }

    this.captionMode = captionMode;
    // Clear the working memory.
    currentCue = new Eia608CueBuilder();
    if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_UNKNOWN) {
      // When switching to roll-up or unknown, we also need to clear the caption.
      cues.clear();
    }
  }

  private void renderCues() {
    subtitle.setCues(Eia608CueBuilder.buildCues(cues));
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

  private static boolean isMiscCode(byte cc1, byte cc2) {
    return (cc1 == CTRL_MISC_CHAN_1 || cc1 == CTRL_MISC_CHAN_2)
        && (cc2 >= 0x20 && cc2 <= 0x2F);
  }

  private static boolean isPreambleAddressCode(byte cc1, byte cc2) {
    return (cc1 >= 0x10 && cc1 <= 0x1F) && (cc2 >= 0x40 && cc2 <= 0x7F);
  }

  private static boolean isRepeatable(byte cc1) {
    return cc1 >= 0x10 && cc1 <= 0x1F;
  }

  private static boolean isTabOffset(byte cc1, byte cc2) {
    return (cc1 == CTRL_TAB_OFFSET_CHAN_1 || cc1 == CTRL_TAB_OFFSET_CHAN_2)
      && (cc2 >= 0x21 && cc2 <= 0x23);
  }

  /**
   * Inspects an sei message to determine whether it contains EIA-608.
   * <p>
   * The position of {@code payload} is left unchanged.
   *
   * @param payloadType The payload type of the message.
   * @param payloadLength The length of the payload.
   * @param payload A {@link ParsableByteArray} containing the payload.
   * @return Whether the sei message contains EIA-608.
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
