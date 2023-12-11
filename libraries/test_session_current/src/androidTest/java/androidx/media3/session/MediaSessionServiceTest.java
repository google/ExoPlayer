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

import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static androidx.media3.test.session.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.test.session.R;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.TestHandler;
import androidx.media3.test.session.common.TestUtils;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaSessionService}. */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MediaSessionServiceTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule public final RemoteControllerTestRule controllerTestRule = new RemoteControllerTestRule();
  @Rule public final MediaSessionTestRule sessionTestRule = new MediaSessionTestRule();

  @Rule
  public final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaSessionServiceTest");

  private Context context;
  private SessionToken token;

  @Before
  public void setUp() {
    TestServiceRegistry.getInstance().cleanUp();
    context = ApplicationProvider.getApplicationContext();
    token =
        new SessionToken(context, new ComponentName(context, LocalMockMediaSessionService.class));
  }

  @After
  public void cleanUp() {
    TestServiceRegistry.getInstance().cleanUp();
  }

  @Test
  public void onConnect_controllerInfo_sameInstanceFromServiceToConnectedControllerManager()
      throws Exception {
    TestServiceRegistry testServiceRegistry = TestServiceRegistry.getInstance();
    List<ControllerInfo> onGetSessionControllerInfos = new ArrayList<>();
    List<ControllerInfo> onConnectControllerInfos = new ArrayList<>();
    List<ControllerInfo> playbackCommandControllerInfos = new ArrayList<>();
    List<ControllerInfo> onDisconnectedCommandControllerInfos = new ArrayList<>();
    AtomicReference<MediaSession> session = new AtomicReference<>();
    testServiceRegistry.setOnGetSessionHandler(
        controllerInfo -> {
          // The controllerInfo passed to the onGetSession of the service.
          onGetSessionControllerInfos.add(controllerInfo);
          Player player = new ExoPlayer.Builder(context).build();
          ForwardingPlayer forwardingPlayer =
              new ForwardingPlayer(player) {
                @Override
                public void setRepeatMode(@Player.RepeatMode int repeatMode) {
                  // The controllerInfo assigned for the current player command request.
                  playbackCommandControllerInfos.add(
                      session.get().getControllerForCurrentRequest());
                  super.setRepeatMode(repeatMode);
                }
              };
          session.set(
              new MediaSession.Builder(context, forwardingPlayer)
                  .setCallback(
                      new MediaSession.Callback() {
                        @Override
                        public MediaSession.ConnectionResult onConnect(
                            MediaSession session, ControllerInfo controller) {
                          if (!session.isMediaNotificationController(controller)) {
                            // The controllerInfo passed to MediaSession.Callback.onConnect()
                            onConnectControllerInfos.add(controllerInfo);
                          }
                          return MediaSession.Callback.super.onConnect(session, controller);
                        }

                        @Override
                        public void onDisconnected(
                            MediaSession session, ControllerInfo controller) {
                          if (!session.isMediaNotificationController(controller)) {
                            // The controllerInfo when disconnecting.
                            onDisconnectedCommandControllerInfos.add(controller);
                          }
                          MediaSession.Callback.super.onDisconnected(session, controller);
                        }
                      })
                  .build());
          return session.get();
        });
    // Create the remote controller to start the service.
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(
            token, /* waitForConnection= */ true, /* connectionHints= */ null);
    // Get the started service instance after creation.
    MockMediaSessionService service =
        (MockMediaSessionService) testServiceRegistry.getServiceInstance();
    controller.setRepeatMode(Player.REPEAT_MODE_ONE);
    List<ControllerInfo> connectedControllerManagerControllerInfos = new ArrayList<>();
    for (ControllerInfo controllerInfo : session.get().getConnectedControllers()) {
      if (!session.get().isMediaNotificationController(controllerInfo)) {
        // The controllerInfo in the connected controller manager.
        connectedControllerManagerControllerInfos.add(controllerInfo);
      }
    }

    controller.release();

    service.blockUntilAllControllersUnbind(TIMEOUT_MS);
    assertThat(onGetSessionControllerInfos).hasSize(1);
    assertThat(onGetSessionControllerInfos).isEqualTo(onConnectControllerInfos);
    assertThat(onGetSessionControllerInfos).isEqualTo(playbackCommandControllerInfos);
    assertThat(onGetSessionControllerInfos).isEqualTo(onDisconnectedCommandControllerInfos);
    assertThat(onGetSessionControllerInfos).isEqualTo(connectedControllerManagerControllerInfos);
    assertThat(onGetSessionControllerInfos.get(0))
        .isSameInstanceAs(onConnectControllerInfos.get(0));
    assertThat(onGetSessionControllerInfos.get(0))
        .isSameInstanceAs(playbackCommandControllerInfos.get(0));
    assertThat(onGetSessionControllerInfos.get(0))
        .isSameInstanceAs(onDisconnectedCommandControllerInfos.get(0));
    assertThat(onGetSessionControllerInfos.get(0))
        .isSameInstanceAs(connectedControllerManagerControllerInfos.get(0));
  }

  @Test
  public void controllerRelease_keepsControllerBoundUntilCommandsHandled() throws Exception {
    TestServiceRegistry testServiceRegistry = TestServiceRegistry.getInstance();
    ConditionVariable mediaItemsAdded = new ConditionVariable();
    AtomicBoolean controllerBoundWhenMediaItemsAdded = new AtomicBoolean(false);
    testServiceRegistry.setOnGetSessionHandler(
        controllerInfo -> {
          // Save bound state at the point where media items are added and listeners are informed.
          MockMediaSessionService service =
              (MockMediaSessionService) testServiceRegistry.getServiceInstance();
          Player player = new ExoPlayer.Builder(service).build();
          player.addListener(
              new Player.Listener() {
                @Override
                public void onEvents(Player player, Player.Events events) {
                  if (events.contains(Player.EVENT_TIMELINE_CHANGED)
                      && !player.getCurrentTimeline().isEmpty()) {
                    controllerBoundWhenMediaItemsAdded.set(service.hasBoundController());
                    mediaItemsAdded.open();
                  }
                }
              });
          // Add short delay for resolving media items.
          return new MediaSession.Builder(service, player)
              .setCallback(
                  new MediaSession.Callback() {
                    @Override
                    public ListenableFuture<List<MediaItem>> onAddMediaItems(
                        MediaSession mediaSession,
                        ControllerInfo controller,
                        List<MediaItem> mediaItems) {
                      SettableFuture<List<MediaItem>> future = SettableFuture.create();
                      MediaItem playableItem =
                          mediaItems.get(0).buildUpon().setUri("https://test.test").build();
                      new Handler(Looper.myLooper())
                          .postDelayed(
                              () -> future.set(ImmutableList.of(playableItem)),
                              /* delayMillis= */ 500);
                      return future;
                    }
                  })
              .build();
        });
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(
            token, /* waitForConnection= */ true, /* connectionHints= */ null);
    MockMediaSessionService service =
        (MockMediaSessionService) testServiceRegistry.getServiceInstance();

    // Add items and release controller immediately.
    controller.addMediaItem(new MediaItem.Builder().setMediaId("media_id").build());
    controller.release();

    // Assert controller is still bound when command is fully handled and unbound after that.
    mediaItemsAdded.block(TIMEOUT_MS);
    assertThat(controllerBoundWhenMediaItemsAdded.get()).isEqualTo(true);
    service.blockUntilAllControllersUnbind(TIMEOUT_MS);
  }

  @Test
  public void onCreate_mediaNotificationManagerController_correctSessionStateFromOnConnect()
      throws Exception {
    SessionCommand command1 = new SessionCommand("command1", Bundle.EMPTY);
    SessionCommand command2 = new SessionCommand("command2", Bundle.EMPTY);
    SessionCommand command3 = new SessionCommand("command3", Bundle.EMPTY);
    CommandButton button1 =
        new CommandButton.Builder()
            .setDisplayName("button1")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command1)
            .build();
    CommandButton button2 =
        new CommandButton.Builder()
            .setDisplayName("button2")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command2)
            .build();
    CommandButton button3 =
        new CommandButton.Builder()
            .setDisplayName("button3")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command3)
            .build();
    Bundle testHints = new Bundle();
    testHints.putString("test_key", "test_value");
    List<ControllerInfo> controllerInfoList = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);
    TestHandler handler = new TestHandler(Looper.getMainLooper());
    ExoPlayer player =
        handler.postAndSync(
            () -> {
              ExoPlayer exoPlayer = new TestExoPlayerBuilder(context).build();
              exoPlayer.setMediaItem(MediaItem.fromUri("asset:///media/mp4/sample.mp4"));
              exoPlayer.prepare();
              return exoPlayer;
            });
    MediaSession mediaSession =
        new MediaSession.Builder(ApplicationProvider.getApplicationContext(), player)
            .setCustomLayout(Lists.newArrayList(button1, button2))
            .setCallback(
                new MediaSession.Callback() {
                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession session, ControllerInfo controller) {
                    controllerInfoList.add(controller);
                    if (session.isMediaNotificationController(controller)) {
                      latch.countDown();
                      return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                          .setAvailableSessionCommands(
                              SessionCommands.EMPTY.buildUpon().add(command1).add(command3).build())
                          .setAvailablePlayerCommands(Player.Commands.EMPTY)
                          .setCustomLayout(ImmutableList.of(button1, button3))
                          .build();
                    }
                    latch.countDown();
                    return new MediaSession.ConnectionResult.AcceptedResultBuilder(session).build();
                  }
                })
            .build();
    TestServiceRegistry.getInstance().setOnGetSessionHandler(controllerInfo -> mediaSession);
    MediaControllerCompat mediaControllerCompat =
        new MediaControllerCompat(
            ApplicationProvider.getApplicationContext(), mediaSession.getSessionCompat());
    ImmutableList<CommandButton> initialCustomLayoutInControllerCompat =
        LegacyConversions.convertToCustomLayout(mediaControllerCompat.getPlaybackState());

    // Start the service by creating a remote controller.
    RemoteMediaController remoteController =
        controllerTestRule.createRemoteController(token, /* waitForConnection= */ true, testHints);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(
            controllerInfoList
                .get(0)
                .getConnectionHints()
                .getBoolean(
                    MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG,
                    /* defaultValue= */ false))
        .isTrue();
    assertThat(TestUtils.equals(controllerInfoList.get(1).getConnectionHints(), testHints))
        .isTrue();
    assertThat(mediaControllerCompat.getPlaybackState().getActions())
        .isEqualTo(PlaybackStateCompat.ACTION_SET_RATING);
    assertThat(remoteController.getCustomLayout()).containsExactly(button1, button2).inOrder();
    assertThat(initialCustomLayoutInControllerCompat).isEmpty();
    assertThat(LegacyConversions.convertToCustomLayout(mediaControllerCompat.getPlaybackState()))
        .containsExactly(button1.copyWithIsEnabled(true), button3.copyWithIsEnabled(true))
        .inOrder();
    mediaSession.release();
    ((MockMediaSessionService) TestServiceRegistry.getInstance().getServiceInstance())
        .blockUntilAllControllersUnbind(TIMEOUT_MS);
  }

  /**
   * Tests whether {@link MediaSessionService#onGetSession(ControllerInfo)} is called when
   * controller tries to connect, with the proper arguments.
   */
  @Test
  public void onGetSessionIsCalled() throws Exception {
    Bundle testHints = new Bundle();
    testHints.putString("test_key", "test_value");
    List<ControllerInfo> controllerInfoList = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);
    TestServiceRegistry.getInstance()
        .setOnGetSessionHandler(
            new TestServiceRegistry.OnGetSessionHandler() {
              @Override
              public MediaSession onGetSession(ControllerInfo controllerInfo) {
                if (SUPPORT_APP_PACKAGE_NAME.equals(controllerInfo.getPackageName())
                    && TestUtils.equals(testHints, controllerInfo.getConnectionHints())) {
                  controllerInfoList.add(controllerInfo);
                  latch.countDown();
                }
                return null;
              }
            });
    controllerTestRule.createRemoteController(token, /* waitForConnection= */ false, testHints);

    // onGetSession() should be called.
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(TestUtils.equals(controllerInfoList.get(0).getConnectionHints(), testHints))
        .isTrue();
  }

  /**
   * Tests whether the controller is connected to the session which is returned from {@link
   * MediaSessionService#onGetSession(ControllerInfo)}. Also checks whether the connection hints are
   * properly passed to {@link MediaSession.Callback#onConnect(MediaSession, ControllerInfo)}.
   */
  @Test
  public void onGetSession_returnsSession() throws Exception {
    Bundle testHints = new Bundle();
    testHints.putString("test_key", "test_value");
    List<ControllerInfo> controllerInfoList = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);

    MediaSession testSession =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(
                    context,
                    new MockPlayer.Builder()
                        .setApplicationLooper(threadTestRule.getHandler().getLooper())
                        .build())
                .setId("testOnGetSession_returnsSession")
                .setCallback(
                    new MediaSession.Callback() {
                      @Override
                      public MediaSession.ConnectionResult onConnect(
                          MediaSession session, ControllerInfo controller) {
                        if (SUPPORT_APP_PACKAGE_NAME.equals(controller.getPackageName())
                            && TestUtils.equals(testHints, controller.getConnectionHints())) {
                          controllerInfoList.add(controller);
                          latch.countDown();
                        }
                        return MediaSession.ConnectionResult.accept(
                            SessionCommands.EMPTY, Player.Commands.EMPTY);
                      }
                    })
                .build());

    TestServiceRegistry.getInstance()
        .setOnGetSessionHandler(
            new TestServiceRegistry.OnGetSessionHandler() {
              @Override
              public MediaSession onGetSession(ControllerInfo controllerInfo) {
                return testSession;
              }
            });

    RemoteMediaController controller =
        controllerTestRule.createRemoteController(token, true, testHints);

    // MediaSession.SessionCallback#onConnect() should be called.
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(TestUtils.equals(controllerInfoList.get(0).getConnectionHints(), testHints))
        .isTrue();

    // The controller should be connected to the right session.
    assertThat(controller.getConnectedSessionToken()).isNotEqualTo(token);
    assertThat(controller.getConnectedSessionToken()).isEqualTo(testSession.getToken());
  }

  /**
   * Tests whether {@link MediaSessionService#onGetSession(ControllerInfo)} can return different
   * sessions for different controllers.
   */
  @Test
  public void onGetSession_returnsDifferentSessions() throws Exception {
    List<SessionToken> tokens = new ArrayList<>();
    TestServiceRegistry.getInstance()
        .setOnGetSessionHandler(
            new TestServiceRegistry.OnGetSessionHandler() {
              @Override
              public MediaSession onGetSession(ControllerInfo controllerInfo) {
                MediaSession session =
                    createMediaSession(
                        "testOnGetSession_returnsDifferentSessions" + System.currentTimeMillis());
                tokens.add(session.getToken());
                return session;
              }
            });

    RemoteMediaController controller1 =
        controllerTestRule.createRemoteController(token, true, null);
    RemoteMediaController controller2 =
        controllerTestRule.createRemoteController(token, true, null);

    assertThat(controller2.getConnectedSessionToken())
        .isNotEqualTo(controller1.getConnectedSessionToken());
    assertThat(controller1.getConnectedSessionToken()).isEqualTo(tokens.get(0));
    assertThat(controller2.getConnectedSessionToken()).isEqualTo(tokens.get(1));
  }

  /**
   * Tests whether {@link MediaSessionService#onGetSession(ControllerInfo)} can reject incoming
   * connection by returning null.
   */
  @Test
  public void onGetSession_rejectsConnection() throws Exception {
    TestServiceRegistry.getInstance().setOnGetSessionHandler(controllerInfo -> null);
    ListenableFuture<MediaController> future =
        new MediaController.Builder(context, token)
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .buildAsync();

    ExecutionException thrown =
        assertThrows(ExecutionException.class, () -> future.get(TIMEOUT_MS, MILLISECONDS));
    assertThat(thrown).hasCauseThat().isInstanceOf(SecurityException.class);
  }

  @Test
  public void allControllersDisconnected_oneSession() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    TestServiceRegistry.getInstance()
        .setSessionServiceCallback(
            new TestServiceRegistry.SessionServiceCallback() {
              @Override
              public void onCreated() {
                // no-op
              }

              @Override
              public void onDestroyed() {
                latch.countDown();
              }
            });

    RemoteMediaController controller1 =
        controllerTestRule.createRemoteController(token, true, null);
    RemoteMediaController controller2 =
        controllerTestRule.createRemoteController(token, true, null);
    controller1.release();
    controller2.release();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void allControllersDisconnected_multipleSessions() throws Exception {
    TestServiceRegistry.getInstance()
        .setOnGetSessionHandler(
            new TestServiceRegistry.OnGetSessionHandler() {
              @Override
              public MediaSession onGetSession(ControllerInfo controllerInfo) {
                return createMediaSession(
                    "testAllControllersDisconnected" + System.currentTimeMillis());
              }
            });
    CountDownLatch latch = new CountDownLatch(1);
    TestServiceRegistry.getInstance()
        .setSessionServiceCallback(
            new TestServiceRegistry.SessionServiceCallback() {
              @Override
              public void onCreated() {
                // no-op
              }

              @Override
              public void onDestroyed() {
                latch.countDown();
              }
            });

    RemoteMediaController controller1 =
        controllerTestRule.createRemoteController(token, true, null);
    RemoteMediaController controller2 =
        controllerTestRule.createRemoteController(token, true, null);

    controller1.release();
    assertThat(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();

    // Service should be closed only when all controllers are closed.
    controller2.release();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void getSessions() throws Exception {
    controllerTestRule.createRemoteController(
        token, /* waitForConnection= */ true, /* connectionHints= */ null);
    MediaSessionService service = TestServiceRegistry.getInstance().getServiceInstance();
    MediaSession session = createMediaSession("testGetSessions");
    service.addSession(session);
    List<MediaSession> sessions = service.getSessions();
    assertThat(sessions.contains(session)).isTrue();
    assertThat(sessions.size()).isEqualTo(2);

    service.removeSession(session);
    sessions = service.getSessions();

    assertThat(sessions.contains(session)).isFalse();
  }

  @Test
  public void addSessions_removedWhenReleased() throws Exception {
    controllerTestRule.createRemoteController(
        token, /* waitForConnection= */ true, /* connectionHints= */ null);
    MediaSessionService service = TestServiceRegistry.getInstance().getServiceInstance();
    MediaSession session = createMediaSession("testAddSessions_removedWhenReleased");
    service.addSession(session);
    // Wait until connection of session is propagated.
    MainLooperTestRule.runOnMainSync(() -> {});
    List<MediaSession> sessions = service.getSessions();
    assertThat(sessions.contains(session)).isTrue();
    assertThat(sessions.size()).isEqualTo(2);
    threadTestRule.getHandler().postAndSync(session::release);
    // Wait until release of session is propagated.
    MainLooperTestRule.runOnMainSync(() -> {});
    assertThat(service.getSessions()).doesNotContain(session);
  }

  private MediaSession createMediaSession(String id) {
    return sessionTestRule.ensureReleaseAfterTest(
        new MediaSession.Builder(
                context,
                new MockPlayer.Builder()
                    .setApplicationLooper(threadTestRule.getHandler().getLooper())
                    .build())
            .setId(id)
            .build());
  }
}
