/*
 * Copyright 2018 The Android Open Source Project
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

import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link MediaLibrarySession.Callback}.
 *
 * <p>TODO: Make this class extend MediaSessionCallbackTest. TODO: Create MediaLibrarySessionTest
 * which extends MediaSessionTest.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MediaLibrarySessionCallbackTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule
  public final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaLibrarySessionCallbackTest");

  @Rule public final RemoteControllerTestRule controllerTestRule = new RemoteControllerTestRule();

  @Rule public final MediaSessionTestRule sessionTestRule = new MediaSessionTestRule();

  private Context context;
  private MockPlayer player;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    player =
        new MockPlayer.Builder()
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .build();
  }

  @Test
  public void onSubscribe() throws Exception {
    String testParentId = "testSubscribeId";
    LibraryParams testParams = MediaTestUtils.createLibraryParams();

    CountDownLatch latch = new CountDownLatch(1);
    MediaLibrarySession.Callback sessionCallback =
        new MediaLibrarySession.Callback() {
          @Override
          public ListenableFuture<LibraryResult<Void>> onSubscribe(
              MediaLibrarySession session,
              ControllerInfo browser,
              String parentId,
              @Nullable LibraryParams params) {
            assertThat(parentId).isEqualTo(testParentId);
            MediaTestUtils.assertLibraryParamsEquals(testParams, params);
            latch.countDown();
            return Futures.immediateFuture(LibraryResult.ofVoid(params));
          }
        };

    MockMediaLibraryService service = new MockMediaLibraryService();
    service.attachBaseContext(context);

    MediaLibrarySession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaLibrarySession.Builder(service, player, sessionCallback)
                .setId("testOnSubscribe")
                .build());
    RemoteMediaBrowser browser = controllerTestRule.createRemoteBrowser(session.getToken());
    browser.subscribe(testParentId, testParams);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void onUnsubscribe() throws Exception {
    String testParentId = "testUnsubscribeId";

    CountDownLatch latch = new CountDownLatch(1);
    MediaLibrarySession.Callback sessionCallback =
        new MediaLibrarySession.Callback() {
          @Override
          public ListenableFuture<LibraryResult<Void>> onUnsubscribe(
              MediaLibrarySession session, ControllerInfo browser, String parentId) {
            assertThat(parentId).isEqualTo(testParentId);
            latch.countDown();
            return Futures.immediateFuture(LibraryResult.ofVoid());
          }
        };

    MockMediaLibraryService service = new MockMediaLibraryService();
    service.attachBaseContext(context);

    MediaLibrarySession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaLibrarySession.Builder(service, player, sessionCallback)
                .setId("testOnUnsubscribe")
                .build());
    RemoteMediaBrowser browser = controllerTestRule.createRemoteBrowser(session.getToken());
    browser.unsubscribe(testParentId);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }
}
