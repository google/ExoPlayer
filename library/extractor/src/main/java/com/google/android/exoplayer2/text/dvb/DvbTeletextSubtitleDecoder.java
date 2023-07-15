package com.google.android.exoplayer2.text.dvb;

import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.common.primitives.UnsignedBytes;
import java.util.List;

/** A {@link SimpleSubtitleDecoder} for DVB Teletext subtitles. */
public final class DvbTeletextSubtitleDecoder extends SimpleSubtitleDecoder {

  private final DvbTeletextSubtitleParser parser;

  /**
   * @param initializationData The initialization data for the decoder. The initialization data must
   *     consist of a single byte array containing 2 bytes: magazine_number (1), page_number (2).
   */
  public DvbTeletextSubtitleDecoder(List<byte[]> initializationData) {
    super("DvbTeletextSubtitleDecoder");
    byte[] data = initializationData.get(0);
    int magazineNumber = UnsignedBytes.toInt(data[0]);
    if (magazineNumber == 0) {
      magazineNumber = 8;
    }
    int pageNumber = UnsignedBytes.toInt(data[1]);
    parser = new DvbTeletextSubtitleParser(magazineNumber, pageNumber);
  }

  @Override
  protected Subtitle decode(byte[] data, int size, boolean reset) throws SubtitleDecoderException {
    if (reset) {
      parser.reset();
    }
    return new DvbSubtitle(parser.decode(data, size));
  }
}
