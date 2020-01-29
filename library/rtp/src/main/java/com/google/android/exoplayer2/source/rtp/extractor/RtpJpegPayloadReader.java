/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtp.extractor;

import android.util.SparseArray;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtp.format.RtpVideoPayload;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TrackIdGenerator;

import java.util.Arrays;


/**
 * Extracts individual JPEG frames from RTP payload for JPEG-compressed video
 */
/*package*/ final class RtpJpegPayloadReader implements RtpPayloadReader {

    private static final byte[] ZIG_ZAG = {
        0, 1, 8, 16, 9, 2, 3, 10,
        17, 24, 32, 25, 18, 11, 4, 5,
        12, 19, 26, 33, 40, 48, 41, 34,
        27, 20, 13, 6, 7, 14, 21, 28,
        35, 42, 49, 56, 57, 50, 43, 36,
        29, 22, 15, 23, 30, 37, 44, 51,
        58, 59, 52, 45, 38, 31, 39, 46,
        53, 60, 61, 54, 47, 55, 62, 63
    };

    /*
     * Table K.1 from JPEG spec, as defined in RFC 2435 Appendix A
     */
    private static final byte[] JPEG_LUMA_QUANTIZER = {
        16, 11, 10, 16, 24, 40, 51, 61,
        12, 12, 14, 19, 26, 58, 60, 55,
        14, 13, 16, 24, 40, 57, 69, 56,
        14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68, 109, 103, 77,
        24, 35, 55, 64, 81, 104, 113, 92,
        49, 64, 78, 87, 103, 121, 120, 101,
        72, 92, 95, 98, 112, 100, 103, 99
    };

    /*
     * Table K.2 from JPEG spec, as defined in RFC 2435 Appendix A
     */
    private static final byte[] JPEG_CHROMA_QUANTIZER = {
        17, 18, 24, 47, 99, 99, 99, 99,
        18, 21, 26, 66, 99, 99, 99, 99,
        24, 26, 56, 99, 99, 99, 99, 99,
        47, 66, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99
    };


    /*
     * Table-specification data for JPEG body, as defined in RFC 2435 Appendix A
     */
    private static final byte[] LUM_DC_CODELENS = {
        0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0
    };

    private static final byte[] LUM_DC_SYMBOLS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
    };

    private static final byte[] LUM_AC_CODELENS = {
        0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d
    };

    private static final byte[] LUM_AC_SYMBOLS = {
        0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12,
        0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
        0x22, 0x71, 0x14, 0x32, (byte) 0x81, (byte) 0x91, (byte) 0xa1, 0x08,
        0x23, 0x42, (byte) 0xb1, (byte) 0xc1, 0x15, 0x52, (byte) 0xd1, (byte) 0xf0,
        0x24, 0x33, 0x62, 0x72, (byte) 0x82, 0x09, 0x0a, 0x16,
        0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
        0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
        0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
        0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
        0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
        0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79,
        0x7a, (byte) 0x83, (byte) 0x84, (byte) 0x85, (byte) 0x86, (byte) 0x87, (byte) 0x88, (byte) 0x89,
        (byte) 0x8a, (byte) 0x92, (byte) 0x93, (byte) 0x94, (byte) 0x95, (byte) 0x96, (byte) 0x97, (byte) 0x98,
        (byte) 0x99, (byte) 0x9a, (byte) 0xa2, (byte) 0xa3, (byte) 0xa4, (byte) 0xa5, (byte) 0xa6, (byte) 0xa7,
        (byte) 0xa8, (byte) 0xa9, (byte) 0xaa, (byte) 0xb2, (byte) 0xb3, (byte) 0xb4, (byte) 0xb5, (byte) 0xb6,
        (byte) 0xb7, (byte) 0xb8, (byte) 0xb9, (byte) 0xba, (byte) 0xc2, (byte) 0xc3, (byte) 0xc4, (byte) 0xc5,
        (byte) 0xc6, (byte) 0xc7, (byte) 0xc8, (byte) 0xc9, (byte) 0xca, (byte) 0xd2, (byte) 0xd3, (byte) 0xd4,
        (byte) 0xd5, (byte) 0xd6, (byte) 0xd7, (byte) 0xd8, (byte) 0xd9, (byte) 0xda, (byte) 0xe1, (byte) 0xe2,
        (byte) 0xe3, (byte) 0xe4, (byte) 0xe5, (byte) 0xe6, (byte) 0xe7, (byte) 0xe8, (byte) 0xe9, (byte) 0xea,
        (byte) 0xf1, (byte) 0xf2, (byte) 0xf3, (byte) 0xf4, (byte) 0xf5, (byte) 0xf6, (byte) 0xf7, (byte) 0xf8,
        (byte) 0xf9, (byte) 0xfa
    };

    private static final byte[] CHM_DC_CODELENS = {
        0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0
    };

    private static final byte[] CHM_DC_SYMBOLS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
    };

    private static final byte[] CHM_AC_CODELENS = {
        0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77
    };

    private static final byte[] CHM_AC_SYMBOLS = {
        0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21,
        0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
        0x13, 0x22, 0x32, (byte) 0x81, 0x08, 0x14, 0x42, (byte) 0x91,
        (byte) 0xa1, (byte) 0xb1, (byte) 0xc1, 0x09, 0x23, 0x33, 0x52, (byte) 0xf0,
        0x15, 0x62, 0x72, (byte) 0xd1, 0x0a, 0x16, 0x24, 0x34,
        (byte) 0xe1, 0x25, (byte) 0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26,
        0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38,
        0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
        0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
        0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
        0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78,
        0x79, 0x7a, (byte) 0x82, (byte) 0x83, (byte) 0x84, (byte) 0x85, (byte) 0x86, (byte) 0x87,
        (byte) 0x88, (byte) 0x89, (byte) 0x8a, (byte) 0x92, (byte) 0x93, (byte) 0x94, (byte) 0x95, (byte) 0x96,
        (byte) 0x97, (byte) 0x98, (byte) 0x99, (byte) 0x9a, (byte) 0xa2, (byte) 0xa3, (byte) 0xa4, (byte) 0xa5,
        (byte) 0xa6, (byte) 0xa7, (byte) 0xa8, (byte) 0xa9, (byte) 0xaa, (byte) 0xb2, (byte) 0xb3, (byte) 0xb4,
        (byte) 0xb5, (byte) 0xb6, (byte) 0xb7, (byte) 0xb8, (byte) 0xb9, (byte) 0xba, (byte) 0xc2, (byte) 0xc3,
        (byte) 0xc4, (byte) 0xc5, (byte) 0xc6, (byte) 0xc7, (byte) 0xc8, (byte) 0xc9, (byte) 0xca, (byte) 0xd2,
        (byte) 0xd3, (byte) 0xd4, (byte) 0xd5, (byte) 0xd6, (byte) 0xd7, (byte) 0xd8, (byte) 0xd9, (byte) 0xda,
        (byte) 0xe2, (byte) 0xe3, (byte) 0xe4, (byte) 0xe5, (byte) 0xe6, (byte) 0xe7, (byte) 0xe8, (byte) 0xe9,
        (byte) 0xea, (byte) 0xf2, (byte) 0xf3, (byte) 0xf4, (byte) 0xf5, (byte) 0xf6, (byte) 0xf7, (byte) 0xf8,
        (byte) 0xf9, (byte) 0xfa
    };

    private static final byte[] EOI_MARKER = {(byte) 0xFF, (byte) 0xD9};

    private JpegHeaderReader jpegReader;
    private FragmentedJpegFrame fragmentedJpegFrame;

    private TrackOutput output;

    private boolean completeIndicator;

    private Format format;
    private String formatId;
    private boolean hasOutputFormat;

    private final RtpVideoPayload payloadFormat;
    private final RtpTimestampAdjuster timestampAdjuster;


    public RtpJpegPayloadReader(RtpVideoPayload payloadFormat) {
        this.payloadFormat = payloadFormat;

        fragmentedJpegFrame = new FragmentedJpegFrame();
        jpegReader = new JpegHeaderReader(payloadFormat, fragmentedJpegFrame);
        timestampAdjuster = new RtpTimestampAdjuster(payloadFormat.clockrate());
    }

    @Override
    public void seek() {
        fragmentedJpegFrame.reset();
    }

    @Override
    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator trackIdGenerator) {
        trackIdGenerator.generateNewId();

        output = extractorOutput.track(trackIdGenerator.getTrackId(), C.TRACK_TYPE_VIDEO);
        formatId = trackIdGenerator.getFormatId();

        if (payloadFormat.width() > 0 && payloadFormat.height() > 0) {
            format = Format.createVideoSampleFormat(formatId,
                    payloadFormat.sampleMimeType(), payloadFormat.codecs(), payloadFormat.bitrate(),
                    Format.NO_VALUE, Format.NO_VALUE, Format.NO_VALUE, payloadFormat.framerate(),
                    payloadFormat.buildCodecSpecificData(), null);

            hasOutputFormat = true;
            output.format(format);
        }
    }

    @Override
    public boolean packetStarted(long sampleTimeStamp, boolean completeIndicator,
                                 int sequenceNumber) {
        this.completeIndicator = completeIndicator;

        if (completeIndicator) {
            timestampAdjuster.adjustSampleTimestamp(sampleTimeStamp);
        }

        return true;
    }

    @Override
    public void consume(ParsableByteArray packet) throws ParserException {
        if (jpegReader.consume(packet)) {
            if (completeIndicator) {

                if (!hasOutputFormat && jpegReader.isHasOutputFormat()) {
                    format = Format.createVideoSampleFormat(formatId,
                            payloadFormat.sampleMimeType(), payloadFormat.codecs(),
                            payloadFormat.bitrate(), Format.NO_VALUE, jpegReader.getWidth(),
                            jpegReader.getHeight(), payloadFormat.framerate(),
                            payloadFormat.buildCodecSpecificData(), null);

                    hasOutputFormat = true;
                    output.format(format);
                }

                if (hasOutputFormat) {
                    int length = fragmentedJpegFrame.length;
                    output.sampleData(new ParsableByteArray(fragmentedJpegFrame.data), length);

                    // take the last bytes of the jpeg data to see if there is an EOI marker
                    if (fragmentedJpegFrame.data[length - 2] != (byte) 0xFF &&
                            fragmentedJpegFrame.data[length - 1] != (byte) 0xD9) {
                        // no EOI marker, add one
                        output.sampleData(new ParsableByteArray(EOI_MARKER),2);
                        length += 2;
                    }

                    @C.BufferFlags int flags = C.BUFFER_FLAG_KEY_FRAME;
                    output.sampleMetadata(timestampAdjuster.getSampleTimeUs(), flags, length,
                            0,null);
                }

                fragmentedJpegFrame.reset();
            }

        } else {
            fragmentedJpegFrame.reset();
        }
    }


    private static final class JpegHeaderReader {

        private int width;
        private int height;
        private boolean hasOutputFormat;

        private RtpVideoPayload payloadFormat;
        private FragmentedJpegFrame fragmentedJpegFrame;

        /* cached quantization tables */
        private SparseArray<byte[]> quantizationTables;

        JpegHeaderReader(RtpVideoPayload payloadFormat,
                         FragmentedJpegFrame fragmentedJpegFrame) {
            this.payloadFormat = payloadFormat;
            this.fragmentedJpegFrame = fragmentedJpegFrame;

            if (payloadFormat.width() != Format.NO_VALUE ||
                    payloadFormat.height() != Format.NO_VALUE) {
                hasOutputFormat = true;
            }

            quantizationTables = new SparseArray<>();
        }

        int getWidth() {
            return width;
        }

        int getHeight() {
            return height;
        }

        boolean isHasOutputFormat() {
            return hasOutputFormat;
        }

        boolean consume(ParsableByteArray payload) throws ParserException {
            int dri = 0;
            int length = 0;
            int precision = 0;

            if (payload.bytesLeft() < 8) {
                //Log.w("RtpJpegDep", "Discard packet: jpeg empty payload");
                return false;
            }

            payload.skipBytes(1); // skip type-specific byte

            int fragmentOffset = payload.readUnsignedInt24();
            int type = payload.readUnsignedByte();
            int q = payload.readUnsignedByte();

            width = payload.readUnsignedByte() * 8;
            height = payload.readUnsignedByte() * 8;

            if (width <= 0) {
                width = payloadFormat.width();
            }

            if (height <= 0) {
                height = payloadFormat.height();
            }

            if (width == Format.NO_VALUE || height == Format.NO_VALUE) {
                //Log.w("RtpJpegDep", "Discard packet: Jpeg invalid dimension");
                return false;

            } else if (!hasOutputFormat) {
                hasOutputFormat = true;
            }

            // Restart Marker header is present when using types 64-127
            if (type > 63) {
                if (payload.bytesLeft() < 4) {
                    //Log.w("RtpJpegDep", "Discard packet: jpeg empty payload");
                    return false;
                }

                dri = payload.readUnsignedShort();
                payload.skipBytes(2); // skip F, L and Restart Count fields
            }

            byte[] quantizationTable = new byte[0];

            // Quantization table header is present when using Q values 128-255
            if (q >= 128 && fragmentOffset == 0) {
                if (payload.bytesLeft() < 4) {
                    //Log.w("RtpJpegDep", "Discard packet: Jpeg empty payload");
                    return false;
                }

                payload.skipBytes(1); // skip MBZ

                precision = payload.readUnsignedByte();
                length = payload.readUnsignedShort();

                if (q == 255 && length == 0) {
                    //Log.w("RtpJpegDep", "Discard packet: Jpeg empty payload");
                    return false;
                }

                if (length > payload.bytesLeft()) {
                    //Log.w("RtpJpegDep", "Discard packet: Jpeg empty payload");
                    return false;
                }

                // Quantization table data is present
                if (length > 0) {
                    quantizationTable = new byte[length];
                    System.arraycopy(payload.data, payload.getPosition(), quantizationTable, 0,
                            length);
                    payload.skipBytes(length);

                } else {
                    quantizationTable = quantizationTables.valueAt(q);
                }
            }

            if (fragmentOffset == 0) {
                // first packet
                if (length == 0) {
                    if (q < 128) {
                        // no quantization table, see if we have one cached
                        quantizationTable = quantizationTables.valueAt(q);
                        if (quantizationTable.length == 0) {
                            quantizationTable = buildQuantizationTable(q);
                            quantizationTables.setValueAt(q, quantizationTable);
                        }

                        // all 8 bit quantizers
                        precision = 0;
                    }
                }

                if (quantizationTable.length == 0) {
                    //Log.w("RtpJpegDep", "Discard packet: Jpeg no quantization table");
                    return false;
                }

                byte[] headers = buildHeaders(type, width, height, quantizationTable, precision, dri);
                fragmentedJpegFrame.appendFragment(headers, 0, headers.length);
            }

            fragmentedJpegFrame.appendFragment(payload.data, payload.getPosition(), payload.bytesLeft());

            return true;
        }

        private byte[] buildQuantizationTable(int q) {
            int factor = clamp (q, 1, 99);

            if (q < 50) {
                q = 5000 / factor;

            } else {
                q = 200 - factor * 2;
            }

            byte[] quantizationTable = new byte[128];

            for (int i = 0; i < 64; i++) {
                int lq = (JPEG_LUMA_QUANTIZER[ZIG_ZAG[i]] * q + 50) / 100;
                int cq = (JPEG_CHROMA_QUANTIZER[ZIG_ZAG[i]] * q + 50) / 100;

                // Limit the quantizers to 1 <= q <= 255
                quantizationTable[i] = clamp(lq, 1, 255);
                quantizationTable[i + 64] = clamp(cq, 1, 255);
            }

            return quantizationTable;
        }

        private byte clamp(int val, int min, int max) {
            return (byte) Math.max(min, Math.min(max, val));
        }

        private byte[] buildDRIHeader(int dri) {
            byte[] hdr = new byte[6];
            hdr[0] = (byte) 0xFF;
            hdr[1] = (byte) 0xDD;          // DRI
            hdr[2] = (byte) 0x0;           // length msb
            hdr[3] = (byte) 4;             // length lsb
            hdr[4] = (byte) (dri >> 8);      // dri msb
            hdr[5] = (byte) (dri & 0xFF);    // dri lsb

            return hdr;
        }

        private byte[] buildQuantizationHeader(byte[] quantizationTable, int position, int size, int tableNo) {
            byte[] hdr = new byte[5 + size];
            hdr[0] = (byte) 0xFF;
            hdr[1] = (byte) 0xDB;      // DQT
            hdr[2] = (byte) 0;         // length msb
            hdr[3] = (byte) (size + 3);  // length lsb
            hdr[4] = (byte) tableNo;

            System.arraycopy(quantizationTable, position, hdr, 5, size);

            return hdr;
        }

        private byte[] buildHuffmanHeader(byte[] codelens, byte[] symbols, int tableNo,
                                          int tableClass) {
            byte[] hdr = new byte[5 + codelens.length + symbols.length];
            hdr[0] = (byte) 0xFF;
            hdr[1] = (byte) 0xC4;                                  // DHT
            hdr[2] = (byte) 0;                                     // length msb
            hdr[3] = (byte) (3 + codelens.length + symbols.length);  // length lsb
            hdr[4] = (byte) ((tableClass << 4) | tableNo);

            System.arraycopy(codelens, 0, hdr, 5, codelens.length);
            System.arraycopy(symbols, 0, hdr, 5 + codelens.length, symbols.length);

            return hdr;
        }

        /**
         * Generate a frame and scan headers that can be prepended to the RTP/JPEG data payload to
         * produce a JPEG compressed image in interchange format (except for possible trailing
         * garbage and absence of an EOI marker to terminate the scan).
         *
         * @return the headers
         */
        private byte[] buildHeaders(int type, int width, int height, byte[] quantizationTable,
                                    int precision, int dri) {
            int position = 0;
            byte[] headers = new byte[1000];  // max header length, should be big enough

            headers[position++] = (byte) 0xFF;
            headers[position++] = (byte) 0xD8;  // SOI

            int size1 = ((precision & 1) == 1) ? 128 : 64;
            byte[] quantHdr1 = buildQuantizationHeader(quantizationTable, 0, size1, 0);
            System.arraycopy(quantHdr1, 0, headers, position, quantHdr1.length);
            position += quantHdr1.length;

            int size2 = ((precision & 2) == 2) ? 128 : 64;
            byte[] quantHdr2 = buildQuantizationHeader(quantizationTable, size1, size2, 1);
            System.arraycopy(quantHdr2, 0, headers, position, quantHdr2.length);
            position += quantHdr1.length;

            if (dri != 0) {
                byte[] driHeader = buildDRIHeader(dri);
                System.arraycopy(driHeader, 0, headers, position, driHeader.length);
                position += driHeader.length;
            }

            headers[position++] = (byte) 0xFF;
            headers[position++] = (byte) 0xC0;          // SOF - Start of Frame market
            headers[position++] = (byte) 0;             // length msb
            headers[position++] = (byte) 17;            // length lsb
            headers[position++] = (byte) 8;             // 8-bit precision
            headers[position++] = (byte) (height >> 8); // height msb
            headers[position++] = (byte) height;        // height lsb
            headers[position++] = (byte) (width >> 8);  // width msb
            headers[position++] = (byte) width;         // width lsb
            headers[position++] = (byte) 3;             // number of components
            headers[position++] = (byte) 0;             // comp 0

            if ((type & 0x3F) == 0) {
                headers[position++] = (byte) 0x21;      // hsamp = 2, vsamp = 1

            } else {
                headers[position++] = (byte) 0x22;      // hsamp = 2, vsamp = 2
            }

            headers[position++] = (byte) 0;             // quant table 0
            headers[position++] = (byte) 1;             // comp 1
            headers[position++] = (byte) 0x11;          // hsamp = 1, vsamp = 1
            headers[position++] = (byte) 1;             // quant table 1
            headers[position++] = (byte) 2;             // comp 2
            headers[position++] = (byte) 0x11;          // hsamp = 1, vsamp = 1
            headers[position++] = (byte) 1;             // quant table 1

            byte[] huffmanHdr1 = buildHuffmanHeader(LUM_DC_CODELENS, LUM_DC_SYMBOLS, 0, 0);
            System.arraycopy(huffmanHdr1, 0, headers, position, huffmanHdr1.length);
            position += huffmanHdr1.length;

            byte[] huffmanHdr2 = buildHuffmanHeader(LUM_AC_CODELENS, LUM_AC_SYMBOLS, 0, 1);
            System.arraycopy(huffmanHdr2, 0, headers, position, huffmanHdr2.length);
            position += huffmanHdr2.length;

            byte[] huffmanHdr3 = buildHuffmanHeader(CHM_DC_CODELENS, CHM_DC_SYMBOLS, 1, 0);
            System.arraycopy(huffmanHdr3, 0, headers, position, huffmanHdr3.length);
            position += huffmanHdr3.length;

            byte[] huffmanHdr4 = buildHuffmanHeader(CHM_AC_CODELENS, CHM_AC_SYMBOLS, 1, 1);
            System.arraycopy(huffmanHdr4, 0, headers, position, huffmanHdr4.length);
            position += huffmanHdr4.length;

            headers[position++] = (byte) 0xFF;
            headers[position++] = (byte) 0xDA;      // SOS - Start of Scan marker
            headers[position++] = (byte) 0;         // length msb
            headers[position++] = (byte) 12;        // length lsb
            headers[position++] = (byte) 3;         // 3 components
            headers[position++] = (byte) 0;         // comp 0
            headers[position++] = (byte) 0;         // huffman table 0
            headers[position++] = (byte) 1;         // comp 1
            headers[position++] = (byte) 0x11;      // huffman table 1
            headers[position++] = (byte) 2;         // comp 2
            headers[position++] = (byte) 0x11;      // huffman table 1
            headers[position++] = (byte) 0;         // first DCT coeff
            headers[position++] = (byte) 63;        // last DCT coeff
            headers[position++] = (byte) 0;         // sucessive approx.

            return Arrays.copyOf(headers, position);
        }
    }

    /**
     * Stores the consecutive fragment JPEG to reconstruct an fragmented JPEG frame
     */
    private static final class FragmentedJpegFrame {
        public byte[] data;
        public int length;

        FragmentedJpegFrame() {
            data = new byte[0];
            length = 0;
        }

        /**
         * Resets the buffer, clearing any data that it holds.
         */
        void reset() {
            length = 0;
        }

        /**
         * Called to add a fragment JPEG to fragmented JPEG frame.
         *
         * @param fragment Holds the data of fragment unit being passed.
         * @param offset   The offset of the data in {@code fragment}.
         * @param limit    The limit (exclusive) of the data in {@code fragment}.
         */
        void appendFragment(byte[] fragment, int offset, int limit) {
            if (data.length < length + limit) {
                data = Arrays.copyOf(data, (length + limit) * 2);
            }

            System.arraycopy(fragment, offset, data, length, limit);
            length += limit;
        }
    }
}
