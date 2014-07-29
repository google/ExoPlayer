package com.google.android.exoplayer.parser.ts;

import android.media.MediaExtractor;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.chunk.HLSExtractor;
import com.google.android.exoplayer.parser.aac.AACExtractor;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.TraceUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class TSExtractor extends HLSExtractor {
  private static final String TAG = "TSExtractor";

  private final NonBlockingInputStream inputStream;

  private MediaFormat audioMediaFormat;

  private UnsignedByteArray packet;
  int packetWriteOffset;

  private final SparseArray<PayloadHandler> activePayloadHandlers;
  private final ArrayList<PESHandler> activePESHandlers;

  private LinkedList<Sample> recycledSampleList;
  private ArrayList<LinkedList<Sample>> sampleLists;
  private boolean endOfStream;
  int audioStreamType;
  private boolean unitStartNotSignalled;

  static class Sample {
    public UnsignedByteArray data;
    public int position;
    public long timeUs;

    public Sample() {
      data = new UnsignedByteArray(2*1024);
    }
  }

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

  Sample getSample() {
    if (recycledSampleList.size() > 0) {
      return recycledSampleList.removeFirst();
    } else {
      return new Sample();
    }
  }

  void releaseSample(Sample s) {
    recycledSampleList.add(s);
  }

  class PESHandler extends PayloadHandler {
    private Sample currentSample;
    private LinkedList<Sample> list;
    private int length;

    public PESHandler(int pid, LinkedList<Sample> list) {
      super.init(pid);
      this.list = list;
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
          list.add(currentSample);
          if (length != 0 && length != currentSample.position) {
            Log.d(TAG, "PES length " + currentSample.position + " != " + length);
          }
          currentSample = null;
        }

        currentSample = getSample();

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
        System.arraycopy(packet.array(), offset, currentSample.data.array(), 0, 188 - offset);
        currentSample.position = 188 - offset;
        return;
      }

      if (currentSample.position + 188 > currentSample.data.length()) {
        currentSample.data.resize(2*(currentSample.position + 188));
      }
      System.arraycopy(packet.array(), offset, currentSample.data.array(), currentSample.position, 188 - offset);
      currentSample.position += 188 - offset;
    }

    public void terminate() {
      if (currentSample != null) {
        list.add(currentSample);
      }
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
      int copy = Math.min(sectionLength - sectionWriteOffset, 188 - offset);

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
          audio_handler = new PESHandler(s.pid, sampleLists.get(TYPE_AUDIO));
          handler = audio_handler;
          Log.d(TAG, String.format("audio found on pid %04x", s.pid));

          // XXX: not nice
          if (audioStreamType == STREAM_TYPE_MPEG_AUDIO) {
            unitStartNotSignalled = true;
          }
        } else if (video_handler == null && s.type == STREAM_TYPE_H264) {
          video_handler = new PESHandler(s.pid, sampleLists.get(TYPE_VIDEO));
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

  public TSExtractor(NonBlockingInputStream inputStream) {
    packet = new UnsignedByteArray(188);
    activePayloadHandlers = new SparseArray<PayloadHandler>(4);
    PayloadHandler payloadHandler = (PayloadHandler)new PATHandler(0);
    activePayloadHandlers.put(0, payloadHandler);
    recycledSampleList = new LinkedList<Sample>();
    sampleLists = new ArrayList<LinkedList<Sample>>();
    sampleLists.add(new LinkedList<Sample>());
    sampleLists.add(new LinkedList<Sample>());
    this.inputStream = inputStream;
    activePESHandlers = new ArrayList<PESHandler>();

  }

  /**
   *
   * @return true if it wants to ba called again
   * @throws ParserException
   */
  private boolean readOnePacket() throws ParserException
  {
    int result;
    int remaining = 188 - packetWriteOffset;

    result = inputStream.read(packet.array(), packetWriteOffset, remaining);
    if (result == -1) {
      if (endOfStream == false) {
        for (PESHandler h : activePESHandlers) {
          h.terminate();
        }
        endOfStream = true;
      }
      return false;
    }

    packetWriteOffset += result;

    if (result != remaining) {
      packetWriteOffset = 0;
      return false;
    }

    if (packet.get(0) != 0x47) {
      packetWriteOffset = 0;
      throw new ParserException("bad sync byte: " + packet.get(0));
    }

    boolean unit_start = (packet.get(1) & 0x40) != 0;
    int pid = (packet.get(1) & 0x1f) << 8;
    pid |= packet.get(2);

    int cc_counter = packet.get(3) & 0xf;

    PayloadHandler payloadHandler = activePayloadHandlers.get(pid);
    if (payloadHandler == null) {
      // skip packet
      packetWriteOffset = 0;
      return true;
    }

    int expected_cc_counter = (payloadHandler.cc_counter + 1) & 0xf;
    if (expected_cc_counter != cc_counter) {
      Log.d(TAG, "cc_error: " + payloadHandler.cc_counter + " -> " + cc_counter);
    }
    payloadHandler.cc_counter = cc_counter;

    boolean adaptation_field_exists = (packet.get(3) & 0x20) != 0;

    int payload_offset = 4;

    if (adaptation_field_exists) {
      payload_offset += packet.get(4) + 1;
    }

    payloadHandler.handlePayload(packet, payload_offset, unit_start);
    packetWriteOffset = 0;
    return true;
  }

  static public double getDuration(Sample s, int sampleRate) {
    int position = 0;
    int frameCount = 0;
    UnsignedByteArray d = s.data;

    while (position < s.position) {
      if (d.get(position + 0) != 0xff || ((d.get(position + 1) & 0xf0) != 0xf0)) {
        Log.d(TAG, "no ADTS sync");
      }

      int frameLength = (s.data.get(position + 3) & 0x3) << 11;
      frameLength += (s.data.get(position + 4) << 3);
      frameLength += (s.data.get(position + 5) & 0xe0) >> 5;
      //Log.d(TAG, "frame length: " + frameLength);

      position += frameLength;
      frameCount ++;
    }
    if (position != s.position) {
      Log.d(TAG, "bad frame " + position + " != " + s.position);
    }

    return (1024 * (double)frameCount * 1000000) / sampleRate;
  }

  public int read(int type, SampleHolder out)
          throws ParserException {

    LinkedList<Sample> list = sampleLists.get(type);
    LinkedList<Sample> otherList = sampleLists.get(1 - type);

    TraceUtil.beginSection("TSExtractor::read");
    // XXX: should I check that the otherList does not grow too much ?
    int packets = 0;
    long start = SystemClock.uptimeMillis();
    while (list.size() == 0 && (SystemClock.uptimeMillis() - start) < 10) {
      if (!readOnePacket()) {
        break;
      }
      packets++;
    }
        /*String debugString = String.format("processed %4d packets in %4d ms [A %4d][V %4d]", packets, (SystemClock.uptimeMillis() - start),
                sampleLists.get(TYPE_AUDIO).size(), sampleLists.get(TYPE_VIDEO).size());
        Log.d(TAG, debugString);*/
    TraceUtil.endSection();

    if (list.size() > 0) {
      Sample s = list.get(0);
      if (type == TYPE_AUDIO && audioMediaFormat == null) {
        if (audioStreamType == PMTHandler.STREAM_TYPE_AAC_ADTS) {
          AACExtractor.ADTSHeader h = new AACExtractor.ADTSHeader();
          h.update(s.data, 0);
          audioMediaFormat = h.toMediaFormat();
        } else {
          audioMediaFormat = MediaFormat.createAudioFormat(MimeTypes.AUDIO_MPEG, -1, 2, 44100, null);
        }
      }

      if (out.data != null) {
        out.data.put(s.data.array(), 0, s.position);
        out.timeUs = s.timeUs;
        out.flags = MediaExtractor.SAMPLE_FLAG_SYNC;
      }
      list.removeFirst();
      releaseSample(s);

      return RESULT_READ_SAMPLE_FULL;
    } else {
      if (endOfStream) {
        return RESULT_END_OF_STREAM;
      } else {
        return RESULT_NEED_MORE_DATA;
      }
    }
  }

  public MediaFormat getAudioMediaFormat() {
    SampleHolder holder = new SampleHolder(false);
    while(audioMediaFormat == null) {
      try {
        if (read(TYPE_AUDIO, holder) == RESULT_END_OF_STREAM) {
          return null;
        }
      } catch (ParserException e) {
        e.printStackTrace();
        return null;
      }
    }

    return audioMediaFormat;
  }

  public boolean isReadFinished() {
    if (endOfStream == false) {
      return false;
    }
    for (LinkedList<Sample> list : sampleLists) {
      if (list.size() > 0)
        return false;
    }

    return true;
  }
}