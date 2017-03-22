/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.google.android.exoplayer2.upstream.rtmp.amf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author leoma
 */
public class AmfUndefined implements AmfData {

  @Override
  public void writeTo(OutputStream out) throws IOException {
    out.write(AmfType.UNDEFINED.getValue());
  }

  @Override
  public void readFrom(InputStream in) throws IOException {
    // Skip data type byte (we assume it's already read)
  }

  public static void writeUndefinedTo(OutputStream out) throws IOException {
    out.write(AmfType.UNDEFINED.getValue());
  }

  @Override
  public int getSize() {
    return 1;
  }
}
