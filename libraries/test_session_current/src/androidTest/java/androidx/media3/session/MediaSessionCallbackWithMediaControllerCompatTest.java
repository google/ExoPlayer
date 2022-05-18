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
import static androidx.media3.common.Player.STATE_IDLE;
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
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioManagerCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.DeviceInfo;
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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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

  private static final String EXPECTED_CONTROLLER_PACKAGE_NAME =
      (Util.SDK_INT < 21 || Util.SDK_INT >= 24) ? SUPPORT_APP_PACKAGE_NAME : LEGACY_CONTROLLER;

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule public final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);

  private Context context;
  private TestHandler handler;
  private MediaSession session;
  private RemoteMediaControllerCompat controller;
  private MockPlayer player;
  private AudioManager audioManager;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    handler = threadTestRule.getHandler();
    player = new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build();
    audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
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
  public void play() throws Exception {
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
  public void addQueueItem() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("addQueueItem")
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

    // Prepare an item to add.
    String mediaId = "newMediaItemId";
    MediaDescriptionCompat desc = new MediaDescriptionCompat.Builder().setMediaId(mediaId).build();
    controller.addQueueItem(desc);

    player.awaitMethodCalled(MockPlayer.METHOD_ADD_MEDIA_ITEM, TIMEOUT_MS);
    assertThat(player.mediaItem.mediaId).isEqualTo(mediaId);
  }

  @Test
  public void addQueueItemWithIndex() throws Exception {
    session =
        new MediaSession.Builder(context, player)
            .setId("addQueueItemWithIndex")
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

    // Prepare an item to add.
    int testIndex = 1;
    String mediaId = "media_id";
    MediaDescriptionCompat desc = new MediaDescriptionCompat.Builder().setMediaId(mediaId).build();
    controller.addQueueItem(desc, testIndex);

    player.awaitMethodCalled(MockPlayer.METHOD_ADD_MEDIA_ITEM_WITH_INDEX, TIMEOUT_MS);
    assertThat(player.index).isEqualTo(testIndex);
    assertThat(player.mediaItem.mediaId).isEqualTo(mediaId);
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
  public void skipToPrevious() throws Exception {
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
  public void skipToNext() throws Exception {
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
    handler.postAndSync(
        () -> {
          remotePlayer.deviceInfo =
              new DeviceInfo(
                  DeviceInfo.PLAYBACK_TYPE_REMOTE, /* minVolume= */ 0, /* maxVolume= */ 100);
          remotePlayer.deviceVolume = 23;
          session.setPlayer(remotePlayer);
        });

    int targetVolume = 50;
    controller.setVolumeTo(targetVolume, /* flags= */ 0);

    remotePlayer.awaitMethodCalled(MockPlayer.METHOD_SET_DEVICE_VOLUME, TIMEOUT_MS);
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
    handler.postAndSync(
        () -> {
          remotePlayer.deviceInfo =
              new DeviceInfo(
                  DeviceInfo.PLAYBACK_TYPE_REMOTE, /* minVolume= */ 0, /* maxVolume= */ 100);
          remotePlayer.deviceVolume = 23;
          session.setPlayer(remotePlayer);
        });

    controller.adjustVolume(AudioManager.ADJUST_RAISE, /* flags= */ 0);

    remotePlayer.awaitMethodCalled(MockPlayer.METHOD_INCREASE_DEVICE_VOLUME, TIMEOUT_MS);
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
    handler.postAndSync(
        () -> {
          remotePlayer.deviceInfo =
              new DeviceInfo(
                  DeviceInfo.PLAYBACK_TYPE_REMOTE, /* minVolume= */ 0, /* maxVolume= */ 100);
          remotePlayer.deviceVolume = 23;
          session.setPlayer(remotePlayer);
        });

    controller.adjustVolume(AudioManager.ADJUST_LOWER, /* flags= */ 0);

    remotePlayer.awaitMethodCalled(MockPlayer.METHOD_DECREASE_DEVICE_VOLUME, TIMEOUT_MS);
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
              MediaUtils.convertToAudioAttributes(
                  new AudioAttributesCompat.Builder().setLegacyStreamType(stream).build());
          player.audioAttributes = attrs;
          player.notifyAudioAttributesChanged(attrs);
        });

    int originalVolume = audioManager.getStreamVolume(stream);
    int targetVolume = originalVolume == minVolume ? originalVolume + 1 : originalVolume - 1;
    Log.d(TAG, "originalVolume=" + originalVolume + ", targetVolume=" + targetVolume);

    controller.setVolumeTo(targetVolume, AudioManager.FLAG_SHOW_UI);
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
              MediaUtils.convertToAudioAttributes(
                  new AudioAttributesCompat.Builder().setLegacyStreamType(stream).build());
          player.audioAttributes = attrs;
          player.notifyAudioAttributesChanged(attrs);
        });

    int originalVolume = audioManager.getStreamVolume(stream);
    int direction =
        originalVolume == minVolume ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
    int targetVolume = originalVolume + direction;
    Log.d(TAG, "originalVolume=" + originalVolume + ", targetVolume=" + targetVolume);

    controller.adjustVolume(direction, AudioManager.FLAG_SHOW_UI);
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
  public void prepareFromMediaUri() throws Exception {
    Uri mediaUri = Uri.parse("foo://bar");
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.Callback callback =
        new TestSessionCallback() {
          @Override
          public int onSetMediaUri(
              MediaSession session, ControllerInfo controller, Uri uri, Bundle extras) {
            assertThat(uri).isEqualTo(mediaUri);
            assertThat(TestUtils.equals(bundle, extras)).isTrue();
            latch.countDown();
            return RESULT_SUCCESS;
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

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
  }

  @Test
  public void playFromMediaUri() throws Exception {
    Uri request = Uri.parse("foo://bar");
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.Callback callback =
        new TestSessionCallback() {
          @Override
          public int onSetMediaUri(
              MediaSession session, ControllerInfo controller, Uri uri, Bundle extras) {
            assertThat(uri).isEqualTo(request);
            assertThat(TestUtils.equals(bundle, extras)).isTrue();
            latch.countDown();
            return RESULT_SUCCESS;
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

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
  }

  @Test
  public void prepareFromMediaId() throws Exception {
    String request = "media_id";
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.Callback callback =
        new TestSessionCallback() {
          @Override
          public int onSetMediaUri(
              MediaSession session, ControllerInfo controller, Uri uri, Bundle extras) {
            assertThat(uri.toString())
                .isEqualTo("androidx://media3-session/prepareFromMediaId?id=" + request);
            assertThat(TestUtils.equals(bundle, extras)).isTrue();
            latch.countDown();
            return RESULT_SUCCESS;
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

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
  }

  @Test
  public void playFromMediaId() throws Exception {
    String mediaId = "media_id";
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.Callback callback =
        new TestSessionCallback() {
          @Override
          public int onSetMediaUri(
              MediaSession session, ControllerInfo controller, Uri uri, Bundle extras) {
            assertThat(uri.toString())
                .isEqualTo("androidx://media3-session/playFromMediaId?id=" + mediaId);
            assertThat(TestUtils.equals(bundle, extras)).isTrue();
            latch.countDown();
            return RESULT_SUCCESS;
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

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
  }

  @Test
  public void prepareFromSearch() throws Exception {
    String query = "test_query";
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.Callback callback =
        new TestSessionCallback() {
          @Override
          public int onSetMediaUri(
              MediaSession session, ControllerInfo controller, Uri uri, Bundle extras) {
            assertThat(uri.toString())
                .isEqualTo("androidx://media3-session/prepareFromSearch?query=" + query);
            assertThat(TestUtils.equals(bundle, extras)).isTrue();
            latch.countDown();
            return RESULT_SUCCESS;
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

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
  }

  @Test
  public void playFromSearch() throws Exception {
    String query = "test_query";
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.Callback callback =
        new TestSessionCallback() {
          @Override
          public int onSetMediaUri(
              MediaSession session, ControllerInfo controller, Uri uri, Bundle extras) {
            assertThat(uri.toString())
                .isEqualTo("androidx://media3-session/playFromSearch?query=" + query);
            assertThat(TestUtils.equals(bundle, extras)).isTrue();
            latch.countDown();
            return RESULT_SUCCESS;
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

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
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
            assertThat(ratingOut).isEqualTo(MediaUtils.convertToRating(rating));
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
}
