/*
 * Copyright 2020 The Android Open Source Project
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
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.MOCK_MEDIA_BROWSER_SERVICE_COMPAT;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserServiceCompatConstants.TEST_CONNECT_REJECTED;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserServiceCompatConstants.TEST_ON_CHILDREN_CHANGED_SUBSCRIBE_AND_UNSUBSCRIBE;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.android.exoplayer2.session.MediaBrowser.BrowserCallback;
import com.google.android.exoplayer2.session.MediaLibraryService.LibraryParams;
import com.google.android.exoplayer2.session.vct.common.HandlerThreadTestRule;
import java.util.concurrent.CountDownLatch;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/** Tests for {@link BrowserCallback} with {@link MediaBrowserServiceCompat}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaBrowserCallbackWithMediaBrowserServiceCompatTest {

  private final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaBrowserCallbackTestWithMediaBrowserServiceCompat");
  private final MediaControllerTestRule controllerTestRule =
      new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  private Context context;
  private RemoteMediaBrowserServiceCompat remoteService;

  private MediaBrowser createBrowser(boolean waitForConnect, @Nullable BrowserCallback callback)
      throws Exception {
    SessionToken token = new SessionToken(context, MOCK_MEDIA_BROWSER_SERVICE_COMPAT);
    return (MediaBrowser)
        controllerTestRule.createController(
            token, waitForConnect, /* connectionHints= */ null, callback);
  }

  @Before
  public void setUp() {
    controllerTestRule.setControllerType(MediaBrowser.class);
    context = ApplicationProvider.getApplicationContext();
    remoteService = new RemoteMediaBrowserServiceCompat(context);
  }

  @After
  public void cleanUp() throws Exception {
    remoteService.release();
  }

  @Test
  public void connect() throws Exception {
    createBrowser(/* waitForConnect= */ true, new BrowserCallback() {});
    // If connection failed, exception will be thrown inside of #createBrowser().
  }

  @Test
  public void connect_rejected() throws Exception {
    remoteService.setProxyForTest(TEST_CONNECT_REJECTED);

    CountDownLatch latch = new CountDownLatch(1);
    createBrowser(
        /* waitForConnect= */ false,
        new BrowserCallback() {
          @Override
          public void onConnected(MediaController controller) {
            assertWithMessage("shouldn't allow connection").fail();
          }

          @Override
          public void onDisconnected(@NonNull MediaController controller) {
            latch.countDown();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void onChildrenChanged_subscribeAndUnsubscribe() throws Exception {
    String testParentId = "testOnChildrenChanged";
    CountDownLatch latch = new CountDownLatch(2);
    BrowserCallback browserCallback =
        new BrowserCallback() {
          @Override
          public void onChildrenChanged(
              @NonNull MediaBrowser browser,
              @NonNull String parentId,
              int itemCount,
              @Nullable LibraryParams params) {
            // Triggered by both subscribe and notifyChildrenChanged().
            // Shouldn't be called after the unsubscribe().
            assertThat(latch.getCount()).isNotEqualTo(0);
            assertThat(parentId).isEqualTo(testParentId);
            assertThat(params).isNull();
            latch.countDown();
          }
        };

    remoteService.setProxyForTest(TEST_ON_CHILDREN_CHANGED_SUBSCRIBE_AND_UNSUBSCRIBE);
    MediaBrowser browser = createBrowser(/* waitForConnect= */ true, browserCallback);

    LibraryResult resultForSubscribe =
        browser.subscribe(testParentId, null).get(TIMEOUT_MS, MILLISECONDS);
    assertThat(resultForSubscribe.resultCode).isEqualTo(RESULT_SUCCESS);
    remoteService.notifyChildrenChanged(testParentId);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    LibraryResult resultForUnsubscribe =
        browser.unsubscribe(testParentId).get(TIMEOUT_MS, MILLISECONDS);
    assertThat(resultForUnsubscribe.resultCode).isEqualTo(RESULT_SUCCESS);
    // Unsubscribe takes some time. Wait for some time.
    Thread.sleep(TIMEOUT_MS);
    remoteService.notifyChildrenChanged(testParentId);
    // This shouldn't trigger browser's onChildrenChanged().
    // Wait for some time. Exception will be thrown in the callback if error happens.
    Thread.sleep(TIMEOUT_MS);
  }
}
