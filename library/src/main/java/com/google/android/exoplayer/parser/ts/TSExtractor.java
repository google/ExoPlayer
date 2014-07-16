package com.google.android.exoplayer.parser.ts;

import android.media.MediaExtractor;
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

    private UnsignedByteArray packet;
    int packetWriteOffset;

    private final SparseArray<PayloadHandler> activePayloadHandlers;

    private SampleHolder holder;
    private boolean holderValid;

    static class UnsignedByteArray {
        byte[] array;
        public UnsignedByteArray(int length) {
            array = new byte[length];
        }

        public void resize(int newLength) {
            byte [] newArray = new byte[newLength];
            System.arraycopy(array, 0, newArray, 0, array.length);
        }

        public int length() {
            return array.length;
        }

        public int get(int index) {
            return (int)array[index] & 0xff;
        }
        public int getShort(int index) {
            return get(index) << 8 | get(index + 1);
        }
        public byte[] array() {
            return array;
        }
    }

    static abstract class PayloadHandler {
        public int cc_counter;

        /**
         *
         * @param packet: the TS packet with position set to the beginning of the payload
         * @param payloadStart
         * @param unit_start
         */
        abstract public void handlePayload(UnsignedByteArray packet, int payloadStart, boolean unit_start);
    }

    class PESHandler extends PayloadHandler {
        UnsignedByteArray temporaryPayload;
        int temporaryPayloadLength;
        long temporaryPts;

        public PESHandler() {
            temporaryPayload = new UnsignedByteArray(188);
        }

        @Override
        public void handlePayload(UnsignedByteArray packet, int payloadStart, boolean unit_start) {
            int offset = payloadStart;
            if (unit_start) {
                // output previous packet
                if (holder.data.position() > 0) {
                    holder.timeUs = temporaryPts * 1000 / 45;
                    // XXX: remove
                    holder.timeUs -= 10 * 1000000;

                    //Log.d(TAG, "timestamp:" + holder.timeUs/1000);

                    holder.flags = MediaExtractor.SAMPLE_FLAG_SYNC;
                    holderValid = true;
                }

                int[] prefix = new int[3];
                prefix[0] = packet.get(offset++);
                prefix[1] = packet.get(offset++);
                prefix[2] = packet.get(offset++);
                if (prefix[0] != 0 || prefix[1] != 0 || prefix[2] != 1 ) {
                    Log.d(TAG, String.format("bad start code: 0x%02x%02x%02x", prefix[0], prefix[1], prefix[2]));
                }
                // skip stream id
                offset++;
                // length should be 0 for my use case
                int length = packet.getShort(offset);
                offset += 2;
                if (length != 0) {
                    Log.d(TAG, "PES length != 0: " + length);
                }

                // skip some stuff
                offset++;
                int flags = packet.get(offset++);
                int headerDataLength = packet.get(offset++);
                int fixedOffset = offset;

                if ((flags & 0x80) == 0x80) {
                    temporaryPts = (long)(packet.get(offset++) & 0x0e) << 28;
                    temporaryPts |= (packet.get(offset++)) << 21;
                    temporaryPts |= (packet.get(offset++) & 0xfe) << 13;
                    temporaryPts |= (packet.get(offset++)) << 6;
                    temporaryPts |= (packet.get(offset++) & 0xfe) >> 2;

                }
                if ((flags & 0x40) == 0x40) {
                    // DTS
                    offset += 5;
                }

                offset = fixedOffset + headerDataLength;
                temporaryPayloadLength = 188 - offset;
                // remember this payload for later
                System.arraycopy(packet.array(), offset, temporaryPayload.array(), 0, temporaryPayloadLength);
                return;
            }

            if (temporaryPayloadLength > 0) {
                holder.data.put(temporaryPayload.array(), 0, temporaryPayloadLength);
                temporaryPayloadLength = 0;
            }

            holder.data.put(packet.array(), offset, 188 - offset);
        }
    }

    abstract class SectionHandler extends PayloadHandler {
        protected int tableID;
        protected UnsignedByteArray section;
        int sectionLength;
        int sectionWriteOffset;

        public SectionHandler(int tableID) {
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
                if (audio_handler == null && s.type == STREAM_TYPE_AAC_ADTS) {
                    audio_handler = new PESHandler();
                    // XXX: uncomment when audio is needed
                    // handler = audio_handler;
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
                PMTHandler payloadHandler = new PMTHandler();
                activePayloadHandlers.put(program.pmt_pid, payloadHandler);
            }
        }
    }

    public TSExtractor() {
        packet = new UnsignedByteArray(188);
        activePayloadHandlers = new SparseArray<PayloadHandler>(4);
        PayloadHandler payloadHandler = (PayloadHandler)new PATHandler();
        activePayloadHandlers.put(0, payloadHandler);
    }

    private int readOnePacket(NonBlockingInputStream inputStream, SampleHolder out) throws ParserException
    {
        int result;
        int remaining = 188 - packetWriteOffset;

        result = inputStream.read(packet.array(), 0, remaining);
        if (result == -1) {
            return RESULT_END_OF_STREAM;
        } else if (result != remaining) {
            return  RESULT_NEED_MORE_DATA;
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
            return RESULT_NEED_MORE_DATA;
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

        holder = out;
        payloadHandler.handlePayload(packet, payload_offset, unit_start);
        packetWriteOffset = 0;
        if (holderValid) {
            holderValid = false;
            return RESULT_READ_SAMPLE_FULL;
        } else {
            return RESULT_NEED_MORE_DATA;
        }
    }

    public int read(NonBlockingInputStream inputStream, SampleHolder out)
            throws ParserException {
        // XXX: this breaks buffering as the MediaCodecTrack stops feeding its inputBuffers and
        // ExoPlayerImplInternal believes the renderer is not ready
       // return readOnePacket(inputStream, out);

        int ret = RESULT_NEED_MORE_DATA;
        while (ret == RESULT_NEED_MORE_DATA) {
            ret = readOnePacket(inputStream,out);
        }
        return ret;
    }
}