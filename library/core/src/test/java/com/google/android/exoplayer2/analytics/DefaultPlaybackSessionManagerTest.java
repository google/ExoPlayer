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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit test for {@link DefaultPlaybackSessionManager}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultPlaybackSessionManagerTest {

  private DefaultPlaybackSessionManager sessionManager;

  @Mock private PlaybackSessionManager.Listener mockListener;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    sessionManager = new DefaultPlaybackSessionManager();
    sessionManager.setListener(mockListener);
  }

  @Test
  public void updateSessions_withoutMediaPeriodId_createsNewSession() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    EventTime eventTime = createEventTime(timeline, /* windowIndex= */ 0, /* mediaPeriodId */ null);

    sessionManager.updateSessions(eventTime);

    verify(mockListener).onSessionCreated(eq(eventTime), anyString());
    verify(mockListener).onSessionActive(eq(eventTime), anyString());
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void updateSessions_withMediaPeriodId_createsNewSession() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaPeriodId mediaPeriodId =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0);
    EventTime eventTime = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId);

    sessionManager.updateSessions(eventTime);

    ArgumentCaptor<String> sessionId = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime), sessionId.capture());
    verify(mockListener).onSessionActive(eventTime, sessionId.getValue());
    verifyNoMoreInteractions(mockListener);
    assertThat(sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId))
        .isEqualTo(sessionId.getValue());
  }

  @Test
  public void
      updateSessions_ofSameWindow_withMediaPeriodId_afterWithoutMediaPeriodId_doesNotCreateNewSession() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaPeriodId mediaPeriodId =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0);
    EventTime eventTime1 =
        createEventTime(timeline, /* windowIndex= */ 0, /* mediaPeriodId= */ null);
    EventTime eventTime2 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId);

    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);

    ArgumentCaptor<String> sessionId = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime1), sessionId.capture());
    verify(mockListener).onSessionActive(eventTime1, sessionId.getValue());
    verifyNoMoreInteractions(mockListener);
    assertThat(sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId))
        .isEqualTo(sessionId.getValue());
  }

  @Test
  public void updateSessions_ofSameWindow_withAd_afterWithoutMediaPeriodId_createsNewSession() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaPeriodId mediaPeriodId =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0),
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0,
            /* windowSequenceNumber= */ 0);
    EventTime eventTime1 =
        createEventTime(timeline, /* windowIndex= */ 0, /* mediaPeriodId= */ null);
    EventTime eventTime2 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId);

    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);

    ArgumentCaptor<String> contentSessionId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> adSessionId = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime1), contentSessionId.capture());
    verify(mockListener).onSessionCreated(eq(eventTime2), adSessionId.capture());
    verify(mockListener).onSessionActive(eventTime1, contentSessionId.getValue());
    verifyNoMoreInteractions(mockListener);
    assertThat(contentSessionId).isNotEqualTo(adSessionId);
    assertThat(sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId))
        .isEqualTo(adSessionId.getValue());
  }

  @Test
  public void
      updateSessions_ofSameWindow_withoutMediaPeriodId_afterMediaPeriodId_doesNotCreateNewSession() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaPeriodId mediaPeriodId =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0);
    EventTime eventTime1 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId);
    EventTime eventTime2 =
        createEventTime(timeline, /* windowIndex= */ 0, /* mediaPeriodId= */ null);

    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);

    ArgumentCaptor<String> sessionId = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime1), sessionId.capture());
    verify(mockListener).onSessionActive(eventTime1, sessionId.getValue());
    verifyNoMoreInteractions(mockListener);
    assertThat(sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId))
        .isEqualTo(sessionId.getValue());
  }

  @Test
  public void updateSessions_ofSameWindow_withoutMediaPeriodId_afterAd_doesNotCreateNewSession() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaPeriodId mediaPeriodId =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0),
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0,
            /* windowSequenceNumber= */ 0);
    EventTime eventTime1 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId);
    EventTime eventTime2 =
        createEventTime(timeline, /* windowIndex= */ 0, /* mediaPeriodId= */ null);

    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);

    ArgumentCaptor<String> sessionId = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime1), sessionId.capture());
    verify(mockListener).onSessionActive(eventTime1, sessionId.getValue());
    verifyNoMoreInteractions(mockListener);
    assertThat(sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId))
        .isEqualTo(sessionId.getValue());
  }

  @Test
  public void updateSessions_withOtherMediaPeriodId_ofSameWindow_doesNotCreateNewSession() {
    Timeline timeline =
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 2, /* id= */ 0));
    MediaPeriodId mediaPeriodId1 =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0);
    MediaPeriodId mediaPeriodId2 =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 0);
    EventTime eventTime1 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId1);
    EventTime eventTime2 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId2);

    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);

    ArgumentCaptor<String> sessionId = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime1), sessionId.capture());
    verify(mockListener).onSessionActive(eventTime1, sessionId.getValue());
    verifyNoMoreInteractions(mockListener);
    assertThat(sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId1))
        .isEqualTo(sessionId.getValue());
    assertThat(sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId2))
        .isEqualTo(sessionId.getValue());
  }

  @Test
  public void updateSessions_withAd_ofSameWindow_createsNewSession() {
    Timeline timeline =
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 2, /* id= */ 0));
    MediaPeriodId mediaPeriodId1 =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0);
    MediaPeriodId mediaPeriodId2 =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0),
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0,
            /* windowSequenceNumber= */ 0);
    EventTime eventTime1 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId1);
    EventTime eventTime2 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId2);

    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);

    ArgumentCaptor<String> contentSessionId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> adSessionId = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime1), contentSessionId.capture());
    verify(mockListener).onSessionActive(eventTime1, contentSessionId.getValue());
    verify(mockListener).onSessionCreated(eq(eventTime2), adSessionId.capture());
    verifyNoMoreInteractions(mockListener);
    assertThat(contentSessionId).isNotEqualTo(adSessionId);
    assertThat(sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId1))
        .isEqualTo(contentSessionId.getValue());
    assertThat(sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId2))
        .isEqualTo(adSessionId.getValue());
  }

  @Test
  public void updateSessions_ofOtherWindow_createsNewSession() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    EventTime eventTime1 =
        createEventTime(timeline, /* windowIndex= */ 0, /* mediaPeriodId= */ null);
    EventTime eventTime2 =
        createEventTime(timeline, /* windowIndex= */ 1, /* mediaPeriodId= */ null);

    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);

    ArgumentCaptor<String> sessionId1 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> sessionId2 = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime1), sessionId1.capture());
    verify(mockListener).onSessionCreated(eq(eventTime2), sessionId2.capture());
    verify(mockListener).onSessionActive(eventTime1, sessionId1.getValue());
    verifyNoMoreInteractions(mockListener);
    assertThat(sessionId1).isNotEqualTo(sessionId2);
  }

  @Test
  public void updateSessions_withMediaPeriodId_ofOtherWindow_createsNewSession() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    MediaPeriodId mediaPeriodId1 =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0);
    MediaPeriodId mediaPeriodId2 =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 1);
    EventTime eventTime1 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId1);
    EventTime eventTime2 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId2);

    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);

    ArgumentCaptor<String> sessionId1 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> sessionId2 = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime1), sessionId1.capture());
    verify(mockListener).onSessionCreated(eq(eventTime2), sessionId2.capture());
    verify(mockListener).onSessionActive(eventTime1, sessionId1.getValue());
    verifyNoMoreInteractions(mockListener);
    assertThat(sessionId1).isNotEqualTo(sessionId2);
    assertThat(sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId1))
        .isEqualTo(sessionId1.getValue());
    assertThat(sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId2))
        .isEqualTo(sessionId2.getValue());
  }

  @Test
  public void updateSessions_ofSameWindow_withNewWindowSequenceNumber_createsNewSession() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaPeriodId mediaPeriodId1 =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0);
    MediaPeriodId mediaPeriodId2 =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 1);
    EventTime eventTime1 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId1);
    EventTime eventTime2 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId2);

    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);

    ArgumentCaptor<String> sessionId1 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> sessionId2 = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime1), sessionId1.capture());
    verify(mockListener).onSessionActive(eventTime1, sessionId1.getValue());
    verify(mockListener).onSessionCreated(eq(eventTime2), sessionId2.capture());
    verifyNoMoreInteractions(mockListener);
    assertThat(sessionId1).isNotEqualTo(sessionId2);
    assertThat(sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId1))
        .isEqualTo(sessionId1.getValue());
    assertThat(sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId2))
        .isEqualTo(sessionId2.getValue());
  }

  @Test
  public void
      updateSessions_withoutMediaPeriodId_andPreviouslyCreatedSessions_doesNotCreateNewSession() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaPeriodId mediaPeriodId1 =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0);
    MediaPeriodId mediaPeriodId2 =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 1);
    MediaPeriodId mediaPeriodIdWithAd =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0),
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0,
            /* windowSequenceNumber= */ 0);
    EventTime eventTime1 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId1);
    EventTime eventTime2 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId2);
    EventTime eventTime3 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodIdWithAd);
    EventTime eventTime4 =
        createEventTime(timeline, /* windowIndex= */ 0, /* mediaPeriodId= */ null);

    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);
    sessionManager.updateSessions(eventTime3);
    sessionManager.updateSessions(eventTime4);

    verify(mockListener).onSessionCreated(eq(eventTime1), anyString());
    verify(mockListener).onSessionActive(eq(eventTime1), anyString());
    verify(mockListener).onSessionCreated(eq(eventTime2), anyString());
    verify(mockListener).onSessionCreated(eq(eventTime3), anyString());
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void getSessionForMediaPeriodId_returnsValue_butDoesNotCreateSession() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaPeriodId mediaPeriodId =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0);
    String session = sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId);

    assertThat(session).isNotEmpty();
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void updateSessions_afterSessionForMediaPeriodId_withSameMediaPeriodId_returnsSameValue() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaPeriodId mediaPeriodId =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0);
    EventTime eventTime = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId);

    String expectedSessionId = sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId);
    sessionManager.updateSessions(eventTime);

    ArgumentCaptor<String> sessionId = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime), sessionId.capture());
    verify(mockListener).onSessionActive(eventTime, sessionId.getValue());
    verifyNoMoreInteractions(mockListener);
    assertThat(sessionId.getValue()).isEqualTo(expectedSessionId);
  }

  @Test
  public void updateSessions_withoutMediaPeriodId_afterSessionForMediaPeriodId_returnsSameValue() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaPeriodId mediaPeriodId =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0);
    EventTime eventTime =
        createEventTime(timeline, /* windowIndex= */ 0, /* mediaPeriodId= */ null);

    String expectedSessionId = sessionManager.getSessionForMediaPeriodId(timeline, mediaPeriodId);
    sessionManager.updateSessions(eventTime);

    ArgumentCaptor<String> sessionId = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime), sessionId.capture());
    verify(mockListener).onSessionActive(eventTime, sessionId.getValue());
    verifyNoMoreInteractions(mockListener);
    assertThat(sessionId.getValue()).isEqualTo(expectedSessionId);
  }

  @Test
  public void belongsToSession_withSameWindowIndex_returnsTrue() {
    EventTime eventTime =
        createEventTime(Timeline.EMPTY, /* windowIndex= */ 0, /* mediaPeriodId= */ null);
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    EventTime eventTimeWithTimeline =
        createEventTime(timeline, /* windowIndex= */ 0, /* mediaPeriodId= */ null);
    MediaPeriodId mediaPeriodId =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0);
    EventTime eventTimeWithMediaPeriodId =
        createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId);
    sessionManager.updateSessions(eventTime);

    ArgumentCaptor<String> sessionId = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime), sessionId.capture());
    assertThat(sessionManager.belongsToSession(eventTime, sessionId.getValue())).isTrue();
    assertThat(sessionManager.belongsToSession(eventTimeWithTimeline, sessionId.getValue()))
        .isTrue();
    assertThat(sessionManager.belongsToSession(eventTimeWithMediaPeriodId, sessionId.getValue()))
        .isTrue();
  }

  @Test
  public void belongsToSession_withOtherWindowIndex_returnsFalse() {
    EventTime eventTime =
        createEventTime(Timeline.EMPTY, /* windowIndex= */ 0, /* mediaPeriodId= */ null);
    EventTime eventTimeOtherWindow =
        createEventTime(Timeline.EMPTY, /* windowIndex= */ 1, /* mediaPeriodId= */ null);
    Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    MediaPeriodId mediaPeriodId =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 1);
    EventTime eventTimeWithOtherMediaPeriodId =
        createEventTime(timeline, /* windowIndex= */ 1, mediaPeriodId);
    sessionManager.updateSessions(eventTime);

    ArgumentCaptor<String> sessionId = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime), sessionId.capture());
    assertThat(sessionManager.belongsToSession(eventTimeOtherWindow, sessionId.getValue()))
        .isFalse();
    assertThat(
            sessionManager.belongsToSession(eventTimeWithOtherMediaPeriodId, sessionId.getValue()))
        .isFalse();
  }

  @Test
  public void belongsToSession_withOtherWindowSequenceNumber_returnsFalse() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaPeriodId mediaPeriodId1 =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0);
    MediaPeriodId mediaPeriodId2 =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 1);
    EventTime eventTime1 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId1);
    EventTime eventTime2 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId2);
    sessionManager.updateSessions(eventTime1);

    ArgumentCaptor<String> sessionId = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime1), sessionId.capture());
    assertThat(sessionManager.belongsToSession(eventTime2, sessionId.getValue())).isFalse();
  }

  @Test
  public void belongsToSession_withAd_returnsFalse() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaPeriodId mediaPeriodId1 =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0);
    MediaPeriodId mediaPeriodId2 =
        new MediaPeriodId(
            timeline.getUidOfPeriod(/* periodIndex= */ 0),
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0,
            /* windowSequenceNumber= */ 1);
    EventTime eventTime1 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId1);
    EventTime eventTime2 = createEventTime(timeline, /* windowIndex= */ 0, mediaPeriodId2);
    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);

    ArgumentCaptor<String> sessionId1 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> sessionId2 = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime1), sessionId1.capture());
    verify(mockListener).onSessionCreated(eq(eventTime2), sessionId2.capture());
    assertThat(sessionManager.belongsToSession(eventTime2, sessionId1.getValue())).isFalse();
    assertThat(sessionManager.belongsToSession(eventTime1, sessionId2.getValue())).isFalse();
    assertThat(sessionManager.belongsToSession(eventTime2, sessionId2.getValue())).isTrue();
  }

  @Test
  public void initialTimelineUpdate_finishesAllSessionsOutsideTimeline() {
    EventTime eventTime1 =
        createEventTime(Timeline.EMPTY, /* windowIndex= */ 0, /* mediaPeriodId= */ null);
    EventTime eventTime2 =
        createEventTime(Timeline.EMPTY, /* windowIndex= */ 1, /* mediaPeriodId= */ null);
    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    EventTime newTimelineEventTime =
        createEventTime(timeline, /* windowIndex= */ 0, /* mediaPeriodId= */ null);

    sessionManager.handleTimelineUpdate(newTimelineEventTime);
    sessionManager.updateSessions(newTimelineEventTime);

    ArgumentCaptor<String> sessionId1 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> sessionId2 = ArgumentCaptor.forClass(String.class);
    verify(mockListener).onSessionCreated(eq(eventTime1), sessionId1.capture());
    verify(mockListener).onSessionCreated(eq(eventTime2), sessionId2.capture());
    verify(mockListener).onSessionActive(eventTime1, sessionId1.getValue());
    verify(mockListener)
        .onSessionFinished(
            newTimelineEventTime,
            sessionId2.getValue(),
            /* automaticTransitionToNextPlayback= */ false);
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void dynamicTimelineUpdate_resolvesWindowIndices() {
    Timeline initialTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 2, /* id= */ 100),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 200),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 300));
    EventTime eventForInitialTimelineId100 =
        createEventTime(
            initialTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                initialTimeline.getUidOfPeriod(/* periodIndex= */ 1),
                /* windowSequenceNumber= */ 0));
    EventTime eventForInitialTimelineId200 =
        createEventTime(
            initialTimeline,
            /* windowIndex= */ 1,
            new MediaPeriodId(
                initialTimeline.getUidOfPeriod(/* periodIndex= */ 2),
                /* windowSequenceNumber= */ 1));
    EventTime eventForInitialTimelineId300 =
        createEventTime(
            initialTimeline,
            /* windowIndex= */ 2,
            new MediaPeriodId(
                initialTimeline.getUidOfPeriod(/* periodIndex= */ 3),
                /* windowSequenceNumber= */ 2));
    sessionManager.handleTimelineUpdate(eventForInitialTimelineId100);
    sessionManager.updateSessions(eventForInitialTimelineId100);
    sessionManager.updateSessions(eventForInitialTimelineId200);
    sessionManager.updateSessions(eventForInitialTimelineId300);
    String sessionId100 =
        sessionManager.getSessionForMediaPeriodId(
            initialTimeline, eventForInitialTimelineId100.mediaPeriodId);
    String sessionId200 =
        sessionManager.getSessionForMediaPeriodId(
            initialTimeline, eventForInitialTimelineId200.mediaPeriodId);
    String sessionId300 =
        sessionManager.getSessionForMediaPeriodId(
            initialTimeline, eventForInitialTimelineId300.mediaPeriodId);

    Timeline timelineUpdate =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 300),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 100));
    EventTime eventForTimelineUpdateId100 =
        createEventTime(
            timelineUpdate,
            /* windowIndex= */ 1,
            new MediaPeriodId(
                timelineUpdate.getUidOfPeriod(/* periodIndex= */ 1),
                /* windowSequenceNumber= */ 0));
    EventTime eventForTimelineUpdateId300 =
        createEventTime(
            timelineUpdate,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                timelineUpdate.getUidOfPeriod(/* periodIndex= */ 0),
                /* windowSequenceNumber= */ 2));

    sessionManager.handleTimelineUpdate(eventForTimelineUpdateId100);
    String updatedSessionId100 =
        sessionManager.getSessionForMediaPeriodId(
            timelineUpdate, eventForTimelineUpdateId100.mediaPeriodId);
    String updatedSessionId300 =
        sessionManager.getSessionForMediaPeriodId(
            timelineUpdate, eventForTimelineUpdateId300.mediaPeriodId);

    verify(mockListener).onSessionCreated(eventForInitialTimelineId100, sessionId100);
    verify(mockListener).onSessionActive(eventForInitialTimelineId100, sessionId100);
    verify(mockListener).onSessionCreated(eventForInitialTimelineId200, sessionId200);
    verify(mockListener).onSessionCreated(eventForInitialTimelineId300, sessionId300);
    verify(mockListener)
        .onSessionFinished(
            eventForTimelineUpdateId100,
            sessionId200,
            /* automaticTransitionToNextPlayback= */ false);
    verifyNoMoreInteractions(mockListener);
    assertThat(updatedSessionId100).isEqualTo(sessionId100);
    assertThat(updatedSessionId300).isEqualTo(sessionId300);
  }

  @Test
  public void timelineUpdate_withContent_doesNotFinishFuturePostrollAd() {
    Timeline adTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs =*/ 10 * C.MICROS_PER_SECOND,
                new AdPlaybackState(/* adGroupTimesUs= */ C.TIME_END_OF_SOURCE)
                    .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)));
    EventTime adEventTime =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0));
    EventTime contentEventTime =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* windowSequenceNumber= */ 0,
                /* nextAdGroupIndex= */ 0));
    sessionManager.updateSessions(contentEventTime);
    sessionManager.updateSessions(adEventTime);

    sessionManager.handleTimelineUpdate(contentEventTime);

    verify(mockListener, never()).onSessionFinished(any(), anyString(), anyBoolean());
  }

  @Test
  public void positionDiscontinuity_withinWindow_doesNotFinishSession() {
    Timeline timeline =
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 2, /* id= */ 100));
    EventTime eventTime1 =
        createEventTime(
            timeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0));
    EventTime eventTime2 =
        createEventTime(
            timeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 0));
    sessionManager.handleTimelineUpdate(eventTime1);
    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);

    sessionManager.handlePositionDiscontinuity(
        eventTime2, Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
    sessionManager.updateSessions(eventTime2);

    verify(mockListener).onSessionCreated(eq(eventTime1), anyString());
    verify(mockListener).onSessionActive(eq(eventTime1), anyString());
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void positionDiscontinuity_toNewWindow_withPeriodTransitionReason_finishesSession() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    EventTime eventTime1 =
        createEventTime(
            timeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0));
    EventTime eventTime2 =
        createEventTime(
            timeline,
            /* windowIndex= */ 1,
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 1));
    sessionManager.handleTimelineUpdate(eventTime1);
    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);
    String sessionId1 =
        sessionManager.getSessionForMediaPeriodId(timeline, eventTime1.mediaPeriodId);
    String sessionId2 =
        sessionManager.getSessionForMediaPeriodId(timeline, eventTime2.mediaPeriodId);

    sessionManager.handlePositionDiscontinuity(
        eventTime2, Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
    sessionManager.updateSessions(eventTime2);

    verify(mockListener).onSessionCreated(eventTime1, sessionId1);
    verify(mockListener).onSessionActive(eventTime1, sessionId1);
    verify(mockListener).onSessionCreated(eq(eventTime2), anyString());
    verify(mockListener)
        .onSessionFinished(eventTime2, sessionId1, /* automaticTransitionToNextPlayback= */ true);
    verify(mockListener).onSessionActive(eventTime2, sessionId2);
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void positionDiscontinuity_toNewWindow_withSeekTransitionReason_finishesSession() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    EventTime eventTime1 =
        createEventTime(
            timeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0));
    EventTime eventTime2 =
        createEventTime(
            timeline,
            /* windowIndex= */ 1,
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 1));
    sessionManager.handleTimelineUpdate(eventTime1);
    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);
    String sessionId1 =
        sessionManager.getSessionForMediaPeriodId(timeline, eventTime1.mediaPeriodId);
    String sessionId2 =
        sessionManager.getSessionForMediaPeriodId(timeline, eventTime2.mediaPeriodId);

    sessionManager.handlePositionDiscontinuity(eventTime2, Player.DISCONTINUITY_REASON_SEEK);
    sessionManager.updateSessions(eventTime2);

    verify(mockListener).onSessionCreated(eventTime1, sessionId1);
    verify(mockListener).onSessionActive(eventTime1, sessionId1);
    verify(mockListener).onSessionCreated(eq(eventTime2), anyString());
    verify(mockListener)
        .onSessionFinished(eventTime2, sessionId1, /* automaticTransitionToNextPlayback= */ false);
    verify(mockListener).onSessionActive(eventTime2, sessionId2);
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void positionDiscontinuity_toSameWindow_withoutMediaPeriodId_doesNotFinishSession() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    EventTime eventTime1 =
        createEventTime(
            timeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0));
    EventTime eventTime2 =
        createEventTime(timeline, /* windowIndex= */ 0, /* mediaPeriodId= */ null);
    sessionManager.handleTimelineUpdate(eventTime1);
    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);

    sessionManager.handlePositionDiscontinuity(eventTime2, Player.DISCONTINUITY_REASON_SEEK);
    sessionManager.updateSessions(eventTime2);

    verify(mockListener, never()).onSessionFinished(any(), anyString(), anyBoolean());
  }

  @Test
  public void positionDiscontinuity_toNewWindow_finishesOnlyPastSessions() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 4);
    EventTime eventTime1 =
        createEventTime(
            timeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0));
    EventTime eventTime2 =
        createEventTime(
            timeline,
            /* windowIndex= */ 1,
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 1));
    EventTime eventTime3 =
        createEventTime(
            timeline,
            /* windowIndex= */ 2,
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 2), /* windowSequenceNumber= */ 2));
    EventTime eventTime4 =
        createEventTime(
            timeline,
            /* windowIndex= */ 3,
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 3), /* windowSequenceNumber= */ 3));
    sessionManager.handleTimelineUpdate(eventTime1);
    sessionManager.updateSessions(eventTime1);
    sessionManager.updateSessions(eventTime2);
    sessionManager.updateSessions(eventTime3);
    sessionManager.updateSessions(eventTime4);
    String sessionId1 =
        sessionManager.getSessionForMediaPeriodId(timeline, eventTime1.mediaPeriodId);
    String sessionId2 =
        sessionManager.getSessionForMediaPeriodId(timeline, eventTime2.mediaPeriodId);

    sessionManager.handlePositionDiscontinuity(eventTime3, Player.DISCONTINUITY_REASON_SEEK);
    sessionManager.updateSessions(eventTime3);

    verify(mockListener).onSessionCreated(eventTime1, sessionId1);
    verify(mockListener).onSessionActive(eventTime1, sessionId1);
    verify(mockListener).onSessionCreated(eventTime2, sessionId2);
    verify(mockListener).onSessionCreated(eq(eventTime3), anyString());
    verify(mockListener).onSessionCreated(eq(eventTime4), anyString());
    verify(mockListener)
        .onSessionFinished(eventTime3, sessionId1, /* automaticTransitionToNextPlayback= */ false);
    verify(mockListener)
        .onSessionFinished(eventTime3, sessionId2, /* automaticTransitionToNextPlayback= */ false);
    verify(mockListener).onSessionActive(eq(eventTime3), anyString());
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void positionDiscontinuity_fromAdToContent_finishesAd() {
    Timeline adTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs =*/ 10 * C.MICROS_PER_SECOND,
                new AdPlaybackState(/* adGroupTimesUs= */ 0, 5 * C.MICROS_PER_SECOND)
                    .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
                    .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1)));
    EventTime adEventTime1 =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0));
    EventTime adEventTime2 =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* adGroupIndex= */ 1,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0));
    EventTime contentEventTime =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* windowSequenceNumber= */ 0,
                /* nextAdGroupIndex= */ 1));
    sessionManager.handleTimelineUpdate(adEventTime1);
    sessionManager.updateSessions(adEventTime1);
    sessionManager.updateSessions(adEventTime2);
    String adSessionId1 =
        sessionManager.getSessionForMediaPeriodId(adTimeline, adEventTime1.mediaPeriodId);

    sessionManager.handlePositionDiscontinuity(
        contentEventTime, Player.DISCONTINUITY_REASON_AD_INSERTION);
    sessionManager.updateSessions(contentEventTime);

    verify(mockListener).onSessionCreated(adEventTime1, adSessionId1);
    verify(mockListener).onSessionActive(adEventTime1, adSessionId1);
    verify(mockListener).onSessionCreated(eq(adEventTime2), anyString());
    verify(mockListener)
        .onSessionFinished(
            contentEventTime, adSessionId1, /* automaticTransitionToNextPlayback= */ true);
    verify(mockListener).onSessionCreated(eq(contentEventTime), anyString());
    verify(mockListener).onSessionActive(eq(contentEventTime), anyString());
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void positionDiscontinuity_fromContentToAd_doesNotFinishSessions() {
    Timeline adTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs =*/ 10 * C.MICROS_PER_SECOND,
                new AdPlaybackState(
                        /* adGroupTimesUs= */ 2 * C.MICROS_PER_SECOND, 5 * C.MICROS_PER_SECOND)
                    .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
                    .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1)));
    EventTime adEventTime1 =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0));
    EventTime adEventTime2 =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* adGroupIndex= */ 1,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0));
    EventTime contentEventTime =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* windowSequenceNumber= */ 0,
                /* nextAdGroupIndex= */ 0));
    sessionManager.handleTimelineUpdate(contentEventTime);
    sessionManager.updateSessions(contentEventTime);
    sessionManager.updateSessions(adEventTime1);
    sessionManager.updateSessions(adEventTime2);

    sessionManager.handlePositionDiscontinuity(
        adEventTime1, Player.DISCONTINUITY_REASON_AD_INSERTION);
    sessionManager.updateSessions(adEventTime1);

    verify(mockListener, never()).onSessionFinished(any(), anyString(), anyBoolean());
  }

  @Test
  public void positionDiscontinuity_fromAdToAd_finishesPastAds_andNotifiesAdPlaybackStated() {
    Timeline adTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs =*/ 10 * C.MICROS_PER_SECOND,
                new AdPlaybackState(/* adGroupTimesUs= */ 0, 5 * C.MICROS_PER_SECOND)
                    .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
                    .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1)));
    EventTime adEventTime1 =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0));
    EventTime adEventTime2 =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* adGroupIndex= */ 1,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0));
    EventTime contentEventTime =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* windowSequenceNumber= */ 0,
                /* nextAdGroupIndex= */ 1));
    sessionManager.handleTimelineUpdate(contentEventTime);
    sessionManager.updateSessions(contentEventTime);
    sessionManager.updateSessions(adEventTime1);
    sessionManager.updateSessions(adEventTime2);
    String contentSessionId =
        sessionManager.getSessionForMediaPeriodId(adTimeline, contentEventTime.mediaPeriodId);
    String adSessionId1 =
        sessionManager.getSessionForMediaPeriodId(adTimeline, adEventTime1.mediaPeriodId);
    String adSessionId2 =
        sessionManager.getSessionForMediaPeriodId(adTimeline, adEventTime2.mediaPeriodId);

    sessionManager.handlePositionDiscontinuity(
        adEventTime1, Player.DISCONTINUITY_REASON_AD_INSERTION);
    sessionManager.updateSessions(adEventTime1);
    sessionManager.handlePositionDiscontinuity(adEventTime2, Player.DISCONTINUITY_REASON_SEEK);
    sessionManager.updateSessions(adEventTime2);

    verify(mockListener).onSessionCreated(eq(contentEventTime), anyString());
    verify(mockListener).onSessionActive(eq(contentEventTime), anyString());
    verify(mockListener).onSessionCreated(adEventTime1, adSessionId1);
    verify(mockListener).onSessionCreated(adEventTime2, adSessionId2);
    verify(mockListener).onAdPlaybackStarted(adEventTime1, contentSessionId, adSessionId1);
    verify(mockListener).onSessionActive(adEventTime1, adSessionId1);
    verify(mockListener)
        .onSessionFinished(
            adEventTime2, adSessionId1, /* automaticTransitionToNextPlayback= */ false);
    verify(mockListener).onAdPlaybackStarted(adEventTime2, contentSessionId, adSessionId2);
    verify(mockListener).onSessionActive(adEventTime2, adSessionId2);
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void
      updateSessions_withNewAd_afterDiscontinuitiesFromContentToAdAndBack_doesNotActivateNewAd() {
    Timeline adTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs =*/ 10 * C.MICROS_PER_SECOND,
                new AdPlaybackState(
                        /* adGroupTimesUs= */ 2 * C.MICROS_PER_SECOND, 5 * C.MICROS_PER_SECOND)
                    .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
                    .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1)));
    EventTime adEventTime1 =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0));
    EventTime adEventTime2 =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* adGroupIndex= */ 1,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0));
    EventTime contentEventTime1 =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* windowSequenceNumber= */ 0,
                /* nextAdGroupIndex= */ 0));
    EventTime contentEventTime2 =
        createEventTime(
            adTimeline,
            /* windowIndex= */ 0,
            new MediaPeriodId(
                adTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* windowSequenceNumber= */ 0,
                /* nextAdGroupIndex= */ 1));
    sessionManager.handleTimelineUpdate(contentEventTime1);
    sessionManager.updateSessions(contentEventTime1);
    sessionManager.updateSessions(adEventTime1);
    sessionManager.handlePositionDiscontinuity(
        adEventTime1, Player.DISCONTINUITY_REASON_AD_INSERTION);
    sessionManager.updateSessions(adEventTime1);
    sessionManager.handlePositionDiscontinuity(
        contentEventTime2, Player.DISCONTINUITY_REASON_AD_INSERTION);
    sessionManager.updateSessions(contentEventTime2);
    String adSessionId2 =
        sessionManager.getSessionForMediaPeriodId(adTimeline, adEventTime2.mediaPeriodId);

    sessionManager.updateSessions(adEventTime2);

    verify(mockListener, never()).onSessionActive(any(), eq(adSessionId2));
  }

  @Test
  public void finishAllSessions_callsOnSessionFinishedForAllCreatedSessions() {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 4);
    EventTime eventTimeWindow0 =
        createEventTime(timeline, /* windowIndex= */ 0, /* mediaPeriodId= */ null);
    EventTime eventTimeWindow2 =
        createEventTime(timeline, /* windowIndex= */ 2, /* mediaPeriodId= */ null);
    // Actually create sessions for window 0 and 2.
    sessionManager.updateSessions(eventTimeWindow0);
    sessionManager.updateSessions(eventTimeWindow2);
    // Query information about session for window 1, but don't create it.
    sessionManager.getSessionForMediaPeriodId(
        timeline,
        new MediaPeriodId(
            timeline.getPeriod(/* periodIndex= */ 1, new Timeline.Period(), /* setIds= */ true).uid,
            /* windowSequenceNumber= */ 123));
    verify(mockListener, times(2)).onSessionCreated(any(), anyString());

    EventTime finishEventTime =
        createEventTime(Timeline.EMPTY, /* windowIndex= */ 0, /* mediaPeriodId= */ null);
    sessionManager.finishAllSessions(finishEventTime);

    verify(mockListener, times(2)).onSessionFinished(eq(finishEventTime), anyString(), eq(false));
  }

  private static EventTime createEventTime(
      Timeline timeline, int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
    return new EventTime(
        /* realtimeMs = */ 0,
        timeline,
        windowIndex,
        mediaPeriodId,
        /* eventPlaybackPositionMs= */ 0,
        /* currentPlaybackPositionMs= */ 0,
        /* totalBufferedDurationMs= */ 0);
  }
}
