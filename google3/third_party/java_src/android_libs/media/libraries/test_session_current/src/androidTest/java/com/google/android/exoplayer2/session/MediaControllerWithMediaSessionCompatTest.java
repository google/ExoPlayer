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
package com.google.android.exoplayer2.session;

import static com.google.android.exoplayer2.Player.STATE_BUFFERING;
import static com.google.android.exoplayer2.Player.STATE_READY;
import static com.google.android.exoplayer2.session.LegacyMediaMetadata.METADATA_KEY_ADVERTISEMENT;
import static com.google.android.exoplayer2.session.LegacyMediaMetadata.METADATA_KEY_DURATION;
import static com.google.android.exoplayer2.session.LegacyMediaMetadata.METADATA_KEY_MEDIA_ID;
import static com.google.android.exoplayer2.session.MediaConstants.ARGUMENT_CAPTIONING_ENABLED;
import static com.google.android.exoplayer2.session.MediaConstants.SESSION_COMMAND_ON_CAPTIONING_ENABLED_CHANGED;
import static com.google.android.exoplayer2.session.SessionResult.RESULT_INFO_SKIPPED;
import static com.google.android.exoplayer2.session.SessionResult.RESULT_SUCCESS;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.DEFAULT_TEST_NAME;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.session.PlaybackStateCompat.CustomAction;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.VolumeProviderCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.ext.truth.os.BundleSubject;
import androidx.test.filters.MediumTest;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Player.PositionInfo;
import com.google.android.exoplayer2.Player.RepeatMode;
import com.google.android.exoplayer2.Player.State;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.session.MediaController.ControllerCallback;
import com.google.android.exoplayer2.session.vct.common.HandlerThreadTestRule;
import com.google.android.exoplayer2.session.vct.common.MainLooperTestRule;
import com.google.android.exoplayer2.session.vct.common.MockActivity;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

  private final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);
  private final MediaControllerTestRule controllerTestRule =
      new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  private Context context;
  private RemoteMediaSessionCompat session;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    session = new RemoteMediaSessionCompat(DEFAULT_TEST_NAME, context);
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
  public void disconnected_bySessionRelease() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    threadTestRule.getHandler().postAndSync(controller::release);
    controllerTestRule.waitForDisconnect(controller, /* expected= */ true);
    assertThat(controller.isConnected()).isFalse();
  }

  @Test
  public void disconnected_byControllerClose() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    threadTestRule.getHandler().postAndSync(controller::release);
    controllerTestRule.waitForDisconnect(controller, /* expected= */ true);
    assertThat(controller.isConnected()).isFalse();
  }

  @Test
  public void close_twice_doesNotCrash() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    threadTestRule.getHandler().postAndSync(controller::release);
    threadTestRule.getHandler().postAndSync(controller::release);
  }

  @Test
  public void close_beforeConnected_doesNotCrash() throws Exception {
    MediaController controller =
        controllerTestRule.createController(
            session.getSessionToken(), /* waitForConnect= */ false, /* callback= */ null);
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
        MediaUtils.convertToMediaMetadataCompat(
            new LegacyMediaMetadata.Builder()
                .putString(METADATA_KEY_MEDIA_ID, "gettersAfterConnected")
                .putLong(METADATA_KEY_DURATION, duration)
                .putLong(METADATA_KEY_ADVERTISEMENT, isPlayingAd ? 1 : 0)
                .build());

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

    AtomicInteger playerStateRef = new AtomicInteger();
    AtomicInteger bufferingStateRef = new AtomicInteger();
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
  }

  @Test
  public void getPackageName() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    assertThat(controller.getConnectedToken().getPackageName()).isEqualTo(SUPPORT_APP_PACKAGE_NAME);
  }

  @Test
  public void getSessionActivity() throws Exception {
    Intent sessionActivity = new Intent(context, MockActivity.class);
    PendingIntent pi = PendingIntent.getActivity(context, 0, sessionActivity, 0);
    session.setSessionActivity(pi);

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    PendingIntent sessionActivityOut = controller.getSessionActivity();
    assertThat(sessionActivityOut).isNotNull();
    if (Build.VERSION.SDK_INT >= 17) {
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
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onRepeatModeChanged(@RepeatMode int repeatMode) {
            repeatModeFromParamRef.set(repeatMode);
            repeatModeFromGetterRef.set(controller.getRepeatMode());
            latch.countDown();
          }
        };
    controller.addListener(callback);

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
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            shuffleModeFromParamRef.set(shuffleModeEnabled);
            shuffleModeFromGetterRef.set(controller.getShuffleModeEnabled());
            latch.countDown();
          }
        };
    controller.addListener(callback);

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
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineFromGetterRef.set(controller.getCurrentTimeline());
            reasonRef.set(reason);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    Timeline testTimeline = MediaTestUtils.createTimeline(/* windowCount= */ 2);
    List<QueueItem> testQueue =
        MediaUtils.convertToQueueItemList(MediaUtils.convertToMediaItemList(testTimeline));
    session.setQueue(testQueue);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertMediaIdEquals(testTimeline, timelineFromParamRef.get());
    MediaTestUtils.assertMediaIdEquals(testTimeline, timelineFromParamRef.get());
    assertThat(reasonRef.get()).isEqualTo(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
  }

  @Test
  public void setQueue_withNull_notifiesEmptyTimeline() throws Exception {
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 2);
    List<QueueItem> queue =
        MediaUtils.convertToQueueItemList(MediaUtils.convertToMediaItemList(timeline));
    session.setQueue(queue);

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Timeline> timelineRef = new AtomicReference<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineRef.set(timeline);
            latch.countDown();
          }
        };
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    controller.addListener(callback);

    session.setQueue(null);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(timelineRef.get().getWindowCount()).isEqualTo(0);
    assertThat(timelineRef.get().getPeriodCount()).isEqualTo(0);
  }

  @Test
  public void setQueueTitle_updatesAndNotifiesPlaylistMetadata() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaMetadata> metadataFromParamRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> metadataFromGetterRef = new AtomicReference<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaylistMetadataChanged(MediaMetadata playlistMetadata) {
            metadataFromParamRef.set(playlistMetadata);
            metadataFromGetterRef.set(controller.getPlaylistMetadata());
            latch.countDown();
          }
        };
    controller.addListener(callback);

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
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem item, @Player.MediaItemTransitionReason int reason) {
            itemRef.set(item);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    String testMediaId = "testMediaId";
    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder().putText(METADATA_KEY_MEDIA_ID, testMediaId).build();
    session.setMetadata(metadata);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(itemRef.get().mediaId).isEqualTo(testMediaId);
  }

  @Test
  public void isPlayingAd_withMetadataWithAd_returnsTrue() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean isPlayingAdRef = new AtomicBoolean();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            isPlayingAdRef.set(controller.isPlayingAd());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder()
            .putLong(MediaMetadataCompat.METADATA_KEY_ADVERTISEMENT, 1)
            .build();
    session.setMetadata(metadata);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(isPlayingAdRef.get()).isTrue();
  }

  @Test
  public void setMediaUri_resultSetAfterPrepare() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    Uri testUri = Uri.parse("androidx://test");
    ListenableFuture<SessionResult> future =
        threadTestRule
            .getHandler()
            .postAndSync(() -> controller.setMediaUri(testUri, /* extras= */ null));

    SessionResult result;
    try {
      result = future.get(NO_RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      assertWithMessage("TimeoutException is expected").fail();
    } catch (TimeoutException e) {
      // expected.
    }

    threadTestRule.getHandler().postAndSync(controller::prepare);

    result = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
  }

  @Test
  public void setMediaUri_resultSetAfterPlay() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    Uri testUri = Uri.parse("androidx://test");
    ListenableFuture<SessionResult> future =
        threadTestRule
            .getHandler()
            .postAndSync(() -> controller.setMediaUri(testUri, /* extras= */ null));

    SessionResult result;
    try {
      result = future.get(NO_RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      assertWithMessage("TimeoutException is expected").fail();
    } catch (TimeoutException e) {
      // expected.
    }

    threadTestRule.getHandler().postAndSync(controller::play);

    result = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
  }

  @Test
  public void setMediaUris_multipleCalls_previousCallReturnsResultInfoSkipped() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    Uri testUri1 = Uri.parse("androidx://test1");
    Uri testUri2 = Uri.parse("androidx://test2");
    ListenableFuture<SessionResult> future1 =
        threadTestRule
            .getHandler()
            .postAndSync(() -> controller.setMediaUri(testUri1, /* extras= */ null));
    ListenableFuture<SessionResult> future2 =
        threadTestRule
            .getHandler()
            .postAndSync(() -> controller.setMediaUri(testUri2, /* extras= */ null));

    threadTestRule.getHandler().postAndSync(controller::prepare);

    SessionResult result1 = future1.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    SessionResult result2 = future2.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    assertThat(result1.resultCode).isEqualTo(RESULT_INFO_SKIPPED);
    assertThat(result2.resultCode).isEqualTo(RESULT_SUCCESS);
  }

  @Test
  public void seekToDefaultPosition_withWindowIndex_updatesExpectedWindowIndex() throws Exception {
    List<MediaItem> testList = MediaTestUtils.createConvergedMediaItems(3);
    List<QueueItem> testQueue = MediaUtils.convertToQueueItemList(testList);
    session.setQueue(testQueue);
    session.setPlaybackState(/* state= */ null);
    int testWindowIndex = 2;
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger currentWindowIndexRef = new AtomicInteger();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            currentWindowIndexRef.set(controller.getCurrentWindowIndex());
            latch.countDown();
          }
        };
    controller.addListener(callback);
    threadTestRule
        .getHandler()
        .postAndSync(() -> controller.seekToDefaultPosition(testWindowIndex));

    session.setMetadata(
        new MediaMetadataCompat.Builder()
            .putString(
                MediaMetadataCompat.METADATA_KEY_MEDIA_ID, testList.get(testWindowIndex).mediaId)
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(currentWindowIndexRef.get()).isEqualTo(testWindowIndex);
  }

  @Test
  public void seekTo_withWindowIndex_updatesExpectedWindowIndex() throws Exception {
    List<MediaItem> testList = MediaTestUtils.createConvergedMediaItems(3);
    List<QueueItem> testQueue = MediaUtils.convertToQueueItemList(testList);
    session.setQueue(testQueue);
    session.setPlaybackState(/* state= */ null);
    long testPositionMs = 23L;
    int testWindowIndex = 2;
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger windowIndexFromParamRef = new AtomicInteger();
    AtomicInteger windowIndexFromGetterRef = new AtomicInteger();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            windowIndexFromParamRef.set(newPosition.windowIndex);
            windowIndexFromGetterRef.set(controller.getCurrentWindowIndex());
            latch.countDown();
          }
        };
    controller.addListener(callback);
    threadTestRule
        .getHandler()
        .postAndSync(() -> controller.seekTo(testWindowIndex, testPositionMs));

    session.setMetadata(
        new MediaMetadataCompat.Builder()
            .putString(
                MediaMetadataCompat.METADATA_KEY_MEDIA_ID, testList.get(testWindowIndex).mediaId)
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(windowIndexFromParamRef.get()).isEqualTo(testWindowIndex);
    assertThat(windowIndexFromGetterRef.get()).isEqualTo(testWindowIndex);
  }

  @Test
  public void setMetadata_withAd_notifiesPositionDiscontinuityByAdInsertion() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger positionDiscontinuityReasonRef = new AtomicInteger();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            latch.countDown();
          }
        };
    controller.addListener(callback);

    String testMediaId = "testMediaId";
    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder()
            .putText(METADATA_KEY_MEDIA_ID, testMediaId)
            .putLong(METADATA_KEY_ADVERTISEMENT, 1)
            .build();
    session.setMetadata(metadata);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(positionDiscontinuityReasonRef.get())
        .isEqualTo(Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
  }

  @Test
  public void setPlaybackState_withActiveQueueItemId_notifiesCurrentMediaItem() throws Exception {
    List<MediaItem> testList = MediaTestUtils.createConvergedMediaItems(/* size= */ 2);
    List<QueueItem> testQueue = MediaUtils.convertToQueueItemList(testList);
    session.setQueue(testQueue);

    PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();

    // Set the current active queue item to index 'oldItemIndex'.
    int oldItemIndex = 0;
    builder.setActiveQueueItemId(testQueue.get(oldItemIndex).getQueueId());
    session.setPlaybackState(builder.build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaItem> itemRef = new AtomicReference<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem item, @Player.MediaItemTransitionReason int reason) {
            itemRef.set(item);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    // The new playbackState will tell the controller that the active queue item is changed to
    // 'newItemIndex'.
    int newItemIndex = 1;
    builder.setActiveQueueItemId(testQueue.get(newItemIndex).getQueueId());
    session.setPlaybackState(builder.build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertMediaIdEquals(testList.get(newItemIndex), itemRef.get());
  }

  @Test
  public void
      setPlaybackState_withAdjacentQueueItemWhilePlaying_notifiesPositionDiscontinuityBySeek()
          throws Exception {
    long testDuration = 3000;
    List<QueueItem> testQueue = MediaTestUtils.createQueueItems(/* size= */ 2);
    session.setQueue(testQueue);
    session.setMetadata(
        new MediaMetadataCompat.Builder()
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, testDuration)
            .build());

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
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            oldPositionRef.set(oldPosition);
            newPositionRef.set(newPosition);
            positionDiscontinuityReasonRef.set(reason);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    int newItemIndex = 1;
    builder.setActiveQueueItemId(testQueue.get(newItemIndex).getQueueId());
    session.setPlaybackState(builder.build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(positionDiscontinuityReasonRef.get()).isEqualTo(Player.DISCONTINUITY_REASON_SEEK);
    assertThat(oldPositionRef.get().windowIndex).isEqualTo(oldItemIndex);
    assertThat(newPositionRef.get().windowIndex).isEqualTo(newItemIndex);
  }

  @Test
  public void
      setPlaybackState_withAdjacentQueueItemAfterPlaybackDone_notifiesPositionDiscontinuityByTransition()
          throws Exception {
    long testDuration = 3000;
    List<QueueItem> testQueue = MediaTestUtils.createQueueItems(/* size= */ 2);
    session.setQueue(testQueue);
    session.setMetadata(
        new MediaMetadataCompat.Builder()
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, testDuration)
            .build());

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
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            oldPositionRef.set(oldPosition);
            newPositionRef.set(newPosition);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    int newItemIndex = 1;
    builder.setActiveQueueItemId(testQueue.get(newItemIndex).getQueueId());
    builder.setState(
        PlaybackStateCompat.STATE_PLAYING, /* position= */ 0, /* playbackSpeed= */ 1.0f);
    session.setPlaybackState(builder.build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(positionDiscontinuityReasonRef.get())
        .isEqualTo(Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    assertThat(oldPositionRef.get().windowIndex).isEqualTo(oldItemIndex);
    assertThat(newPositionRef.get().windowIndex).isEqualTo(newItemIndex);
  }

  @Test
  public void setPlaybackState_withDistantQueueItem_notifiesPositionDiscontinuityBySeek()
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
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            oldPositionRef.set(oldPosition);
            newPositionRef.set(newPosition);
            positionDiscontinuityReasonRef.set(reason);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    int newItemIndex = 2;
    builder.setActiveQueueItemId(testQueue.get(newItemIndex).getQueueId());
    session.setPlaybackState(builder.build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(positionDiscontinuityReasonRef.get()).isEqualTo(Player.DISCONTINUITY_REASON_SEEK);
    assertThat(oldPositionRef.get().windowIndex).isEqualTo(oldItemIndex);
    assertThat(newPositionRef.get().windowIndex).isEqualTo(newItemIndex);
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
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
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
    controller.addListener(callback);

    long testNewCurrentPositionMs = 900L;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PAUSED, testNewCurrentPositionMs, /* playbackSpeed= */ 1f)
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(positionDiscontinuityReasonRef.get()).isEqualTo(Player.DISCONTINUITY_REASON_SEEK);
    assertThat(oldPositionRef.get().positionMs).isEqualTo(testOldCurrentPositionMs);
    assertThat(newPositionRef.get().positionMs).isEqualTo(testNewCurrentPositionMs);
  }

  @Test
  public void setPlaybackState_fromStateBufferingToPlaying_notifiesReadyState() throws Exception {
    List<MediaItem> testPlaylist = MediaTestUtils.createConvergedMediaItems(/* size= */ 1);
    MediaMetadataCompat metadata =
        MediaUtils.convertToMediaMetadataCompat(testPlaylist.get(0), /* durationMs= */ 1_000);
    long testBufferedPosition = 500;
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
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackStateChanged(@State int state) {
            stateFromParamRef.set(state);
            stateFromGetterRef.set(controller.getPlaybackState());
            bufferedPositionFromGetterRef.set(controller.getBufferedPosition());
            latch.countDown();
          }
        };
    controller.addListener(callback);

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
    List<MediaItem> testPlaylist = MediaTestUtils.createConvergedMediaItems(1);
    MediaMetadataCompat metadata =
        MediaUtils.convertToMediaMetadataCompat(testPlaylist.get(0), /* durationMs= */ 1_000);
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
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackStateChanged(int state) {
            stateFromParamRef.set(state);
            stateFromGetterRef.set(controller.getPlaybackState());
            bufferedPositionFromGetterRef.set(controller.getBufferedPosition());
            latch.countDown();
          }
        };
    controller.addListener(callback);

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
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackStateChanged(int state) {
            stateFromParamRef.set(state);
            stateFromGetterRef.set(controller.getPlaybackState());
            latch.countDown();
          }
        };
    controller.addListener(callback);

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
    AtomicInteger playbackSuppressionReasonFromParamRef = new AtomicInteger();
    AtomicInteger playbackSuppressionReasonFromGetterRef = new AtomicInteger();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayWhenReadyChanged(
              boolean playWhenReady, @Player.PlaybackSuppressionReason int reason) {
            playWhenReadyFromParamRef.set(playWhenReady);
            playWhenReadyFromGetterRef.set(controller.getPlayWhenReady());
            playbackSuppressionReasonFromParamRef.set(reason);
            playbackSuppressionReasonFromGetterRef.set(controller.getPlaybackSuppressionReason());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PLAYING, /* position= */ 0, /* playbackSpeed= */ 1f)
            .build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playWhenReadyFromParamRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(playWhenReadyFromGetterRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(playbackSuppressionReasonFromParamRef.get())
        .isEqualTo(Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    assertThat(playbackSuppressionReasonFromGetterRef.get())
        .isEqualTo(Player.PLAYBACK_SUPPRESSION_REASON_NONE);
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
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackStateChanged(@Player.State int state) {
            playbackStateFromParamRef.set(state);
            playbackStateFromGetterRef.set(controller.getPlaybackState());
            latch.countDown();
          }
        };
    controller.addListener(callback);

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
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackStateChanged(@Player.State int state) {
            playbackStateFromParamRef.set(state);
            playbackStateFromGetterRef.set(controller.getPlaybackState());
            latch.countDown();
          }
        };
    controller.addListener(callback);

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
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            playbackParametersFromParamRef.set(playbackParameters);
            playbackParametersFromGetterRef.set(controller.getPlaybackParameters());
            latch.countDown();
          }
        };
    controller.addListener(callback);

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
  public void setPlaybackToRemote_notifiesDeviceInfoAndVolume() throws Exception {
    int volumeControlType = VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE;
    int maxVolume = 100;
    int currentVolume = 45;

    AtomicReference<DeviceInfo> deviceInfoRef = new AtomicReference<>();
    CountDownLatch latchForDeviceInfo = new CountDownLatch(1);
    CountDownLatch latchForDeviceVolume = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onDeviceInfoChanged(@NonNull DeviceInfo deviceInfo) {
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
    controller.addListener(callback);

    session.setPlaybackToRemote(volumeControlType, maxVolume, currentVolume);

    assertThat(latchForDeviceInfo.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(latchForDeviceVolume.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceInfoRef.get().maxVolume).isEqualTo(maxVolume);
  }

  @Test
  public void setPlaybackToLocal_notifiesDeviceInfoAndVolume() throws Exception {
    if (Build.VERSION.SDK_INT == 21 || Build.VERSION.SDK_INT == 22) {
      // In API 21 and 22, onAudioInfoChanged is not called.
      return;
    }
    session.setPlaybackToRemote(
        VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,
        /* maxVolume= */ 100,
        /* currentVolume= */ 45);

    int testLocalStreamType = AudioManager.STREAM_ALARM;
    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    int maxVolume = audioManager.getStreamMaxVolume(testLocalStreamType);
    int currentVolume = audioManager.getStreamVolume(testLocalStreamType);

    AtomicReference<DeviceInfo> deviceInfoRef = new AtomicReference<>();
    CountDownLatch latchForDeviceInfo = new CountDownLatch(1);
    CountDownLatch latchForDeviceVolume = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onDeviceInfoChanged(@NonNull DeviceInfo deviceInfo) {
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
    controller.addListener(callback);

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
    ControllerCallback callback =
        new ControllerCallback() {
          @Override
          @NonNull
          public ListenableFuture<SessionResult> onCustomCommand(
              @NonNull MediaController controller, @NonNull SessionCommand command, Bundle args) {
            commandRef.set(command);
            argsRef.set(args);
            latch.countDown();
            return new SessionResult(RESULT_SUCCESS).asFuture();
          }
        };
    controllerTestRule.createController(
        session.getSessionToken(), /* waitForConnect= */ true, callback);

    String event = "customCommand";
    Bundle extras = TestUtils.createTestBundle();
    session.sendSessionEvent(event, extras);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(commandRef.get().customAction).isEqualTo(event);
    assertThat(TestUtils.equals(extras, argsRef.get())).isTrue();
  }

  @Test
  public void setPlaybackState_withCustomAction_notifiesCustomLayout() throws Exception {
    CustomAction testCustomAction1 =
        new CustomAction.Builder("testCustomAction1", "testName1", 1).build();
    CustomAction testCustomAction2 =
        new CustomAction.Builder("testCustomAction2", "testName2", 2).build();
    CountDownLatch latch = new CountDownLatch(2);
    ControllerCallback callback =
        new ControllerCallback() {
          @Override
          @NonNull
          public ListenableFuture<SessionResult> onSetCustomLayout(
              @NonNull MediaController controller, @NonNull List<CommandButton> layout) {
            assertThat(layout).hasSize(1);
            CommandButton button = layout.get(0);

            switch ((int) latch.getCount()) {
              case 2:
                assertThat(button.sessionCommand.customAction)
                    .isEqualTo(testCustomAction1.getAction());
                assertThat(button.displayName.toString())
                    .isEqualTo(testCustomAction1.getName().toString());
                assertThat(button.iconResId).isEqualTo(testCustomAction1.getIcon());
                break;
              case 1:
                assertThat(button.sessionCommand.customAction)
                    .isEqualTo(testCustomAction2.getAction());
                assertThat(button.displayName.toString())
                    .isEqualTo(testCustomAction2.getName().toString());
                assertThat(button.iconResId).isEqualTo(testCustomAction2.getIcon());
                break;
            }
            latch.countDown();
            return new SessionResult(RESULT_SUCCESS).asFuture();
          }
        };
    session.setPlaybackState(
        new PlaybackStateCompat.Builder().addCustomAction(testCustomAction1).build());
    // onSetCustomLayout will be called when its connected
    controllerTestRule.createController(
        session.getSessionToken(), /* waitForConnect= */ true, callback);
    // onSetCustomLayout will be called again when the custom action in the playback state is
    // changed.
    session.setPlaybackState(
        new PlaybackStateCompat.Builder().addCustomAction(testCustomAction2).build());
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void setPlaybackState_withCustomAction_notifiesAvailableCommands() throws Exception {
    CustomAction testCustomAction1 =
        new CustomAction.Builder("testCustomAction1", "testName1", 1).build();
    CustomAction testCustomAction2 =
        new CustomAction.Builder("testCustomAction2", "testName2", 2).build();
    CountDownLatch latch = new CountDownLatch(1);
    ControllerCallback callback =
        new ControllerCallback() {
          @Override
          public void onAvailableSessionCommandsChanged(
              MediaController controller, SessionCommands commands) {
            assertThat(
                    commands.contains(
                        new SessionCommand(
                            testCustomAction1.getAction(), testCustomAction1.getExtras())))
                .isFalse();
            assertThat(
                    commands.contains(
                        new SessionCommand(
                            testCustomAction2.getAction(), testCustomAction2.getExtras())))
                .isTrue();
            latch.countDown();
          }
        };
    session.setPlaybackState(
        new PlaybackStateCompat.Builder().addCustomAction(testCustomAction1).build());
    controllerTestRule.createController(
        session.getSessionToken(), /* waitForConnect= */ true, callback);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder().addCustomAction(testCustomAction2).build());
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void setCaptioningEnabled_notifiesCustomCommand() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<SessionCommand> commandRef = new AtomicReference<>();
    AtomicReference<Bundle> argsRef = new AtomicReference<>();
    ControllerCallback callback =
        new ControllerCallback() {
          @Override
          @NonNull
          public ListenableFuture<SessionResult> onCustomCommand(
              @NonNull MediaController controller,
              @NonNull SessionCommand command,
              @Nullable Bundle args) {
            commandRef.set(command);
            argsRef.set(args);
            latch.countDown();
            return new SessionResult(RESULT_SUCCESS).asFuture();
          }
        };
    controllerTestRule.createController(session.getSessionToken(), true, callback);

    session.setCaptioningEnabled(true);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(commandRef.get().customAction)
        .isEqualTo(SESSION_COMMAND_ON_CAPTIONING_ENABLED_CHANGED);
    BundleSubject.assertThat(argsRef.get()).bool(ARGUMENT_CAPTIONING_ENABLED).isTrue();
  }
}
