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
import com.google.android.exoplayer2.drm.ExoMediaDrm.KeyRequest;
import com.google.android.exoplayer2.drm.ExoMediaDrm.ProvisionRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link DrmSession} that supports playbacks using {@link MediaDrm}.
 */
@TargetApi(18)
/* package */ class DefaultDrmSession<T extends ExoMediaCrypto> implements DrmSession<T> {

  /**
   * Listener of {@link DefaultDrmSession} events.
   */
  public interface EventListener {

    /**
     * Called each time provision is completed.
     */
    void onProvisionCompleted();

  }

  private static final String TAG = "DefaultDrmSession";

  private static final int MSG_PROVISION = 0;
  private static final int MSG_KEYS = 1;
  private static final int MAX_LICENSE_DURATION_TO_RENEW = 60;

  private final Handler eventHandler;
  private final DefaultDrmSessionManager.EventListener eventListener;
  private final ExoMediaDrm<T> mediaDrm;
  private final HashMap<String, String> optionalKeyRequestParameters;
  /* package */ final MediaDrmCallback callback;
  /* package */ final UUID uuid;
  /* package */ PostResponseHandler postResponseHandler;
  private HandlerThread requestHandlerThread;
  private Handler postRequestHandler;

  @DefaultDrmSessionManager.Mode
  private final int mode;
  private int openCount;
  private final AtomicBoolean provisioningInProgress;
  private final EventListener sessionEventListener;
  @DrmSession.State
  private int state;
  private T mediaCrypto;
  private DrmSessionException lastException;
  private final byte[] initData;
  private final String mimeType;
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
  public DefaultDrmSession(UUID uuid, ExoMediaDrm<T> mediaDrm, byte[] initData, String mimeType,
      @DefaultDrmSessionManager.Mode int mode, byte[] offlineLicenseKeySetId,
      HashMap<String, String> optionalKeyRequestParameters, MediaDrmCallback callback,
      Looper playbackLooper, Handler eventHandler,
      DefaultDrmSessionManager.EventListener eventListener, AtomicBoolean provisioningInProgress,
      EventListener sessionEventListener) {
    this.uuid = uuid;
    this.mediaDrm = mediaDrm;
    this.mode = mode;
    this.offlineLicenseKeySetId = offlineLicenseKeySetId;
    this.optionalKeyRequestParameters = optionalKeyRequestParameters;
    this.callback = callback;

    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.provisioningInProgress = provisioningInProgress;
    this.sessionEventListener = sessionEventListener;
    state = STATE_OPENING;

    postResponseHandler = new PostResponseHandler(playbackLooper);
    requestHandlerThread = new HandlerThread("DrmRequestHandler");
    requestHandlerThread.start();
    postRequestHandler = new PostRequestHandler(requestHandlerThread.getLooper());

    if (offlineLicenseKeySetId == null) {
      this.initData = initData;
      this.mimeType = mimeType;
    } else {
      this.initData = null;
      this.mimeType = null;
    }
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

  public boolean canReuse(byte[] initData) {
    return Arrays.equals(this.initData, initData);
  }

  public boolean hasSessionId(byte[] sessionId) {
    return Arrays.equals(this.sessionId, sessionId);
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
    if (provisioningInProgress.getAndSet(true)) {
      return;
    }
    ProvisionRequest request = mediaDrm.getProvisionRequest();
    postRequestHandler.obtainMessage(MSG_PROVISION, request).sendToTarget();
  }

  private void onProvisionResponse(Object response) {
    provisioningInProgress.set(false);
    if (response instanceof Exception) {
      onError((Exception) response);
      return;
    }

    try {
      mediaDrm.provideProvisionResponse((byte[]) response);
    } catch (DeniedByServerException e) {
      onError(e);
      return;
    }

    if (sessionEventListener != null) {
      sessionEventListener.onProvisionCompleted();
    }
  }

  public void onProvisionCompleted() {
    if (state != STATE_OPENING && !isOpen()) {
      // This event is stale.
      return;
    }

    if (openInternal(false)) {
      doLicense();
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
      default:
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
      KeyRequest request = mediaDrm.getKeyRequest(scope, initData, mimeType, type,
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

  @SuppressWarnings("deprecation")
  public void onMediaDrmEvent(int what) {
    if (!isOpen()) {
      return;
    }
    switch (what) {
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
      default:
        break;
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
        default:
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

}
