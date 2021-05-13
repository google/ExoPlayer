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

package com.google.android.exoplayer2.session;

import static com.google.android.exoplayer2.session.LibraryResult.RESULT_SUCCESS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import com.google.android.exoplayer2.session.MediaLibraryService.LibraryParams;
import com.google.android.exoplayer2.session.MediaLibraryService.MediaLibrarySession;
import com.google.android.exoplayer2.session.vct.common.HandlerThreadTestRule;
import com.google.android.exoplayer2.session.vct.common.MainLooperTestRule;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link MediaLibrarySession.MediaLibrarySessionCallback}.
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
    MediaLibrarySession.MediaLibrarySessionCallback sessionCallback =
        new MediaLibrarySession.MediaLibrarySessionCallback() {
          @Override
          @NonNull
          public ListenableFuture<LibraryResult> onSubscribe(
              @NonNull MediaLibrarySession session,
              @NonNull MediaSession.ControllerInfo browser,
              @NonNull String parentId,
              LibraryParams params) {
            assertThat(parentId).isEqualTo(testParentId);
            MediaTestUtils.assertLibraryParamsEquals(testParams, params);
            latch.countDown();
            return new LibraryResult(RESULT_SUCCESS).asFuture();
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
    MediaLibrarySession.MediaLibrarySessionCallback sessionCallback =
        new MediaLibrarySession.MediaLibrarySessionCallback() {
          @Override
          @NonNull
          public ListenableFuture<LibraryResult> onUnsubscribe(
              @NonNull MediaLibrarySession session,
              @NonNull MediaSession.ControllerInfo browser,
              @NonNull String parentId) {
            assertThat(parentId).isEqualTo(testParentId);
            latch.countDown();
            return new LibraryResult(RESULT_SUCCESS).asFuture();
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
