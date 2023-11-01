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

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ADVERTISEMENT;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_URI;
import static androidx.media3.common.PlaybackException.ERROR_CODE_REMOTE_ERROR;
import static androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO;
import static androidx.media3.common.Player.STATE_BUFFERING;
import static androidx.media3.common.Player.STATE_READY;
import static androidx.media3.session.MediaConstants.ARGUMENT_CAPTIONING_ENABLED;
import static androidx.media3.session.MediaConstants.SESSION_COMMAND_ON_CAPTIONING_ENABLED_CHANGED;
import static androidx.media3.session.SessionResult.RESULT_SUCCESS;
import static androidx.media3.test.session.common.CommonConstants.DEFAULT_TEST_NAME;
import static androidx.media3.test.session.common.CommonConstants.METADATA_ALBUM_TITLE;
import static androidx.media3.test.session.common.CommonConstants.METADATA_ARTIST;
import static androidx.media3.test.session.common.CommonConstants.METADATA_DESCRIPTION;
import static androidx.media3.test.session.common.CommonConstants.METADATA_TITLE;
import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.session.PlaybackStateCompat.CustomAction;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media.VolumeProviderCompat;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.DiscontinuityReason;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Player.State;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSourceBitmapLoader;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.MockActivity;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.ext.truth.os.BundleSubject;
import androidx.test.filters.MediumTest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/** Tests for {@link MediaController} interacting with {@link MediaSessionCompat}. */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MediaControllerWithMediaSessionCompatTest {

  private static final String TAG = "MCwMSCTest";

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private static final String TEST_IMAGE_PATH = "media/png/non-motion-photo-shortened.png";

  private final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);
  private final MediaControllerTestRule controllerTestRule =
      new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  private Context context;
  private RemoteMediaSessionCompat session;
  private BitmapLoader bitmapLoader;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    session = new RemoteMediaSessionCompat(DEFAULT_TEST_NAME, context);
    bitmapLoader = new CacheBitmapLoader(new DataSourceBitmapLoader(context));
  }

  @After
  public void cleanUp() throws Exception {
    session.cleanUp();
  }

  @Test
  public void connected() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    assertThat(controller.isConnected()).isTrue();
  }

  @Test
  public void setPlaybackSpeed() throws Exception {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PAUSED,
                /* position= */ 10_000L,
                /* playbackSpeed= */ 1.0f)
            .setActions(PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED)
            .build();
    session.setPlaybackState(playbackStateCompat);
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch countDownLatch = new CountDownLatch(1);
    AtomicReference<PlaybackParameters> parametersRef = new AtomicReference<>();
    controller.addListener(
        new Player.Listener() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            parametersRef.set(playbackParameters);
            countDownLatch.countDown();
          }
        });

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              assertThat(
                      controller
                          .getAvailableCommands()
                          .contains(Player.COMMAND_SET_SPEED_AND_PITCH))
                  .isTrue();
              controller.setPlaybackSpeed(2.0f);
            });

    assertThat(countDownLatch.await(1000, MILLISECONDS)).isTrue();
    assertThat(parametersRef.get().speed).isEqualTo(2.0f);
  }

  @Test
  public void setPlaybackSpeed_actionSetPlaybackSpeedNotAvailable_commandNotAvailable()
      throws Exception {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, 10_000L, /* playbackSpeed= */ 1.0f)
            .setActions(PlaybackStateCompat.ACTION_PAUSE)
            .build();
    session.setPlaybackState(playbackStateCompat);
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    threadTestRule
        .getHandler()
        .postAndSync(
            () ->
                assertThat(
                        controller
                            .getAvailableCommands()
                            .contains(Player.COMMAND_SET_SPEED_AND_PITCH))
                    .isFalse());
  }

  @Test
  public void disconnected_bySessionRelease() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MediaController controller =
        controllerTestRule.createController(
            session.getSessionToken(),
            new MediaController.Listener() {
              @Override
              public void onDisconnected(MediaController controller) {
                latch.countDown();
              }
            });
    session.release();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controller.isConnected()).isFalse();
  }

  @Test
  public void disconnected_byControllerRelease() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MediaController controller =
        controllerTestRule.createController(
            session.getSessionToken(),
            new MediaController.Listener() {
              @Override
              public void onDisconnected(MediaController controller) {
                latch.countDown();
              }
            });
    threadTestRule.getHandler().postAndSync(controller::release);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controller.isConnected()).isFalse();
  }

  @Test
  public void disconnected_byControllerReleaseRightAfterCreated() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Exception> exception = new AtomicReference<>();
    MediaController controller =
        controllerTestRule.createController(
            session.getSessionToken(),
            new MediaController.Listener() {
              @Override
              public void onDisconnected(MediaController controller) {
                latch.countDown();
              }
            },
            /* controllerCreationListener= */ mediaController -> {
              // We must release the controller on the app thread.
              try {
                threadTestRule.getHandler().postAndSync(() -> mediaController.release());
              } catch (Exception e) {
                exception.set(e);
              }
            });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(exception.get()).isNull();
    assertThat(controller.isConnected()).isFalse();
  }

  @Test
  public void close_twice_doesNotCrash() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    threadTestRule.getHandler().postAndSync(controller::release);
    threadTestRule.getHandler().postAndSync(controller::release);
  }

  @Test
  public void gettersAfterConnected() throws Exception {
    long position = 150_000;
    long bufferedPosition = 900_000;
    long duration = 1_000_000;
    float speed = 1.5f;
    CharSequence queueTitle = "queueTitle";
    @PlaybackStateCompat.ShuffleMode int shuffleMode = PlaybackStateCompat.SHUFFLE_MODE_GROUP;
    @PlaybackStateCompat.RepeatMode int repeatMode = PlaybackStateCompat.REPEAT_MODE_GROUP;
    boolean isPlayingAd = true;

    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder()
            .putString(METADATA_KEY_MEDIA_ID, "gettersAfterConnected")
            .putLong(METADATA_KEY_DURATION, duration)
            .putLong(METADATA_KEY_ADVERTISEMENT, isPlayingAd ? 1 : 0)
            .build();

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PLAYING, position, speed)
            .setBufferedPosition(bufferedPosition)
            .build());
    session.setMetadata(metadata);
    session.setQueueTitle(queueTitle);
    session.setShuffleMode(shuffleMode);
    session.setRepeatMode(repeatMode);

    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    AtomicLong positionRef = new AtomicLong();
    AtomicLong bufferedPositionRef = new AtomicLong();
    AtomicReference<Float> speedRef = new AtomicReference<>();
    AtomicReference<MediaItem> mediaItemRef = new AtomicReference<>();
    AtomicBoolean playWhenReadyRef = new AtomicBoolean();
    AtomicLong playbackStateRef = new AtomicLong();
    AtomicBoolean shuffleModeEnabledRef = new AtomicBoolean();
    AtomicLong repeatModeRef = new AtomicLong();
    AtomicReference<MediaMetadata> playlistMetadataRef = new AtomicReference<>();
    AtomicBoolean isPlayingAdRef = new AtomicBoolean();
    AtomicLong durationRef = new AtomicLong();
    AtomicLong durationInTimelineRef = new AtomicLong();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              positionRef.set(controller.getCurrentPosition());
              bufferedPositionRef.set(controller.getBufferedPosition());
              speedRef.set(controller.getPlaybackParameters().speed);
              mediaItemRef.set(controller.getCurrentMediaItem());
              playWhenReadyRef.set(controller.getPlayWhenReady());
              playbackStateRef.set(controller.getPlaybackState());
              repeatModeRef.set(controller.getRepeatMode());
              shuffleModeEnabledRef.set(controller.getShuffleModeEnabled());
              playlistMetadataRef.set(controller.getPlaylistMetadata());
              isPlayingAdRef.set(controller.isPlayingAd());
              durationRef.set(controller.getDuration());
              durationInTimelineRef.set(
                  controller
                      .getCurrentTimeline()
                      .getWindow(/* windowIndex= */ 0, new Timeline.Window())
                      .getDurationMs());
            });

    assertThat(positionRef.get())
        .isIn(Range.closedOpen(position, position + (long) (speed * TIMEOUT_MS)));
    assertThat(bufferedPositionRef.get()).isEqualTo(bufferedPosition);
    assertThat(speedRef.get()).isEqualTo(speed);
    assertThat(mediaItemRef.get().mediaId).isEqualTo(metadata.getDescription().getMediaId());
    assertThat(playWhenReadyRef.get()).isTrue();
    assertThat(playbackStateRef.get()).isEqualTo(STATE_READY);
    assertThat(shuffleModeEnabledRef.get()).isTrue();
    assertThat(repeatModeRef.get()).isEqualTo(Player.REPEAT_MODE_ALL);
    assertThat(playlistMetadataRef.get().title.toString()).isEqualTo(queueTitle.toString());
    assertThat(isPlayingAdRef.get()).isEqualTo(isPlayingAd);
    assertThat(durationRef.get()).isEqualTo(duration);
    assertThat(durationInTimelineRef.get()).isEqualTo(duration);
  }

  @Test
  public void getPackageName() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    assertThat(controller.getConnectedToken().getPackageName()).isEqualTo(SUPPORT_APP_PACKAGE_NAME);
  }

  @Test
  public void getSessionVersion() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    assertThat(controller.getConnectedToken().getSessionVersion()).isLessThan(1_000_000);
  }

  @Test
  public void getSessionActivity() throws Exception {
    Intent sessionActivity = new Intent(context, MockActivity.class);
    PendingIntent pi =
        PendingIntent.getActivity(
            context, 0, sessionActivity, Util.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    session.setSessionActivity(pi);

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    PendingIntent sessionActivityOut = controller.getSessionActivity();
    assertThat(sessionActivityOut).isNotNull();
    if (Util.SDK_INT >= 17) {
      // PendingIntent#getCreatorPackage() is added in API 17.
      assertThat(sessionActivityOut.getCreatorPackage()).isEqualTo(context.getPackageName());
    }
  }

  @Test
  public void setRepeatMode_updatesAndNotifiesRepeatMode() throws Exception {
    @Player.RepeatMode int testRepeatMode = Player.REPEAT_MODE_ALL;
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger repeatModeFromParamRef = new AtomicInteger();
    AtomicInteger repeatModeFromGetterRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(@RepeatMode int repeatMode) {
            repeatModeFromParamRef.set(repeatMode);
            repeatModeFromGetterRef.set(controller.getRepeatMode());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_GROUP);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(repeatModeFromParamRef.get()).isEqualTo(testRepeatMode);
    assertThat(repeatModeFromGetterRef.get()).isEqualTo(testRepeatMode);
  }

  @Test
  public void setShuffleModeEnabled_updatesAndNotifiesShuffleModeEnabled() throws Exception {
    boolean testShuffleModeEnabled = true;

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean shuffleModeFromParamRef = new AtomicBoolean();
    AtomicBoolean shuffleModeFromGetterRef = new AtomicBoolean();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            shuffleModeFromParamRef.set(shuffleModeEnabled);
            shuffleModeFromGetterRef.set(controller.getShuffleModeEnabled());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(shuffleModeFromParamRef.get()).isEqualTo(testShuffleModeEnabled);
    assertThat(shuffleModeFromGetterRef.get()).isEqualTo(testShuffleModeEnabled);
  }

  @Test
  public void setQueue_updatesAndNotifiesTimeline() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    AtomicInteger reasonRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineFromGetterRef.set(controller.getCurrentTimeline());
            reasonRef.set(reason);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    Timeline testTimeline = MediaTestUtils.createTimeline(/* windowCount= */ 2);
    List<QueueItem> testQueue =
        MediaTestUtils.convertToQueueItemsWithoutBitmap(
            LegacyConversions.convertToMediaItemList(testTimeline));
    session.setQueue(testQueue);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertMediaIdEquals(testTimeline, timelineFromParamRef.get());
    MediaTestUtils.assertMediaIdEquals(testTimeline, timelineFromGetterRef.get());
    assertThat(reasonRef.get()).isEqualTo(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
  }

  @Test
  public void setQueue_withNull_notifiesEmptyTimeline() throws Exception {
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 2);
    List<QueueItem> queue =
        MediaTestUtils.convertToQueueItemsWithoutBitmap(
            LegacyConversions.convertToMediaItemList(timeline));
    session.setQueue(queue);

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Timeline> timelineRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineRef.set(timeline);
            latch.countDown();
          }
        };
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setQueue(null);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(timelineRef.get().getWindowCount()).isEqualTo(0);
    assertThat(timelineRef.get().getPeriodCount()).isEqualTo(0);
  }

  @Test
  public void setQueue_withDuplicatedMediaItems_updatesAndNotifiesTimeline() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    AtomicInteger reasonRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineFromGetterRef.set(controller.getCurrentTimeline());
            reasonRef.set(reason);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 2);
    Timeline testTimeline =
        MediaTestUtils.createTimeline(
            ImmutableList.copyOf(Iterables.concat(mediaItems, mediaItems)));
    List<QueueItem> testQueue =
        MediaTestUtils.convertToQueueItemsWithoutBitmap(
            LegacyConversions.convertToMediaItemList(testTimeline));
    session.setQueue(testQueue);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertMediaIdEquals(testTimeline, timelineFromParamRef.get());
    MediaTestUtils.assertMediaIdEquals(testTimeline, timelineFromGetterRef.get());
    assertThat(reasonRef.get()).isEqualTo(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
  }

  @Test
  public void setQueue_withDescription_notifiesTimelineWithMetadata() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Timeline> timelineRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineRef.set(timeline);
            latch.countDown();
          }
        };
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    String testMediaId = "testMediaId";
    CharSequence testTitle = "testTitle";
    CharSequence testSubtitle = "testSubtitle";
    CharSequence testDescription = "testDescription";
    Uri testIconUri = Uri.parse("androidx://media3-session/icon");
    Uri testMediaUri = Uri.parse("androidx://media3-session/media");
    Bundle testExtras = TestUtils.createTestBundle();
    byte[] testArtworkData =
        TestUtils.getByteArrayForScaledBitmap(context.getApplicationContext(), TEST_IMAGE_PATH);
    @Nullable Bitmap testBitmap = bitmapLoader.decodeBitmap(testArtworkData).get(10, SECONDS);
    MediaDescriptionCompat description =
        new MediaDescriptionCompat.Builder()
            .setMediaId(testMediaId)
            .setTitle(testTitle)
            .setSubtitle(testSubtitle)
            .setDescription(testDescription)
            .setIconUri(testIconUri)
            .setMediaUri(testMediaUri)
            .setExtras(testExtras)
            .setIconBitmap(testBitmap)
            .build();
    QueueItem queueItem = new QueueItem(description, /* id= */ 0);
    session.setQueue(ImmutableList.of(queueItem));

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(timelineRef.get().getWindowCount()).isEqualTo(1);
    MediaItem mediaItem =
        timelineRef.get().getWindow(/* windowIndex= */ 0, new Timeline.Window()).mediaItem;
    MediaMetadata metadata = mediaItem.mediaMetadata;
    assertThat(TextUtils.equals(metadata.title, testTitle)).isTrue();
    assertThat(TextUtils.equals(metadata.subtitle, testSubtitle)).isTrue();
    assertThat(TextUtils.equals(metadata.description, testDescription)).isTrue();
    assertThat(metadata.artworkUri).isEqualTo(testIconUri);
    if (Util.SDK_INT >= 21) {
      // Bitmap conversion and back gives not exactly the same byte array below API 21
      assertThat(metadata.artworkData).isEqualTo(testArtworkData);
    }
    if (Util.SDK_INT < 21 || Util.SDK_INT >= 23) {
      // TODO(b/199055952): Test mediaUri for all API levels once the bug is fixed.
      assertThat(mediaItem.requestMetadata.mediaUri).isEqualTo(testMediaUri);
    }
    assertThat(TestUtils.equals(metadata.extras, testExtras)).isTrue();
  }

  @Test
  public void setQueueTitle_updatesAndNotifiesPlaylistMetadata() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaMetadata> metadataFromParamRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> metadataFromGetterRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaylistMetadataChanged(MediaMetadata playlistMetadata) {
            metadataFromParamRef.set(playlistMetadata);
            metadataFromGetterRef.set(controller.getPlaylistMetadata());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    CharSequence queueTitle = "queueTitle";
    session.setQueueTitle(queueTitle);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(metadataFromParamRef.get().title.toString()).isEqualTo(queueTitle.toString());
    assertThat(metadataFromGetterRef.get().title.toString()).isEqualTo(queueTitle.toString());
  }

  @Test
  public void getCurrentMediaItem_byDefault_returnsNull() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    MediaItem mediaItem = threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItem);
    assertThat(mediaItem).isNull();
  }

  @Test
  public void getCurrentMediaItem_withSetMetadata_returnsMediaItemWithMediaId() throws Exception {
    String testMediaId = "testMediaId";
    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder().putText(METADATA_KEY_MEDIA_ID, testMediaId).build();
    session.setMetadata(metadata);
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    MediaItem mediaItem = threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItem);

    assertThat(mediaItem.mediaId).isEqualTo(testMediaId);
  }

  @Test
  public void setMetadata_notifiesCurrentMediaItem() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaItem> itemRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem item, @Player.MediaItemTransitionReason int reason) {
            itemRef.set(item);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    String testMediaId = "testMediaId";
    CharSequence testTitle = "testTitle";
    CharSequence testSubtitle = "testSubtitle";
    CharSequence testDescription = "testDescription";
    String testIconUri = "androidx://media3-session/icon";
    String testMediaUri = "androidx://media3-session/media";
    MediaMetadataCompat metadataCompat =
        new MediaMetadataCompat.Builder()
            .putText(METADATA_KEY_MEDIA_ID, testMediaId)
            .putText(METADATA_KEY_DISPLAY_TITLE, testTitle)
            .putText(METADATA_KEY_DISPLAY_SUBTITLE, testSubtitle)
            .putText(METADATA_KEY_DISPLAY_DESCRIPTION, testDescription)
            .putString(METADATA_KEY_DISPLAY_ICON_URI, testIconUri)
            .putString(METADATA_KEY_MEDIA_URI, testMediaUri)
            .build();
    session.setMetadata(metadataCompat);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaItem mediaItem = itemRef.get();
    assertThat(mediaItem.mediaId).isEqualTo(testMediaId);
    assertThat(mediaItem.requestMetadata.mediaUri).isEqualTo(Uri.parse(testMediaUri));
    MediaMetadata metadata = mediaItem.mediaMetadata;
    assertThat(TextUtils.equals(metadata.title, testTitle)).isTrue();
    assertThat(TextUtils.equals(metadata.subtitle, testSubtitle)).isTrue();
    assertThat(TextUtils.equals(metadata.description, testDescription)).isTrue();
    assertThat(metadata.artworkUri).isEqualTo(Uri.parse(testIconUri));
  }

  @Test
  public void isPlayingAd_withMetadataWithAd_returnsTrue() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean isPlayingAdRef = new AtomicBoolean();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            isPlayingAdRef.set(controller.isPlayingAd());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_ADVERTISEMENT, 1).build();
    session.setMetadata(metadata);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(isPlayingAdRef.get()).isTrue();
  }

  @Test
  public void seekToDefaultPosition_withMediaItemIndex_updatesExpectedMediaItemIndex()
      throws Exception {
    List<MediaItem> testList = MediaTestUtils.createMediaItems(3);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);
    session.setQueue(testQueue);
    session.setPlaybackState(/* state= */ null);
    int testMediaItemIndex = 2;
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    threadTestRule
        .getHandler()
        .postAndSync(() -> controller.seekToDefaultPosition(testMediaItemIndex));

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setActiveQueueItemId(testQueue.get(testMediaItemIndex).getQueueId())
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testMediaItemIndex);
  }

  @Test
  public void seekTo_withMediaItemIndex_updatesExpectedMediaItemIndex() throws Exception {
    List<MediaItem> testList = MediaTestUtils.createMediaItems(3);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);
    session.setQueue(testQueue);
    session.setPlaybackState(/* state= */ null);
    long testPositionMs = 23L;
    int testMediaItemIndex = 2;
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger mediaItemIndexFromParamRef = new AtomicInteger();
    AtomicInteger mediaItemIndexFromGetterRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            mediaItemIndexFromParamRef.set(newPosition.mediaItemIndex);
            mediaItemIndexFromGetterRef.set(controller.getCurrentMediaItemIndex());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    threadTestRule
        .getHandler()
        .postAndSync(() -> controller.seekTo(testMediaItemIndex, testPositionMs));

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setActiveQueueItemId(testQueue.get(testMediaItemIndex).getQueueId())
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(mediaItemIndexFromParamRef.get()).isEqualTo(testMediaItemIndex);
    assertThat(mediaItemIndexFromGetterRef.get()).isEqualTo(testMediaItemIndex);
  }

  @Test
  public void getMediaItemCount_withValidQueueAndQueueId_returnsQueueSize() throws Exception {
    List<MediaItem> testList = MediaTestUtils.createMediaItems(3);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);
    session.setQueue(testQueue);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setActiveQueueItemId(testQueue.get(0).getQueueId())
            .build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    int mediaItemCount = threadTestRule.getHandler().postAndSync(controller::getMediaItemCount);
    assertThat(mediaItemCount).isEqualTo(testList.size());
  }

  @Test
  public void getMediaItemCount_withoutQueue_returnsZero() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    int mediaItemCount = threadTestRule.getHandler().postAndSync(controller::getMediaItemCount);
    assertThat(mediaItemCount).isEqualTo(0);
  }

  @Test
  public void getMediaItemCount_withoutQueueButEmptyMetadata_returnsOne() throws Exception {
    session.setMetadata(new MediaMetadataCompat.Builder().build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    int mediaItemCount = threadTestRule.getHandler().postAndSync(controller::getMediaItemCount);
    assertThat(mediaItemCount).isEqualTo(1);
  }

  @Test
  public void getMediaItemCount_withInvalidQueueIdWithoutMetadata_returnsAdjustedCount()
      throws Exception {
    List<MediaItem> testList = MediaTestUtils.createMediaItems(3);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);
    session.setQueue(testQueue);
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    int mediaItemCount = threadTestRule.getHandler().postAndSync(controller::getMediaItemCount);
    assertThat(mediaItemCount).isEqualTo(testList.size());
  }

  @Test
  public void getMediaItemCount_withInvalidQueueIdWithMetadata_returnsAdjustedCount()
      throws Exception {
    List<MediaItem> testList = MediaTestUtils.createMediaItems(3);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);
    MediaItem testRemoveMediaItem = MediaTestUtils.createMediaItem("removed");
    MediaMetadataCompat testMetadataCompat =
        LegacyConversions.convertToMediaMetadataCompat(
            testRemoveMediaItem.mediaMetadata,
            "mediaId",
            Uri.parse("http://example.com"),
            /* durationMs= */ 100L,
            /* artworkBitmap= */ null);
    session.setQueue(testQueue);
    session.setMetadata(testMetadataCompat);
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    int mediaItemCount = threadTestRule.getHandler().postAndSync(controller::getMediaItemCount);

    assertThat(mediaItemCount).isEqualTo(testList.size() + 1);
  }

  @Test
  public void getMediaItemCount_whenQueueIdIsChangedFromInvalidToValid_returnOriginalCount()
      throws Exception {
    List<MediaItem> testList = MediaTestUtils.createMediaItems(3);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);
    MediaItem testRemoveMediaItem = MediaTestUtils.createMediaItem("removed");
    MediaMetadataCompat testMetadataCompat =
        LegacyConversions.convertToMediaMetadataCompat(
            testRemoveMediaItem.mediaMetadata,
            "mediaId",
            Uri.parse("http://example.com"),
            /* durationMs= */ 100L,
            /* artworkBitmap= */ null);
    session.setQueue(testQueue);
    session.setMetadata(testMetadataCompat);
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setActiveQueueItemId(testQueue.get(0).getQueueId())
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    int mediaItemCount = threadTestRule.getHandler().postAndSync(controller::getMediaItemCount);

    assertThat(mediaItemCount).isEqualTo(testList.size());
  }

  @Test
  public void getCurrentMediaItemIndex_withInvalidQueueIdWithMetadata_returnsEndOfList()
      throws Exception {
    List<MediaItem> testList = MediaTestUtils.createMediaItems(3);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);
    MediaItem testRemoveMediaItem = MediaTestUtils.createMediaItem("removed");
    MediaMetadataCompat testMetadataCompat =
        LegacyConversions.convertToMediaMetadataCompat(
            testRemoveMediaItem.mediaMetadata,
            "mediaId",
            Uri.parse("http://example.com"),
            /* durationMs= */ 100L,
            /* artworkBitmap= */ null);
    session.setQueue(testQueue);
    session.setMetadata(testMetadataCompat);
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    int mediaItemIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItemIndex);

    assertThat(mediaItemIndex).isEqualTo(testList.size());
  }

  @Test
  public void getMediaMetadata_withMediaMetadataCompat_returnsConvertedMediaMetadata()
      throws Exception {
    MediaItem testMediaItem = MediaTestUtils.createMediaItem("test");
    MediaMetadata testMediaMetadata = testMediaItem.mediaMetadata;
    MediaMetadataCompat testMediaMetadataCompat =
        LegacyConversions.convertToMediaMetadataCompat(
            testMediaMetadata,
            "mediaId",
            Uri.parse("http://example.com"),
            /* durationMs= */ 100L,
            /* artworkBitmap= */ null);
    session.setMetadata(testMediaMetadataCompat);
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    MediaMetadata mediaMetadata =
        threadTestRule.getHandler().postAndSync(controller::getMediaMetadata);

    assertThat(mediaMetadata).isEqualTo(testMediaMetadata);
  }

  @Test
  public void getMediaMetadata_withMediaMetadataCompatAndArtworkData_returnsConvertedMediaMetadata()
      throws Exception {
    MediaItem testMediaItem = MediaTestUtils.createMediaItemWithArtworkData("test");
    MediaMetadata testMediaMetadata = testMediaItem.mediaMetadata;
    @Nullable Bitmap artworkBitmap = getBitmapFromMetadata(testMediaMetadata);
    MediaMetadataCompat testMediaMetadataCompat =
        LegacyConversions.convertToMediaMetadataCompat(
            testMediaMetadata,
            "mediaId",
            Uri.parse("http://example.com"),
            /* durationMs= */ 100L,
            artworkBitmap);
    session.setMetadata(testMediaMetadataCompat);
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    MediaMetadata mediaMetadata =
        threadTestRule.getHandler().postAndSync(controller::getMediaMetadata);

    assertThat(mediaMetadata.artworkData).isNotNull();
    if (Util.SDK_INT < 21) {
      // Bitmap conversion and back gives not exactly the same byte array below API 21
      mediaMetadata =
          mediaMetadata
              .buildUpon()
              .setArtworkData(/* artworkData= */ null, /* artworkDataType= */ null)
              .build();
      testMediaMetadata =
          testMediaMetadata
              .buildUpon()
              .setArtworkData(/* artworkData= */ null, /* artworkDataType= */ null)
              .build();
    }
    assertThat(mediaMetadata).isEqualTo(testMediaMetadata);
  }

  @Test
  public void getMediaMetadata_withoutMediaMetadataCompat_returnsEmptyMediaMetadata()
      throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    MediaMetadata mediaMetadata =
        threadTestRule.getHandler().postAndSync(controller::getMediaMetadata);
    assertThat(mediaMetadata).isEqualTo(MediaMetadata.EMPTY);
  }

  @Test
  public void
      getMediaMetadata_withMediaDescriptionWithoutMediaMetadata_returnsMediaDescriptionValues()
          throws Exception {
    MediaDescriptionCompat testMediaDescriptionCompat =
        new MediaDescriptionCompat.Builder()
            .setTitle(METADATA_TITLE)
            .setDescription(METADATA_DESCRIPTION)
            .build();
    long testActiveQueueId = 0;
    List<QueueItem> testQueueItemList =
        ImmutableList.of(
            new MediaSessionCompat.QueueItem(testMediaDescriptionCompat, testActiveQueueId));
    session.setQueue(testQueueItemList);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder().setActiveQueueItemId(testActiveQueueId).build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    MediaMetadata mediaMetadata =
        threadTestRule.getHandler().postAndSync(controller::getMediaMetadata);
    assertThat(mediaMetadata.title.toString())
        .isEqualTo(testMediaDescriptionCompat.getTitle().toString());
    assertThat(mediaMetadata.description.toString())
        .isEqualTo(testMediaDescriptionCompat.getDescription().toString());
  }

  @Test
  public void getMediaMetadata_withMediaMetadataCompatWithQueue_returnsMediaMetadataCompatValues()
      throws Exception {
    String testMediaDescriptionCompatDescription = "testMediaDescriptionCompatDescription";
    String testMediaMetadataCompatDescription = "testMediaMetadataCompatDescription";
    MediaDescriptionCompat testMediaDescriptionCompat =
        new MediaDescriptionCompat.Builder()
            .setDescription(testMediaDescriptionCompatDescription)
            .build();
    MediaMetadataCompat testMediaMetadataCompat =
        new MediaMetadataCompat.Builder()
            .putText(
                MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                testMediaMetadataCompatDescription)
            .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, METADATA_ARTIST)
            .putText(MediaMetadataCompat.METADATA_KEY_ALBUM, METADATA_ALBUM_TITLE)
            .build();
    long testActiveQueueId = 0;
    List<QueueItem> testQueueItemList =
        ImmutableList.of(
            new MediaSessionCompat.QueueItem(testMediaDescriptionCompat, testActiveQueueId));
    session.setQueue(testQueueItemList);
    session.setMetadata(testMediaMetadataCompat);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder().setActiveQueueItemId(testActiveQueueId).build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    MediaMetadata mediaMetadata =
        threadTestRule.getHandler().postAndSync(controller::getMediaMetadata);
    assertThat(TextUtils.equals(mediaMetadata.description, testMediaMetadataCompatDescription))
        .isTrue();
    assertThat(TextUtils.equals(mediaMetadata.artist, METADATA_ARTIST)).isTrue();
    assertThat(TextUtils.equals(mediaMetadata.albumTitle, METADATA_ALBUM_TITLE)).isTrue();
  }

  @Test
  public void getMediaMetadata_withoutMediaMetadataCompatWithQueue_returnsEmptyMediaMetadata()
      throws Exception {
    List<MediaItem> testList = MediaTestUtils.createMediaItems(3);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);
    int testIndex = 1;
    long testActiveQueueId = testQueue.get(testIndex).getQueueId();
    session.setQueue(testQueue);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder().setActiveQueueItemId(testActiveQueueId).build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    MediaMetadata mediaMetadata =
        threadTestRule.getHandler().postAndSync(controller::getMediaMetadata);
    assertThat(mediaMetadata).isEqualTo(testList.get(testIndex).mediaMetadata);
  }

  @Test
  public void setPlaybackState_withActiveQueueItemId_notifiesCurrentMediaItem() throws Exception {
    List<MediaItem> testList = MediaTestUtils.createMediaItems(/* size= */ 2);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);
    session.setQueue(testQueue);

    PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();

    // Set the current active queue item to index 'oldItemIndex'.
    int oldItemIndex = 0;
    builder.setActiveQueueItemId(testQueue.get(oldItemIndex).getQueueId());
    session.setPlaybackState(builder.build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaItem> itemRef = new AtomicReference<>();
    AtomicInteger mediaItemTransitionReasonRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem item, @Player.MediaItemTransitionReason int reason) {
            itemRef.set(item);
            mediaItemTransitionReasonRef.set(reason);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    // The new playbackState will tell the controller that the active queue item is changed to
    // 'newItemIndex'.
    int newItemIndex = 1;
    builder.setActiveQueueItemId(testQueue.get(newItemIndex).getQueueId());
    session.setPlaybackState(builder.build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    int currentIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItemIndex);
    assertThat(currentIndex).isEqualTo(newItemIndex);
    MediaTestUtils.assertMediaIdEquals(testList.get(newItemIndex), itemRef.get());
    assertThat(mediaItemTransitionReasonRef.get()).isEqualTo(MEDIA_ITEM_TRANSITION_REASON_AUTO);
  }

  @Test
  public void
      setPlaybackState_withAdjacentQueueItemWhilePlaying_notifiesPositionDiscontinuityByAutoTransition()
          throws Exception {
    long testDuration = 3_000;
    List<QueueItem> testQueue = MediaTestUtils.createQueueItems(/* size= */ 2);
    session.setQueue(testQueue);
    session.setMetadata(
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_DURATION, testDuration).build());

    PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();

    // Set the current active queue item to index 'oldItemIndex'.
    int oldItemIndex = 0;
    builder.setActiveQueueItemId(testQueue.get(oldItemIndex).getQueueId());
    builder.setState(
        PlaybackStateCompat.STATE_PLAYING, /* position= */ 0L, /* playbackSpeed= */ 1.0f);
    session.setPlaybackState(builder.build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PositionInfo> oldPositionRef = new AtomicReference<>();
    AtomicReference<PositionInfo> newPositionRef = new AtomicReference<>();
    AtomicInteger positionDiscontinuityReasonRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            oldPositionRef.set(oldPosition);
            newPositionRef.set(newPosition);
            positionDiscontinuityReasonRef.set(reason);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    int newItemIndex = 1;
    builder.setActiveQueueItemId(testQueue.get(newItemIndex).getQueueId());
    session.setPlaybackState(builder.build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(positionDiscontinuityReasonRef.get())
        .isEqualTo(Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    assertThat(oldPositionRef.get().mediaItemIndex).isEqualTo(oldItemIndex);
    assertThat(newPositionRef.get().mediaItemIndex).isEqualTo(newItemIndex);
  }

  @Test
  public void
      setPlaybackState_withAdjacentQueueItemAfterPlaybackDone_notifiesPositionDiscontinuityByTransition()
          throws Exception {
    long testDuration = 3000;
    List<QueueItem> testQueue = MediaTestUtils.createQueueItems(/* size= */ 2);
    session.setQueue(testQueue);
    session.setMetadata(
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_DURATION, testDuration).build());

    PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();

    // Set the current active queue item to index 'oldItemIndex'.
    int oldItemIndex = 0;
    builder.setActiveQueueItemId(testQueue.get(oldItemIndex).getQueueId());
    builder.setState(
        PlaybackStateCompat.STATE_PLAYING, /* position= */ testDuration, /* playbackSpeed= */ 1.0f);
    session.setPlaybackState(builder.build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PositionInfo> oldPositionRef = new AtomicReference<>();
    AtomicReference<PositionInfo> newPositionRef = new AtomicReference<>();
    AtomicInteger positionDiscontinuityReasonRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            oldPositionRef.set(oldPosition);
            newPositionRef.set(newPosition);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    int newItemIndex = 1;
    builder.setActiveQueueItemId(testQueue.get(newItemIndex).getQueueId());
    builder.setState(
        PlaybackStateCompat.STATE_PLAYING, /* position= */ 0, /* playbackSpeed= */ 1.0f);
    session.setPlaybackState(builder.build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(positionDiscontinuityReasonRef.get())
        .isEqualTo(Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    assertThat(oldPositionRef.get().mediaItemIndex).isEqualTo(oldItemIndex);
    assertThat(newPositionRef.get().mediaItemIndex).isEqualTo(newItemIndex);
  }

  @Test
  public void setPlaybackState_withDistantQueueItem_notifiesPositionDiscontinuityByAutoTransition()
      throws Exception {
    List<QueueItem> testQueue = MediaTestUtils.createQueueItems(/* size= */ 3);
    session.setQueue(testQueue);
    session.setMetadata(new MediaMetadataCompat.Builder().build());

    PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();

    // Set the current active queue item to index 'oldItemIndex'.
    int oldItemIndex = 0;
    builder.setActiveQueueItemId(testQueue.get(oldItemIndex).getQueueId());
    session.setPlaybackState(builder.build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PositionInfo> oldPositionRef = new AtomicReference<>();
    AtomicReference<PositionInfo> newPositionRef = new AtomicReference<>();
    AtomicInteger positionDiscontinuityReasonRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            oldPositionRef.set(oldPosition);
            newPositionRef.set(newPosition);
            positionDiscontinuityReasonRef.set(reason);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    int newItemIndex = 2;
    builder.setActiveQueueItemId(testQueue.get(newItemIndex).getQueueId());
    session.setPlaybackState(builder.build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(positionDiscontinuityReasonRef.get())
        .isEqualTo(Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    assertThat(oldPositionRef.get().mediaItemIndex).isEqualTo(oldItemIndex);
    assertThat(newPositionRef.get().mediaItemIndex).isEqualTo(newItemIndex);
  }

  @Test
  public void setPlaybackState_withNewPosition_notifiesOnPositionDiscontinuity() throws Exception {
    long testOldCurrentPositionMs = 300L;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PAUSED, testOldCurrentPositionMs, /* playbackSpeed= */ 1f)
            .build());
    session.setMetadata(new MediaMetadataCompat.Builder().build());

    AtomicReference<PositionInfo> oldPositionRef = new AtomicReference<>();
    AtomicReference<PositionInfo> newPositionRef = new AtomicReference<>();
    AtomicInteger positionDiscontinuityReasonRef = new AtomicInteger();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            oldPositionRef.set(oldPosition);
            newPositionRef.set(newPosition);
            positionDiscontinuityReasonRef.set(reason);
            latch.countDown();
          }
        };
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    long testNewCurrentPositionMs = 900L;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PAUSED, testNewCurrentPositionMs, /* playbackSpeed= */ 1f)
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(positionDiscontinuityReasonRef.get())
        .isEqualTo(Player.DISCONTINUITY_REASON_INTERNAL);
    assertThat(oldPositionRef.get().positionMs).isEqualTo(testOldCurrentPositionMs);
    assertThat(newPositionRef.get().positionMs).isEqualTo(testNewCurrentPositionMs);
  }

  @Test
  public void setPlaybackState_fromStateBufferingToPlaying_notifiesReadyState() throws Exception {
    List<MediaItem> testPlaylist = MediaTestUtils.createMediaItems(/* size= */ 1);
    MediaItem firstMediaItemInPlaylist = testPlaylist.get(0);
    MediaMetadataCompat metadata =
        LegacyConversions.convertToMediaMetadataCompat(
            firstMediaItemInPlaylist.mediaMetadata,
            "mediaId",
            Uri.parse("http://example.com"),
            /* durationMs= */ 50_000,
            /* artworkBitmap= */ null);
    long testBufferedPosition = 5_000;
    session.setMetadata(metadata);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_BUFFERING, /* position= */ 0, /* playbackSpeed= */ 1f)
            .setBufferedPosition(0)
            .build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger stateFromParamRef = new AtomicInteger();
    AtomicInteger stateFromGetterRef = new AtomicInteger();
    AtomicLong bufferedPositionFromGetterRef = new AtomicLong();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(@State int playbackState) {
            stateFromParamRef.set(playbackState);
            stateFromGetterRef.set(controller.getPlaybackState());
            bufferedPositionFromGetterRef.set(controller.getBufferedPosition());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PLAYING, /* position= */ 0, /* playbackSpeed= */ 1f)
            .setBufferedPosition(testBufferedPosition)
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(stateFromParamRef.get()).isEqualTo(STATE_READY);
    assertThat(stateFromGetterRef.get()).isEqualTo(STATE_READY);
    assertThat(bufferedPositionFromGetterRef.get()).isEqualTo(testBufferedPosition);
  }

  @Test
  public void setPlaybackState_fromStatePlayingToBuffering_notifiesBufferingState()
      throws Exception {
    List<MediaItem> testPlaylist = MediaTestUtils.createMediaItems(1);
    MediaItem firstMediaItemInPlaylist = testPlaylist.get(0);
    MediaMetadataCompat metadata =
        LegacyConversions.convertToMediaMetadataCompat(
            firstMediaItemInPlaylist.mediaMetadata,
            "mediaId",
            Uri.parse("http://example.com"),
            /* durationMs= */ 1_000,
            /* artworkBitmap= */ null);
    long testBufferingPosition = 0;
    session.setMetadata(metadata);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PLAYING, /* position= */ 100, /* playbackSpeed= */ 1f)
            .setBufferedPosition(500)
            .build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger stateFromParamRef = new AtomicInteger();
    AtomicInteger stateFromGetterRef = new AtomicInteger();
    AtomicLong bufferedPositionFromGetterRef = new AtomicLong();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int playbackState) {
            stateFromParamRef.set(playbackState);
            stateFromGetterRef.set(controller.getPlaybackState());
            bufferedPositionFromGetterRef.set(controller.getBufferedPosition());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_BUFFERING, /* position= */ 0, /* playbackSpeed= */ 1f)
            .setBufferedPosition(testBufferingPosition)
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(stateFromParamRef.get()).isEqualTo(STATE_BUFFERING);
    assertThat(stateFromGetterRef.get()).isEqualTo(STATE_BUFFERING);
    assertThat(bufferedPositionFromGetterRef.get()).isEqualTo(testBufferingPosition);
  }

  @Test
  public void setPlaybackState_fromStateNoneToPlaying_notifiesReadyState() throws Exception {
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_NONE, /* position= */ 0, /* playbackSpeed= */ 1f)
            .build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger stateFromParamRef = new AtomicInteger();
    AtomicInteger stateFromGetterRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int playbackState) {
            stateFromParamRef.set(playbackState);
            stateFromGetterRef.set(controller.getPlaybackState());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PLAYING, /* position= */ 0, /* playbackSpeed= */ 1f)
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(stateFromParamRef.get()).isEqualTo(STATE_READY);
    assertThat(stateFromGetterRef.get()).isEqualTo(STATE_READY);
  }

  @Test
  public void setPlaybackState_fromStatePausedToPlaying_notifiesPlayWhenReady() throws Exception {
    boolean testPlayWhenReady = true;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, /* position= */ 0, /* playbackSpeed= */ 1f)
            .build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean playWhenReadyFromParamRef = new AtomicBoolean();
    AtomicBoolean playWhenReadyFromGetterRef = new AtomicBoolean();
    AtomicInteger playWhenReadyChangeReasonFromParamRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlayWhenReadyChanged(
              boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
            playWhenReadyFromParamRef.set(playWhenReady);
            playWhenReadyFromGetterRef.set(controller.getPlayWhenReady());
            playWhenReadyChangeReasonFromParamRef.set(reason);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PLAYING, /* position= */ 0, /* playbackSpeed= */ 1f)
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playWhenReadyFromParamRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(playWhenReadyFromGetterRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(playWhenReadyChangeReasonFromParamRef.get())
        .isEqualTo(Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE);
  }

  @Test
  public void setPlaybackState_toBuffering_notifiesPlaybackStateBuffering() throws Exception {
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, /* position= */ 0, /* playbackSpeed= */ 1f)
            .build());
    session.setMetadata(
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_DURATION, 1_000).build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger playbackStateFromParamRef = new AtomicInteger();
    AtomicInteger playbackStateFromGetterRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(@Player.State int playbackState) {
            playbackStateFromParamRef.set(playbackState);
            playbackStateFromGetterRef.set(controller.getPlaybackState());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_BUFFERING, /* position= */ 0, /* playbackSpeed= */ 1f)
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateFromParamRef.get()).isEqualTo(Player.STATE_BUFFERING);
    assertThat(playbackStateFromGetterRef.get()).isEqualTo(Player.STATE_BUFFERING);
  }

  @Test
  public void setPlaybackState_toPausedWithEndPosition_notifiesPlaybackStateEnded()
      throws Exception {
    long testDuration = 1_000;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PLAYING, /* position= */ 0, /* playbackSpeed= */ 1f)
            .build());
    session.setMetadata(
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_DURATION, testDuration).build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger playbackStateFromParamRef = new AtomicInteger();
    AtomicInteger playbackStateFromGetterRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(@Player.State int playbackState) {
            playbackStateFromParamRef.set(playbackState);
            playbackStateFromGetterRef.set(controller.getPlaybackState());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PAUSED,
                /* position= */ testDuration,
                /* playbackSpeed= */ 1f)
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateFromParamRef.get()).isEqualTo(Player.STATE_ENDED);
    assertThat(playbackStateFromGetterRef.get()).isEqualTo(Player.STATE_ENDED);
  }

  @Test
  public void setPlaybackState_withSpeed_notifiesOnPlaybackParametersChanged() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PlaybackParameters> playbackParametersFromParamRef = new AtomicReference<>();
    AtomicReference<PlaybackParameters> playbackParametersFromGetterRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            playbackParametersFromParamRef.set(playbackParameters);
            playbackParametersFromGetterRef.set(controller.getPlaybackParameters());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    float testSpeed = 3.0f;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PLAYING,
                /* position= */ 0,
                /* playbackSpeed= */ testSpeed)
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackParametersFromParamRef.get().speed).isEqualTo(testSpeed);
    assertThat(playbackParametersFromGetterRef.get().speed).isEqualTo(testSpeed);
  }

  @Test
  public void setPlaybackState_withError_notifiesOnPlayerErrorChanged() throws Exception {
    String testErrorMessage = "testErrorMessage";
    int testErrorCode = PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR; // 0
    String testConvertedErrorMessage = "testErrorMessage, code=0";
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PlaybackException> errorFromParamRef = new AtomicReference<>();
    AtomicReference<PlaybackException> errorFromGetterRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlayerErrorChanged(@Nullable PlaybackException error) {
            errorFromParamRef.set(error);
            errorFromGetterRef.set(controller.getPlayerError());
            latch.countDown();
          }
        };
    controller.addListener(listener);

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_ERROR, /* position= */ 0, /* playbackSpeed= */ 1.0f)
            .setErrorMessage(testErrorCode, testErrorMessage)
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(errorFromParamRef.get().errorCode).isEqualTo(ERROR_CODE_REMOTE_ERROR);
    assertThat(errorFromParamRef.get().getMessage()).isEqualTo(testConvertedErrorMessage);
    assertThat(errorFromGetterRef.get().errorCode).isEqualTo(ERROR_CODE_REMOTE_ERROR);
    assertThat(errorFromGetterRef.get().getMessage()).isEqualTo(testConvertedErrorMessage);
  }

  @Test
  public void setPlaybackState_withActions_updatesAndNotifiesAvailableCommands() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Player.Commands> commandsFromParamRef = new AtomicReference<>();
    AtomicReference<Player.Commands> commandsFromGetterRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onAvailableCommandsChanged(Player.Commands commands) {
            commandsFromParamRef.set(commands);
            commandsFromGetterRef.set(controller.getAvailableCommands());
            latch.countDown();
          }
        };
    controller.addListener(listener);

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_FAST_FORWARD)
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(commandsFromParamRef.get().contains(Player.COMMAND_PLAY_PAUSE)).isTrue();
    assertThat(commandsFromParamRef.get().contains(Player.COMMAND_SEEK_FORWARD)).isTrue();
    assertThat(commandsFromGetterRef.get().contains(Player.COMMAND_PLAY_PAUSE)).isTrue();
    assertThat(commandsFromGetterRef.get().contains(Player.COMMAND_SEEK_FORWARD)).isTrue();
  }

  @Test
  public void setPlaybackToRemote_notifiesDeviceInfoAndVolume() throws Exception {
    int volumeControlType = VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE;
    int maxVolume = 100;
    int currentVolume = 45;
    String routingSessionId = Util.SDK_INT >= 30 ? "route" : null;

    AtomicReference<DeviceInfo> deviceInfoRef = new AtomicReference<>();
    CountDownLatch latchForDeviceInfo = new CountDownLatch(1);
    CountDownLatch latchForDeviceVolume = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceInfoChanged(DeviceInfo deviceInfo) {
            if (deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
              deviceInfoRef.set(deviceInfo);
              latchForDeviceInfo.countDown();
            }
          }

          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            if (volume == currentVolume) {
              latchForDeviceVolume.countDown();
            }
          }
        };
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setPlaybackToRemote(volumeControlType, maxVolume, currentVolume, routingSessionId);

    assertThat(latchForDeviceInfo.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(latchForDeviceVolume.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceInfoRef.get().maxVolume).isEqualTo(maxVolume);
    assertThat(deviceInfoRef.get().routingControllerId).isEqualTo(routingSessionId);
  }

  @Test
  public void setPlaybackToLocal_notifiesDeviceInfoAndVolume() throws Exception {
    if (Util.SDK_INT == 21 || Util.SDK_INT == 22) {
      // In API 21 and 22, onAudioInfoChanged is not called.
      return;
    }
    session.setPlaybackToRemote(
        VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,
        /* maxVolume= */ 100,
        /* currentVolume= */ 45,
        /* routingControllerId= */ "route");

    int testLocalStreamType = AudioManager.STREAM_ALARM;
    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    int maxVolume = audioManager.getStreamMaxVolume(testLocalStreamType);
    int currentVolume = audioManager.getStreamVolume(testLocalStreamType);

    AtomicReference<DeviceInfo> deviceInfoRef = new AtomicReference<>();
    CountDownLatch latchForDeviceInfo = new CountDownLatch(1);
    CountDownLatch latchForDeviceVolume = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceInfoChanged(DeviceInfo deviceInfo) {
            if (deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL) {
              deviceInfoRef.set(deviceInfo);
              latchForDeviceInfo.countDown();
            }
          }

          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            if (volume == currentVolume) {
              latchForDeviceVolume.countDown();
            }
          }
        };
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    session.setPlaybackToLocal(testLocalStreamType);

    assertThat(latchForDeviceInfo.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(latchForDeviceVolume.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceInfoRef.get().maxVolume).isEqualTo(maxVolume);
  }

  @Test
  public void sendSessionEvent_notifiesCustomCommand() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<SessionCommand> commandRef = new AtomicReference<>();
    AtomicReference<Bundle> argsRef = new AtomicReference<>();
    MediaController.Listener listener =
        new MediaController.Listener() {
          @Override
          public ListenableFuture<SessionResult> onCustomCommand(
              MediaController controller, SessionCommand command, Bundle args) {
            commandRef.set(command);
            argsRef.set(args);
            latch.countDown();
            return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
          }
        };
    controllerTestRule.createController(session.getSessionToken(), listener);

    String event = "customCommand";
    Bundle extras = TestUtils.createTestBundle();
    session.sendSessionEvent(event, extras);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(commandRef.get().customAction).isEqualTo(event);
    assertThat(TestUtils.equals(extras, argsRef.get())).isTrue();
  }

  @Test
  public void setPlaybackState_withCustomAction_notifiesCustomLayout() throws Exception {
    CustomAction testCustomAction =
        new CustomAction.Builder("testCustomAction", "testName", 1).build();

    CountDownLatch latch = new CountDownLatch(1);
    List<CommandButton> layoutOut = new ArrayList<>();
    MediaController.Listener listener =
        new MediaController.Listener() {
          @Override
          public ListenableFuture<SessionResult> onSetCustomLayout(
              MediaController controller, List<CommandButton> layout) {
            layoutOut.addAll(layout);
            latch.countDown();
            return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
          }
        };
    controllerTestRule.createController(session.getSessionToken(), listener);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder().addCustomAction(testCustomAction).build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(layoutOut).hasSize(1);
    CommandButton button = layoutOut.get(0);
    assertThat(button.sessionCommand.customAction).isEqualTo(testCustomAction.getAction());
    assertThat(button.displayName.toString()).isEqualTo(testCustomAction.getName().toString());
    assertThat(button.iconResId).isEqualTo(testCustomAction.getIcon());
  }

  @Test
  public void setPlaybackState_withCustomAction_notifiesAvailableCommands() throws Exception {
    CustomAction testCustomAction =
        new CustomAction.Builder("testCustomAction", "testName1", 1).build();
    SessionCommand testSessionCommand =
        new SessionCommand(testCustomAction.getAction(), /* extras= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<SessionCommands> commandsRef = new AtomicReference<>();
    MediaController.Listener listener =
        new MediaController.Listener() {
          @Override
          public void onAvailableSessionCommandsChanged(
              MediaController controller, SessionCommands commands) {
            commandsRef.set(commands);
            latch.countDown();
          }
        };
    controllerTestRule.createController(session.getSessionToken(), listener);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder().addCustomAction(testCustomAction).build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    SessionCommands commands = commandsRef.get();
    assertThat(commands.contains(testSessionCommand)).isTrue();
  }

  @Test
  public void setCaptioningEnabled_notifiesCustomCommand() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<SessionCommand> commandRef = new AtomicReference<>();
    AtomicReference<Bundle> argsRef = new AtomicReference<>();
    MediaController.Listener listener =
        new MediaController.Listener() {
          @Override
          public ListenableFuture<SessionResult> onCustomCommand(
              MediaController controller, SessionCommand command, Bundle args) {
            commandRef.set(command);
            argsRef.set(args);
            latch.countDown();
            return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
          }
        };
    controllerTestRule.createController(session.getSessionToken(), listener);

    session.setCaptioningEnabled(true);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(commandRef.get().customAction)
        .isEqualTo(SESSION_COMMAND_ON_CAPTIONING_ENABLED_CHANGED);
    BundleSubject.assertThat(argsRef.get()).bool(ARGUMENT_CAPTIONING_ENABLED).isTrue();
  }

  @Test
  public void getCurrentPosition_byDefault_returnsZero() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    long currentPositionMs =
        threadTestRule.getHandler().postAndSync(controller::getCurrentPosition);
    assertThat(currentPositionMs).isEqualTo(0);
  }

  @Test
  public void getCurrentPosition_withNegativePosition_adjustsToZero() throws Exception {
    long testPositionMs = -100L;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPositionMs, /* playbackSpeed= */ 1.0f)
            .build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    long currentPositionMs =
        threadTestRule.getHandler().postAndSync(controller::getCurrentPosition);
    assertThat(currentPositionMs).isEqualTo(0);
  }

  @Test
  public void getCurrentPosition_withGreaterThanDuration_adjustsToDuration() throws Exception {
    long testDurationMs = 100L;
    long testPositionMs = 200L;
    session.setMetadata(
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_DURATION, testDurationMs).build());
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPositionMs, /* playbackSpeed= */ 1.0f)
            .build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    long currentPositionMs =
        threadTestRule.getHandler().postAndSync(controller::getCurrentPosition);
    assertThat(currentPositionMs).isEqualTo(testDurationMs);
  }

  @Test
  public void getCurrentPosition_withDelayWhileNotPlaying_doesNotAdvance() throws Exception {
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PAUSED, /* position= */ 500, /* playbackSpeed= */ 2.0f)
            .build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    long currentPositionMs =
        threadTestRule
            .getHandler()
            .postAndSync(
                () -> {
                  Thread.sleep(100);
                  return controller.getCurrentPosition();
                });

    assertThat(currentPositionMs).isEqualTo(500);
  }

  @Test
  public void getCurrentPosition_withTimeDiffWhilePlaying_advancesWithTimeDiff() throws Exception {
    long timeBeforeSetPlaybackState = SystemClock.elapsedRealtime();
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PLAYING, /* position= */ 500, /* playbackSpeed= */ 2.0f)
            .build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    long timeAfterControllerCreated = SystemClock.elapsedRealtime();

    AtomicLong timeBeforeGetCurrentPosition = new AtomicLong();
    AtomicLong timeAfterGetCurrentPosition = new AtomicLong();
    AtomicLong currentPositionMs = new AtomicLong();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              Thread.sleep(100);
              timeBeforeGetCurrentPosition.set(SystemClock.elapsedRealtime());
              currentPositionMs.set(controller.getCurrentPosition());
              timeAfterGetCurrentPosition.set(SystemClock.elapsedRealtime());
            });

    long minTimeElapsedMs = timeBeforeGetCurrentPosition.get() - timeAfterControllerCreated;
    long maxTimeElapsedMs = timeAfterGetCurrentPosition.get() - timeBeforeSetPlaybackState;
    long minExpectedPositionMs = 500 + minTimeElapsedMs * 2;
    long maxExpectedPositionMs = 500 + maxTimeElapsedMs * 2;
    assertThat(currentPositionMs.get())
        .isIn(Range.closed(minExpectedPositionMs, maxExpectedPositionMs));
  }

  @Test
  public void getContentPosition_byDefault_returnsZero() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    long contentPositionMs =
        threadTestRule.getHandler().postAndSync(controller::getContentPosition);
    assertThat(contentPositionMs).isEqualTo(0);
  }

  @Test
  public void getContentBufferedPosition_byDefault_returnsZero() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    long contentBufferedPositionMs =
        threadTestRule.getHandler().postAndSync(controller::getContentBufferedPosition);
    assertThat(contentBufferedPositionMs).isEqualTo(0);
  }

  @Test
  public void getBufferedPosition_byDefault_returnsZero() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    long bufferedPositionMs =
        threadTestRule.getHandler().postAndSync(controller::getBufferedPosition);
    assertThat(bufferedPositionMs).isEqualTo(0);
  }

  @Test
  public void getBufferedPosition_withLessThanPosition_adjustsToPosition() throws Exception {
    long testPositionMs = 300L;
    long testBufferedPositionMs = 100L;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPositionMs, /* playbackSpeed= */ 1.0f)
            .setBufferedPosition(testBufferedPositionMs)
            .build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    long bufferedPositionMs =
        threadTestRule.getHandler().postAndSync(controller::getBufferedPosition);
    assertThat(bufferedPositionMs).isEqualTo(testPositionMs);
  }

  @Test
  public void getBufferedPosition_withGreaterThanDuration_adjustsToDuration() throws Exception {
    long testDurationMs = 100L;
    long testBufferedPositionMs = 200L;
    session.setMetadata(
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_DURATION, testDurationMs).build());
    session.setPlaybackState(
        new PlaybackStateCompat.Builder().setBufferedPosition(testBufferedPositionMs).build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    long bufferedPositionMs =
        threadTestRule.getHandler().postAndSync(controller::getBufferedPosition);
    assertThat(bufferedPositionMs).isEqualTo(testDurationMs);
  }

  @Test
  public void getBufferedPosition() throws Exception {
    long testPositionMs = 300L;
    long testBufferedPositionMs = 331L;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPositionMs, /* playbackSpeed= */ 1.0f)
            .setBufferedPosition(testBufferedPositionMs)
            .build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    long bufferedPositionMs =
        threadTestRule.getHandler().postAndSync(controller::getBufferedPosition);
    assertThat(bufferedPositionMs).isEqualTo(testBufferedPositionMs);
  }

  @Test
  public void getTotalBufferedDuration() throws Exception {
    long testCurrentPositionMs = 224L;
    long testBufferedPositionMs = 331L;
    long testTotalBufferedDurationMs = testBufferedPositionMs - testCurrentPositionMs;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PAUSED, testCurrentPositionMs, /* playbackSpeed= */ 1.0f)
            .setBufferedPosition(testBufferedPositionMs)
            .build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    long totalBufferedDurationMs =
        threadTestRule.getHandler().postAndSync(controller::getTotalBufferedDuration);
    assertThat(totalBufferedDurationMs).isEqualTo(testTotalBufferedDurationMs);
  }

  @Test
  public void prepare_empty_correctInitializationState() throws Exception {
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_NONE, /* position= */ 0, /* playbackSpeed= */ 0.0f)
            .build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    // Assert the constructed timeline and start index after connecting to an empty session.
    int mediaItemCount = threadTestRule.getHandler().postAndSync(controller::getMediaItemCount);
    int currentMediaItemIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItemIndex);
    assertThat(mediaItemCount).isEqualTo(0);
    assertThat(currentMediaItemIndex).isEqualTo(0);
  }

  @Test
  public void prepare_withMetadata_callsPrepareFromMediaId() throws Exception {
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_NONE, /* position= */ 0, /* playbackSpeed= */ 0.0f)
            .build());
    session.setMetadata(
        new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "mediaItem_2")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Title")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "Subtitle")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Artist")
            .build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch countDownLatch = new CountDownLatch(1);
    controller.addListener(
        new Player.Listener() {
          @Override
          public void onEvents(Player player, Player.Events events) {
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
              countDownLatch.countDown();
            }
          }
        });

    // Assert the constructed timeline and start index for preparation.
    int mediaItemCount = threadTestRule.getHandler().postAndSync(controller::getMediaItemCount);
    int currentMediaItemIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItemIndex);
    assertThat(mediaItemCount).isEqualTo(1);
    assertThat(currentMediaItemIndex).isEqualTo(0);

    threadTestRule.getHandler().postAndSync(controller::prepare);

    // Assert whether the correct preparation method has been called and received by the session.
    assertThat(countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    int callbackMethodCount =
        session.getCallbackMethodCount(
            MediaSessionCompatProviderService.METHOD_ON_PREPARE_FROM_MEDIA_ID);
    assertThat(callbackMethodCount).isEqualTo(1);
  }

  @Test
  public void prepare_withMetadataAndActiveQueueItemId_callsPrepareFromMediaId() throws Exception {
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setActiveQueueItemId(4)
            .setState(PlaybackStateCompat.STATE_NONE, /* position= */ 0, /* playbackSpeed= */ 0.0f)
            .build());
    session.setMetadata(
        new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "mediaItem_2")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Title")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "Subtitle")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Artist")
            .build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch countDownLatch = new CountDownLatch(1);
    controller.addListener(
        new Player.Listener() {
          @Override
          public void onEvents(Player player, Player.Events events) {
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
              countDownLatch.countDown();
            }
          }
        });

    // Assert the constructed timeline and start index for preparation.
    int mediaItemCount = threadTestRule.getHandler().postAndSync(controller::getMediaItemCount);
    int currentMediaItemIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItemIndex);
    assertThat(mediaItemCount).isEqualTo(1);
    assertThat(currentMediaItemIndex).isEqualTo(0);

    threadTestRule.getHandler().postAndSync(controller::prepare);

    // Assert whether the correct preparation method has been called and received by the session.
    assertThat(countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    int callbackMethodCount =
        session.getCallbackMethodCount(
            MediaSessionCompatProviderService.METHOD_ON_PREPARE_FROM_MEDIA_ID);
    assertThat(callbackMethodCount).isEqualTo(1);
  }

  @Test
  public void prepare_withQueue_callsPrepare() throws Exception {
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItems(10);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testMediaItems);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_NONE, /* position= */ 0, /* playbackSpeed= */ 0.0f)
            .build());
    session.setQueue(testQueue);
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch countDownLatch = new CountDownLatch(1);
    controller.addListener(
        new Player.Listener() {
          @Override
          public void onEvents(Player player, Player.Events events) {
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
              countDownLatch.countDown();
            }
          }
        });

    // Assert the constructed timeline and start index for preparation.
    int mediaItemCount = threadTestRule.getHandler().postAndSync(controller::getMediaItemCount);
    int currentMediaItemIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItemIndex);
    assertThat(mediaItemCount).isEqualTo(10);
    assertThat(currentMediaItemIndex).isEqualTo(0);

    threadTestRule.getHandler().postAndSync(controller::prepare);

    // Assert whether the correct preparation method has been called and received by the session.
    assertThat(countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    int callbackMethodCount =
        session.getCallbackMethodCount(MediaSessionCompatProviderService.METHOD_ON_PREPARE);
    assertThat(callbackMethodCount).isEqualTo(1);
  }

  @Test
  public void prepare_withQueueAndActiveQueueItemId_callsPrepare() throws Exception {
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItems(10);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testMediaItems);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setActiveQueueItemId(5)
            .setState(PlaybackStateCompat.STATE_NONE, /* position= */ 0, /* playbackSpeed= */ 0.0f)
            .build());
    session.setQueue(testQueue);
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch countDownLatch = new CountDownLatch(1);
    controller.addListener(
        new Player.Listener() {
          @Override
          public void onEvents(Player player, Player.Events events) {
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
              countDownLatch.countDown();
            }
          }
        });

    // Assert the constructed timeline and start index for preparation.
    int mediaItemCount = threadTestRule.getHandler().postAndSync(controller::getMediaItemCount);
    int currentMediaItemIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItemIndex);
    assertThat(mediaItemCount).isEqualTo(10);
    assertThat(currentMediaItemIndex).isEqualTo(5);

    threadTestRule.getHandler().postAndSync(controller::prepare);

    // Assert whether the correct preparation method has been called and received by the session.
    assertThat(countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    int callbackMethodCount =
        session.getCallbackMethodCount(MediaSessionCompatProviderService.METHOD_ON_PREPARE);
    assertThat(callbackMethodCount).isEqualTo(1);
  }

  @Test
  public void prepare_withQueueAndMetadata_callsPrepareFromMediaId() throws Exception {
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItems(10);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testMediaItems);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_NONE, /* position= */ 0, /* playbackSpeed= */ 0.0f)
            .build());
    session.setMetadata(
        new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "mediaItem_2")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Title")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "Subtitle")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Artist")
            .build());
    session.setQueue(testQueue);
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch countDownLatch = new CountDownLatch(1);
    controller.addListener(
        new Player.Listener() {
          @Override
          public void onEvents(Player player, Player.Events events) {
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
              countDownLatch.countDown();
            }
          }
        });

    // Assert the constructed timeline and start index for preparation.
    int mediaItemCount = threadTestRule.getHandler().postAndSync(controller::getMediaItemCount);
    int currentMediaItemIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItemIndex);
    assertThat(mediaItemCount).isEqualTo(11);
    assertThat(currentMediaItemIndex).isEqualTo(10);

    threadTestRule.getHandler().postAndSync(controller::prepare);

    // Assert whether the correct preparation method has been called and received by the session.
    assertThat(countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    int callbackMethodCount =
        session.getCallbackMethodCount(
            MediaSessionCompatProviderService.METHOD_ON_PREPARE_FROM_MEDIA_ID);
    assertThat(callbackMethodCount).isEqualTo(1);
  }

  @Test
  public void prepare_withQueueAndMetadataAndActiveQueueItemId_callsPrepare() throws Exception {
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItems(10);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testMediaItems);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setActiveQueueItemId(4)
            .setState(PlaybackStateCompat.STATE_NONE, /* position= */ 0, /* playbackSpeed= */ 0.0f)
            .build());
    session.setMetadata(
        new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "mediaItem_5")
            .build());
    session.setQueue(testQueue);
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch countDownLatch = new CountDownLatch(1);
    controller.addListener(
        new Player.Listener() {
          @Override
          public void onEvents(Player player, Player.Events events) {
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
              countDownLatch.countDown();
            }
          }
        });

    // Assert the constructed timeline and start index for preparation.
    int mediaItemCount = threadTestRule.getHandler().postAndSync(controller::getMediaItemCount);
    int currentMediaItemIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItemIndex);
    assertThat(mediaItemCount).isEqualTo(10);
    assertThat(currentMediaItemIndex).isEqualTo(4);

    threadTestRule.getHandler().postAndSync(controller::prepare);

    // Assert whether the correct preparation method has been called and received by the session.
    assertThat(countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    int callbackMethodCount =
        session.getCallbackMethodCount(MediaSessionCompatProviderService.METHOD_ON_PREPARE);
    assertThat(callbackMethodCount).isEqualTo(1);
  }

  @Nullable
  private Bitmap getBitmapFromMetadata(MediaMetadata metadata) throws Exception {
    @Nullable Bitmap bitmap = null;
    @Nullable ListenableFuture<Bitmap> bitmapFuture = bitmapLoader.loadBitmapFromMetadata(metadata);
    if (bitmapFuture != null) {
      bitmap = bitmapFuture.get(10, SECONDS);
    }
    return bitmap;
  }
}
