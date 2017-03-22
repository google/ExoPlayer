package com.google.android.exoplayer2.upstream.rtmp.amf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.google.android.exoplayer2.upstream.rtmp.Util;

/**
 * AMF0 Number data type
 *
 * @author francois
 */
public class AmfNumber implements AmfData {

  double value;
  /**
   * Size of an AMF number, in bytes (including type bit)
   */
  public static final int SIZE = 9;

  public AmfNumber(double value) {
    this.value = value;
  }

  public AmfNumber() {
  }

  public double getValue() {
    return value;
  }

  public void setValue(double value) {
    this.value = value;
  }

  @Override
  public void writeTo(OutputStream out) throws IOException {
    out.write(AmfType.NUMBER.getValue());
    Util.writeDouble(out, value);
  }

  @Override
  public void readFrom(InputStream in) throws IOException {
    // Skip data type byte (we assume it's already read)
    value = Util.readDouble(in);
  }

  public static double readNumberFrom(InputStream in) throws IOException {
    // Skip data type byte
    in.read();
    return Util.readDouble(in);
  }

  public static void writeNumberTo(OutputStream out, double number) throws IOException {
    out.write(AmfType.NUMBER.getValue());
    Util.writeDouble(out, number);
  }

  @Override
  public int getSize() {
    return SIZE;
  }

}
