package com.google.android.exoplayer.parser.ts;

import android.util.Log;
import android.util.SparseArray;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class TSExtractor {
    private static final String TAG = "TSExtractor";

    /**
     * An attempt to read from the input stream returned 0 bytes of data.
     */
    public static final int RESULT_NEED_MORE_DATA = 1;
    /**
     * The end of the input stream was reached.
     */
    public static final int RESULT_END_OF_STREAM = 2;
    /**
     * A media sample was read.
     */
    public static final int RESULT_READ_SAMPLE_FULL = 3;

    private ByteBuffer packet;
    private final SparseArray<PayloadHandler> activePayloadHandlers;

    static abstract class PayloadHandler {
        public int cc_counter;

        abstract public void handlePayloadData(byte[] data);
    }

    abstract class SectionHandler extends PayloadHandler {
        protected int table_id;
        protected int section_length;
        protected ByteBuffer section;

        public SectionHandler(int table_id) {
            this.table_id = table_id;
        }

        @Override
        public void handlePayloadData(byte[] data) {
            int offset = 0;
            if (section_length == 0) {
                int pointer_field = data[0];
                // XXX: what if pointer_field is != 0 ?
                if (table_id != data[1]) {
                    Log.d(TAG, "unexepected table_id: " + data[i] + " != " + table_id);
                }
                section_length = ((data[1] & 0xf) << 8) | data[2];
                if (section.capacity() < section_length) {
                    section = ByteBuffer.allocate(section_length);
                }
            }
            int copy = Math.min(section_length - section.position(), data.length - offset);

            section.put(data, offset, copy);

            if (section.position() == section_length) {
                handleSectionData(section.array());
                section.clear();
                section_length = 0;
            }
        }

        abstract protected void handleSectionData(byte[] data);
    }

    class PESHandler extends PayloadHandler {

        @Override
        public void handlePayloadData(byte[] data) {

        }
    }

    class PMTHandler extends SectionHandler {
        static final int STREAM_TYPE_AAC_ADTS = 0xf;
        static final int STREAM_TYPE_H264 = 0x1b;

        public List<Stream> streams;

        class Stream {
            int type;
            int pid;
        }

        public PMTHandler() {
            super(2);
            streams = new ArrayList<Stream>();
        }

        @Override
        protected void handleSectionData(byte[] data) {
            // start from 5 to skip transport_stream_id, version_number, current_next_indicator, etc.
            int i = 7;
            int program_info_length = ((data[i] & 0xf) << 8) | data[i+1];
            i += 2 + program_info_length;
            while (i < data.length - 4) {
                Stream stream = new Stream();
                stream.type = data[i];
                i++;
                stream.pid = ((data[i] & 0x1f) << 8) | data[i+1];
                i+=2;
                int ES_info_length = ((data[i] & 0xf) << 8) | data[i+1];
                i += ES_info_length;
                streams.add(stream);
            }

            PESHandler audio_handler = null;
            PESHandler video_handler = null;
            PESHandler handler;
            for (Stream s: streams) {
                handler = null;
                if (audio_handler == null && s.type == STREAM_TYPE_AAC_ADTS) {
                    audio_handler = new PESHandler();
                    handler = audio_handler;
                    Log.d(TAG, String.format("audio found on pid %04x", s.pid));
                } else if (video_handler == null && s.type == STREAM_TYPE_H264) {
                    video_handler = new PESHandler();
                    handler = video_handler;
                    Log.d(TAG, String.format("video found on pid %04x", s.pid));
                }
                if (handler != null) {
                    activePayloadHandlers.put(s.pid, handler);
                }
            }
        }
    }

    class PATHandler extends SectionHandler {
        class Program {
            int number;
            int pmt_pid;
        }

        public final List<Program> programs;

        public PATHandler() {
            super(0);
            programs = new ArrayList<Program>();
        }

        @Override
        public void handleSectionData(byte[] data) {
            // start from 5 to skip transport_stream_id, version_number, current_next_indicator, etc.
            // stop at length - 4 to skip crc
            for (int i = 5; i < data.length - 4; ){
                Program program = new Program();
                program.number = (data[i] << 8) - data[i+1];
                i += 2;
                program.pmt_pid = ((data[i]& 0x1f) << 8) | data[i+1];
                i += 2;
                Log.d(TAG, String.format("found PMT pid: %04x", program.pmt_pid));
                programs.add(program);
            }

            if (programs.size() > 0) {
                // use first program
                Program program = programs.get(0);
                PMTHandler payloadHandler = new PMTHandler();
                activePayloadHandlers.put(program.pmt_pid, payloadHandler);
            }
        }
    }

    public TSExtractor() {
        packet = ByteBuffer.allocate(188);
        packet.clear();
        activePayloadHandlers = new SparseArray<PayloadHandler>(4);
        PayloadHandler payloadHandler = (PayloadHandler)new PATHandler();
        activePayloadHandlers.put(0, payloadHandler);
    }

    public int read(NonBlockingInputStream inputStream, SampleHolder out)
            throws ParserException {
        int result;
        int remaining = 188 - packet.position();

        result = inputStream.read(packet, remaining);
        if (result == -1) {
            return RESULT_END_OF_STREAM;
        } else if (result != remaining) {
            return  RESULT_NEED_MORE_DATA;
        }

        byte[] data = packet.array();

        if (data[0] != 0x47) {
            throw new ParserException("bad sync byte: " + data[0]);
        }

        int pid = (data[1] & 0x1f) << 8;
        pid |= data[2];

        int cc_counter = data[3] & 0xf;

        PayloadHandler payloadHandler = activePayloadHandlers.get(pid);
        if (payloadHandler == null) {
            return RESULT_NEED_MORE_DATA;
        }

        int expected_cc_counter = (payloadHandler.cc_counter + 1) & 0xf;
        if (expected_cc_counter != cc_counter) {
            Log.d(TAG, "cc_error: " + payloadHandler.cc_counter + " -> " + cc_counter);
        }
        payloadHandler.cc_counter = cc_counter;

        boolean adaptation_field_exists = (data[3] & 0x20) != 0;

        int payload_start = 4;

        if (adaptation_field_exists) {
            payload_start += data[4] + 1;
        }
    }
}