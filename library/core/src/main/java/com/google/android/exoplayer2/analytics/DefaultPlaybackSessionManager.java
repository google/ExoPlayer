/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.analytics;

import androidx.annotation.Nullable;
import android.util.Base64;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Default {@link PlaybackSessionManager} which instantiates a new session for each window in the
 * timeline and also for each ad within the windows.
 *
 * <p>Sessions are identified by Base64-encoded, URL-safe, random strings.
 */
public final class DefaultPlaybackSessionManager implements PlaybackSessionManager {

  private static final Random RANDOM = new Random();
  private static final int SESSION_ID_LENGTH = 12;

  private final Timeline.Window window;
  private final Timeline.Period period;
  private final HashMap<String, SessionDescriptor> sessions;

  @MonotonicNonNull private Listener listener;
  private Timeline currentTimeline;
  @Nullable private MediaPeriodId currentMediaPeriodId;
  @Nullable private String activeSessionId;

  /** Creates session manager. */
  public DefaultPlaybackSessionManager() {
    window = new Timeline.Window();
    period = new Timeline.Period();
    sessions = new HashMap<>();
    currentTimeline = Timeline.EMPTY;
  }

  @Override
  public void setListener(Listener listener) {
    this.listener = listener;
  }

  @Override
  public synchronized String getSessionForMediaPeriodId(
      Timeline timeline, MediaPeriodId mediaPeriodId) {
    int windowIndex = timeline.getPeriodByUid(mediaPeriodId.periodUid, period).windowIndex;
    return getOrAddSession(windowIndex, mediaPeriodId).sessionId;
  }

  @Override
  public synchronized boolean belongsToSession(EventTime eventTime, String sessionId) {
    SessionDescriptor sessionDescriptor = sessions.get(sessionId);
    if (sessionDescriptor == null) {
      return false;
    }
    sessionDescriptor.maybeSetWindowSequenceNumber(eventTime.windowIndex, eventTime.mediaPeriodId);
    return sessionDescriptor.belongsToSession(eventTime.windowIndex, eventTime.mediaPeriodId);
  }

  @Override
  public synchronized void updateSessions(EventTime eventTime) {
    boolean isObviouslyFinished =
        eventTime.mediaPeriodId != null
            && currentMediaPeriodId != null
            && eventTime.mediaPeriodId.windowSequenceNumber
                < currentMediaPeriodId.windowSequenceNumber;
    if (!isObviouslyFinished) {
      SessionDescriptor descriptor =
          getOrAddSession(eventTime.windowIndex, eventTime.mediaPeriodId);
      if (!descriptor.isCreated) {
        descriptor.isCreated = true;
        Assertions.checkNotNull(listener).onSessionCreated(eventTime, descriptor.sessionId);
        if (activeSessionId == null) {
          updateActiveSession(eventTime, descriptor);
        }
      }
    }
  }

  @Override
  public synchronized void handleTimelineUpdate(EventTime eventTime) {
    Assertions.checkNotNull(listener);
    Timeline previousTimeline = currentTimeline;
    currentTimeline = eventTime.timeline;
    Iterator<SessionDescriptor> iterator = sessions.values().iterator();
    while (iterator.hasNext()) {
      SessionDescriptor session = iterator.next();
      if (!session.tryResolvingToNewTimeline(previousTimeline, currentTimeline)) {
        iterator.remove();
        if (session.isCreated) {
          if (session.sessionId.equals(activeSessionId)) {
            activeSessionId = null;
          }
          listener.onSessionFinished(
              eventTime, session.sessionId, /* automaticTransitionToNextPlayback= */ false);
        }
      }
    }
    handlePositionDiscontinuity(eventTime, Player.DISCONTINUITY_REASON_INTERNAL);
  }

  @Override
  public synchronized void handlePositionDiscontinuity(
      EventTime eventTime, @DiscontinuityReason int reason) {
    Assertions.checkNotNull(listener);
    boolean hasAutomaticTransition =
        reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION
            || reason == Player.DISCONTINUITY_REASON_AD_INSERTION;
    Iterator<SessionDescriptor> iterator = sessions.values().iterator();
    while (iterator.hasNext()) {
      SessionDescriptor session = iterator.next();
      if (session.isFinishedAtEventTime(eventTime)) {
        iterator.remove();
        if (session.isCreated) {
          boolean isRemovingActiveSession = session.sessionId.equals(activeSessionId);
          boolean isAutomaticTransition = hasAutomaticTransition && isRemovingActiveSession;
          if (isRemovingActiveSession) {
            activeSessionId = null;
          }
          listener.onSessionFinished(eventTime, session.sessionId, isAutomaticTransition);
        }
      }
    }
    SessionDescriptor activeSessionDescriptor =
        getOrAddSession(eventTime.windowIndex, eventTime.mediaPeriodId);
    if (eventTime.mediaPeriodId != null
        && eventTime.mediaPeriodId.isAd()
        && (currentMediaPeriodId == null
            || currentMediaPeriodId.windowSequenceNumber
                != eventTime.mediaPeriodId.windowSequenceNumber
            || currentMediaPeriodId.adGroupIndex != eventTime.mediaPeriodId.adGroupIndex
            || currentMediaPeriodId.adIndexInAdGroup != eventTime.mediaPeriodId.adIndexInAdGroup)) {
      // New ad playback started. Find corresponding content session and notify ad playback started.
      MediaPeriodId contentMediaPeriodId =
          new MediaPeriodId(
              eventTime.mediaPeriodId.periodUid, eventTime.mediaPeriodId.windowSequenceNumber);
      SessionDescriptor contentSession =
          getOrAddSession(eventTime.windowIndex, contentMediaPeriodId);
      if (contentSession.isCreated && activeSessionDescriptor.isCreated) {
        listener.onAdPlaybackStarted(
            eventTime, contentSession.sessionId, activeSessionDescriptor.sessionId);
      }
    }
    updateActiveSession(eventTime, activeSessionDescriptor);
  }

  private SessionDescriptor getOrAddSession(
      int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
    // There should only be one matching session if mediaPeriodId is non-null. If mediaPeriodId is
    // null, there may be multiple matching sessions with different window sequence numbers or
    // adMediaPeriodIds. The best match is the one with the smaller window sequence number, and for
    // windows with ads, the content session is preferred over ad sessions.
    SessionDescriptor bestMatch = null;
    long bestMatchWindowSequenceNumber = Long.MAX_VALUE;
    for (SessionDescriptor sessionDescriptor : sessions.values()) {
      sessionDescriptor.maybeSetWindowSequenceNumber(windowIndex, mediaPeriodId);
      if (sessionDescriptor.belongsToSession(windowIndex, mediaPeriodId)) {
        long windowSequenceNumber = sessionDescriptor.windowSequenceNumber;
        if (windowSequenceNumber == C.INDEX_UNSET
            || windowSequenceNumber < bestMatchWindowSequenceNumber) {
          bestMatch = sessionDescriptor;
          bestMatchWindowSequenceNumber = windowSequenceNumber;
        } else if (windowSequenceNumber == bestMatchWindowSequenceNumber
            && Util.castNonNull(bestMatch).adMediaPeriodId != null
            && sessionDescriptor.adMediaPeriodId != null) {
          bestMatch = sessionDescriptor;
        }
      }
    }
    if (bestMatch == null) {
      String sessionId = generateSessionId();
      bestMatch = new SessionDescriptor(sessionId, windowIndex, mediaPeriodId);
      sessions.put(sessionId, bestMatch);
    }
    return bestMatch;
  }

  @RequiresNonNull("listener")
  private void updateActiveSession(EventTime eventTime, SessionDescriptor sessionDescriptor) {
    currentMediaPeriodId = eventTime.mediaPeriodId;
    if (sessionDescriptor.isCreated) {
      activeSessionId = sessionDescriptor.sessionId;
      if (!sessionDescriptor.isActive) {
        sessionDescriptor.isActive = true;
        listener.onSessionActive(eventTime, sessionDescriptor.sessionId);
      }
    }
  }

  private static String generateSessionId() {
    byte[] randomBytes = new byte[SESSION_ID_LENGTH];
    RANDOM.nextBytes(randomBytes);
    return Base64.encodeToString(randomBytes, Base64.URL_SAFE | Base64.NO_WRAP);
  }

  /**
   * Descriptor for a session.
   *
   * <p>The session may be described in one of three ways:
   *
   * <ul>
   *   <li>A window index with unset window sequence number and a null ad media period id
   *   <li>A content window with index and sequence number, but a null ad media period id.
   *   <li>An ad with all values set.
   * </ul>
   */
  private final class SessionDescriptor {

    private final String sessionId;

    private int windowIndex;
    private long windowSequenceNumber;
    private @MonotonicNonNull MediaPeriodId adMediaPeriodId;

    private boolean isCreated;
    private boolean isActive;

    public SessionDescriptor(
        String sessionId, int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
      this.sessionId = sessionId;
      this.windowIndex = windowIndex;
      this.windowSequenceNumber =
          mediaPeriodId == null ? C.INDEX_UNSET : mediaPeriodId.windowSequenceNumber;
      if (mediaPeriodId != null && mediaPeriodId.isAd()) {
        this.adMediaPeriodId = mediaPeriodId;
      }
    }

    public boolean tryResolvingToNewTimeline(Timeline oldTimeline, Timeline newTimeline) {
      windowIndex = resolveWindowIndexToNewTimeline(oldTimeline, newTimeline, windowIndex);
      if (windowIndex == C.INDEX_UNSET) {
        return false;
      }
      if (adMediaPeriodId == null) {
        return true;
      }
      int newPeriodIndex = newTimeline.getIndexOfPeriod(adMediaPeriodId.periodUid);
      return newPeriodIndex != C.INDEX_UNSET;
    }

    public boolean belongsToSession(
        int eventWindowIndex, @Nullable MediaPeriodId eventMediaPeriodId) {
      if (eventMediaPeriodId == null) {
        // Events without concrete media period id are for all sessions of the same window.
        return eventWindowIndex == windowIndex;
      }
      if (adMediaPeriodId == null) {
        // If this is a content session, only events for content with the same window sequence
        // number belong to this session.
        return !eventMediaPeriodId.isAd()
            && eventMediaPeriodId.windowSequenceNumber == windowSequenceNumber;
      }
      // If this is an ad session, only events for this ad belong to the session.
      return eventMediaPeriodId.windowSequenceNumber == adMediaPeriodId.windowSequenceNumber
          && eventMediaPeriodId.adGroupIndex == adMediaPeriodId.adGroupIndex
          && eventMediaPeriodId.adIndexInAdGroup == adMediaPeriodId.adIndexInAdGroup;
    }

    public void maybeSetWindowSequenceNumber(
        int eventWindowIndex, @Nullable MediaPeriodId eventMediaPeriodId) {
      if (windowSequenceNumber == C.INDEX_UNSET
          && eventWindowIndex == windowIndex
          && eventMediaPeriodId != null
          && !eventMediaPeriodId.isAd()) {
        // Set window sequence number for this session as soon as we have one.
        windowSequenceNumber = eventMediaPeriodId.windowSequenceNumber;
      }
    }

    public boolean isFinishedAtEventTime(EventTime eventTime) {
      if (windowSequenceNumber == C.INDEX_UNSET) {
        // Sessions with unspecified window sequence number are kept until we know more.
        return false;
      }
      if (eventTime.mediaPeriodId == null) {
        // For event times without media period id (e.g. after seek to new window), we only keep
        // sessions of this window.
        return windowIndex != eventTime.windowIndex;
      }
      if (eventTime.mediaPeriodId.windowSequenceNumber > windowSequenceNumber) {
        // All past window sequence numbers are finished.
        return true;
      }
      if (adMediaPeriodId == null) {
        // Current or future content is not finished.
        return false;
      }
      int eventPeriodIndex = eventTime.timeline.getIndexOfPeriod(eventTime.mediaPeriodId.periodUid);
      int adPeriodIndex = eventTime.timeline.getIndexOfPeriod(adMediaPeriodId.periodUid);
      if (eventTime.mediaPeriodId.windowSequenceNumber < adMediaPeriodId.windowSequenceNumber
          || eventPeriodIndex < adPeriodIndex) {
        // Ads in future windows or periods are not finished.
        return false;
      }
      if (eventPeriodIndex > adPeriodIndex) {
        // Ads in past periods are finished.
        return true;
      }
      if (eventTime.mediaPeriodId.isAd()) {
        int eventAdGroup = eventTime.mediaPeriodId.adGroupIndex;
        int eventAdIndex = eventTime.mediaPeriodId.adIndexInAdGroup;
        // Finished if event is for an ad after this one in the same period.
        return eventAdGroup > adMediaPeriodId.adGroupIndex
            || (eventAdGroup == adMediaPeriodId.adGroupIndex
                && eventAdIndex > adMediaPeriodId.adIndexInAdGroup);
      } else {
        // Finished if the event is for content after this ad.
        return eventTime.mediaPeriodId.nextAdGroupIndex == C.INDEX_UNSET
            || eventTime.mediaPeriodId.nextAdGroupIndex > adMediaPeriodId.adGroupIndex;
      }
    }

    private int resolveWindowIndexToNewTimeline(
        Timeline oldTimeline, Timeline newTimeline, int windowIndex) {
      if (windowIndex >= oldTimeline.getWindowCount()) {
        return windowIndex < newTimeline.getWindowCount() ? windowIndex : C.INDEX_UNSET;
      }
      oldTimeline.getWindow(windowIndex, window);
      for (int periodIndex = window.firstPeriodIndex;
          periodIndex <= window.lastPeriodIndex;
          periodIndex++) {
        Object periodUid = oldTimeline.getUidOfPeriod(periodIndex);
        int newPeriodIndex = newTimeline.getIndexOfPeriod(periodUid);
        if (newPeriodIndex != C.INDEX_UNSET) {
          return newTimeline.getPeriod(newPeriodIndex, period).windowIndex;
        }
      }
      return C.INDEX_UNSET;
    }
  }
}
