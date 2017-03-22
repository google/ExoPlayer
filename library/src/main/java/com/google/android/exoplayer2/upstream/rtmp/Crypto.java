package com.google.android.exoplayer2.upstream.rtmp;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import android.util.Log;

/**
 * Some helper utilities for SHA256, mostly (used during handshake)
 * This is separated in order to be more easily replaced on platforms that
 * do not have the javax.crypto.* and/or java.security.* packages
 * <p>
 * This implementation is directly inspired by the RTMPHandshake class of the
 * Red5  Open Source Flash Server project
 *
 * @author francois
 */
public class Crypto {

  private static final String TAG = "Crypto";

  private Mac hmacSHA256;

  public Crypto() {
    try {
      hmacSHA256 = Mac.getInstance("HmacSHA256");
    } catch (SecurityException e) {
      Log.e(TAG, "Security exception when getting HMAC", e);
    } catch (NoSuchAlgorithmException e) {
      Log.e(TAG, "HMAC SHA256 does not exist");
    }
  }

  /**
   * Calculates an HMAC SHA256 hash using a default key length.
   *
   * @param input
   * @param key
   * @return hmac hashed bytes
   */
  public byte[] calculateHmacSHA256(byte[] input, byte[] key) {
    byte[] output = null;
    try {
      hmacSHA256.init(new SecretKeySpec(key, "HmacSHA256"));
      output = hmacSHA256.doFinal(input);
    } catch (InvalidKeyException e) {
      Log.e(TAG, "Invalid key", e);
    }
    return output;
  }

  /**
   * Calculates an HMAC SHA256 hash using a set key length.
   *
   * @param input
   * @param key
   * @param length
   * @return hmac hashed bytes
   */
  public byte[] calculateHmacSHA256(byte[] input, byte[] key, int length) {
    byte[] output = null;
    try {
      hmacSHA256.init(new SecretKeySpec(key, 0, length, "HmacSHA256"));
      output = hmacSHA256.doFinal(input);
    } catch (InvalidKeyException e) {
      Log.e(TAG, "Invalid key", e);
    }
    return output;
  }
}
