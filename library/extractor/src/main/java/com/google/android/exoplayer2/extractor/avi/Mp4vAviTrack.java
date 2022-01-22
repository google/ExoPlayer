package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableNalUnitBitArray;
import java.io.IOException;

public class Mp4vAviTrack extends AviTrack {
  private static final byte SEQUENCE_START_CODE = (byte)0xb0;
  private static final int LAYER_START_CODE = 0x20;
  private static final float[] ASPECT_RATIO = {0f, 1f, 12f/11f, 10f/11f, 16f/11f, 40f/33f};
  private static final int Extended_PAR = 0xf;
  private final Format.Builder formatBuilder;
  private float pixelWidthHeightRatio = 1f;

  Mp4vAviTrack(int id, @NonNull StreamHeaderBox streamHeaderBox, @NonNull TrackOutput trackOutput,
      @NonNull Format.Builder formatBuilder) {
    super(id, streamHeaderBox, trackOutput);
    this.formatBuilder = formatBuilder;
  }

  private void processLayerStart(byte[] peek, int offset) {
    final ParsableNalUnitBitArray in = new ParsableNalUnitBitArray(peek, offset, peek.length);
    in.skipBit(); // random_accessible_vol
    in.skipBits(8); // video_object_type_indication
    boolean is_object_layer_identifier = in.readBit();
    if (is_object_layer_identifier) {
      in.skipBits(7); // video_object_layer_verid, video_object_layer_priority
    }
    int aspect_ratio_info = in.readBits(4);
    final float aspectRatio;
    if (aspect_ratio_info == Extended_PAR) {
      float par_width = (float)in.readBits(8);
      float par_height = (float)in.readBits(8);
      aspectRatio = par_width / par_height;
    } else {
      aspectRatio = ASPECT_RATIO[aspect_ratio_info];
    }
    if (aspectRatio != pixelWidthHeightRatio) {
      trackOutput.format(formatBuilder.setPixelWidthHeightRatio(aspectRatio).build());
      pixelWidthHeightRatio = aspectRatio;
    }
  }

  private void seekLayerStart(ExtractorInput input) throws IOException {
    byte[] peek = new byte[128];
    input.peekFully(peek, 0, peek.length);
    for (int i = 4;i<peek.length - 4;i++) {
      if (peek[i] == 0 && peek[i+1] == 0 && peek[i+2] == 1 && (peek[i+3] & 0xf0) == LAYER_START_CODE) {
        processLayerStart(peek, i+4);
        break;
      }
    }
  }

  @Override
  public boolean newChunk(int tag, int size, ExtractorInput input) throws IOException {
    final byte[] peek = new byte[4];
    input.peekFully(peek, 0, peek.length);
    if (peek[0] == 0 && peek[1] == 0 && peek[2] == 1 && peek[3] == SEQUENCE_START_CODE) {
      seekLayerStart(input);
    }
    return super.newChunk(tag, size, input);
  }
}
