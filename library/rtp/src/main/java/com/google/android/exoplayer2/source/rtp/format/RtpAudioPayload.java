package com.google.android.exoplayer2.source.rtp.format;

import android.util.Pair;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

import java.util.Collections;
import java.util.List;

public final class RtpAudioPayload extends RtpPayloadFormat {
    private int channels;
    private final long ptime;
    private final long maxptime;

    private int numSubFrames;

    private String codecs;
    private List<byte[]> codecSpecificData = null;

    RtpAudioPayload(Builder builder) {
        super(builder);

        this.channels = builder.channels;
        this.ptime = builder.ptime;
        this.maxptime = builder.maxptime;
    }

    public int channels() { return channels; }

    public void setChannels(int channels) { this.channels = channels; }

    public long ptime() { return ptime; }

    public long maxptime() { return maxptime; }

    public String codecs() { return codecs; }

    public int numSubFrames() { return numSubFrames; }

    @Override
    public void buildCodecProfileLevel() {
        if (MimeTypes.AUDIO_AAC.equals(sampleMimeType())) {
            if (parameters.contains(FormatSpecificParameter.PROFILE_LEVEL_ID)) {
                //     1: AAC Main
                //     2: AAC LC (Low Complexity)
                //     3: AAC SSR (Scalable Sample Rate)
                //     4: AAC LTP (Long Term Prediction)
                //     5: SBR (Spectral Band Replication)
                //     6: AAC Scalable
                codecs = "mp4a.40." + parameters.value(FormatSpecificParameter.PROFILE_LEVEL_ID);
            }
        } else if (MimeTypes.AUDIO_MP4.equals(sampleMimeType())) {
            if (parameters.contains(FormatSpecificParameter.PROFILE_LEVEL_ID)) {
                //     1: Main Audio Profile Level 1
                //     9: Speech Audio Profile Level 1
                //    15: High Quality Audio Profile Level 2
                //    30: Natural Audio Profile Level 1
                //    44: High Efficiency AAC Profile Level 2
                //    48: High Efficiency AAC v2 Profile Level 2
                //    55: Baseline MPEG Surround Profile (see ISO/IEC 23003-1) Level 3
                codecs = "mp4a.40." + parameters.value(FormatSpecificParameter.PROFILE_LEVEL_ID);

            } else {
                codecs = "mp4a.40.1";
            }
        }
    }

    @Override
    public List<byte[]> buildCodecSpecificData() {
        if (codecSpecificData == null) {
            if (MimeTypes.AUDIO_AAC.equals(sampleMimeType())) {
                if (channels > 0 && clockrate > 0) {
                    codecSpecificData = Collections.singletonList(
                            CodecSpecificDataUtil.buildAacLcAudioSpecificConfig(clockrate,
                                    channels));
                } else {

                    if (parameters.contains(FormatSpecificParameter.CONFIG)) {
                        byte[] config = parameters.value(FormatSpecificParameter.CONFIG).getBytes();

                        try {
                            Pair<Integer, Integer> specConfigParsed = CodecSpecificDataUtil
                                    .parseAacAudioSpecificConfig(config);

                            clockrate = (clockrate > 0) ? clockrate : specConfigParsed.first;
                            channels = (channels > 0) ? channels : specConfigParsed.second;

                            codecSpecificData = Collections.singletonList(CodecSpecificDataUtil
                                    .buildAacLcAudioSpecificConfig(clockrate, channels));

                        } catch (ParserException ex) {
                            codecSpecificData = Collections.singletonList(new byte[0]);
                        }
                    }
                }

            } else if (MimeTypes.AUDIO_MP4.equals(sampleMimeType())) {
                boolean cpresent = true;
                if (parameters.contains(FormatSpecificParameter.CPRESENT) &&
                        "0".equals(parameters.value(FormatSpecificParameter.CPRESENT))) {
                    cpresent = false;
                }

                String config = parameters.value(FormatSpecificParameter.CONFIG);
                if (config != null) {
                    try {
                        if (config.length() % 2 == 0) {
                            byte[] smc = Util.getBytesFromHexString(config);
                            Pair<Integer, Pair<Integer, Integer>> cfgOpts = CodecSpecificDataUtil.
                                    parseMpeg4AudioStreamMuxConfig(smc);

                            numSubFrames = cfgOpts.first;
                            clockrate = cfgOpts.second.first;
                            channels = cfgOpts.second.second;

                            codecSpecificData = Collections.singletonList(
                                    CodecSpecificDataUtil.buildAacLcAudioSpecificConfig(
                                            clockrate, channels));
                        }

                    } catch (IllegalArgumentException | ParserException ex) {

                    }

                } else {
                    if (cpresent) {
                        if (channels > 0 && clockrate > 0) {
                            codecSpecificData = Collections.singletonList(
                                    CodecSpecificDataUtil.buildAacLcAudioSpecificConfig(clockrate,
                                            channels));
                        }
                    }
                }

            } else {
                codecSpecificData = Collections.singletonList(new byte[0]);
            }
        }

        return codecSpecificData;
    }

    public static final class Builder extends RtpPayloadFormat.Builder {
        private static final int DEFAULT_NUM_CHANNELS = 1;

        int channels = DEFAULT_NUM_CHANNELS;
        long ptime;
        long maxptime;

        public Builder() {
            super(AUDIO);
            ptime = Format.NO_VALUE;
            maxptime = Format.NO_VALUE;
        }

        public Builder channels(int channels) {
            this.channels = channels;
            return this;
        }

        public Builder ptime(long ptime) {
            this.ptime = ptime;
            return this;
        }

        public Builder maxptime(long maxptime) {
            this.maxptime = ptime;
            return this;
        }

        @Override
        public RtpPayloadFormat build() {
            return new RtpAudioPayload(this);
        }
    }
}