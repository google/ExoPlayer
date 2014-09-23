/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.drm;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.DeniedByServerException;
import android.media.MediaCrypto;
import android.media.MediaDrm;
import android.media.MediaDrm.KeyRequest;
import android.media.MediaDrm.OnEventListener;
import android.media.MediaDrm.ProvisionRequest;
import android.media.NotProvisionedException;
import android.media.UnsupportedSchemeException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import java.util.Map;
import java.util.UUID;

/**
 * A base class for {@link DrmSessionManager} implementations that support streaming playbacks
 * using {@link MediaDrm}.
 */
@TargetApi(18)
public class StreamingDrmSessionManager implements DrmSessionManager {

  /**
   * Interface definition for a callback to be notified of {@link StreamingDrmSessionManager}
   * events.
   */
  public interface EventListener {

    /**
     * Invoked when a drm error occurs.
     *
     * @param e The corresponding exception.
     */
    void onDrmSessionManagerError(Exception e);

  }

  private static final int MSG_PROVISION = 0;
  private static final int MSG_KEYS = 1;

  private final Handler eventHandler;
  private final EventListener eventListener;
  private final MediaDrm mediaDrm;

  /* package */ final MediaDrmHandler mediaDrmHandler;
  /* package */ final MediaDrmCallback callback;
  /* package */ final PostResponseHandler postResponseHandler;
  /* package */ final UUID uuid;

  private HandlerThread requestHandlerThread;
  private Handler postRequestHandler;

  private int openCount;
  private boolean provisioningInProgress;
  private int state;
  private MediaCrypto mediaCrypto;
  private Exception lastException;
  private String mimeType;
  private byte[] schemePsshData;
  private byte[] sessionId;

  /**
   * @param uuid The UUID of the drm scheme.
   * @param playbackLooper The looper associated with the media playback thread. Should usually be
   *     obtained using {@link com.google.android.exoplayer.ExoPlayer#getPlaybackLooper()}.
   * @param callback Performs key and provisioning requests.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @throws UnsupportedSchemeException If the specified DRM scheme is not supported.
   */
  public StreamingDrmSessionManager(UUID uuid, Looper playbackLooper, MediaDrmCallback callback,
      Handler eventHandler, EventListener eventListener) throws UnsupportedSchemeException {
    this.uuid = uuid;
    this.callback = callback;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    mediaDrm = new MediaDrm(uuid);
    mediaDrm.setOnEventListener(new MediaDrmEventListener());
    mediaDrmHandler = new MediaDrmHandler(playbackLooper);
    postResponseHandler = new PostResponseHandler(playbackLooper);
    state = STATE_CLOSED;
  }

  @Override
  public int getState() {
    return state;
  }

  @Override
  public MediaCrypto getMediaCrypto() {
    if (state != STATE_OPENED && state != STATE_OPENED_WITH_KEYS) {
      throw new IllegalStateException();
    }
    return mediaCrypto;
  }

  @Override
  public boolean requiresSecureDecoderComponent(String mimeType) {
    if (state != STATE_OPENED && state != STATE_OPENED_WITH_KEYS) {
      throw new IllegalStateException();
    }
    return mediaCrypto.requiresSecureDecoderComponent(mimeType);
  }

  @Override
  public Exception getError() {
    return state == STATE_ERROR ? lastException : null;
  }

  /**
   * Provides access to {@link MediaDrm#getPropertyString(String)}.
   * <p>
   * This method may be called when the manager is in any state.
   *
   * @param key The key to request.
   * @return The retrieved property.
   */
  public final String getPropertyString(String key) {
    return mediaDrm.getPropertyString(key);
  }

  /**
   * Provides access to {@link MediaDrm#getPropertyByteArray(String)}.
   * <p>
   * This method may be called when the manager is in any state.
   *
   * @param key The key to request.
   * @return The retrieved property.
   */
  public final byte[] getPropertyByteArray(String key) {
    return mediaDrm.getPropertyByteArray(key);
  }

  @Override
  public void open(Map<UUID, byte[]> psshData, String mimeType) {
    if (++openCount != 1) {
      return;
    }
    if (postRequestHandler == null) {
      requestHandlerThread = new HandlerThread("DrmRequestHandler");
      requestHandlerThread.start();
      postRequestHandler = new PostRequestHandler(requestHandlerThread.getLooper());
    }
    if (this.schemePsshData == null) {
      this.mimeType = mimeType;
      schemePsshData = psshData.get(uuid);
      if (schemePsshData == null) {
        onError(new IllegalStateException("Media does not support uuid: " + uuid));
        return;
      }
    }
    state = STATE_OPENING;
    openInternal(true);
  }

  @Override
  public void close() {
    if (--openCount != 0) {
      return;
    }
    state = STATE_CLOSED;
    provisioningInProgress = false;
    mediaDrmHandler.removeCallbacksAndMessages(null);
    postResponseHandler.removeCallbacksAndMessages(null);
    postRequestHandler.removeCallbacksAndMessages(null);
    postRequestHandler = null;
    requestHandlerThread.quit();
    requestHandlerThread = null;
    schemePsshData = null;
    mediaCrypto = null;
    lastException = null;
    if (sessionId != null) {
      mediaDrm.closeSession(sessionId);
      sessionId = null;
    }
  }

  private void openInternal(boolean allowProvisioning) {
    try {
      sessionId = mediaDrm.openSession();
      mediaCrypto = new MediaCrypto(uuid, sessionId);
      state = STATE_OPENED;
      postKeyRequest();
    } catch (NotProvisionedException e) {
      if (allowProvisioning) {
        postProvisionRequest();
      } else {
        onError(e);
      }
    } catch (Exception e) {
      onError(e);
    }
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
    if (state != STATE_OPENING && state != STATE_OPENED && state != STATE_OPENED_WITH_KEYS) {
      // This event is stale.
      return;
    }

    if (response instanceof Exception) {
      onError((Exception) response);
      return;
    }

    try {
      mediaDrm.provideProvisionResponse((byte[]) response);
      if (state == STATE_OPENING) {
        openInternal(false);
      } else {
        postKeyRequest();
      }
    } catch (DeniedByServerException e) {
      onError(e);
    }
  }

  private void postKeyRequest() {
    KeyRequest keyRequest;
    try {
      keyRequest = mediaDrm.getKeyRequest(sessionId, schemePsshData, mimeType,
          MediaDrm.KEY_TYPE_STREAMING, null);
      postRequestHandler.obtainMessage(MSG_KEYS, keyRequest).sendToTarget();
    } catch (NotProvisionedException e) {
      onKeysError(e);
    }
  }

  private void onKeyResponse(Object response) {
    if (state != STATE_OPENED && state != STATE_OPENED_WITH_KEYS) {
      // This event is stale.
      return;
    }

    if (response instanceof Exception) {
      onKeysError((Exception) response);
      return;
    }

    try {
      mediaDrm.provideKeyResponse(sessionId, (byte[]) response);
      state = STATE_OPENED_WITH_KEYS;
    } catch (Exception e) {
      onKeysError(e);
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
    lastException = e;
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

  @SuppressLint("HandlerLeak")
  private class MediaDrmHandler extends Handler {

    public MediaDrmHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      if (openCount == 0 || (state != STATE_OPENED && state != STATE_OPENED_WITH_KEYS)) {
        return;
      }
      switch (msg.what) {
        case MediaDrm.EVENT_KEY_REQUIRED:
          postKeyRequest();
          return;
        case MediaDrm.EVENT_KEY_EXPIRED:
          state = STATE_OPENED;
          postKeyRequest();
          return;
        case MediaDrm.EVENT_PROVISION_REQUIRED:
          state = STATE_OPENED;
          postProvisionRequest();
          return;
      }
    }

  }

  private class MediaDrmEventListener implements OnEventListener {

    @Override
    public void onEvent(MediaDrm md, byte[] sessionId, int event, int extra, byte[] data) {
      mediaDrmHandler.sendEmptyMessage(event);
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
          return;
        case MSG_KEYS:
          onKeyResponse(msg.obj);
          return;
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
