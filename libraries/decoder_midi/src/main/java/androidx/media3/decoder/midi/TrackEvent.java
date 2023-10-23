/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.decoder.midi;

import androidx.media3.common.C;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;

/**
 * Represents a standard MIDI file track event.
 *
 * <p>A track event is a sequence of bytes in the track chunk, consisting of an elapsed time delta
 * and Midi, Meta, or SysEx command bytes. A track event is followed by either another track event,
 * or end of chunk marker bytes.
 */
@UnstableApi
/* package */ final class TrackEvent {

  /** The length of a MIDI event message in bytes. */
  public static final int MIDI_MESSAGE_LENGTH_BYTES = 3;

  /** A default or unset data value. */
  public static final int DATA_FIELD_UNSET = Integer.MIN_VALUE;

  private static final int TICKS_UNSET = -1;
  private static final int SYSEX_BEGIN_STATUS = 0xF0;
  private static final int SYSEX_END_STATUS = 0xF7;
  private static final int META_EVENT_STATUS = 0xFF;
  private static final int META_END_OF_TRACK = 0x2F;
  private static final int META_TEMPO_CHANGE = 0x51;
  private static final int[] CHANNEL_BYTE_LENGTHS = {3, 3, 3, 3, 2, 2, 3};
  private static final int[] SYSTEM_BYTE_LENGTHS = {1, 2, 3, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};

  public int timestampSize;
  public int eventFileSizeBytes;
  public int eventDecoderSizeBytes;
  public int statusByte;
  public long usPerQuarterNote;
  public long elapsedTimeDeltaTicks;

  private int data1;
  private int data2;
  private boolean isPopulated;

  public TrackEvent() {
    reset();
  }

  public void writeTo(byte[] data) {
    data[0] = (byte) statusByte;
    data[1] = (byte) data1;
    data[2] = (byte) data2;
  }

  public boolean populateFrom(ParsableByteArray parsableTrackEventBytes, int previousEventStatus)
      throws ParserException {

    reset();

    int startingPosition = parsableTrackEventBytes.getPosition();

    // At least two bytes must remain for there to be a valid MIDI event present to parse.
    if (parsableTrackEventBytes.bytesLeft() < 2) {
      return false;
    }

    elapsedTimeDeltaTicks = readVariableLengthInt(parsableTrackEventBytes);
    timestampSize = parsableTrackEventBytes.getPosition() - startingPosition;

    int firstByte = parsableTrackEventBytes.readUnsignedByte();
    eventDecoderSizeBytes = 1;

    if (firstByte == SYSEX_BEGIN_STATUS) {
      // TODO(b/228838584): Handle this gracefully.
      statusByte = firstByte;

      int currentByte;
      do { // Consume SysEx message.
        currentByte = parsableTrackEventBytes.readUnsignedByte();
      } while (currentByte != SYSEX_END_STATUS);
    } else if (firstByte == META_EVENT_STATUS) { // This is a Meta event.
      int metaEventMessageType = parsableTrackEventBytes.readUnsignedByte();
      int eventLength = readVariableLengthInt(parsableTrackEventBytes);

      statusByte = firstByte;

      switch (metaEventMessageType) {
        case META_TEMPO_CHANGE:
          usPerQuarterNote = parsableTrackEventBytes.readUnsignedInt24();

          if (usPerQuarterNote <= 0) {
            throw ParserException.createForUnsupportedContainerFeature(
                "Tempo event data value must be a non-zero positive value. Parsed value: "
                    + usPerQuarterNote);
          }

          parsableTrackEventBytes.skipBytes(eventLength - /* tempoDataLength */ 3);
          break;
        case META_END_OF_TRACK:
          parsableTrackEventBytes.setPosition(startingPosition);
          reset();
          return false;
        default: // Ignore all other Meta events.
          parsableTrackEventBytes.skipBytes(eventLength);
      }
    } else { // This is a MIDI channel event.
      // Check for running status, an occurrence where the statusByte has been omitted from the
      // bytes of this event. The standard expects us to assume that this command has the same
      // statusByte as the last command.
      boolean isRunningStatus = firstByte < 0x80;
      if (isRunningStatus) {
        if (previousEventStatus == DATA_FIELD_UNSET) {
          throw ParserException.createForMalformedContainer(
              /* message= */ "Running status in the first event.", /* cause= */ null);
        }
        data1 = firstByte;
        firstByte = previousEventStatus;
        eventDecoderSizeBytes++;
      }

      int messageLength = getMidiMessageLengthBytes(firstByte);
      if (!isRunningStatus) {
        if (messageLength > eventDecoderSizeBytes) {
          data1 = parsableTrackEventBytes.readUnsignedByte();
          eventDecoderSizeBytes++;
        }
      }
      // Only read the next data byte if expected to be present.
      if (messageLength > eventDecoderSizeBytes) {
        data2 = parsableTrackEventBytes.readUnsignedByte();
        eventDecoderSizeBytes++;
      }

      statusByte = firstByte;
    }

    eventFileSizeBytes = parsableTrackEventBytes.getPosition() - startingPosition;
    parsableTrackEventBytes.setPosition(startingPosition);
    isPopulated = true;

    return true;
  }

  public boolean isMidiEvent() {
    return statusByte != META_EVENT_STATUS && statusByte != SYSEX_BEGIN_STATUS;
  }

  public boolean isNoteChannelEvent() {
    int highNibble = statusByte >>> 4;
    return isMidiEvent() && (highNibble == 8 || highNibble == 9);
  }

  public boolean isMetaEvent() {
    return statusByte == META_EVENT_STATUS;
  }

  public boolean isPopulated() {
    return isPopulated;
  }

  public void reset() {
    isPopulated = false;
    timestampSize = C.LENGTH_UNSET;
    statusByte = DATA_FIELD_UNSET;
    data1 = DATA_FIELD_UNSET;
    data2 = DATA_FIELD_UNSET;
    elapsedTimeDeltaTicks = TICKS_UNSET;
    eventFileSizeBytes = C.LENGTH_UNSET;
    eventDecoderSizeBytes = C.LENGTH_UNSET;
    usPerQuarterNote = C.TIME_UNSET;
  }

  private static int readVariableLengthInt(ParsableByteArray data) {
    int result = 0;
    int currentByte;
    int bytesRead = 0;

    do {
      currentByte = data.readUnsignedByte();
      result = result << 7 | (currentByte & 0x7F);
      bytesRead++;
    } while (((currentByte & 0x80) != 0) && bytesRead <= /* maxByteLength= */ 4);

    return result;
  }

  private static int getMidiMessageLengthBytes(int status) {
    if ((status < 0x80) || (status > 0xFF)) {
      return 0;
    } else if (status >= 0xF0) {
      return SYSTEM_BYTE_LENGTHS[status & 0x0F];
    } else {
      return CHANNEL_BYTE_LENGTHS[(status >> 4) & 0x07];
    }
  }
}
