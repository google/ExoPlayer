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

    private SampleHolder holder;
    private boolean holderValid;

    static abstract class PayloadHandler {
        public int cc_counter;

        /**
         *
         * @param packet: the TS packet with position set to the beginning of the payload
         * @param unit_start
         */
        abstract public void handlePayload(ByteBuffer packet, boolean unit_start);
    }

    class PESHandler extends PayloadHandler {
        byte [] temporaryPayload;
        int temporaryPayloadPosition;
        int temporaryPts;

        public PESHandler() {
            temporaryPayload = new byte[188];
        }
        @Override
        public void handlePayload(ByteBuffer packet, boolean unit_start) {
            if (unit_start) {
                // output previous packet
                if (holder.data.position() > 0) {
                    holder.timeUs = (long)temporaryPts * 1000 / 45;
                    holderValid = true;
                }

                byte[] prefix = new byte[3];
                packet.get(prefix, 0, 3);
                if (prefix[0] != 0 || prefix[1] != 0 || prefix[2] != 1 ) {
                    Log.d(TAG, String.format("bad start code: 0x%02x%02x%02x", prefix[0], prefix[1], prefix[2]));
                }
                // skip stream id
                packet.get();
                // length should be 0 for my use case
                int length = packet.getShort();
                if (length != 0) {
                    Log.d(TAG, "PES length != 0: " + length);
                }

                // skip some stuff
                packet.get();
                int flags = packet.get();
                int headerDataLength = packet.get();

                if ((flags & 0x80) == 0x80) {
                    temporaryPts = (packet.get() & 0xd) << 29;
                    temporaryPts |= (packet.get()) << 21;
                    temporaryPts |= (packet.get() & 0xfd) << 14;
                    temporaryPts |= (packet.get()) << 6;
                    temporaryPts |= (packet.get() & 0xfd) >> 1;
                }
                if ((flags & 0x40) == 0x40) {
                    // DTS
                    packet.get();
                    packet.get();
                    packet.get();
                    packet.get();
                }

                packet.position(packet.position() + headerDataLength);
                // remember this payload for later
                int remaining = packet.remaining();
                packet.get(temporaryPayload, 0, remaining);
                temporaryPayloadPosition = remaining;
                return;
            }

            if (temporaryPayloadPosition > 0) {
                holder.data.put(temporaryPayload,0,temporaryPayloadPosition);
                temporaryPayloadPosition = 0;
            }

            holder.data.put(packet);
        }
    }

    abstract class SectionHandler extends PayloadHandler {
        protected int tableID;
        protected int sectionLength;
        protected byte[] section;
        protected int sectionPosition;

        public SectionHandler(int tableID) {
            this.tableID = tableID;
            section = new byte[2*1024];
        }

        @Override
        public void handlePayload(ByteBuffer packet, boolean unit_start) {
            if (sectionLength == 0) {
                int pointer_field = packet.get();
                // XXX: what if pointer_field is != 0 ?
                int tableID = packet.get();
                if (this.tableID != tableID) {
                    Log.d(TAG, "unexepected tableID: " + tableID + " != " + this.tableID);
                }
                sectionLength = ((packet.get() & 0xf) << 8) | packet.get();
                sectionPosition = 0;
            }
            int copy = Math.min(sectionLength - sectionPosition, packet.remaining());

            if (sectionPosition + copy > section.length) {
                byte[] newSection = new byte[2*(sectionPosition + copy)];
                System.arraycopy(section, 0, newSection, 0, sectionPosition);
            }

            packet.get(section, sectionPosition, copy);
            sectionPosition += copy;
            if (sectionPosition == sectionLength) {
                handleSection(section);
                sectionLength = 0;
            }
        }

        abstract protected void handleSection(byte[] data);
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
        protected void handleSection(byte[] data) {
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
                    // XXX: uncomment when audio is needed
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
        public void handleSection(byte[] data) {
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

        boolean unit_start = (data[1] & 0x40) != 0;
        int pid = (data[1] & 0x1f) << 8;
        pid |= data[2];

        int cc_counter = data[3] & 0xf;

        PayloadHandler payloadHandler = activePayloadHandlers.get(pid);
        if (payloadHandler == null) {
            // skip packet
            return RESULT_NEED_MORE_DATA;
        }

        int expected_cc_counter = (payloadHandler.cc_counter + 1) & 0xf;
        if (expected_cc_counter != cc_counter) {
            Log.d(TAG, "cc_error: " + payloadHandler.cc_counter + " -> " + cc_counter);
        }
        payloadHandler.cc_counter = cc_counter;

        boolean adaptation_field_exists = (data[3] & 0x20) != 0;

        int payload_offset = 4;

        if (adaptation_field_exists) {
            payload_offset += data[4] + 1;
        }

        holder = out;
        packet.position(payload_offset);
        payloadHandler.handlePayload(packet, unit_start);
        if (holderValid) {
            holderValid = false;
            return RESULT_READ_SAMPLE_FULL;
        } else {
            return RESULT_NEED_MORE_DATA;
        }
    }
}