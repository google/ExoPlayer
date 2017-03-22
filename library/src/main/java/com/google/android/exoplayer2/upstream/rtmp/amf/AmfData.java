package com.google.android.exoplayer2.upstream.rtmp.amf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Base AMF data object. All other AMF data type instances derive from this
 * (including AmfObject)
 *
 * @author francois
 */
public interface AmfData {

  /**
   * Write/Serialize this AMF data intance (Object/string/integer etc) to
   * the specified OutputStream
   */
  void writeTo(OutputStream out) throws IOException;

  /**
   * Read and parse bytes from the specified input stream to populate this
   * AMFData instance (deserialize)
   *
   * @return the amount of bytes read
   */
  void readFrom(InputStream in) throws IOException;

  /**
   * @return the amount of bytes required for this object
   */
  int getSize();
}
