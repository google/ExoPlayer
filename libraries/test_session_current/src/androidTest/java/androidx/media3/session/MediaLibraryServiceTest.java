/*
 * Copyright 2023 The Android Open Source Project
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

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaLibraryService}. */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MediaLibraryServiceTest {

  @Rule public final RemoteControllerTestRule controllerTestRule = new RemoteControllerTestRule();

  private Context context;
  private SessionToken token;

  @Before
  public void setUp() {
    TestServiceRegistry.getInstance().cleanUp();
    context = ApplicationProvider.getApplicationContext();
    token =
        new SessionToken(context, new ComponentName(context, LocalMockMediaLibraryService.class));
  }

  @After
  public void cleanUp() {
    TestServiceRegistry.getInstance().cleanUp();
  }

  @Test
  public void onConnect_controllerInfo_sameInstanceInOnGetSessionAndCallback() throws Exception {
    TestServiceRegistry testServiceRegistry = TestServiceRegistry.getInstance();
    List<MediaSession.ControllerInfo> onGetSessionControllerInfos = new ArrayList<>();
    List<MediaSession.ControllerInfo> browserCommandControllerInfos = new ArrayList<>();
    AtomicReference<MediaSession> session = new AtomicReference<>();
    testServiceRegistry.setOnGetSessionHandler(
        controllerInfo -> {
          MockMediaLibraryService service =
              (MockMediaLibraryService) testServiceRegistry.getServiceInstance();
          // The controllerInfo passed to the onGetSession of the service.
          onGetSessionControllerInfos.add(controllerInfo);
          Player player = new ExoPlayer.Builder(context).build();
          MediaLibrarySession.Callback callback =
              new MediaLibrarySession.Callback() {
                @Override
                public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
                    MediaLibrarySession session,
                    MediaSession.ControllerInfo browser,
                    String mediaId) {
                  browserCommandControllerInfos.add(browser);
                  return Futures.immediateFuture(
                      LibraryResult.ofItem(
                          new MediaItem.Builder()
                              .setMediaId("media-id-321")
                              .setMediaMetadata(
                                  new MediaMetadata.Builder()
                                      .setIsPlayable(false)
                                      .setIsBrowsable(true)
                                      .build())
                              .build(),
                          /* params= */ null));
                }
              };
          session.set(new MediaLibrarySession.Builder(service, player, callback).build());
          return session.get();
        });
    // Create the remote browser to start the service.
    RemoteMediaBrowser browser =
        controllerTestRule.createRemoteBrowser(token, /* connectionHints= */ Bundle.EMPTY);
    // Get the started service instance after creation.
    MockMediaLibraryService service =
        (MockMediaLibraryService) testServiceRegistry.getServiceInstance();

    assertThat(browser.getItem("mediaId").resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
    browser.release();

    service.blockUntilAllControllersUnbind(TIMEOUT_MS);
    assertThat(onGetSessionControllerInfos).hasSize(1);
    assertThat(browserCommandControllerInfos).hasSize(1);
    assertThat(onGetSessionControllerInfos.get(0))
        .isSameInstanceAs(browserCommandControllerInfos.get(0));
  }
}
