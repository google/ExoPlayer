package com.google.android.exoplayer.extractor.ts;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a continuous mpeg2 byte stream and extracts individual frames.
 */
/* package */ final class H262Reader extends ElementaryStreamReader {
    private static final String TAG = "H262Reader";

    private final int H262_FRAME_TYPE_IFRAME = 0;
    private final int H262_FRAME_TYPE_AUD = 1;

    private boolean hasSequenceCompleted;
    private boolean foundFirstSample;

    // State that should not be reset on seek.
    private boolean hasOutputFormat;

    // State that should be reset on seek.
    private long totalBytesWritten;
    private final boolean[] prefixFlags;

    // Per sample state that gets reset at the start of each sample.
    private boolean isKeyframe;
    private long samplePosition;
    private long sampleTimeUs;

    public H262Reader(TrackOutput output) {
        super(output);
        prefixFlags = new boolean[4];
    }

    @Override
    public void seek() {
        clearPrefixFlags(prefixFlags);
        hasSequenceCompleted = false;
        samplePosition = 0;
        totalBytesWritten = 0;
    }

    @Override
    public void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket) {
        while (data.bytesLeft() > 0) {
            int offset = data.getPosition();
            int limit = data.limit();
            byte[] dataArray = data.data;
            // Append the data to the buffer.
            totalBytesWritten += data.bytesLeft();
            output.sampleData(data, data.bytesLeft());

            if (!hasSequenceCompleted) {
               hasSequenceCompleted = checkForCompleteSequence(dataArray, offset, limit, prefixFlags);
               if (hasSequenceCompleted && !hasOutputFormat)
                   output.format(parseMediaFormatFromSeqHeader(dataArray, offset, limit, prefixFlags));
            }

            while (hasOutputFormat && offset < limit) {
                int nextframeAtOffset = findValidStartCode(dataArray, offset, limit, prefixFlags);
                if (nextframeAtOffset < limit) {
                    int frameType = getH262FrameType(dataArray, nextframeAtOffset);
                    int bytesWrittenPast = limit - nextframeAtOffset;
                    switch (frameType) {
                        case H262_FRAME_TYPE_IFRAME:
                            isKeyframe = true;
                            break;
                        case H262_FRAME_TYPE_AUD:
                            if (foundFirstSample) {
                                int flags = isKeyframe ? C.SAMPLE_FLAG_SYNC : 0;
                                int size = (int) (totalBytesWritten - samplePosition) - bytesWrittenPast;
                                output.sampleMetadata(sampleTimeUs, flags, size, bytesWrittenPast, null);
                            }
                            foundFirstSample = true;
                            samplePosition = totalBytesWritten - bytesWrittenPast;
                            sampleTimeUs = pesTimeUs;
                            isKeyframe = false;
                            break;
                    }
                    offset = nextframeAtOffset + 4;
                } else {
                    offset = limit;
                }
            }
        }
    }

    @Override
    public void packetFinished() {
        // Do nothing.
    }

    /**
     * Locates the next frame start code (0x00, 0x00, 0x01, 0xXX)
     * where XX can be anything lesser than 0x01 or more than 0xaf
     * @param data The buffer whose position should be advanced.
     * @param startOffset The offset of the data in {@code data}.
     * @param endOffset The limit (exclusive) of the data in {@code data}.
     * @param prefixFlags A boolean array whose first four elements are used to store the state
     *     required to detect H262 frame units where the frame unit prefix spans array boundaries. The array
     *     must be at least 4 elements long.
     * @return The offset into the buffer where a new frame starts from.
     */
    private int findValidStartCode(byte[] data, int startOffset, int endOffset,
                                   boolean[] prefixFlags ) {
        int length = endOffset - startOffset;

        Assertions.checkState(length >= 0);
        if (length == 0) {
            return endOffset;
        }

        if (prefixFlags != null) {
            if (prefixFlags[0]) {
                clearPrefixFlags(prefixFlags);
                return startOffset - 4;
            } else if (length > 1 && prefixFlags[1] && checkMpegVideoFrame(data[startOffset])) {
                clearPrefixFlags(prefixFlags);
                return startOffset - 3;
            } else if (length > 2 && prefixFlags[2] && data[startOffset] == 1
                    && checkMpegVideoFrame(data[startOffset + 1])) {
                clearPrefixFlags(prefixFlags);
                return startOffset - 2;
            }  else if (length > 3 && prefixFlags[3] && data[startOffset] == 0
                    && data[startOffset + 1] == 1 && checkMpegVideoFrame(data[startOffset + 1])) {
                clearPrefixFlags(prefixFlags);
                return startOffset - 1;
            }
        }

        int limit = endOffset - 1;
        for (int i = startOffset + 3; i < limit; i += 4) {
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

        if (prefixFlags != null) {
            // True if the last four bytes in the data seen so far are {0,0,1, <valid MPEG2 video code>}.
            prefixFlags[0] = length > 3
                    ? (data[endOffset - 4] == 0 && data[endOffset - 3] == 0 && data[endOffset - 2] == 1 && checkMpegVideoFrame(data[endOffset - 1]))
                    : length == 3 ? (prefixFlags[3] && data[endOffset - 3] == 0 && data[endOffset - 2] == 1 && checkMpegVideoFrame(data[endOffset - 1]))
                    : length == 2 ? (prefixFlags[2] && data[endOffset - 2] == 1 && checkMpegVideoFrame(data[endOffset - 1]))
                    : (prefixFlags[1] && checkMpegVideoFrame(data[endOffset - 1]));
            // True if the last three bytes in the data seen so far are {0,0,1}.
            prefixFlags[1] = length > 2 ? (data[endOffset - 3] == 0 && data[endOffset - 2] == 0 && data[endOffset - 1] == 1)
                    : length == 2 ? (prefixFlags[2] && data[endOffset - 2] == 0 && data[endOffset - 1] == 1)
                    : (prefixFlags[1] && data[endOffset - 1] == 1);
            // True if the last two bytes in the data seen so far are {0,0}.
            prefixFlags[2] = length > 1 ? data[endOffset - 2] == 0 && data[endOffset - 1] == 0
                    : prefixFlags[3] && data[endOffset - 1] == 0;
            // True if the last byte in the data seen so far is {0}.
            prefixFlags[3] = data[endOffset - 1] == 0;
         }

        return endOffset;
    }

    public  boolean checkMpegVideoFrame (int data) {
        int byte1 = data & 0xFF;
        if (byte1 < 0x01 || byte1 > 0xaf){
            return true;
        }
        return false;
    }

    /**
     * Parses the video width and height from sequence header and prepares media format
     * @param data The buffer whose position should be advanced.
     * @param offset The offset of the data in {@code data}.
     * @param limit The limit (exclusive) of the data in {@code data}.
     * @return MediaFormat object
     */
    private MediaFormat parseMediaFormatFromSeqHeader(byte[] data, int offset, int limit, boolean[] prefixFlags) {
        int width;
        int height;
        int aspectRatioCode = 0;

        // Width and Height
        int b1 = data[4 + offset] & 0xFF;
        int b2 = data[5 + offset] & 0xFF;
        int b3 = data[6 + offset] & 0xFF;
        width = (b1 << 4) | (b2 & 0xF0);
        height = (b2 & 0x0F) << 8 | b3;
        aspectRatioCode = (data[7 + offset] & 0xF0) >> 4;
        float pixelWidthAspectRatio = 1; //default
        switch(aspectRatioCode) {
            case 2:
                pixelWidthAspectRatio = 4 / 3;
                break;
            case 3:
                pixelWidthAspectRatio = 16 / 9;
                break;
            case 4:
                pixelWidthAspectRatio = 221 / 100;
                break;
            default:
                pixelWidthAspectRatio = 1;
        }

        // TODO: parse the sequence display extension if present
        // as if present the aspect ration can differ

        List<byte[]> initializationData = new ArrayList<>();
        hasOutputFormat = true;
        return (MediaFormat.createVideoFormat(MediaFormat.NO_VALUE, MimeTypes.VIDEO_MPEG2,
                      MediaFormat.NO_VALUE, MediaFormat.NO_VALUE, C.UNKNOWN_TIME_US,
                      width, height, initializationData, MediaFormat.NO_VALUE, pixelWidthAspectRatio));
    }

    /**
     * Parses the video width and height from sequence header and prepares media format
     * @param data The buffer whose position should be advanced.
     * @param offset The offset of the data in {@code data}.
     * @param prefixFlags A boolean array whose first four elements are used to store the state
     *     required to detect H262 frame units where the frame unit prefix spans array boundaries. The array
     *     must be at least 4 elements long.
     * @return boolean value indicating if the complete sequence header was found or not
     */
    private boolean checkForCompleteSequence (byte[] data, int offset, int limit, boolean[] prefixFlags) {
        boolean seqHeaderEndFound = false;
        boolean seqHeaderStartFound = false;
        int nextFrameOffset = 0;

        // Make sure sequence data (sequence header, sequence extentions are collected
        // till a GOP or sequence extension is encountered in the bitstream
        // Remember this loop has to run on entire buffer and not from current buffer offset
        while (offset < limit) {
            nextFrameOffset = findValidStartCode(data, offset, limit, prefixFlags);
            if ((nextFrameOffset == limit) || ((limit - nextFrameOffset) < 3))
                break;

            if (!seqHeaderStartFound && (data[nextFrameOffset + 3] & 0xFF) == 0xB3) {
                seqHeaderStartFound = true;
            }
            if (seqHeaderStartFound && (((data[nextFrameOffset + 3] & 0xFF) == 0xB8) || ((data[nextFrameOffset + 3] & 0xFF) == 0xB5))) {
                seqHeaderEndFound = true;
            }
            if (seqHeaderStartFound && seqHeaderEndFound) {
                return true;
            }
            offset = nextFrameOffset + 4;
        }
        return false;
    }

    /**
     * Parses the video width and height from sequence header and prepares media format
     * @param data The buffer whose position should be advanced.
     * @param offset The offset of the data in {@code data}.
     * @return Type of H262 frame
     * Note: As per 13818 the first picture following a GOP is always an I-Frame
     * Also, for a packet with picture header, the 4th, 5th and 6th bit of 5th byte signifies if the frame is of Intra type
     */
    private int getH262FrameType (byte[] data, int offset) {

        if ((data[offset] & 0xFF) == 0x00 && (data[offset + 1] & 0xFF) == 0x00 && (data[offset + 2] & 0xFF) == 0x01 && (data[offset + 3] & 0xFF) == 0xb8) {
            // As per 13818 6.3.1 the first coded frame following GOP is always an I-frame
            return H262_FRAME_TYPE_IFRAME;
        }
        if ((data[offset] & 0xFF) == 0x00 && (data[offset + 1] & 0xFF) == 0x00 && (data[offset + 2] & 0xFF) == 0x01 && (data[offset + 3] & 0xFF) == 0x00) {
            //Log.i(TAG, "getH262FrameType Data is " + String.format("0x%2s", Integer.toHexString(data[offset + 5])));
            // The packet has picture start code, check if the frame type is Intra codec. Not working correctly
            return (((data[offset + 5]) & 0x38 >> 3) == 1 ? H262_FRAME_TYPE_IFRAME : H262_FRAME_TYPE_AUD);
        }
        return H262_FRAME_TYPE_AUD;
    }

    /**
     * Clears prefix flags, as used by {@link #findValidStartCode(byte[], int, int, boolean[]) }.
     * @param prefixFlags The flags to clear.
     */
    public static void clearPrefixFlags(boolean[] prefixFlags) {
        prefixFlags[0] = false;
        prefixFlags[1] = false;
        prefixFlags[2] = false;
        prefixFlags[3] = false;
    }
}

