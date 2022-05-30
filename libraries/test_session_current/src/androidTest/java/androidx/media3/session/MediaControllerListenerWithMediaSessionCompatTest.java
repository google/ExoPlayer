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
package androidx.media3.session;

import static androidx.media3.common.Player.EVENT_REPEAT_MODE_CHANGED;
import static androidx.media3.session.SessionResult.RESULT_SUCCESS;
import static androidx.media3.test.session.common.CommonConstants.DEFAULT_TEST_NAME;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.media3.common.C;
import androidx.media3.common.FlagSet;
import androidx.media3.common.Player;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
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

/** Tests for {@link MediaController.Listener} with {@link MediaSessionCompat}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerListenerWithMediaSessionCompatTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private static final int EVENT_ON_EVENTS = C.INDEX_UNSET;

  private final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaControllerListenerWithMediaSessionCompatTest");
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
  public void onEvents_whenOnRepeatModeChanges_isCalledAfterOtherListenerMethods()
      throws Exception {
    Player.Events testEvents =
        new Player.Events(new FlagSet.Builder().add(EVENT_REPEAT_MODE_CHANGED).build());
    CopyOnWriteArrayList<Integer> listenerEventCodes = new CopyOnWriteArrayList<>();

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
            listenerEventCodes.add(EVENT_REPEAT_MODE_CHANGED);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            listenerEventCodes.add(EVENT_ON_EVENTS);
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_GROUP);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(listenerEventCodes).containsExactly(EVENT_REPEAT_MODE_CHANGED, EVENT_ON_EVENTS);
    assertThat(eventsRef.get()).isEqualTo(testEvents);
  }

  @Test
  public void setPlaybackState_withCustomActions_onSetCustomLayoutCalled() throws Exception {
    Bundle extras1 = new Bundle();
    extras1.putString("key", "value-1");
    PlaybackStateCompat.CustomAction customAction1 =
        new PlaybackStateCompat.CustomAction.Builder("action1", "actionName1", /* icon= */ 1)
            .setExtras(extras1)
            .build();
    Bundle extras2 = new Bundle();
    extras2.putString("key", "value-2");
    PlaybackStateCompat.CustomAction customAction2 =
        new PlaybackStateCompat.CustomAction.Builder("action2", "actionName2", /* icon= */ 2)
            .setExtras(extras2)
            .build();
    PlaybackStateCompat.Builder builder =
        new PlaybackStateCompat.Builder()
            .addCustomAction(customAction1)
            .addCustomAction(customAction2);
    List<String> receivedActions = new ArrayList<>();
    List<String> receivedDisplayNames = new ArrayList<>();
    List<String> receivedBundleValues = new ArrayList<>();
    List<Integer> receivedIconResIds = new ArrayList<>();
    List<Integer> receivedCommandCodes = new ArrayList<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);
    controllerTestRule.createController(
        session.getSessionToken(),
        new MediaController.Listener() {
          @Override
          public ListenableFuture<SessionResult> onSetCustomLayout(
              MediaController controller, List<CommandButton> layout) {
            for (CommandButton button : layout) {
              receivedActions.add(button.sessionCommand.customAction);
              receivedDisplayNames.add(String.valueOf(button.displayName));
              receivedBundleValues.add(button.sessionCommand.customExtras.getString("key"));
              receivedCommandCodes.add(button.sessionCommand.commandCode);
              receivedIconResIds.add(button.iconResId);
            }
            countDownLatch.countDown();
            return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
          }
        });

    session.setPlaybackState(builder.build());

    assertThat(countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedActions).containsExactly("action1", "action2").inOrder();
    assertThat(receivedCommandCodes)
        .containsExactly(SessionCommand.COMMAND_CODE_CUSTOM, SessionCommand.COMMAND_CODE_CUSTOM)
        .inOrder();
    assertThat(receivedDisplayNames).containsExactly("actionName1", "actionName2").inOrder();
    assertThat(receivedIconResIds).containsExactly(1, 2).inOrder();
    assertThat(receivedBundleValues).containsExactly("value-1", "value-2").inOrder();
  }

  @Test
  public void setSessionExtras_onExtrasChangedCalled() throws Exception {
    Bundle sessionExtras = new Bundle();
    sessionExtras.putString("key-1", "value-1");
    CountDownLatch countDownLatch = new CountDownLatch(1);
    List<Bundle> receivedSessionExtras = new ArrayList<>();
    controllerTestRule.createController(
        session.getSessionToken(),
        new MediaController.Listener() {
          @Override
          public void onExtrasChanged(MediaController controller, Bundle extras) {
            receivedSessionExtras.add(extras);
            countDownLatch.countDown();
          }
        });

    session.setExtras(sessionExtras);

    assertThat(countDownLatch.await(1_000, MILLISECONDS)).isTrue();
    assertThat(TestUtils.equals(receivedSessionExtras.get(0), sessionExtras)).isTrue();
  }
}
