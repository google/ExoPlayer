/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cast;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import java.io.IOException;
import org.json.JSONException;

/** Implements {@link CastSessionManager} by using JSON message passing. */
public class DefaultCastSessionManager implements CastSessionManager {

  private static final String TAG = "DefaultCastSessionManager";
  private static final String EXOPLAYER_CAST_NAMESPACE = "urn:x-cast:com.google.exoplayer.cast";

  private final SessionManager sessionManager;
  private final CastSessionListener castSessionListener;
  private final StateListener stateListener;
  private final Cast.MessageReceivedCallback messageReceivedCallback;

  private boolean started;
  private long sequenceNumber;
  private long expectedInitialStateUpdateSequence;
  @Nullable private CastSession currentSession;

  /**
   * @param context The Cast context from which the cast session is obtained.
   * @param stateListener The listener to notify of state changes.
   */
  public DefaultCastSessionManager(CastContext context, StateListener stateListener) {
    this.stateListener = stateListener;
    sessionManager = context.getSessionManager();
    currentSession = sessionManager.getCurrentCastSession();
    castSessionListener = new CastSessionListener();
    messageReceivedCallback = new CastMessageCallback();
    expectedInitialStateUpdateSequence = SEQUENCE_NUMBER_UNSET;
  }

  @Override
  public void start() {
    started = true;
    sessionManager.addSessionManagerListener(castSessionListener, CastSession.class);
    currentSession = sessionManager.getCurrentCastSession();
    if (currentSession != null) {
      setMessageCallbackOnSession();
    }
  }

  @Override
  public void stopTrackingSession() {
    stop(/* stopCasting= */ false);
  }

  @Override
  public void stopTrackingSessionAndCasting() {
    stop(/* stopCasting= */ true);
  }

  @Override
  public boolean isCastSessionAvailable() {
    return currentSession != null && expectedInitialStateUpdateSequence == SEQUENCE_NUMBER_UNSET;
  }

  @Override
  public long send(ExoCastMessage message) {
    if (currentSession != null) {
      currentSession.sendMessage(EXOPLAYER_CAST_NAMESPACE, message.toJsonString(sequenceNumber));
    } else {
      Log.w(TAG, "Tried to send a message with no established session. Method: " + message.method);
    }
    return sequenceNumber++;
  }

  private void stop(boolean stopCasting) {
    sessionManager.removeSessionManagerListener(castSessionListener, CastSession.class);
    if (currentSession != null) {
      sessionManager.endCurrentSession(stopCasting);
    }
    currentSession = null;
    started = false;
  }

  private void setCastSession(@Nullable CastSession session) {
    Assertions.checkState(started);
    boolean hadSession = currentSession != null;
    currentSession = session;
    if (!hadSession && session != null) {
      setMessageCallbackOnSession();
    } else if (hadSession && session == null) {
      stateListener.onCastSessionUnavailable();
    }
  }

  private void setMessageCallbackOnSession() {
    try {
      Assertions.checkNotNull(currentSession)
          .setMessageReceivedCallbacks(EXOPLAYER_CAST_NAMESPACE, messageReceivedCallback);
      expectedInitialStateUpdateSequence = send(new ExoCastMessage.OnClientConnected());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Listens for Cast session state changes. */
  private class CastSessionListener implements SessionManagerListener<CastSession> {

    @Override
    public void onSessionStarting(CastSession castSession) {}

    @Override
    public void onSessionStarted(CastSession castSession, String sessionId) {
      setCastSession(castSession);
    }

    @Override
    public void onSessionStartFailed(CastSession castSession, int error) {}

    @Override
    public void onSessionEnding(CastSession castSession) {}

    @Override
    public void onSessionEnded(CastSession castSession, int error) {
      setCastSession(null);
    }

    @Override
    public void onSessionResuming(CastSession castSession, String sessionId) {}

    @Override
    public void onSessionResumed(CastSession castSession, boolean wasSuspended) {
      setCastSession(castSession);
    }

    @Override
    public void onSessionResumeFailed(CastSession castSession, int error) {}

    @Override
    public void onSessionSuspended(CastSession castSession, int reason) {
      setCastSession(null);
    }
  }

  private class CastMessageCallback implements Cast.MessageReceivedCallback {

    @Override
    public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
      if (!EXOPLAYER_CAST_NAMESPACE.equals(namespace)) {
        // Non-matching namespace. Ignore.
        Log.e(TAG, String.format("Unrecognized namespace: '%s'.", namespace));
        return;
      }
      try {
        ReceiverAppStateUpdate receivedUpdate = ReceiverAppStateUpdate.fromJsonMessage(message);
        if (expectedInitialStateUpdateSequence == SEQUENCE_NUMBER_UNSET
            || receivedUpdate.sequenceNumber >= expectedInitialStateUpdateSequence) {
          stateListener.onStateUpdateFromReceiverApp(receivedUpdate);
          if (expectedInitialStateUpdateSequence != SEQUENCE_NUMBER_UNSET) {
            expectedInitialStateUpdateSequence = SEQUENCE_NUMBER_UNSET;
            stateListener.onCastSessionAvailable();
          }
        }
      } catch (JSONException e) {
        Log.e(TAG, "Error while parsing state update from receiver: ", e);
      }
    }
  }
}
