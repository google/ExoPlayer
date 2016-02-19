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

import android.graphics.Color;

/* package */ final class ClosedCaptionCtrl extends ClosedCaption {

  /**
   * The receipt of the {@link #RESUME_CAPTION_LOADING} command initiates pop-on style captioning.
   * Subsequent data should be loaded into a non-displayed memory and held there until the
   * {@link #END_OF_CAPTION} command is received, at which point the non-displayed memory becomes
   * the displayed memory (and vice versa).
   */
  public static final byte RESUME_CAPTION_LOADING = 0x20;
  /**
   * The receipt of the {@link #ROLL_UP_CAPTIONS_2_ROWS} command initiates roll-up style
   * captioning, with the maximum of 2 rows displayed simultaneously.
   */
  public static final byte ROLL_UP_CAPTIONS_2_ROWS = 0x25;
  /**
   * The receipt of the {@link #ROLL_UP_CAPTIONS_3_ROWS} command initiates roll-up style
   * captioning, with the maximum of 3 rows displayed simultaneously.
   */
  public static final byte ROLL_UP_CAPTIONS_3_ROWS = 0x26;
  /**
   * The receipt of the {@link #ROLL_UP_CAPTIONS_4_ROWS} command initiates roll-up style
   * captioning, with the maximum of 4 rows displayed simultaneously.
   */
  public static final byte ROLL_UP_CAPTIONS_4_ROWS = 0x27;
  /**
   * The receipt of the {@link #RESUME_DIRECT_CAPTIONING} command initiates paint-on style
   * captioning. Subsequent data should be addressed immediately to displayed memory without need
   * for the {@link #RESUME_CAPTION_LOADING} command.
   */
  public static final byte RESUME_DIRECT_CAPTIONING = 0x29;
  /**
   * The receipt of the {@link #END_OF_CAPTION} command indicates the end of pop-on style caption,
   * at this point already loaded in non-displayed memory caption should become the displayed
   * memory (and vice versa). If no {@link #RESUME_CAPTION_LOADING} command has been received,
   * {@link #END_OF_CAPTION} command forces the receiver into pop-on style.
   */
  public static final byte END_OF_CAPTION = 0x2F;

  public static final byte ERASE_DISPLAYED_MEMORY = 0x2C;
  public static final byte CARRIAGE_RETURN = 0x2D;
  public static final byte ERASE_NON_DISPLAYED_MEMORY = 0x2E;

  public static final byte BACKSPACE = 0x21;

  public static final byte MID_ROW_CHAN_1 = 0x11;
  public static final byte MID_ROW_CHAN_2 = 0x19;

  public static final byte MISC_CHAN_1 = 0x14;
  public static final byte MISC_CHAN_2 = 0x1C;

  public static final byte TAB_OFFSET_CHAN_1 = 0x17;
  public static final byte TAB_OFFSET_CHAN_2 = 0x1F;

  public static final int COLUMN_INDEX_BASE = 1; // standard uses 1 based index for positioning

  private static final int[] COLOR_VALUES = {
          Color.WHITE,
          Color.GREEN,
          Color.BLUE,
          Color.CYAN,
          Color.RED,
          Color.YELLOW,
          Color.MAGENTA,
          Color.TRANSPARENT // Last color setting should be kept
  };

  // for debug purposes
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

  // The final position of the text should be one of the predefined ROWs of the display.
  // The target row is selected by 3 bits of CC1 and 1 bit of CC2. So let's have an array of 16
  // indices that can be addressed by the incoming 4 bits.
  // There is one duplication, row 11 is the default, where the bit in CC2 is not used for row
  // selection, so the first 2 value will be 11: if the CC1 has the value of 0x10 or 0x18 than the
  // target row is '11' irrespective of CC2.
  final static short[] ROW_INDICES = { 11, 11, 1, 2, 3, 4, 12, 13, 14, 15, 5, 6, 7, 8, 9, 10 };

  // 608 parser drops the first bits of the incoming data (parity bit), so both bytes has the max
  // value of 0x7F.
  // For Closed Captioning, cc1 values in the range 0 - 0xF are not defined, they are used as XDS
  // control values (independent from closed caption control codes), so probably the parser should
  // drop such values: the following functions do not really expect cc1 values
  // outside the 0x10 - 0x7F range.
  public final byte cc1;
  public final byte cc2;

  protected ClosedCaptionCtrl(byte cc1, byte cc2) {
    super(ClosedCaption.TYPE_CTRL);
    this.cc1 = cc1;
    this.cc2 = cc2;
  }

  public boolean isMidRowCode() {
    return (cc1 == MID_ROW_CHAN_1 || cc1 == MID_ROW_CHAN_2) && (cc2 >= 0x20 && cc2 <= 0x2F);
  }

  public boolean isMiscCode() {
    return (cc1 == MISC_CHAN_1 || cc1 == MISC_CHAN_2) && (cc2 >= 0x20 && cc2 <= 0x2F);
  }

  public boolean isTabOffsetCode() {
    return (cc1 == TAB_OFFSET_CHAN_1 || cc1 == TAB_OFFSET_CHAN_2) && (cc2 >= 0x21 && cc2 <= 0x23);
  }

  public boolean isPreambleAddressCode() {
    // Note: Current implementation might throw compile warnings as cc2 cannot be bigger than
    // 0x7F when we call this function.
    // Preamble Code could also be checked as
    // cc1 & 0x70 == 0x10 // bits required: 0bx001xxxx
    // and
    // cc2 & 0x40 == 0x40 // bits required: 0bx1xxxxxx
    return (cc1 >= 0x10 && cc1 <= 0x1F) && (cc2 >= 0x40 && cc2 <= 0x7F);
  }

  public boolean isRepeatable() {
    return cc1 >= 0x10 && cc1 <= 0x1F;
  }

  // for debug purposes only
  public String getMidRowCodeMeaning() {
    String styleStr = COLOR_NAMES[ getMidRowControlColorIdx() ];

    if (isUnderline()) {
      styleStr += " + UNDERLINE";
    }

    return styleStr;
  }

  // returns the index that can be used to address the predefined arrays for selecting the color
  // based on the CC2 byte. Only call this function, if you are sure, that this is a mid row
  // command.
  private int getMidRowControlColorIdx() {
    return (cc2 - 0x20) / 2; // first color is 0x20, than every second value is a new color
  }

  // note: TRANSPARENT should be handled carefully! That is used for Italics and underline changes
  int getMidRowColorValue() {
    return COLOR_VALUES[ getMidRowControlColorIdx() ];
  }

  // returns 0 based index (while the standard mentions channel 1 and 2)!
  public int getPreambleAddressCodeChannel() {
    // the first byte is between 0x10 - 0x17 for channel 1 and
    // between 0x18 - 0x1F for channel 2.
    return (cc1 > 0x17) ? 1 : 0;
  }

  // returns a 1 based index!
  public int getPreambleAddressCodePositionRow() {
    int cc1Bits = (cc1 & 0x7);
    int cc2bit = (cc2 & 0x20) > 0 ? 1 : 0;

    int index = (cc1Bits << 1) + cc2bit;

    return ROW_INDICES[ index ];
  }

  // for debug purposes
  public String getPreambleAddressCodeMeaning() {
    return "Row:"+getPreambleAddressCodePositionRow()
            + "; Col:" + getPreambleAddressCodePositionColumn()
            + "; Color:" + getPreambleColorName()
            + "; italic:" + isPreambleItalic()
            + "; underline:" + isUnderline();
  }

  // color values are defined for CC2 between 0x40 and 0x4F only. Higher values only set indentation
  private int getPreambleColorIdx() {
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

  public int getPreambleColor() {
    return COLOR_VALUES[getPreambleColorIdx()];
  }

  // for debug purposes
  public String getPreambleColorName() {
    return COLOR_NAMES[getPreambleColorIdx()];
  }

  public  boolean isPreambleItalic() {
    // The last bit ("Underline Flag") is irrelevant
    // The 6th least significant bit is the part of the Row selection, so only the bits 2-5 count.
    // For italic we need the least significant bits to be exactly 0b0111x so the entire byte is
    // 0bxxR0111U, where x is "don't care", R is the 'Row Selector', U is the 'Underline Flag'
    return (cc2 & 0x1E) == 0xE;
  }

  // Indentation is the column address of the Caption on the final display. The useful area of the
  // screen should be divided into 32 equal columns. The useful area is the center 80% of the
  // screen (10-10% should be left empty on both left and right side). Column 0 and 33 can be
  // used to display a "solid space to improve legibility", but cannot contain displayable
  // characters.
  // A single column can be further divided into 4 equal parts. Tabs can be used (0-3) for
  // positioning inside a column.
  // Fun fact: the standard says that for screens with aspect ration of 16x9, the screen should be
  // divided into 42 equal columns instead of 32. So the text positioning should be depending on
  // the aspect ratio of the video content. What if the content is letterboxed? Than aspect ration
  // checks will return incorrect results. I do not introduce this dependency at this time.
  // See Federal Communications Commission §79.102
  public int getPreambleAddressCodePositionColumn() {
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
      return COLUMN_INDEX_BASE; // the S bits mean a color value.
    }

    // If we are here, the S bits mean indentation.
    // Lets remove the unnecessary bits
    int indentValue = (cc2 & 0xE) >> 1; // Note: we can only have values between 0 and 7 from now on.

    // the required indentation is 4 times of this value represented by the bits.
    // Note: Indentation is between 0 and 28 columns this way, and there can be 0-3 tabs also added
    // by following TAB offset codes.
    return indentValue * 4 + COLUMN_INDEX_BASE;
  }

  // last bit of the styling commands (Preamble Address Code or Mid Row CODE) is "Underline Flag".
  public boolean isUnderline() {
    return (cc2 & 1) == 1;
  }

  // for debug purposes
  public String getMiscControlCodeMeaning() {
    switch ( cc2 ) {
      case RESUME_CAPTION_LOADING:     return "RESUME_CAPTION_LOADING";
      case ROLL_UP_CAPTIONS_2_ROWS:    return "ROLL_UP_CAPTIONS_2_ROWS";
      case ROLL_UP_CAPTIONS_3_ROWS:    return "ROLL_UP_CAPTIONS_3_ROWS";
      case ROLL_UP_CAPTIONS_4_ROWS:    return "ROLL_UP_CAPTIONS_4_ROWS";
      case RESUME_DIRECT_CAPTIONING:   return "RESUME_DIRECT_CAPTIONING";
      case END_OF_CAPTION:             return "END_OF_CAPTION";
      case ERASE_DISPLAYED_MEMORY:     return "ERASE_DISPLAYED_MEMORY";
      case CARRIAGE_RETURN:            return "CARRIAGE_RETURN";
      case ERASE_NON_DISPLAYED_MEMORY: return "ERASE_NON_DISPLAYED_MEMORY";
      case BACKSPACE:                  return "BACKSPACE";
    }
    return "UNKNOWN";
  }

  // for debug purposes, useful as the IDE shows this string value of the commands while debugging
  @Override
  public String toString() {

    if (isPreambleAddressCode()) {
      return "PAC: " + getPreambleAddressCodeMeaning();
    } else if (isMidRowCode()) {
      return "MRC: " + getMidRowCodeMeaning();
    } else if (isTabOffsetCode()) {
      return "TAB: " + (cc2 - 0x20) ;
    } else if (isMiscCode()){
      return "MISC: " + getMiscControlCodeMeaning();
    }

    return "UNKNOWN - CC1:" + Integer.toHexString(cc1) + "; CC2:" + Integer.toHexString(cc2);
  }
}
