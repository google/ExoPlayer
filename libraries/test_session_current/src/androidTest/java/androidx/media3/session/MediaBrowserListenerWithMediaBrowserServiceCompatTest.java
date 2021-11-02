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
package androidx.media3.session;

import static androidx.media3.session.LibraryResult.RESULT_SUCCESS;
import static androidx.media3.test.session.common.CommonConstants.MOCK_MEDIA_BROWSER_SERVICE_COMPAT;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_CONNECT_REJECTED;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_ON_CHILDREN_CHANGED_SUBSCRIBE_AND_UNSUBSCRIBE;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/** Tests for {@link MediaBrowser.Listener} with {@link MediaBrowserServiceCompat}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaBrowserListenerWithMediaBrowserServiceCompatTest {

  private final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaBrowserListenerTestWithMediaBrowserServiceCompat");
  private final MediaControllerTestRule controllerTestRule =
      new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  private Context context;
  private RemoteMediaBrowserServiceCompat remoteService;

  private MediaBrowser createBrowser(@Nullable MediaBrowser.Listener listener) throws Exception {
    SessionToken token = new SessionToken(context, MOCK_MEDIA_BROWSER_SERVICE_COMPAT);
    return (MediaBrowser)
        controllerTestRule.createController(token, /* connectionHints= */ null, listener);
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
    createBrowser(/* listener= */ null);
    // If connection failed, exception will be thrown inside of #createBrowser().
  }

  @Test
  public void connect_rejected() throws Exception {
    remoteService.setProxyForTest(TEST_CONNECT_REJECTED);

    ExecutionException thrown =
        assertThrows(ExecutionException.class, () -> createBrowser(/* listener= */ null));
    assertThat(thrown).hasCauseThat().isInstanceOf(SecurityException.class);
  }

  @Test
  public void onChildrenChanged_subscribeAndUnsubscribe() throws Exception {
    String testParentId = "testOnChildrenChanged";
    CountDownLatch latch = new CountDownLatch(2);
    MediaBrowser.Listener listener =
        new MediaBrowser.Listener() {
          @Override
          public void onChildrenChanged(
              MediaBrowser browser,
              String parentId,
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
    MediaBrowser browser = createBrowser(listener);

    LibraryResult<Void> resultForSubscribe =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.subscribe(testParentId, null))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(resultForSubscribe.resultCode).isEqualTo(RESULT_SUCCESS);
    remoteService.notifyChildrenChanged(testParentId);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    LibraryResult<Void> resultForUnsubscribe =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.unsubscribe(testParentId))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(resultForUnsubscribe.resultCode).isEqualTo(RESULT_SUCCESS);
    // Unsubscribe takes some time. Wait for some time.
    Thread.sleep(TIMEOUT_MS);
    remoteService.notifyChildrenChanged(testParentId);
    // This shouldn't trigger browser's onChildrenChanged().
    // Wait for some time. Exception will be thrown in the listener if error happens.
    Thread.sleep(TIMEOUT_MS);
  }
}
