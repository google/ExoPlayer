package com.google.android.exoplayer.extractor.flv;

import android.util.Log;
import android.util.Pair;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.CodecSpecificDataUtil;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.util.Collections;

/**
 * Created by joliva on 9/27/15.
 */
public class AudioTagReader extends TagReader{

  private static final String TAG = "AudioTagReader";

  // Sound format
  private static final int AUDIO_FORMAT_LINEAR_PCM_PLATFORM_ENDIAN = 0;
  private static final int AUDIO_FORMAT_ADPCM = 1;
  private static final int AUDIO_FORMAT_MP3 = 2;
  private static final int AUDIO_FORMAT_LINEAR_PCM_LITTLE_ENDIAN = 3;
  private static final int AUDIO_FORMAT_NELLYMOSER_16KHZ_MONO = 4;
  private static final int AUDIO_FORMAT_NELLYMOSER_8KHZ_MONO = 5;
  private static final int AUDIO_FORMAT_NELLYMOSER = 6;
  private static final int AUDIO_FORMAT_G711_A_LAW = 7;
  private static final int AUDIO_FORMAT_G711_MU_LAW = 8;
  private static final int AUDIO_FORMAT_RESERVED = 9;
  private static final int AUDIO_FORMAT_AAC = 10;
  private static final int AUDIO_FORMAT_SPEEX = 11;
  private static final int AUDIO_FORMAT_MP3_8KHZ = 14;
  private static final int AUDIO_FORMAT_DEVICE_SPECIFIC = 15;

  // AAC PACKET TYPE
  private static final int AAC_PACKET_TYPE_SEQUENCE_HEADER = 0;
  private static final int AAC_PACKET_TYPE_AAC_RAW = 1;

  private static final int[] AUDIO_SAMPLING_RATE_TABLE = new int[] {
      5500, 11000, 22000, 44000
  };

  private int format;
  private int sampleRate;
  private int bitsPerSample;
  private int channels;

  private boolean hasParsedAudioData;
  private boolean hasOutputFormat;

  /**
   * @param output A {@link TrackOutput} to which samples should be written.
   */
  public AudioTagReader(TrackOutput output) {
    super(output);
  }

  @Override
  public void seek() {

  }

  @Override
  protected void parseHeader(ParsableByteArray data) throws UnsupportedTrack {
    if (!hasParsedAudioData) {
      int header = data.readUnsignedByte();
      int soundFormat = (header >> 4) & 0x0F;
      int sampleRateIndex = (header >> 2) & 0x03;
      int bitsPerSample = (header & 0x02) == 0x02 ? 16 : 8;
      int channels = (header & 0x01) + 1;

      if (sampleRateIndex < 0 || sampleRateIndex >= AUDIO_SAMPLING_RATE_TABLE.length) {
        throw new UnsupportedTrack("Invalid sample rate for the audio track");
      }

      if (!hasOutputFormat) {
        switch (soundFormat) {
          // raw audio data. Just creates media format
          case AUDIO_FORMAT_LINEAR_PCM_LITTLE_ENDIAN:
            output.format(MediaFormat.createAudioFormat(MimeTypes.AUDIO_RAW, MediaFormat.NO_VALUE,
                MediaFormat.NO_VALUE, MediaFormat.NO_VALUE, channels,
                AUDIO_SAMPLING_RATE_TABLE[sampleRateIndex], null, null));
            hasOutputFormat = true;
            break;

          case AUDIO_FORMAT_AAC:
            break;

          case AUDIO_FORMAT_MP3:
          case AUDIO_FORMAT_MP3_8KHZ:
          case AUDIO_FORMAT_LINEAR_PCM_PLATFORM_ENDIAN:
          default:
            throw new UnsupportedTrack("Audio track not supported. Format: " + soundFormat +
                ", Sample rate: " + sampleRateIndex + ", bps: " + bitsPerSample + ", channels: " +
                channels);
        }
      }

      this.format = soundFormat;
      this.sampleRate = AUDIO_SAMPLING_RATE_TABLE[sampleRateIndex];
      this.bitsPerSample = bitsPerSample;
      this.channels = channels;

      hasParsedAudioData = true;
    } else {
      data.skipBytes(1);
    }
  }

  @Override
  protected void parsePayload(ParsableByteArray data, long timeUs) {
    int packetType = data.readUnsignedByte();
    if (packetType == AAC_PACKET_TYPE_SEQUENCE_HEADER && !hasOutputFormat) {
      ParsableBitArray adtsScratch = new ParsableBitArray(new byte[data.bytesLeft()]);
      data.readBytes(adtsScratch.data, 0, data.bytesLeft());

      int audioObjectType = adtsScratch.readBits(5);
      int sampleRateIndex = adtsScratch.readBits(4);
      int channelConfig = adtsScratch.readBits(4);

      byte[] audioSpecificConfig = CodecSpecificDataUtil.buildAacAudioSpecificConfig(
          audioObjectType, sampleRateIndex, channelConfig);
      Pair<Integer, Integer> audioParams = CodecSpecificDataUtil.parseAacAudioSpecificConfig(
          audioSpecificConfig);

      MediaFormat mediaFormat = MediaFormat.createAudioFormat(MimeTypes.AUDIO_AAC,
          MediaFormat.NO_VALUE, MediaFormat.NO_VALUE, durationUs, audioParams.second,
          audioParams.first, Collections.singletonList(audioSpecificConfig), null);

      output.format(mediaFormat);
      hasOutputFormat = true;
    } else if (packetType == AAC_PACKET_TYPE_AAC_RAW) {
      int bytesToWrite = data.bytesLeft();
      output.sampleData(data, bytesToWrite);
      output.sampleMetadata(timeUs, C.SAMPLE_FLAG_SYNC, bytesToWrite, 0, null);

      Log.d(TAG, "AAC TAG. Size: " + bytesToWrite + ", timeUs: " + timeUs);
    }
  }

  @Override
  protected boolean shouldParsePayload() {
    return (format == AUDIO_FORMAT_AAC);
  }

}
