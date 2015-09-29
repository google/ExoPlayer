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
public class VideoTagReader extends TagReader{
  private static final String TAG = "VideoTagReader";

  // Video codec
  private static final int VIDEO_CODEC_JPEG = 1;
  private static final int VIDEO_CODEC_H263 = 2;
  private static final int VIDEO_CODEC_SCREEN_VIDEO = 3;
  private static final int VIDEO_CODEC_VP6 = 4;
  private static final int VIDEO_CODEC_VP6_WITH_ALPHA_CHANNEL = 5;
  private static final int VIDEO_CODEC_SCREEN_VIDEO_V2 = 6;
  private static final int VIDEO_CODEC_AVC = 7;

  // FRAME TYPE
  private static final int VIDEO_FRAME_KEYFRAME = 1;
  private static final int VIDEO_FRAME_INTERFRAME = 2;
  private static final int VIDEO_FRAME_DISPOSABLE_INTERFRAME = 3;
  private static final int VIDEO_FRAME_GENERATED_KEYFRAME = 4;
  private static final int VIDEO_FRAME_VIDEO_INFO = 5;

  // PACKET TYPE
  private static final int AVC_PACKET_TYPE_SEQUENCE_HEADER = 0;
  private static final int AVC_PACKET_TYPE_AVC_NALU = 1;
  private static final int AVC_PACKET_TYPE_AVC_END_OF_SEQUENCE = 2;

  private boolean hasOutputFormat;
  private int format;
  private int frameType;

  /**
   * @param output A {@link TrackOutput} to which samples should be written.
   */
  public VideoTagReader(TrackOutput output) {
    super(output);
  }

  @Override
  public void seek() {

  }

  @Override
  protected void parseHeader(ParsableByteArray data) throws UnsupportedTrack {
    int header = data.readUnsignedByte();
    int frameType = (header >> 4) & 0x0F;
    int videoCodec = (header & 0x0F);

    if (videoCodec != VIDEO_CODEC_AVC) {
      throw new UnsupportedTrack("Video codec not supported. Codec: " + videoCodec);
    }

    this.format = videoCodec;
    this.frameType = frameType;
  }

  @Override
  protected void parsePayload(ParsableByteArray data, long timeUs) {
    int packetType = data.readUnsignedByte();
    int compositionTime = data.readUnsignedInt24();
    if (packetType == AVC_PACKET_TYPE_SEQUENCE_HEADER && !hasOutputFormat) {
      ParsableBitArray videoSequence = new ParsableBitArray(new byte[data.bytesLeft()]);
      data.readBytes(videoSequence.data, 0, data.bytesLeft());


/*
      // Construct and output the format.
      output.format(MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, MediaFormat.NO_VALUE,
        MediaFormat.NO_VALUE, C.UNKNOWN_TIME_US, parsedSpsData.width, parsedSpsData.height,
        initializationData, MediaFormat.NO_VALUE, parsedSpsData.pixelWidthAspectRatio));
*/
//      output.format(mediaFormat);
      hasOutputFormat = true;
    } else if (packetType == AVC_PACKET_TYPE_AVC_NALU) {
      int bytesToWrite = data.bytesLeft();
      output.sampleData(data, bytesToWrite);
      output.sampleMetadata(timeUs, frameType == VIDEO_FRAME_KEYFRAME ? C.SAMPLE_FLAG_SYNC : 0,
          bytesToWrite, 0, null);

      Log.d(TAG, "AAC TAG. Size: " + bytesToWrite + ", timeUs: " + timeUs);
    }
  }

  @Override
  protected boolean shouldParsePayload() {
    return (format == VIDEO_CODEC_AVC && frameType != VIDEO_FRAME_VIDEO_INFO);
  }
}
