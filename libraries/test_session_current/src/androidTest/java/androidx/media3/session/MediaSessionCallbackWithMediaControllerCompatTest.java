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

import static androidx.media.MediaSessionManager.RemoteUserInfo.LEGACY_CONTROLLER;
import static androidx.media3.common.Player.COMMAND_PLAY_PAUSE;
import static androidx.media3.common.Player.COMMAND_PREPARE;
import static androidx.media3.common.Player.STATE_ENDED;
import static androidx.media3.common.Player.STATE_IDLE;
import static androidx.media3.common.Player.STATE_READY;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.session.SessionResult.RESULT_ERROR_INVALID_STATE;
import static androidx.media3.session.SessionResult.RESULT_SUCCESS;
import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static androidx.media3.test.session.common.TestUtils.LONG_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.VOLUME_CHANGE_TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioManagerCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Rating;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.PollingCheck;
import androidx.media3.test.session.common.TestHandler;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaSession.Callback} working with {@link MediaControllerCompat}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaSessionCallbackWithMediaControllerCompatTest {

  private static final String TAG = "MSCallbackWithMCCTest";

  private static final String TEST_URI = "http://test.test";
  private static final String EXPECTED_CONTROLLER_PACKAGE_NAME =
      (Util.SDK_INT < 21 || Util.SDK_INT >= 24) ? SUPPORT_APP_PACKAGE_NAME : LEGACY_CONTROLLER;

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule public final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);

  @Rule public final MediaSessionTestRule mediaSessionTestRule = new MediaSessionTestRule();

  private Context context;
  private TestHandler handler;
  private MediaSession session;
  private RemoteMediaControllerCompat controller;
  private MockPlayer player;
  private AudioManager audioManager;
  private ListeningExecutorService executorService;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    handler = threadTestRule.getHandler();
    player = new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build();
    audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    // Intentionally use an Executor with another thread to test asynchronous workflows involving
    // background tasks.
    executorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
  }

  @After
  public void cleanUp() {
    if (session != null) {
      session.release();
      session = null;
    }
    if (controller != null) {
      controller.cleanUp();
      controller = null;
    }
    executorService.shutdownNow();
  }

  @Test
  public void onDisconnected_afterTimeout_isCalled() throws Exception {
    CountDownLatch disconnectedLatch = new CountDownLatch(1);
    session =
        new MediaSession.Builder(context, player)
            .setId("onDisconnected_afterTimeout_isCalled")
            .setCallback(
                new MediaSession.Callback() {
                  private ControllerInfo connectedController;

                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession session, ControllerInfo controller) {
                    if (EXPECTED_CONTROLLER_PACKAGE_NAME.equals(controller.getPackageName())) {
                      connectedController = controller;
                      return MediaSession.Callback.super.onConnect(session, controller);
                    }
                    return MediaSession.ConnectionResult.reject();
                  }

                  @Override
                  public void onDisconnected(MediaSession session, ControllerInfo controller) {
                    if (Util.areEqual(connectedController, controller)) {
                      disconnectedLatch.countDown();
                    }
                  }
                })
            .build();
    // Make onDisconnected() to be called immediately after the connection.
    session.setLegacyControllerConnectionTimeoutMs(0);

    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    // Invoke any command for session to recognize the controller compat.
    controller.getTransportControls().seekTo(111);

    assertThat(disconnectedLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void onConnected_afterDisconnectedByTimeout_isCalled() throws Exception {
    CountDownLatch connectedLatch = new CountDownLatch(2);
    CountDownLatch disconnectedLatch = new CountDownLatch(1);
    session =
        new MediaSession.Builder(context, player)
            .setId("onConnected_afterDisconnectedByTimeout_isCalled")
            .setCallback(
                new MediaSession.Callback() {
                  private ControllerInfo connectedController;

                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession session, ControllerInfo controller) {
                    if (EXPECTED_CONTROLLER_PACKAGE_NAME.equals(controller.getPackageName())) {
                      connectedController = controller;
                      connectedLatch.countDown();
                      return MediaSession.Callback.super.onConnect(session, controller);
                    }
                    return MediaSession.ConnectionResult.reject();
                  }

                  @Override
                  public void onDisconnected(MediaSession session, ControllerInfo controller) {
                    if (Util.areEqual(connectedController, controller)) {
                      disconnectedLatch.countDown();
                    }
                  }
                })
            .build();
    // Make onDisconnected() to be called immediately after the connection.
    session.setLegacyControllerConnectionTimeoutMs(0);
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    // Invoke any command for session to recognize the controller compat.
    controller.getTransportControls().seekTo(111);
    assertThat(disconnectedLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    // Test whenter onConnect() is called again after the onDisconnected().
    controller.getTransportControls().seekTo(111);

    assertThat(connectedLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void play_whileReady_callsPlay() throws Exception {
    player.playbackState = STATE_READY;
    session =
        new MediaSession.Builder(context, player)
            .setId("play")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().play();
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isFalse();
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SEEK_TO_DEFAULT_POSITION)).isFalse();
  }

  @Test
  public void play_whileIdle_callsPrepareAndPlay() throws Exception {
    player.playbackState = STATE_IDLE;
    session =
        new MediaSession.Builder(context, player)
            .setId("play")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().play();
    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SEEK_TO_DEFAULT_POSITION)).isFalse();
  }

  @Test
  public void play_whileIdleWithoutPrepareCommandAvailable_callsJustPlay() throws Exception {
    player.playbackState = STATE_IDLE;
    player.commands =
        new Player.Commands.Builder().addAllCommands().remove(Player.COMMAND_PREPARE).build();
    session =
        new MediaSession.Builder(context, player)
            .setId("play")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().play();
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isFalse();
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SEEK_TO_DEFAULT_POSITION)).isFalse();
  }

  @Test
  public void play_whileEnded_callsSeekToDefaultPositionAndPlay() throws Exception {
    player.playbackState = STATE_ENDED;
    session =
        new MediaSession.Builder(context, player)
            .setId("play")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().play();
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_DEFAULT_POSITION, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isFalse();
  }

  @Test
  public void play_whileEndedWithoutSeekToDefaultPositionCommandAvailable_callsJustPlay()
      throws Exception {
    player.playbackState = STATE_ENDED;
    player.commands =
        new Player.Commands.Builder()
            .addAllCommands()
            .remove(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
            .build();
    session =
        new MediaSession.Builder(context, player)
            .setId("play")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().play();
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SEEK_TO_DEFAULT_POSITION)).isFalse();
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isFalse();
  }

  @Test
  public void pause() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("pause")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().pause();
    player.awaitMethodCalled(MockPlayer.METHOD_PAUSE, TIMEOUT_MS);
  }

  @Test
  public void stop() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("stop")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().stop();
    player.awaitMethodCalled(MockPlayer.METHOD_STOP, TIMEOUT_MS);
  }

  @Test
  public void prepare() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("prepare")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().prepare();
    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
  }

  @Test
  public void seekTo() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("seekTo")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    long seekPosition = 12125L;

    controller.getTransportControls().seekTo(seekPosition);
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO, TIMEOUT_MS);

    assertThat(player.seekPositionMs).isEqualTo(seekPosition);
  }

  @Test
  public void setPlaybackSpeed_callsSetPlaybackSpeed() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("setPlaybackSpeed")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    float testSpeed = 2.0f;

    controller.getTransportControls().setPlaybackSpeed(testSpeed);
    player.awaitMethodCalled(MockPlayer.METHOD_SET_PLAYBACK_SPEED, TIMEOUT_MS);

    assertThat(player.playbackParameters.speed).isEqualTo(testSpeed);
  }

  @Test
  public void setPlaybackSpeed_withInvalidSpeed_doesNotCrashSession() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("setPlaybackSpeed")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().setPlaybackSpeed(-0.0001f);
    controller.getTransportControls().setPlaybackSpeed(Float.NaN);
    controller.getTransportControls().setPlaybackSpeed(0.5f); // Add a valid action to wait for.
    player.awaitMethodCalled(MockPlayer.METHOD_SET_PLAYBACK_SPEED, TIMEOUT_MS);

    assertThat(player.playbackParameters.speed).isEqualTo(0.5f);
  }

  @Test
  public void addQueueItem() throws Exception {
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaItem resolvedMediaItem = MediaItem.fromUri(TEST_URI);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItem asynchronously to test correct threading logic.
            return executorService.submit(() -> ImmutableList.of(resolvedMediaItem));
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setId("addQueueItem")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    handler.postAndSync(
        () -> {
          List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 10);
          player.setMediaItems(mediaItems);
          player.timeline = MediaTestUtils.createTimeline(mediaItems);
          player.notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
        });
    // Prepare an item to add.
    String mediaId = "newMediaItemId";
    Uri mediaUri = Uri.parse("https://test.test");
    MediaDescriptionCompat desc =
        new MediaDescriptionCompat.Builder().setMediaId(mediaId).setMediaUri(mediaUri).build();

    controller.addQueueItem(desc);
    player.awaitMethodCalled(MockPlayer.METHOD_ADD_MEDIA_ITEMS, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).hasSize(1);
    assertThat(requestedMediaItems.get().get(0).mediaId).isEqualTo(mediaId);
    assertThat(requestedMediaItems.get().get(0).requestMetadata.mediaUri).isEqualTo(mediaUri);
    assertThat(player.mediaItems).hasSize(11);
    assertThat(player.mediaItems.get(10)).isEqualTo(resolvedMediaItem);
  }

  @Test
  public void addQueueItemWithIndex() throws Exception {
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaItem resolvedMediaItem = MediaItem.fromUri(TEST_URI);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItem asynchronously to test correct threading logic.
            return executorService.submit(() -> ImmutableList.of(resolvedMediaItem));
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setId("addQueueItemWithIndex")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    handler.postAndSync(
        () -> {
          List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 10);
          player.setMediaItems(mediaItems);
          player.timeline = MediaTestUtils.createTimeline(mediaItems);
          player.notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
        });
    // Prepare an item to add.
    int testIndex = 1;
    String mediaId = "media_id";
    Uri mediaUri = Uri.parse("https://test.test");
    MediaDescriptionCompat desc =
        new MediaDescriptionCompat.Builder().setMediaId(mediaId).setMediaUri(mediaUri).build();

    controller.addQueueItem(desc, testIndex);
    player.awaitMethodCalled(MockPlayer.METHOD_ADD_MEDIA_ITEMS_WITH_INDEX, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).hasSize(1);
    assertThat(requestedMediaItems.get().get(0).mediaId).isEqualTo(mediaId);
    assertThat(requestedMediaItems.get().get(0).requestMetadata.mediaUri).isEqualTo(mediaUri);
    assertThat(player.index).isEqualTo(testIndex);
    assertThat(player.mediaItems).hasSize(11);
    assertThat(player.mediaItems.get(1)).isEqualTo(resolvedMediaItem);
  }

  @Test
  public void addQueueItemWithIndex_withInvalidIndex_doesNotCrashSession() throws Exception {
    MediaItem resolvedMediaItem = MediaItem.fromUri(TEST_URI);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            // Resolve MediaItem asynchronously to test correct threading logic.
            return executorService.submit(() -> ImmutableList.of(resolvedMediaItem));
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setId("addQueueItemWithIndex_invalidIndex")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    handler.postAndSync(
        () -> {
          List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 10);
          player.setMediaItems(mediaItems);
          player.timeline = MediaTestUtils.createTimeline(mediaItems);
          player.notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
        });
    // Prepare an item to add.
    MediaDescriptionCompat desc =
        new MediaDescriptionCompat.Builder()
            .setMediaId("media_id")
            .setMediaUri(Uri.parse("https://test.test"))
            .build();

    controller.addQueueItem(desc, /* index= */ -1);
    controller.addQueueItem(desc, /* index= */ 1); // Add valid call to wait for.
    player.awaitMethodCalled(MockPlayer.METHOD_ADD_MEDIA_ITEMS_WITH_INDEX, TIMEOUT_MS);

    assertThat(player.index).isEqualTo(1);
  }

  @Test
  public void removeQueueItem() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("removeQueueItem")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 10);
    handler.postAndSync(
        () -> {
          player.setMediaItems(mediaItems);
          player.timeline = new PlaylistTimeline(mediaItems);
          player.notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
        });
    // Select an item to remove.
    int targetIndex = 3;
    MediaItem targetItem = mediaItems.get(targetIndex);
    MediaDescriptionCompat desc =
        new MediaDescriptionCompat.Builder().setMediaId(targetItem.mediaId).build();

    controller.removeQueueItem(desc);
    player.awaitMethodCalled(MockPlayer.METHOD_REMOVE_MEDIA_ITEM, TIMEOUT_MS);

    assertThat(player.index).isEqualTo(targetIndex);
  }

  @Test
  public void skipToPrevious_withAllCommandsAvailable_callsSeekToPrevious() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("skipToPrevious")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().skipToPrevious();
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_PREVIOUS, TIMEOUT_MS);
  }

  @Test
  public void skipToPrevious_withoutSeekToPreviousCommandAvailable_callsSeekToPreviousMediaItem()
      throws Exception {
    player.commands =
        new Player.Commands.Builder()
            .addAllCommands()
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build();
    session =
        new MediaSession.Builder(context, player)
            .setId("skipToPrevious")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().skipToPrevious();
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_PREVIOUS_MEDIA_ITEM, TIMEOUT_MS);
  }

  @Test
  public void skipToNext_withAllCommandsAvailable_callsSeekToNext() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("skipToNext")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().skipToNext();
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_NEXT, TIMEOUT_MS);
  }

  @Test
  public void skipToNext_withoutSeekToNextCommandAvailable_callsSeekToNextMediaItem()
      throws Exception {
    player.commands =
        new Player.Commands.Builder().addAllCommands().remove(Player.COMMAND_SEEK_TO_NEXT).build();
    session =
        new MediaSession.Builder(context, player)
            .setId("skipToNext")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().skipToNext();
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_NEXT_MEDIA_ITEM, TIMEOUT_MS);
  }

  @Test
  public void skipToQueueItem() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("skipToQueueItem")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    handler.postAndSync(
        () -> {
          player.timeline = MediaTestUtils.createTimeline(/* windowCount= */ 10);
          player.notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
        });

    // Get Queue from local MediaControllerCompat.
    List<QueueItem> queue = session.getSessionCompat().getController().getQueue();
    int targetIndex = 3;
    controller.getTransportControls().skipToQueueItem(queue.get(targetIndex).getQueueId());
    player.awaitMethodCalled(
        MockPlayer.METHOD_SEEK_TO_DEFAULT_POSITION_WITH_MEDIA_ITEM_INDEX, TIMEOUT_MS);

    assertThat(player.seekMediaItemIndex).isEqualTo(targetIndex);
  }

  @Test
  public void skipToQueueItem_withInvalidValue_doesNotCrashSession() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("skipToQueueItem_invalidValues")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    handler.postAndSync(
        () -> {
          player.timeline = MediaTestUtils.createTimeline(/* windowCount= */ 10);
          player.notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
        });

    controller.getTransportControls().skipToQueueItem(-1);
    controller.getTransportControls().skipToQueueItem(1); // Add valid call to wait for.
    player.awaitMethodCalled(
        MockPlayer.METHOD_SEEK_TO_DEFAULT_POSITION_WITH_MEDIA_ITEM_INDEX, TIMEOUT_MS);

    assertThat(player.seekMediaItemIndex).isEqualTo(1);
  }

  @Test
  public void dispatchMediaButtonEvent_playWithEmptyTimeline_callsPlaybackResumptionPrepareAndPlay()
      throws Exception {
    ArrayList<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    AtomicReference<MediaSession> session = new AtomicReference<>();
    CallerCollectorPlayer callerCollectorPlayer = new CallerCollectorPlayer(session, player);
    session.set(
        mediaSessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, callerCollectorPlayer)
                .setId("dispatchMediaButtonEvent")
                .setCallback(
                    new MediaSession.Callback() {
                      @Override
                      public ListenableFuture<MediaSession.MediaItemsWithStartPosition>
                          onPlaybackResumption(
                              MediaSession mediaSession, ControllerInfo controller) {
                        return Futures.immediateFuture(
                            new MediaSession.MediaItemsWithStartPosition(
                                mediaItems, /* startIndex= */ 1, /* startPositionMs= */ 123L));
                      }
                    })
                .build()));
    controller =
        new RemoteMediaControllerCompat(
            context,
            session.get().getSessionCompat().getSessionToken(),
            /* waitForConnection= */ true);
    KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);

    session.get().getSessionCompat().getController().dispatchMediaButtonEvent(keyEvent);

    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX))
        .isTrue();
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isTrue();
    assertThat(player.startMediaItemIndex).isEqualTo(1);
    assertThat(player.startPositionMs).isEqualTo(123L);
    assertThat(player.mediaItems).isEqualTo(mediaItems);
    assertThat(callerCollectorPlayer.callers).hasSize(3);
    for (ControllerInfo controllerInfo : callerCollectorPlayer.callers) {
      assertThat(session.get().isMediaNotificationController(controllerInfo)).isFalse();
    }
  }

  @Test
  public void
      dispatchMediaButtonEvent_playWithEmptyTimelineWithoutCommandGetCurrentMediaItem_doesNotTriggerPlaybackResumption()
          throws Exception {
    player.commands =
        new Player.Commands.Builder()
            .addAllCommands()
            .remove(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .build();
    session = new MediaSession.Builder(context, player).setId("dispatchMediaButtonEvent").build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);

    session.getSessionCompat().getController().dispatchMediaButtonEvent(keyEvent);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isTrue();
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX))
        .isFalse();
    assertThat(player.mediaItems).isEmpty();
  }

  @Test
  public void
      dispatchMediaButtonEvent_playWithEmptyTimelineWithoutCommandSetOrChangeMediaItems_doesNotTriggerPlaybackResumption()
          throws Exception {
    player.commands =
        new Player.Commands.Builder()
            .addAllCommands()
            .removeAll(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .build();
    session = new MediaSession.Builder(context, player).setId("dispatchMediaButtonEvent").build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);

    session.getSessionCompat().getController().dispatchMediaButtonEvent(keyEvent);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isTrue();
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX))
        .isFalse();
    assertThat(player.mediaItems).isEmpty();
  }

  @Test
  public void
      dispatchMediaButtonEvent_playWithEmptyTimelineWithoutCommandChangeMediaItems_setsSingleItem()
          throws Exception {
    player.commands =
        new Player.Commands.Builder()
            .addAllCommands()
            .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .build();
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(
              MediaSession mediaSession, ControllerInfo controller) {
            return Futures.immediateFuture(
                new MediaSession.MediaItemsWithStartPosition(
                    mediaItems, /* startIndex= */ 1, /* startPositionMs= */ 123L));
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setCallback(callback)
            .setId("dispatchMediaButtonEvent")
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);

    session.getSessionCompat().getController().dispatchMediaButtonEvent(keyEvent);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isTrue();
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEM_WITH_START_POSITION))
        .isTrue();
    assertThat(player.startMediaItemIndex).isEqualTo(0);
    assertThat(player.startPositionMs).isEqualTo(123L);
    assertThat(player.mediaItems).containsExactly(mediaItems.get(0));
  }

  @Test
  public void
      dispatchMediaButtonEvent_playWithEmptyTimelineWithMediaNotificationController_callsPlaybackResumptionPrepareAndPlay()
          throws Exception {
    ArrayList<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    AtomicReference<MediaSession> session = new AtomicReference<>();
    CallerCollectorPlayer callerCollectorPlayer = new CallerCollectorPlayer(session, player);
    session.set(
        mediaSessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, callerCollectorPlayer)
                .setId("dispatchMediaButtonEvent")
                .setCallback(
                    new MediaSession.Callback() {
                      @Override
                      public ListenableFuture<MediaSession.MediaItemsWithStartPosition>
                          onPlaybackResumption(
                              MediaSession mediaSession, ControllerInfo controller) {
                        return Futures.immediateFuture(
                            new MediaSession.MediaItemsWithStartPosition(
                                mediaItems, /* startIndex= */ 1, /* startPositionMs= */ 123L));
                      }
                    })
                .build()));
    controller =
        new RemoteMediaControllerCompat(
            context,
            session.get().getSessionCompat().getSessionToken(),
            /* waitForConnection= */ true);
    KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true);
    new MediaController.Builder(
            ApplicationProvider.getApplicationContext(), session.get().getToken())
        .setConnectionHints(connectionHints)
        .buildAsync()
        .get();

    session.get().getSessionCompat().getController().dispatchMediaButtonEvent(keyEvent);

    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX))
        .isTrue();
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isTrue();
    assertThat(player.startMediaItemIndex).isEqualTo(1);
    assertThat(player.startPositionMs).isEqualTo(123L);
    assertThat(player.mediaItems).isEqualTo(mediaItems);
    assertThat(callerCollectorPlayer.callers).hasSize(3);
    for (ControllerInfo controllerInfo : callerCollectorPlayer.callers) {
      assertThat(session.get().isMediaNotificationController(controllerInfo)).isTrue();
    }
  }

  @Test
  public void
      dispatchMediaButtonEvent_playWithEmptyTimelinePlaybackResumptionFailure_callsHandlePlayButtonAction()
          throws Exception {
    player.mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    player.startMediaItemIndex = 1;
    player.startPositionMs = 321L;
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(
              MediaSession mediaSession, ControllerInfo controller) {
            return Futures.immediateFailedFuture(new UnsupportedOperationException());
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setCallback(callback)
            .setId("sendMediaButtonEvent")
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);

    session.getSessionCompat().getController().dispatchMediaButtonEvent(keyEvent);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isTrue();
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX))
        .isFalse();
    assertThat(player.startMediaItemIndex).isEqualTo(1);
    assertThat(player.startPositionMs).isEqualTo(321L);
    assertThat(player.mediaItems).hasSize(3);
  }

  @Test
  public void dispatchMediaButtonEvent_playWithNonEmptyTimeline_callsHandlePlayButtonAction()
      throws Exception {
    KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);
    player.mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    player.timeline = new PlaylistTimeline(player.mediaItems);
    player.startMediaItemIndex = 1;
    player.startPositionMs = 321L;
    AtomicReference<MediaSession> session = new AtomicReference<>();
    CallerCollectorPlayer callerCollectorPlayer = new CallerCollectorPlayer(session, player);
    session.set(
        mediaSessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, callerCollectorPlayer)
                .setId("dispatchMediaButtonEvent")
                .build()));
    controller =
        new RemoteMediaControllerCompat(
            context,
            session.get().getSessionCompat().getSessionToken(),
            /* waitForConnection= */ true);

    session.get().getSessionCompat().getController().dispatchMediaButtonEvent(keyEvent);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isTrue();
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX))
        .isFalse();
    assertThat(player.startMediaItemIndex).isEqualTo(1);
    assertThat(player.startPositionMs).isEqualTo(321L);
    assertThat(player.mediaItems).hasSize(3);
    assertThat(callerCollectorPlayer.callers).hasSize(2);
    for (ControllerInfo controllerInfo : callerCollectorPlayer.callers) {
      assertThat(session.get().isMediaNotificationController(controllerInfo)).isFalse();
    }
  }

  @Test
  public void
      dispatchMediaButtonEvent_playWithNonEmptyTimelineWithMediaNotificationController_callsHandlePlayButtonAction()
          throws Exception {
    KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);
    player.mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    player.timeline = new PlaylistTimeline(player.mediaItems);
    AtomicReference<MediaSession> session = new AtomicReference<>();
    CallerCollectorPlayer callerCollectorPlayer = new CallerCollectorPlayer(session, player);
    session.set(
        mediaSessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, callerCollectorPlayer)
                .setId("dispatchMediaButtonEvent")
                .build()));
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true);
    new MediaController.Builder(
            ApplicationProvider.getApplicationContext(), session.get().getToken())
        .setConnectionHints(connectionHints)
        .buildAsync()
        .get();
    controller =
        new RemoteMediaControllerCompat(
            context,
            session.get().getSessionCompat().getSessionToken(),
            /* waitForConnection= */ true);

    session.get().getSessionCompat().getController().dispatchMediaButtonEvent(keyEvent);

    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
    assertThat(player.mediaItems).hasSize(3);
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isTrue();
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX))
        .isFalse();
    assertThat(callerCollectorPlayer.callers).hasSize(2);
    for (ControllerInfo controllerInfo : callerCollectorPlayer.callers) {
      assertThat(session.get().isMediaNotificationController(controllerInfo)).isTrue();
    }
  }

  @Test
  public void setShuffleMode() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("setShuffleMode")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    @PlaybackStateCompat.ShuffleMode int testShuffleMode = PlaybackStateCompat.SHUFFLE_MODE_GROUP;

    controller.getTransportControls().setShuffleMode(testShuffleMode);
    player.awaitMethodCalled(MockPlayer.METHOD_SET_SHUFFLE_MODE, TIMEOUT_MS);

    assertThat(player.shuffleModeEnabled).isTrue();
  }

  @Test
  public void setRepeatMode() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("setRepeatMode")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    int testRepeatMode = Player.REPEAT_MODE_ALL;

    controller.getTransportControls().setRepeatMode(testRepeatMode);
    player.awaitMethodCalled(MockPlayer.METHOD_SET_REPEAT_MODE, TIMEOUT_MS);

    assertThat(player.repeatMode).isEqualTo(testRepeatMode);
  }

  @Test
  public void setVolumeTo_setsDeviceVolume() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("setVolumeTo_setsDeviceVolume")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    MockPlayer remotePlayer =
        new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build();
    remotePlayer.commands =
        new Player.Commands.Builder()
            .addAllCommands()
            .remove(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
            .build();
    handler.postAndSync(
        () -> {
          remotePlayer.deviceInfo =
              new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(100).build();
          remotePlayer.deviceVolume = 23;
          session.setPlayer(remotePlayer);
        });
    int targetVolume = 50;

    controller.setVolumeTo(targetVolume, /* flags= */ 0);
    remotePlayer.awaitMethodCalled(MockPlayer.METHOD_SET_DEVICE_VOLUME, TIMEOUT_MS);

    assertThat(remotePlayer.deviceVolume).isEqualTo(targetVolume);
  }

  @Test
  public void setVolumeTo_setsDeviceVolumeWithFlags() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("setVolumeTo_setsDeviceVolumeWithFlags")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    MockPlayer remotePlayer =
        new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build();
    remotePlayer.commands = new Player.Commands.Builder().addAllCommands().build();
    handler.postAndSync(
        () -> {
          remotePlayer.deviceInfo =
              new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(100).build();
          remotePlayer.deviceVolume = 23;
          session.setPlayer(remotePlayer);
        });
    int targetVolume = 50;

    controller.setVolumeTo(targetVolume, /* flags= */ 0);
    remotePlayer.awaitMethodCalled(MockPlayer.METHOD_SET_DEVICE_VOLUME_WITH_FLAGS, TIMEOUT_MS);

    assertThat(remotePlayer.deviceVolume).isEqualTo(targetVolume);
  }

  @Test
  public void adjustVolume_raise_increasesDeviceVolume() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("adjustVolume_raise_increasesDeviceVolume")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    MockPlayer remotePlayer =
        new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build();
    remotePlayer.commands =
        new Player.Commands.Builder()
            .addAllCommands()
            .remove(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
            .build();
    handler.postAndSync(
        () -> {
          remotePlayer.deviceInfo =
              new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(100).build();
          remotePlayer.deviceVolume = 23;
          session.setPlayer(remotePlayer);
        });

    controller.adjustVolume(AudioManager.ADJUST_RAISE, /* flags= */ 0);
    remotePlayer.awaitMethodCalled(MockPlayer.METHOD_INCREASE_DEVICE_VOLUME, TIMEOUT_MS);
  }

  @Test
  public void adjustVolume_raise_increasesDeviceVolumeWithFlags() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("adjustVolume_raise_increasesDeviceVolumeWithFlags")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    MockPlayer remotePlayer =
        new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build();
    handler.postAndSync(
        () -> {
          remotePlayer.deviceInfo =
              new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(100).build();
          remotePlayer.deviceVolume = 23;
          session.setPlayer(remotePlayer);
        });

    controller.adjustVolume(AudioManager.ADJUST_RAISE, /* flags= */ 0);
    remotePlayer.awaitMethodCalled(MockPlayer.METHOD_INCREASE_DEVICE_VOLUME_WITH_FLAGS, TIMEOUT_MS);
  }

  @Test
  public void adjustVolume_lower_decreasesDeviceVolume() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("adjustVolume_lower_decreasesDeviceVolume")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    MockPlayer remotePlayer =
        new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build();
    remotePlayer.commands =
        new Player.Commands.Builder()
            .addAllCommands()
            .remove(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
            .build();
    handler.postAndSync(
        () -> {
          remotePlayer.deviceInfo =
              new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(100).build();
          remotePlayer.deviceVolume = 23;
          session.setPlayer(remotePlayer);
        });

    controller.adjustVolume(AudioManager.ADJUST_LOWER, /* flags= */ 0);
    remotePlayer.awaitMethodCalled(MockPlayer.METHOD_DECREASE_DEVICE_VOLUME, TIMEOUT_MS);
  }

  @Test
  public void adjustVolume_lower_decreasesDeviceVolumeWithFlags() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("adjustVolume_lower_decreasesDeviceVolumeWithFlags")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    MockPlayer remotePlayer =
        new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build();
    remotePlayer.commands = new Player.Commands.Builder().addAllCommands().build();
    handler.postAndSync(
        () -> {
          remotePlayer.deviceInfo =
              new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(100).build();
          remotePlayer.deviceVolume = 23;
          session.setPlayer(remotePlayer);
        });

    controller.adjustVolume(AudioManager.ADJUST_LOWER, /* flags= */ 0);
    remotePlayer.awaitMethodCalled(MockPlayer.METHOD_DECREASE_DEVICE_VOLUME_WITH_FLAGS, TIMEOUT_MS);
  }

  @Test
  public void setVolumeWithLocalVolume() throws Exception {
    if (Util.SDK_INT >= 21 && audioManager.isVolumeFixed()) {
      // This test is not eligible for this device.
      return;
    }

    session =
        new MediaSession.Builder(context, player)
            .setId("setVolumeWithLocalVolume")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    // Here, we intentionally choose STREAM_ALARM in order not to consider
    // 'Do Not Disturb' or 'Volume limit'.
    int stream = AudioManager.STREAM_ALARM;
    int maxVolume = AudioManagerCompat.getStreamMaxVolume(audioManager, stream);
    int minVolume = AudioManagerCompat.getStreamMinVolume(audioManager, stream);
    Log.d(TAG, "maxVolume=" + maxVolume + ", minVolume=" + minVolume);
    if (maxVolume <= minVolume) {
      return;
    }

    handler.postAndSync(
        () -> {
          // Set stream of the session.
          AudioAttributes attrs =
              LegacyConversions.convertToAudioAttributes(
                  new AudioAttributesCompat.Builder().setLegacyStreamType(stream).build());
          player.audioAttributes = attrs;
          player.notifyAudioAttributesChanged(attrs);
        });

    int originalVolume = audioManager.getStreamVolume(stream);
    int targetVolume = originalVolume == minVolume ? originalVolume + 1 : originalVolume - 1;
    Log.d(TAG, "originalVolume=" + originalVolume + ", targetVolume=" + targetVolume);

    controller.setVolumeTo(targetVolume, C.VOLUME_FLAG_SHOW_UI);
    PollingCheck.waitFor(
        VOLUME_CHANGE_TIMEOUT_MS, () -> targetVolume == audioManager.getStreamVolume(stream));

    // Set back to original volume.
    audioManager.setStreamVolume(stream, originalVolume, /* flags= */ 0);
  }

  @Test
  public void adjustVolumeWithLocalVolume() throws Exception {
    if (Util.SDK_INT >= 21 && audioManager.isVolumeFixed()) {
      // This test is not eligible for this device.
      return;
    }

    session =
        new MediaSession.Builder(context, player)
            .setId("adjustVolumeWithLocalVolume")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    // Here, we intentionally choose STREAM_ALARM in order not to consider
    // 'Do Not Disturb' or 'Volume limit'.
    int stream = AudioManager.STREAM_ALARM;
    int maxVolume = AudioManagerCompat.getStreamMaxVolume(audioManager, stream);
    int minVolume = AudioManagerCompat.getStreamMinVolume(audioManager, stream);
    Log.d(TAG, "maxVolume=" + maxVolume + ", minVolume=" + minVolume);
    if (maxVolume <= minVolume) {
      return;
    }

    handler.postAndSync(
        () -> {
          // Set stream of the session.
          AudioAttributes attrs =
              LegacyConversions.convertToAudioAttributes(
                  new AudioAttributesCompat.Builder().setLegacyStreamType(stream).build());
          player.audioAttributes = attrs;
          player.notifyAudioAttributesChanged(attrs);
        });

    int originalVolume = audioManager.getStreamVolume(stream);
    int direction =
        originalVolume == minVolume ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
    int targetVolume = originalVolume + direction;
    Log.d(TAG, "originalVolume=" + originalVolume + ", targetVolume=" + targetVolume);

    controller.adjustVolume(direction, C.VOLUME_FLAG_SHOW_UI);
    PollingCheck.waitFor(
        VOLUME_CHANGE_TIMEOUT_MS, () -> targetVolume == audioManager.getStreamVolume(stream));

    // Set back to original volume.
    audioManager.setStreamVolume(stream, originalVolume, /* flags= */ 0);
  }

  @Test
  public void sendCommand() throws Exception {
    // TODO(jaewan): Need to revisit with the permission.
    String testCommand = "test_command";
    Bundle testArgs = new Bundle();
    testArgs.putString("args", "test_args");

    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public MediaSession.ConnectionResult onConnect(
              MediaSession session, ControllerInfo controller) {
            if (EXPECTED_CONTROLLER_PACKAGE_NAME.equals(controller.getPackageName())) {
              MediaSession.ConnectionResult commands =
                  MediaSession.Callback.super.onConnect(session, controller);
              SessionCommands.Builder builder = commands.availableSessionCommands.buildUpon();
              builder.add(new SessionCommand(testCommand, /* extras= */ Bundle.EMPTY));
              return MediaSession.ConnectionResult.accept(
                  /* availableSessionCommands= */ builder.build(),
                  commands.availablePlayerCommands);
            } else {
              return MediaSession.ConnectionResult.reject();
            }
          }

          @Override
          public ListenableFuture<SessionResult> onCustomCommand(
              MediaSession session,
              ControllerInfo controller,
              SessionCommand sessionCommand,
              Bundle args) {
            assertThat(sessionCommand.customAction).isEqualTo(testCommand);
            assertThat(TestUtils.equals(testArgs, args)).isTrue();
            latch.countDown();
            return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setId("sendCommand")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.sendCommand(testCommand, testArgs, /* cb= */ null);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void sendCustomCommand() throws Exception {
    String testCommand = "test_custom_command";
    Bundle testArgs = new Bundle();
    testArgs.putString("args", "test_custom_args");
    SessionCommand customCommand = new SessionCommand(testCommand, /* extras= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public MediaSession.ConnectionResult onConnect(
              MediaSession session, ControllerInfo controller) {
            if (EXPECTED_CONTROLLER_PACKAGE_NAME.equals(controller.getPackageName())) {
              MediaSession.ConnectionResult connectionResult =
                  MediaSession.Callback.super.onConnect(session, controller);
              SessionCommands.Builder builder =
                  connectionResult.availableSessionCommands.buildUpon().add(customCommand);
              return MediaSession.ConnectionResult.accept(
                  /* availableSessionCommands= */ builder.build(),
                  connectionResult.availablePlayerCommands);
            } else {
              return MediaSession.ConnectionResult.reject();
            }
          }

          @Override
          public ListenableFuture<SessionResult> onCustomCommand(
              MediaSession session,
              ControllerInfo controller,
              SessionCommand sessionCommand,
              Bundle args) {
            if (sessionCommand.customAction.equals(testCommand)
                && TestUtils.equals(testArgs, args)) {
              latch.countDown();
            }
            return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setId("sendCommand")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.sendCustomCommand(customCommand, testArgs);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void controllerCallback_sessionRejects() throws Exception {
    MediaSession.Callback sessionCallback =
        new MediaSession.Callback() {
          @Override
          public MediaSession.ConnectionResult onConnect(
              MediaSession session, ControllerInfo controller) {
            return MediaSession.ConnectionResult.reject();
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setId("controllerCallback_sessionRejects")
            .setCallback(sessionCallback)
            .build();
    // Session will not accept the controller's commands.
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().play();

    Thread.sleep(NO_RESPONSE_TIMEOUT_MS);
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PLAY)).isFalse();
  }

  @Test
  public void prepareFromMediaUri_withOnAddMediaItems() throws Exception {
    Uri mediaUri = Uri.parse("foo://bar");
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaItem resolvedMediaItem = MediaItem.fromUri(TEST_URI);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItem asynchronously to test correct threading logic.
            return executorService.submit(() -> ImmutableList.of(resolvedMediaItem));
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setId("prepareFromMediaUri")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().prepareFromUri(mediaUri, bundle);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
    assertThat(requestedMediaItems.get()).hasSize(1);
    assertThat(requestedMediaItems.get().get(0).requestMetadata.mediaUri).isEqualTo(mediaUri);
    TestUtils.equals(requestedMediaItems.get().get(0).requestMetadata.extras, bundle);
    assertThat(player.mediaItems).containsExactly(resolvedMediaItem);
  }

  @Test
  public void playFromMediaUri_withOnAddMediaItems() throws Exception {
    Uri request = Uri.parse("foo://bar");
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaItem resolvedMediaItem = MediaItem.fromUri(TEST_URI);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItem asynchronously to test correct threading logic.
            return executorService.submit(() -> ImmutableList.of(resolvedMediaItem));
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setId("playFromMediaUri")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().playFromUri(request, bundle);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
    assertThat(requestedMediaItems.get()).hasSize(1);
    assertThat(requestedMediaItems.get().get(0).requestMetadata.mediaUri).isEqualTo(request);
    TestUtils.equals(requestedMediaItems.get().get(0).requestMetadata.extras, bundle);
    assertThat(player.mediaItems).containsExactly(resolvedMediaItem);
  }

  @Test
  public void prepareFromMediaId_withOnAddMediaItems() throws Exception {
    String request = "media_id";
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaItem resolvedMediaItem = MediaItem.fromUri(TEST_URI);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItem asynchronously to test correct threading logic.
            return executorService.submit(() -> ImmutableList.of(resolvedMediaItem));
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setId("prepareFromMediaId")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().prepareFromMediaId(request, bundle);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
    assertThat(requestedMediaItems.get()).hasSize(1);
    assertThat(requestedMediaItems.get().get(0).mediaId).isEqualTo(request);
    TestUtils.equals(requestedMediaItems.get().get(0).requestMetadata.extras, bundle);
    assertThat(player.mediaItems).containsExactly(resolvedMediaItem);
  }

  @Test
  public void prepareFromMediaId_withOnSetMediaItems_callsPlayerWithStartIndex() throws Exception {
    String request = "media_id";
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaItem resolvedMediaItem = MediaItem.fromUri(TEST_URI);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(
              MediaSession mediaSession,
              ControllerInfo controller,
              List<MediaItem> mediaItems,
              int startIndex,
              long startPositionMs) {
            requestedMediaItems.set(mediaItems);
            return executorService.submit(
                () ->
                    new MediaSession.MediaItemsWithStartPosition(
                        ImmutableList.of(resolvedMediaItem),
                        /* startIndex= */ 2,
                        /* startPositionMs= */ 100));
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setId("prepareFromMediaId")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().prepareFromMediaId(request, bundle);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
    assertThat(requestedMediaItems.get()).hasSize(1);
    assertThat(requestedMediaItems.get().get(0).mediaId).isEqualTo(request);
    TestUtils.equals(requestedMediaItems.get().get(0).requestMetadata.extras, bundle);
    assertThat(player.mediaItems).containsExactly(resolvedMediaItem);
    assertThat(player.startMediaItemIndex).isEqualTo(2);
    assertThat(player.startPositionMs).isEqualTo(100);
  }

  @Test
  public void playFromMediaId_withOnAddMediaItems() throws Exception {
    String mediaId = "media_id";
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaItem resolvedMediaItem = MediaItem.fromUri(TEST_URI);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItem asynchronously to test correct threading logic.
            return executorService.submit(() -> ImmutableList.of(resolvedMediaItem));
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setId("playFromMediaId")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().playFromMediaId(mediaId, bundle);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
    assertThat(requestedMediaItems.get()).hasSize(1);
    assertThat(requestedMediaItems.get().get(0).mediaId).isEqualTo(mediaId);
    TestUtils.equals(requestedMediaItems.get().get(0).requestMetadata.extras, bundle);
    assertThat(player.mediaItems).containsExactly(resolvedMediaItem);
  }

  @Test
  public void playFromMediaId_withOnSetMediaItems_callsPlayerWithStartIndex() throws Exception {
    String mediaId = "media_id";
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");
    MediaItem resolvedMediaItem = MediaItem.fromUri(TEST_URI);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(
              MediaSession mediaSession,
              ControllerInfo controller,
              List<MediaItem> mediaItems,
              int startIndex,
              long startPositionMs) {
            return executorService.submit(
                () ->
                    new MediaSession.MediaItemsWithStartPosition(
                        ImmutableList.of(resolvedMediaItem),
                        /* startIndex= */ 2,
                        /* startPositionMs= */ 100));
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setId("playFromMediaId")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().playFromMediaId(mediaId, bundle);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
    assertThat(player.mediaItems).containsExactly(resolvedMediaItem);
    assertThat(player.startMediaItemIndex).isEqualTo(2);
    assertThat(player.startPositionMs).isEqualTo(100);
  }

  @Test
  public void prepareFromSearch_withOnAddMediaItems() throws Exception {
    String query = "test_query";
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaItem resolvedMediaItem = MediaItem.fromUri(TEST_URI);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItem asynchronously to test correct threading logic.
            return executorService.submit(() -> ImmutableList.of(resolvedMediaItem));
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setId("prepareFromSearch")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().prepareFromSearch(query, bundle);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
    assertThat(requestedMediaItems.get()).hasSize(1);
    assertThat(requestedMediaItems.get().get(0).requestMetadata.searchQuery).isEqualTo(query);
    TestUtils.equals(requestedMediaItems.get().get(0).requestMetadata.extras, bundle);
    assertThat(player.mediaItems).containsExactly(resolvedMediaItem);
  }

  @Test
  public void playFromSearch_withOnAddMediaItems() throws Exception {
    String query = "test_query";
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaItem resolvedMediaItem = MediaItem.fromUri(TEST_URI);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItem asynchronously to test correct threading logic.
            return executorService.submit(() -> ImmutableList.of(resolvedMediaItem));
          }
        };
    session =
        new MediaSession.Builder(context, player)
            .setId("playFromSearch")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().playFromSearch(query, bundle);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
    assertThat(requestedMediaItems.get()).hasSize(1);
    assertThat(requestedMediaItems.get().get(0).requestMetadata.searchQuery).isEqualTo(query);
    TestUtils.equals(requestedMediaItems.get().get(0).requestMetadata.extras, bundle);
    assertThat(player.mediaItems).containsExactly(resolvedMediaItem);
  }

  @Test
  public void prepareFromMediaUri_withoutAvailablePrepareCommand_justCallsSetMediaItems()
      throws Exception {
    MediaItem resolvedMediaItem = MediaItem.fromUri(TEST_URI);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            return Futures.immediateFuture(ImmutableList.of(resolvedMediaItem));
          }
        };
    player.commands =
        new Player.Commands.Builder().addAllCommands().remove(COMMAND_PREPARE).build();
    session =
        new MediaSession.Builder(context, player)
            .setId("prepareFromMediaUri")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().prepareFromUri(Uri.parse("foo://bar"), Bundle.EMPTY);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isFalse();
    assertThat(player.mediaItems).containsExactly(resolvedMediaItem);
  }

  @Test
  public void playFromMediaUri_withoutAvailablePrepareCommand_justCallsSetMediaItemsAndPlay()
      throws Exception {
    MediaItem resolvedMediaItem = MediaItem.fromUri(TEST_URI);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            return Futures.immediateFuture(ImmutableList.of(resolvedMediaItem));
          }
        };
    player.commands =
        new Player.Commands.Builder().addAllCommands().remove(COMMAND_PREPARE).build();
    session =
        new MediaSession.Builder(context, player)
            .setId("prepareFromMediaUri")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().playFromUri(Uri.parse("foo://bar"), Bundle.EMPTY);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isFalse();
    assertThat(player.mediaItems).containsExactly(resolvedMediaItem);
  }

  @Test
  public void playFromMediaUri_withoutAvailablePrepareAndPlayCommand_justCallsSetMediaItems()
      throws Exception {
    MediaItem resolvedMediaItem = MediaItem.fromUri(TEST_URI);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            return Futures.immediateFuture(ImmutableList.of(resolvedMediaItem));
          }
        };
    player.commands =
        new Player.Commands.Builder()
            .addAllCommands()
            .removeAll(COMMAND_PREPARE, COMMAND_PLAY_PAUSE)
            .build();
    session =
        new MediaSession.Builder(context, player)
            .setId("prepareFromMediaUri")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().playFromUri(Uri.parse("foo://bar"), Bundle.EMPTY);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isFalse();
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PLAY)).isFalse();
    assertThat(player.mediaItems).containsExactly(resolvedMediaItem);
  }

  @Test
  public void setRating() throws Exception {
    int ratingType = RatingCompat.RATING_5_STARS;
    float ratingValue = 3.5f;
    RatingCompat rating = RatingCompat.newStarRating(ratingType, ratingValue);
    String mediaId = "media_id";

    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.Callback callback =
        new TestSessionCallback() {
          @Override
          public ListenableFuture<SessionResult> onSetRating(
              MediaSession session,
              ControllerInfo controller,
              String mediaIdOut,
              Rating ratingOut) {
            assertThat(mediaIdOut).isEqualTo(mediaId);
            assertThat(ratingOut).isEqualTo(LegacyConversions.convertToRating(rating));
            latch.countDown();
            return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
          }
        };
    handler.postAndSync(
        () -> {
          List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(mediaId);
          player.timeline = MediaTestUtils.createTimeline(mediaItems);
        });
    session =
        new MediaSession.Builder(context, player).setId("setRating").setCallback(callback).build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().setRating(rating);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void onCommandRequest() throws Exception {
    ArrayList<Integer> commands = new ArrayList<>();
    CountDownLatch latchForPause = new CountDownLatch(1);
    MediaSession.Callback callback =
        new TestSessionCallback() {
          @Override
          public int onPlayerCommandRequest(
              MediaSession session, ControllerInfo controllerInfo, @Player.Command int command) {
            assertThat(controllerInfo.isTrusted()).isFalse();
            commands.add(command);
            if (command == COMMAND_PLAY_PAUSE) {
              latchForPause.countDown();
              return RESULT_ERROR_INVALID_STATE;
            }
            return RESULT_SUCCESS;
          }
        };

    session =
        new MediaSession.Builder(context, player)
            .setId("onPlayerCommandRequest")
            .setCallback(callback)
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);

    controller.getTransportControls().pause();

    assertThat(latchForPause.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    Thread.sleep(NO_RESPONSE_TIMEOUT_MS);
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PAUSE)).isFalse();
    assertThat(commands).hasSize(1);
    assertThat(commands.get(0)).isEqualTo(COMMAND_PLAY_PAUSE);

    controller.getTransportControls().prepare();

    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PAUSE)).isFalse();
    assertThat(commands).hasSize(2);
    assertThat(commands.get(1)).isEqualTo(COMMAND_PREPARE);
  }

  /** Test potential deadlock for calls between controller and session. */
  @Test
  public void deadlock() throws Exception {
    MockPlayer player = new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build();
    session =
        new MediaSession.Builder(context, player)
            .setId("deadlock")
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    // This may hang if deadlock happens.
    handler.postAndSync(
        () -> {
          int state = STATE_IDLE;
          for (int i = 0; i < 100; i++) {
            // triggers call from session to controller.
            player.notifyPlaybackStateChanged(state);
            // triggers call from controller to session.
            controller.getTransportControls().play();

            // Repeat above
            player.notifyPlaybackStateChanged(state);
            controller.getTransportControls().pause();
            player.notifyPlaybackStateChanged(state);
            controller.getTransportControls().stop();
            player.notifyPlaybackStateChanged(state);
            controller.getTransportControls().skipToNext();
            player.notifyPlaybackStateChanged(state);
            controller.getTransportControls().skipToPrevious();
          }
        },
        LONG_TIMEOUT_MS);
  }

  @Test
  public void closedSession_ignoresController() throws Exception {
    String sessionId = "closedSession_ignoresController";
    session =
        new MediaSession.Builder(context, player)
            .setId(sessionId)
            .setCallback(new TestSessionCallback())
            .build();
    controller =
        new RemoteMediaControllerCompat(
            context, session.getSessionCompat().getSessionToken(), /* waitForConnection= */ true);
    session.release();
    session = null;

    controller.getTransportControls().play();
    Thread.sleep(NO_RESPONSE_TIMEOUT_MS);
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PLAY)).isFalse();

    // Ensure that the controller cannot use newly create session with the same ID.
    // Recreated session has different session stub, so previously created controller
    // shouldn't be available.
    session =
        new MediaSession.Builder(context, player)
            .setId(sessionId)
            .setCallback(new TestSessionCallback())
            .build();

    controller.getTransportControls().play();
    Thread.sleep(NO_RESPONSE_TIMEOUT_MS);
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PLAY)).isFalse();
  }

  private static class TestSessionCallback implements MediaSession.Callback {

    @Override
    public MediaSession.ConnectionResult onConnect(
        MediaSession session, ControllerInfo controller) {
      if (EXPECTED_CONTROLLER_PACKAGE_NAME.equals(controller.getPackageName())) {
        return MediaSession.Callback.super.onConnect(session, controller);
      }
      return MediaSession.ConnectionResult.reject();
    }
  }

  private static class CallerCollectorPlayer extends ForwardingPlayer {
    private final List<ControllerInfo> callers;
    private final AtomicReference<MediaSession> mediaSession;

    public CallerCollectorPlayer(AtomicReference<MediaSession> mediaSession, MockPlayer player) {
      super(player);
      this.mediaSession = mediaSession;
      callers = new ArrayList<>();
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
      callers.add(checkNotNull(mediaSession.get().getControllerForCurrentRequest()));
      super.setMediaItems(mediaItems, startIndex, startPositionMs);
    }

    @Override
    public void prepare() {
      callers.add(checkNotNull(mediaSession.get().getControllerForCurrentRequest()));
      super.prepare();
    }

    @Override
    public void play() {
      callers.add(checkNotNull(mediaSession.get().getControllerForCurrentRequest()));
      super.play();
    }
  }
}
