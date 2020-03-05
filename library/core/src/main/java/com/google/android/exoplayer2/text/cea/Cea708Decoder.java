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
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Cue.AnchorType;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoder;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.CircularByteQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * A {@link SubtitleDecoder} for CEA-708 (also known as "EIA-708").
 */
public final class Cea708Decoder extends CeaDecoder {

  private static final String TAG = "Cea708Decoder";

  private static final int NUM_WINDOWS = 8;

  private static final int DTVCC_PACKET_DATA = 0x02;
  private static final int DTVCC_PACKET_START = 0x03;
  private static final int CC_VALID_FLAG = 0x04;

  // Base Commands
  private static final int GROUP_C0_END = 0x1F;  // Miscellaneous Control Codes
  private static final int GROUP_G0_END = 0x7F;  // ASCII Printable Characters
  private static final int GROUP_C1_END = 0x9F;  // Captioning Command Control Codes
  private static final int GROUP_G1_END = 0xFF;  // ISO 8859-1 LATIN-1 Character Set

  // Extended Commands
  private static final int GROUP_C2_END = 0x1F;  // Extended Control Code Set 1
  private static final int GROUP_G2_END = 0x7F;  // Extended Miscellaneous Characters
  private static final int GROUP_C3_END = 0x9F;  // Extended Control Code Set 2
  private static final int GROUP_G3_END = 0xFF;  // Future Expansion

  // Group C0 Commands
  private static final int COMMAND_NUL = 0x00;        // Nul
  private static final int COMMAND_ETX = 0x03;        // EndOfText
  private static final int COMMAND_BS = 0x08;         // Backspace
  private static final int COMMAND_FF = 0x0C;         // FormFeed (Flush)
  private static final int COMMAND_CR = 0x0D;         // CarriageReturn
  private static final int COMMAND_HCR = 0x0E;        // ClearLine
  private static final int COMMAND_EXT1 = 0x10;       // Extended Control Code Flag
  private static final int COMMAND_EXT1_START = 0x11;
  private static final int COMMAND_EXT1_END = 0x17;
  private static final int COMMAND_P16_START = 0x18;
  private static final int COMMAND_P16_END = 0x1F;

  // Group C1 Commands
  private static final int COMMAND_CW0 = 0x80;  // SetCurrentWindow to 0
  private static final int COMMAND_CW1 = 0x81;  // SetCurrentWindow to 1
  private static final int COMMAND_CW2 = 0x82;  // SetCurrentWindow to 2
  private static final int COMMAND_CW3 = 0x83;  // SetCurrentWindow to 3
  private static final int COMMAND_CW4 = 0x84;  // SetCurrentWindow to 4
  private static final int COMMAND_CW5 = 0x85;  // SetCurrentWindow to 5
  private static final int COMMAND_CW6 = 0x86;  // SetCurrentWindow to 6
  private static final int COMMAND_CW7 = 0x87;  // SetCurrentWindow to 7
  private static final int COMMAND_CLW = 0x88;  // ClearWindows (+1 byte)
  private static final int COMMAND_DSW = 0x89;  // DisplayWindows (+1 byte)
  private static final int COMMAND_HDW = 0x8A;  // HideWindows (+1 byte)
  private static final int COMMAND_TGW = 0x8B;  // ToggleWindows (+1 byte)
  private static final int COMMAND_DLW = 0x8C;  // DeleteWindows (+1 byte)
  private static final int COMMAND_DLY = 0x8D;  // Delay (+1 byte)
  private static final int COMMAND_DLC = 0x8E;  // DelayCancel
  private static final int COMMAND_RST = 0x8F;  // Reset
  private static final int COMMAND_SPA = 0x90;  // SetPenAttributes (+2 bytes)
  private static final int COMMAND_SPC = 0x91;  // SetPenColor (+3 bytes)
  private static final int COMMAND_SPL = 0x92;  // SetPenLocation (+2 bytes)
  private static final int COMMAND_SWA = 0x97;  // SetWindowAttributes (+4 bytes)
  private static final int COMMAND_DF0 = 0x98;  // DefineWindow 0 (+6 bytes)
  private static final int COMMAND_DF1 = 0x99;  // DefineWindow 1 (+6 bytes)
  private static final int COMMAND_DF2 = 0x9A;  // DefineWindow 2 (+6 bytes)
  private static final int COMMAND_DF3 = 0x9B;  // DefineWindow 3 (+6 bytes)
  private static final int COMMAND_DS4 = 0x9C;  // DefineWindow 4 (+6 bytes)
  private static final int COMMAND_DF5 = 0x9D;  // DefineWindow 5 (+6 bytes)
  private static final int COMMAND_DF6 = 0x9E;  // DefineWindow 6 (+6 bytes)
  private static final int COMMAND_DF7 = 0x9F;  // DefineWindow 7 (+6 bytes)

  // G0 Table Special Chars
  private static final int CHARACTER_MN = 0x7F;  // MusicNote

  // G2 Table Special Chars
  private static final int CHARACTER_TSP = 0x20;
  private static final int CHARACTER_NBTSP = 0x21;
  private static final int CHARACTER_ELLIPSIS = 0x25;
  private static final int CHARACTER_BIG_CARONS = 0x2A;
  private static final int CHARACTER_BIG_OE = 0x2C;
  private static final int CHARACTER_SOLID_BLOCK = 0x30;
  private static final int CHARACTER_OPEN_SINGLE_QUOTE = 0x31;
  private static final int CHARACTER_CLOSE_SINGLE_QUOTE = 0x32;
  private static final int CHARACTER_OPEN_DOUBLE_QUOTE = 0x33;
  private static final int CHARACTER_CLOSE_DOUBLE_QUOTE = 0x34;
  private static final int CHARACTER_BOLD_BULLET = 0x35;
  private static final int CHARACTER_TM = 0x39;
  private static final int CHARACTER_SMALL_CARONS = 0x3A;
  private static final int CHARACTER_SMALL_OE = 0x3C;
  private static final int CHARACTER_SM = 0x3D;
  private static final int CHARACTER_DIAERESIS_Y = 0x3F;
  private static final int CHARACTER_ONE_EIGHTH = 0x76;
  private static final int CHARACTER_THREE_EIGHTHS = 0x77;
  private static final int CHARACTER_FIVE_EIGHTHS = 0x78;
  private static final int CHARACTER_SEVEN_EIGHTHS = 0x79;
  private static final int CHARACTER_VERTICAL_BORDER = 0x7A;
  private static final int CHARACTER_UPPER_RIGHT_BORDER = 0x7B;
  private static final int CHARACTER_LOWER_LEFT_BORDER = 0x7C;
  private static final int CHARACTER_HORIZONTAL_BORDER = 0x7D;
  private static final int CHARACTER_LOWER_RIGHT_BORDER = 0x7E;
  private static final int CHARACTER_UPPER_LEFT_BORDER = 0x7F;

  private final ParsableByteArray ccData;

  private final int selectedServiceNumber;
  private final CueBuilder[] cueBuilders;

  private CueBuilder currentCueBuilder;
  private List<Cue> cues;
  private List<Cue> lastCues;


  private int lastSequenceNo = -1;
  private int currentWindow;

  private long delayUs; // delayed processing due to DLC command
  private long startOfDelayUs; // to keep track of the delay timeout
  private static final int SERVICE_INPUT_BUFF_SIZE = 128;
  private long inputTimestampUs;// currently processing input buffer timestamp

  // Caption Channel Packet processing related
  private int ccpDataIndex;
  private int ccpSize;

  // service block processing related
  private static final int SERVICE_BLOCK_HEADER = 0;
  private static final int SERVICE_BLOCK_EXT_HEADER = 1;
  private static final int SERVICE_BLOCK_DATA = 2;
  private int serviceBlockProcessingState = SERVICE_BLOCK_HEADER;
  private int serviceBlockSize;
  private int serviceNumber;
  private int serviceBlockDataBufferOffset = 0;

  // service input buffer queue
  CircularByteQueue serviceInputBufferQ = new CircularByteQueue(SERVICE_INPUT_BUFF_SIZE);

  // Closed Caption data related
  private static final int CC_DATA_STATE_COMMAND = 0;
  private static final int CC_DATA_STATE_WAITING_FOR_PARAM = 1;
  private static final int CC_DATA_STATE_SKIPPING = 2;
  private int ccDataState = CC_DATA_STATE_COMMAND;
  private int ccDataSkipCount = 0;
  private int currentCommand = -1;
  private boolean isExtendedCommand = false;
  private boolean cuesNeedUpdate;
  private static final boolean DEBUG = false;

  // TODO: Retrieve isWideAspectRatio from initializationData and use it.
  public Cea708Decoder(int accessibilityChannel, @Nullable List<byte[]> initializationData) {
    ccData = new ParsableByteArray();
    selectedServiceNumber = (accessibilityChannel == Format.NO_VALUE) ? 1 : accessibilityChannel;
    cueBuilders = new CueBuilder[NUM_WINDOWS];
    for (int i = 0; i < NUM_WINDOWS; i++) {
      cueBuilders[i] = new CueBuilder();
    }

    currentCueBuilder = cueBuilders[0];
    resetCueBuilders();
  }

  @Override
  public String getName() {
    return "Cea708Decoder";
  }

  @Override
  public void flush() {
    super.flush();
    cues = null;
    lastCues = null;
    currentWindow = 0;
    currentCueBuilder = cueBuilders[currentWindow];
    resetCueBuilders();
    finishCCPacket();
    resetCCDataState();
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

  @Override
  protected void decode(SubtitleInputBuffer inputBuffer) {
    inputTimestampUs = inputBuffer.timeUs;
    ccData.reset(inputBuffer.data.array(), inputBuffer.data.limit());
    while (ccData.bytesLeft() >= 3) {
      int ccTypeAndValid = (ccData.readUnsignedByte() & 0x07);

      int ccType = ccTypeAndValid & (DTVCC_PACKET_DATA | DTVCC_PACKET_START);
      boolean ccValid = (ccTypeAndValid & CC_VALID_FLAG) == CC_VALID_FLAG;
      byte ccData1 = (byte) ccData.readUnsignedByte();
      byte ccData2 = (byte) ccData.readUnsignedByte();

      // Ignore any non-CEA-708 data
      if (ccType != DTVCC_PACKET_DATA && ccType != DTVCC_PACKET_START) {
        continue;
      }

      if (!ccValid) {
        // This byte-pair isn't valid, ignore it and continue.
        continue;
      }

      if (ccType == DTVCC_PACKET_START) {
        finishCCPacket();

        int sequenceNumber = (ccData1 & 0xC0) >> 6; // first 2 bits
        if (lastSequenceNo != -1 && sequenceNumber != (lastSequenceNo + 1) % 4) {
          resetCueBuilders();
          Log.w(TAG, "discontinuity in sequence number detected : lastSequenceNo = " + lastSequenceNo
            + " sequenceNumber = " + sequenceNumber);
        }
        lastSequenceNo = sequenceNumber;

        ccpSize = ccData1 & 0x3F; // last 6 bits
        if (ccpSize == 0) {
          ccpSize = 127;
        } else {
          ccpSize *= 2;
          ccpSize--;
        }
        if (DEBUG) {
          Log.d(TAG,"DTVCC_PACKET_START : sequenceNumber = " + sequenceNumber+ " ccpSize = " + ccpSize);
        }
        processCCPacket(ccData2);
        ccpDataIndex = 1;
      } else {
        // The only remaining valid packet type is DTVCC_PACKET_DATA
        Assertions.checkArgument(ccType == DTVCC_PACKET_DATA);
        if (ccpSize == 0) {
          Log.w(TAG,"Encountered DTVCC_PACKET_DATE before DTVCC_PACKET_START, ignoring...");
          continue;
        }
        processCCPacket(ccData1, ccData2);
        ccpDataIndex += 2;
        if (DEBUG) {
          Log.d(TAG, "DTVCC_PACKET_DATA : ccpDataIndex = " + ccpDataIndex);
      }
      }
    }
    if (cuesNeedUpdate) {
      updateCues();
  }
    }
  private void finishCCPacket() {
    ccpDataIndex = 0;
    ccpSize = 0;
    resetServiceBlockState();
  }
  private void updateCues() {
    cuesNeedUpdate = false;
    cues = getDisplayCues();
    onNewSubtitleDataAvailable(inputTimestampUs);
    }
  private void processCCPacket(byte... dtvccPkt) {
    for (int i = 0; i < dtvccPkt.length; i++) {
      switch(serviceBlockProcessingState){
        case SERVICE_BLOCK_HEADER: {
          serviceNumber = (dtvccPkt[i] & 0xE0) >> 5; // 3 bits
          serviceBlockSize = (dtvccPkt[i] & 0x1F); // 5 bits
          if (DEBUG) {
            Log.d(TAG,"SERVICE_BLOCK_HEADER: serviceNumber = " + serviceNumber +
                    ", serviceBlockSize = " + serviceBlockSize);
          }
          if (serviceNumber == 7 && serviceBlockSize != 0) {
            serviceBlockProcessingState = SERVICE_BLOCK_EXT_HEADER;
          } else if (serviceBlockSize != 0) {
            serviceBlockProcessingState = SERVICE_BLOCK_DATA;
            serviceBlockDataBufferOffset = 0;
          } else { // 0 size block. remain in service block header state
            serviceNumber = 0;
          }
        }
        break;
        case SERVICE_BLOCK_EXT_HEADER: {
      // extended service numbers
          serviceNumber = (dtvccPkt[i] & 0x3F); // 6 bits
          if (serviceBlockSize != 0) {
            serviceBlockProcessingState = SERVICE_BLOCK_DATA;
            serviceBlockDataBufferOffset = 0;
            if (DEBUG) {
              Log.d(TAG, "SERVICE_BLOCK_EXT_HEADER: serviceNumber = " + serviceNumber +
                      ", serviceBlockSize = " + serviceBlockSize);
      }
          } else {
            // reset service block processing state
            serviceBlockProcessingState = SERVICE_BLOCK_HEADER;
            serviceNumber = 0;
          }
        }
        break;
        case SERVICE_BLOCK_DATA: {
          serviceBlockDataBufferOffset++;
          if (serviceNumber == selectedServiceNumber) {
            processCCData(dtvccPkt[i]);
    }

          if (serviceBlockDataBufferOffset == serviceBlockSize) {
            if (DEBUG) {
              Log.d(TAG, "End of Service Block");
      }
            resetServiceBlockState();
            if (cuesNeedUpdate) {
              updateCues();
            }
          }
        }
        break;
      }
    }
  }
  // resets service block processing state
  private void resetServiceBlockState() {
    serviceBlockDataBufferOffset = 0;
    serviceBlockProcessingState = SERVICE_BLOCK_HEADER;
    serviceNumber = 0;
    serviceBlockSize = 0;
    }

  private void resetCCDataState() {
    currentCommand = -1;
    cuesNeedUpdate = false;
    ccDataSkipCount = 0;
    ccDataState = CC_DATA_STATE_COMMAND;
  }
  // process the closed caption data
  // It manages the service input buffer queue for the selected service number.
  // It has a RST and DLS pre-processing block
  private void processCCData(byte data) {
    // DLC and RST command pre-processor
    int command = data & 0xFF;
    if (command == COMMAND_RST || command == COMMAND_DLC) {
      // cancel the delay
      delayUs = 0;
      // additionally RST command also clears the service input buffer queue.
      if (command == COMMAND_RST) {
        serviceInputBufferQ.reset();
      }
    }
    // now add the incoming data to service input buffer queue.
    if (serviceInputBufferQ.canWrite()) {
      // push to service input buffer queue
      serviceInputBufferQ.write(data);
    } else {
      Log.w(TAG, "Service Input buffer FULL!!!");
      // if service input buffer is full, cancel delay command
      delayUs = 0;
    }
    // detect timeout of delay and cancel it so that the processing happens immediately.
    if (delayUs != 0 && inputTimestampUs - startOfDelayUs >= delayUs) {
      delayUs = 0;
    }
    // if delay is active, skip processing the command
    if (delayUs != 0) {
      return;
    }
    // process the closed caption data stored in service input buffer
    while (serviceInputBufferQ.canRead(1)) {
      switch(ccDataState) {
        case CC_DATA_STATE_COMMAND: {
          currentCommand = serviceInputBufferQ.read();
          //if delay is not enabled or RST command, process command immediately
          if (currentCommand == COMMAND_RST || delayUs == 0) {
            cuesNeedUpdate |= handleCommand(currentCommand);
          } else if (currentCommand == COMMAND_DLC ||
                     serviceInputBufferQ.size() >= SERVICE_INPUT_BUFF_SIZE ||
                     (inputTimestampUs - startOfDelayUs >= delayUs)) {
            // cancel delay if DLC or service input buffer is full or delay timer expired
            delayUs = 0;
            // now process all delayed commands already stored in service input buffer queue.
            cuesNeedUpdate |= handleCommand(currentCommand);
          } else {
            // we are in delay mode
          }
        }
        break;
        case CC_DATA_STATE_WAITING_FOR_PARAM: {
          // we reset state to command here, because if the command still expects more params,
          // handleCommand will change the state to waiting for param again
          ccDataState = CC_DATA_STATE_COMMAND;
          cuesNeedUpdate |= handleCommand(currentCommand);
        }
        break;
        case CC_DATA_STATE_SKIPPING: {
          serviceInputBufferQ.read();
          ccDataSkipCount--;
          if (ccDataSkipCount == 0) {
            ccDataState = CC_DATA_STATE_COMMAND;
            isExtendedCommand = false;
          }
        }
        break;
      }
      if (ccDataState == CC_DATA_STATE_WAITING_FOR_PARAM) {
        break;
      }
    }

  }
  private void skipBytes(int count) {
    if (count > 0) {
      ccDataState = CC_DATA_STATE_SKIPPING;
      ccDataSkipCount = count;
    }
  }
  private void printCommandName(int command) {
    String commandName = null;
    switch (command) {
      case COMMAND_NUL: commandName = "Null"; break;
      case COMMAND_ETX: commandName = "EndOfText"; break;
      case COMMAND_BS:  commandName = "Backspace"; break;
      case COMMAND_FF:  commandName = "FormFeed (Flush)"; break;
      case COMMAND_CR:  commandName = "Carriage Return"; break;
      case COMMAND_HCR: commandName = "ClearLine"; break;

      case COMMAND_CW0: commandName = "SetCurrentWindow 0"; break;
      case COMMAND_CW1: commandName = "SetCurrentWindow 1"; break;
      case COMMAND_CW2: commandName = "SetCurrentWindow 2"; break;
      case COMMAND_CW3: commandName = "SetCurrentWindow 3"; break;
      case COMMAND_CW4: commandName = "SetCurrentWindow 4"; break;
      case COMMAND_CW5: commandName = "SetCurrentWindow 5"; break;
      case COMMAND_CW6: commandName = "SetCurrentWindow 6"; break;
      case COMMAND_CW7: commandName = "SetCurrentWindow 7"; break;

      case COMMAND_CLW: commandName = "ClearWindows"; break;
      case COMMAND_DSW: commandName = "DisplayWindows"; break;
      case COMMAND_HDW: commandName = "HideWindows"; break;
      case COMMAND_TGW: commandName = "ToggleWindows"; break;
      case COMMAND_DLW: commandName = "DeleteWindows"; break;
      case COMMAND_DLY: commandName = "Delay"; break;
      case COMMAND_DLC: commandName = "DelayCancel"; break;
      case COMMAND_RST: commandName = "Reset"; break;
      case COMMAND_SPA: commandName = "SetPenAttributes"; break;
      case COMMAND_SPC: commandName = "SetPenColor"; break;
      case COMMAND_SPL: commandName = "SetPenLocation"; break;
      case COMMAND_SWA: commandName = "SetWindowAttributes"; break;
      case COMMAND_DF0: commandName = "DefineWindow 0"; break;
      case COMMAND_DF1: commandName = "DefineWindow 1"; break;
      case COMMAND_DF2: commandName = "DefineWindow 2"; break;
      case COMMAND_DF3: commandName = "DefineWindow 3"; break;
      case COMMAND_DS4: commandName = "DefineWindow 4"; break;
      case COMMAND_DF5: commandName = "DefineWindow 5"; break;
      case COMMAND_DF6: commandName = "DefineWindow 6"; break;
      case COMMAND_DF7: commandName = "DefineWindow 7"; break;
    }

    if (commandName != null) {
      Log.d(TAG, "handleCommand: " + commandName);
    }
  }

  private boolean handleCommand(int command) {
    if (DEBUG) {
      printCommandName(command);
    }
    boolean shouldUpdateCue = false;
    try {
      if (command == COMMAND_EXT1) {
        // Read the extended command
        if (!serviceInputBufferQ.canRead(1)) {
          ccDataState = CC_DATA_STATE_WAITING_FOR_PARAM;
          return shouldUpdateCue;
        }
        command = serviceInputBufferQ.read();
        // update the current command
        currentCommand = command;
        isExtendedCommand = true;
      }
      if (!isExtendedCommand) {
        if (command <= GROUP_C0_END) {
          // If the C0 command was an ETX command, the cues are updated in handleC0Command.
          shouldUpdateCue |= handleC0Command(command);
        } else if (command <= GROUP_G0_END) {
          shouldUpdateCue |= handleG0Character(command);
        } else if (command <= GROUP_C1_END) {
          shouldUpdateCue |= handleC1Command(command);
        } else if (command <= GROUP_G1_END) {
          shouldUpdateCue |= handleG1Character(command);
        } else {
          Log.w(TAG, "Invalid base command: " + command);
        }
      } else {
        if (command <= GROUP_C2_END) {
          shouldUpdateCue |= handleC2Command(command);
        } else if (command <= GROUP_G2_END) {
          shouldUpdateCue |= handleG2Character(command);
          isExtendedCommand = false;
        } else if (command <= GROUP_C3_END) {
          shouldUpdateCue |= handleC3Command(command);
        } else if (command <= GROUP_G3_END) {
          shouldUpdateCue |= handleG3Character(command);
          isExtendedCommand = false;
        } else {
          Log.w(TAG, "Invalid extended command: " + command);
          isExtendedCommand = false;
        }
      }
    } catch (IllegalStateException ex) {
      Log.w(TAG, "CEA708 stream seems to be broken, captions might be incorrect as data in invalid");
    }
    return shouldUpdateCue;
  }

  private boolean handleC0Command(int command) {
    switch (command) {
      case COMMAND_NUL:
        // Do nothing.
        break;
      case COMMAND_ETX:
        updateCues();
        break;
      case COMMAND_BS:
        currentCueBuilder.backspace();
        break;
      case COMMAND_FF:
        cueBuilders[currentWindow].clear();
        currentCueBuilder.setPenLocation(0, 0);
        break;
      case COMMAND_CR:
        currentCueBuilder.append('\n');
        break;
      case COMMAND_HCR:
        currentCueBuilder.hcr();
        break;
      default:
        if (command >= COMMAND_EXT1_START && command <= COMMAND_EXT1_END) {
          Log.w(TAG, "Currently unsupported COMMAND_EXT1 Command: " + command);
          skipBytes(1);
        } else if (command >= COMMAND_P16_START && command <= COMMAND_P16_END) {
          Log.w(TAG, "Currently unsupported COMMAND_P16 Command: " + command);
          skipBytes(2);
        } else {
          Log.w(TAG, "Invalid C0 command: " + command);
        }
    }
    return false;
  }

  private boolean handleC1Command(int command) {
    int window;
    switch (command) {
      case COMMAND_CW0:
      case COMMAND_CW1:
      case COMMAND_CW2:
      case COMMAND_CW3:
      case COMMAND_CW4:
      case COMMAND_CW5:
      case COMMAND_CW6:
      case COMMAND_CW7:
        window = (command - COMMAND_CW0);
        if (currentWindow != window) {
          currentWindow = window;
          currentCueBuilder = cueBuilders[window];
          if (DEBUG) {
            Log.d(TAG, "Setting current window to " + window);
          }
        }
        break;
      case COMMAND_CLW: {
        if (!serviceInputBufferQ.canRead(1)) {
          ccDataState = CC_DATA_STATE_WAITING_FOR_PARAM;
          break;
          }
        int windowMap = serviceInputBufferQ.read();
        for (int i = 0; i < NUM_WINDOWS; i++) {
          if ((windowMap & (1 << i)) != 0) {
            cueBuilders[i].clear();
            if (DEBUG) {
              Log.d(TAG, "Clearing window with ID: " + i);
            }
          }
        }
        break;
      }
      case COMMAND_DSW: {
        if (!serviceInputBufferQ.canRead(1)) {
          ccDataState = CC_DATA_STATE_WAITING_FOR_PARAM;
          break;
        }
        int windowMap = serviceInputBufferQ.read();
        for (int i = 0; i < NUM_WINDOWS; i++) {
          if ((windowMap & (1 << i)) != 0) {
            CueBuilder builder = cueBuilders[i];
            if (!builder.defined) {
              if (DEBUG) {
                Log.d(TAG, "DisplayWindow command skipped for undefined window" + " ID: " + i);
              }
              continue;
            }
            cueBuilders[i].setVisibility(true);
            if (DEBUG) {
              Log.d(TAG, "Showing window with ID: " + i);
            }
          }
        }
        break;
      }
      case COMMAND_HDW: {
        if (!serviceInputBufferQ.canRead(1)) {
          ccDataState = CC_DATA_STATE_WAITING_FOR_PARAM;
          break;
        }
        int windowMap = serviceInputBufferQ.read();
        for (int i = 0; i < NUM_WINDOWS; i++) {
          if ((windowMap & (1 << i)) != 0) {
            cueBuilders[i].setVisibility(false);
            if (DEBUG) {
              Log.d(TAG, "Hiding window with ID: " + i);
            }
          }
        }
        break;
      }
      case COMMAND_TGW: {
        if (!serviceInputBufferQ.canRead(1)) {
          ccDataState = CC_DATA_STATE_WAITING_FOR_PARAM;
          break;
        }
        int windowMap = serviceInputBufferQ.read();
        for (int i = 0; i < NUM_WINDOWS; i++) {
          if ((windowMap & (1 << i)) != 0) {
            CueBuilder builder = cueBuilders[i];
            if (!builder.defined) {
              if (DEBUG) {
                Log.d(TAG, "ToggleWindow command skipped for undefined window" + " ID: " + i);
              }
              continue;
            }
            builder.setVisibility(!builder.isVisible());
            if (DEBUG) {
              Log.d(TAG, "Toggling window with ID: " + i);
          }
        }
        }
        break;
      }
      case COMMAND_DLW: {
        if (!serviceInputBufferQ.canRead(1)) {
          ccDataState = CC_DATA_STATE_WAITING_FOR_PARAM;
        break;
        }
        int windowMap = serviceInputBufferQ.read();
        for (int i = 0; i < NUM_WINDOWS; i++) {
          if ((windowMap & (1 << i)) != 0) {
            cueBuilders[i].reset();
            if (DEBUG) {
              Log.d(TAG, "Deleting window: " + i);
            }
          }
        }
        break;
      }
      case COMMAND_DLY: {
        if (!serviceInputBufferQ.canRead(1)) {
          ccDataState = CC_DATA_STATE_WAITING_FOR_PARAM;
        break;
        }
        // delay is in tenths of a second
        delayUs = serviceInputBufferQ.read() * (C.MICROS_PER_SECOND / 10);
        startOfDelayUs = inputTimestampUs;
        break;
      }
      case COMMAND_RST: {
        resetCueBuilders();
        break;
      }
      case COMMAND_SPA: {
        int paramLen = 2;
        if (!currentCueBuilder.isDefined()) {
          // ignore this command if the current window/cue isn't defined
          skipBytes(paramLen);
        } else if (!serviceInputBufferQ.canRead(paramLen)) {
          // param not received yet. wait....
          ccDataState = CC_DATA_STATE_WAITING_FOR_PARAM;
        } else {
          handleSetPenAttributes();
        }
        break;
      }
      case COMMAND_SPC: {
        int paramLen = 3;
        if (!currentCueBuilder.isDefined()) {
          // ignore this command if the current window/cue isn't defined
          skipBytes(paramLen);
        } else if (!serviceInputBufferQ.canRead(paramLen)) {
          // param not received yet. wait....
          ccDataState = CC_DATA_STATE_WAITING_FOR_PARAM;
        } else {
          handleSetPenColor();
        }
        break;
      }
      case COMMAND_SPL: {
        int paramLen = 2;
        if (!currentCueBuilder.isDefined()) {
          // ignore this command if the current window/cue isn't defined
          skipBytes(paramLen);
        } else if (!serviceInputBufferQ.canRead(paramLen)) {
          // param not received yet. wait....
          ccDataState = CC_DATA_STATE_WAITING_FOR_PARAM;
        } else {
          handleSetPenLocation();
        }
        break;
      }
      case COMMAND_SWA: {
        int paramLen = 4;
        if (!currentCueBuilder.isDefined()) {
          // ignore this command if the current window/cue isn't defined
          skipBytes(paramLen);
        } else if (!serviceInputBufferQ.canRead(paramLen)) {
          // param not received yet. wait....
          ccDataState = CC_DATA_STATE_WAITING_FOR_PARAM;
        } else {
          handleSetWindowAttributes();
        }
        break;
      }
      case COMMAND_DF0:
      case COMMAND_DF1:
      case COMMAND_DF2:
      case COMMAND_DF3:
      case COMMAND_DS4:
      case COMMAND_DF5:
      case COMMAND_DF6:
      case COMMAND_DF7: {
        if (!serviceInputBufferQ.canRead(6)) {
          // param not received yet. wait....
          ccDataState = CC_DATA_STATE_WAITING_FOR_PARAM;
          break;
        }
        window = (command - COMMAND_DF0);
        handleDefineWindow(window);
        // We also set the current window to the newly defined window.
        if (currentWindow != window) {
          currentWindow = window;
          currentCueBuilder = cueBuilders[window];
        }
        break;
      }
      default:
        Log.w(TAG, "Invalid C1 command: " + command);
    }

    // if the state is either skipping or waiting for param, don't render cue
    return ccDataState == CC_DATA_STATE_COMMAND;
  }

  private boolean handleC2Command(int command) {
    // C2 Table doesn't contain any commands in CEA-708-B, but we do need to skip bytes
    if (command <= 0x07) {
      // Do nothing.
    } else if (command <= 0x0F) {
      skipBytes(1);
    } else if (command <= 0x17) {
      skipBytes(2);
    } else if (command <= 0x1F) {
      skipBytes(3);
    }
    return false;
  }

  private boolean handleC3Command(int command) {
    // C3 Table doesn't contain any commands in CEA-708-B, but we do need to skip bytes
    if (command <= 0x87) {
      skipBytes(4);
    } else if (command <= 0x8F) {
      skipBytes(5);
    } else if (command <= 0x9F) {
      // 90-9F are variable length codes; the first byte defines the header with the first
      // 2 bits specifying the type and the last 6 bits specifying the remaining length of the
      // command in bytes
      if (!serviceInputBufferQ.canRead(1)) {
        ccDataState = CC_DATA_STATE_WAITING_FOR_PARAM;
        return false;
    }
      int val = serviceInputBufferQ.read();
      skipBytes((int) (val & 0x3F)); // 6 bits
    }
    return false;
  }

  private boolean handleG0Character(int characterCode) {
    if (characterCode == CHARACTER_MN) {
      currentCueBuilder.append('\u266B');
    } else {
      currentCueBuilder.append((char) (characterCode & 0xFF));
    }
    return true;
  }

  private boolean handleG1Character(int characterCode) {
    currentCueBuilder.append((char) (characterCode & 0xFF));
    return true;
  }

  private boolean handleG2Character(int characterCode) {
    switch (characterCode) {
      case CHARACTER_TSP:
        currentCueBuilder.append('\u0020');
        break;
      case CHARACTER_NBTSP:
        currentCueBuilder.append('\u00A0');
        break;
      case CHARACTER_ELLIPSIS:
        currentCueBuilder.append('\u2026');
        break;
      case CHARACTER_BIG_CARONS:
        currentCueBuilder.append('\u0160');
        break;
      case CHARACTER_BIG_OE:
        currentCueBuilder.append('\u0152');
        break;
      case CHARACTER_SOLID_BLOCK:
        currentCueBuilder.append('\u2588');
        break;
      case CHARACTER_OPEN_SINGLE_QUOTE:
        currentCueBuilder.append('\u2018');
        break;
      case CHARACTER_CLOSE_SINGLE_QUOTE:
        currentCueBuilder.append('\u2019');
        break;
      case CHARACTER_OPEN_DOUBLE_QUOTE:
        currentCueBuilder.append('\u201C');
        break;
      case CHARACTER_CLOSE_DOUBLE_QUOTE:
        currentCueBuilder.append('\u201D');
        break;
      case CHARACTER_BOLD_BULLET:
        currentCueBuilder.append('\u2022');
        break;
      case CHARACTER_TM:
        currentCueBuilder.append('\u2122');
        break;
      case CHARACTER_SMALL_CARONS:
        currentCueBuilder.append('\u0161');
        break;
      case CHARACTER_SMALL_OE:
        currentCueBuilder.append('\u0153');
        break;
      case CHARACTER_SM:
        currentCueBuilder.append('\u2120');
        break;
      case CHARACTER_DIAERESIS_Y:
        currentCueBuilder.append('\u0178');
        break;
      case CHARACTER_ONE_EIGHTH:
        currentCueBuilder.append('\u215B');
        break;
      case CHARACTER_THREE_EIGHTHS:
        currentCueBuilder.append('\u215C');
        break;
      case CHARACTER_FIVE_EIGHTHS:
        currentCueBuilder.append('\u215D');
        break;
      case CHARACTER_SEVEN_EIGHTHS:
        currentCueBuilder.append('\u215E');
        break;
      case CHARACTER_VERTICAL_BORDER:
        currentCueBuilder.append('\u2502');
        break;
      case CHARACTER_UPPER_RIGHT_BORDER:
        currentCueBuilder.append('\u2510');
        break;
      case CHARACTER_LOWER_LEFT_BORDER:
        currentCueBuilder.append('\u2514');
        break;
      case CHARACTER_HORIZONTAL_BORDER:
        currentCueBuilder.append('\u2500');
        break;
      case CHARACTER_LOWER_RIGHT_BORDER:
        currentCueBuilder.append('\u2518');
        break;
      case CHARACTER_UPPER_LEFT_BORDER:
        currentCueBuilder.append('\u250C');
        break;
      default:
        Log.w(TAG, "Invalid G2 character: " + characterCode);
        // The CEA-708 specification doesn't specify what to do in the case of an unexpected
        // value in the G2 character range, so we ignore it.
        return false;
    }
    return true;
  }

  private boolean handleG3Character(int characterCode) {
    if (characterCode == 0xA0) {
      currentCueBuilder.append('\u33C4');
    } else {
      Log.w(TAG, "Invalid G3 character: " + characterCode);
      // Substitute any unsupported G3 character with an underscore as per CEA-708 specification.
      currentCueBuilder.append('_');
    }
    return true;
  }

  private void handleSetPenAttributes() {
    // the SetPenAttributes command contains 2 bytes of data
    // first byte
    int param = serviceInputBufferQ.read();
    int textTag = (param & 0xF0) >> 4; // xxxx 0000
    int offset = (param & 0x0C) >> 2;  // 0000 xx00
    int penSize = (param & 0x03);      // 0000 00xx
    // second byte
    param = serviceInputBufferQ.read();
    boolean italicsToggle = ((param & 0x80) >> 7 == 1);    // x000 0000
    boolean underlineToggle = ((param & 0x40) >> 6 == 1);  // 0x00 0000
    int edgeType = (param & 0x38) >> 3;                    // 00xx x000
    int fontStyle = (param & 0x07);                        // 0000 0xxx

    currentCueBuilder.setPenAttributes(textTag, offset, penSize, italicsToggle, underlineToggle,
        edgeType, fontStyle);
  }

  private void handleSetPenColor() {
    // the SetPenColor command contains 3 bytes of data
    // first byte
    int param = serviceInputBufferQ.read();
    int foregroundA = (param & 0xC0) >> 6; // xx00 0000
    int foregroundR = (param & 0x30) >> 4; // 00xx 0000
    int foregroundG = (param & 0x0C) >> 2; // 0000 xx00
    int foregroundB = (param & 0x03);      // 0000 00xx
    int foregroundColor = CueBuilder.getArgbColorFromCeaColor(foregroundR, foregroundG, foregroundB,
            foregroundA);
    // second byte
    param = serviceInputBufferQ.read();
    int backgroundA = (param & 0xC0) >> 6; // xx00 0000
    int backgroundR = (param & 0x30) >> 4; // 00xx 0000
    int backgroundG = (param & 0x0C) >> 2; // 0000 xx00
    int backgroundB = (param & 0x03);      // 0000 00xx
    int backgroundColor = CueBuilder.getArgbColorFromCeaColor(backgroundR, backgroundG, backgroundB,
            backgroundA);
    // third byte
    param = serviceInputBufferQ.read();
     // skip 2 bits null padding     // xx00 0000
    int edgeR = (param & 0x30) >> 4; // 00xx 0000
    int edgeG = (param & 0x0C) >> 2; // 0000 xx00
    int edgeB = (param & 0x03);      // 0000 00xx
    int edgeColor = CueBuilder.getArgbColorFromCeaColor(edgeR, edgeG, edgeB);

    currentCueBuilder.setPenColor(foregroundColor, backgroundColor, edgeColor);
  }

  private void handleSetPenLocation() {
    // the SetPenLocation command contains 2 bytes of data
    // first byte
    int param = serviceInputBufferQ.read();
    // skip 4 bits             // xxxx 0000
    int row = (param & 0x0F);  // 0000 xxxx
    // second byte
    param = serviceInputBufferQ.read();
    // skip 2 bits               // xx00 0000
    int column = (param & 0x3F); // 00xx xxxx

    currentCueBuilder.setPenLocation(row, column);
  }

  private void handleSetWindowAttributes() {
    // the SetWindowAttributes command contains 4 bytes of data
    // first byte
    int param = serviceInputBufferQ.read();
    int fillA = (param & 0xC0) >> 6; // xx00 0000
    int fillR = (param & 0x30) >> 4; // 00xx 0000
    int fillG = (param & 0x0C) >> 2; // 0000 xx00
    int fillB = (param & 0x03);      // 0000 00xx
    int fillColor = CueBuilder.getArgbColorFromCeaColor(fillR, fillG, fillB, fillA);
    // second byte
    param = serviceInputBufferQ.read();
    int borderType = (param & 0xC0) >> 6; // xx00 0000
    int borderR = (param & 0x30) >> 4;    // 00xx 0000
    int borderG = (param & 0x0C) >> 2;    // 0000 xx00
    int borderB = (param & 0x03);         // 0000 00xx
    int borderColor = CueBuilder.getArgbColorFromCeaColor(borderR, borderG, borderB);
    // third byte
    param = serviceInputBufferQ.read();
    if (((param & 0x80) >> 7 == 1)) {                     // x000 0000
      borderType |= 0x04; // set the top bit of the 3-bit borderType
    }
    boolean wordWrapToggle = ((param & 0x40) >> 6 == 1);  // 0x00 0000
    int printDirection = (param & 0x30) >> 4;             // 00xx 0000
    int scrollDirection = (param & 0x0C) >> 2;            // 0000 xx00
    int justification = (param & 0x03);                   // 0000 00xx
    // fourth byte
    // Note that we don't intend to support display effects
    param = serviceInputBufferQ.read(); // skip display effects

    currentCueBuilder.setWindowAttributes(fillColor, borderColor, wordWrapToggle, borderType,
        printDirection, scrollDirection, justification);
  }

  private void handleDefineWindow(int window) {
    CueBuilder cueBuilder = cueBuilders[window];

    // the DefineWindow command contains 6 bytes of data
    // first byte
    int param = serviceInputBufferQ.read();
    // skip 2 bits null padding                            // xx00 0000
    boolean visible = ((param & 0x20) >> 5 == 1);          // 00x0 0000
    boolean rowLock = ((param & 0x10) >> 4 == 1);          // 000x 0000
    boolean columnLock = ((param & 0x08) >> 3 == 1);       // 0000 x000
    int priority = (param & 0x07);                         // 0000 0xxx
    // second byte
    param = serviceInputBufferQ.read();
    boolean relativePositioning = ((param & 0x80) >> 7 == 1);    // x000 0000
    int verticalAnchor = (param & 0x7F);                         // 0xxx xxxx
    // third byte
    int horizontalAnchor = serviceInputBufferQ.read();           // xxxx xxxx
    // fourth byte
    param = serviceInputBufferQ.read();
    int anchorId = (param & 0xF0) >> 4;  // xxxx 0000
    int rowCount = (param & 0x0F);       // 0000 xxxx
    // fifth byte
    param = serviceInputBufferQ.read();
    // skip 2 bits null padding           // xx00 0000
    int columnCount = (param & 0x3F);     // 00xx xxxx
    // sixth byte
    param = serviceInputBufferQ.read();
    // skip 2 bits null padding             // xx00 0000
    int windowStyle = (param & 0x38) >> 3;  // 00xx x000
    int penStyle = (param & 0x07);          // 0000 0xxx

    cueBuilder.defineWindow(visible, rowLock, columnLock, priority, relativePositioning,
        verticalAnchor, horizontalAnchor, rowCount, columnCount, anchorId, windowStyle, penStyle);
  }

  private List<Cue> getDisplayCues() {
    List<Cea708Cue> displayCues = new ArrayList<>();
    for (int i = 0; i < NUM_WINDOWS; i++) {
      // we need to render empty window, so allow empty captions.
      if (cueBuilders[i].isVisible()) {
        displayCues.add(cueBuilders[i].build());
      }
    }
    Collections.sort(displayCues);
    return Collections.<Cue>unmodifiableList(displayCues);
  }

  private void resetCueBuilders() {
    for (int i = 0; i < NUM_WINDOWS; i++) {
      cueBuilders[i].reset();
    }
    delayUs = 0;
    //serviceInputBufLen = 0;
    serviceInputBufferQ.reset();
  }

  // TODO: There is a lot of overlap between Cea708Decoder.CueBuilder and Cea608Decoder.CueBuilder
  // which could be refactored into a separate class.
  private static final class CueBuilder {

    private static final int RELATIVE_CUE_SIZE = 99;
    private static final int VERTICAL_SIZE = 74;
    private static final int HORIZONTAL_SIZE = 209;

    private static final int DEFAULT_PRIORITY = 4;

    private static final int MAXIMUM_ROW_COUNT = 15;

    private static final int JUSTIFICATION_LEFT = 0;
    private static final int JUSTIFICATION_RIGHT = 1;
    private static final int JUSTIFICATION_CENTER = 2;
    private static final int JUSTIFICATION_FULL = 3;

    private static final int DIRECTION_LEFT_TO_RIGHT = 0;
    private static final int DIRECTION_RIGHT_TO_LEFT = 1;
    private static final int DIRECTION_TOP_TO_BOTTOM = 2;
    private static final int DIRECTION_BOTTOM_TO_TOP = 3;

    // TODO: Add other border/edge types when utilized.
    private static final int BORDER_AND_EDGE_TYPE_NONE = 0;
    private static final int BORDER_AND_EDGE_TYPE_UNIFORM = 3;

    public static final int COLOR_SOLID_WHITE = getArgbColorFromCeaColor(2, 2, 2, 0);
    public static final int COLOR_SOLID_BLACK = getArgbColorFromCeaColor(0, 0, 0, 0);
    public static final int COLOR_TRANSPARENT = getArgbColorFromCeaColor(0, 0, 0, 3);

    // TODO: Add other sizes when utilized.
    private static final int PEN_SIZE_STANDARD = 1;

    // TODO: Add other pen font styles when utilized.
    private static final int PEN_FONT_STYLE_DEFAULT = 0;
    private static final int PEN_FONT_STYLE_MONOSPACED_WITH_SERIFS = 1;
    private static final int PEN_FONT_STYLE_PROPORTIONALLY_SPACED_WITH_SERIFS = 2;
    private static final int PEN_FONT_STYLE_MONOSPACED_WITHOUT_SERIFS = 3;
    private static final int PEN_FONT_STYLE_PROPORTIONALLY_SPACED_WITHOUT_SERIFS = 4;

    // TODO: Add other pen offsets when utilized.
    private static final int PEN_OFFSET_NORMAL = 1;

    // The window style properties are specified in the CEA-708 specification.
    private static final int[] WINDOW_STYLE_JUSTIFICATION = new int[] {
        JUSTIFICATION_LEFT, JUSTIFICATION_LEFT, JUSTIFICATION_LEFT,
        JUSTIFICATION_LEFT, JUSTIFICATION_LEFT, JUSTIFICATION_CENTER,
        JUSTIFICATION_LEFT
    };
    private static final int[] WINDOW_STYLE_PRINT_DIRECTION = new int[] {
        DIRECTION_LEFT_TO_RIGHT, DIRECTION_LEFT_TO_RIGHT, DIRECTION_LEFT_TO_RIGHT,
        DIRECTION_LEFT_TO_RIGHT, DIRECTION_LEFT_TO_RIGHT, DIRECTION_LEFT_TO_RIGHT,
        DIRECTION_TOP_TO_BOTTOM
    };
    private static final int[] WINDOW_STYLE_SCROLL_DIRECTION = new int[] {
        DIRECTION_BOTTOM_TO_TOP, DIRECTION_BOTTOM_TO_TOP, DIRECTION_BOTTOM_TO_TOP,
        DIRECTION_BOTTOM_TO_TOP, DIRECTION_BOTTOM_TO_TOP, DIRECTION_BOTTOM_TO_TOP,
        DIRECTION_RIGHT_TO_LEFT
    };
    private static final boolean[] WINDOW_STYLE_WORD_WRAP = new boolean[] {
        false, false, false, true, true, true, false
    };
    private static final int[] WINDOW_STYLE_FILL = new int[] {
        COLOR_SOLID_BLACK, COLOR_TRANSPARENT, COLOR_SOLID_BLACK, COLOR_SOLID_BLACK,
        COLOR_TRANSPARENT, COLOR_SOLID_BLACK, COLOR_SOLID_BLACK
    };

    // The pen style properties are specified in the CEA-708 specification.
    private static final int[] PEN_STYLE_FONT_STYLE = new int[] {
        PEN_FONT_STYLE_DEFAULT, PEN_FONT_STYLE_MONOSPACED_WITH_SERIFS,
        PEN_FONT_STYLE_PROPORTIONALLY_SPACED_WITH_SERIFS, PEN_FONT_STYLE_MONOSPACED_WITHOUT_SERIFS,
        PEN_FONT_STYLE_PROPORTIONALLY_SPACED_WITHOUT_SERIFS,
        PEN_FONT_STYLE_MONOSPACED_WITHOUT_SERIFS,
        PEN_FONT_STYLE_PROPORTIONALLY_SPACED_WITHOUT_SERIFS
    };
    private static final int[] PEN_STYLE_EDGE_TYPE = new int[] {
        BORDER_AND_EDGE_TYPE_NONE, BORDER_AND_EDGE_TYPE_NONE, BORDER_AND_EDGE_TYPE_NONE,
        BORDER_AND_EDGE_TYPE_NONE, BORDER_AND_EDGE_TYPE_NONE, BORDER_AND_EDGE_TYPE_UNIFORM,
        BORDER_AND_EDGE_TYPE_UNIFORM
    };
    private static final int[] PEN_STYLE_BACKGROUND = new int[] {
        COLOR_SOLID_BLACK, COLOR_SOLID_BLACK, COLOR_SOLID_BLACK, COLOR_SOLID_BLACK,
        COLOR_SOLID_BLACK, COLOR_TRANSPARENT, COLOR_TRANSPARENT};

    private final List<SpannableString> rolledUpCaptions;
    private final SpannableStringBuilder captionStringBuilder;

    // Window/Cue properties
    private boolean defined;
    private boolean visible;
    private int priority;
    private boolean relativePositioning;
    private int verticalAnchor;
    private int horizontalAnchor;
    private int anchorId;
    private int rowCount;
    private boolean rowLock;
    private int justification;
    private int windowStyleId;
    private int penStyleId;
    private int windowFillColor;

    // Pen/Text properties
    private int italicsStartPosition;
    private int underlineStartPosition;
    private int foregroundColorStartPosition;
    private int foregroundColor;
    private int backgroundColorStartPosition;
    private int backgroundColor;
    private int row;

    public CueBuilder() {
      rolledUpCaptions = new ArrayList<>();
      captionStringBuilder = new SpannableStringBuilder();
      reset();
    }

    public boolean isEmpty() {
      return !isDefined() || (rolledUpCaptions.isEmpty() && captionStringBuilder.length() == 0);
    }

    public void reset() {
      clear();

      defined = false;
      visible = false;
      priority = DEFAULT_PRIORITY;
      relativePositioning = false;
      verticalAnchor = 0;
      horizontalAnchor = 0;
      anchorId = 0;
      rowCount = MAXIMUM_ROW_COUNT;
      rowLock = true;
      justification = JUSTIFICATION_LEFT;
      windowStyleId = 0;
      penStyleId = 0;
      windowFillColor = COLOR_SOLID_BLACK;

      foregroundColor = COLOR_SOLID_WHITE;
      backgroundColor = COLOR_SOLID_BLACK;
    }
    public void hcr() {
      captionStringBuilder.clear();
    }
    public void clear() {
      rolledUpCaptions.clear();
      captionStringBuilder.clear();
      italicsStartPosition = C.POSITION_UNSET;
      underlineStartPosition = C.POSITION_UNSET;
      foregroundColorStartPosition = C.POSITION_UNSET;
      backgroundColorStartPosition = C.POSITION_UNSET;
      row = 0;
    }

    public boolean isDefined() {
      return defined;
    }

    public void setVisibility(boolean visible) {
      this.visible = visible;
    }

    public boolean isVisible() {
      return visible;
    }

    public void defineWindow(boolean visible, boolean rowLock, boolean columnLock, int priority,
        boolean relativePositioning, int verticalAnchor, int horizontalAnchor, int rowCount,
        int columnCount, int anchorId, int windowStyleId, int penStyleId) {
      this.defined = true;
      this.visible = visible;
      this.rowLock = rowLock;
      this.priority = priority;
      this.relativePositioning = relativePositioning;
      this.verticalAnchor = verticalAnchor;
      this.horizontalAnchor = horizontalAnchor;
      this.anchorId = anchorId;

      // Decoders must add one to rowCount to get the desired number of rows.
      if (this.rowCount != rowCount + 1) {
        this.rowCount = rowCount + 1;

        // Trim any rolled up captions that are no longer valid, if applicable.
        while ((rowLock && (rolledUpCaptions.size() >= this.rowCount))
            || (rolledUpCaptions.size() >= MAXIMUM_ROW_COUNT)) {
          rolledUpCaptions.remove(0);
        }
      }

      // TODO: Add support for column lock and count.

      if (windowStyleId != 0 && this.windowStyleId != windowStyleId) {
        this.windowStyleId = windowStyleId;
        // windowStyleId is 1-based.
        int windowStyleIdIndex = windowStyleId - 1;
        // Note that Border type and border color are the same for all window styles.
        setWindowAttributes(WINDOW_STYLE_FILL[windowStyleIdIndex], COLOR_TRANSPARENT,
            WINDOW_STYLE_WORD_WRAP[windowStyleIdIndex], BORDER_AND_EDGE_TYPE_NONE,
            WINDOW_STYLE_PRINT_DIRECTION[windowStyleIdIndex],
            WINDOW_STYLE_SCROLL_DIRECTION[windowStyleIdIndex],
            WINDOW_STYLE_JUSTIFICATION[windowStyleIdIndex]);
      }

      if (penStyleId != 0 && this.penStyleId != penStyleId) {
        this.penStyleId = penStyleId;
        // penStyleId is 1-based.
        int penStyleIdIndex = penStyleId - 1;
        // Note that pen size, offset, italics, underline, foreground color, and foreground
        // opacity are the same for all pen styles.
        setPenAttributes(0, PEN_OFFSET_NORMAL, PEN_SIZE_STANDARD, false, false,
            PEN_STYLE_EDGE_TYPE[penStyleIdIndex], PEN_STYLE_FONT_STYLE[penStyleIdIndex]);
        setPenColor(COLOR_SOLID_WHITE, PEN_STYLE_BACKGROUND[penStyleIdIndex], COLOR_SOLID_BLACK);
      }
    }


    public void setWindowAttributes(int fillColor, int borderColor, boolean wordWrapToggle,
        int borderType, int printDirection, int scrollDirection, int justification) {
      this.windowFillColor = fillColor;
      // TODO: Add support for border color and types.
      // TODO: Add support for word wrap.
      // TODO: Add support for other scroll directions.
      // TODO: Add support for other print directions.
      this.justification = justification;

    }

    public void setPenAttributes(int textTag, int offset, int penSize, boolean italicsToggle,
        boolean underlineToggle, int edgeType, int fontStyle) {
      // TODO: Add support for text tags.
      // TODO: Add support for other offsets.
      // TODO: Add support for other pen sizes.

      if (italicsStartPosition != C.POSITION_UNSET) {
        if (!italicsToggle) {
          captionStringBuilder.setSpan(new StyleSpan(Typeface.ITALIC), italicsStartPosition,
              captionStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          italicsStartPosition = C.POSITION_UNSET;
        }
      } else if (italicsToggle) {
        italicsStartPosition = captionStringBuilder.length();
      }

      if (underlineStartPosition != C.POSITION_UNSET) {
        if (!underlineToggle) {
          captionStringBuilder.setSpan(new UnderlineSpan(), underlineStartPosition,
              captionStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          underlineStartPosition = C.POSITION_UNSET;
        }
      } else if (underlineToggle) {
        underlineStartPosition = captionStringBuilder.length();
      }

      // TODO: Add support for edge types.
      // TODO: Add support for other font styles.
    }

    public void setPenColor(int foregroundColor, int backgroundColor, int edgeColor) {
      if (foregroundColorStartPosition != C.POSITION_UNSET) {
        if (this.foregroundColor != foregroundColor) {
          captionStringBuilder.setSpan(new ForegroundColorSpan(this.foregroundColor),
              foregroundColorStartPosition, captionStringBuilder.length(),
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }
      if (foregroundColor != COLOR_SOLID_WHITE) {
        foregroundColorStartPosition = captionStringBuilder.length();
        this.foregroundColor = foregroundColor;
      }

      if (backgroundColorStartPosition != C.POSITION_UNSET) {
        if (this.backgroundColor != backgroundColor) {
          captionStringBuilder.setSpan(new BackgroundColorSpan(this.backgroundColor),
              backgroundColorStartPosition, captionStringBuilder.length(),
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }
      if (backgroundColor != COLOR_SOLID_BLACK) {
        backgroundColorStartPosition = captionStringBuilder.length();
        this.backgroundColor = backgroundColor;
      }

      // TODO: Add support for edge color.
    }

    public void setPenLocation(int row, int column) {
      // TODO: Support moving the pen location with a window properly.

      // Until we support proper pen locations, if we encounter a row that's different from the
      // previous one, we should append a new line. Otherwise, we'll see strings that should be
      // on new lines concatenated with the previous, resulting in 2 words being combined, as
      // well as potentially drawing beyond the width of the window/screen.
      if (this.row != row) {
        append('\n');
      }
      this.row = row;
    }

    public void backspace() {
      int length = captionStringBuilder.length();
      if (length > 0) {
        captionStringBuilder.delete(length - 1, length);
      }
    }

    public void append(char text) {
      if (text == '\n') {
        rolledUpCaptions.add(buildSpannableString());
        captionStringBuilder.clear();

        if (italicsStartPosition != C.POSITION_UNSET) {
          italicsStartPosition = 0;
        }
        if (underlineStartPosition != C.POSITION_UNSET) {
          underlineStartPosition = 0;
        }
        if (foregroundColorStartPosition != C.POSITION_UNSET) {
          foregroundColorStartPosition = 0;
        }
        if (backgroundColorStartPosition != C.POSITION_UNSET) {
          backgroundColorStartPosition = 0;
        }

        while ((rowLock && (rolledUpCaptions.size() >= rowCount))
            || (rolledUpCaptions.size() >= MAXIMUM_ROW_COUNT)) {
          rolledUpCaptions.remove(0);
        }
      } else {
        captionStringBuilder.append(text);
      }
    }

    public SpannableString buildSpannableString() {
      SpannableStringBuilder spannableStringBuilder =
          new SpannableStringBuilder(captionStringBuilder);
      int length = spannableStringBuilder.length();

      if (length > 0) {
        if (italicsStartPosition != C.POSITION_UNSET) {
          spannableStringBuilder.setSpan(new StyleSpan(Typeface.ITALIC), italicsStartPosition,
              length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (underlineStartPosition != C.POSITION_UNSET) {
          spannableStringBuilder.setSpan(new UnderlineSpan(), underlineStartPosition,
              length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (foregroundColorStartPosition != C.POSITION_UNSET) {
          spannableStringBuilder.setSpan(new ForegroundColorSpan(foregroundColor),
              foregroundColorStartPosition, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (backgroundColorStartPosition != C.POSITION_UNSET) {
          spannableStringBuilder.setSpan(new BackgroundColorSpan(backgroundColor),
              backgroundColorStartPosition, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }

      return new SpannableString(spannableStringBuilder);
    }

    public Cea708Cue build() {
      SpannableStringBuilder cueString = new SpannableStringBuilder();

      // Add any rolled up captions, separated by new lines.
      for (int i = 0; i < rolledUpCaptions.size(); i++) {
        cueString.append(rolledUpCaptions.get(i));
        cueString.append('\n');
      }
      // Add the current line.
      cueString.append(buildSpannableString());

      // TODO: Add support for right-to-left languages (i.e. where right would correspond to normal
      // alignment).
      Alignment alignment;
      switch (justification) {
        case JUSTIFICATION_FULL:
          // TODO: Add support for full justification.
        case JUSTIFICATION_LEFT:
          alignment = Alignment.ALIGN_NORMAL;
          break;
        case JUSTIFICATION_RIGHT:
          alignment = Alignment.ALIGN_OPPOSITE;
          break;
        case JUSTIFICATION_CENTER:
          alignment = Alignment.ALIGN_CENTER;
          break;
        default:
          throw new IllegalArgumentException("Unexpected justification value: " + justification);
      }

      float position;
      float line;
      if (relativePositioning) {
        position = (float) horizontalAnchor / RELATIVE_CUE_SIZE;
        line = (float) verticalAnchor / RELATIVE_CUE_SIZE;
        position = (position * 0.8f) + 0.1f;
        line = (line * 0.8f) + 0.1f;
      } else {
        //position = (float) horizontalAnchor / HORIZONTAL_SIZE;
        //line = (float) verticalAnchor / VERTICAL_SIZE;

        //Use the same calculation formula of position with SDK 4
        position = getXPercent(horizontalAnchor);
        line =  getYPercent(verticalAnchor);
      }
      // Apply screen-edge padding to the line and position.
      //position = (position * 0.9f) + 0.05f;
      //line = (line * 0.9f) + 0.05f;

      // anchorId specifies where the anchor should be placed on the caption cue/window. The 9
      // possible configurations are as follows:
      //   0-----1-----2
      //   |           |
      //   3     4     5
      //   |           |
      //   6-----7-----8
      @AnchorType int verticalAnchorType;
      if (anchorId % 3 == 0) {
        verticalAnchorType = Cue.ANCHOR_TYPE_START;
      } else if (anchorId % 3 == 1) {
        verticalAnchorType = Cue.ANCHOR_TYPE_MIDDLE;
      } else {
        verticalAnchorType = Cue.ANCHOR_TYPE_END;
      }
      // TODO: Add support for right-to-left languages (i.e. where start is on the right).
      @AnchorType int horizontalAnchorType;
      if (anchorId / 3 == 0) {
        horizontalAnchorType = Cue.ANCHOR_TYPE_START;
      } else if (anchorId / 3 == 1) {
        horizontalAnchorType = Cue.ANCHOR_TYPE_MIDDLE;
      } else {
        horizontalAnchorType = Cue.ANCHOR_TYPE_END;
      }

      boolean windowColorSet = (windowFillColor != COLOR_SOLID_BLACK);

      return new Cea708Cue(cueString, alignment, line, Cue.LINE_TYPE_FRACTION, verticalAnchorType,
          position, horizontalAnchorType, Cue.DIMEN_UNSET, windowColorSet, windowFillColor,
          priority);
    }

    private static float getXPercent(int x){
      final float CC708_COLS_SAFE_AREA = 300.0f;
      final float CC708_COLS_SAFE_AREA_BASE = 45.0f;
      return (x+CC708_COLS_SAFE_AREA_BASE)/CC708_COLS_SAFE_AREA;
    }

    private static float getYPercent(int y){
      final float CC708_ROWS_SAFE_AREA = 93.75f;
      final float CC708_ROWS_SAFE_AREA_BASE = 9.375f;
      return (y+CC708_ROWS_SAFE_AREA_BASE)/CC708_ROWS_SAFE_AREA;
    }

    public static int getArgbColorFromCeaColor(int red, int green, int blue) {
      return getArgbColorFromCeaColor(red, green, blue, 0);
    }

    public static int getArgbColorFromCeaColor(int red, int green, int blue, int opacity) {
      Assertions.checkIndex(red, 0, 4);
      Assertions.checkIndex(green, 0, 4);
      Assertions.checkIndex(blue, 0, 4);
      Assertions.checkIndex(opacity, 0, 4);

      int alpha;
      switch (opacity) {
        case 0:
        case 1:
          // Note the value of '1' is actually FLASH, but we don't support that.
          alpha = 255;
          break;
        case 2:
          alpha = 127;
          break;
        case 3:
          alpha = 0;
          break;
        default:
          alpha = 255;
      }

      // TODO: Add support for the Alternative Minimum Color List or the full 64 RGB combinations.

      // Return values based on the Minimum Color List
      return Color.argb(alpha,
          (red > 1 ? 255 : 0),
          (green > 1 ? 255 : 0),
          (blue > 1 ? 255 : 0));
    }

  }

}
