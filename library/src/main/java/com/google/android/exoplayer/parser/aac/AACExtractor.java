package com.google.android.exoplayer.parser.aac;

import android.media.MediaExtractor;
import android.util.Log;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.chunk.HLSExtractor;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;

import junit.framework.Assert;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class AACExtractor extends HLSExtractor {
    private static final String TAG = "AACExtractor";
    private NonBlockingInputStream inputStream;
    private UnsignedByteArray data;
    private int state;
    private int position;
    private ID3Header mID3Header = new ID3Header();
    private long timeUs;
    private final ADTSHeader mADTSHeader = new ADTSHeader();
    private boolean readFinished;

    private static final int STATE_ID3_HEADER = 0;
    private static final int STATE_ID3_DATA = 1;
    private static final int STATE_ADTS_HEADER = 2;
    private static final int STATE_AAC_DATA = 3;

    private MediaFormat audioMediaFormat;
    private final SampleHolder temporaryHolder;

    public static class ADTSHeader {
        public int frameLength;
        public int sampleRate;

        private int sampleRateIndex;
        private int channelConfigIndex;

        private static int getSampleRate(int sampleRateIndex) {
            switch(sampleRateIndex) {
                case 0: return 96000;
                case 1: return 88200;
                case 2: return 64000;
                case 3: return 48000;
                case 4: return 44100;
                case 5: return 32000;
                case 6: return 24000;
                case 7: return 22050;
                case 8: return 16000;
                case 9: return 12000;
                case 10: return 11025;
                case 11: return 8000;
                case 12: return 7350;
            }
            return 44100;
        }

        public void update(UnsignedByteArray data, int offset)
        {
            UnsignedByteArray d = data;
            if (d.get(0) != 0xff || ((d.get(1) & 0xf0) != 0xf0)) {
                Log.d(TAG, "no ADTS sync");
            } else {
                sampleRateIndex =(d.get(2) & 0x3c) >> 2;
                sampleRate = getSampleRate(sampleRateIndex);
                channelConfigIndex = (((d.get(2) & 0x1) << 2) + ((d.get(3) & 0xc0) >> 6));
                frameLength = (d.get(3) & 0x3) << 11;
                frameLength += (d.get(4) << 3);
                frameLength += (d.get(5) & 0xe0) >> 5;
                /*Log.d(TAG, "version: " + ((d.get(1) & 0x08) >> 3));
                Log.d(TAG, "layer: " + ((d.get(1) & 0x06) >> 1));
                Log.d(TAG, "protection absent: " + ((d.get(1) & 0x01) >> 0));
                Log.d(TAG, "profile: " + ((d.get(2) & 0xc0) >> 6));
                Log.d(TAG, "sample rate: " + sampleRate);
                Log.d(TAG, "channel config index: " + channelConfigIndex);
                Log.d(TAG, "frame length: " + frameLength);*/
            }
        }

        public MediaFormat toMediaFormat()
        {
            MediaFormat mediaFormat;
            List<byte[]> initializationData = new ArrayList<byte[]>();
            byte[] data = new byte[2];
            data[0] = (byte)(0x10 | ((sampleRateIndex & 0xe) >> 1));
            data[1] = (byte)(((sampleRateIndex & 0x1) << 7) | ((channelConfigIndex & 0xf) << 3));
            initializationData.add(data);
            mediaFormat = MediaFormat.createAudioFormat(MimeTypes.AUDIO_AAC, -1, 2, sampleRate, initializationData);
            mediaFormat.setIsADTS(true);
            return mediaFormat;
        }
    }

    static private class ID3Header {
        int size;
        boolean extendedHeader;
        boolean footerPresent;
    }

    public AACExtractor(NonBlockingInputStream inputStream) {
        super();
        this.inputStream = inputStream;
        data = new UnsignedByteArray(32*1024);
        temporaryHolder = new SampleHolder(false);
        temporaryHolder.data = ByteBuffer.allocate(32*1024);
    }

    @Override
    public int read(int type, SampleHolder out) throws ParserException {
        Assertions.checkState(type == TYPE_AUDIO);

        int ret;
        while (true) {
            switch (state) {
                case STATE_ID3_HEADER:
                    ret = inputStream.read(data.array(), position, 10 - position);
                    if (ret == -1) {
                        readFinished = true;
                        return RESULT_END_OF_STREAM;
                    }
                    position += ret;
                    if (position == 10) {
                        String sync = new String(data.array(), 0, 3);
                        /*if (data.get(0) != 0x49 || data.get(1) != 0x44 || data.get(2) != 0x33) {
                            Log.d(TAG, "no ID3 sync");
                        }*/

                        if (!sync.equals("ID3")) {
                            Log.d(TAG, "no ID3 sync");
                        }

                        int flags = data.get(5);
                        mID3Header.extendedHeader = (flags & 0x40) != 0;
                        mID3Header.footerPresent = (flags & 0x10) != 0;
                        mID3Header.size = getSynchSafeInteger(data, 6);
                        if (mID3Header.size == 0 || mID3Header.size > data.length()) {
                            throw new ParserException("bad ID3 tag size");
                        }
                        position = 0;
                        state = STATE_ID3_DATA;
                    } else {
                        return RESULT_NEED_MORE_DATA;
                    }
                    break;
                case STATE_ID3_DATA:
                    ret = inputStream.read(data.array(), position, mID3Header.size - position);
                    if (ret == -1) {
                        readFinished = true;
                        return RESULT_END_OF_STREAM;
                    }
                    position += ret;
                    if (position == mID3Header.size) {
                        int offset = 0;
                        if (mID3Header.extendedHeader) {
                            offset += 10;
                        }

                        while (offset < mID3Header.size) {
                            String id = new String(data.array(), offset, 4);
                            offset += 4;
                            int size = getSynchSafeInteger(data, offset);
                            offset += 6;
                            if (id.equals("PRIV")) {
                                int limit = offset + size;
                                String ownerIdentifier = "";
                                while (offset < limit) {
                                    int c = data.get(offset++);
                                    if (c == 0) {
                                        break;
                                    } else {
                                        ownerIdentifier += (char)c;
                                    }
                                }
                                if (ownerIdentifier.equals("com.apple.streaming.transportStreamTimestamp")) {
                                    Assertions.checkState(limit - offset == 8);
                                    timeUs = (data.getLong(offset) * 1000) / 90;
                                    state = STATE_ADTS_HEADER;
                                    position = 0;
                                    break;
                                }
                                offset = limit;
                            } else {
                                offset += size;
                            }
                        }
                    } else {
                        return RESULT_NEED_MORE_DATA;
                    }
                    break;
                case STATE_ADTS_HEADER:
                    ret = inputStream.read(data.array(), position, 7 - position);
                    if (ret == -1) {
                        readFinished = true;
                        return RESULT_END_OF_STREAM;
                    }
                    position += ret;
                    if (position == 7) {
                        mADTSHeader.update(data, 0);

                        if (audioMediaFormat == null) {
                            audioMediaFormat = mADTSHeader.toMediaFormat();
                        }
                        state = STATE_AAC_DATA;
                    } else {
                        return RESULT_NEED_MORE_DATA;
                    }
                    break;
                case STATE_AAC_DATA:
                    ret = inputStream.read(data.array(), position, mADTSHeader.frameLength - position);
                    if (ret == -1) {
                        readFinished = true;
                        return RESULT_END_OF_STREAM;
                    }
                    position += ret;
                    if (position == mADTSHeader.frameLength) {
                        mADTSHeader.update(data, 0);
                        if (out.data != null) {
                            out.data.put(data.array(), 0, mADTSHeader.frameLength);
                            /*
                             * XXX: I could compute a better timestamp based on the number of frames send
                             * but this won't work for HE AAC where the effective sample rate depends if
                             * the decoder can handle the SBR
                             */
                            out.timeUs = timeUs;
                            out.flags = MediaExtractor.SAMPLE_FLAG_SYNC;
                        }
                        if (audioMediaFormat == null) {
                            audioMediaFormat = mADTSHeader.toMediaFormat();
                        }
                        position = 0;
                        state = STATE_ADTS_HEADER;
                        return RESULT_READ_SAMPLE_FULL;
                    } else {
                        return RESULT_NEED_MORE_DATA;
                    }
            }
        }
    }

    private static int getSynchSafeInteger(UnsignedByteArray data, int offset) {
        return (data.get(offset) << 21) | (data.get(offset + 1) << 14) | (data.get(offset + 2) << 7) | (data.get(offset + 3));
    }

    @Override
    public MediaFormat getAudioMediaFormat() {
        while (audioMediaFormat == null) {
            try {
                /*
                 * XXX: we should get the audioMediaFormat with the first sample
                 */
                if (read(TYPE_AUDIO, temporaryHolder) == RESULT_END_OF_STREAM) {
                    break;
                }
            } catch (ParserException e) {
                e.printStackTrace();
                break;
            }
        }
        return audioMediaFormat;
    }

    @Override
    public boolean isReadFinished() {
        return readFinished;
    }
}
