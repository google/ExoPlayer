/*
 * Copyright (C) 2020 The Android Open Source Project
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
 *
 */
package com.google.android.exoplayer2.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for {@link MediaSourceEventDispatcher}. */
@RunWith(AndroidJUnit4.class)
public class MediaSourceEventDispatcherTest {

  private static final MediaSource.MediaPeriodId MEDIA_PERIOD_ID =
      new MediaSource.MediaPeriodId("test uid");
  private static final int WINDOW_INDEX = 200;
  private static final int MEDIA_TIME_OFFSET_MS = 1_000;

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private MediaSourceEventListener mediaSourceEventListener;
  @Mock private MediaAndDrmEventListener mediaAndDrmEventListener;

  private MediaSourceEventDispatcher eventDispatcher;

  @Before
  public void setupEventDispatcher() {
    eventDispatcher = new MediaSourceEventDispatcher();
    eventDispatcher =
        eventDispatcher.withParameters(WINDOW_INDEX, MEDIA_PERIOD_ID, MEDIA_TIME_OFFSET_MS);
  }

  @Test
  public void listenerReceivesEventPopulatedWithMediaPeriodInfo() {
    eventDispatcher.addEventListener(
        Util.createHandler(), mediaSourceEventListener, MediaSourceEventListener.class);

    eventDispatcher.dispatch(
        MediaSourceEventListener::onMediaPeriodCreated, MediaSourceEventListener.class);

    verify(mediaSourceEventListener).onMediaPeriodCreated(WINDOW_INDEX, MEDIA_PERIOD_ID);
  }

  @Test
  public void sameListenerObjectRegisteredTwiceOnlyReceivesEventsOnce() {
    eventDispatcher.addEventListener(
        Util.createHandler(), mediaSourceEventListener, MediaSourceEventListener.class);
    eventDispatcher.addEventListener(
        Util.createHandler(), mediaSourceEventListener, MediaSourceEventListener.class);

    eventDispatcher.dispatch(
        MediaSourceEventListener::onMediaPeriodCreated, MediaSourceEventListener.class);

    verify(mediaSourceEventListener).onMediaPeriodCreated(WINDOW_INDEX, MEDIA_PERIOD_ID);
  }

  @Test
  public void sameListenerInstanceCanBeRegisteredWithTwoTypes() {
    eventDispatcher.addEventListener(
        new Handler(Looper.getMainLooper()),
        mediaAndDrmEventListener,
        MediaSourceEventListener.class);
    eventDispatcher.addEventListener(
        new Handler(Looper.getMainLooper()),
        mediaAndDrmEventListener,
        DrmSessionEventListener.class);

    eventDispatcher.dispatch(
        MediaSourceEventListener::onMediaPeriodCreated, MediaSourceEventListener.class);
    eventDispatcher.dispatch(
        (listener, windowIndex, mediaPeriodId) -> listener.onDrmKeysLoaded(),
        DrmSessionEventListener.class);

    verify(mediaAndDrmEventListener).onMediaPeriodCreated(WINDOW_INDEX, MEDIA_PERIOD_ID);
    verify(mediaAndDrmEventListener).onDrmKeysLoaded();
  }

  // If a listener is added that implements multiple types, it should only receive events for the
  // type specified at registration time.
  @Test
  public void listenerOnlyReceivesEventsForRegisteredType() {
    eventDispatcher.addEventListener(
        new Handler(Looper.getMainLooper()),
        mediaAndDrmEventListener,
        MediaSourceEventListener.class);

    eventDispatcher.dispatch(
        MediaSourceEventListener::onMediaPeriodCreated, MediaSourceEventListener.class);
    eventDispatcher.dispatch(
        (listener, windowIndex, mediaPeriodId) -> listener.onDrmKeysLoaded(),
        DrmSessionEventListener.class);

    verify(mediaAndDrmEventListener).onMediaPeriodCreated(WINDOW_INDEX, MEDIA_PERIOD_ID);
    verify(mediaAndDrmEventListener, never()).onDrmKeysLoaded();
  }

  @Test
  public void listenerDoesntReceiveEventsDispatchedToSubclass() {
    SubclassListener subclassListener = mock(SubclassListener.class);
    eventDispatcher.addEventListener(
        new Handler(Looper.getMainLooper()), subclassListener, MediaSourceEventListener.class);

    eventDispatcher.dispatch(SubclassListener::subclassMethod, SubclassListener.class);

    // subclassListener can handle the call to subclassMethod, but it isn't called because
    // it was registered 'as-a' MediaSourceEventListener, not SubclassListener.
    verify(subclassListener, never()).subclassMethod(anyInt(), any());
  }

  @Test
  public void listenerDoesntReceiveEventsDispatchedToSuperclass() {
    SubclassListener subclassListener = mock(SubclassListener.class);
    eventDispatcher.addEventListener(
        new Handler(Looper.getMainLooper()), subclassListener, SubclassListener.class);

    eventDispatcher.dispatch(
        MediaSourceEventListener::onMediaPeriodCreated, MediaSourceEventListener.class);

    // subclassListener 'is-a' a MediaSourceEventListener, but it isn't called because the event
    // is dispatched specifically to listeners registered as MediaSourceEventListener.
    verify(subclassListener, never()).onMediaPeriodCreated(anyInt(), any());
  }

  @Test
  public void listenersAreCopiedToNewDispatcher() {
    eventDispatcher.addEventListener(
        Util.createHandler(), mediaSourceEventListener, MediaSourceEventListener.class);

    MediaSource.MediaPeriodId newPeriodId = new MediaSource.MediaPeriodId("different uid");
    MediaSourceEventDispatcher newEventDispatcher =
        this.eventDispatcher.withParameters(
            /* windowIndex= */ 250, newPeriodId, /* mediaTimeOffsetMs= */ 500);

    newEventDispatcher.dispatch(
        MediaSourceEventListener::onMediaPeriodCreated, MediaSourceEventListener.class);

    verify(mediaSourceEventListener).onMediaPeriodCreated(250, newPeriodId);
  }

  @Test
  public void removingListenerStopsEventDispatch() {
    eventDispatcher.addEventListener(
        Util.createHandler(), mediaSourceEventListener, MediaSourceEventListener.class);
    eventDispatcher.removeEventListener(mediaSourceEventListener, MediaSourceEventListener.class);

    eventDispatcher.dispatch(
        MediaSourceEventListener::onMediaPeriodCreated, MediaSourceEventListener.class);

    verify(mediaSourceEventListener, never()).onMediaPeriodCreated(anyInt(), any());
  }

  @Test
  public void removingListenerWithDifferentTypeToRegistrationDoesntRemove() {
    eventDispatcher.addEventListener(
        Util.createHandler(), mediaAndDrmEventListener, MediaSourceEventListener.class);
    eventDispatcher.removeEventListener(mediaAndDrmEventListener, DrmSessionEventListener.class);

    eventDispatcher.dispatch(
        MediaSourceEventListener::onMediaPeriodCreated, MediaSourceEventListener.class);

    verify(mediaAndDrmEventListener).onMediaPeriodCreated(WINDOW_INDEX, MEDIA_PERIOD_ID);
  }

  @Test
  public void listenersAreCountedBasedOnListenerAndType() {
    // Add the listener twice and remove it once.
    eventDispatcher.addEventListener(
        Util.createHandler(), mediaSourceEventListener, MediaSourceEventListener.class);
    eventDispatcher.addEventListener(
        Util.createHandler(), mediaSourceEventListener, MediaSourceEventListener.class);
    eventDispatcher.removeEventListener(mediaSourceEventListener, MediaSourceEventListener.class);

    eventDispatcher.dispatch(
        MediaSourceEventListener::onMediaPeriodCreated, MediaSourceEventListener.class);

    verify(mediaSourceEventListener).onMediaPeriodCreated(WINDOW_INDEX, MEDIA_PERIOD_ID);

    // Remove it a second time and confirm the events stop being propagated.
    eventDispatcher.removeEventListener(mediaSourceEventListener, MediaSourceEventListener.class);

    verifyNoMoreInteractions(mediaSourceEventListener);
  }

  private interface MediaAndDrmEventListener
      extends MediaSourceEventListener, DrmSessionEventListener {}

  private interface SubclassListener extends MediaSourceEventListener {
    void subclassMethod(int windowIndex, @Nullable MediaPeriodId mediaPeriodId);
  }
}
