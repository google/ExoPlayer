package com.google.android.exoplayer2.text.dvb;

import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.util.Log;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Parses {@link Cue}s from a DVB Teletext subtitle bitstream. */
/* package */ final class DvbTeletextSubtitleParser {

  private static final String TAG = "DvbTeletextSubParser";

  private static final int TTX_HEIGHT = 576;
  private static final int TTX_COLS = 40;
  private static final int TTX_ROWS = 24;
  private static final int CHAR_HEIGHT = 21;

  private static final int[] TXT_COLORS = {
      0xff000000, // Alpha Black
      0xffff0000, // Alpha Red
      0xff00ff00, // Alpha Green
      0xffffff00, // Alpha Yellow
      0xff0000ff, // Alpha Blue
      0xffff00ff, // Alpha Magenta
      0xff00ffff, // Alpha Cyan
      0xffffffff, // Alpha White
  };

  private static final short[] TAB_REVERT_BITS = {
      0x00, 0x80, 0x40, 0xc0, 0x20, 0xa0, 0x60, 0xe0,
      0x10, 0x90, 0x50, 0xd0, 0x30, 0xb0, 0x70, 0xf0,
      0x08, 0x88, 0x48, 0xc8, 0x28, 0xa8, 0x68, 0xe8,
      0x18, 0x98, 0x58, 0xd8, 0x38, 0xb8, 0x78, 0xf8,

      0x04, 0x84, 0x44, 0xc4, 0x24, 0xa4, 0x64, 0xe4,
      0x14, 0x94, 0x54, 0xd4, 0x34, 0xb4, 0x74, 0xf4,
      0x0c, 0x8c, 0x4c, 0xcc, 0x2c, 0xac, 0x6c, 0xec,
      0x1c, 0x9c, 0x5c, 0xdc, 0x3c, 0xbc, 0x7c, 0xfc,

      0x02, 0x82, 0x42, 0xc2, 0x22, 0xa2, 0x62, 0xe2,
      0x12, 0x92, 0x52, 0xd2, 0x32, 0xb2, 0x72, 0xf2,
      0x0a, 0x8a, 0x4a, 0xca, 0x2a, 0xaa, 0x6a, 0xea,
      0x1a, 0x9a, 0x5a, 0xda, 0x3a, 0xba, 0x7a, 0xfa,

      0x06, 0x86, 0x46, 0xc6, 0x26, 0xa6, 0x66, 0xe6,
      0x16, 0x96, 0x56, 0xd6, 0x36, 0xb6, 0x76, 0xf6,
      0x0e, 0x8e, 0x4e, 0xce, 0x2e, 0xae, 0x6e, 0xee,
      0x1e, 0x9e, 0x5e, 0xde, 0x3e, 0xbe, 0x7e, 0xfe,

      0x01, 0x81, 0x41, 0xc1, 0x21, 0xa1, 0x61, 0xe1,
      0x11, 0x91, 0x51, 0xd1, 0x31, 0xb1, 0x71, 0xf1,
      0x09, 0x89, 0x49, 0xc9, 0x29, 0xa9, 0x69, 0xe9,
      0x19, 0x99, 0x59, 0xd9, 0x39, 0xb9, 0x79, 0xf9,

      0x05, 0x85, 0x45, 0xc5, 0x25, 0xa5, 0x65, 0xe5,
      0x15, 0x95, 0x55, 0xd5, 0x35, 0xb5, 0x75, 0xf5,
      0x0d, 0x8d, 0x4d, 0xcd, 0x2d, 0xad, 0x6d, 0xed,
      0x1d, 0x9d, 0x5d, 0xdd, 0x3d, 0xbd, 0x7d, 0xfd,

      0x03, 0x83, 0x43, 0xc3, 0x23, 0xa3, 0x63, 0xe3,
      0x13, 0x93, 0x53, 0xd3, 0x33, 0xb3, 0x73, 0xf3,
      0x0b, 0x8b, 0x4b, 0xcb, 0x2b, 0xab, 0x6b, 0xeb,
      0x1b, 0x9b, 0x5b, 0xdb, 0x3b, 0xbb, 0x7b, 0xfb,

      0x07, 0x87, 0x47, 0xc7, 0x27, 0xa7, 0x67, 0xe7,
      0x17, 0x97, 0x57, 0xd7, 0x37, 0xb7, 0x77, 0xf7,
      0x0f, 0x8f, 0x4f, 0xcf, 0x2f, 0xaf, 0x6f, 0xef,
      0x1f, 0x9f, 0x5f, 0xdf, 0x3f, 0xbf, 0x7f, 0xff
  };

  // 7bit with 1 bit Odd parity
  private static final byte[] PARITY_TABLE = {
      0, 33, 34, 3, 35, 2, 1, 32, 36, 5, 6, 39, 7, 38, 37, 4,
      37, 4, 7, 38, 6, 39, 36, 5, 1, 32, 35, 2, 34, 3, 0, 33,
      38, 7, 4, 37, 5, 36, 39, 6, 2, 35, 32, 1, 33, 0, 3, 34,
      3, 34, 33, 0, 32, 1, 2, 35, 39, 6, 5, 36, 4, 37, 38, 7,
      39, 6, 5, 36, 4, 37, 38, 7, 3, 34, 33, 0, 32, 1, 2, 35,
      2, 35, 32, 1, 33, 0, 3, 34, 38, 7, 4, 37, 5, 36, 39, 6,
      1, 32, 35, 2, 34, 3, 0, 33, 37, 4, 7, 38, 6, 39, 36, 5,
      36, 5, 6, 39, 7, 38, 37, 4, 0, 33, 34, 3, 35, 2, 1, 32,
      40, 9, 10, 43, 11, 42, 41, 8, 12, 45, 46, 15, 47, 14, 13, 44,
      13, 44, 47, 14, 46, 15, 12, 45, 41, 8, 11, 42, 10, 43, 40, 9,
      14, 47, 44, 13, 45, 12, 15, 46, 42, 11, 8, 41, 9, 40, 43, 10,
      43, 10, 9, 40, 8, 41, 42, 11, 15, 46, 45, 12, 44, 13, 14, 47,
      15, 46, 45, 12, 44, 13, 14, 47, 43, 10, 9, 40, 8, 41, 42, 11,
      42, 11, 8, 41, 9, 40, 43, 10, 14, 47, 44, 13, 45, 12, 15, 46,
      41, 8, 11, 42, 10, 43, 40, 9, 13, 44, 47, 14, 46, 15, 12, 45,
      12, 45, 46, 15, 47, 14, 13, 44, 40, 9, 10, 43, 11, 42, 41, 8
  };

  /**
   * Decoded value is in the lowest 4 bits
   * if an error has been recovered bit 6 is set
   * if an uncorrectable error has been encountered bit 7 is set
   */
  private static final short[] HAMMING_8_4_TABLE = {
      0x41, 0x8F, 0x01, 0x41, 0x8F, 0x40, 0x41, 0x8F,
      0x8F, 0x42, 0x41, 0x8F, 0x4A, 0x8F, 0x8F, 0x47,
      0x8F, 0x40, 0x41, 0x8F, 0x40, 0x00, 0x8F, 0x40,
      0x46, 0x8F, 0x8F, 0x4B, 0x8F, 0x40, 0x43, 0x8F,
      0x8F, 0x4C, 0x41, 0x8F, 0x44, 0x8F, 0x8F, 0x47,
      0x46, 0x8F, 0x8F, 0x47, 0x8F, 0x47, 0x47, 0x07,
      0x46, 0x8F, 0x8F, 0x45, 0x8F, 0x40, 0x4D, 0x8F,
      0x06, 0x46, 0x46, 0x8F, 0x46, 0x8F, 0x8F, 0x47,
      0x8F, 0x42, 0x41, 0x8F, 0x44, 0x8F, 0x8F, 0x49,
      0x42, 0x02, 0x8F, 0x42, 0x8F, 0x42, 0x43, 0x8F,
      0x48, 0x8F, 0x8F, 0x45, 0x8F, 0x40, 0x43, 0x8F,
      0x8F, 0x42, 0x43, 0x8F, 0x43, 0x8F, 0x03, 0x43,
      0x44, 0x8F, 0x8F, 0x45, 0x04, 0x44, 0x44, 0x8F,
      0x8F, 0x42, 0x4F, 0x8F, 0x44, 0x8F, 0x8F, 0x47,
      0x8F, 0x45, 0x45, 0x05, 0x44, 0x8F, 0x8F, 0x45,
      0x46, 0x8F, 0x8F, 0x45, 0x8F, 0x4E, 0x43, 0x8F,
      0x8F, 0x4C, 0x41, 0x8F, 0x4A, 0x8F, 0x8F, 0x49,
      0x4A, 0x8F, 0x8F, 0x4B, 0x0A, 0x4A, 0x4A, 0x8F,
      0x48, 0x8F, 0x8F, 0x4B, 0x8F, 0x40, 0x4D, 0x8F,
      0x8F, 0x4B, 0x4B, 0x0B, 0x4A, 0x8F, 0x8F, 0x4B,
      0x4C, 0x0C, 0x8F, 0x4C, 0x8F, 0x4C, 0x4D, 0x8F,
      0x8F, 0x4C, 0x4F, 0x8F, 0x4A, 0x8F, 0x8F, 0x47,
      0x8F, 0x4C, 0x4D, 0x8F, 0x4D, 0x8F, 0x0D, 0x4D,
      0x46, 0x8F, 0x8F, 0x4B, 0x8F, 0x4E, 0x4D, 0x8F,
      0x48, 0x8F, 0x8F, 0x49, 0x8F, 0x49, 0x49, 0x09,
      0x8F, 0x42, 0x4F, 0x8F, 0x4A, 0x8F, 0x8F, 0x49,
      0x08, 0x48, 0x48, 0x8F, 0x48, 0x8F, 0x8F, 0x49,
      0x48, 0x8F, 0x8F, 0x4B, 0x8F, 0x4E, 0x43, 0x8F,
      0x8F, 0x4C, 0x4F, 0x8F, 0x44, 0x8F, 0x8F, 0x49,
      0x4F, 0x8F, 0x0F, 0x4F, 0x8F, 0x4E, 0x4F, 0x8F,
      0x48, 0x8F, 0x8F, 0x45, 0x8F, 0x4E, 0x4D, 0x8F,
      0x8F, 0x4E, 0x4F, 0x8F, 0x4E, 0x0E, 0x8F, 0x4E,
  };

  private static final int[] SPECIAL_CHARS_IDX;
  private static final char[][] SPECIAL_CHARS = {
      /* for latin-1 font */
      /* English (100%) */
      {0, '£', '$', '@', '«', '½', '»', '¬', '#', '­', '¼', '¦', '¾', '÷'},
      /* German (100%) */
      {0, '#', '$', '§', 'Ä', 'Ö', 'Ü', '^', '_', '°', 'ä', 'ö', 'ü', 'ß'},
      /* Swedish/Finnish/Hungarian (100%) */
      {0, '#', '¤', 'É', 'Ä', 'Ö', 'Å', 'Ü', '_', 'é', 'ä', 'ö', 'å', 'ü'},
      /* Italian (100%) */
      {0, '£', '$', 'é', '°', 'ç', '»', '¬', '#', 'ù', 'à', 'ò', 'è', 'ì'},
      /* French (100%) */
      {0, 'é', 'ï', 'à', 'ë', 'ê', 'ù', 'î', '#', 'è', 'â', 'ô', 'û', 'ç'},
      /* Portuguese/Spanish (100%) */
      {0, 'ç', '$', '¡', 'á', 'é', 'í', 'ó', 'ú', '¿', 'ü', 'ñ', 'è', 'à'},
      /* Czech/Slovak (60%) */
      {0, '#', 'u', 'c', 't', 'z', 'ý', 'í', 'r', 'é', 'á', 'e', 'ú', 's'},
      /* reserved (English mapping) */
      {0, '£', '$', '@', '«', '½', '»', '¬', '#', '­', '¼', '¦', '¾', '÷'},

      /* for latin-2 font */
      /* Polish (100%) */
      {0, '#', 'ñ', '±', '¯', '¦', '£', 'æ', 'ó', 'ê', '¿', '¶', '³', '¼'},
      /* German (100%) */
      {0, '#', '$', '§', 'Ä', 'Ö', 'Ü', '^', '_', '°', 'ä', 'ö', 'ü', 'ß'},
      /* Estonian (100%) */
      {0, '#', 'õ', '©', 'Ä', 'Ö', '®', 'Ü', 'Õ', '¹', 'ä', 'ö', '¾', 'ü'},
      /* Lettish/Lithuanian (90%) */
      {0, '#', '$', '©', 'ë', 'ê', '®', 'è', 'ü', '¹', '±', 'u', '¾', 'i'},
      /* French (90%) */
      {0, 'é', 'i', 'a', 'ë', 'ì', 'u', 'î', '#', 'e', 'â', 'ô', 'u', 'ç'},
      /* Serbian/Croation/Slovenian (100%) */
      {0, '#', 'Ë', 'È', 'Æ', '®', 'Ð', '©', 'ë', 'è', 'æ', '®', 'ð', '¹'},
      /* Czech/Slovak (100%) */
      {0, '#', 'ù', 'è', '»', '¾', 'ý', 'í', 'ø', 'é', 'á', 'ì', 'ú', '¹'},
      /* Rumanian (95%) */
      {0, '#', '¢', 'Þ', 'Â', 'ª', 'Ã', 'Î', 'i', 'þ', 'â', 'º', 'ã', 'î'},
  };

  private static final int[] CHARSET_INDEXES = {0, 4, 2, 6, 1, 5, 3, 7};

  private static void checkParity(byte[] data, int offset, int len) {
    for (int i = 0; i < len; ++i) {
      int a = data[offset + i] & 0xFF;
      if ((PARITY_TABLE[a] & 32) == 0) {
        Log.w(TAG, "parity error at " + (offset + i));
      }
      data[offset + i] &= 0x7F;
    }
  }

  // revert bits order from LSB first to MSB first
  private static void revertBytes(byte[] data, int offset, int len) {
    for (int i = 0; i < len; ++i) {
      data[offset + i] = (byte) (TAB_REVERT_BITS[data[offset + i] & 0xFF] & 0xFF);
    }
  }

  private static int hamming84Decode(int d0) {
    int a = HAMMING_8_4_TABLE[d0];
    if ((a & 0x80) != 0) {
      Log.w(TAG, "uncorrectable hamming error");
    }
    return a & 0x0F;
  }

  private static int hamming84Decode(int d0, int d1) {
    /* convert two consecutive hamming 8/4 encoded bytes */
    int a = HAMMING_8_4_TABLE[d0];
    int b = HAMMING_8_4_TABLE[d1];
    if ((a & 0x80) != 0 || (b & 0x80) != 0) {
      Log.w(TAG, "uncorrectable hamming error");
    }
    return ((b & 0x0F) << 4) | (a & 0x0F);
  }

  private static void handleSpecialChars(byte[] data, int offset, int len, int lang) {
    boolean mosaic = false;
    for (int i = offset; i < offset + len; ++i) {
      int c = data[i] & 0xFF;
      if (c >= 0x20) {
        int rep = SPECIAL_CHARS_IDX[c];
        if (rep != 0) {
          if (!mosaic || ((rep >= 0x40) && (rep <= 0x50))) {
            data[i] = (byte) (SPECIAL_CHARS[lang][rep] & 0xFF);
          }
        }
      } else {
        mosaic = c >= 0x10;
      }
    }
  }

  static {
    SPECIAL_CHARS_IDX = new int[256];
    SPECIAL_CHARS_IDX[0x23] = 1;
    SPECIAL_CHARS_IDX[0x24] = 2;
    SPECIAL_CHARS_IDX[0x40] = 3;
    SPECIAL_CHARS_IDX[0x5b] = 4;
    SPECIAL_CHARS_IDX[0x5c] = 5;
    SPECIAL_CHARS_IDX[0x5d] = 6;
    SPECIAL_CHARS_IDX[0x5e] = 7;
    SPECIAL_CHARS_IDX[0x5f] = 8;
    SPECIAL_CHARS_IDX[0x60] = 9;
    SPECIAL_CHARS_IDX[0x7b] = 10;
    SPECIAL_CHARS_IDX[0x7c] = 11;
    SPECIAL_CHARS_IDX[0x7d] = 12;
    SPECIAL_CHARS_IDX[0x7e] = 13;
  }

  private final int magazineNumber;
  private final int pageNumber;
  private final TeletextPage page = new TeletextPage();
  private final ArrayList<Cue> cues = new ArrayList<>();

  /**
   * Construct an instance for the given magazine number and page number.
   *
   * @param magazineNumber The id of the subtitle magazine carrying the subtitle to be parsed.
   * @param pageNumber The id of the subtitle page carrying the subtitle to be parsed.
   */
  public DvbTeletextSubtitleParser(int magazineNumber, int pageNumber) {
    this.magazineNumber = magazineNumber;
    this.pageNumber = pageNumber;
  }

  /** Resets the parser. */
  public void reset() {
    page.clear();
    cues.clear();
  }

  /**
   * Decodes a subtitling packet, returning a list of parsed {@link Cue}s.
   *
   * @param data The subtitling packet data to decode.
   * @param size The limit in {@code data} at which to stop decoding.
   * @return The parsed {@link Cue}s.
   */
  public List<Cue> decode(byte[] data, int size) {
    int offset = 0;
    int len = size;

    int dataIdentifier = data[offset++] & 0xFF;
    len -= 1;

    if (dataIdentifier < 0x10 || dataIdentifier > 0x1F) {
      // ignore invalid data identifiers
      return Collections.emptyList();
    }

    while (len > 2) {
      int dataUnitId = data[offset++] & 0xFF;
      int dataUnitLength = data[offset++] & 0xFF;
      len -= 2;

      if (dataUnitLength > len) {
        // ignore invalid data unit length
        break;
      }

      switch (dataUnitId) {
        case 0x02:
          // EBU Teletext non-subtitle data
          break;
        case 0x03:
          // EBU Teletext subtitle data
          @Nullable TeletextLine line = processTeletextSubtitleField(data, offset, dataUnitLength);
          if (line != null) {
            if (line.erase) {
              cues.clear();
            }
            cues.add(buildCue(line));
          }
          break;
        case 0xFF:
          // stuffing
          break;
        default:
          // ignore unknown data unit id
          break;
      }

      offset += dataUnitLength;
      len -= dataUnitLength;
    }
    return new ArrayList<>(cues);
  }

  private Cue buildCue(TeletextLine line) {
    Cue.Builder cueBuilder = new Cue.Builder();
    byte[] printable = new byte[TTX_COLS];
    int color = TXT_COLORS[7];
    float size = (float) CHAR_HEIGHT / TTX_HEIGHT;
    boolean box = false;
    boolean newBox = false;

    for (int i = 0; i < line.line.length; ++i) {
      int c = line.line[i] & 0xFF;
      if (c < 0x20) {
        if (c > 0 && c < 8) {
          color = TXT_COLORS[c];
        } else if (c == 0xa) {
          newBox = false;
        } else if (c == 0xb) {
          newBox = true;
        } else if (c == 0xd) {
          size = (float) CHAR_HEIGHT / TTX_HEIGHT * 2;
        }
        c = ' ';
      }

      if (!box) {
        c = ' ';
      }
      printable[i] = (byte) c;
      box = newBox;
    }

    String latin1Line;
    try {
      String charsetName;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        charsetName = StandardCharsets.ISO_8859_1.name();
      } else {
        charsetName = "ISO-8859-1";
      }
      latin1Line = new String(printable, 0, printable.length, charsetName);
    } catch (UnsupportedEncodingException e) {
      latin1Line = "";
    }
    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(latin1Line);
    stringBuilder.setSpan(new ForegroundColorSpan(color), 0, latin1Line.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    stringBuilder.setSpan(new BackgroundColorSpan(0x00000000), 0, latin1Line.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return cueBuilder
        .setText(stringBuilder)
        .setTextSize(size, Cue.TEXT_SIZE_TYPE_FRACTIONAL)
        .setLine((float) line.row / (TTX_ROWS + 1), Cue.LINE_TYPE_FRACTION)
        .build();
  }

  @Nullable
  TeletextLine processTeletextSubtitleField(byte[] data, int offset, int len) {
    if (len != 0x2C) {
      // ignore invalid data unit length
      return null;
    }

    int framingCode = data[offset + 1] & 0xFF;

    // see: ETS 300 706 / 6.2
    // The bit pattern in transmission order is: 1110 0100 (0xE4)
    if (framingCode != 0xE4) {
      // ignore invalid framing code
      return null;
    }

    revertBytes(data, offset, len);

    offset += 2;
    len -= 2;

    int magazineAndFrameAddr = hamming84Decode(data[offset] & 0xFF, data[offset + 1] & 0xFF);
    int magazine = magazineAndFrameAddr & 0x7;
    if (magazine == 0) {
      magazine = 8;
    }
    int packetNumber = (magazineAndFrameAddr >> 3) & 0x1F;

    offset += 2;
    len -= 2;

    if (magazine != magazineNumber) {
      return null;
    }

    if (packetNumber > 25) {
      // non displayable
      return null;
    }

    // see: ETS 300 706 / 7.1.4 Packet types
    int rowOffset = 0;

    if (packetNumber == 0) {
      int pageNumber = hamming84Decode(data[offset] & 0xFF, data[offset + 1] & 0xFF);
      if (pageNumber == 0xFF) {
        return null;
      }
      page.pageNumber = pageNumber;

      // page header
      int s2AndC4 = hamming84Decode(data[offset + 3] & 0xFF);
      page.erase = (s2AndC4 & 0x08) != 0;
      int s4AndC5C6 = hamming84Decode(data[offset + 5] & 0xFF);
      page.isNewsflash = (s4AndC5C6 & 0x4) != 0;
      page.isSubtitle = (s4AndC5C6 & 0x8) != 0;
      int c7C10 = hamming84Decode(data[offset + 6] & 0xFF);
      page.suppressHeader = (c7C10 & 0x1) != 0;
      page.inhibitDisplay = (c7C10 & 0x8) != 0;
      int c11C14 = hamming84Decode(data[offset + 7] & 0xFF);
      page.charset = CHARSET_INDEXES[c11C14 >> 1];
      offset += 8;
      len -= 8;
    }

    if (page.pageNumber != pageNumber) {
      return null;
    }

    if (packetNumber >= 1 && packetNumber <= 24 && page.inhibitDisplay) {
      page.erase = true;
      return updatePage(page, packetNumber);
    }

    // decode odd parity encoded
    checkParity(data, offset, len);

    if (packetNumber == 0 && page.suppressHeader) {
      for (int i = offset; i < offset + len; ++i) {
        data[i] = 0x20;
      }
    }

    handleSpecialChars(data, offset, len, page.charset);
    page.updateLine(packetNumber, data, offset, rowOffset, len);
    return updatePage(page, packetNumber);
  }

  private TeletextLine updatePage(@NonNull TeletextPage page, int row) {
    TeletextLine dr = new TeletextLine(row, page.erase, page.lines[row]);
    this.page.erase = false;
    return dr;
  }

  private static class TeletextPage {
    int pageNumber = -1;
    int charset;
    boolean isSubtitle;
    boolean isNewsflash;
    public boolean erase;
    public boolean suppressHeader;
    public boolean inhibitDisplay;
    public byte[][] lines = new byte[26][40];

    void clear() {
      erase = true;
      for (byte[] line : lines) {
        Arrays.fill(line, (byte) 0);
      }
    }

    void updateLine(int row, byte[] line, int offset, int rowOffset, int len) {
      System.arraycopy(line, offset, lines[row], rowOffset, len);
    }
  }

  private static class TeletextLine {
    boolean erase;
    int row;
    byte[] line;

    public TeletextLine(int row, boolean erase, byte[] line) {
      this.erase = erase;
      this.line = new byte[line.length];
      System.arraycopy(line, 0, this.line, 0, line.length);
      this.row = row;
    }
  }
}
