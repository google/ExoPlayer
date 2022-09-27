/*
 * Copyright 2022 The Android Open Source Project
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

import static androidx.media3.test.session.common.CommonConstants.DEFAULT_TEST_NAME;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/**
 * Tests for {@link MediaControllerCompat.Callback} with {@link MediaSessionCompat}.
 *
 * <p>The tests in this class represents the reference specific usages of the legacy API for which
 * we need to provide support with the Media3 API.
 *
 * <p>So these test are actually not tests but rather a reference of how the legacy API is used and
 * expected to work.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerCompatCallbackWithMediaSessionCompatTest {

  private static final int TIMEOUT_MS = 1_000;

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaControllerCompatCallbackWithMediaSessionCompatTest");
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

  /** Custom actions in the legacy session used for instance by Android Auto and Wear OS. */
  @Test
  @Ignore("Flaky, see b/235057692")
  public void setPlaybackState_withCustomActions_onPlaybackStateCompatChangedCalled()
      throws Exception {
    MediaSessionCompat.Token sessionToken = session.getSessionToken();
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
    CountDownLatch countDownLatch = new CountDownLatch(1);
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              MediaControllerCompat mediaControllerCompat =
                  new MediaControllerCompat(context, sessionToken);
              mediaControllerCompat.registerCallback(
                  new MediaControllerCompat.Callback() {
                    @Override
                    public void onPlaybackStateChanged(PlaybackStateCompat state) {
                      List<PlaybackStateCompat.CustomAction> layout = state.getCustomActions();
                      for (PlaybackStateCompat.CustomAction action : layout) {
                        receivedActions.add(action.getAction());
                        receivedDisplayNames.add(String.valueOf(action.getName()));
                        receivedBundleValues.add(action.getExtras().getString("key"));
                        receivedIconResIds.add(action.getIcon());
                      }
                      countDownLatch.countDown();
                    }
                  });
            });

    session.setPlaybackState(builder.build());

    assertThat(countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedActions).containsExactly("action1", "action2").inOrder();
    assertThat(receivedDisplayNames).containsExactly("actionName1", "actionName2").inOrder();
    assertThat(receivedIconResIds).containsExactly(1, 2).inOrder();
    assertThat(receivedBundleValues).containsExactly("value-1", "value-2").inOrder();
  }

  /**
   * Setting the session extras is used for instance by Wear OS and System UI (starting with T) to
   * receive extras for UI customization. An app needs a way to set the session extras that are
   * stored in the legacy session and broadcast to the connected controllers.
   */
  @Test
  public void setExtras_onExtrasChangedCalled() throws Exception {
    Bundle sessionExtras = new Bundle();
    sessionExtras.putString("key-1", "value-1");
    CountDownLatch countDownLatch = new CountDownLatch(1);
    MediaSessionCompat.Token sessionToken = session.getSessionToken();
    List<Bundle> receivedSessionExtras = new ArrayList<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              MediaControllerCompat mediaControllerCompat =
                  new MediaControllerCompat(context, sessionToken);
              mediaControllerCompat.registerCallback(
                  new MediaControllerCompat.Callback() {
                    @Override
                    public void onExtrasChanged(Bundle extras) {
                      receivedSessionExtras.add(extras);
                      receivedSessionExtras.add(mediaControllerCompat.getExtras());
                      countDownLatch.countDown();
                    }
                  });
            });

    session.setExtras(sessionExtras);

    assertThat(countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(TestUtils.equals(receivedSessionExtras.get(0), sessionExtras)).isTrue();
    assertThat(TestUtils.equals(receivedSessionExtras.get(1), sessionExtras)).isTrue();
  }
}
