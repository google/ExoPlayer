package com.google.android.exoplayer2.source.rtp.format;

import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.Collections;
import java.util.List;

public final class RtpAudioPayload extends RtpPayloadFormat {
    private int channels;
    private final long ptime;
    private final long maxptime;

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
        }
    }

    @Override
    public List<byte[]> buildCodecSpecificData() {
        if (codecSpecificData == null) {
            if (MimeTypes.AUDIO_AAC.equals(sampleMimeType())) {
                codecSpecificData = Collections.singletonList(
                        CodecSpecificDataUtil.buildAacLcAudioSpecificConfig(clockrate,
                                this.channels()));

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