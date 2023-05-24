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

import static androidx.media3.common.Player.COMMAND_ADJUST_DEVICE_VOLUME;
import static androidx.media3.common.Player.COMMAND_CHANGE_MEDIA_ITEMS;
import static androidx.media3.common.Player.COMMAND_PLAY_PAUSE;
import static androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SET_DEVICE_VOLUME;
import static androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SET_PLAYLIST_METADATA;
import static androidx.media3.common.Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS;
import static androidx.media3.session.MediaUtils.createPlayerCommandsWith;
import static androidx.media3.session.MediaUtils.createPlayerCommandsWithout;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_SESSION_SET_RATING;
import static androidx.media3.session.SessionResult.RESULT_SUCCESS;
import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static androidx.media3.test.session.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.Rating;
import androidx.media3.common.StarRating;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.CueGroup;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for permission handling of {@link MediaSession}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaSessionPermissionTest {
  private static final String SESSION_ID = "MediaSessionTest_permission";

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule
  public final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaSessionPermissionTest");

  @Rule public final RemoteControllerTestRule controllerTestRule = new RemoteControllerTestRule();

  private Context context;
  private MockPlayer player;
  private MediaSession session;
  private MySessionCallback callback;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
  }

  @After
  public void tearDown() {
    if (session != null) {
      session.release();
      session = null;
    }
    player = null;
    callback = null;
  }

  @Test
  public void play() throws Exception {
    testOnCommandRequest(COMMAND_PLAY_PAUSE, RemoteMediaController::play);
  }

  @Test
  public void pause() throws Exception {
    testOnCommandRequest(COMMAND_PLAY_PAUSE, RemoteMediaController::pause);
  }

  @Test
  public void seekTo() throws Exception {
    long position = 10;
    testOnCommandRequest(
        COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, controller -> controller.seekTo(position));
  }

  @Test
  public void seekToNextMediaItem() throws Exception {
    testOnCommandRequest(
        COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, RemoteMediaController::seekToNextMediaItem);
  }

  @Test
  public void seekToPreviousMediaItem() throws Exception {
    testOnCommandRequest(
        COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, RemoteMediaController::seekToPreviousMediaItem);
  }

  @Test
  public void setPlaylistMetadata() throws Exception {
    testOnCommandRequest(
        COMMAND_SET_PLAYLIST_METADATA,
        controller -> controller.setPlaylistMetadata(MediaMetadata.EMPTY));
  }

  @Test
  public void setMediaItem() throws Exception {
    testOnCommandRequest(
        COMMAND_SET_MEDIA_ITEM, controller -> controller.setMediaItem(MediaItem.EMPTY));
  }

  @Test
  public void setMediaItems() throws Exception {
    testOnCommandRequest(
        COMMAND_CHANGE_MEDIA_ITEMS,
        controller -> controller.setMediaItems(Collections.emptyList()));
  }

  @Test
  public void addMediaItems() throws Exception {
    testOnCommandRequest(
        COMMAND_CHANGE_MEDIA_ITEMS,
        controller -> controller.addMediaItems(/* index= */ 0, Collections.emptyList()));
  }

  @Test
  public void removeMediaItems() throws Exception {
    testOnCommandRequest(
        COMMAND_CHANGE_MEDIA_ITEMS,
        /* mediaItems= */ MediaTestUtils.createMediaItems(/* size= */ 5),
        controller -> controller.removeMediaItems(/* fromIndex= */ 0, /* toIndex= */ 1));
  }

  @Test
  public void replaceMediaItem() throws Exception {
    testOnCommandRequest(
        COMMAND_CHANGE_MEDIA_ITEMS,
        controller -> controller.replaceMediaItem(/* index= */ 0, MediaItem.EMPTY));
  }

  @Test
  public void replaceMediaItems() throws Exception {
    testOnCommandRequest(
        COMMAND_CHANGE_MEDIA_ITEMS,
        controller ->
            controller.replaceMediaItems(/* fromIndex= */ 0, /* toIndex= */ 1, ImmutableList.of()));
  }

  @Test
  public void setDeviceVolume() throws Exception {
    testOnCommandRequest(COMMAND_SET_DEVICE_VOLUME, controller -> controller.setDeviceVolume(0));
  }

  @Test
  public void increaseDeviceVolume() throws Exception {
    testOnCommandRequest(COMMAND_ADJUST_DEVICE_VOLUME, RemoteMediaController::increaseDeviceVolume);
  }

  @Test
  public void decreaseDeviceVolume() throws Exception {
    testOnCommandRequest(COMMAND_ADJUST_DEVICE_VOLUME, RemoteMediaController::decreaseDeviceVolume);
  }

  @Test
  public void setDeviceMuted() throws Exception {
    testOnCommandRequest(
        COMMAND_ADJUST_DEVICE_VOLUME, controller -> controller.setDeviceMuted(true));
  }

  @Test
  public void setRating() throws Exception {
    String mediaId = "testSetRating";
    Rating rating = new StarRating(5, 3.5f);
    createSession(
        createSessionCommandsWith(new SessionCommand(COMMAND_CODE_SESSION_SET_RATING)),
        Player.Commands.EMPTY);
    controllerTestRule.createRemoteController(session.getToken()).setRating(mediaId, rating);

    assertThat(callback.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(callback.onSetRatingCalled).isTrue();
    assertThat(callback.mediaId).isEqualTo(mediaId);
    assertThat(callback.rating).isEqualTo(rating);

    createSession(SessionCommands.EMPTY, Player.Commands.EMPTY);
    controllerTestRule.createRemoteController(session.getToken()).setRating(mediaId, rating);
    assertThat(callback.countDownLatch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
    assertThat(callback.onSetRatingCalled).isFalse();
  }

  @Test
  public void changingPermissionForSessionCommandWithSetAvailableCommands() throws Exception {
    String mediaId = "testSetRating";
    Rating rating = new StarRating(5, 3.5f);
    createSession(
        createSessionCommandsWith(new SessionCommand(COMMAND_CODE_SESSION_SET_RATING)),
        Player.Commands.EMPTY);
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.setRating(mediaId, rating);
    assertThat(callback.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(callback.onSetRatingCalled).isTrue();
    callback.reset();

    // Change allowed commands.
    session.setAvailableCommands(
        getTestControllerInfo(), SessionCommands.EMPTY, Player.Commands.EMPTY);

    controller.setRating(mediaId, rating);
    assertThat(callback.countDownLatch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
  }

  @Test
  public void changingPermissionForPlayerCommandWithSetAvailableCommands() throws Exception {
    int playPauseCommand = COMMAND_PLAY_PAUSE;
    Player.Commands commandsWithPlayPause = createPlayerCommandsWith(playPauseCommand);
    Player.Commands commandsWithoutPlayPause = createPlayerCommandsWithout(playPauseCommand);

    // Create session with play/pause command.
    createSession(SessionCommands.EMPTY, commandsWithPlayPause);
    // Create player with play/pause command.
    player.commands = commandsWithPlayPause;
    player.notifyAvailableCommandsChanged(commandsWithPlayPause);
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.play();
    assertThat(callback.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(callback.onCommandRequestCalled).isTrue();
    assertThat(callback.command).isEqualTo(playPauseCommand);
    callback.reset();

    // Change session to not have play/pause command.
    session.setAvailableCommands(
        getTestControllerInfo(), SessionCommands.EMPTY, commandsWithoutPlayPause);

    controller.play();
    assertThat(callback.countDownLatch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
    assertThat(callback.onCommandRequestCalled).isFalse();
  }

  @Test
  public void setTrackSelectionParameters() throws Exception {
    testOnCommandRequest(
        COMMAND_SET_TRACK_SELECTION_PARAMETERS,
        controller ->
            controller.setTrackSelectionParameters(
                TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT));
  }

  @Test
  public void setPlayer_withoutAvailableCommands_doesNotCallProtectedPlayerGetters()
      throws Exception {
    MockPlayer mockPlayer =
        new MockPlayer.Builder()
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .build();
    // Set remote device info to ensure we also cover the volume provider compat setup.
    mockPlayer.deviceInfo =
        new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(100).build();
    Player player =
        new ForwardingPlayer(mockPlayer) {
          @Override
          public boolean isCommandAvailable(int command) {
            return false;
          }

          @Override
          public Tracks getCurrentTracks() {
            throw new UnsupportedOperationException();
          }

          @Override
          public MediaMetadata getMediaMetadata() {
            throw new UnsupportedOperationException();
          }

          @Override
          public MediaMetadata getPlaylistMetadata() {
            throw new UnsupportedOperationException();
          }

          @Override
          public Timeline getCurrentTimeline() {
            throw new UnsupportedOperationException();
          }

          @Override
          public int getCurrentPeriodIndex() {
            throw new UnsupportedOperationException();
          }

          @Override
          public int getCurrentMediaItemIndex() {
            throw new UnsupportedOperationException();
          }

          @Override
          public int getNextMediaItemIndex() {
            throw new UnsupportedOperationException();
          }

          @Override
          public int getPreviousMediaItemIndex() {
            throw new UnsupportedOperationException();
          }

          @Nullable
          @Override
          public MediaItem getCurrentMediaItem() {
            throw new UnsupportedOperationException();
          }

          @Override
          public long getDuration() {
            throw new UnsupportedOperationException();
          }

          @Override
          public long getCurrentPosition() {
            throw new UnsupportedOperationException();
          }

          @Override
          public long getBufferedPosition() {
            throw new UnsupportedOperationException();
          }

          @Override
          public long getTotalBufferedDuration() {
            throw new UnsupportedOperationException();
          }

          @Override
          public boolean isCurrentMediaItemDynamic() {
            throw new UnsupportedOperationException();
          }

          @Override
          public boolean isCurrentMediaItemLive() {
            throw new UnsupportedOperationException();
          }

          @Override
          public boolean isPlayingAd() {
            throw new UnsupportedOperationException();
          }

          @Override
          public int getCurrentAdGroupIndex() {
            throw new UnsupportedOperationException();
          }

          @Override
          public int getCurrentAdIndexInAdGroup() {
            throw new UnsupportedOperationException();
          }

          @Override
          public long getContentDuration() {
            throw new UnsupportedOperationException();
          }

          @Override
          public long getContentPosition() {
            throw new UnsupportedOperationException();
          }

          @Override
          public long getContentBufferedPosition() {
            throw new UnsupportedOperationException();
          }

          @Override
          public AudioAttributes getAudioAttributes() {
            throw new UnsupportedOperationException();
          }

          @Override
          public CueGroup getCurrentCues() {
            throw new UnsupportedOperationException();
          }

          @Override
          public int getDeviceVolume() {
            throw new UnsupportedOperationException();
          }

          @Override
          public boolean isDeviceMuted() {
            throw new UnsupportedOperationException();
          }
        };
    MediaSession session = new MediaSession.Builder(context, player).setId(SESSION_ID).build();

    MediaController controller =
        new MediaController.Builder(context, session.getToken())
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .buildAsync()
            .get();

    // Test passes if none of the protected player getters have been called.
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.release();
              session.release();
              player.release();
            });
  }

  private ControllerInfo getTestControllerInfo() {
    List<ControllerInfo> controllers = session.getConnectedControllers();
    assertThat(controllers).isNotNull();
    for (int i = 0; i < controllers.size(); i++) {
      if (TextUtils.equals(SUPPORT_APP_PACKAGE_NAME, controllers.get(i).getPackageName())) {
        return controllers.get(i);
      }
    }
    throw new IllegalStateException("Failed to get test controller info");
  }

  /* @FunctionalInterface */
  private interface PermissionTestTask {
    void run(RemoteMediaController controller) throws Exception;
  }

  private static class MySessionCallback implements MediaSession.Callback {
    public CountDownLatch countDownLatch;

    public @Player.Command int command;
    public String mediaId;
    public Bundle extras;
    public Rating rating;

    public boolean onCommandRequestCalled;
    public boolean onSetRatingCalled;

    public MySessionCallback() {
      countDownLatch = new CountDownLatch(1);
    }

    public void reset() {
      countDownLatch = new CountDownLatch(1);

      mediaId = null;

      onCommandRequestCalled = false;
      onSetRatingCalled = false;
    }

    @Override
    public int onPlayerCommandRequest(
        MediaSession session, ControllerInfo controller, @Player.Command int command) {
      assertThat(TextUtils.equals(SUPPORT_APP_PACKAGE_NAME, controller.getPackageName())).isTrue();
      onCommandRequestCalled = true;
      this.command = command;
      countDownLatch.countDown();
      return MediaSession.Callback.super.onPlayerCommandRequest(session, controller, command);
    }

    @Override
    public ListenableFuture<SessionResult> onSetRating(
        MediaSession session, ControllerInfo controller, String mediaId, Rating rating) {
      assertThat(TextUtils.equals(SUPPORT_APP_PACKAGE_NAME, controller.getPackageName())).isTrue();
      onSetRatingCalled = true;
      this.mediaId = mediaId;
      this.rating = rating;
      countDownLatch.countDown();
      return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
    }
  }

  private void createSession(SessionCommands sessionCommands, Player.Commands playerCommands) {
    createSession(sessionCommands, playerCommands, /* mediaItems= */ ImmutableList.of());
  }

  private void createSession(
      SessionCommands sessionCommands, Player.Commands playerCommands, List<MediaItem> mediaItems) {
    player =
        new MockPlayer.Builder()
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .build();
    // Add media items directly on the mock player's list so that the player's interaction state
    // does not change.
    player.mediaItems.addAll(mediaItems);
    callback =
        new MySessionCallback() {
          @Override
          public MediaSession.ConnectionResult onConnect(
              MediaSession session, ControllerInfo controller) {
            if (!TextUtils.equals(SUPPORT_APP_PACKAGE_NAME, controller.getPackageName())) {
              return MediaSession.ConnectionResult.reject();
            }
            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands);
          }
        };
    if (this.session != null) {
      this.session.release();
    }
    this.session =
        new MediaSession.Builder(context, player).setId(SESSION_ID).setCallback(callback).build();
  }

  private SessionCommands createSessionCommandsWith(SessionCommand command) {
    return new SessionCommands.Builder().add(command).build();
  }

  private void testOnCommandRequest(int commandCode, PermissionTestTask runnable) throws Exception {
    testOnCommandRequest(commandCode, /* mediaItems= */ ImmutableList.of(), runnable);
  }

  private void testOnCommandRequest(
      int commandCode, List<MediaItem> mediaItems, PermissionTestTask runnable) throws Exception {
    createSession(SessionCommands.EMPTY, createPlayerCommandsWith(commandCode), mediaItems);
    runnable.run(controllerTestRule.createRemoteController(session.getToken()));

    assertThat(callback.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(callback.onCommandRequestCalled).isTrue();
    assertThat(callback.command).isEqualTo(commandCode);

    createSession(SessionCommands.EMPTY, createPlayerCommandsWithout(commandCode), mediaItems);
    runnable.run(controllerTestRule.createRemoteController(session.getToken()));

    assertThat(callback.countDownLatch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
    assertThat(callback.onCommandRequestCalled).isFalse();
  }
}
