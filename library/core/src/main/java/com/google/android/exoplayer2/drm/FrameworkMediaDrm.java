/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.drm;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.DeniedByServerException;
import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaDrm;
import android.media.MediaDrmException;
import android.media.NotProvisionedException;
import android.media.UnsupportedSchemeException;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.mp4.PsshAtomUtil;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An {@link ExoMediaDrm} implementation that wraps the framework {@link MediaDrm}.
 */
@TargetApi(23)
public final class FrameworkMediaDrm implements ExoMediaDrm<FrameworkMediaCrypto> {

  private static final String CENC_SCHEME_MIME_TYPE = "cenc";

  private final UUID uuid;
  private final MediaDrm mediaDrm;

  /**
   * Creates an instance for the specified scheme UUID.
   *
   * @param uuid The scheme uuid.
   * @return The created instance.
   * @throws UnsupportedDrmException If the DRM scheme is unsupported or cannot be instantiated.
   */
  public static FrameworkMediaDrm newInstance(UUID uuid) throws UnsupportedDrmException {
    try {
      return new FrameworkMediaDrm(uuid);
    } catch (UnsupportedSchemeException e) {
      throw new UnsupportedDrmException(UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME, e);
    } catch (Exception e) {
      throw new UnsupportedDrmException(UnsupportedDrmException.REASON_INSTANTIATION_ERROR, e);
    }
  }

  private FrameworkMediaDrm(UUID uuid) throws UnsupportedSchemeException {
    Assertions.checkNotNull(uuid);
    Assertions.checkArgument(!C.COMMON_PSSH_UUID.equals(uuid), "Use C.CLEARKEY_UUID instead");
    this.uuid = uuid;
    // ClearKey had to be accessed using the Common PSSH UUID prior to API level 27.
    this.mediaDrm =
        new MediaDrm(Util.SDK_INT < 27 && C.CLEARKEY_UUID.equals(uuid) ? C.COMMON_PSSH_UUID : uuid);
    if (C.WIDEVINE_UUID.equals(uuid) && needsForceWidevineL3Workaround()) {
      forceWidevineL3(mediaDrm);
    }
  }

  @Override
  public void setOnEventListener(
      final ExoMediaDrm.OnEventListener<? super FrameworkMediaCrypto> listener) {
    mediaDrm.setOnEventListener(
        listener == null
            ? null
            : (mediaDrm, sessionId, event, extra, data) ->
                listener.onEvent(FrameworkMediaDrm.this, sessionId, event, extra, data));
  }

  @Override
  public void setOnKeyStatusChangeListener(
      final ExoMediaDrm.OnKeyStatusChangeListener<? super FrameworkMediaCrypto> listener) {
    if (Util.SDK_INT < 23) {
      throw new UnsupportedOperationException();
    }

    mediaDrm.setOnKeyStatusChangeListener(
        listener == null
            ? null
            : (mediaDrm, sessionId, keyInfo, hasNewUsableKey) -> {
              List<KeyStatus> exoKeyInfo = new ArrayList<>();
              for (MediaDrm.KeyStatus keyStatus : keyInfo) {
                exoKeyInfo.add(new KeyStatus(keyStatus.getStatusCode(), keyStatus.getKeyId()));
              }
              listener.onKeyStatusChange(
                  FrameworkMediaDrm.this, sessionId, exoKeyInfo, hasNewUsableKey);
            },
        null);
  }

  @Override
  public byte[] openSession() throws MediaDrmException {
    return mediaDrm.openSession();
  }

  @Override
  public void closeSession(byte[] sessionId) {
    mediaDrm.closeSession(sessionId);
  }

  @Override
  public KeyRequest getKeyRequest(
      byte[] scope,
      byte[] init,
      String mimeType,
      int keyType,
      HashMap<String, String> optionalParameters)
      throws NotProvisionedException {

    // Prior to L the Widevine CDM required data to be extracted from the PSSH atom. Some Amazon
    // devices also required data to be extracted from the PSSH atom for PlayReady.
    if ((Util.SDK_INT < 21 && C.WIDEVINE_UUID.equals(uuid))
        || (C.PLAYREADY_UUID.equals(uuid)
            && "Amazon".equals(Util.MANUFACTURER)
            && ("AFTB".equals(Util.MODEL) // Fire TV Gen 1
                || "AFTS".equals(Util.MODEL) // Fire TV Gen 2
                || "AFTM".equals(Util.MODEL)))) { // Fire TV Stick Gen 1
      byte[] psshData = PsshAtomUtil.parseSchemeSpecificData(init, uuid);
      if (psshData == null) {
        // Extraction failed. schemeData isn't a PSSH atom, so leave it unchanged.
      } else {
        init = psshData;
      }
    }

    // Prior to API level 26 the ClearKey CDM only accepted "cenc" as the scheme for MP4.
    if (Util.SDK_INT < 26
        && C.CLEARKEY_UUID.equals(uuid)
        && (MimeTypes.VIDEO_MP4.equals(mimeType) || MimeTypes.AUDIO_MP4.equals(mimeType))) {
      mimeType = CENC_SCHEME_MIME_TYPE;
    }

    final MediaDrm.KeyRequest request = mediaDrm.getKeyRequest(scope, init, mimeType, keyType,
        optionalParameters);

    byte[] requestData = request.getData();
    if (C.CLEARKEY_UUID.equals(uuid)) {
      requestData = ClearKeyUtil.adjustRequestData(requestData);
    }

    return new KeyRequest(requestData, request.getDefaultUrl());
  }

  @Override
  public byte[] provideKeyResponse(byte[] scope, byte[] response)
      throws NotProvisionedException, DeniedByServerException {

    if (C.CLEARKEY_UUID.equals(uuid)) {
      response = ClearKeyUtil.adjustResponseData(response);
    }

    return mediaDrm.provideKeyResponse(scope, response);
  }

  @Override
  public ProvisionRequest getProvisionRequest() {
    final MediaDrm.ProvisionRequest request = mediaDrm.getProvisionRequest();
    return new ProvisionRequest(request.getData(), request.getDefaultUrl());
  }

  @Override
  public void provideProvisionResponse(byte[] response) throws DeniedByServerException {
    mediaDrm.provideProvisionResponse(response);
  }

  @Override
  public Map<String, String> queryKeyStatus(byte[] sessionId) {
    return mediaDrm.queryKeyStatus(sessionId);
  }

  @Override
  public void release() {
    mediaDrm.release();
  }

  @Override
  public void restoreKeys(byte[] sessionId, byte[] keySetId) {
    mediaDrm.restoreKeys(sessionId, keySetId);
  }

  @Override
  public String getPropertyString(String propertyName) {
    return mediaDrm.getPropertyString(propertyName);
  }

  @Override
  public byte[] getPropertyByteArray(String propertyName) {
    return mediaDrm.getPropertyByteArray(propertyName);
  }

  @Override
  public void setPropertyString(String propertyName, String value) {
    mediaDrm.setPropertyString(propertyName, value);
  }

  @Override
  public void setPropertyByteArray(String propertyName, byte[] value) {
    mediaDrm.setPropertyByteArray(propertyName, value);
  }

  @Override
  public FrameworkMediaCrypto createMediaCrypto(byte[] initData) throws MediaCryptoException {
    // Work around a bug prior to Lollipop where L1 Widevine forced into L3 mode would still
    // indicate that it required secure video decoders [Internal ref: b/11428937].
    boolean forceAllowInsecureDecoderComponents = Util.SDK_INT < 21
        && C.WIDEVINE_UUID.equals(uuid) && "L3".equals(getPropertyString("securityLevel"));
    return new FrameworkMediaCrypto(new MediaCrypto(uuid, initData),
        forceAllowInsecureDecoderComponents);
  }

  @SuppressLint("WrongConstant") // Suppress spurious lint error [Internal ref: b/32137960]
  private static void forceWidevineL3(MediaDrm mediaDrm) {
    mediaDrm.setPropertyString("securityLevel", "L3");
  }

  /**
   * Returns whether the device codec is known to fail if security level L1 is used.
   *
   * <p>See <a href="https://github.com/google/ExoPlayer/issues/4413">GitHub issue #4413</a>.
   */
  private static boolean needsForceWidevineL3Workaround() {
    return "ASUS_Z00AD".equals(Util.MODEL);
  }
}
