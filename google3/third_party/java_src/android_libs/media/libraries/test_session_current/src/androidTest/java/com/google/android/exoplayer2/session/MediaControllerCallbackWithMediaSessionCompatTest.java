/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.session;

import static com.google.android.exoplayer2.Player.EVENT_REPEAT_MODE_CHANGED;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.DEFAULT_TEST_NAME;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.RemoteException;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.session.vct.common.HandlerThreadTestRule;
import com.google.android.exoplayer2.session.vct.common.MainLooperTestRule;
import com.google.android.exoplayer2.util.ExoFlags;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/** Tests for {@link MediaController.ControllerCallback} with {@link MediaSessionCompat}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerCallbackWithMediaSessionCompatTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private static final int EVENT_ON_EVENTS = C.INDEX_UNSET;

  private final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaControllerCallbackTest");
  private final MediaControllerTestRule controllerTestRule =
      new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  private Context context;
  private RemoteMediaSessionCompat session;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    session = new RemoteMediaSessionCompat(DEFAULT_TEST_NAME, context);
  }

  @After
  public void cleanUp() throws RemoteException {
    session.cleanUp();
  }

  @Test
  public void onEvents_whenOnRepeatModeChanges_isCalledAfterOtherCallbacks() throws Exception {
    Player.Events testEvents =
        new Player.Events(new ExoFlags.Builder().add(EVENT_REPEAT_MODE_CHANGED).build());
    CopyOnWriteArrayList<Integer> callbackEventCodes = new CopyOnWriteArrayList<>();

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
            callbackEventCodes.add(EVENT_REPEAT_MODE_CHANGED);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            callbackEventCodes.add(EVENT_ON_EVENTS);
            eventsRef.set(events);
            latch.countDown();
          }
        };
    controller.addListener(callback);
    session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_GROUP);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(callbackEventCodes).containsExactly(EVENT_REPEAT_MODE_CHANGED, EVENT_ON_EVENTS);
    assertThat(eventsRef.get()).isEqualTo(testEvents);
  }
}
