package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.ParsableNalUnitBitArray;
import java.io.IOException;

public class AvcAviTrack extends AviTrack{
  private static final int NAL_TYPE_IRD = 5;
  private static final int NAL_TYPE_SEI = 6;
  private static final int NAL_TYPE_SPS = 7;
  private static final int NAL_MASK = 0x1f;
  private Format.Builder formatBuilder;
  private float pixelWidthHeightRatio = 1f;
  private NalUnitUtil.SpsData spsData;
  //The frame as a calculated from the picCount
  private int picFrame;
  private int lastPicFrame;
  //Largest picFrame, used when we hit an I frame
  private int maxPicFrame =-1;
  private int maxPicCount;
  private int posHalf;
  private int negHalf;

  AvcAviTrack(int id, @NonNull StreamHeaderBox streamHeaderBox, @NonNull TrackOutput trackOutput,
      @NonNull Format.Builder formatBuilder) {
    super(id, streamHeaderBox, trackOutput);
    this.formatBuilder = formatBuilder;
  }

  public void setFormatBuilder(Format.Builder formatBuilder) {
    this.formatBuilder = formatBuilder;
  }

  private int seekNal(final ParsableByteArray parsableByteArray) {
    final byte[] buffer = parsableByteArray.getData();
    for (int i=parsableByteArray.getPosition();i<buffer.length - 5;i++) {
      if (buffer[i] == 0 && buffer[i+1] == 0) {
        if (buffer[i+2] == 1) {
          parsableByteArray.setPosition(i+3);
        } else if (buffer[i+2] == 0 && buffer[i+3] == 1) {
          parsableByteArray.setPosition(i+4);
        } else {
          continue;
        }
        return (parsableByteArray.readUnsignedByte() & NAL_MASK);
      }
    }
    return -1;
  }

  private void processIdr() {
    lastPicFrame = 0;
    picFrame = maxPicFrame + 1;
  }

  private void readSps(int size, ExtractorInput input) throws IOException {
    final byte[] buffer = new byte[size];
    input.readFully(buffer, 0, size, false);
    final ParsableByteArray parsableByteArray = new ParsableByteArray(buffer);
    int nal;
    while ((nal = seekNal(parsableByteArray)) >= 0) {
      if (nal == NAL_TYPE_SPS) {
        spsData = NalUnitUtil.parseSpsNalUnitPayload(parsableByteArray.getData(), parsableByteArray.getPosition(), parsableByteArray.capacity());
        maxPicCount = 1 << (spsData.picOrderCntLsbLength);
        posHalf = maxPicCount / 2;  //Not sure why pics are 2x
        negHalf = -posHalf;
        //Not sure if this works after the fact
        if (spsData.pixelWidthHeightRatio != pixelWidthHeightRatio) {
          formatBuilder.setPixelWidthHeightRatio(spsData.pixelWidthHeightRatio);
          trackOutput.format(formatBuilder.build());
        }
        Log.d(AviExtractor.TAG, "SPS Frame: maxPicCount=" + maxPicCount);
      } else if (nal == NAL_TYPE_IRD) {
        processIdr();
      }
    }
    parsableByteArray.setPosition(0);
    trackOutput.sampleData(parsableByteArray, parsableByteArray.capacity());
    int flags = 0;
    if (isKeyFrame()) {
      flags |= C.BUFFER_FLAG_KEY_FRAME;
    }
    trackOutput.sampleMetadata(getUs(frame), flags, parsableByteArray.capacity(), 0, null);
    Log.d(AviExtractor.TAG, "SPS Frame: " + (isVideo()? 'V' : 'A') + " us=" + getUs() + " size=" + size + " frame=" + frame + " usFrame=" + getUsFrame());
    advance();
  }

  @Override
  int getUsFrame() {
    return picFrame;
  }

  int getPicOrderCountLsb(byte[] peek) {
    if (peek[3] != 1) {
      return 0;
    }
    final ParsableNalUnitBitArray in = new ParsableNalUnitBitArray(peek, 5, peek.length);
    //slide_header()
    in.readUnsignedExpGolombCodedInt(); //first_mb_in_slice
    in.readUnsignedExpGolombCodedInt(); //slice_type
    in.readUnsignedExpGolombCodedInt(); //pic_parameter_set_id
    if (spsData.separateColorPlaneFlag) {
      in.skipBits(2); //colour_plane_id
    }
    in.readBits(spsData.frameNumLength); //frame_num
    if (!spsData.frameMbsOnlyFlag) {
      boolean field_pic_flag = in.readBit(); // field_pic_flag
      if (field_pic_flag) {
        in.readBit(); // bottom_field_flag
      }
    }
    //We skip IDR in the switch
    if (spsData.picOrderCountType == 0) {
      int picOrderCountLsb = in.readBits(spsData.picOrderCntLsbLength);
      Log.d("Test", "FrameNum: " + frame + " cnt=" + picOrderCountLsb);
      return picOrderCountLsb;
    }
    return 0;
  }

  @Override
  public boolean newChunk(int tag, int size, ExtractorInput input) throws IOException {
    final int peekSize = Math.min(size, 16);
    byte[] peek = new byte[peekSize];
    input.peekFully(peek, 0, peekSize);
    final int nalType = peek[4] & NAL_MASK;
    switch (nalType) {
      case 1:
      case 2:
      case 3:
      case 4: {
        final int myPicCount = getPicOrderCountLsb(peek);
        int delta = myPicCount - lastPicFrame;
        if (delta < negHalf) {
          delta += maxPicCount;
        } else if (delta > posHalf) {
          delta -= maxPicCount;
        }
        picFrame += delta / 2;
        lastPicFrame = myPicCount;
        if (maxPicFrame < picFrame) {
          maxPicFrame = picFrame;
        }
        break;
      }
      case NAL_TYPE_IRD:
        processIdr();
        break;
      case NAL_TYPE_SEI:
      case NAL_TYPE_SPS:
        readSps(size, input);
        return true;
    }
    return super.newChunk(tag, size, input);
  }

  public static String toString(byte[] buffer, int i, final int len) {
    final StringBuilder sb = new StringBuilder((len - i) * 3);
    while (i < len) {
      String hex = Integer.toHexString(buffer[i] & 0xff);
      if (hex.length() == 1) {
        sb.append('0');
      }
      sb.append(hex);
      sb.append(' ');
      i++;
    }
    return sb.toString();
  }
}
