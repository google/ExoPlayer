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

import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.test.session.common.CommonConstants.DEFAULT_TEST_NAME;
import static androidx.media3.test.session.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.Looper;
import android.os.RemoteException;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.PollingCheck;
import androidx.media3.test.session.common.SurfaceActivity;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * A class that tests whether {@link Player.Listener#onSurfaceSizeChanged(int, int)} is called when
 * calling {@link MediaController#setVideoSurface(Surface)} and other surface related methods.
 */
@RunWith(Parameterized.class)
@LargeTest
public class MediaControllerSurfaceSizeChangeTest {

  private static final String SET_VIDEO_SURFACE = "setVideoSurface";
  private static final String SET_VIDEO_SURFACE_HOLDER = "setVideoSurfaceHolder";
  private static final String SET_VIDEO_SURFACE_VIEW = "setVideoSurfaceView";
  private static final String SET_VIDEO_TEXTURE_VIEW = "setVideoTextureView";
  private static final String CLEAR_VIDEO_SURFACE = "clearVideoSurface";

  // When this is used, sizeCondition should be SIZE_NOT_APPLICABLE.
  private static final String NO_PRECONDITION = "none";

  private static final String SIZE_SAME = "same";
  private static final String SIZE_DIFFERENT = "different";
  // Used when the size cannot be compared. For example, when the action is CLEAR_VIDEO_SURFACE,
  // or precondition is NO_PRECONDITION.
  private static final String SIZE_NOT_APPLICABLE = "notApplicable";

  @Parameterized.Parameters(
      name = "action:{0}, precondition:{1}, sizeCondition:{2}, callbackShouldBeCalled:{3}")
  public static List<Object[]> params() {
    return ImmutableList.of(
        // Major use cases
        new Object[] {SET_VIDEO_SURFACE, SET_VIDEO_SURFACE, SIZE_SAME, false},
        new Object[] {SET_VIDEO_SURFACE, SET_VIDEO_SURFACE, SIZE_DIFFERENT, false},
        new Object[] {SET_VIDEO_SURFACE, NO_PRECONDITION, SIZE_NOT_APPLICABLE, false},
        new Object[] {SET_VIDEO_SURFACE_HOLDER, SET_VIDEO_SURFACE_HOLDER, SIZE_SAME, false},
        new Object[] {SET_VIDEO_SURFACE_HOLDER, SET_VIDEO_SURFACE_HOLDER, SIZE_DIFFERENT, true},
        new Object[] {SET_VIDEO_SURFACE_HOLDER, NO_PRECONDITION, SIZE_NOT_APPLICABLE, true},
        new Object[] {SET_VIDEO_SURFACE_VIEW, SET_VIDEO_SURFACE_VIEW, SIZE_SAME, false},
        new Object[] {SET_VIDEO_SURFACE_VIEW, SET_VIDEO_SURFACE_VIEW, SIZE_DIFFERENT, true},
        new Object[] {SET_VIDEO_SURFACE_VIEW, NO_PRECONDITION, SIZE_NOT_APPLICABLE, true},
        new Object[] {SET_VIDEO_TEXTURE_VIEW, SET_VIDEO_TEXTURE_VIEW, SIZE_SAME, false},
        new Object[] {SET_VIDEO_TEXTURE_VIEW, SET_VIDEO_TEXTURE_VIEW, SIZE_DIFFERENT, true},
        new Object[] {SET_VIDEO_TEXTURE_VIEW, NO_PRECONDITION, SIZE_NOT_APPLICABLE, true},
        new Object[] {CLEAR_VIDEO_SURFACE, SET_VIDEO_SURFACE, SIZE_NOT_APPLICABLE, true},
        new Object[] {CLEAR_VIDEO_SURFACE, SET_VIDEO_SURFACE_HOLDER, SIZE_NOT_APPLICABLE, true},
        new Object[] {CLEAR_VIDEO_SURFACE, SET_VIDEO_SURFACE_VIEW, SIZE_NOT_APPLICABLE, true},
        new Object[] {CLEAR_VIDEO_SURFACE, SET_VIDEO_TEXTURE_VIEW, SIZE_NOT_APPLICABLE, true},

        // Minor use cases
        new Object[] {SET_VIDEO_SURFACE, SET_VIDEO_SURFACE_HOLDER, SIZE_SAME, true},
        new Object[] {SET_VIDEO_SURFACE, SET_VIDEO_SURFACE_HOLDER, SIZE_DIFFERENT, true},
        new Object[] {SET_VIDEO_SURFACE, SET_VIDEO_SURFACE_VIEW, SIZE_SAME, true},
        new Object[] {SET_VIDEO_SURFACE, SET_VIDEO_SURFACE_VIEW, SIZE_DIFFERENT, true},
        new Object[] {SET_VIDEO_SURFACE, SET_VIDEO_TEXTURE_VIEW, SIZE_SAME, true},
        new Object[] {SET_VIDEO_SURFACE, SET_VIDEO_TEXTURE_VIEW, SIZE_DIFFERENT, true},
        new Object[] {SET_VIDEO_SURFACE_HOLDER, SET_VIDEO_SURFACE, SIZE_SAME, true},
        new Object[] {SET_VIDEO_SURFACE_HOLDER, SET_VIDEO_SURFACE, SIZE_DIFFERENT, true},
        new Object[] {SET_VIDEO_SURFACE_HOLDER, SET_VIDEO_SURFACE_VIEW, SIZE_SAME, false},
        new Object[] {SET_VIDEO_SURFACE_HOLDER, SET_VIDEO_SURFACE_VIEW, SIZE_DIFFERENT, true},
        new Object[] {SET_VIDEO_SURFACE_HOLDER, SET_VIDEO_TEXTURE_VIEW, SIZE_SAME, false},
        new Object[] {SET_VIDEO_SURFACE_HOLDER, SET_VIDEO_TEXTURE_VIEW, SIZE_DIFFERENT, true},
        new Object[] {SET_VIDEO_SURFACE_VIEW, SET_VIDEO_SURFACE, SIZE_SAME, true},
        new Object[] {SET_VIDEO_SURFACE_VIEW, SET_VIDEO_SURFACE, SIZE_DIFFERENT, true},
        new Object[] {SET_VIDEO_SURFACE_VIEW, SET_VIDEO_SURFACE_HOLDER, SIZE_SAME, false},
        new Object[] {SET_VIDEO_SURFACE_VIEW, SET_VIDEO_SURFACE_HOLDER, SIZE_DIFFERENT, true},
        new Object[] {SET_VIDEO_SURFACE_VIEW, SET_VIDEO_TEXTURE_VIEW, SIZE_SAME, false},
        new Object[] {SET_VIDEO_SURFACE_VIEW, SET_VIDEO_TEXTURE_VIEW, SIZE_DIFFERENT, true},
        new Object[] {SET_VIDEO_TEXTURE_VIEW, SET_VIDEO_SURFACE, SIZE_SAME, true},
        new Object[] {SET_VIDEO_TEXTURE_VIEW, SET_VIDEO_SURFACE, SIZE_DIFFERENT, true},
        new Object[] {SET_VIDEO_TEXTURE_VIEW, SET_VIDEO_SURFACE_HOLDER, SIZE_SAME, false},
        new Object[] {SET_VIDEO_TEXTURE_VIEW, SET_VIDEO_SURFACE_HOLDER, SIZE_DIFFERENT, true},
        new Object[] {SET_VIDEO_TEXTURE_VIEW, SET_VIDEO_SURFACE_VIEW, SIZE_SAME, false},
        new Object[] {SET_VIDEO_TEXTURE_VIEW, SET_VIDEO_SURFACE_VIEW, SIZE_DIFFERENT, true},
        new Object[] {CLEAR_VIDEO_SURFACE, NO_PRECONDITION, SIZE_NOT_APPLICABLE, true});
  }

  @Rule public final MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private final String action;
  private final String precondition;
  private final String sizeCondition;
  private final boolean surfaceSizeChangedShouldBeCalled;
  private final CountDownLatch countDownLatch;
  private final AtomicInteger newSurfaceWidthRef;
  private final AtomicInteger newSurfaceHeightRef;
  private final AtomicReference<Player.Events> eventsRef;

  @Rule
  public ActivityTestRule<SurfaceActivity> activityRule =
      new ActivityTestRule<>(SurfaceActivity.class);

  private SurfaceActivity activity;
  private RemoteMediaSession remoteSession;

  @Nullable private View viewForPrecondition;
  @Nullable private View viewForAction;
  private int expectedWidthFromCallback;
  private int expectedHeightFromCallback;

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

  public MediaControllerSurfaceSizeChangeTest(
      String action,
      String precondition,
      String sizeCondition,
      boolean surfaceSizeChangedShouldBeCalled) {
    this.action = action;
    this.precondition = precondition;
    this.sizeCondition = sizeCondition;
    this.surfaceSizeChangedShouldBeCalled = surfaceSizeChangedShouldBeCalled;

    countDownLatch = new CountDownLatch(2);
    newSurfaceWidthRef = new AtomicInteger(C.LENGTH_UNSET);
    newSurfaceHeightRef = new AtomicInteger(C.LENGTH_UNSET);
    eventsRef = new AtomicReference<>();
  }

  @Test
  public void test() throws Throwable {
    MediaController controller =
        new MediaController.Builder(activity, remoteSession.getToken())
            .setApplicationLooper(Looper.getMainLooper())
            .buildAsync()
            .get();
    setPrecondition(controller);
    setViewForAction();
    matchSizeOfViewForAction();
    setExpectedWidthAndHeightFromCallback();

    doAction(controller);

    waitCallbackAndAssert();
  }

  private void setPrecondition(MediaController controller) throws Exception {
    switch (precondition) {
      case SET_VIDEO_SURFACE:
        {
          Surface testSurface = activity.getFirstSurfaceHolder().getSurface();
          MainLooperTestRule.runOnMainSync(() -> controller.setVideoSurface(testSurface));
          viewForPrecondition = activity.getFirstSurfaceView();
          break;
        }
      case SET_VIDEO_SURFACE_HOLDER:
        {
          SurfaceHolder testSurfaceHolder = activity.getFirstSurfaceHolder();
          MainLooperTestRule.runOnMainSync(
              () -> controller.setVideoSurfaceHolder(testSurfaceHolder));
          viewForPrecondition = activity.getFirstSurfaceView();
          break;
        }
      case SET_VIDEO_SURFACE_VIEW:
        {
          SurfaceView testSurfaceView = activity.getFirstSurfaceView();
          MainLooperTestRule.runOnMainSync(() -> controller.setVideoSurfaceView(testSurfaceView));
          viewForPrecondition = activity.getFirstSurfaceView();
          break;
        }
      case SET_VIDEO_TEXTURE_VIEW:
        {
          TextureView testTextureView = activity.getFirstTextureView();
          MainLooperTestRule.runOnMainSync(() -> controller.setVideoTextureView(testTextureView));
          viewForPrecondition = activity.getFirstTextureView();
          break;
        }
      case NO_PRECONDITION:
        // Do nothing
        break;
      default:
        throw new AssertionError(precondition + " is not an allowed precondition.");
    }
    MainLooperTestRule.runOnMainSync(
        () ->
            controller.addListener(
                new Player.Listener() {
                  @Override
                  public void onSurfaceSizeChanged(int width, int height) {
                    if (countDownLatch.getCount() == 0) {
                      return;
                    }
                    newSurfaceWidthRef.set(width);
                    newSurfaceHeightRef.set(height);
                    countDownLatch.countDown();
                  }

                  @Override
                  public void onEvents(Player player, Player.Events events) {
                    eventsRef.set(events);
                    countDownLatch.countDown();
                  }
                }));
  }

  private void setViewForAction() {
    switch (action) {
      case SET_VIDEO_SURFACE:
      case SET_VIDEO_SURFACE_HOLDER:
      case SET_VIDEO_SURFACE_VIEW:
        viewForAction = activity.getSecondSurfaceView();
        break;
      case SET_VIDEO_TEXTURE_VIEW:
        viewForAction = activity.getSecondTextureView();
        break;
      case CLEAR_VIDEO_SURFACE:
        // Do nothing
        break;
      default:
        throw new AssertionError(action + " is not an allowed action.");
    }
  }

  /** Resizes the view that will be used in action as same as the view used in precondition. */
  private void matchSizeOfViewForAction() throws Throwable {
    if (viewForPrecondition == null || viewForAction == null || !sizeCondition.equals(SIZE_SAME)) {
      return;
    }
    int targetWidth = viewForPrecondition.getWidth();
    int targetHeight = viewForPrecondition.getHeight();

    activityRule.runOnUiThread(
        () -> {
          LayoutParams layoutParams = viewForAction.getLayoutParams();
          layoutParams.width = targetWidth;
          layoutParams.height = targetHeight;
          viewForAction.setLayoutParams(layoutParams);
        });
    PollingCheck.waitFor(
        TIMEOUT_MS,
        () -> viewForAction.getWidth() == targetWidth && viewForAction.getHeight() == targetHeight);
  }

  private void setExpectedWidthAndHeightFromCallback() {
    if (!surfaceSizeChangedShouldBeCalled) {
      return;
    }

    if (action.equals(CLEAR_VIDEO_SURFACE)) {
      expectedWidthFromCallback = 0;
      expectedHeightFromCallback = 0;
    } else if (action.equals(SET_VIDEO_SURFACE)) {
      expectedWidthFromCallback = C.LENGTH_UNSET;
      expectedHeightFromCallback = C.LENGTH_UNSET;
    } else {
      checkStateNotNull(viewForAction);
      expectedWidthFromCallback = viewForAction.getWidth();
      expectedHeightFromCallback = viewForAction.getHeight();
    }
  }

  private void doAction(MediaController controller) throws Exception {
    switch (action) {
      case SET_VIDEO_SURFACE:
        {
          SurfaceView testSurfaceView = (SurfaceView) viewForAction;
          Surface surface = testSurfaceView.getHolder().getSurface();
          MainLooperTestRule.runOnMainSync(() -> controller.setVideoSurface(surface));
          break;
        }
      case SET_VIDEO_SURFACE_HOLDER:
        {
          SurfaceView testSurfaceView = (SurfaceView) viewForAction;
          SurfaceHolder testSurfaceHolder = testSurfaceView.getHolder();
          MainLooperTestRule.runOnMainSync(
              () -> controller.setVideoSurfaceHolder(testSurfaceHolder));
          break;
        }
      case SET_VIDEO_SURFACE_VIEW:
        {
          SurfaceView testSurfaceView = (SurfaceView) viewForAction;
          MainLooperTestRule.runOnMainSync(() -> controller.setVideoSurfaceView(testSurfaceView));
          break;
        }
      case SET_VIDEO_TEXTURE_VIEW:
        {
          TextureView testTextureView = (TextureView) viewForAction;
          MainLooperTestRule.runOnMainSync(() -> controller.setVideoTextureView(testTextureView));
          break;
        }
      case CLEAR_VIDEO_SURFACE:
        {
          MainLooperTestRule.runOnMainSync(controller::clearVideoSurface);
          break;
        }
      default:
        throw new AssertionError(action + " is not an allowed action.");
    }
  }

  private void waitCallbackAndAssert() throws Exception {
    if (surfaceSizeChangedShouldBeCalled) {
      assertThat(countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
      assertThat(newSurfaceWidthRef.get()).isEqualTo(expectedWidthFromCallback);
      assertThat(newSurfaceHeightRef.get()).isEqualTo(expectedHeightFromCallback);
      assertThat(TestUtils.getEventsAsList(eventsRef.get()))
          .containsExactly(Player.EVENT_SURFACE_SIZE_CHANGED);
    } else {
      assertThat(countDownLatch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
    }
  }
}
