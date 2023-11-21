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

import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.getEventsAsList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.media.VolumeProviderCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.FlagSet;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Util;
import androidx.media3.test.session.R;
import androidx.media3.test.session.common.CommonConstants;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/** Tests for {@link MediaController.Listener} with {@link MediaSessionCompat}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerListenerWithMediaSessionCompatTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private static final int EVENT_ON_EVENTS = C.INDEX_UNSET;

  private final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaControllerListenerWithMediaSessionCompatTest");
  private final MediaControllerTestRule controllerTestRule =
      new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  private Context context;
  private RemoteMediaSessionCompat session;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    session = new RemoteMediaSessionCompat(CommonConstants.DEFAULT_TEST_NAME, context);
  }

  @After
  public void cleanUp() throws RemoteException {
    session.cleanUp();
  }

  @Test
  public void onEvents_whenOnRepeatModeChanges_isCalledAfterOtherListenerMethods()
      throws Exception {
    Player.Events testEvents =
        new Player.Events(new FlagSet.Builder().add(Player.EVENT_REPEAT_MODE_CHANGED).build());
    CopyOnWriteArrayList<Integer> listenerEventCodes = new CopyOnWriteArrayList<>();

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
            listenerEventCodes.add(Player.EVENT_REPEAT_MODE_CHANGED);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            listenerEventCodes.add(EVENT_ON_EVENTS);
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_GROUP);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(listenerEventCodes)
        .containsExactly(Player.EVENT_REPEAT_MODE_CHANGED, EVENT_ON_EVENTS);
    assertThat(eventsRef.get()).isEqualTo(testEvents);
  }

  @Test
  public void setPlaybackState_withCustomActions_onSetCustomLayoutCalled() throws Exception {
    Bundle extras1 = new Bundle();
    extras1.putString("key", "value-1");
    PlaybackStateCompat.CustomAction customAction1 =
        new PlaybackStateCompat.CustomAction.Builder("action1", "actionName1", /* icon= */ 1)
            .setExtras(extras1)
            .build();
    Bundle extras2 = new Bundle();
    extras2.putString("key", "value-2");
    PlaybackStateCompat.CustomAction customAction2 =
        new PlaybackStateCompat.CustomAction.Builder("action2", "actionName2", /* icon= */ 2)
            .setExtras(extras2)
            .build();
    PlaybackStateCompat.Builder builder =
        new PlaybackStateCompat.Builder()
            .addCustomAction(customAction1)
            .addCustomAction(customAction2);
    List<String> receivedActions = new ArrayList<>();
    List<String> receivedDisplayNames = new ArrayList<>();
    List<String> receivedBundleValues = new ArrayList<>();
    List<Integer> receivedIconResIds = new ArrayList<>();
    List<Integer> receivedCommandCodes = new ArrayList<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);
    controllerTestRule.createController(
        session.getSessionToken(),
        new MediaController.Listener() {
          @Override
          public ListenableFuture<SessionResult> onSetCustomLayout(
              MediaController controller, List<CommandButton> layout) {
            for (CommandButton button : layout) {
              receivedActions.add(button.sessionCommand.customAction);
              receivedDisplayNames.add(String.valueOf(button.displayName));
              receivedBundleValues.add(button.sessionCommand.customExtras.getString("key"));
              receivedCommandCodes.add(button.sessionCommand.commandCode);
              receivedIconResIds.add(button.iconResId);
            }
            countDownLatch.countDown();
            return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
          }
        });

    session.setPlaybackState(builder.build());

    assertThat(countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedActions).containsExactly("action1", "action2").inOrder();
    assertThat(receivedCommandCodes)
        .containsExactly(SessionCommand.COMMAND_CODE_CUSTOM, SessionCommand.COMMAND_CODE_CUSTOM)
        .inOrder();
    assertThat(receivedDisplayNames).containsExactly("actionName1", "actionName2").inOrder();
    assertThat(receivedIconResIds).containsExactly(1, 2).inOrder();
    assertThat(receivedBundleValues).containsExactly("value-1", "value-2").inOrder();
  }

  @Test
  public void setSessionExtras_onExtrasChangedCalled() throws Exception {
    Bundle sessionExtras = new Bundle();
    sessionExtras.putString("key-1", "value-1");
    CountDownLatch countDownLatch = new CountDownLatch(1);
    List<Bundle> receivedSessionExtras = new ArrayList<>();
    List<Bundle> getterSessionExtras = new ArrayList<>();
    controllerTestRule.createController(
        session.getSessionToken(),
        new MediaController.Listener() {
          @Override
          public void onExtrasChanged(MediaController controller, Bundle extras) {
            receivedSessionExtras.add(extras);
            getterSessionExtras.add(controller.getSessionExtras());
            countDownLatch.countDown();
          }
        });

    session.setExtras(sessionExtras);

    assertThat(countDownLatch.await(1_000, MILLISECONDS)).isTrue();
    assertThat(TestUtils.equals(receivedSessionExtras.get(0), sessionExtras)).isTrue();
    assertThat(TestUtils.equals(getterSessionExtras.get(0), sessionExtras)).isTrue();
  }

  @Test
  public void setSessionExtras_includedWhenConnecting() throws Exception {
    Bundle sessionExtras = new Bundle();
    sessionExtras.putString("key-1", "value-1");
    session.setExtras(sessionExtras);

    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    assertThat(
            TestUtils.equals(
                threadTestRule.getHandler().postAndSync(controller::getSessionExtras),
                sessionExtras))
        .isTrue();
  }

  @Test
  public void onPlaylistMetadataChanged() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<MediaMetadata> playlistMetadataParamRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> playlistMetadataGetterRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> playlistMetadataOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEvents = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaylistMetadataChanged(MediaMetadata mediaMetadata) {
            playlistMetadataParamRef.set(mediaMetadata);
            playlistMetadataGetterRef.set(controller.getPlaylistMetadata());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEvents.set(events);
            playlistMetadataOnEventsRef.set(player.getPlaylistMetadata());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setQueueTitle("queue-title");

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playlistMetadataParamRef.get().title.toString()).isEqualTo("queue-title");
    assertThat(playlistMetadataGetterRef.get()).isEqualTo(playlistMetadataParamRef.get());
    assertThat(playlistMetadataOnEventsRef.get()).isEqualTo(playlistMetadataParamRef.get());
    assertThat(getEventsAsList(onEvents.get()))
        .containsExactly(Player.EVENT_PLAYLIST_METADATA_CHANGED);
  }

  @Test
  public void onAudioAttributesChanged() throws Exception {
    // We need to trigger MediaControllerCompat.Callback.onAudioInfoChanged in order to raise the
    // onAudioAttributesChanged() callback. In API 21 and 22, onAudioInfoChanged is not called when
    // playback is changed to local.
    assumeTrue(Util.SDK_INT != 21 && Util.SDK_INT != 22);

    session.setPlaybackToRemote(
        /* volumeControl= */ VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,
        /* maxVolume= */ 100,
        /* currentVolume= */ 50,
        /* routingControllerId= */ "route");
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<AudioAttributes> audioAttributesParamRef = new AtomicReference<>();
    AtomicReference<AudioAttributes> audioAttributesGetterRef = new AtomicReference<>();
    AtomicReference<AudioAttributes> audioAttributesOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEvents = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onAudioAttributesChanged(AudioAttributes audioAttributes) {
            audioAttributesParamRef.set(audioAttributes);
            audioAttributesGetterRef.set(controller.getAudioAttributes());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEvents.set(events);
            audioAttributesOnEventsRef.set(player.getAudioAttributes());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setPlaybackToLocal(AudioManager.STREAM_ALARM);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(audioAttributesGetterRef.get().contentType).isEqualTo(AudioManager.STREAM_ALARM);
    assertThat(audioAttributesGetterRef.get()).isEqualTo(audioAttributesParamRef.get());
    assertThat(audioAttributesOnEventsRef.get()).isEqualTo(audioAttributesParamRef.get());
    assertThat(getEventsAsList(onEvents.get())).contains(Player.EVENT_AUDIO_ATTRIBUTES_CHANGED);
  }

  @Test
  public void onDeviceInfoChanged() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<DeviceInfo> deviceInfoParamRef = new AtomicReference<>();
    AtomicReference<DeviceInfo> deviceInfoGetterRef = new AtomicReference<>();
    AtomicReference<DeviceInfo> deviceInfoOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEvents = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceInfoChanged(DeviceInfo deviceInfo) {
            deviceInfoParamRef.set(deviceInfo);
            deviceInfoGetterRef.set(controller.getDeviceInfo());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            deviceInfoOnEventsRef.set(player.getDeviceInfo());
            onEvents.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    String testRoutingSessionId = Util.SDK_INT >= 30 ? "route" : null;

    session.setPlaybackToRemote(
        /* volumeControl= */ VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,
        /* maxVolume= */ 100,
        /* currentVolume= */ 50,
        testRoutingSessionId);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceInfoParamRef.get().playbackType).isEqualTo(DeviceInfo.PLAYBACK_TYPE_REMOTE);
    assertThat(deviceInfoParamRef.get().maxVolume).isEqualTo(100);
    assertThat(deviceInfoParamRef.get().routingControllerId).isEqualTo(testRoutingSessionId);
    assertThat(deviceInfoGetterRef.get()).isEqualTo(deviceInfoParamRef.get());
    assertThat(deviceInfoOnEventsRef.get()).isEqualTo(deviceInfoGetterRef.get());
    assertThat(getEventsAsList(onEvents.get())).contains(Player.EVENT_DEVICE_VOLUME_CHANGED);
  }

  @Test
  public void onDeviceVolumeChanged() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger deviceVolumeParam = new AtomicInteger();
    AtomicInteger deviceVolumeGetter = new AtomicInteger();
    AtomicInteger deviceVolumeOnEvents = new AtomicInteger();
    AtomicReference<Player.Events> onEvents = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceVolumeParam.set(volume);
            deviceVolumeGetter.set(controller.getDeviceVolume());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            deviceVolumeOnEvents.set(player.getDeviceVolume());
            onEvents.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setPlaybackToRemote(
        /* volumeControl= */ VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,
        /* maxVolume= */ 100,
        /* currentVolume= */ 50,
        /* routingControllerId= */ "route");

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceVolumeParam.get()).isEqualTo(50);
    assertThat(deviceVolumeGetter.get()).isEqualTo(50);
    assertThat(deviceVolumeOnEvents.get()).isEqualTo(50);
    assertThat(getEventsAsList(onEvents.get())).contains(Player.EVENT_DEVICE_VOLUME_CHANGED);
  }

  @Test
  public void getCustomLayout() throws Exception {
    CommandButton button1 =
        new CommandButton.Builder()
            .setDisplayName("button1")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(new SessionCommand("command1", Bundle.EMPTY))
            .build();
    CommandButton button2 =
        new CommandButton.Builder()
            .setDisplayName("button2")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(new SessionCommand("command2", Bundle.EMPTY))
            .build();
    ConditionVariable onSetCustomLayoutCalled = new ConditionVariable();
    ConditionVariable onCustomLayoutChangedCalled = new ConditionVariable();
    List<List<CommandButton>> setCustomLayoutArguments = new ArrayList<>();
    List<List<CommandButton>> customLayoutChangedArguments = new ArrayList<>();
    List<List<CommandButton>> customLayoutFromGetter = new ArrayList<>();
    controllerTestRule.createController(
        session.getSessionToken(),
        new MediaController.Listener() {
          @Override
          public ListenableFuture<SessionResult> onSetCustomLayout(
              MediaController controller, List<CommandButton> layout) {
            setCustomLayoutArguments.add(layout);
            onSetCustomLayoutCalled.open();
            return MediaController.Listener.super.onSetCustomLayout(controller, layout);
          }

          @Override
          public void onCustomLayoutChanged(
              MediaController controller, List<CommandButton> layout) {
            customLayoutChangedArguments.add(layout);
            customLayoutFromGetter.add(controller.getCustomLayout());
            onCustomLayoutChangedCalled.open();
          }
        });
    Bundle extras1 = new Bundle();
    extras1.putString("key", "value-1");
    PlaybackStateCompat.CustomAction customAction1 =
        new PlaybackStateCompat.CustomAction.Builder(
                "command1", "button1", /* icon= */ R.drawable.media3_notification_small_icon)
            .setExtras(extras1)
            .build();
    Bundle extras2 = new Bundle();
    extras2.putString("key", "value-2");
    PlaybackStateCompat.CustomAction customAction2 =
        new PlaybackStateCompat.CustomAction.Builder(
                "command2", "button2", /* icon= */ R.drawable.media3_notification_small_icon)
            .setExtras(extras2)
            .build();
    PlaybackStateCompat.Builder playbackState1 =
        new PlaybackStateCompat.Builder()
            .addCustomAction(customAction1)
            .addCustomAction(customAction2);
    PlaybackStateCompat.Builder playbackState2 =
        new PlaybackStateCompat.Builder().addCustomAction(customAction1);

    session.setPlaybackState(playbackState1.build());
    assertThat(onSetCustomLayoutCalled.block(TIMEOUT_MS)).isTrue();
    assertThat(onCustomLayoutChangedCalled.block(TIMEOUT_MS)).isTrue();
    onSetCustomLayoutCalled.close();
    onCustomLayoutChangedCalled.close();
    session.setPlaybackState(playbackState2.build());
    assertThat(onSetCustomLayoutCalled.block(TIMEOUT_MS)).isTrue();
    assertThat(onCustomLayoutChangedCalled.block(TIMEOUT_MS)).isTrue();

    ImmutableList<CommandButton> expectedFirstCustomLayout =
        ImmutableList.of(button1.copyWithIsEnabled(true), button2.copyWithIsEnabled(true));
    ImmutableList<CommandButton> expectedSecondCustomLayout =
        ImmutableList.of(button1.copyWithIsEnabled(true));
    assertThat(setCustomLayoutArguments)
        .containsExactly(expectedFirstCustomLayout, expectedSecondCustomLayout)
        .inOrder();
    assertThat(customLayoutChangedArguments)
        .containsExactly(expectedFirstCustomLayout, expectedSecondCustomLayout)
        .inOrder();
    assertThat(customLayoutFromGetter)
        .containsExactly(expectedFirstCustomLayout, expectedSecondCustomLayout)
        .inOrder();
  }

  @Test
  public void getCurrentPosition_unknownPlaybackPosition_convertedToZero() throws Exception {
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_NONE,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                /* playbackSpeed= */ 1.0f)
            .build());
    MediaControllerCompat legacyController =
        new MediaControllerCompat(
            ApplicationProvider.getApplicationContext(), session.getSessionToken());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    assertThat(legacyController.getPlaybackState().getPosition())
        .isEqualTo(PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN);
    assertThat(threadTestRule.getHandler().postAndSync(controller::getCurrentPosition))
        .isEqualTo(0);
  }
}
