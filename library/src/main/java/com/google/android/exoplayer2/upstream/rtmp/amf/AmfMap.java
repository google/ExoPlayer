package com.google.android.exoplayer2.upstream.rtmp.amf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import com.google.android.exoplayer2.upstream.rtmp.Util;

/**
 * AMF map; that is, an "object"-like structure of key/value pairs, but with
 * an array-like size indicator at the start (which is seemingly always 0)
 *
 * @author francois
 */
public class AmfMap extends AmfObject {

  @Override
  public void writeTo(OutputStream out) throws IOException {
    // Begin the map/object/array/whatever exactly this is
    out.write(AmfType.MAP.getValue());

    // Write the "array size"
    Util.writeUnsignedInt32(out, properties.size());

    // Write key/value pairs in this object
    for (Map.Entry<String, AmfData> entry : properties.entrySet()) {
      // The key must be a STRING type, and thus the "type-definition" byte is implied (not included in message)
      AmfString.writeStringTo(out, entry.getKey(), true);
      entry.getValue().writeTo(out);
    }

    // End the object
    out.write(OBJECT_END_MARKER);
  }

  @Override
  public void readFrom(InputStream in) throws IOException {
    // Skip data type byte (we assume it's already read)
    int length = Util.readUnsignedInt32(in); // Seems this is always 0
    super.readFrom(in);
    size += 4; // Add the bytes read for parsing the array size (length)
  }

  @Override
  public int getSize() {
    if (size == -1) {
      size = super.getSize();
      size += 4; // array length bytes
    }
    return size;
  }
}
