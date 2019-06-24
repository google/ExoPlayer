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

/** Handles communication with the receiver app using a cast session. */
public interface CastSessionManager {

  /** Factory for {@link CastSessionManager} instances. */
  interface Factory {

    /**
     * Creates a {@link CastSessionManager} instance with the given listener.
     *
     * @param listener The listener to notify on receiver app and session state updates.
     * @return The created instance.
     */
    CastSessionManager create(StateListener listener);
  }

  /**
   * Extends {@link SessionAvailabilityListener} by adding receiver app state notifications.
   *
   * <p>Receiver app state notifications contain a sequence number that matches the sequence number
   * of the last {@link ExoCastMessage} sent (using {@link #send(ExoCastMessage)}) by this session
   * manager and processed by the receiver app. Sequence numbers are non-negative numbers.
   */
  interface StateListener extends SessionAvailabilityListener {

    /**
     * Called when a status update is received from the Cast Receiver app.
     *
     * @param stateUpdate A {@link ReceiverAppStateUpdate} containing the fields included in the
     *     message.
     */
    void onStateUpdateFromReceiverApp(ReceiverAppStateUpdate stateUpdate);
  }

  /**
   * Special constant representing an unset sequence number. It is guaranteed to be a negative
   * value.
   */
  long SEQUENCE_NUMBER_UNSET = Long.MIN_VALUE;

  /**
   * Connects the session manager to the cast message bus and starts listening for session
   * availability changes. Also announces that this sender app is connected to the message bus.
   */
  void start();

  /** Stops tracking the state of the cast session and closes any existing session. */
  void stopTrackingSession();

  /**
   * Same as {@link #stopTrackingSession()}, but also stops the receiver app if a session is
   * currently available.
   */
  void stopTrackingSessionAndCasting();

  /** Whether a cast session is available. */
  boolean isCastSessionAvailable();

  /**
   * Sends an {@link ExoCastMessage} to the receiver app.
   *
   * <p>A sequence number is assigned to every sent message. Message senders may mask the local
   * state until a status update from the receiver app (see {@link StateListener}) is received with
   * a greater or equal sequence number.
   *
   * @param message The message to send.
   * @return The sequence number assigned to the message.
   */
  long send(ExoCastMessage message);
}
