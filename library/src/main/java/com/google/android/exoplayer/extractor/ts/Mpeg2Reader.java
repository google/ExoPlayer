package com.google.android.exoplayer.extractor.ts;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a continuous mpeg2 byte stream and extracts individual frames.
 */
/* package */ final class Mpeg2Reader extends ElementaryStreamReader {
    private static final String TAG = "Mpeg2Reader";

    private int flags;
    private boolean seqHeaderFound;
    private boolean frameCollected;
    private boolean isSync;

    private int width;
    private int height;

    private long timeUs;
    private long samplePosition;

    private boolean foundFirstSample;
    private long totalBytesWritten;

    public Mpeg2Reader(TrackOutput output) {
        super(output);
    }

    @Override
    public void seek() {
        seqHeaderFound = false;
        samplePosition = 0;
        foundFirstSample = false;
        totalBytesWritten = 0;
    }

    @Override
    public void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket) {
        while (data.bytesLeft() > 0) {
            int frameAtOffset = 0;
            int offset = data.getPosition();
            int limit = data.limit();
            byte[] dataArray = data.data;
            // Append the data to the buffer.
            totalBytesWritten += data.bytesLeft();
            output.sampleData(data, data.bytesLeft());

            if (!seqHeaderFound && skipToNextSync(dataArray, offset, limit)) {
                if (seqHeaderFound) {
                    flags = C.SAMPLE_FLAG_SYNC;
                    foundFirstSample = true;
                    timeUs = pesTimeUs;
                    findSampleSizeAndPrepareMediaFormat(dataArray, offset); // Seq header has been already been found
                }
            }

            if (startOfPacket && seqHeaderFound) {

                frameAtOffset = findValidStartCode(dataArray, offset, limit);

                if (frameAtOffset < limit) {
                    frameCollected = true;
                    if (!foundFirstSample)
                        flags = (isSync == true) ? C.SAMPLE_FLAG_SYNC : 0;
                    foundFirstSample = false;
                }
            }

            if (frameCollected) {
                int dataWrittenPast = limit - frameAtOffset;
                int sampleSize = (int) (totalBytesWritten - samplePosition) - dataWrittenPast;
                if (sampleSize > 0) {
                    output.sampleMetadata(timeUs, flags, sampleSize, dataWrittenPast, null);
                    samplePosition = totalBytesWritten - dataWrittenPast;
                    timeUs = pesTimeUs; // Update the time to new pes
                }
                frameCollected = false;
            }
        }
    }

    @Override
    public void packetFinished() {
        // Do nothing.
    }


    /**
     * Locates the next sync word, advancing the position to the byte that immediately follows it.
     * If a sync word was not located, the position is advanced to the limit.
     * @param data The buffer whose position should be advanced.
     * @param startoffset The offset of the data in {@code data}.
     * @param endoffset The limit (exclusive) of the data in {@code data}.
     * @return True if a sync word position was found. False otherwise.
     */

    private boolean skipToNextSync(byte[] data, int startoffset, int endoffset) {
        int length = endoffset - startoffset;

        Assertions.checkState(length >= 0);
        if (length == 0) {
            return false;
        }

        while (startoffset < length) {
            if((data[startoffset + 0] & 0xFF) == 0x00 && (data[startoffset + 1] & 0xFF) == 0x00 && (data[startoffset + 2] & 0xFF) == 0x01 && (data[startoffset + 3] & 0xFF) == 0xB3) {
                seqHeaderFound = true;
                Log.i(TAG, "Found the Sequence Header");
                return true;
            }
            startoffset++;
            continue;
        }
        return false;
    }

    /**
     * Locates the next frame start code (0x00, 0x00, 0x01, 0xXX)
     * where XX can be anything lesser than 0x01 or more than 0xaf
     * @param data The buffer whose position should be advanced.
     * @param startoffset The offset of the data in {@code data}.
     * @param endoffset The limit (exclusive) of the data in {@code data}.
     * @return The offset into the buffer where a new frame starts from.
     */
    private int findValidStartCode(byte[] data, int startoffset, int endoffset) {
        int length = endoffset - startoffset;

        Assertions.checkState(length >= 0);
        if (length == 0) {
            return endoffset;
        }

        int limit = endoffset - 3;
        for (int i = startoffset + 3; i < limit; i += 4) {
            if ((data[i - 1] & 0xFE) != 0) {
                // There isn't a frame prefix here, or at the next two positions. Do nothing and let the
                // loop advance the index by four.
            } else if (data[i - 3] == 0 && data[i - 2] == 0 && data[i - 1] == 1) {
                if (checkMpegVideoFrame(data[i]))
                   return i - 3;
            } else {
                i -= 3;
            }
        }
        return endoffset;
    }

    public  boolean checkMpegVideoFrame (int data) {
        int byte1 = data & 0xFF;
        if (byte1 < 0x01 || byte1 > 0xaf){
            isSync = (byte1 == 0xb3) ? true : false;
            return true;
        }
        return false;
    }

    /**
     * Parses the sample size from sequence header and prepares media format
     * @param data The buffer whose position should be advanced.
     * @param offset The offset of the data in {@code data}.
     * @return None
     */
    private void findSampleSizeAndPrepareMediaFormat(byte[] data, int offset) {
        if (seqHeaderFound) {
            // Width and Height
            int b1 = data[4 + offset] & 0xFF;
            int b2 = data[5 + offset] & 0xFF;
            int b3 = data[6 + offset] & 0xFF;
            width = (b1 << 4) | (b2 & 0xF0);
            height = (b2 & 0x0F) << 8 | b3;
            Log.i(TAG, "Width is " + width + " Height is " + height);
        }
        List<byte[]> initializationData = new ArrayList<>();
        output.format(MediaFormat.createVideoFormat(MediaFormat.NO_VALUE, MimeTypes.VIDEO_MPEG2,
                      MediaFormat.NO_VALUE, MediaFormat.NO_VALUE, C.UNKNOWN_TIME_US,
                      width, height, initializationData));
    }
}

