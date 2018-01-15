package com.google.android.exoplayer2.text.pgs;

import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.util.ParsableByteArray;

@SuppressWarnings("unused")
public class PgsDecoder extends SimpleSubtitleDecoder {

  @SuppressWarnings("unused")
  public PgsDecoder() {
    super("PgsDecoder");
  }

  @Override
  protected Subtitle decode(byte[] data, int size, boolean reset) throws SubtitleDecoderException {
    ParsableByteArray buffer = new ParsableByteArray(data, size);
    PgsBuilder builder = new PgsBuilder();
    do {
      if (!builder.readNextSection(buffer))
        break;
    } while (buffer.bytesLeft() > 0);
    return builder.build();
  }
}
