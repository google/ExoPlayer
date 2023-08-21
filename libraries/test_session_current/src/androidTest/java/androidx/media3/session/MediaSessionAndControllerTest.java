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

import static androidx.media3.common.Player.STATE_IDLE;
import static androidx.media3.test.session.common.TestUtils.LONG_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/** Tests for {@link MediaSession} and {@link MediaController} in the same process. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaSessionAndControllerTest {

  private static final String TAG = "MSessionControllerTest";

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  public final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);

  public final MediaControllerTestRule controllerTestRule =
      new MediaControllerTestRule(threadTestRule);

  @Rule public final MediaSessionTestRule sessionTestRule = new MediaSessionTestRule();

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  private Context context;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
  }

  /**
   * Tests potential deadlock for calls between controller and session when they send commands each
   * other.
   */
  @Test
  public void deadlock() throws Exception {
    MockPlayer player =
        new MockPlayer.Builder().setApplicationLooper(Looper.getMainLooper()).build();
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setId(TAG).build());
    MediaController controller = controllerTestRule.createController(session.getToken());

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              int state = STATE_IDLE;
              for (int i = 0; i < 100; i++) {
                // triggers call from session to controller.
                MainLooperTestRule.runOnMainSync(() -> player.notifyPlaybackStateChanged(state));
                // triggers call from controller to session.
                controller.play();

                controller.pause();
                MainLooperTestRule.runOnMainSync(() -> player.notifyPlaybackStateChanged(state));
                controller.seekTo(0);
                MainLooperTestRule.runOnMainSync(() -> player.notifyPlaybackStateChanged(state));
                controller.seekToNextMediaItem();
                MainLooperTestRule.runOnMainSync(() -> player.notifyPlaybackStateChanged(state));
                controller.seekToPreviousMediaItem();
              }
            });
  }

  /** Tests to ensure that disconnection is notified. */
  @Test
  public void connecting_whileSessionIsReleasing_notified() throws Exception {
    MockPlayer player =
        new MockPlayer.Builder()
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .build();
    Handler mainHandler = new Handler(Looper.getMainLooper());
    for (int i = 0; i < 100; i++) {
      MediaSession session =
          sessionTestRule.ensureReleaseAfterTest(
              new MediaSession.Builder(context, player).setId(TAG).build());
      CountDownLatch latch = new CountDownLatch(1);
      // Keep the instance in AtomicReference to prevent GC.
      // Otherwise ListenableFuture and corresponding controllers can be GCed and disconnected
      // callback can be ignored.
      AtomicReference<ListenableFuture<MediaController>> controllerFutureRef =
          new AtomicReference<>();
      mainHandler.post(
          () -> {
            ListenableFuture<MediaController> controllerFuture =
                new MediaController.Builder(context, session.getToken())
                    .setListener(
                        new MediaController.Listener() {
                          @Override
                          public void onDisconnected(MediaController controller) {
                            latch.countDown();
                          }
                        })
                    .buildAsync();
            controllerFutureRef.set(controllerFuture);
            controllerFuture.addListener(
                () -> {
                  try {
                    MediaController controller =
                        controllerFuture.get(/* timeout=* */ 0, MILLISECONDS);
                    assertThat(controller).isNotNull();
                  } catch (ExecutionException
                      | InterruptedException
                      | CancellationException
                      | TimeoutException e) {
                    latch.countDown();
                  }
                },
                mainHandler::post);
          });
      threadTestRule.getHandler().postAndSync(session::release);
      assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    }
  }

  @Test
  public void connect_withTheSameLooper_connectsInTheSameLooperIteration() throws Exception {
    // This may hang if controller cannot be connected immediately in a looper iteration.
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              MockPlayer player = new MockPlayer.Builder().build();
              MediaSession session =
                  sessionTestRule.ensureReleaseAfterTest(
                      new MediaSession.Builder(context, player).setId(TAG).build());
              controllerTestRule.createController(session.getToken());
            },
            LONG_TIMEOUT_MS);
  }

  @Test
  public void play_withTheSameLooper_sendsToSession() throws Exception {
    MockPlayer player =
        new MockPlayer.Builder()
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .build();
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setId(TAG).build());
    MediaController controller = controllerTestRule.createController(session.getToken());

    threadTestRule.getHandler().postAndSync(controller::play);

    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
  }

  @Test
  public void playWhenReadyChanged_withTheSameLooper_sendsToController() throws Exception {
    boolean testPlayWhenReady = true;
    MockPlayer player =
        new MockPlayer.Builder()
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .build();
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setId(TAG).build());
    MediaController controller = controllerTestRule.createController(session.getToken());

    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean playWhenReadyRef = new AtomicBoolean();
    controller.addListener(
        new Player.Listener() {
          @Override
          public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            playWhenReadyRef.set(playWhenReady);
            latch.countDown();
          }
        });

    threadTestRule
        .getHandler()
        .postAndSync(
            () ->
                player.notifyPlayWhenReadyChanged(
                    testPlayWhenReady, Player.PLAYBACK_SUPPRESSION_REASON_NONE));

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playWhenReadyRef.get()).isEqualTo(testPlayWhenReady);
  }

  @Test
  public void commandBeforeControllerRelease_handledBySession() throws Exception {
    MockPlayer player =
        new MockPlayer.Builder().setApplicationLooper(Looper.getMainLooper()).build();
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setId(TAG).build());
    MediaController controller = controllerTestRule.createController(session.getToken());

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.prepare();
              controller.play();
              controller.release();
            });

    // Assert these methods are called without timing out.
    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
  }

  @Test
  public void setTrackSelectionParameters_withOverrides_matchesExpectedTrackGroupInPlayer()
      throws Exception {
    MockPlayer player =
        new MockPlayer.Builder().setApplicationLooper(Looper.getMainLooper()).build();
    // Intentionally add metadata to the format as this can't be bundled.
    Tracks.Group trackGroupInPlayer =
        new Tracks.Group(
            new TrackGroup(
                new Format.Builder()
                    .setId("0")
                    .setSampleMimeType(MimeTypes.VIDEO_H264)
                    .setMetadata(new Metadata())
                    .build(),
                new Format.Builder()
                    .setId("1")
                    .setSampleMimeType(MimeTypes.VIDEO_H264)
                    .setMetadata(new Metadata())
                    .build()),
            /* adaptiveSupported= */ false,
            /* trackSupport= */ new int[] {C.FORMAT_HANDLED, C.FORMAT_HANDLED},
            /* trackSelected= */ new boolean[] {true, false});
    player.currentTracks = new Tracks(ImmutableList.of(trackGroupInPlayer));
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setId(TAG).build());
    MediaController controller = controllerTestRule.createController(session.getToken());

    threadTestRule
        .getHandler()
        .postAndSync(
            () ->
                controller.setTrackSelectionParameters(
                    controller
                        .getTrackSelectionParameters()
                        .buildUpon()
                        .setOverrideForType(
                            new TrackSelectionOverride(
                                controller
                                    .getCurrentTracks()
                                    .getGroups()
                                    .get(0)
                                    .getMediaTrackGroup(),
                                /* trackIndex= */ 1))
                        .build()));
    player.awaitMethodCalled(MockPlayer.METHOD_SET_TRACK_SELECTION_PARAMETERS, TIMEOUT_MS);

    assertThat(player.trackSelectionParameters.overrides)
        .containsExactly(
            trackGroupInPlayer.getMediaTrackGroup(),
            new TrackSelectionOverride(
                trackGroupInPlayer.getMediaTrackGroup(), /* trackIndex= */ 1));
  }
}
