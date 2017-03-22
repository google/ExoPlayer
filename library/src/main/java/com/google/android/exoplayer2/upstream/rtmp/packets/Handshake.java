package com.google.android.exoplayer2.upstream.rtmp.packets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import android.util.Log;

import com.google.android.exoplayer2.upstream.rtmp.Crypto;
import com.google.android.exoplayer2.upstream.rtmp.Util;

/**
 * Handles the RTMP handshake song 'n dance
 * <p>
 * Thanks to http://thompsonng.blogspot.com/2010/11/rtmp-part-10-handshake.html for some very useful information on
 * the the hidden "features" of the RTMP handshake
 *
 * @author francois
 */
public final class Handshake {
  private static final String TAG = "Handshake";
  /**
   * S1 as sent by the server
   */
  private byte[] s1;
  private static final int PROTOCOL_VERSION = 0x03;
  private static final int HANDSHAKE_SIZE = 1536;
  private static final int SHA256_DIGEST_SIZE = 32;

  private static final int DIGEST_OFFSET_INDICATOR_POS = 772; // should either be byte 772 or byte 8

  private static final byte[] GENUINE_FP_KEY = {
      (byte) 0x47, (byte) 0x65, (byte) 0x6E, (byte) 0x75, (byte) 0x69, (byte) 0x6E, (byte) 0x65, (byte) 0x20,
      (byte) 0x41, (byte) 0x64, (byte) 0x6F, (byte) 0x62, (byte) 0x65, (byte) 0x20, (byte) 0x46, (byte) 0x6C,
      (byte) 0x61, (byte) 0x73, (byte) 0x68, (byte) 0x20, (byte) 0x50, (byte) 0x6C, (byte) 0x61, (byte) 0x79,
      (byte) 0x65, (byte) 0x72, (byte) 0x20, (byte) 0x30, (byte) 0x30, (byte) 0x31, // Genuine Adobe Flash Player 001
      (byte) 0xF0, (byte) 0xEE, (byte) 0xC2, (byte) 0x4A, (byte) 0x80, (byte) 0x68, (byte) 0xBE, (byte) 0xE8,
      (byte) 0x2E, (byte) 0x00, (byte) 0xD0, (byte) 0xD1, (byte) 0x02, (byte) 0x9E, (byte) 0x7E, (byte) 0x57,
      (byte) 0x6E, (byte) 0xEC, (byte) 0x5D, (byte) 0x2D, (byte) 0x29, (byte) 0x80, (byte) 0x6F, (byte) 0xAB,
      (byte) 0x93, (byte) 0xB8, (byte) 0xE6, (byte) 0x36, (byte) 0xCF, (byte) 0xEB, (byte) 0x31, (byte) 0xAE};

  /**
   * Generates and writes the first handshake packet (C0)
   */
  public final void writeC0(OutputStream out) throws IOException {
    Log.d(TAG, "writeC0");
    out.write(PROTOCOL_VERSION);
  }

  public final void readS0(InputStream in) throws IOException {
    Log.d(TAG, "readS0");
    byte s0 = (byte) in.read();
    if (s0 != PROTOCOL_VERSION) {
      if (s0 == -1) {
        throw new IOException("InputStream closed");
      } else {
        throw new IOException("Invalid RTMP protocol version; expected " + PROTOCOL_VERSION + ", got " + s0);
      }
    }
  }

  /**
   * Generates and writes the second handshake packet (C1)
   */
  public final void writeC1(OutputStream out) throws IOException {
    Log.d(TAG, "writeC1");
//        Util.writeUnsignedInt32(out, (int) (System.currentTimeMillis() / 1000)); // Bytes 0 - 3 bytes: current epoch (timestamp)
    //out.write(new byte[]{0x09, 0x00, 0x7c, 0x02}); // Bytes 4 - 7: Flash player version: 9.0.124.2

//        out.write(new byte[]{(byte) 0x80, 0x00, 0x07, 0x02}); // Bytes 4 - 7: Flash player version: 11.2.202.233


    Log.d(TAG, "writeC1(): Calculating digest offset");
    Random random = new Random();
    // Since we are faking a real Flash Player handshake, include a digest in C1
    // Choose digest offset point (scheme 1; that is, offset is indicated by bytes 772 - 775 (4 bytes) )
    final int digestOffset = random.nextInt(HANDSHAKE_SIZE - DIGEST_OFFSET_INDICATOR_POS - 4 - 8 - SHA256_DIGEST_SIZE); //random.nextInt(DIGEST_OFFSET_INDICATOR_POS - SHA256_DIGEST_SIZE);

    final int absoluteDigestOffset = ((digestOffset % 728) + DIGEST_OFFSET_INDICATOR_POS + 4);
    Log.d(TAG, "writeC1(): (real value of) digestOffset: " + digestOffset);


    Log.d(TAG, "writeC1(): recalculated digestOffset: " + absoluteDigestOffset);

    int remaining = digestOffset;
    final byte[] digestOffsetBytes = new byte[4];
    for (int i = 3; i >= 0; i--) {
      if (remaining > 255) {
        digestOffsetBytes[i] = (byte) 255;
        remaining -= 255;
      } else {
        digestOffsetBytes[i] = (byte) remaining;
        remaining -= remaining;
      }
    }


    // Calculate the offset value that will be written
    //inal byte[] digestOffsetBytes = Util.unsignedInt32ToByteArray(digestOffset);// //((digestOffset - DIGEST_OFFSET_INDICATOR_POS) % 728)); // Thanks to librtmp for the mod 728
    Log.d(TAG, "writeC1(): digestOffsetBytes: " + Util.toHexString(digestOffsetBytes));  //Util.unsignedInt32ToByteArray((digestOffset % 728))));

    // Create random bytes up to the digest offset point
    byte[] partBeforeDigest = new byte[absoluteDigestOffset];
    Log.d(TAG, "partBeforeDigest(): size: " + partBeforeDigest.length);
    random.nextBytes(partBeforeDigest);

    Log.d(TAG, "writeC1(): Writing timestamp and Flash Player version");
    byte[] timeStamp = Util.unsignedInt32ToByteArray((int) (System.currentTimeMillis() / 1000));
    System.arraycopy(timeStamp, 0, partBeforeDigest, 0, 4); // Bytes 0 - 3 bytes: current epoch timestamp
    System.arraycopy(new byte[]{(byte) 0x80, 0x00, 0x07, 0x02}, 0, partBeforeDigest, 4, 4); // Bytes 4 - 7: Flash player version: 11.2.202.233

    // Create random bytes for the part after the digest
    byte[] partAfterDigest = new byte[HANDSHAKE_SIZE - absoluteDigestOffset - SHA256_DIGEST_SIZE]; // subtract 8 because of initial 8 bytes already written
    Log.d(TAG, "partAfterDigest(): size: " + partAfterDigest.length);
    random.nextBytes(partAfterDigest);


    // Set the offset byte
//        if (digestOffset > 772) {                      
    Log.d(TAG, "copying digest offset bytes in partBeforeDigest");
    System.arraycopy(digestOffsetBytes, 0, partBeforeDigest, 772, 4);
//        } else {
    // Implied offset of partAfterDigest is digestOffset + 32
///        Log.d(TAG, "copying digest offset bytes in partAfterDigest");
///        Log.d(TAG, " writing to location: " + (DIGEST_OFFSET_INDICATOR_POS - digestOffset - SHA256_DIGEST_SIZE - 8));
//        System.arraycopy(digestOffsetBytes, 0, partAfterDigest, (DIGEST_OFFSET_INDICATOR_POS - digestOffset - SHA256_DIGEST_SIZE - 8), 4);
//        }

    Log.d(TAG, "writeC1(): Calculating digest");
    byte[] tempBuffer = new byte[HANDSHAKE_SIZE - SHA256_DIGEST_SIZE];
    System.arraycopy(partBeforeDigest, 0, tempBuffer, 0, partBeforeDigest.length);
    System.arraycopy(partAfterDigest, 0, tempBuffer, partBeforeDigest.length, partAfterDigest.length);

    Crypto crypto = new Crypto();
    byte[] digest = crypto.calculateHmacSHA256(tempBuffer, GENUINE_FP_KEY, 30);

    // Now write the packet
    Log.d(TAG, "writeC1(): writing C1 packet");
    out.write(partBeforeDigest);
    out.write(digest);
    out.write(partAfterDigest);
  }

  public final void readS1(InputStream in) throws IOException {
    // S1 == 1536 bytes. We do not bother with checking the content of it
    Log.d(TAG, "readS1");
    s1 = new byte[HANDSHAKE_SIZE];

    // Read server time (4 bytes)
    int totalBytesRead = 0;
    int read;
    do {
      read = in.read(s1, totalBytesRead, (HANDSHAKE_SIZE - totalBytesRead));
      if (read != -1) {
        totalBytesRead += read;
      }
    } while (totalBytesRead < HANDSHAKE_SIZE);

    if (totalBytesRead != HANDSHAKE_SIZE) {
      throw new IOException("Unexpected EOF while reading S1, expected " + HANDSHAKE_SIZE + " bytes, but only read " + totalBytesRead + " bytes");
    } else {
      Log.d(TAG, "readS1(): S1 total bytes read OK");
    }
  }

  /**
   * Generates and writes the third handshake packet (C2)
   */
  public final void writeC2(OutputStream out) throws IOException {
    Log.d(TAG, "readC2");
    // C2 is an echo of S1
    if (s1 == null) {
      throw new IllegalStateException("C2 cannot be written without S1 being read first");
    }
    out.write(s1);
  }

  public final void readS2(InputStream in) throws IOException {
    // S2 should be an echo of C1, but we are not too strict
    Log.d(TAG, "readS2");
    byte[] sr_serverTime = new byte[4];
    byte[] s2_serverVersion = new byte[4];
    byte[] s2_rest = new byte[HANDSHAKE_SIZE - 8]; // subtract 4+4 bytes for time and version

    // Read server time (4 bytes)
    int totalBytesRead = 0;
    int read;
    do {
      read = in.read(sr_serverTime, totalBytesRead, (4 - totalBytesRead));
      if (read == -1) {
        // End of stream reached - should not have happened at this point
        throw new IOException("Unexpected EOF while reading S2 bytes 0-3");
      } else {
        totalBytesRead += read;
      }
    } while (totalBytesRead < 4);

    // Read server version (4 bytes)
    totalBytesRead = 0;
    do {
      read = in.read(s2_serverVersion, totalBytesRead, (4 - totalBytesRead));
      if (read == -1) {
        // End of stream reached - should not have happened at this point
        throw new IOException("Unexpected EOF while reading S2 bytes 4-7");
      } else {
        totalBytesRead += read;
      }
    } while (totalBytesRead < 4);

    // Read 1528 bytes (to make up S1 total size of 1536 bytes)
    final int remainingBytes = HANDSHAKE_SIZE - 8;
    totalBytesRead = 0;
    do {
      read = in.read(s2_rest, totalBytesRead, (remainingBytes - totalBytesRead));
      if (read != -1) {
        totalBytesRead += read;
      }
    } while (totalBytesRead < remainingBytes && read != -1);

    if (totalBytesRead != remainingBytes) {
      throw new IOException("Unexpected EOF while reading remainder of S2, expected " + remainingBytes + " bytes, but only read " + totalBytesRead + " bytes");
    } else {
      Log.d(TAG, "readS2(): S2 total bytes read OK");
    }

    // Technically we should check that S2 == C1, but for now this is ignored
  }
}
