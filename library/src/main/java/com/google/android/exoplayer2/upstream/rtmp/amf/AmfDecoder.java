package com.google.android.exoplayer2.upstream.rtmp.amf;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author francois
 */
public class AmfDecoder {

  public static AmfData readFrom(InputStream in) throws IOException {

    byte amfTypeByte = (byte) in.read();
    AmfType amfType = AmfType.valueOf(amfTypeByte);

    AmfData amfData;
    switch (amfType) {
      case NUMBER:
        amfData = new AmfNumber();
        break;
      case BOOLEAN:
        amfData = new AmfBoolean();
        break;
      case STRING:
        amfData = new AmfString();
        break;
      case OBJECT:
        amfData = new AmfObject();
        break;
      case NULL:
        return new AmfNull();
      case UNDEFINED:
        return new AmfUndefined();
      case MAP:
        amfData = new AmfMap();
        break;
      case ARRAY:
        amfData = new AmfArray();
        break;
      default:
        throw new IOException("Unknown/unimplemented AMF data type: " + amfType);
    }

    amfData.readFrom(in);
    return amfData;
  }
}
