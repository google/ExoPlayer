package com.google.android.exoplayer2.upstream.rtmp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Misc utility method
 *
 * @author francois
 */
public class Util {

  private static final String HEXES = "0123456789ABCDEF";

  public static void writeUnsignedInt32(OutputStream out, int value) throws IOException {
    out.write((byte) (value >>> 24));
    out.write((byte) (value >>> 16));
    out.write((byte) (value >>> 8));
    out.write((byte) value);
  }

  public static int readUnsignedInt32(InputStream in) throws IOException {
    return ((in.read() & 0xff) << 24) | ((in.read() & 0xff) << 16) | ((in.read() & 0xff) << 8) | (in.read() & 0xff);
  }

  public static int readUnsignedInt24(InputStream in) throws IOException {
    return ((in.read() & 0xff) << 16) | ((in.read() & 0xff) << 8) | (in.read() & 0xff);
  }

  public static int readUnsignedInt16(InputStream in) throws IOException {
    return ((in.read() & 0xff) << 8) | (in.read() & 0xff);
  }

  public static void writeUnsignedInt24(OutputStream out, int value) throws IOException {
    out.write((byte) (value >>> 16));
    out.write((byte) (value >>> 8));
    out.write((byte) value);
  }

  public static void writeUnsignedInt16(OutputStream out, int value) throws IOException {
    out.write((byte) (value >>> 8));
    out.write((byte) value);
  }

  public static int toUnsignedInt32(byte[] bytes) {
    return (((int) bytes[0] & 0xff) << 24) | (((int) bytes[1] & 0xff) << 16) | (((int) bytes[2] & 0xff) << 8) | ((int) bytes[3] & 0xff);
  }

  public static int toUnsignedInt32LittleEndian(byte[] bytes) {
    return ((bytes[3] & 0xff) << 24) | ((bytes[2] & 0xff) << 16) | ((bytes[1] & 0xff) << 8) | (bytes[0] & 0xff);
  }

  public static void writeUnsignedInt32LittleEndian(OutputStream out, int value) throws IOException {
    out.write((byte) value);
    out.write((byte) (value >>> 8));
    out.write((byte) (value >>> 16));
    out.write((byte) (value >>> 24));
  }

  public static int toUnsignedInt24(byte[] bytes) {
    return ((bytes[1] & 0xff) << 16) | ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
  }

  public static int toUnsignedInt16(byte[] bytes) {
    return ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
  }

  public static String toHexString(byte[] raw) {
    if (raw == null) {
      return null;
    }
    final StringBuilder hex = new StringBuilder(2 * raw.length);
    for (final byte b : raw) {
      hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
    }
    return hex.toString();
  }

  public static String toHexString(byte b) {
    return new StringBuilder().append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F))).toString();
  }

  /**
   * Reads bytes from the specified inputstream into the specified target buffer until it is filled up
   */
  public static void readBytesUntilFull(InputStream in, byte[] targetBuffer) throws IOException {
    int totalBytesRead = 0;
    int read;
    final int targetBytes = targetBuffer.length;
    do {
      read = in.read(targetBuffer, totalBytesRead, (targetBytes - totalBytesRead));
      if (read != -1) {
        totalBytesRead += read;
      } else {
        throw new IOException("Unexpected EOF reached before read buffer was filled");
      }
    } while (totalBytesRead < targetBytes);
  }

  public static byte[] toByteArray(double d) {
    long l = Double.doubleToRawLongBits(d);
    return new byte[]{
        (byte) ((l >> 56) & 0xff),
        (byte) ((l >> 48) & 0xff),
        (byte) ((l >> 40) & 0xff),
        (byte) ((l >> 32) & 0xff),
        (byte) ((l >> 24) & 0xff),
        (byte) ((l >> 16) & 0xff),
        (byte) ((l >> 8) & 0xff),
        (byte) (l & 0xff),};
  }

  public static byte[] unsignedInt32ToByteArray(int value) throws IOException {
    return new byte[]{
        (byte) (value >>> 24),
        (byte) (value >>> 16),
        (byte) (value >>> 8),
        (byte) value};
  }

  public static double readDouble(InputStream in) throws IOException {
    long bits = ((long) (in.read() & 0xff) << 56) | ((long) (in.read() & 0xff) << 48) | ((long) (in.read() & 0xff) << 40) | ((long) (in.read() & 0xff) << 32) | ((in.read() & 0xff) << 24) | ((in.read() & 0xff) << 16) | ((in.read() & 0xff) << 8) | (in.read() & 0xff);
    return Double.longBitsToDouble(bits);
  }

  public static void writeDouble(OutputStream out, double d) throws IOException {
    long l = Double.doubleToRawLongBits(d);
    out.write(new byte[]{
        (byte) ((l >> 56) & 0xff),
        (byte) ((l >> 48) & 0xff),
        (byte) ((l >> 40) & 0xff),
        (byte) ((l >> 32) & 0xff),
        (byte) ((l >> 24) & 0xff),
        (byte) ((l >> 16) & 0xff),
        (byte) ((l >> 8) & 0xff),
        (byte) (l & 0xff)});
  }
}
