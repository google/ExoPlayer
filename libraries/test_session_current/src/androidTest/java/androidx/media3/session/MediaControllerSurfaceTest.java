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
package androidx.media3.session;

import static androidx.media3.test.session.common.CommonConstants.DEFAULT_TEST_NAME;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;

import android.os.Looper;
import android.os.RemoteException;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import androidx.media3.test.session.common.PollingCheck;
import androidx.media3.test.session.common.SurfaceActivity;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaController#setVideoSurface(Surface)}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerSurfaceTest {
  private static final String TAG = "MC_SurfaceTest";

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
  public void setVideoSurface() throws Throwable {
    MediaController controller = createController();
    Surface testSurface = activity.getFirstSurfaceHolder().getSurface();

    activityRule.runOnUiThread(() -> controller.setVideoSurface(testSurface));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void setVideoSurface_withNull_clearsSurface() throws Throwable {
    MediaController controller = createController();
    Surface testSurface = activity.getFirstSurfaceHolder().getSurface();
    activityRule.runOnUiThread(() -> controller.setVideoSurface(testSurface));

    activityRule.runOnUiThread(() -> controller.setVideoSurface(null));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoSurface_withTheSameSurface() throws Throwable {
    MediaController controller = createController();
    Surface testSurface = activity.getFirstSurfaceHolder().getSurface();
    activityRule.runOnUiThread(() -> controller.setVideoSurface(testSurface));

    activityRule.runOnUiThread(() -> controller.clearVideoSurface(testSurface));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoSurface_withDifferentSurface_doesNothing() throws Throwable {
    MediaController controller = createController();
    Surface testSurface = activity.getFirstSurfaceHolder().getSurface();
    activityRule.runOnUiThread(() -> controller.setVideoSurface(testSurface));
    Surface anotherSurface = activity.getSecondSurfaceHolder().getSurface();

    activityRule.runOnUiThread(() -> controller.clearVideoSurface(anotherSurface));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoSurface_withNull_doesNothing() throws Throwable {
    MediaController controller = createController();
    Surface testSurface = activity.getFirstSurfaceHolder().getSurface();
    activityRule.runOnUiThread(() -> controller.setVideoSurface(testSurface));

    activityRule.runOnUiThread(() -> controller.clearVideoSurface(null));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void setVideoSurfaceHolder() throws Throwable {
    MediaController controller = createController();
    SurfaceHolder testSurfaceHolder = activity.getFirstSurfaceHolder();

    activityRule.runOnUiThread(
        () -> {
          controller.setVideoSurfaceHolder(testSurfaceHolder);
        });

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void setVideoSurfaceHolder_withNull_clearsSurfaceHolder() throws Throwable {
    MediaController controller = createController();
    SurfaceHolder testSurfaceHolder = activity.getFirstSurfaceHolder();
    activityRule.runOnUiThread(() -> controller.setVideoSurfaceHolder(testSurfaceHolder));

    activityRule.runOnUiThread(() -> controller.setVideoSurfaceHolder(null));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void setVideoSurfaceHolder_whenSurfaceIsDestroyed_surfaceIsClearedFromPlayer()
      throws Throwable {
    MediaController controller = createController();
    SurfaceHolder testSurfaceHolder = activity.getFirstSurfaceHolder();
    activityRule.runOnUiThread(() -> controller.setVideoSurfaceHolder(testSurfaceHolder));

    activityRule.runOnUiThread(
        () -> {
          SurfaceView firstSurfaceView = activity.getFirstSurfaceView();
          firstSurfaceView.setVisibility(View.GONE);
        });

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void setVideoSurfaceHolder_whenSurfaceIsCreated_surfaceIsSetToPlayer() throws Throwable {
    MediaController controller = createController();
    SurfaceHolder testSurfaceHolder = activity.getFirstSurfaceHolder();
    activityRule.runOnUiThread(() -> controller.setVideoSurfaceHolder(testSurfaceHolder));
    activityRule.runOnUiThread(
        () -> {
          SurfaceView firstSurfaceView = activity.getFirstSurfaceView();
          firstSurfaceView.setVisibility(View.GONE);
        });

    activityRule.runOnUiThread(
        () -> {
          SurfaceView firstSurfaceView = activity.getFirstSurfaceView();
          firstSurfaceView.setVisibility(View.VISIBLE);
        });

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoSurfaceHolder_withTheSameSurfaceHolder() throws Throwable {
    MediaController controller = createController();
    SurfaceHolder testSurfaceHolder = activity.getFirstSurfaceHolder();
    activityRule.runOnUiThread(() -> controller.setVideoSurfaceHolder(testSurfaceHolder));

    activityRule.runOnUiThread(() -> controller.clearVideoSurfaceHolder(testSurfaceHolder));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoSurfaceHolder_withDifferentSurfaceHolder_doesNothing() throws Throwable {
    MediaController controller = createController();
    SurfaceHolder testSurfaceHolder = activity.getFirstSurfaceHolder();
    activityRule.runOnUiThread(() -> controller.setVideoSurfaceHolder(testSurfaceHolder));
    SurfaceHolder anotherTestSurfaceHolder = activity.getSecondSurfaceHolder();

    activityRule.runOnUiThread(() -> controller.clearVideoSurfaceHolder(anotherTestSurfaceHolder));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoSurfaceHolder_withNull_doesNothing() throws Throwable {
    MediaController controller = createController();
    SurfaceHolder testSurfaceHolder = activity.getFirstSurfaceHolder();
    activityRule.runOnUiThread(() -> controller.setVideoSurfaceHolder(testSurfaceHolder));

    activityRule.runOnUiThread(() -> controller.clearVideoSurfaceHolder(null));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void setVideoSurfaceView() throws Throwable {
    MediaController controller = createController();
    SurfaceView testSurfaceView = activity.getFirstSurfaceView();

    activityRule.runOnUiThread(() -> controller.setVideoSurfaceView(testSurfaceView));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void setVideoSurfaceView_withNull_clearsSurfaceView() throws Throwable {
    MediaController controller = createController();
    SurfaceView testSurfaceView = activity.getFirstSurfaceView();
    activityRule.runOnUiThread(() -> controller.setVideoSurfaceView(testSurfaceView));

    activityRule.runOnUiThread(() -> controller.setVideoSurfaceView(null));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void setVideoSurfaceView_whenSurfaceIsDestroyed_surfaceIsClearedFromPlayer()
      throws Throwable {
    MediaController controller = createController();
    SurfaceView testSurfaceView = activity.getFirstSurfaceView();
    activityRule.runOnUiThread(() -> controller.setVideoSurfaceView(testSurfaceView));

    activityRule.runOnUiThread(
        () -> {
          testSurfaceView.setVisibility(View.GONE);
        });

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void setVideoSurfaceView_whenSurfaceIsCreated_surfaceIsSetToPlayer() throws Throwable {
    MediaController controller = createController();
    SurfaceView testSurfaceView = activity.getFirstSurfaceView();
    activityRule.runOnUiThread(() -> controller.setVideoSurfaceView(testSurfaceView));
    activityRule.runOnUiThread(
        () -> {
          testSurfaceView.setVisibility(View.GONE);
        });

    activityRule.runOnUiThread(
        () -> {
          testSurfaceView.setVisibility(View.VISIBLE);
        });

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoSurfaceView_withTheSameSurfaceView() throws Throwable {
    MediaController controller = createController();
    SurfaceView testSurfaceView = activity.getFirstSurfaceView();
    activityRule.runOnUiThread(() -> controller.setVideoSurfaceView(testSurfaceView));

    activityRule.runOnUiThread(() -> controller.clearVideoSurfaceView(testSurfaceView));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoSurfaceView_withDifferentSurfaceView_doesNothing() throws Throwable {
    MediaController controller = createController();
    SurfaceView testSurfaceView = activity.getFirstSurfaceView();
    activityRule.runOnUiThread(() -> controller.setVideoSurfaceView(testSurfaceView));
    SurfaceView anotherTestSurfaceView = activity.getSecondSurfaceView();

    activityRule.runOnUiThread(() -> controller.clearVideoSurfaceView(anotherTestSurfaceView));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoSurfaceView_withNull_doesNothing() throws Throwable {
    MediaController controller = createController();
    SurfaceView testSurfaceView = activity.getFirstSurfaceView();
    activityRule.runOnUiThread(() -> controller.setVideoSurfaceView(testSurfaceView));
    SurfaceView anotherTestSurfaceView = activity.getSecondSurfaceView();

    activityRule.runOnUiThread(() -> controller.clearVideoSurfaceView(null));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void setVideoTextureView() throws Throwable {
    MediaController controller = createController();
    TextureView testTextureView = activity.getFirstTextureView();

    activityRule.runOnUiThread(() -> controller.setVideoTextureView(testTextureView));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void setVideoTextureView_withNull_clearsTextureView() throws Throwable {
    MediaController controller = createController();
    TextureView testTextureView = activity.getFirstTextureView();
    activityRule.runOnUiThread(() -> controller.setVideoTextureView(testTextureView));

    activityRule.runOnUiThread(() -> controller.setVideoTextureView(null));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void setVideoTextureView_whenSurfaceTextureIsDestroyed_surfaceIsClearedFromPlayer()
      throws Throwable {
    MediaController controller = createController();
    TextureView testTextureView = activity.getFirstTextureView();
    activityRule.runOnUiThread(() -> controller.setVideoTextureView(testTextureView));

    activityRule.runOnUiThread(
        () -> {
          ViewGroup rootViewGroup = activity.getRootViewGroup();
          TextureView firstTextureView = activity.getFirstTextureView();
          rootViewGroup.removeView(firstTextureView);
        });

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void setVideoTextureView_whenSurfaceTextureIsAvailable_surfaceIsSetToPlayer()
      throws Throwable {
    MediaController controller = createController();
    TextureView testTextureView = activity.getFirstTextureView();
    activityRule.runOnUiThread(() -> controller.setVideoTextureView(testTextureView));
    activityRule.runOnUiThread(
        () -> {
          ViewGroup rootViewGroup = activity.getRootViewGroup();
          TextureView firstTextureView = activity.getFirstTextureView();
          rootViewGroup.removeView(firstTextureView);
        });

    activityRule.runOnUiThread(
        () -> {
          ViewGroup rootViewGroup = activity.getRootViewGroup();
          TextureView firstTextureView = activity.getFirstTextureView();
          rootViewGroup.addView(firstTextureView);
        });

    PollingCheck.waitFor(TIMEOUT_MS, () -> remoteSession.getMockPlayer().surfaceExists());
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoTextureView_withTheSameTextureView() throws Throwable {
    MediaController controller = createController();
    TextureView testTextureView = activity.getFirstTextureView();
    activityRule.runOnUiThread(() -> controller.setVideoTextureView(testTextureView));

    activityRule.runOnUiThread(() -> controller.clearVideoTextureView(testTextureView));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoTextureView_withDifferentTextureView_doesNothing() throws Throwable {
    MediaController controller = createController();
    TextureView testTextureView = activity.getFirstTextureView();
    activityRule.runOnUiThread(() -> controller.setVideoTextureView(testTextureView));
    TextureView anotherTestTextureView = activity.getSecondTextureView();

    activityRule.runOnUiThread(() -> controller.clearVideoTextureView(anotherTestTextureView));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoTextureView_withNull_doesNothing() throws Throwable {
    MediaController controller = createController();
    TextureView testTextureView = activity.getFirstTextureView();
    activityRule.runOnUiThread(() -> controller.setVideoTextureView(testTextureView));

    activityRule.runOnUiThread(() -> controller.clearVideoTextureView(null));

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isTrue();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoSurfaceWithNoArguments_afterSetVideoSurface() throws Throwable {
    MediaController controller = createController();
    Surface testSurface = activity.getFirstSurfaceHolder().getSurface();
    activityRule.runOnUiThread(() -> controller.setVideoSurface(testSurface));

    activityRule.runOnUiThread(() -> controller.clearVideoSurface());

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoSurfaceWithNoArguments_afterSetVideoSurfaceHolder() throws Throwable {
    MediaController controller = createController();
    SurfaceHolder testSurfaceHolder = activity.getFirstSurfaceHolder();
    activityRule.runOnUiThread(() -> controller.setVideoSurfaceHolder(testSurfaceHolder));

    activityRule.runOnUiThread(() -> controller.clearVideoSurface());

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoSurfaceWithNoArguments_afterSetVideoSurfaceView() throws Throwable {
    MediaController controller = createController();
    SurfaceView testSurfaceView = activity.getFirstSurfaceView();
    activityRule.runOnUiThread(() -> controller.setVideoSurfaceView(testSurfaceView));

    activityRule.runOnUiThread(() -> controller.clearVideoSurface());

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  @Test
  public void clearVideoSurfaceWithNoArguments_afterSetVideoTextureView() throws Throwable {
    MediaController controller = createController();
    TextureView testTextureView = activity.getFirstTextureView();
    activityRule.runOnUiThread(() -> controller.setVideoTextureView(testTextureView));

    activityRule.runOnUiThread(() -> controller.clearVideoSurface());

    assertThat(remoteSession.getMockPlayer().surfaceExists()).isFalse();
    activityRule.runOnUiThread(controller::release);
  }

  private MediaController createController() throws Exception {
    return new MediaController.Builder(activity, remoteSession.getToken())
        .setApplicationLooper(Looper.getMainLooper())
        .buildAsync()
        .get();
  }
}
