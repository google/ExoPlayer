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
package com.google.android.exoplayer.hls.parser;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.util.CodecSpecificDataUtil;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.util.Pair;

import java.util.Collections;

/**
   * Parses a continuous MPEG Audio byte stream and extracts individual
   * frames.
   */
/* package */ public class MpaReader extends ElementaryStreamReader {

    private static final int STATE_FINDING_SYNC = 0;
    private static final int STATE_READING_HEADER = 1;
    private static final int STATE_READING_SAMPLE = 2;

    private static final int HEADER_SIZE = 4;
    private static final int CRC_SIZE = 2;

    private final ParsableBitArray mpaScratch;

    private int state;
    private int bytesRead;

    // Used to find the header.
    private boolean hasCrc;

    // Parsed from the header.
    private long frameDurationUs;
    private int sampleSize;

    // Used when reading the samples.
    private long timeUs;

    //
    /**
     * sampling rates in hertz:
     *
     *     @index MPEG Version ID
     *     @index sampling rate index
     */

    private static final int[][] MPA_SAMPLING_RATES = new int[][] {
            {11025, 12000,  8000},    // MPEG 2.5
            {    0,     0,     0},    // reserved
            {22050, 24000, 16000},    // MPEG 2
            {44100, 48000, 32000}     // MPEG 1
    };

    /**
     * bitrates:
     *
     *     @index LSF
     *     @index Layer
     *     @index bitrate index
     */

    private static final int[][][] MPA_BITRATES = new int[][][] {
            { // MPEG 1
                    // Layer1
                    {  0,  32,  64,  96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448},
                    // Layer2
                    {  0,  32,  48,  56,  64,  80,  96, 112, 128, 160, 192, 224, 256, 320, 384},
                    // Layer3
                    {  0,  32,  40,  48,  56,  64,  80,  96, 112, 128, 160, 192, 224, 256, 320}
            },
            { // MPEG 2, 2.5
                    // Layer1
                    {  0,  32,  48,  56,  64,  80,  96, 112, 128, 144, 160, 176, 192, 224, 256},
                    // Layer2
                    {  0,   8,  16,  24,  32,  40,  48,  56,  64,  80,  96, 112, 128, 144, 160},
                    // Layer3
                    {  0,   8,  16,  24,  32,  40,  48,  56,  64,  80,  96, 112, 128, 144, 160}
            }
    };

    /**
     * Samples per Frame:
     *
     *  @index LSF
     *  @index Layer
     */

    private static final int[][] MPA_SAMPLES_PER_FRAME = new int[][] {
            {           // MPEG 1
                    384,   // Layer1
                    1152,   // Layer2
                    1152    // Layer3
            },
            {           // MPEG 2, 2.5
                    384,   // Layer1
                    1152,   // Layer2
                    576    // Layer3
            }
    };

    /**
     * Coefficients (samples per frame / 8):
     *
     * @index = LSF
     * @index = Layer
     */

    private static final int[][] MPA_COEFFICIENTS = new int[][] {
            {           // MPEG 1
                    12,    // Layer1
                    144,    // Layer2
                    144     // Layer3
            },
            {           // MPEG 2, 2.5
                    12,    // Layer1
                    144,    // Layer2
                    72     // Layer3
            }
    };

    /**
     * slot size per layer:
     *
     * @index = Layer
     */

    private static final int[] MPA_SLOT_SIZE = new int[] {
            4,          // Layer1
            1,          // Layer2
            1           // Layer3
    };

    public MpaReader(BufferPool bufferPool) {
        super(bufferPool);
        mpaScratch = new ParsableBitArray(new byte[HEADER_SIZE + CRC_SIZE]);
        state = STATE_FINDING_SYNC;
    }

    @Override
    public void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket) {
        if (startOfPacket) {
            timeUs = pesTimeUs;
        }
        while (data.bytesLeft() > 0) {
            switch (state) {
                case STATE_FINDING_SYNC:
                    if (skipToNextSync(data)) {
                        bytesRead = 0;
                        state = STATE_READING_HEADER;
                    }
                    break;
                case STATE_READING_HEADER:
                    int targetLength = hasCrc ? HEADER_SIZE + CRC_SIZE : HEADER_SIZE;
                    if (continueRead(data, mpaScratch.getData(), targetLength)) {
                        startSample(timeUs);
                        parseHeader();
                        bytesRead = 0;
                        state = STATE_READING_SAMPLE;
                    }
                    break;
                case STATE_READING_SAMPLE:
                    int bytesToRead = Math.min(data.bytesLeft(), sampleSize - bytesRead);
                    appendData(data, bytesToRead);
                    bytesRead += bytesToRead;
                    if (bytesRead == sampleSize) {
                        commitSample(true);
                        timeUs += frameDurationUs;
                        bytesRead = 0;
                        state = STATE_FINDING_SYNC;
                    }
                    break;
            }
        }
    }

    @Override
    public void packetFinished() {
        // Do nothing.
    }

    /**
     * Continues a read from the provided {@code source} into a given {@code target}. It's assumed
     * that the data should be written into {@code target} starting from an offset of zero.
     *
     * @param source The source from which to read.
     * @param target The target into which data is to be read.
     * @param targetLength The target length of the read.
     * @return Whether the target length was reached.
     */
    private boolean continueRead(ParsableByteArray source, byte[] target, int targetLength) {
        int bytesToRead = Math.min(source.bytesLeft(), targetLength - bytesRead);
        source.readBytes(target, bytesRead, bytesToRead);
        bytesRead += bytesToRead;
        return bytesRead == targetLength;
    }

    /**
     * Locates the next sync word, advancing the position to the byte that immediately follows it.
     * If a sync word was not located, the position is advanced to the limit.
     *
     * @param pesBuffer The buffer whose position should be advanced.
     * @return True if a sync word position was found. False otherwise.
     */
    private boolean skipToNextSync(ParsableByteArray pesBuffer) {
        byte[] mpaData = pesBuffer.data;
        int startOffset = pesBuffer.getPosition();
        int endOffset = pesBuffer.limit();
        for (int i = startOffset; i < endOffset - 1; i++) {
            int syncBits = ((mpaData[i] & 0xFF) << 8 ) | (mpaData[i + 1] & 0xFF);
            if ((syncBits & 0xFFF0) == 0xFFF0) {
                hasCrc = (mpaData[i + 1] & 0x1) == 0;
                pesBuffer.setPosition(i);
                return true;
            }
        }
        pesBuffer.setPosition(endOffset);
        return false;
    }

    /**
     * Calculates MPEG Audio frame size
     *
     * @param layer The MPEG layer
     * @param LSF Low Sample rate Format (MPEG 2)
     * @param bitrate The bitrate in bits per second
     * @param samplesPerSec The sampling rate in hertz
     * @param paddingSize
     * @return Frame size in bytes
     */
    private static int CalcMpaFrameSize (int layer, int LSF, int bitrate, int samplesPerSec, int paddingSize) {
        return (int)(Math.floor(MPA_COEFFICIENTS[LSF][layer] * bitrate / samplesPerSec) + paddingSize) * MPA_SLOT_SIZE[layer];
    }

    /**
     * Parses the sample header.
     */
    private void parseHeader() {
        int headerLength = hasCrc ? HEADER_SIZE + CRC_SIZE : HEADER_SIZE;
        mpaScratch.setPosition(0);

        if (!hasMediaFormat()) {
            mpaScratch.skipBits(12);
            int isLSF = (!mpaScratch.readBit()) ? 1 : 0;
            int layer = mpaScratch.readBits(2) ^ 3;
            mpaScratch.skipBits(1);
            int audioObjectType = 32 + layer;
            int bitRate = MPA_BITRATES[isLSF][layer][mpaScratch.readBits(4)];
            int sampleRate = MPA_SAMPLING_RATES[3 - isLSF][mpaScratch.readBits(2)];
            int sampleRateIndex = CodecSpecificDataUtil.getSampleRateIndex(sampleRate);
            int paddingBit = (mpaScratch.readBit()) ? 1 : 0;
            mpaScratch.skipBits(1);
            int channelConfig = mpaScratch.readBits(2) == 3 ? 1 : 2;

            byte[] audioSpecificConfig = CodecSpecificDataUtil.buildAudioSpecificConfig(
                    audioObjectType, sampleRateIndex, channelConfig);
            Pair<Integer, Integer> audioParams = CodecSpecificDataUtil.parseAudioSpecificConfig(
                    audioSpecificConfig);

            // need to investigate how to detect if the mpeg decoder supports Layers other than Layer III
            MediaFormat mediaFormat = MediaFormat.createAudioFormat(/*isLSF == 1 ?*/ MimeTypes.AUDIO_MPEG/* : MimeTypes.AUDIO_MP1L2*/,
                    MediaFormat.NO_VALUE, audioParams.second, audioParams.first,
                    Collections.singletonList(audioSpecificConfig));
            frameDurationUs = (C.MICROS_PER_SECOND * MPA_SAMPLES_PER_FRAME[isLSF][layer]) / mediaFormat.sampleRate;
            setMediaFormat(mediaFormat);
            sampleSize = CalcMpaFrameSize(layer, isLSF, bitRate * 1000, sampleRate, paddingBit) - headerLength;
        }

        mpaScratch.setPosition(0);

        ParsableByteArray header = new ParsableByteArray(mpaScratch.getData(),headerLength);
        appendData(header, headerLength);
    }
}
