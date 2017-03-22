package com.google.android.exoplayer2.upstream.rtmp.amf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.String;

import android.util.Log;

import com.google.android.exoplayer2.upstream.rtmp.Util;

/**
 * @author francois
 */
public class AmfString implements AmfData {

  private static final String TAG = "AmfString";

  private String value;
  private boolean key;
  private int size = -1;

  public AmfString() {
  }

  public AmfString(String value, boolean isKey) {
    this.value = value;
    this.key = isKey;
  }

  public AmfString(String value) {
    this(value, false);
  }

  public AmfString(boolean isKey) {
    this.key = isKey;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public boolean isKey() {
    return key;
  }

  public void setKey(boolean key) {
    this.key = key;
  }

  @Override
  public void writeTo(OutputStream out) throws IOException {
    // Strings are ASCII encoded
    byte[] byteValue = this.value.getBytes("ASCII");
    // Write the STRING data type definition (except if this String is used as a key)
    if (!key) {
      out.write(AmfType.STRING.getValue());
    }
    // Write 2 bytes indicating string length
    Util.writeUnsignedInt16(out, byteValue.length);
    // Write string
    out.write(byteValue);
  }

  @Override
  public void readFrom(InputStream in) throws IOException {
    // Skip data type byte (we assume it's already read)
    int length = Util.readUnsignedInt16(in);
    size = 3 + length; // 1 + 2 + length
    // Read string value
    byte[] byteValue = new byte[length];
    Util.readBytesUntilFull(in, byteValue);
    value = new String(byteValue, "ASCII");
  }

  public static String readStringFrom(InputStream in, boolean isKey) throws IOException {
    if (!isKey) {
      // Read past the data type byte
      in.read();
    }
    int length = Util.readUnsignedInt16(in);
    // Read string value
    byte[] byteValue = new byte[length];
    Util.readBytesUntilFull(in, byteValue);
    return new String(byteValue, "ASCII");
  }

  public static void writeStringTo(OutputStream out, String string, boolean isKey) throws IOException {
    // Strings are ASCII encoded
    byte[] byteValue = string.getBytes("ASCII");
    // Write the STRING data type definition (except if this String is used as a key)
    if (!isKey) {
      out.write(AmfType.STRING.getValue());
    }
    // Write 2 bytes indicating string length
    Util.writeUnsignedInt16(out, byteValue.length);
    // Write string
    out.write(byteValue);
  }

  @Override
  public int getSize() {
    if (size == -1) {
      try {
        size = (isKey() ? 0 : 1) + 2 + value.getBytes("ASCII").length;
      } catch (UnsupportedEncodingException ex) {
        Log.e(TAG, "AmfString.getSize(): caught exception", ex);
        throw new RuntimeException(ex);
      }
    }
    return size;
  }

  /**
   * @return the byte size of the resulting AMF string of the specified value
   */
  public static int sizeOf(String string, boolean isKey) {
    try {
      int size = (isKey ? 0 : 1) + 2 + string.getBytes("ASCII").length;
      return size;
    } catch (UnsupportedEncodingException ex) {
      Log.e(TAG, "AmfString.SizeOf(): caught exception", ex);
      throw new RuntimeException(ex);
    }
  }
}
