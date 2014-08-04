package com.google.android.exoplayer.parser.ts;

import android.media.MediaExtractor;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.chunk.HLSExtractor;
import com.google.android.exoplayer.parser.aac.AACExtractor;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.TraceUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class TSExtractor extends HLSExtractor {
  private static final String TAG = "TSExtractor";

  private final DataSource dataSource;

  private UnsignedByteArray packet;
  int packetWriteOffset;

  private final SparseArray<PayloadHandler> activePayloadHandlers;
  private final ArrayList<PESHandler> activePESHandlers;

  private boolean endOfStream;
  int audioStreamType;
  private boolean unitStartNotSignalled;

  private int dataSize;
  private int dataPosition;
  private int dataIncompletePosition;
  private boolean dataSourceFinished;

  private Sample outSample;

  static abstract class PayloadHandler {
    public int cc_counter;
    public int pid;

    protected void init(int pid) {
      this.pid = pid;
    }

    /**
     *
     * @param packet: the TS packet with position set to the beginning of the payload
     * @param payloadStart
     * @param unit_start
     */
    abstract public void handlePayload(UnsignedByteArray packet, int payloadStart, boolean unit_start);
  }

  class PESHandler extends PayloadHandler {
    private Sample currentSample;
    private int length;
    private int type;

    public PESHandler(int pid, int type) {
      super.init(pid);
      this.type = type;
    }

    @Override
    public void handlePayload(UnsignedByteArray packet, int payloadStart, boolean unitStart) {
      int offset = payloadStart;
      if (unitStartNotSignalled) {
        unitStart = (packet.get(payloadStart) == 0x00
                && packet.get(payloadStart + 1) == 0x00
                && packet.get(payloadStart + 2) == 0x01);
      }
      if (unitStart) {
        long pts = 0;

        // output previous packet
        if (currentSample != null) {
          outSample = currentSample;
          if (length != 0 && length != currentSample.data.position()) {
            Log.d(TAG, "PES length " + currentSample.data.position() + " != " + length);
          }
          currentSample = null;
        }

        currentSample = getSample(type);

        int[] prefix = new int[3];
        prefix[0] = packet.get(offset++);
        prefix[1] = packet.get(offset++);
        prefix[2] = packet.get(offset++);
        if (prefix[0] != 0 || prefix[1] != 0 || prefix[2] != 1 ) {
          Log.d(TAG, String.format("bad start code: 0x%02x%02x%02x", prefix[0], prefix[1], prefix[2]));
        }
        // skip stream id
        offset++;
        length = packet.getShort(offset);
        offset += 2;

        // skip some stuff
        offset++;
        int flags = packet.get(offset++);
        int headerDataLength = packet.get(offset++);
        int fixedOffset = offset;

        if ((flags & 0x80) == 0x80) {
          pts = (long)(packet.get(offset++) & 0x0e) << 28;
          pts |= (packet.get(offset++)) << 21;
          pts |= (packet.get(offset++) & 0xfe) << 13;
          pts |= (packet.get(offset++)) << 6;
          pts |= (packet.get(offset++) & 0xfe) >> 2;

        }
        if ((flags & 0x40) == 0x40) {
          // DTS
          offset += 5;
        }

        currentSample.timeUs = pts  * 1000 / 45;
        // XXX: remove
        currentSample.timeUs -= 10 * 1000000;

        offset = fixedOffset + headerDataLength;
        if (length > 0)
          length -= headerDataLength + 3;
        currentSample.data.put(packet.array(), offset, dataPosition - offset);
        return;
      }

      if (currentSample.data.position() + 188 > currentSample.data.capacity()) {
        resizeSample(currentSample, 2*(currentSample.data.capacity() + 188));
      }
      currentSample.data.put(packet.array(), offset, dataPosition - offset);
    }
  }

  abstract class SectionHandler extends PayloadHandler {
    protected int tableID;
    protected UnsignedByteArray section;
    int sectionLength;
    int sectionWriteOffset;

    public SectionHandler(int pid, int tableID) {
      super.init(pid);
      this.tableID = tableID;
      section = new UnsignedByteArray(1024);
    }

    @Override
    public void handlePayload(UnsignedByteArray packet, int payloadStart, boolean unit_start) {
      int offset = payloadStart;
      if (sectionLength == 0) {
        // pointer_field (what if pointer_field is != 0 ?)
        offset++;
        int tableID = packet.get(offset++);
        if (this.tableID != tableID) {
          Log.d(TAG, "unexepected tableID: " + tableID + " != " + this.tableID);
        }
        sectionLength= ((packet.get(offset++) & 0xf) << 8) | packet.get(offset++);
        if (sectionLength > section.length()) {
          section.resize(sectionLength * 2);
        }
        sectionWriteOffset = 0;
      }
      int copy = Math.min(sectionLength - sectionWriteOffset, dataPosition - offset);

      System.arraycopy(packet.array(), offset, section.array(), sectionWriteOffset, copy);
      sectionWriteOffset += copy;
      if (sectionWriteOffset == sectionLength) {
        handleSection(section, sectionLength);
        sectionLength = 0;
      }
    }

    abstract protected void handleSection(UnsignedByteArray data, int dataLength);
  }

  class PMTHandler extends SectionHandler {
    static final int STREAM_TYPE_AAC_ADTS = 0xf;
    static final int STREAM_TYPE_H264 = 0x1b;
    static final int STREAM_TYPE_MPEG_AUDIO = 0x3;

    public List<Stream> streams;

    class Stream {
      int type;
      int pid;
    }

    public PMTHandler(int pid) {
      super(pid, 2);
      streams = new ArrayList<Stream>();
    }

    @Override
    protected void handleSection(UnsignedByteArray data, int dataLength) {
      // start from 5 to skip transport_stream_id, version_number, current_next_indicator, etc.
      int i = 7;
      int program_info_length = ((data.get(i) & 0xf) << 8) | data.get(i+1);
      i += 2 + program_info_length;
      while (i < dataLength - 4) {
        Stream stream = new Stream();
        stream.type = data.get(i);
        i++;
        stream.pid = ((data.get(i) & 0x1f) << 8) | data.get(i+1);
        i+=2;
        int ES_info_length = ((data.get(i) & 0xf) << 8) | data.get(i+1);
        i+=2;
        i += ES_info_length;
        streams.add(stream);
      }

      PESHandler audio_handler = null;
      PESHandler video_handler = null;
      PESHandler handler;
      for (Stream s: streams) {
        handler = null;
        if (audio_handler == null && (s.type == STREAM_TYPE_AAC_ADTS || s.type == STREAM_TYPE_MPEG_AUDIO)) {
          audioStreamType = s.type;
          audio_handler = new PESHandler(s.pid, TYPE_AUDIO);
          handler = audio_handler;
          Log.d(TAG, String.format("audio found on pid %04x", s.pid));

          // XXX: not nice
          if (audioStreamType == STREAM_TYPE_MPEG_AUDIO) {
            unitStartNotSignalled = true;
          }
        } else if (video_handler == null && s.type == STREAM_TYPE_H264) {
          video_handler = new PESHandler(s.pid, TYPE_VIDEO);
          handler = video_handler;
          Log.d(TAG, String.format("video found on pid %04x", s.pid));
        }
        if (handler != null) {
          activePayloadHandlers.put(s.pid, handler);
          activePESHandlers.add(handler);
        }
      }

      // do not listen to future PMT updates
      activePayloadHandlers.remove(this.pid);
    }
  }

  class PATHandler extends SectionHandler {
    class Program {
      int number;
      int pmt_pid;
    }

    public final List<Program> programs;

    public PATHandler(int pid) {
      super(pid, 0);
      programs = new ArrayList<Program>();
    }

    @Override
    public void handleSection(UnsignedByteArray data, int dataLength) {
      // start from 5 to skip transport_stream_id, version_number, current_next_indicator, etc.
      // stop at length - 4 to skip crc
      for (int i = 5; i < dataLength - 4; ){
        Program program = new Program();
        program.number = (data.get(i) << 8) - data.get(i+1);
        i += 2;
        program.pmt_pid = ((data.get(i)& 0x1f) << 8) | data.get(i+1);
        i += 2;
        Log.d(TAG, String.format("found PMT pid: %04x", program.pmt_pid));
        programs.add(program);
      }

      if (programs.size() > 0) {
        // use first program
        Program program = programs.get(0);
        PMTHandler payloadHandler = new PMTHandler(program.pmt_pid);
        activePayloadHandlers.put(program.pmt_pid, payloadHandler);
      }

      // do not listen to PAT updates
      activePayloadHandlers.remove(this.pid);
    }
  }

  public TSExtractor(DataSource dataSource) {
    packet = new UnsignedByteArray(200 * 188);
    activePayloadHandlers = new SparseArray<PayloadHandler>(4);
    PayloadHandler payloadHandler = (PayloadHandler)new PATHandler(0);
    activePayloadHandlers.put(0, payloadHandler);
    this.dataSource = dataSource;
    activePESHandlers = new ArrayList<PESHandler>();

  }

  private boolean fillData() throws ParserException {
    int offset = 0;
    int length = packet.length();

    if (dataIncompletePosition != 0) {
      // we need multiple of 188 bytes
      offset = dataIncompletePosition;
      length = packet.length() - dataIncompletePosition;
    }

    int ret = 0;
    try {
      ret = dataSource.read(packet.array(), offset, length);
    } catch (IOException e) {
      e.printStackTrace();
      throw new ParserException("IOException");
    }
    if (ret == -1) {
      if ((dataSize % 188) != 0) {
        Log.d(TAG, String.format("TS file is not a multiple of 188 bytes (%d)?", dataSize));
        dataSize = 188 * ((dataSize + 187) / 188);
      }
      dataSourceFinished = true;
      return false;
    } else {
      offset += ret;
      if ((offset % 188) != 0) {
        dataIncompletePosition = offset;
      } else {
        dataSize = offset;
        dataIncompletePosition = 0;
        dataPosition = 0;
      }
    }

    return true;
  }

  /**
   *
   * @return true if it wants to ba called again
   * @throws ParserException
   */
  private void readOnePacket() throws ParserException
  {
    if (dataPosition == dataSize || (dataIncompletePosition != 0)) {
      fillData();
      if (dataSourceFinished) {
        if (endOfStream == false) {
          endOfStream = true;
        }
        return;
      } else if (dataPosition == dataSize || (dataIncompletePosition != 0)) {
        return;
      }
    }

    int offset =  dataPosition;
    dataPosition += 188;

    if (packet.get(offset) != 0x47) {
      packetWriteOffset = 0;
      throw new ParserException("bad sync byte: " + packet.get(offset+0));
    }

    boolean unit_start = (packet.get(offset+1) & 0x40) != 0;
    int pid = (packet.get(offset+1) & 0x1f) << 8;
    pid |= packet.get(offset+2);

    int cc_counter = packet.get(offset+3) & 0xf;

    PayloadHandler payloadHandler = activePayloadHandlers.get(pid);
    if (payloadHandler == null) {
      // skip packet
      packetWriteOffset = 0;
      return;
    }

    int expected_cc_counter = (payloadHandler.cc_counter + 1) & 0xf;
    if (expected_cc_counter != cc_counter) {
      Log.d(TAG, "cc_error: " + payloadHandler.cc_counter + " -> " + cc_counter);
    }
    payloadHandler.cc_counter = cc_counter;

    boolean adaptation_field_exists = (packet.get(offset+3) & 0x20) != 0;

    int payload_offset = offset+4;

    if (adaptation_field_exists) {
      payload_offset += packet.get(offset+4) + 1;
    }

    payloadHandler.handlePayload(packet, payload_offset, unit_start);
    packetWriteOffset = 0;
  }

  public Sample read()
          throws ParserException {

    TraceUtil.beginSection("TSExtractor::read");
    while (outSample == null) {
      if (!endOfStream) {
        readOnePacket();
      } else {
        for (PESHandler pesh : activePESHandlers) {
          if (pesh.currentSample != null) {
            Sample s = pesh.currentSample;
            pesh.currentSample = null;
            return s;
          }
        }
        return null;
      }
    }

    Sample s = outSample;
    outSample = null;
    return s;
  }
}