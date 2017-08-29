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
import android.media.MediaDrm;
import android.media.NotProvisionedException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.drm.ExoMediaDrm.KeyRequest;
import com.google.android.exoplayer2.drm.ExoMediaDrm.OnEventListener;
import com.google.android.exoplayer2.drm.ExoMediaDrm.ProvisionRequest;
import com.google.android.exoplayer2.extractor.mp4.PsshAtomUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link DrmSession} that supports playbacks using {@link MediaDrm}.
 */
@TargetApi(18)
/* package */ class DefaultDrmSession<T extends ExoMediaCrypto> implements DrmSession<T> {
  private static final String TAG = "DefaultDrmSession";

  private static final String CENC_SCHEME_MIME_TYPE = "cenc";

  private static final int MSG_PROVISION = 0;
  private static final int MSG_KEYS = 1;

  private static final int MAX_LICENSE_DURATION_TO_RENEW = 60;

  private final Handler eventHandler;
  private final DefaultDrmSessionManager.EventListener eventListener;
  private final ExoMediaDrm<T> mediaDrm;
  private final HashMap<String, String> optionalKeyRequestParameters;
  /* package */ final MediaDrmCallback callback;
  /* package */ final UUID uuid;
  /* package */ MediaDrmHandler mediaDrmHandler;
  /* package */ PostResponseHandler postResponseHandler;
  private HandlerThread requestHandlerThread;
  private Handler postRequestHandler;

  @DefaultDrmSessionManager.Mode
  private final int mode;
  private int openCount;
  private boolean provisioningInProgress;
  @DrmSession.State
  private int state;
  private T mediaCrypto;
  private DrmSessionException lastException;
  private final byte[] schemeInitData;
  private final String schemeMimeType;
  private byte[] sessionId;
  private byte[] offlineLicenseKeySetId;

  /**
   * Instantiates a new DRM session.
   *
   * @param uuid The UUID of the drm scheme.
   * @param mediaDrm The media DRM.
   * @param initData The DRM init data.
   * @param mode The DRM mode.
   * @param offlineLicenseKeySetId The offlineLicense KeySetId.
   * @param optionalKeyRequestParameters The optional key request parameters.
   * @param callback The media DRM callback.
   * @param playbackLooper The playback looper.
   * @param eventHandler The handler to post listener events.
   * @param eventListener The DRM session manager event listener.
   */
  public DefaultDrmSession(UUID uuid, ExoMediaDrm<T> mediaDrm, DrmInitData initData,
      @DefaultDrmSessionManager.Mode int mode, byte[] offlineLicenseKeySetId,
      HashMap<String, String> optionalKeyRequestParameters, MediaDrmCallback callback,
      Looper playbackLooper, Handler eventHandler,
      DefaultDrmSessionManager.EventListener eventListener) {
    this.uuid = uuid;
    this.mediaDrm = mediaDrm;
    this.mode = mode;
    this.offlineLicenseKeySetId = offlineLicenseKeySetId;
    this.optionalKeyRequestParameters = optionalKeyRequestParameters;
    this.callback = callback;

    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    state = STATE_OPENING;

    mediaDrmHandler = new MediaDrmHandler(playbackLooper);
    mediaDrm.setOnEventListener(new MediaDrmEventListener());
    postResponseHandler = new PostResponseHandler(playbackLooper);
    requestHandlerThread = new HandlerThread("DrmRequestHandler");
    requestHandlerThread.start();
    postRequestHandler = new PostRequestHandler(requestHandlerThread.getLooper());

    // Parse init data.
    byte[] schemeInitData = null;
    String schemeMimeType = null;
    if (offlineLicenseKeySetId == null) {
      SchemeData data = getSchemeData(initData, uuid);
      if (data == null) {
        onError(new IllegalStateException("Media does not support uuid: " + uuid));
      } else {
        schemeInitData = data.data;
        schemeMimeType = data.mimeType;
        if (Util.SDK_INT < 21) {
          // Prior to L the Widevine CDM required data to be extracted from the PSSH atom.
          byte[] psshData = PsshAtomUtil.parseSchemeSpecificData(schemeInitData, uuid);
          if (psshData == null) {
            // Extraction failed. schemeData isn't a Widevine PSSH atom, so leave it unchanged.
          } else {
            schemeInitData = psshData;
          }
        }
        if (Util.SDK_INT < 26 && C.CLEARKEY_UUID.equals(uuid)
            && (MimeTypes.VIDEO_MP4.equals(schemeMimeType)
            || MimeTypes.AUDIO_MP4.equals(schemeMimeType))) {
          // Prior to API level 26 the ClearKey CDM only accepted "cenc" as the scheme for MP4.
          schemeMimeType = CENC_SCHEME_MIME_TYPE;
        }
      }
    }
    this.schemeInitData = schemeInitData;
    this.schemeMimeType = schemeMimeType;
  }

  // Life cycle.

  public void acquire() {
    if (++openCount == 1) {
      if (state == STATE_ERROR) {
        return;
      }
      if (openInternal(true)) {
        doLicense();
      }
    }
  }

  /**
   * @return True if the session is closed and cleaned up, false otherwise.
   */
  public boolean release() {
    if (--openCount == 0) {
      state = STATE_RELEASED;
      provisioningInProgress = false;
      mediaDrmHandler.removeCallbacksAndMessages(null);
      mediaDrmHandler = null;
      postResponseHandler.removeCallbacksAndMessages(null);
      postRequestHandler.removeCallbacksAndMessages(null);
      postRequestHandler = null;
      requestHandlerThread.quit();
      requestHandlerThread = null;
      mediaCrypto = null;
      lastException = null;
      if (sessionId != null) {
        mediaDrm.closeSession(sessionId);
        sessionId = null;
      }
      return true;
    }
    return false;
  }

  // DrmSession Implementation.

  @Override
  @DrmSession.State
  public final int getState() {
    return state;
  }

  @Override
  public final DrmSessionException getError() {
    return state == STATE_ERROR ? lastException : null;
  }

  @Override
  public final T getMediaCrypto() {
    return mediaCrypto;
  }

  @Override
  public Map<String, String> queryKeyStatus() {
    return sessionId == null ? null : mediaDrm.queryKeyStatus(sessionId);
  }

  @Override
  public byte[] getOfflineLicenseKeySetId() {
    return offlineLicenseKeySetId;
  }

  // Internal methods.

  /**
   *  Try to open a session, do provisioning if necessary.
   *  @param allowProvisioning if provisioning is allowed, set this to false when calling from
   *      processing provision response.
   *  @return true on success, false otherwise.
   */
  private boolean openInternal(boolean allowProvisioning) {
    if (isOpen()) {
      // Already opened
      return true;
    }

    try {
      sessionId = mediaDrm.openSession();
      mediaCrypto = mediaDrm.createMediaCrypto(sessionId);
      state = STATE_OPENED;
      return true;
    } catch (NotProvisionedException e) {
      if (allowProvisioning) {
        postProvisionRequest();
      } else {
        onError(e);
      }
    } catch (Exception e) {
      // MediaCryptoException
      // ResourceBusyException only available on 19+
      onError(e);
    }

    return false;
  }

  private void postProvisionRequest() {
    if (provisioningInProgress) {
      return;
    }
    provisioningInProgress = true;
    ProvisionRequest request = mediaDrm.getProvisionRequest();
    postRequestHandler.obtainMessage(MSG_PROVISION, request).sendToTarget();
  }

  private void onProvisionResponse(Object response) {
    provisioningInProgress = false;
    if (state != STATE_OPENING && !isOpen()) {
      // This event is stale.
      return;
    }

    if (response instanceof Exception) {
      onError((Exception) response);
      return;
    }

    try {
      mediaDrm.provideProvisionResponse((byte[]) response);
      if (openInternal(false)) {
        doLicense();
      }
    } catch (DeniedByServerException e) {
      onError(e);
    }
  }

  private void doLicense() {
    switch (mode) {
      case DefaultDrmSessionManager.MODE_PLAYBACK:
      case DefaultDrmSessionManager.MODE_QUERY:
        if (offlineLicenseKeySetId == null) {
          postKeyRequest(MediaDrm.KEY_TYPE_STREAMING);
        } else {
          if (restoreKeys()) {
            long licenseDurationRemainingSec = getLicenseDurationRemainingSec();
            if (mode == DefaultDrmSessionManager.MODE_PLAYBACK
                && licenseDurationRemainingSec <= MAX_LICENSE_DURATION_TO_RENEW) {
              Log.d(TAG, "Offline license has expired or will expire soon. "
                  + "Remaining seconds: " + licenseDurationRemainingSec);
              postKeyRequest(MediaDrm.KEY_TYPE_OFFLINE);
            } else if (licenseDurationRemainingSec <= 0) {
              onError(new KeysExpiredException());
            } else {
              state = STATE_OPENED_WITH_KEYS;
              if (eventHandler != null && eventListener != null) {
                eventHandler.post(new Runnable() {
                  @Override
                  public void run() {
                    eventListener.onDrmKeysRestored();
                  }
                });
              }
            }
          }
        }
        break;
      case DefaultDrmSessionManager.MODE_DOWNLOAD:
        if (offlineLicenseKeySetId == null) {
          postKeyRequest(MediaDrm.KEY_TYPE_OFFLINE);
        } else {
          // Renew
          if (restoreKeys()) {
            postKeyRequest(MediaDrm.KEY_TYPE_OFFLINE);
          }
        }
        break;
      case DefaultDrmSessionManager.MODE_RELEASE:
        // It's not necessary to restore the key (and open a session to do that) before releasing it
        // but this serves as a good sanity/fast-failure check.
        if (restoreKeys()) {
          postKeyRequest(MediaDrm.KEY_TYPE_RELEASE);
        }
        break;
    }
  }

  private boolean restoreKeys() {
    try {
      mediaDrm.restoreKeys(sessionId, offlineLicenseKeySetId);
      return true;
    } catch (Exception e) {
      Log.e(TAG, "Error trying to restore Widevine keys.", e);
      onError(e);
    }
    return false;
  }

  private long getLicenseDurationRemainingSec() {
    if (!C.WIDEVINE_UUID.equals(uuid)) {
      return Long.MAX_VALUE;
    }
    Pair<Long, Long> pair = WidevineUtil.getLicenseDurationRemainingSec(this);
    return Math.min(pair.first, pair.second);
  }

  private void postKeyRequest(int type) {
    byte[] scope = type == MediaDrm.KEY_TYPE_RELEASE ? offlineLicenseKeySetId : sessionId;
    try {
      KeyRequest request = mediaDrm.getKeyRequest(scope, schemeInitData, schemeMimeType, type,
          optionalKeyRequestParameters);
      postRequestHandler.obtainMessage(MSG_KEYS, request).sendToTarget();
    } catch (Exception e) {
      onKeysError(e);
    }
  }

  private void onKeyResponse(Object response) {
    if (!isOpen()) {
      // This event is stale.
      return;
    }

    if (response instanceof Exception) {
      onKeysError((Exception) response);
      return;
    }

    try {
      if (mode == DefaultDrmSessionManager.MODE_RELEASE) {
        mediaDrm.provideKeyResponse(offlineLicenseKeySetId, (byte[]) response);
        if (eventHandler != null && eventListener != null) {
          eventHandler.post(new Runnable() {
            @Override
            public void run() {
              eventListener.onDrmKeysRemoved();
            }
          });
        }
      } else {
        byte[] keySetId = mediaDrm.provideKeyResponse(sessionId, (byte[]) response);
        if ((mode == DefaultDrmSessionManager.MODE_DOWNLOAD
            || (mode == DefaultDrmSessionManager.MODE_PLAYBACK && offlineLicenseKeySetId != null))
            && keySetId != null && keySetId.length != 0) {
          offlineLicenseKeySetId = keySetId;
        }
        state = STATE_OPENED_WITH_KEYS;
        if (eventHandler != null && eventListener != null) {
          eventHandler.post(new Runnable() {
            @Override
            public void run() {
              eventListener.onDrmKeysLoaded();
            }
          });
        }
      }
    } catch (Exception e) {
      onKeysError(e);
    }
  }

  private void onKeysExpired() {
    if (state == STATE_OPENED_WITH_KEYS) {
      state = STATE_OPENED;
      onError(new KeysExpiredException());
    }
  }

  private void onKeysError(Exception e) {
    if (e instanceof NotProvisionedException) {
      postProvisionRequest();
    } else {
      onError(e);
    }
  }

  private void onError(final Exception e) {
    lastException = new DrmSessionException(e);
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          eventListener.onDrmSessionManagerError(e);
        }
      });
    }
    if (state != STATE_OPENED_WITH_KEYS) {
      state = STATE_ERROR;
    }
  }

  private boolean isOpen() {
    return state == STATE_OPENED || state == STATE_OPENED_WITH_KEYS;
  }

  @SuppressLint("HandlerLeak")
  private class MediaDrmHandler extends Handler {

    public MediaDrmHandler(Looper looper) {
      super(looper);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleMessage(Message msg) {
      if (!isOpen()) {
        return;
      }
      switch (msg.what) {
        case MediaDrm.EVENT_KEY_REQUIRED:
          doLicense();
          break;
        case MediaDrm.EVENT_KEY_EXPIRED:
          // When an already expired key is loaded MediaDrm sends this event immediately. Ignore
          // this event if the state isn't STATE_OPENED_WITH_KEYS yet which means we're still
          // waiting for key response.
          onKeysExpired();
          break;
        case MediaDrm.EVENT_PROVISION_REQUIRED:
          state = STATE_OPENED;
          postProvisionRequest();
          break;
      }
    }

  }

  private class MediaDrmEventListener implements OnEventListener<T> {

    @Override
    public void onEvent(ExoMediaDrm<? extends T> md, byte[] sessionId, int event, int extra,
        byte[] data) {
      if (mode == DefaultDrmSessionManager.MODE_PLAYBACK) {
        mediaDrmHandler.sendEmptyMessage(event);
      }
    }

  }

  @SuppressLint("HandlerLeak")
  private class PostResponseHandler extends Handler {

    public PostResponseHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_PROVISION:
          onProvisionResponse(msg.obj);
          break;
        case MSG_KEYS:
          onKeyResponse(msg.obj);
          break;
      }
    }

  }

  @SuppressLint("HandlerLeak")
  private class PostRequestHandler extends Handler {

    public PostRequestHandler(Looper backgroundLooper) {
      super(backgroundLooper);
    }

    @Override
    public void handleMessage(Message msg) {
      Object response;
      try {
        switch (msg.what) {
          case MSG_PROVISION:
            response = callback.executeProvisionRequest(uuid, (ProvisionRequest) msg.obj);
            break;
          case MSG_KEYS:
            response = callback.executeKeyRequest(uuid, (KeyRequest) msg.obj);
            break;
          default:
            throw new RuntimeException();
        }
      } catch (Exception e) {
        response = e;
      }
      postResponseHandler.obtainMessage(msg.what, response).sendToTarget();
    }

  }

  /**
   * Extracts {@link SchemeData} suitable for the given DRM scheme {@link UUID}.
   *
   * @param drmInitData The {@link DrmInitData} from which to extract the {@link SchemeData}.
   * @param uuid The UUID of the scheme.
   * @return The extracted {@link SchemeData}, or null if no suitable data is present.
   */
  public static SchemeData getSchemeData(DrmInitData drmInitData, UUID uuid) {
    SchemeData schemeData = drmInitData.get(uuid);
    if (schemeData == null && C.CLEARKEY_UUID.equals(uuid)) {
      // If present, the Common PSSH box should be used for ClearKey.
      schemeData = drmInitData.get(C.COMMON_PSSH_UUID);
    }
    return schemeData;
  }

}
