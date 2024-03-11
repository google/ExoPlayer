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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.session.DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID;
import static androidx.media3.session.DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Commands;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowNotificationManager;

/** Tests for {@link DefaultMediaNotificationProvider}. */
@RunWith(AndroidJUnit4.class)
public class DefaultMediaNotificationProviderTest {

  private final Context context = ApplicationProvider.getApplicationContext();
  private static final String TEST_CHANNEL_ID = "test_channel_id";
  private static final NotificationCompat.Action fakeAction =
      new NotificationCompat.Action(0, null, null);

  /**
   * The key string is defined as <a
   * href=https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:core/core/src/main/java/androidx/core/app/NotificationCompatJellybean.java?q=EXTRA_ALLOW_GENERATED_REPLIES>
   * {@code NotificationCompatJellybean.EXTRA_ALLOW_GENERATED_REPLIES}</a>
   */
  private static final String EXTRA_ALLOW_GENERATED_REPLIES =
      "android.support.allowGeneratedReplies";

  /**
   * The key string is defined as <a
   * href=https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:core/core/src/main/java/androidx/core/app/NotificationCompat.java?q=EXTRA_SHOWS_USER_INTERFACE>
   * {@code NotificationCompat.EXTRA_SHOWS_USER_INTERFACE}</a>
   */
  private static final String EXTRA_SHOWS_USER_INTERFACE =
      "android.support.action.showsUserInterface";

  /**
   * The key string is defined as <a
   * href=https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:core/core/src/main/java/androidx/core/app/NotificationCompat.java?q=EXTRA_SEMANTIC_ACTION>
   * {@code NotificationCompat.EXTRA_SEMANTIC_ACTION}</a>
   */
  private static final String EXTRA_SEMANTIC_ACTION = "android.support.action.semanticAction";

  @Mock private MediaNotification.ActionFactory mockActionFactory;

  @Before
  public void setUp() {
    mockActionFactory = mock(MediaNotification.ActionFactory.class);
    when(mockActionFactory.createCustomActionFromCustomCommandButton(any(), any()))
        .thenReturn(fakeAction);
    when(mockActionFactory.createMediaAction(any(), any(), any(), anyInt())).thenReturn(fakeAction);
  }

  @Test
  public void getMediaButtons_playWhenReadyTrueOrFalse_correctPlayPauseResources() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    Commands commands = new Commands.Builder().addAllCommands().build();
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();

    List<CommandButton> mediaButtonsWhenPlaying =
        defaultMediaNotificationProvider.getMediaButtons(
            mediaSession,
            commands,
            /* customLayout= */ ImmutableList.of(),
            /* showPauseButton= */ true);
    List<CommandButton> mediaButtonWhenPaused =
        defaultMediaNotificationProvider.getMediaButtons(
            mediaSession,
            commands,
            /* customLayout= */ ImmutableList.of(),
            /* showPauseButton= */ false);
    mediaSession.release();
    player.release();

    assertThat(mediaButtonsWhenPlaying).hasSize(3);
    assertThat(mediaButtonsWhenPlaying.get(1).playerCommand).isEqualTo(Player.COMMAND_PLAY_PAUSE);
    assertThat(mediaButtonsWhenPlaying.get(1).iconResId).isEqualTo(R.drawable.media3_icon_pause);
    assertThat(String.valueOf(mediaButtonsWhenPlaying.get(1).displayName)).isEqualTo("Pause");
    assertThat(mediaButtonWhenPaused).hasSize(3);
    assertThat(mediaButtonWhenPaused.get(1).playerCommand).isEqualTo(Player.COMMAND_PLAY_PAUSE);
    assertThat(mediaButtonWhenPaused.get(1).iconResId).isEqualTo(R.drawable.media3_icon_play);
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
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(customSessionCommand)
            .build();
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();

    List<CommandButton> mediaButtons =
        defaultMediaNotificationProvider.getMediaButtons(
            mediaSession,
            commands,
            ImmutableList.of(customCommandButton),
            /* showPauseButton= */ true);
    mediaSession.release();
    player.release();

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
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(customSessionCommand)
            .build();
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();

    List<CommandButton> mediaButtons =
        defaultMediaNotificationProvider.getMediaButtons(
            mediaSession,
            commands,
            ImmutableList.of(customCommandButton),
            /* showPauseButton= */ true);
    mediaSession.release();
    player.release();

    assertThat(mediaButtons).containsExactly(customCommandButton);
  }

  @Test
  public void addNotificationActions_customCompactViewDeclarations_correctCompactViewIndices() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    NotificationCompat.Builder notificationBuilder =
        new NotificationCompat.Builder(
            ApplicationProvider.getApplicationContext(), TEST_CHANNEL_ID);
    CommandButton commandButton1 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .build();
    Bundle commandButton2Bundle = new Bundle();
    commandButton2Bundle.putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 0);
    CommandButton commandButton2 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
            .setExtras(commandButton2Bundle)
            .build();
    Bundle commandButton3Bundle = new Bundle();
    commandButton3Bundle.putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 2);
    CommandButton commandButton3 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action3", Bundle.EMPTY))
            .setExtras(commandButton3Bundle)
            .build();
    Bundle commandButton4Bundle = new Bundle();
    commandButton4Bundle.putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 1);
    CommandButton commandButton4 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action4", Bundle.EMPTY))
            .setExtras(commandButton4Bundle)
            .build();
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();

    int[] compactViewIndices =
        defaultMediaNotificationProvider.addNotificationActions(
            mediaSession,
            ImmutableList.of(commandButton1, commandButton2, commandButton3, commandButton4),
            notificationBuilder,
            mockActionFactory);
    mediaSession.release();
    player.release();

    assertThat(notificationBuilder.build().actions).hasLength(4);
    InOrder inOrder = Mockito.inOrder(mockActionFactory);
    inOrder
        .verify(mockActionFactory)
        .createMediaAction(
            eq(mediaSession), any(), eq("displayName"), eq(commandButton1.playerCommand));
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mediaSession, commandButton2);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mediaSession, commandButton3);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mediaSession, commandButton4);
    verifyNoMoreInteractions(mockActionFactory);
    assertThat(compactViewIndices).asList().containsExactly(1, 3, 2).inOrder();
  }

  @Test
  public void
      addNotificationActions_playPauseSeekPrevSeekNextCommands_noCustomDeclaration_seekPrevPlayPauseSeekNextInCompactView() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    NotificationCompat.Builder notificationBuilder =
        new NotificationCompat.Builder(
            ApplicationProvider.getApplicationContext(), TEST_CHANNEL_ID);
    CommandButton commandButton1 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
            .build();
    CommandButton commandButton2 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .build();
    CommandButton commandButton3 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .build();
    CommandButton commandButton4 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .build();
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();

    int[] compactViewIndices =
        defaultMediaNotificationProvider.addNotificationActions(
            mediaSession,
            ImmutableList.of(commandButton1, commandButton2, commandButton3, commandButton4),
            notificationBuilder,
            mockActionFactory);
    mediaSession.release();
    player.release();

    assertThat(notificationBuilder.build().actions).hasLength(4);
    InOrder inOrder = Mockito.inOrder(mockActionFactory);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mediaSession, commandButton1);
    inOrder
        .verify(mockActionFactory)
        .createMediaAction(
            eq(mediaSession), any(), eq("displayName"), eq(commandButton2.playerCommand));
    inOrder
        .verify(mockActionFactory)
        .createMediaAction(
            eq(mediaSession), any(), eq("displayName"), eq(commandButton3.playerCommand));
    inOrder
        .verify(mockActionFactory)
        .createMediaAction(
            eq(mediaSession), any(), eq("displayName"), eq(commandButton4.playerCommand));
    verifyNoMoreInteractions(mockActionFactory);
    assertThat(compactViewIndices).asList().containsExactly(2, 1, 3);
  }

  @Test
  public void
      addNotificationActions_playPauseSeekPrevCommands_noCustomDeclaration_seekPrevPlayPauseInCompactView() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    NotificationCompat.Builder notificationBuilder =
        new NotificationCompat.Builder(
            ApplicationProvider.getApplicationContext(), TEST_CHANNEL_ID);
    CommandButton commandButton1 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
            .build();
    CommandButton commandButton2 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .build();
    CommandButton commandButton3 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .build();
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();

    int[] compactViewIndices =
        defaultMediaNotificationProvider.addNotificationActions(
            mediaSession,
            ImmutableList.of(commandButton1, commandButton2, commandButton3),
            notificationBuilder,
            mockActionFactory);
    mediaSession.release();
    player.release();

    assertThat(notificationBuilder.build().actions).hasLength(3);
    InOrder inOrder = Mockito.inOrder(mockActionFactory);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mediaSession, commandButton1);
    inOrder
        .verify(mockActionFactory)
        .createMediaAction(
            eq(mediaSession), any(), eq("displayName"), eq(commandButton2.playerCommand));
    inOrder
        .verify(mockActionFactory)
        .createMediaAction(
            eq(mediaSession), any(), eq("displayName"), eq(commandButton3.playerCommand));
    verifyNoMoreInteractions(mockActionFactory);
    assertThat(compactViewIndices).asList().containsExactly(2, 1);
  }

  @Test
  public void
      addNotificationActions_noPlayPauseSeekPrevSeekNextCommands_noCustomDeclaration_emptyCompactViewIndices() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    NotificationCompat.Builder notificationBuilder =
        new NotificationCompat.Builder(
            ApplicationProvider.getApplicationContext(), TEST_CHANNEL_ID);
    CommandButton commandButton1 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
            .build();
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();

    int[] compactViewIndices =
        defaultMediaNotificationProvider.addNotificationActions(
            mediaSession, ImmutableList.of(commandButton1), notificationBuilder, mockActionFactory);
    mediaSession.release();
    player.release();

    InOrder inOrder = Mockito.inOrder(mockActionFactory);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mediaSession, commandButton1);
    verifyNoMoreInteractions(mockActionFactory);
    assertThat(compactViewIndices).asList().isEmpty();
  }

  @Test
  public void addNotificationActions_outOfBoundsCompactViewIndices_ignored() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    NotificationCompat.Builder notificationBuilder =
        new NotificationCompat.Builder(
            ApplicationProvider.getApplicationContext(), TEST_CHANNEL_ID);
    Bundle commandButtonBundle1 = new Bundle();
    commandButtonBundle1.putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 2);
    CommandButton commandButton1 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
            .setExtras(commandButtonBundle1)
            .build();
    Bundle commandButtonBundle2 = new Bundle();
    commandButtonBundle2.putInt(
        DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, -1);
    CommandButton commandButton2 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("displayName2")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
            .setExtras(commandButtonBundle2)
            .build();
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();

    int[] compactViewIndices =
        defaultMediaNotificationProvider.addNotificationActions(
            mediaSession,
            /* mediaButtons= */ ImmutableList.of(commandButton1, commandButton2),
            notificationBuilder,
            mockActionFactory);
    mediaSession.release();
    player.release();

    InOrder inOrder = Mockito.inOrder(mockActionFactory);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mediaSession, commandButton1);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mediaSession, commandButton2);
    verifyNoMoreInteractions(mockActionFactory);
    assertThat(compactViewIndices).asList().isEmpty();
  }

  @Test
  public void addNotificationActions_unsetLeadingArrayFields_cropped() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    NotificationCompat.Builder notificationBuilder =
        new NotificationCompat.Builder(
            ApplicationProvider.getApplicationContext(), TEST_CHANNEL_ID);
    Bundle commandButtonBundle = new Bundle();
    commandButtonBundle.putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 1);
    CommandButton commandButton1 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("displayName")
            .setIconResId(R.drawable.media3_icon_circular_play)
            .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
            .setExtras(commandButtonBundle)
            .build();
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();

    int[] compactViewIndices =
        defaultMediaNotificationProvider.addNotificationActions(
            mediaSession, ImmutableList.of(commandButton1), notificationBuilder, mockActionFactory);
    mediaSession.release();
    player.release();

    InOrder inOrder = Mockito.inOrder(mockActionFactory);
    inOrder
        .verify(mockActionFactory)
        .createCustomActionFromCustomCommandButton(mediaSession, commandButton1);
    verifyNoMoreInteractions(mockActionFactory);
    // [INDEX_UNSET, 1, INDEX_UNSET] cropped up to the first INDEX_UNSET value
    assertThat(compactViewIndices).asList().isEmpty();
  }

  @Test
  public void addNotificationActions_correctNotificationActionAttributes() {
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();
    NotificationCompat.Builder notificationBuilder =
        new NotificationCompat.Builder(
            ApplicationProvider.getApplicationContext(), TEST_CHANNEL_ID);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    Bundle commandButtonBundle = new Bundle();
    commandButtonBundle.putString("testKey", "testValue");
    CommandButton commandButton1 =
        new CommandButton.Builder(CommandButton.ICON_PLAY)
            .setDisplayName("displayName1")
            .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
            .setExtras(commandButtonBundle)
            .build();
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();

    defaultMediaNotificationProvider.addNotificationActions(
        mediaSession, ImmutableList.of(commandButton1), notificationBuilder, defaultActionFactory);
    mediaSession.release();
    player.release();

    Notification.Action[] actions = notificationBuilder.build().actions;
    assertThat(actions).hasLength(1);
    assertThat(String.valueOf(actions[0].title)).isEqualTo("displayName1");
    assertThat(actions[0].getIcon().getResId()).isEqualTo(commandButton1.iconResId);
    Bundle extrasInAction = actions[0].getExtras();
    // Remove platform extras added during the construction of Notification.Action.
    extrasInAction.remove(EXTRA_ALLOW_GENERATED_REPLIES);
    extrasInAction.remove(EXTRA_SHOWS_USER_INTERFACE);
    extrasInAction.remove(EXTRA_SEMANTIC_ACTION);
    assertThat(extrasInAction.size()).isEqualTo(0);
  }

  /**
   * Tests that the {@link DefaultMediaNotificationProvider} will discard the pending {@link
   * MediaNotification.Provider.Callback#onNotificationChanged(MediaNotification)}, if there is a
   * new request.
   */
  @Test
  public void createNotification_withNewRequest_discardPendingCallback() {
    // We will advance the main looper manually in the test.
    shadowOf(Looper.getMainLooper()).pause();
    // Create a MediaSession whose player returns non-null media metadata so that the
    // notification provider will request to load artwork bitmaps.
    Player player =
        createPlayerWithMetadata(
            new MediaMetadata.Builder()
                .setArtworkUri(Uri.parse("http://example.test/image.jpg"))
                .build());
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    BitmapLoader mockBitmapLoader = mock(BitmapLoader.class);
    SettableFuture<Bitmap> bitmapFuture = SettableFuture.create();
    when(mockBitmapLoader.loadBitmapFromMetadata(any())).thenReturn(bitmapFuture);
    MediaSession mediaSession =
        new MediaSession.Builder(context, player).setBitmapLoader(mockBitmapLoader).build();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();

    // Ask the notification provider to create a notification twice. Use separate callback instances
    // for each notification so that we can distinguish for which notification we received a
    // callback.
    MediaNotification.Provider.Callback mockOnNotificationChangedCallback1 =
        mock(MediaNotification.Provider.Callback.class);
    defaultMediaNotificationProvider.createNotification(
        mediaSession,
        /* customLayout= */ ImmutableList.of(),
        defaultActionFactory,
        mockOnNotificationChangedCallback1);
    ShadowLooper.idleMainLooper();
    verifyNoInteractions(mockOnNotificationChangedCallback1);
    MediaNotification.Provider.Callback mockOnNotificationChangedCallback2 =
        mock(MediaNotification.Provider.Callback.class);
    defaultMediaNotificationProvider.createNotification(
        mediaSession,
        /* customLayout= */ ImmutableList.of(),
        defaultActionFactory,
        mockOnNotificationChangedCallback2);
    // The bitmap has arrived.
    bitmapFuture.set(Bitmap.createBitmap(/* width= */ 8, /* height= */ 8, Bitmap.Config.RGB_565));
    ShadowLooper.idleMainLooper();
    mediaSession.release();
    player.release();

    verify(mockOnNotificationChangedCallback2).onNotificationChanged(any());
    verifyNoInteractions(mockOnNotificationChangedCallback1);
  }

  @Test
  public void createNotification_invalidButtons_enabledSessionCommandsOnlyForGetMediaButtons() {
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    List<CommandButton> filteredEnabledLayout = new ArrayList<>();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider(ApplicationProvider.getApplicationContext()) {
          @Override
          protected ImmutableList<CommandButton> getMediaButtons(
              MediaSession session,
              Commands playerCommands,
              ImmutableList<CommandButton> customLayout,
              boolean showPauseButton) {
            filteredEnabledLayout.addAll(customLayout);
            return super.getMediaButtons(session, playerCommands, customLayout, showPauseButton);
          }
        };
    MediaSession mediaSession =
        new MediaSession.Builder(
                ApplicationProvider.getApplicationContext(),
                new TestExoPlayerBuilder(context).build())
            .build();
    CommandButton button1 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button1")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build();
    CommandButton button2 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button2")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(new SessionCommand("command2", Bundle.EMPTY))
            .build()
            .copyWithIsEnabled(true);
    CommandButton button3 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button3")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .build()
            .copyWithIsEnabled(true);

    defaultMediaNotificationProvider.createNotification(
        mediaSession,
        /* customLayout= */ ImmutableList.of(button1, button2, button3),
        defaultActionFactory,
        notification -> {
          /* Do nothing. */
        });

    assertThat(filteredEnabledLayout).containsExactly(button2);
    mediaSession.getPlayer().release();
    mediaSession.release();
  }

  @Test
  public void
      createNotification_withStateReadyAndPlayWhenReadyTrueAndNoSuppression_showsPauseButton() {
    Player player =
        createPlayerWithFixedState(
            Player.STATE_READY, /* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();

    MediaNotification mediaNotification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            /* customLayout= */ ImmutableList.of(),
            defaultActionFactory,
            notification -> {});
    mediaSession.release();

    assertThat(mediaNotification.notification.actions[0].title.toString())
        .isEqualTo(context.getString(R.string.media3_controls_pause_description));
  }

  @Test
  public void
      createNotification_withStateReadyAndPlayWhenReadyTrueAndPlaybackSuppression_showsPlayButton() {
    Player player =
        createPlayerWithFixedState(
            Player.STATE_READY,
            /* playWhenReady= */ true,
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();

    MediaNotification mediaNotification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            /* customLayout= */ ImmutableList.of(),
            defaultActionFactory,
            notification -> {});
    mediaSession.release();

    assertThat(mediaNotification.notification.actions[0].title.toString())
        .isEqualTo(context.getString(R.string.media3_controls_play_description));
  }

  @Test
  public void
      createNotification_withStateReadyAndPlayWhenReadyTrueAndPlaybackSuppressionWithoutShowPauseIfSuppressed_showsPauseButton() {
    Player player =
        createPlayerWithFixedState(
            Player.STATE_READY,
            /* playWhenReady= */ true,
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    MediaSession mediaSession =
        new MediaSession.Builder(context, player)
            .setShowPlayButtonIfPlaybackIsSuppressed(false)
            .build();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();

    MediaNotification mediaNotification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            /* customLayout= */ ImmutableList.of(),
            defaultActionFactory,
            notification -> {});
    mediaSession.release();

    assertThat(mediaNotification.notification.actions[0].title.toString())
        .isEqualTo(context.getString(R.string.media3_controls_pause_description));
  }

  @Test
  public void
      createNotification_withStateBufferingAndPlayWhenReadyTrueAndNoSuppression_showsPauseButton() {
    Player player =
        createPlayerWithFixedState(
            Player.STATE_BUFFERING,
            /* playWhenReady= */ true,
            Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();

    MediaNotification mediaNotification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            /* customLayout= */ ImmutableList.of(),
            defaultActionFactory,
            notification -> {});
    mediaSession.release();

    assertThat(mediaNotification.notification.actions[0].title.toString())
        .isEqualTo(context.getString(R.string.media3_controls_pause_description));
  }

  @Test
  public void
      createNotification_withStateBufferingAndPlayWhenReadyTrueAndPlaybackSuppression_showsPlayButton() {
    Player player =
        createPlayerWithFixedState(
            Player.STATE_BUFFERING,
            /* playWhenReady= */ true,
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();

    MediaNotification mediaNotification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            /* customLayout= */ ImmutableList.of(),
            defaultActionFactory,
            notification -> {});
    mediaSession.release();

    assertThat(mediaNotification.notification.actions[0].title.toString())
        .isEqualTo(context.getString(R.string.media3_controls_play_description));
  }

  @Test
  public void
      createNotification_withStateBufferingAndPlayWhenReadyTrueAndPlaybackSuppressionWithoutShowPauseIfSuppressed_showsPauseButton() {
    Player player =
        createPlayerWithFixedState(
            Player.STATE_BUFFERING,
            /* playWhenReady= */ true,
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    MediaSession mediaSession =
        new MediaSession.Builder(context, player)
            .setShowPlayButtonIfPlaybackIsSuppressed(false)
            .build();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();

    MediaNotification mediaNotification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            /* customLayout= */ ImmutableList.of(),
            defaultActionFactory,
            notification -> {});
    mediaSession.release();

    assertThat(mediaNotification.notification.actions[0].title.toString())
        .isEqualTo(context.getString(R.string.media3_controls_pause_description));
  }

  @Test
  public void createNotification_withStateReadyAndPlayWhenReadyFalse_showsPlayButton() {
    Player player =
        createPlayerWithFixedState(
            Player.STATE_READY,
            /* playWhenReady= */ false,
            Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();

    MediaNotification mediaNotification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            /* customLayout= */ ImmutableList.of(),
            defaultActionFactory,
            notification -> {});
    mediaSession.release();

    assertThat(mediaNotification.notification.actions[0].title.toString())
        .isEqualTo(context.getString(R.string.media3_controls_play_description));
  }

  @Test
  public void createNotification_withStateBufferingAndPlayWhenReadyFalse_showsPlayButton() {
    Player player =
        createPlayerWithFixedState(
            Player.STATE_BUFFERING,
            /* playWhenReady= */ false,
            Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();

    MediaNotification mediaNotification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            /* customLayout= */ ImmutableList.of(),
            defaultActionFactory,
            notification -> {});
    mediaSession.release();

    assertThat(mediaNotification.notification.actions[0].title.toString())
        .isEqualTo(context.getString(R.string.media3_controls_play_description));
  }

  @Test
  public void createNotification_withStateEndedAndPlayWhenReadyTrue_showsPlayButton() {
    Player player =
        createPlayerWithFixedState(
            Player.STATE_ENDED, /* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();

    MediaNotification mediaNotification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            /* customLayout= */ ImmutableList.of(),
            defaultActionFactory,
            notification -> {});
    mediaSession.release();

    assertThat(mediaNotification.notification.actions[0].title.toString())
        .isEqualTo(context.getString(R.string.media3_controls_play_description));
  }

  @Test
  public void createNotification_withStateEndedAndPlayWhenReadyFalse_showsPlayButton() {
    Player player =
        createPlayerWithFixedState(
            Player.STATE_ENDED, /* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();

    MediaNotification mediaNotification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            /* customLayout= */ ImmutableList.of(),
            defaultActionFactory,
            notification -> {});
    mediaSession.release();

    assertThat(mediaNotification.notification.actions[0].title.toString())
        .isEqualTo(context.getString(R.string.media3_controls_play_description));
  }

  @Test
  public void createNotification_withStateIdleAndPlayWhenReadyTrue_showsPlayButton() {
    Player player =
        createPlayerWithFixedState(
            Player.STATE_IDLE, /* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();

    MediaNotification mediaNotification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            /* customLayout= */ ImmutableList.of(),
            defaultActionFactory,
            notification -> {});
    mediaSession.release();

    assertThat(mediaNotification.notification.actions[0].title.toString())
        .isEqualTo(context.getString(R.string.media3_controls_play_description));
  }

  @Test
  public void createNotification_withStateIdleAndPlayWhenReadyFalse_showsPlayButton() {
    Player player =
        createPlayerWithFixedState(
            Player.STATE_IDLE, /* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    MediaSession mediaSession = new MediaSession.Builder(context, player).build();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(ApplicationProvider.getApplicationContext())
            .build();

    MediaNotification mediaNotification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            /* customLayout= */ ImmutableList.of(),
            defaultActionFactory,
            notification -> {});
    mediaSession.release();

    assertThat(mediaNotification.notification.actions[0].title.toString())
        .isEqualTo(context.getString(R.string.media3_controls_play_description));
  }

  @Test
  public void provider_idsNotSpecified_usesDefaultIds() {
    Context context = ApplicationProvider.getApplicationContext();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(context).build();
    BitmapLoader mockBitmapLoader = mock(BitmapLoader.class);
    when(mockBitmapLoader.loadBitmapFromMetadata(any())).thenReturn(null);
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession =
        new MediaSession.Builder(context, player).setBitmapLoader(mockBitmapLoader).build();
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));

    MediaNotification notification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            ImmutableList.of(),
            defaultActionFactory,
            mock(MediaNotification.Provider.Callback.class));
    mediaSession.release();
    player.release();

    assertThat(notification.notificationId).isEqualTo(DEFAULT_NOTIFICATION_ID);
    assertThat(notification.notification.getChannelId()).isEqualTo(DEFAULT_CHANNEL_ID);
    ShadowNotificationManager shadowNotificationManager =
        Shadows.shadowOf(
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
    assertHasNotificationChannel(
        shadowNotificationManager.getNotificationChannels(),
        /* channelId= */ DEFAULT_CHANNEL_ID,
        /* channelName= */ context.getString(R.string.default_notification_channel_name));
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
    BitmapLoader mockBitmapLoader = mock(BitmapLoader.class);
    when(mockBitmapLoader.loadBitmapFromMetadata(any())).thenReturn(null);
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession =
        new MediaSession.Builder(context, player).setBitmapLoader(mockBitmapLoader).build();
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));

    MediaNotification notification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            ImmutableList.of(),
            defaultActionFactory,
            mock(MediaNotification.Provider.Callback.class));
    mediaSession.release();
    player.release();

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
  public void provider_withCustomNotificationIdProvider_notificationsUseCustomId() {
    Context context = ApplicationProvider.getApplicationContext();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(context)
            .setNotificationIdProvider(
                session -> {
                  checkNotNull(session);
                  return 3;
                })
            .setChannelName(/* channelNameResourceId= */ R.string.media3_controls_play_description)
            .build();
    BitmapLoader mockBitmapLoader = mock(BitmapLoader.class);
    when(mockBitmapLoader.loadBitmapFromMetadata(any())).thenReturn(null);
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession =
        new MediaSession.Builder(context, player).setBitmapLoader(mockBitmapLoader).build();
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));

    MediaNotification notification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            ImmutableList.of(),
            defaultActionFactory,
            mock(MediaNotification.Provider.Callback.class));
    mediaSession.release();
    player.release();

    assertThat(notification.notificationId).isEqualTo(3);
  }

  @Test
  public void setCustomSmallIcon_notificationUsesCustomSmallIcon() {
    Context context = ApplicationProvider.getApplicationContext();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(context).build();
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    BitmapLoader mockBitmapLoader = mock(BitmapLoader.class);
    when(mockBitmapLoader.loadBitmapFromMetadata(any())).thenReturn(null);
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession =
        new MediaSession.Builder(context, player).setBitmapLoader(mockBitmapLoader).build();

    MediaNotification notification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            ImmutableList.of(),
            defaultActionFactory,
            mock(MediaNotification.Provider.Callback.class));
    // Change the small icon.
    defaultMediaNotificationProvider.setSmallIcon(R.drawable.media3_icon_circular_play);
    MediaNotification notificationWithSmallIcon =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            ImmutableList.of(),
            defaultActionFactory,
            mock(MediaNotification.Provider.Callback.class));
    mediaSession.release();
    player.release();

    assertThat(notification.notification.getSmallIcon().getResId())
        .isEqualTo(R.drawable.media3_notification_small_icon);
    assertThat(notificationWithSmallIcon.notification.getSmallIcon().getResId())
        .isEqualTo(R.drawable.media3_icon_circular_play);
  }

  @Test
  public void setMediaMetadataTitle_notificationUsesItAsContentTitle() {
    Context context = ApplicationProvider.getApplicationContext();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(context).build();
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    Player player = createPlayerWithMetadata(new MediaMetadata.Builder().setTitle("title").build());
    BitmapLoader mockBitmapLoader = mock(BitmapLoader.class);
    when(mockBitmapLoader.loadBitmapFromMetadata(any())).thenReturn(null);
    MediaSession mediaSession =
        new MediaSession.Builder(context, player).setBitmapLoader(mockBitmapLoader).build();

    MediaNotification notification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            ImmutableList.of(),
            defaultActionFactory,
            mock(MediaNotification.Provider.Callback.class));
    mediaSession.release();
    player.release();

    boolean isMediaMetadataTitleEqualToNotificationContentTitle =
        "title".contentEquals(NotificationCompat.getContentTitle(notification.notification));
    assertThat(isMediaMetadataTitleEqualToNotificationContentTitle).isTrue();
  }

  @Test
  public void setMediaMetadataArtist_notificationUsesItAsContentText() {
    Context context = ApplicationProvider.getApplicationContext();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(context).build();
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    Player player =
        createPlayerWithMetadata(new MediaMetadata.Builder().setArtist("artist").build());
    BitmapLoader mockBitmapLoader = mock(BitmapLoader.class);
    when(mockBitmapLoader.loadBitmapFromMetadata(any())).thenReturn(null);
    MediaSession mediaSession =
        new MediaSession.Builder(context, player).setBitmapLoader(mockBitmapLoader).build();

    MediaNotification notification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            ImmutableList.of(),
            defaultActionFactory,
            mock(MediaNotification.Provider.Callback.class));
    mediaSession.release();
    player.release();

    boolean isMediaMetadataArtistEqualToNotificationContentText =
        "artist".contentEquals(NotificationCompat.getContentText(notification.notification));
    assertThat(isMediaMetadataArtistEqualToNotificationContentText).isTrue();
  }

  @Test
  public void
      setMediaMetadata_withoutAvailableCommandToGetMetadata_doesNotUseMetadataForNotification() {
    Context context = ApplicationProvider.getApplicationContext();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider.Builder(context).build();
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    Player player =
        createPlayerWithMetadata(
            new MediaMetadata.Builder().setArtist("artist").setTitle("title").build(),
            /* isMetadataCommandAvailable= */ false);
    BitmapLoader mockBitmapLoader = mock(BitmapLoader.class);
    when(mockBitmapLoader.loadBitmapFromMetadata(any())).thenReturn(null);
    MediaSession mediaSession =
        new MediaSession.Builder(context, player).setBitmapLoader(mockBitmapLoader).build();

    MediaNotification notification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            ImmutableList.of(),
            defaultActionFactory,
            mock(MediaNotification.Provider.Callback.class));
    mediaSession.release();
    player.release();

    assertThat(NotificationCompat.getContentText(notification.notification)).isNull();
    assertThat(NotificationCompat.getContentTitle(notification.notification)).isNull();
  }

  /**
   * {@link DefaultMediaNotificationProvider} is designed to be extendable. Public constructor
   * should not be removed.
   */
  @Test
  public void createsProviderUsingConstructor_idsNotSpecified_usesDefaultIds() {
    Context context = ApplicationProvider.getApplicationContext();
    DefaultMediaNotificationProvider defaultMediaNotificationProvider =
        new DefaultMediaNotificationProvider(context);
    BitmapLoader mockBitmapLoader = mock(BitmapLoader.class);
    when(mockBitmapLoader.loadBitmapFromMetadata(any())).thenReturn(null);
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession mediaSession =
        new MediaSession.Builder(context, player).setBitmapLoader(mockBitmapLoader).build();
    DefaultActionFactory defaultActionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));

    MediaNotification notification =
        defaultMediaNotificationProvider.createNotification(
            mediaSession,
            /* customLayout= */ ImmutableList.of(),
            defaultActionFactory,
            /* onNotificationChangedCallback= */ mock(MediaNotification.Provider.Callback.class));
    mediaSession.release();
    player.release();

    assertThat(notification.notificationId).isEqualTo(DEFAULT_NOTIFICATION_ID);
    assertThat(notification.notification.getChannelId()).isEqualTo(DEFAULT_CHANNEL_ID);
    ShadowNotificationManager shadowNotificationManager =
        Shadows.shadowOf(
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
    assertHasNotificationChannel(
        shadowNotificationManager.getNotificationChannels(),
        /* channelId= */ DEFAULT_CHANNEL_ID,
        /* channelName= */ context.getString(R.string.default_notification_channel_name));
  }

  /**
   * Extends {@link DefaultMediaNotificationProvider} and overrides all known protected methods. If
   * by accident we change the signature of the class in a way that affects inheritance, this test
   * would no longer compile.
   */
  @Test
  public void overridesProviderDefinition_compilesSuccessfully() {
    Context context = ApplicationProvider.getApplicationContext();

    DefaultMediaNotificationProvider unused =
        new DefaultMediaNotificationProvider(context) {
          @Override
          public ImmutableList<CommandButton> getMediaButtons(
              MediaSession mediaSession,
              Player.Commands playerCommands,
              ImmutableList<CommandButton> customLayout,
              boolean showPauseButton) {
            return super.getMediaButtons(
                mediaSession, playerCommands, customLayout, showPauseButton);
          }

          @Override
          public int[] addNotificationActions(
              MediaSession mediaSession,
              ImmutableList<CommandButton> mediaButtons,
              NotificationCompat.Builder builder,
              MediaNotification.ActionFactory actionFactory) {
            return super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory);
          }

          @Override
          public CharSequence getNotificationContentTitle(MediaMetadata metadata) {
            return super.getNotificationContentTitle(metadata);
          }

          @Override
          public CharSequence getNotificationContentText(MediaMetadata metadata) {
            return super.getNotificationContentText(metadata);
          }
        };
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

  private Player createPlayerWithMetadata(MediaMetadata mediaMetadata) {
    return createPlayerWithMetadata(mediaMetadata, /* isMetadataCommandAvailable= */ true);
  }

  private Player createPlayerWithMetadata(
      MediaMetadata mediaMetadata, boolean isMetadataCommandAvailable) {
    return new ForwardingPlayer(new TestExoPlayerBuilder(context).build()) {
      @Override
      public boolean isCommandAvailable(int command) {
        return isMetadataCommandAvailable || command != Player.COMMAND_GET_METADATA;
      }

      @Override
      public Commands getAvailableCommands() {
        Commands.Builder commandsBuilder = new Commands.Builder().addAllCommands();
        if (!isMetadataCommandAvailable) {
          commandsBuilder.remove(Player.COMMAND_GET_METADATA);
        }
        return commandsBuilder.build();
      }

      @Override
      public MediaMetadata getMediaMetadata() {
        return isMetadataCommandAvailable ? mediaMetadata : MediaMetadata.EMPTY;
      }
    };
  }

  private static Player createPlayerWithFixedState(
      @Player.State int playbackState,
      boolean playWhenReady,
      @Player.PlaybackSuppressionReason int suppressionReason) {
    return new SimpleBasePlayer(Looper.getMainLooper()) {
      @Override
      protected State getState() {
        return new State.Builder()
            .setAvailableCommands(new Commands.Builder().add(Player.COMMAND_PLAY_PAUSE).build())
            .setPlaylist(
                ImmutableList.of(new MediaItemData.Builder(/* uid= */ new Object()).build()))
            .setPlaybackState(playbackState)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackSuppressionReason(suppressionReason)
            .build();
      }

      @Override
      protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
        // Do nothing.
        return Futures.immediateVoidFuture();
      }
    };
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
