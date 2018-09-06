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
package com.google.android.exoplayer2.text.cea;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout.Alignment;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoder;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link SubtitleDecoder} for CEA-608 (also known as "line 21 captions" and "EIA-608").
 */
public final class Cea608Decoder extends CeaDecoder {

  private static final int CC_VALID_FLAG = 0x04;
  private static final int CC_TYPE_FLAG = 0x02;
  private static final int CC_FIELD_FLAG = 0x01;

  private static final int NTSC_CC_FIELD_1 = 0x00;
  private static final int NTSC_CC_FIELD_2 = 0x01;
  private static final int NTSC_CC_CHANNEL_1 = 0x00;
  private static final int NTSC_CC_CHANNEL_2 = 0x01;

  private static final int CC_MODE_UNKNOWN = 0;
  private static final int CC_MODE_ROLL_UP = 1;
  private static final int CC_MODE_POP_ON = 2;
  private static final int CC_MODE_PAINT_ON = 3;

  private static final int[] ROW_INDICES = new int[] {11, 1, 3, 12, 14, 5, 7, 9};
  private static final int[] COLUMN_INDICES = new int[] {0, 4, 8, 12, 16, 20, 24, 28};
  private static final int[] COLORS = new int[] {
      Color.WHITE,
      Color.GREEN,
      Color.BLUE,
      Color.CYAN,
      Color.RED,
      Color.YELLOW,
      Color.MAGENTA,
  };

  // The default number of rows to display in roll-up captions mode.
  private static final int DEFAULT_CAPTIONS_ROW_COUNT = 4;

  // An implied first byte for packets that are only 2 bytes long, consisting of marker bits
  // (0b11111) + valid bit (0b1) + NTSC field 1 type bits (0b00).
  private static final byte CC_IMPLICIT_DATA_HEADER = (byte) 0xFC;

  /**
   * Command initiating pop-on style captioning. Subsequent data should be loaded into a
   * non-displayed memory and held there until the {@link #CTRL_END_OF_CAPTION} command is received,
   * at which point the non-displayed memory becomes the displayed memory (and vice versa).
   */
  private static final byte CTRL_RESUME_CAPTION_LOADING = 0x20;

  private static final byte CTRL_BACKSPACE = 0x21;

  @SuppressWarnings("unused")
  private static final byte CTRL_ALARM_OFF= 0x22; // not supported any more

  @SuppressWarnings("unused")
  private static final byte CTRL_ALARM_ON= 0x23; // not supported any more

  private static final byte CTRL_DELETE_TO_END_OF_ROW = 0x24;

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

  @SuppressWarnings("unused")
  private static final byte CTRL_FLASH_ON = 0x28; // not supported any more
  /**
   * Command initiating paint-on style captioning. Subsequent data should be addressed immediately
   * to displayed memory without need for the {@link #CTRL_RESUME_CAPTION_LOADING} command.
   */
  private static final byte CTRL_RESUME_DIRECT_CAPTIONING = 0x29;
  /**
   * TEXT commands are switching to TEXT mode from CAPTION mode. All consecutive incoming
   * data must be filtered out until a command is received that switches back to CAPTION mode
   */
  private static final byte CTRL_TEXT_RESTART = 0x2A;
  private static final byte CTRL_RESUME_TEXT_DISPLAY = 0x2B;

  private static final byte CTRL_ERASE_DISPLAYED_MEMORY = 0x2C;
  private static final byte CTRL_CARRIAGE_RETURN = 0x2D;
  private static final byte CTRL_ERASE_NON_DISPLAYED_MEMORY = 0x2E;

  /**
   * Command indicating the end of a pop-on style caption. At this point the caption loaded in
   * non-displayed memory should be swapped with the one in displayed memory. If no
   * {@link #CTRL_RESUME_CAPTION_LOADING} command has been received, this command forces the
   * receiver into pop-on style.
   */
  private static final byte CTRL_END_OF_CAPTION = 0x2F;

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

  private static final boolean ODD_PARITY_BYTE_TABLE[] = {
    false,	true,	true,	false,	true,	false,	false,	true,	// for values 0 - 7
    true,	false,	false,	true,	false,	true,	true,	false,	// for values 8 - 15
    true,	false,	false,	true,	false,	true,	true,	false,	// for values 16 - 23
    false,	true,	true,	false,	true,	false,	false,	true,	// for values 24 - 31
    true,	false,	false,	true,	false,	true,	true,	false,	// for values 32 - 39
    false,	true,	true,	false,	true,	false,	false,	true,	// for values 40 - 47
    false,	true,	true,	false,	true,	false,	false,	true,	// for values 48 - 55
    true,	false,	false,	true,	false,	true,	true,	false,	// for values 56 - 63
    true,	false,	false,	true,	false,	true,	true,	false,	// for values 64 - 71
    false,	true,	true,	false,	true,	false,	false,	true,	// for values 72 - 79
    false,	true,	true,	false,	true,	false,	false,	true,	// for values 80 - 87
    true,	false,	false,	true,	false,	true,	true,	false,	// for values 88 - 95
    false,	true,	true,	false,	true,	false,	false,	true,	// for values 96 - 103
    true,	false,	false,	true,	false,	true,	true,	false,	// for values 104 - 111
    true,	false,	false,	true,	false,	true,	true,	false,	// for values 112 - 119
    false,	true,	true,	false,	true,	false,	false,	true,	// for values 120 - 127
    false,	false,	true,	false,	false,	false,	false,	true,	// for values 128 - 135
    false,	false,	false,	true,	false,	true,	false,	false,	// for values 136 - 143
    true,	false,	false,	true,	false,	true,	false,	false,	// for values 144 - 151
    true,	true,	false,	false,	true,	false,	true,	true,	// for values 152 - 159
    false,	false,	false,	true,	false,	true,	false,	false,	// for values 160 - 167
    true,	true,	false,	false,	true,	false,	true,	true,	// for values 168 - 175
    false,	true,	false,	false,	true,	false,	true,	true,	// for values 176 - 183
    false,	false,	true,	true,	false,	true,	false,	false,	// for values 184 - 191
    true,	false,	false,	true,	false,	true,	false,	false,	// for values 192 - 199
    true,	true,	false,	false,	true,	false,	true,	true,	// for values 200 - 207
    false,	true,	false,	false,	true,	false,	true,	true,	// for values 208 - 215
    false,	false,	true,	true,	false,	true,	false,	false,	// for values 216 - 223
    true,	true,	false,	false,	true,	false,	true,	true,	// for values 224 - 231
    false,	false,	true,	true,	false,	true,	false,	false,	// for values 232 - 239
    true,	false,	true,	true,	false,	true,	false,	false,	// for values 240 - 247
    true,	true,	false,	false,	true,	false,	true,	true,	// for values 248 - 255
  };

  private final ParsableByteArray ccData;
  private final int packetLength;
  private final int selectedField;
  private final int selectedChannel;
  private final ArrayList<CueBuilder> cueBuilders;

  private CueBuilder currentCueBuilder;
  private List<Cue> cues;
  private List<Cue> lastCues;

  private int captionMode;
  private int captionRowCount;

  private boolean repeatableControlSet;
  private byte repeatableControlCc1;
  private byte repeatableControlCc2;

  private boolean isWideAspectRatio;
  private boolean dataProcessedSinceLastRepeatableControl = true;
  private static final String TAG = Cea608Decoder.class.getSimpleName();
  private static boolean DEBUG = false;
  private List<Cue> savedCues; // For pop-on captions we need to restore old cues after mode switch
  /**
   * The Cea608 stream interleaves byte pairs for 3 modes: CAPTION, TEXT and XDS. Even when we
   * only intend to process CAPTION data, we need to keep track of the current mode and drop all
   * data belonging to unwanted modes.
   * * CAPTION data is highest priority
   * * TEXT mode data can be interleaved into any leftover bandwidth
   * * XDS is allowed on Frame 2 only
   *
   * Value true means the last received and interpreted command was for an unsupported mode
   */
  private boolean dropDataForUnsupportedMode;
  /**
   * All incoming data not having a channel bit belongs to the channel selected by the last
   * command. So we only need to process data not having channel bits when
   * incomingDataTargetChannel matches the selected channel. Possible values are:
   * * NTSC_CC_CHANNEL_1
   * * NTSC_CC_CHANNEL_2
   */
  private int incomingDataTargetChannel;

  // The bandwidth of CEA608 captions is designed for frame rates of 29.97. If the content is
  // 60FPS there might be packets not containing any 608 bytes. Additionally, every byte pair has
  // a header byte, so 1/3rd of this distance value is for those headers
  private final int MAX_ALLOWED_DISTANCE_BETWEEN_CEA608_BYTES = 60;
  private int last608BytesDistance = 0;

  public Cea608Decoder(String mimeType, int accessibilityChannel, List<byte[]> initializationData) {
    ccData = new ParsableByteArray();
    cueBuilders = new ArrayList<>();
    if (initializationData.size() > 0) {
      this.isWideAspectRatio = Cea708InitializationData.fromData(initializationData).isWideAspectRatio;
    }
    currentCueBuilder = new CueBuilder(CC_MODE_UNKNOWN, DEFAULT_CAPTIONS_ROW_COUNT, isWideAspectRatio);
    packetLength = MimeTypes.APPLICATION_MP4CEA608.equals(mimeType) ? 2 : 3;

    // 608 stream has 2 fields and 2 channels on each fields.
    switch (accessibilityChannel) {
      case 1:
        selectedChannel = NTSC_CC_CHANNEL_1;
        selectedField = NTSC_CC_FIELD_1;
        break;
      case 2:
        selectedChannel = NTSC_CC_CHANNEL_2;
        selectedField   = NTSC_CC_FIELD_1;
        break;
      case 3:
        selectedChannel = NTSC_CC_CHANNEL_1;
        selectedField   = NTSC_CC_FIELD_2;
        break;
      case 4:
        selectedChannel = NTSC_CC_CHANNEL_2;
        selectedField = NTSC_CC_FIELD_2;
        break;
      case Format.NO_VALUE:
      default:
        selectedChannel = NTSC_CC_CHANNEL_1;
        selectedField = NTSC_CC_FIELD_1;
        Log.w(TAG,"Subtitle channel was incorrectly set. Defaulting to CC1");
    }

    Log.i(TAG, "Cea608 channel selected: CC" + (1 + selectedChannel + selectedField * 2));

    setCaptionMode(CC_MODE_UNKNOWN);
    resetCueBuilders();
  }

  @Override
  public String getName() {
    return "Cea608Decoder";
  }

  @Override
  public void flush() {
    super.flush();
    cues = null;
    lastCues = null;
    setCaptionMode(CC_MODE_UNKNOWN);
    setCaptionRowCount(DEFAULT_CAPTIONS_ROW_COUNT);
    resetCueBuilders();
    repeatableControlSet = false;
    repeatableControlCc1 = 0;
    repeatableControlCc2 = 0;

    dataProcessedSinceLastRepeatableControl = true;
    dropDataForUnsupportedMode = false;
    incomingDataTargetChannel = NTSC_CC_CHANNEL_1;
    last608BytesDistance = 0;
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  protected boolean isNewSubtitleDataAvailable() {
    return cues != lastCues;
  }

  @Override
  protected Subtitle createSubtitle() {
    lastCues = cues;
    return new CeaSubtitle(cues);
  }

  private int getChannelBit(byte ccData1) {
    // ccData1 - 5th bit selects the channel:  x|x|x|x|C|x|x|x
    return (ccData1 >> 3) & 0x1;
  }

  // returns true if this byte had the channel bit matching of the currently selected channel
  private boolean isBytePairForSelectedChannel(byte ccData1) {
    return getChannelBit(ccData1) == selectedChannel;
  }

  private boolean isCodeForUnsupportedMode(byte cc1, byte cc2) {
    // Control codes from 0x01 to 0x0F indicate the beginning of XDS Data
    if (0x01 <= cc1 && cc1 <= 0x0F) {

      // XDS data should only be inserted on the second FIELD
      Assertions.checkArgument(selectedField == NTSC_CC_FIELD_2);
      return true;
    }

    // 2 commands switch to TEXT mode.
    if (((cc1 & 0xF7) == 0x14) // first byte must be 0x14 or 0x1C based on channel
            && (cc2 == CTRL_TEXT_RESTART || cc2 == CTRL_RESUME_TEXT_DISPLAY)) {
      return true;
    }

    return false;
  }

  private static boolean isControlCodeSwitchingToCaptionMode(byte cc1, byte cc2) {
    // cc1: Matching commands must have the value: 0|0|0|1|C|1|0|0 where C is the channel bit
    if ((cc1 & 0xF7) != 0x14) {
      return false;
    }

    switch (cc2) {
      case CTRL_END_OF_CAPTION:
      case CTRL_RESUME_CAPTION_LOADING:
      case CTRL_RESUME_DIRECT_CAPTIONING:
      case CTRL_ROLL_UP_CAPTIONS_2_ROWS:
      case CTRL_ROLL_UP_CAPTIONS_3_ROWS:
      case CTRL_ROLL_UP_CAPTIONS_4_ROWS:
        return true;
    }
    return false;
  }

  @SuppressWarnings("ByteBufferBackingArray")
  @Override
  protected void decode(SubtitleInputBuffer inputBuffer) {
    ccData.reset(inputBuffer.data.array(), inputBuffer.data.limit());
    boolean captionDataProcessed = false;
    boolean isRepeatableControl = false;
    boolean packetHas608Data = false;
    final int packetSize = ccData.bytesLeft();

    while (ccData.bytesLeft() >= packetLength) {
      byte ccDataHeader = packetLength == 2 ? CC_IMPLICIT_DATA_HEADER
          : (byte) ccData.readUnsignedByte();

      byte ccFullByte1 = (byte) (ccData.readUnsignedByte());
      byte ccFullByte2 = (byte) (ccData.readUnsignedByte());

      boolean byte1ParityBit = (ccFullByte1 & 0x80) != 0;
      boolean byte2ParityBit = (ccFullByte2 & 0x80) != 0;

      byte ccData1 = (byte) (ccFullByte1 & 0x7F); // strip the parity bit
      byte ccData2 = (byte) (ccFullByte2 & 0x7F); // strip the parity bit

      // TODO: We're currently ignoring the top 5 marker bits, which should all be 1s according
      // to the CEA-608 specification. We need to determine if the data should be handled
      // differently when that is not the case.

      if ((ccDataHeader & CC_TYPE_FLAG) != 0) {
        // do not process anything that is not part of the 608 byte stream
        continue;
      }

      packetHas608Data = true;

      // Only examine packets within the selected field
      if ((ccDataHeader & CC_FIELD_FLAG) != selectedField) {
        continue;
      }

      // Ignore empty captions.
      if (ccData1 == 0 && ccData2 == 0) {
        continue;
      }

      // If we've reached this point then there is data to process; flag that work has been done.
      captionDataProcessed = true;

      if ((ccDataHeader & CC_VALID_FLAG) != CC_VALID_FLAG) {
        // To pass NTSC tests we need to clear the captions when corrupt data is met or the stream
        // of the closed captions is turned off (the encoder flips the validity bit)
        resetCueBuilders();
        continue;
      }

      if (dataProcessedSinceLastRepeatableControl) {
        repeatableControlSet = false;
      } else {
        dataProcessedSinceLastRepeatableControl = true;
      }

      // When transmission is digital and reliable (TCP) we should not meet any data corruption.
      // But the NTSC bad parity test content exactly gives the decoder corrupted data, so we
      // need to drop those here. Clearing the screen is expected, so we:
      // - reset the cue builders
      // - update the screen (by having captionDataProcessed set to true above)
      if (byte1ParityBit == ODD_PARITY_BYTE_TABLE[ccData1]
              || byte2ParityBit == ODD_PARITY_BYTE_TABLE[ccData2]) {
        resetCueBuilders();
        continue;
      }

      if (isCodeForUnsupportedMode(ccData1, ccData2)) {
        if (DEBUG) {
          Log.d(TAG,"CEA608 stream contains data for an unsupported mode");
        }
        dropDataForUnsupportedMode = true;
        continue;
      } else if (dropDataForUnsupportedMode) {
        if (!isControlCodeSwitchingToCaptionMode(ccData1, ccData2)) {
          continue;
        } else {
          dropDataForUnsupportedMode = false;
        }
      }

      // Special North American character set.
      // ccData1 - 0|0|0|1|C|0|0|1
      // ccData2 - 0|0|1|1|X|X|X|X
      if (((ccData1 & 0xF7) == 0x11) && ((ccData2 & 0xF0) == 0x30)) {
        if (isBytePairForSelectedChannel(ccData1)) {
          currentCueBuilder.append(getSpecialChar(ccData2));
        }
        continue;
      }

      // Extended Western European character set.
      // ccData1 - 0|0|0|1|C|0|1|S
      // ccData2 - 0|0|1|X|X|X|X|X
      if (((ccData1 & 0xF6) == 0x12) && (ccData2 & 0xE0) == 0x20) {
        if (isBytePairForSelectedChannel(ccData1)) {
          // Remove standard equivalent of the special extended char before appending new one
          currentCueBuilder.backspace();
          if ((ccData1 & 0x01) == 0x00) {
            // Extended Spanish/Miscellaneous and French character set (S = 0).
            currentCueBuilder.append(getExtendedEsFrChar(ccData2));
          } else {
            // Extended Portuguese and German/Danish character set (S = 1).
            currentCueBuilder.append(getExtendedPtDeChar(ccData2));
          }
        }
        continue;
      }

      // Control character.
      // ccData1 - 0|0|0|X|X|X|X|X
      if ((ccData1 & 0xE0) == 0x00) {
        isRepeatableControl = handleCtrl(ccData1, ccData2);
        continue;
      }

      // The processed byte pair does not have a channel bit after this point. We need to rely
      // on the channel information arrived with the last command received
      if (incomingDataTargetChannel != selectedChannel) {
        if (DEBUG) {
          Log.d(TAG,"Dropping characters belonging to other channel of this frame: '" + getChar(ccData1) + "'");
          if ((ccData2 & 0xE0) != 0x00) {
            Log.d(TAG,"Dropping characters belonging to other channel of this frame: '" + getChar(ccData2) + "'");
          }
        }
        continue;
      }

      // Basic North American character set.
      currentCueBuilder.append(getChar(ccData1));
      if ((ccData2 & 0xE0) != 0x00) {
        currentCueBuilder.append(getChar(ccData2));
      }
    }

    // CEA608 has a dedicated bandwidth, so at least invalid or null 608 bytes should be present
    // as the first few bytes of every packet. The absence of them suggest the stream is altered
    // and is not consistent at this point: the Sarnoff tests expect the screen being cleared.
    // (This should be a rare event)
    if (!packetHas608Data) {
      last608BytesDistance += packetSize;
    } else {
      last608BytesDistance = 0;
    }

    // Every video frame was planned to have 2 CEA608 bytes (for 29.97Fps), so the higher frame
    // rate allows bigger distance between CEA608 bytes
    if (last608BytesDistance > MAX_ALLOWED_DISTANCE_BETWEEN_CEA608_BYTES)
    {
      last608BytesDistance = 0;
      Log.w(TAG, "Clearing captions as no CEA608 bytes are present in the stream");
      resetCueBuilders();                            // clear buffers
      cues = new ArrayList<>();                      // clear current screen content
      return;
    }

    if (captionDataProcessed) {
      if (!isRepeatableControl) {
        repeatableControlSet = false;
      }
      if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_PAINT_ON) {

        cues = getDisplayCues();
        if (savedCues != null) {
          for (Cue oneCue : savedCues) {
            cues.add(oneCue);
          }
        }
      }
    }
  }


  private boolean handleCtrl(byte cc1, byte cc2) {
    incomingDataTargetChannel = getChannelBit(cc1);

    boolean isRepeatableControl = isRepeatable(cc1);

    // Most control commands are sent twice in succession to ensure they are received properly.
    // We don't want to process duplicate commands, so if we see the same repeatable command twice
    // in a row, ignore the second one.
    if (isRepeatableControl) {
      if (repeatableControlSet
          && repeatableControlCc1 == cc1
          && repeatableControlCc2 == cc2) {
        // This is a duplicate. Clear the repeatable control flag and return.
        repeatableControlSet = false;
        if (DEBUG) {
          Log.d(TAG, String.format("Dropping repeated Control Code 0x%02X - 0x%02X", cc1, cc2));
        }
        return true;
      } else {
        // This is a repeatable command, but we haven't seen it yet, so set the repeatable control
        // flag (to ensure we ignore the next one should it be a duplicate) and continue processing
        // the command.
        repeatableControlSet = true;
        dataProcessedSinceLastRepeatableControl = false;
        repeatableControlCc1 = cc1;
        repeatableControlCc2 = cc2;
      }
    }

    if (!isBytePairForSelectedChannel(cc1)) {
      return isRepeatableControl;
    }

    if (DEBUG) {
      debugPrintControlCode(cc1, cc2);
    }

    if (isMidrowCtrlCode(cc1, cc2)) {
      handleMidrowCtrl(cc2);
    } else if (isPreambleAddressCode(cc1, cc2)) {
      handlePreambleAddressCode(cc1, cc2);
    } else if (isTabCtrlCode(cc1, cc2)) {
      // Do not apply new TAB value if it arrives mid-row. We do not support re-alignment if the
      // row already has characters.
      if (captionMode != CC_MODE_POP_ON || currentCueBuilder.isEmpty()) {
        currentCueBuilder.setTab(cc2 - 0x20);
      }
    } else if (isMiscCode(cc1, cc2, selectedField)) {
      handleMiscCode(cc2);
    }

    return isRepeatableControl;
  }

  private void debugPrintControlCode(byte cc1, byte cc2) {
    if (isPreambleAddressCode(cc1, cc2)) {
      Log.d(TAG, "PAC: " + getPreambleAddressCodeMeaning(cc1, cc2));
    } else if (isMidrowCtrlCode(cc1, cc2)) {
      Log.d(TAG, "MRC: " + getMidRowCodeMeaning(cc1, cc2));
    } else if (isTabCtrlCode(cc1, cc2)) {
      Log.d(TAG, "TAB: " + (cc2 - 0x20));
    } else if (isMiscCode(cc1, cc2, selectedField)){
      Log.d(TAG,"MISC: " + getMiscControlCodeMeaning(cc2));
    } else {
      Log.d(TAG,String.format("Undefined Control Code 0x%02X - 0x%02X", cc1, cc2));
    }
  }

  private int getPreambleAddressCodePositionColumn(byte cc1, byte cc2) {
    // The bits of the two bytes of the Preamble Are:
    // CC1: 0bP001CRRR CC2: 0b1RSSSSU
    // P is the parity bit for
    // C is the 'Channel selector'
    // R is a bit that is used for 'Row selection'
    // U is the 'Underline Flag'
    // S is a bit used for 'Style selection'

    // we are again interested in the 4 bits marked with S. If the most significant one is set to 1,
    // than the bits mean indentation, color otherwise.
    if ((cc2 & 0x10) == 0) {
      return 1; // the S bits mean a color value.
    }

    // If we are here, the S bits mean indentation.
    // Lets remove the unnecessary bits
    int indentValue = (cc2 & 0xE) >> 1; // Note: we can only have values between 0 and 7 from now on.

    // the required indentation is 4 times of this value represented by the bits.
    // Note: Indentation is between 0 and 28 columns this way, and there can be 0-3 tabs also added
    // by following TAB offset codes.
    return indentValue * 4 + 1;
  }

  private final static short[] DEBUG_ROW_INDICES = { 11, 11, 1, 2, 3, 4, 12, 13, 14, 15, 5, 6, 7, 8, 9, 10 };
  private int getPreambleAddressCodePositionRow(byte cc1, byte cc2) {
    int cc1Bits = (cc1 & 0x7);
    int cc2bit = (cc2 & 0x20) > 0 ? 1 : 0;

    int index = (cc1Bits << 1) + cc2bit;

    return DEBUG_ROW_INDICES[ index ];
  }

  private int getPreambleColorIdx(byte cc2) {
    // The bits of the two bytes of the Preamble Are:
    // CC1: 0bP001CRRR CC2: 0b1RSSSSU
    // P is the parity bit
    // C is the 'Channel selector'
    // R is a bit that is used for 'Row selection'
    // U is the 'Underline Flag'
    // S is a bit used for 'Style selection'

    // so for color selection we are only interested in the 4 bits of cc2 marked with 'S'. If the
    // most significant one is set, than the color is White, and the leftover bits mean indentation.
    if((cc2 & 0x10)> 0) {
      return 0; // the index of white
    }

    // The last bit is irrelevant, that is the "Underline Flag" -> we will divide by 2.
    // Let's mask the 4 bits we are interested in: 0b11110 = 0x1E.
    return (cc2 & 0x1E) / 2;
  }

  private String getPreambleAddressCodeMeaning(byte cc1, byte cc2) {
    return "Row:" + getPreambleAddressCodePositionRow(cc1, cc2)
            + "; Col:" + getPreambleAddressCodePositionColumn(cc1, cc2)
            + "; Color:" + COLOR_NAMES[getPreambleColorIdx(cc2)]
            + "; italic:" + ((cc2 & 0x1E) == 0xE ? "true" : "false")
            + "; underline:" + ((cc2 & 1) == 1);
  }

  private String getMiscControlCodeMeaning(byte cc2) {
    switch (cc2) {
      case CTRL_RESUME_CAPTION_LOADING:     return "RESUME_CAPTION_LOADING";
      case CTRL_ROLL_UP_CAPTIONS_2_ROWS:    return "ROLL_UP_CAPTIONS_2_ROWS";
      case CTRL_ROLL_UP_CAPTIONS_3_ROWS:    return "ROLL_UP_CAPTIONS_3_ROWS";
      case CTRL_ROLL_UP_CAPTIONS_4_ROWS:    return "ROLL_UP_CAPTIONS_4_ROWS";
      case CTRL_RESUME_DIRECT_CAPTIONING:   return "RESUME_DIRECT_CAPTIONING";
      case CTRL_END_OF_CAPTION:             return "END_OF_CAPTION";
      case CTRL_ERASE_DISPLAYED_MEMORY:     return "ERASE_DISPLAYED_MEMORY";
      case CTRL_CARRIAGE_RETURN:            return "CARRIAGE_RETURN";
      case CTRL_ERASE_NON_DISPLAYED_MEMORY: return "ERASE_NON_DISPLAYED_MEMORY";
      case CTRL_BACKSPACE:                  return "BACKSPACE";
    }
    return "UNKNOWN";
  }

  private static final String[] COLOR_NAMES = {
          "WHITE",
          "GREEN",
          "BLUE",
          "CYAN",
          "RED",
          "YELLOW",
          "MAGENTA",
          "KEEP PREVIOUS COLOR" // Used to turn on Italics and UnderLine.
  };

  private String getMidRowCodeMeaning(byte cc1, byte cc2) {
    int colorIdx = (cc2 >> 1) & 0x0F;
    String styleStr = COLOR_NAMES[ colorIdx ];

    boolean isUnderlined = (cc2 & 0x01) == 0x01;
    if (isUnderlined) {
      styleStr += " + UNDERLINE";
    }

    return styleStr;
  }

  private void handleMidrowCtrl(byte cc2) {
    // TODO: support the extended styles (i.e. backgrounds and transparencies)

    // A midrow control code advances the cursor.
    currentCueBuilder.append(' ');

    // cc2 - 0|0|1|0|ATRBT|U
    // ATRBT is the 3-byte encoded attribute, and U is the underline toggle
    boolean isUnderlined = (cc2 & 0x01) == 0x01;
    currentCueBuilder.setUnderline(isUnderlined);

    int attribute = (cc2 >> 1) & 0x0F;
    if (attribute == 0x07) {
      currentCueBuilder.setMidrowStyle(new StyleSpan(Typeface.ITALIC), 2);
      currentCueBuilder.setMidrowStyle(new ForegroundColorSpan(Color.WHITE), 1);
    } else {
      currentCueBuilder.setMidrowStyle(new ForegroundColorSpan(COLORS[attribute]), 1);
    }
  }

  private void handlePreambleAddressCode(byte cc1, byte cc2) {
    // cc1 - 0|0|0|1|C|E|ROW
    // C is the channel toggle, E is the extended flag, and ROW is the encoded row
    int row = ROW_INDICES[cc1 & 0x07];
    // TODO: support the extended address and style

    // cc2 - 0|1|N|ATTRBTE|U
    // N is the next row down toggle, ATTRBTE is the 4-byte encoded attribute, and U is the
    // underline toggle.
    boolean nextRowDown = (cc2 & 0x20) != 0;
    if (nextRowDown) {
      row++;
    }

    if (row != currentCueBuilder.getRow()) {
      if (captionMode != CC_MODE_ROLL_UP && !currentCueBuilder.isEmpty()) {
        currentCueBuilder = new CueBuilder(captionMode, captionRowCount, isWideAspectRatio);
        cueBuilders.add(currentCueBuilder);
      }
      currentCueBuilder.setRow(row);
    } else if (captionMode == CC_MODE_POP_ON && !currentCueBuilder.isEmpty()) {
      // this is an attempt to reposition the cursor in the same row for pop-on mode. This is
      // usually happening after a new style got introduced (mid-row). As arbitrary positioning
      // is not supported, we drop this command to avoid moving and re-styling the characters
      // already existing in this row.
      Log.w(TAG,"Dropping PAC repositioning cursor in the same row");
      return;
    }

    if ((cc2 & 0x01) == 0x01) {
      currentCueBuilder.setPreambleStyle(new UnderlineSpan());
    }

    // cc2 - 0|1|N|0|STYLE|U
    // cc2 - 0|1|N|1|CURSR|U
    int attribute = cc2 >> 1 & 0x0F;
    if (attribute <= 0x07) {
      if (attribute == 0x07) {
        currentCueBuilder.setPreambleStyle(new StyleSpan(Typeface.ITALIC));
        currentCueBuilder.setPreambleStyle(new ForegroundColorSpan(Color.WHITE));
      } else {
        currentCueBuilder.setPreambleStyle(new ForegroundColorSpan(COLORS[attribute]));
      }
    } else {
      currentCueBuilder.setIndent(COLUMN_INDICES[attribute & 0x07]);
    }
  }

  private void handleMiscCode(byte cc2) {
    switch (cc2) {
      case CTRL_ROLL_UP_CAPTIONS_2_ROWS:
        setCaptionMode(CC_MODE_ROLL_UP);
        setCaptionRowCount(2);
        return;
      case CTRL_ROLL_UP_CAPTIONS_3_ROWS:
        setCaptionMode(CC_MODE_ROLL_UP);
        setCaptionRowCount(3);
        return;
      case CTRL_ROLL_UP_CAPTIONS_4_ROWS:
        setCaptionMode(CC_MODE_ROLL_UP);
        setCaptionRowCount(4);
        return;
      case CTRL_RESUME_CAPTION_LOADING:
        setCaptionMode(CC_MODE_POP_ON);
        return;
      case CTRL_RESUME_DIRECT_CAPTIONING:
        setCaptionMode(CC_MODE_PAINT_ON);
        return;
      default:
        // Fall through.
        break;
    }

    if (captionMode == CC_MODE_UNKNOWN) {
      return;
    }

    switch (cc2) {
      case CTRL_ERASE_DISPLAYED_MEMORY:
        cues = null;
        savedCues = null;
        if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_PAINT_ON) {
          resetCueBuilders();
        }
        break;
      case CTRL_ERASE_NON_DISPLAYED_MEMORY:
        resetCueBuilders();
        break;
      case CTRL_END_OF_CAPTION:
        cues = getDisplayCues();
        resetCueBuilders();
        break;
      case CTRL_CARRIAGE_RETURN:
        // carriage returns only apply to rollup captions; don't bother if we don't have anything
        // to add a carriage return to
        if (captionMode == CC_MODE_ROLL_UP && !currentCueBuilder.isEmpty()) {
          currentCueBuilder.rollUp();
        }
        break;
      case CTRL_BACKSPACE:
        currentCueBuilder.backspace();
        break;
      case CTRL_DELETE_TO_END_OF_ROW:
        // TODO: implement
        break;
      default:
        // Fall through.
        break;
    }
  }

  private List<Cue> getDisplayCues() {
    List<Cue> displayCues = new ArrayList<>();

    // Pre-calculate the alignment as except in very rare scenarios all lines should be aligned
    // the same way. CEA608 only had LEFT alignment, CENTER and RIGHT alignment are artificially
    // introduced to get more visually appealing result. The improvement is lost as soon as we
    // align consecutive lines differently. This algorithm picks the left-most to match the others
    // as a CENTER + RIGHT should be probably CENTER, while a LEFT + CENTER should be probably
    // LEFT aligned. Note: other picks should be more or less similarly correct, but LEFT is
    // dominant as that was the only existing option in the original CEA608 standard.
    int leftMostAnchor = Cue.ANCHOR_TYPE_END;
    int cueCount = cueBuilders.size();
    for (int i = 0; i < cueCount; i++) {
      leftMostAnchor = Math.min(cueBuilders.get(i).calculatePreferredAlignment(), leftMostAnchor);
    }

    for (int i = 0; i < cueCount; i++) {
      Cue cue = cueBuilders.get(i).build(leftMostAnchor);
      if (cue != null) {
        displayCues.add(cue);
      }
    }
    return displayCues;
  }

  private void setCaptionMode(int captionMode) {
    if (this.captionMode == captionMode) {
      return;
    }

    int oldCaptionMode = this.captionMode;
    this.captionMode = captionMode;

    // Clear the working memory except for Paint-on mode. Paint-on mode may modify characters
    // irrespective of the previous mode so we need to save currently displayed cues
    if (captionMode == CC_MODE_PAINT_ON) {
      savedCues = cues;
      // update the Mode member of the cueBuilders even if we are mid-row
      for (CueBuilder builder : cueBuilders) {
        builder.setCaptionMode(captionMode);
      }
    } else {
      savedCues = null;
      resetCueBuilders();
    }

    if (oldCaptionMode == CC_MODE_PAINT_ON || captionMode == CC_MODE_ROLL_UP
        || captionMode == CC_MODE_UNKNOWN) {
      // When switching from paint-on or to roll-up or unknown, we also need to clear the caption.
      cues = null;
    }
  }

  private void setCaptionRowCount(int captionRowCount) {
    this.captionRowCount = captionRowCount;
    currentCueBuilder.setCaptionRowCount(captionRowCount);
  }

  private void resetCueBuilders() {
    currentCueBuilder.reset(captionMode);
    cueBuilders.clear();
    cueBuilders.add(currentCueBuilder);
  }

  private static char getChar(byte ccData) {
    int index = (ccData & 0x7F) - 0x20;
    return (char) BASIC_CHARACTER_SET[index];
  }

  private static char getSpecialChar(byte ccData) {
    int index = ccData & 0x0F;
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

  private static boolean isMidrowCtrlCode(byte cc1, byte cc2) {
    // cc1 - 0|0|0|1|C|0|0|1
    // cc2 - 0|0|1|0|X|X|X|X
    return ((cc1 & 0xF7) == 0x11) && ((cc2 & 0xF0) == 0x20);
  }

  private static boolean isPreambleAddressCode(byte cc1, byte cc2) {
    // cc1 - 0|0|0|1|C|X|X|X
    // cc2 - 0|1|X|X|X|X|X|X
    return ((cc1 & 0xF0) == 0x10) && ((cc2 & 0xC0) == 0x40);
  }

  private static boolean isTabCtrlCode(byte cc1, byte cc2) {
    // cc1 - 0|0|0|1|C|1|1|1
    // cc2 - 0|0|1|0|0|0|0|1 to 0|0|1|0|0|0|1|1
    return ((cc1 & 0xF7) == 0x17) && (cc2 >= 0x21 && cc2 <= 0x23);
  }

  private static boolean isMiscCode(byte cc1, byte cc2, int selectedField) {
    // cc1 - 0|0|0|1|C|1|0|F
    // cc2 - 0|0|1|0|X|X|X|X
    // Legends: F=field; C=channel
    return ((cc1 & 0xF6) == 0x14)
            && ((cc1 & 0x1) == selectedField)
            && ((cc2 & 0xF0) == 0x20);
  }

  private static boolean isRepeatable(byte cc1) {
    // cc1 - 0|0|0|1|X|X|X|X
    return (cc1 & 0xF0) == 0x10;
  }

  private static class CueBuilder {

    private static final int POSITION_UNSET = -1;

    // 608 captions define a 15 row by 32 column screen grid. These constants convert from 608
    // positions to normalized screen position.
    private static final int SCREEN_CHARWIDTH = 32;
    private static final int BASE_ROW = 15;

    private final List<CharacterStyle> preambleStyles;
    private final List<CueStyle> midrowStyles;
    private final List<SpannableString> rolledUpCaptions;
    private final SpannableStringBuilder captionStringBuilder;

    private int row;
    private int indent;
    private int tabOffset;
    private int captionMode;
    private int captionRowCount;
    private int underlineStartPosition;

    // The next 3 variables are only introduced to share values between 2 part of a previously
    // single function.
    private SpannableStringBuilder finalCueString;
    private int startPadding;
    private int endPadding;
    private boolean isWideAspectRatio;

    public CueBuilder(int captionMode, int captionRowCount, boolean isWideAspectRatio) {
      preambleStyles = new ArrayList<>();
      midrowStyles = new ArrayList<>();
      rolledUpCaptions = new ArrayList<>();
      captionStringBuilder = new SpannableStringBuilder();
      this.isWideAspectRatio = isWideAspectRatio;
      reset(captionMode);
      setCaptionRowCount(captionRowCount);
    }

    public void setCaptionMode(int captionMode) {
      this.captionMode = captionMode;
    }

    public void reset(int captionMode) {
      this.captionMode = captionMode;
      preambleStyles.clear();
      midrowStyles.clear();
      rolledUpCaptions.clear();
      captionStringBuilder.clear();
      row = BASE_ROW;
      indent = 0;
      tabOffset = 0;
      underlineStartPosition = POSITION_UNSET;
    }

    public void setCaptionRowCount(int captionRowCount) {
      this.captionRowCount = captionRowCount;
    }

    public boolean isEmpty() {
      return preambleStyles.isEmpty() && midrowStyles.isEmpty() && rolledUpCaptions.isEmpty()
          && captionStringBuilder.length() == 0;
    }

    public void backspace() {
      int length = captionStringBuilder.length();
      if (length > 0) {
        captionStringBuilder.delete(length - 1, length);
      }
    }

    public int getRow() {
      return row;
    }

    public void setRow(int row) {
      this.row = row;
    }

    public void rollUp() {
      rolledUpCaptions.add(buildSpannableString());
      captionStringBuilder.clear();
      preambleStyles.clear();
      midrowStyles.clear();
      underlineStartPosition = POSITION_UNSET;

      int numRows = Math.min(captionRowCount, row);
      while (rolledUpCaptions.size() >= numRows) {
        rolledUpCaptions.remove(0);
      }
    }

    public void setIndent(int indent) {
      this.indent = indent;
    }

    public void setTab(int tabs) {
      tabOffset = tabs;
    }

    public void setPreambleStyle(CharacterStyle style) {
      preambleStyles.add(style);
    }

    public void setMidrowStyle(CharacterStyle style, int nextStyleIncrement) {
      midrowStyles.add(new CueStyle(style, captionStringBuilder.length(), nextStyleIncrement));
    }

    public void setUnderline(boolean enabled) {
      if (enabled) {
        underlineStartPosition = captionStringBuilder.length();
      } else if (underlineStartPosition != POSITION_UNSET) {
        // underline spans won't overlap, so it's safe to modify the builder directly with them
        captionStringBuilder.setSpan(new UnderlineSpan(), underlineStartPosition,
            captionStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        underlineStartPosition = POSITION_UNSET;
      }
    }

    public void append(char text) {
      if (DEBUG) {
        Log.d(TAG, "Incoming char: '" + text + "'");
      }
      captionStringBuilder.append(text);
    }

    public SpannableString buildSpannableString() {
      int length = captionStringBuilder.length();

      // preamble styles apply to the entire cue
      for (int i = 0; i < preambleStyles.size(); i++) {
        captionStringBuilder.setSpan(preambleStyles.get(i), 0, length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }

      // midrow styles only apply to part of the cue, and after preamble styles
      for (int i = 0; i < midrowStyles.size(); i++) {
        CueStyle cueStyle = midrowStyles.get(i);
        int end = (i < midrowStyles.size() - cueStyle.nextStyleIncrement)
            ? midrowStyles.get(i + cueStyle.nextStyleIncrement).start
            : length;
        captionStringBuilder.setSpan(cueStyle.style, cueStyle.start, end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }

      // special case for midrow underlines that went to the end of the cue
      if (underlineStartPosition != POSITION_UNSET) {
        captionStringBuilder.setSpan(new UnderlineSpan(), underlineStartPosition, length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }

      return new SpannableString(captionStringBuilder);
    }

    public int calculatePreferredAlignment() {
      finalCueString = new SpannableStringBuilder();

      // Add any rolled up captions, separated by new lines.
      for (int i = 0; i < rolledUpCaptions.size(); i++) {
        finalCueString.append(rolledUpCaptions.get(i));
        finalCueString.append('\n');
      }
      // Add the current line.
      finalCueString.append(buildSpannableString());

      if (finalCueString.length() == 0) {
        // The cue is empty, does not influence alignment
        return Cue.ANCHOR_TYPE_END;
      }

      // The number of empty columns before the start of the text, in the range [0-31].
      startPadding = indent + tabOffset;
      // The number of empty columns after the end of the text, in the same range.
      endPadding = SCREEN_CHARWIDTH - startPadding - finalCueString.length();
      int startEndPaddingDelta = startPadding - endPadding;

      if (captionMode == CC_MODE_POP_ON && (Math.abs(startEndPaddingDelta) < 3 || endPadding < 0)) {
        return Cue.ANCHOR_TYPE_MIDDLE;
      } else if (captionMode == CC_MODE_POP_ON && startEndPaddingDelta > 0 && endPadding < 4) {
        return Cue.ANCHOR_TYPE_END;
      }
      return Cue.ANCHOR_TYPE_START;
    }


    public Cue build(int preferredAnchor) {

      // Making sure, no-one calls build() without having the alignment function called first
      if (finalCueString == null) {
        calculatePreferredAlignment();
      }

      if (finalCueString.length() == 0) {
        // The cue is empty.
        return null;
      }

      float position;
      int positionAnchor;

      if (preferredAnchor == Cue.ANCHOR_TYPE_MIDDLE) {
        // Treat approximately centered pop-on captions as middle aligned. We also treat captions
        // that are wider than they should be in this way. See
        // https://github.com/google/ExoPlayer/issues/3534
        position = 0.5f;
        positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
      } else if (preferredAnchor == Cue.ANCHOR_TYPE_END) {
        // Treat pop-on captions with less padding at the end than the start as end aligned.
        position = (float) (SCREEN_CHARWIDTH - endPadding) / SCREEN_CHARWIDTH;
        // Adjust the position to fit within the safe area.
        position = position * 0.8f + 0.1f;
        positionAnchor = Cue.ANCHOR_TYPE_END;
      } else {
        // For all other cases assume start aligned.
        position = (float) startPadding / SCREEN_CHARWIDTH;
        // Adjust the position to fit within the safe area.
        position = position * 0.8f + 0.1f;
        positionAnchor = Cue.ANCHOR_TYPE_START;
      }

      int lineAnchor;
      int line;
      // Note: Row indices are in the range [1-15].
      if (captionMode == CC_MODE_ROLL_UP || row > (BASE_ROW / 2)) {
        lineAnchor = Cue.ANCHOR_TYPE_END;
        line = row - BASE_ROW;
        // Indices from the bottom start from -1 rather than 0.
        line -= 2;
      } else {
        lineAnchor = Cue.ANCHOR_TYPE_START;
        // CEA608 uses 1 based indices while the SubtitlePainter uses 0 based ones:
        line = row;
      }
      Cue result = new Cue(finalCueString, Alignment.ALIGN_NORMAL, line, Cue.LINE_TYPE_NUMBER, lineAnchor,
              position, positionAnchor, Cue.DIMEN_UNSET);
      finalCueString = null;
      return result;
    }

    @Override
    public String toString() {
      return captionStringBuilder.toString();
    }

    private static class CueStyle {

      public final CharacterStyle style;
      public final int start;
      public final int nextStyleIncrement;

      public CueStyle(CharacterStyle style, int start, int nextStyleIncrement) {
        this.style = style;
        this.start = start;
        this.nextStyleIncrement = nextStyleIncrement;
      }

    }

  }

}
