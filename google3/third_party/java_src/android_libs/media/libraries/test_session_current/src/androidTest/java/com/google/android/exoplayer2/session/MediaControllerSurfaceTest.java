/*
 * Copyright 2019 The Android Open Source Project
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

import static com.google.android.exoplayer2.session.vct.common.CommonConstants.DEFAULT_TEST_NAME;
import static com.google.common.truth.Truth.assertThat;

import android.os.RemoteException;
import android.view.Surface;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import com.google.android.exoplayer2.session.vct.common.HandlerThreadTestRule;
import com.google.android.exoplayer2.session.vct.common.SurfaceActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/** Tests for {@link MediaController#setVideoSurface(Surface)}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerSurfaceTest {
  private static final String TAG = "MC_SurfaceTest";

  private final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);
  private final MediaControllerTestRule controllerTestRule =
      new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  private SurfaceActivity activity;
  private RemoteMediaSession remoteSession;

  @Rule
  public ActivityTestRule<SurfaceActivity> activityRule =
      new ActivityTestRule<>(SurfaceActivity.class);

  @Before
  public void setUp() throws Exception {
    activity = activityRule.getActivity();

    remoteSession =
        new RemoteMediaSession(
            DEFAULT_TEST_NAME, ApplicationProvider.getApplicationContext(), null);
  }

  @After
  public void cleanUp() throws RemoteException {
    remoteSession.cleanUp();
  }

  @Test
  public void setVideoSurface() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    Surface testSurface = activity.getFirstSurfaceHolder().getSurface();

    threadTestRule.getHandler().postAndSync(() -> controller.setVideoSurface(testSurface));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
  }

  @Test
  public void setVideoSurface_withNull_clearsSurface() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    Surface testSurface = activity.getFirstSurfaceHolder().getSurface();
    threadTestRule.getHandler().postAndSync(() -> controller.setVideoSurface(testSurface));

    threadTestRule.getHandler().postAndSync(() -> controller.setVideoSurface(null));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
  }

  @Test
  public void clearVideoSurface() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    Surface testSurface = activity.getFirstSurfaceHolder().getSurface();
    threadTestRule.getHandler().postAndSync(() -> controller.setVideoSurface(testSurface));

    threadTestRule.getHandler().postAndSync(() -> controller.clearVideoSurface());

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
  }

  @Test
  public void clearVideoSurface_withTheSameSurface() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    Surface testSurface = activity.getFirstSurfaceHolder().getSurface();
    threadTestRule.getHandler().postAndSync(() -> controller.setVideoSurface(testSurface));

    threadTestRule.getHandler().postAndSync(() -> controller.clearVideoSurface(testSurface));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
  }

  @Test
  public void clearVideoSurface_withDifferentSurface_doesNothing() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    Surface testSurface = activity.getFirstSurfaceHolder().getSurface();
    threadTestRule.getHandler().postAndSync(() -> controller.setVideoSurface(testSurface));
    Surface anotherSurface = activity.getSecondSurfaceHolder().getSurface();

    threadTestRule.getHandler().postAndSync(() -> controller.clearVideoSurface(anotherSurface));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
  }

  @Test
  public void clearVideoSurface_withNull_doesNothing() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    Surface testSurface = activity.getFirstSurfaceHolder().getSurface();
    threadTestRule.getHandler().postAndSync(() -> controller.setVideoSurface(testSurface));

    threadTestRule.getHandler().postAndSync(() -> controller.clearVideoSurface(null));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
  }
}
