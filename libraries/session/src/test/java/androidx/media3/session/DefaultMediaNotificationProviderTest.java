/*
 * Copyright 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Commands;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.SettableFuture;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowNotificationManager;

/** Tests for {@link DefaultMediaNotificationProvider}. */
@RunWith(AndroidJUnit4.class)
public class DefaultMediaNotificationProviderTest {

  @Test
  public void getMediaButtons_playWhenReadyTrueOrFalse_correctPlayPauseResources() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    Commands commands = new Commands.Builder().addAllCommands().build();

    List<CommandButton> mediaButtonsWhenPlaying =
        defaultMediaNotificationProvider.getMediaButtons(
            commands, /* customLayout= */ ImmutableList.of(), /* playWhenReady= */ true);
    List<CommandButton> mediaButtonWhenPaused =
        defaultMediaNotificationProvider.getMediaButtons(
            commands, /* customLayout= */ ImmutableList.of(), /* playWhenReady= */ false);

    assertThat(mediaButtonsWhenPlaying).hasSize(3);
    assertThat(mediaButtonsWhenPlaying.get(1).playerCommand).isEqualTo(Player.COMMAND_PLAY_PAUSE);
    assertThat(mediaButtonsWhenPlaying.get(1).iconResId)
        .isEqualTo(R.drawable.media3_notification_pause);
    assertThat(String.valueOf(mediaButtonsWhenPlaying.get(1).displayName)).isEqualTo("Pause");
    assertThat(mediaButtonWhenPaused).hasSize(3);
    assertThat(mediaButtonWhenPaused.get(1).playerCommand).isEqualTo(Player.COMMAND_PLAY_PAUSE);
    assertThat(mediaButtonWhenPaused.get(1).iconResId)
        .isEqualTo(R.drawable.media3_notification_play);
    assertThat(String.valueOf(mediaButtonWhenPaused.get(1).displayName)).isEqualTo("Play");
  }

  @Test
  public void getMediaButtons_allCommandsAvailable_createsPauseSkipNextSkipPreviousButtons() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    Commands commands = new Commands.Builder().addAllCommands().build();
    SessionCommand customSessionCommand = new SessionCommand("", Bundle.EMPTY);
    CommandButton customCommandButton =
        new CommandButton.Builder()
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(customSessionCommand)
            .build();

    List<CommandButton> mediaButtons =
        defaultMediaNotificationProvider.getMediaButtons(
            commands, ImmutableList.of(customCommandButton), /* playWhenReady= */ true);

    assertThat(mediaButtons).hasSize(4);
    assertThat(mediaButtons.get(0).playerCommand)
        .isEqualTo(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    assertThat(mediaButtons.get(1).playerCommand).isEqualTo(Player.COMMAND_PLAY_PAUSE);
    assertThat(mediaButtons.get(2).playerCommand).isEqualTo(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);
    assertThat(mediaButtons.get(3)).isEqualTo(customCommandButton);
  }

  @Test
  public void getMediaButtons_noPlayerCommandsAvailable_onlyCustomLayoutButtons() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    Commands commands = new Commands.Builder().build();
    SessionCommand customSessionCommand = new SessionCommand("action1", Bundle.EMPTY);
    CommandButton customCommandButton =
        new CommandButton.Builder()
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(customSessionCommand)
            .build();

    List<CommandButton> mediaButtons =
        defaultMediaNotificationProvider.getMediaButtons(
            commands, ImmutableList.of(customCommandButton), /* playWhenReady= */ true);

    assertThat(mediaButtons).containsExactly(customCommandButton);
  }

  @Test
  public void addNotificationActions_customCompactViewDeclarations_correctCompactViewIndices() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    NotificationCompat.Builder mockNotificationBuilder = mock(NotificationCompat.Builder.class);
    MediaNotification.ActionFactory mockActionFactory = mock(MediaNotification.ActionFactory.class);
    MediaSession mockMediaSession = mock(MediaSession.class);
    CommandButton commandButton1 =
        new CommandButton.Builder()
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .build();
    Bundle commandButton2Bundle = new Bundle();
    commandButton2Bundle.putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 0);
    CommandButton commandButton2 =
        new CommandButton.Builder()
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
            .setExtras(commandButton2Bundle)
            .build();
    Bundle commandButton3Bundle = new Bundle();
    commandButton3Bundle.putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 2);
    CommandButton commandButton3 =
        new CommandButton.Builder()
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action3", Bundle.EMPTY))
            .setExtras(commandButton3Bundle)
            .build();
    Bundle commandButton4Bundle = new Bundle();
    commandButton4Bundle.putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 1);
    CommandButton commandButton4 =
        new CommandButton.Builder()
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action4", Bundle.EMPTY))
            .setExtras(commandButton4Bundle)
            .build();

    int[] compactViewIndices =
        defaultMediaNotificationProvider.addNotificationActions(
            mockMediaSession,
            ImmutableList.of(commandButton1, commandButton2, commandButton3, commandButton4),
            mockNotificationBuilder,
            mockActionFactory);

    verify(mockNotificationBuilder, times(4)).addAction(any());
    InOrder inOrder = Mockito.inOrder(mockActionFactory);
    inOrder
        .verify(mockActionFactory)
        .createMediaAction(
            eq(mockMediaSession), any(), eq("displayName"), eq(commandButton1.playerCommand));
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mockMediaSession, commandButton2);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mockMediaSession, commandButton3);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mockMediaSession, commandButton4);
    verifyNoMoreInteractions(mockActionFactory);
    assertThat(compactViewIndices).asList().containsExactly(1, 3, 2).inOrder();
  }

  @Test
  public void addNotificationActions_playPauseCommandNoCustomDeclaration_playPauseInCompactView() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    NotificationCompat.Builder mockNotificationBuilder = mock(NotificationCompat.Builder.class);
    MediaNotification.ActionFactory mockActionFactory = mock(MediaNotification.ActionFactory.class);
    MediaSession mockMediaSession = mock(MediaSession.class);
    CommandButton commandButton1 =
        new CommandButton.Builder()
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
            .build();
    CommandButton commandButton2 =
        new CommandButton.Builder()
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .build();

    int[] compactViewIndices =
        defaultMediaNotificationProvider.addNotificationActions(
            mockMediaSession,
            ImmutableList.of(commandButton1, commandButton2),
            mockNotificationBuilder,
            mockActionFactory);

    ArgumentCaptor<NotificationCompat.Action> actionCaptor =
        ArgumentCaptor.forClass(NotificationCompat.Action.class);
    verify(mockNotificationBuilder, times(2)).addAction(actionCaptor.capture());
    List<NotificationCompat.Action> actions = actionCaptor.getAllValues();
    assertThat(actions).hasSize(2);
    InOrder inOrder = Mockito.inOrder(mockActionFactory);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mockMediaSession, commandButton1);
    inOrder
        .verify(mockActionFactory)
        .createMediaAction(
            eq(mockMediaSession), any(), eq("displayName"), eq(commandButton2.playerCommand));
    verifyNoMoreInteractions(mockActionFactory);
    assertThat(compactViewIndices).asList().containsExactly(1);
  }

  @Test
  public void
      addNotificationActions_noPlayPauseCommandNoCustomDeclaration_emptyCompactViewIndices() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    NotificationCompat.Builder mockNotificationBuilder = mock(NotificationCompat.Builder.class);
    MediaNotification.ActionFactory mockActionFactory = mock(MediaNotification.ActionFactory.class);
    MediaSession mockMediaSession = mock(MediaSession.class);
    CommandButton commandButton1 =
        new CommandButton.Builder()
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
            .build();

    int[] compactViewIndices =
        defaultMediaNotificationProvider.addNotificationActions(
            mockMediaSession,
            ImmutableList.of(commandButton1),
            mockNotificationBuilder,
            mockActionFactory);

    InOrder inOrder = Mockito.inOrder(mockActionFactory);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mockMediaSession, commandButton1);
    verifyNoMoreInteractions(mockActionFactory);
    assertThat(compactViewIndices).asList().isEmpty();
  }

  @Test
  public void addNotificationActions_outOfBoundsCompactViewIndices_ignored() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    NotificationCompat.Builder mockNotificationBuilder = mock(NotificationCompat.Builder.class);
    MediaNotification.ActionFactory mockActionFactory = mock(MediaNotification.ActionFactory.class);
    MediaSession mockMediaSession = mock(MediaSession.class);
    Bundle commandButtonBundle1 = new Bundle();
    commandButtonBundle1.putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 2);
    CommandButton commandButton1 =
        new CommandButton.Builder()
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
            .setExtras(commandButtonBundle1)
            .build();
    Bundle commandButtonBundle2 = new Bundle();
    commandButtonBundle2.putInt(
        DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, -1);
    CommandButton commandButton2 =
        new CommandButton.Builder()
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
            .setExtras(commandButtonBundle2)
            .build();

    int[] compactViewIndices =
        defaultMediaNotificationProvider.addNotificationActions(
            mockMediaSession,
            ImmutableList.of(commandButton1, commandButton2),
            mockNotificationBuilder,
            mockActionFactory);

    InOrder inOrder = Mockito.inOrder(mockActionFactory);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mockMediaSession, commandButton1);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mockMediaSession, commandButton2);
    verifyNoMoreInteractions(mockActionFactory);
    assertThat(compactViewIndices).asList().isEmpty();
  }

  @Test
  public void addNotificationActions_unsetLeadingArrayFields_cropped() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    NotificationCompat.Builder mockNotificationBuilder = mock(NotificationCompat.Builder.class);
    MediaNotification.ActionFactory mockActionFactory = mock(MediaNotification.ActionFactory.class);
    MediaSession mockMediaSession = mock(MediaSession.class);
    Bundle commandButtonBundle = new Bundle();
    commandButtonBundle.putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 1);
    CommandButton commandButton1 =
        new CommandButton.Builder()
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
            .setExtras(commandButtonBundle)
            .build();

    int[] compactViewIndices =
        defaultMediaNotificationProvider.addNotificationActions(
            mockMediaSession,
            ImmutableList.of(commandButton1),
            mockNotificationBuilder,
            mockActionFactory);

    InOrder inOrder = Mockito.inOrder(mockActionFactory);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mockMediaSession, commandButton1);
    verifyNoMoreInteractions(mockActionFactory);
    // [INDEX_UNSET, 1, INDEX_UNSET] cropped up to the first INDEX_UNSET value
    assertThat(compactViewIndices).asList().isEmpty();
  }

  @Test
  public void addNotificationActions_correctNotificationActionAttributes() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    NotificationCompat.Builder mockNotificationBuilder = mock(NotificationCompat.Builder.class);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    MediaSession mockMediaSession = mock(MediaSession.class);
    MediaSessionImpl mockMediaSessionImpl = mock(MediaSessionImpl.class);
    when(mockMediaSession.getImpl()).thenReturn(mockMediaSessionImpl);
    when(mockMediaSessionImpl.getUri()).thenReturn(Uri.parse("http://example.com"));
    Bundle commandButtonBundle = new Bundle();
    commandButtonBundle.putString("testKey", "testValue");
    CommandButton commandButton1 =
        new CommandButton.Builder()
            .setDisplayName("displayName1")
            .setIconResId(R.drawable.media3_notification_play)
            .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
            .setExtras(commandButtonBundle)
            .build();

    defaultMediaNotificationProvider.addNotificationActions(
        mockMediaSession,
        ImmutableList.of(commandButton1),
        mockNotificationBuilder,
        defaultActionFactory);

    ArgumentCaptor<NotificationCompat.Action> actionCaptor =
        ArgumentCaptor.forClass(NotificationCompat.Action.class);
    verify(mockNotificationBuilder).addAction(actionCaptor.capture());
    verifyNoMoreInteractions(mockNotificationBuilder);
    verify(mockMediaSessionImpl).getUri();
    verifyNoMoreInteractions(mockMediaSessionImpl);
    List<NotificationCompat.Action> actions = actionCaptor.getAllValues();
    assertThat(actions).hasSize(1);
    assertThat(String.valueOf(actions.get(0).title)).isEqualTo("displayName1");
    assertThat(actions.get(0).getIconCompat().getResId()).isEqualTo(commandButton1.iconResId);
    assertThat(actions.get(0).getExtras().size()).isEqualTo(0);
  }

  /**
   * Tests that the {@link DefaultMediaNotificationProvider} will not request to load the same
   * artwork bitmap again, if the same bitmap has been requested already.
   */
  @Test
  public void requestsSameBitmap_withPendingRequest_oneRequestOnly() {
    // We will advance the main looper manually in the test.
    shadowOf(Looper.getMainLooper()).pause();
    // Create a MediaSession whose player returns non-null media metadata so that the
    // notification provider will request to load artwork bitmaps.
    MediaSession mockMediaSession =
        createMockMediaSessionForNotification(
            new MediaMetadata.Builder()
                .setArtworkUri(Uri.parse("http://example.test/image.jpg"))
                .build());
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    BitmapLoader mockBitmapLoader = mock(BitmapLoader.class);
    SettableFuture<Bitmap> bitmapFuture = SettableFuture.create();
    when(mockBitmapLoader.loadBitmap(any())).thenReturn(bitmapFuture);
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .setBitmapLoader(mockBitmapLoader)
            .build();

    // Ask the notification provider to create a notification twice. Use separate callback instances
    // for each notification so that we can distinguish for which notification we received a
    // callback.
    MediaNotification.Provider.Callback mockOnNotificationChangedCallback1 =
        mock(MediaNotification.Provider.Callback.class);
    defaultMediaNotificationProvider.createNotification(
        mockMediaSession,
        /* customLayout= */ ImmutableList.of(),
        defaultActionFactory,
        mockOnNotificationChangedCallback1);
    ShadowLooper.idleMainLooper();
    verify(mockBitmapLoader).loadBitmap(Uri.parse("http://example.test/image.jpg"));
    verifyNoInteractions(mockOnNotificationChangedCallback1);
    MediaNotification.Provider.Callback mockOnNotificationChangedCallback2 =
        mock(MediaNotification.Provider.Callback.class);
    defaultMediaNotificationProvider.createNotification(
        mockMediaSession,
        /* customLayout= */ ImmutableList.of(),
        defaultActionFactory,
        mockOnNotificationChangedCallback2);
    // The bitmap has arrived.
    bitmapFuture.set(Bitmap.createBitmap(/* width= */ 8, /* height= */ 8, Bitmap.Config.RGB_565));
    ShadowLooper.idleMainLooper();

    verifyNoMoreInteractions(mockBitmapLoader);
    verify(mockOnNotificationChangedCallback2).onNotificationChanged(any());
    verifyNoInteractions(mockOnNotificationChangedCallback1);
  }

  @Test
  public void provider_withCustomIds_notificationsUseCustomIds() {
    Context context = ApplicationProvider.getApplicationContext();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(context)
            .setNotificationId(/* notificationId= */ 2)
            .setChannelId(/* channelId= */ "customChannelId")
            .setChannelName(/* channelNameResourceId= */ R.string.media3_controls_play_description)
            .build();
    MediaSession mockMediaSession = createMockMediaSessionForNotification(MediaMetadata.EMPTY);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));

    MediaNotification notification =
        defaultMediaNotificationProvider.createNotification(
            mockMediaSession,
            ImmutableList.of(),
            defaultActionFactory,
            mock(MediaNotification.Provider.Callback.class));

    assertThat(notification.notificationId).isEqualTo(2);
    assertThat(notification.notification.getChannelId()).isEqualTo("customChannelId");
    ShadowNotificationManager shadowNotificationManager =
        Shadows.shadowOf(
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
    assertHasNotificationChannel(
        shadowNotificationManager.getNotificationChannels(),
        /* channelId= */ "customChannelId",
        /* channelName= */ context.getString(R.string.media3_controls_play_description));
  }

  @Test
  public void setCustomSmallIcon_notificationUsesCustomSmallIcon() {
    Context context = ApplicationProvider.getApplicationContext();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(context).build();
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    MediaSession mockMediaSession = createMockMediaSessionForNotification(MediaMetadata.EMPTY);

    MediaNotification notification =
        defaultMediaNotificationProvider.createNotification(
            mockMediaSession,
            ImmutableList.of(),
            defaultActionFactory,
            mock(MediaNotification.Provider.Callback.class));
    // Change the small icon.
    defaultMediaNotificationProvider.setSmallIcon(R.drawable.media3_icon_circular_play);
    MediaNotification notificationWithSmallIcon =
        defaultMediaNotificationProvider.createNotification(
            mockMediaSession,
            ImmutableList.of(),
            defaultActionFactory,
            mock(MediaNotification.Provider.Callback.class));

    assertThat(notification.notification.getSmallIcon().getResId())
        .isEqualTo(R.drawable.media3_notification_small_icon);
    assertThat(notificationWithSmallIcon.notification.getSmallIcon().getResId())
        .isEqualTo(R.drawable.media3_icon_circular_play);
  }

  private static void assertHasNotificationChannel(
      List<Object> notificationChannels, String channelId, String channelName) {
    boolean found = false;
    for (int i = 0; i < notificationChannels.size(); i++) {
      NotificationChannel notificationChannel = (NotificationChannel) notificationChannels.get(i);
      found =
          notificationChannel.getId().equals(channelId)
              // NotificationChannel.getName() is CharSequence. Use String#contentEquals instead
              // because CharSequence.equals() has undefined behavior.
              && channelName.contentEquals(notificationChannel.getName());
      if (found) {
        break;
      }
    }
    assertThat(found).isTrue();
  }

  private static MediaSession createMockMediaSessionForNotification(MediaMetadata mediaMetadata) {
    Player mockPlayer = mock(Player.class);
    when(mockPlayer.getAvailableCommands()).thenReturn(Commands.EMPTY);
    when(mockPlayer.getMediaMetadata()).thenReturn(mediaMetadata);
    MediaSession mockMediaSession = mock(MediaSession.class);
    when(mockMediaSession.getPlayer()).thenReturn(mockPlayer);
    MediaSessionImpl mockMediaSessionImpl = mock(MediaSessionImpl.class);
    when(mockMediaSession.getImpl()).thenReturn(mockMediaSessionImpl);
    when(mockMediaSessionImpl.getUri()).thenReturn(Uri.parse("https://example.test"));
    return mockMediaSession;
  }

  /** A test service for unit tests. */
  private static final class TestService extends MediaLibraryService {
    @Nullable
    @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
      return null;
    }
  }
}
