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

import static androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
import static androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION;
import static androidx.media3.common.Player.EVENT_MEDIA_METADATA_CHANGED;
import static androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY;
import static androidx.media3.common.Player.EVENT_TIMELINE_CHANGED;
import static androidx.media3.common.Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED;
import static androidx.media3.test.session.common.CommonConstants.DEFAULT_TEST_NAME;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.annotation.Nullable;
import androidx.media3.common.FlagSet;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Events;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Timeline;
import androidx.media3.common.Timeline.Window;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSourceBitmapLoader;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/** Tests for {@link MediaController}'s aggregating {@link MediaSessionCompat} callbacks. */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MediaControllerMediaSessionCompatCallbackAggregationTest {

  private final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule("MCwMSCTest");
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
  public void getters_withValidQueueAndQueueIdAndMetadata() throws Exception {
    int testSize = 3;
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItemsWithArtworkData(testSize);
    List<QueueItem> testQueue = convertToQueueItems(testMediaItems);
    int testMediaItemIndex = 1;
    MediaMetadataCompat testMediaMetadataCompat = createMediaMetadataCompat();
    @RatingCompat.Style int testRatingType = RatingCompat.RATING_HEART;
    MediaMetadata testMediaMetadata =
        LegacyConversions.convertToMediaMetadata(testMediaMetadataCompat, testRatingType);
    MediaItem testCurrentMediaItem =
        new MediaItem.Builder()
            .setMediaId(testMediaItems.get(testMediaItemIndex).mediaId)
            .setMediaMetadata(testMediaMetadata)
            .build();
    Events testEvents =
        new Events(
            new FlagSet.Builder()
                .addAll(
                    EVENT_MEDIA_ITEM_TRANSITION,
                    EVENT_MEDIA_METADATA_CHANGED,
                    EVENT_POSITION_DISCONTINUITY,
                    EVENT_TIMELINE_CHANGED)
                .build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(5);
    AtomicReference<Timeline> timelineRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<MediaItem> mediaItemRef = new AtomicReference<>();
    AtomicInteger mediaItemTransitionReasonRef = new AtomicInteger();
    AtomicReference<PositionInfo> positionInfoRef = new AtomicReference<>();
    AtomicInteger discontinuityReasonRef = new AtomicInteger();
    AtomicReference<MediaMetadata> metadataRef = new AtomicReference<>();
    AtomicReference<Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineRef.set(timeline);
            timelineChangeReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
            mediaItemRef.set(mediaItem);
            mediaItemTransitionReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition,
              PositionInfo newPosition,
              @Player.DiscontinuityReason int reason) {
            positionInfoRef.set(newPosition);
            discontinuityReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            metadataRef.set(mediaMetadata);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Events events) {
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setQueue(testQueue);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setActiveQueueItemId(testQueue.get(testMediaItemIndex).getQueueId())
            .build());
    session.setMetadata(testMediaMetadataCompat);
    session.setRatingType(testRatingType);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(mediaItemRef.get()).isEqualTo(testCurrentMediaItem);
    for (int i = 0; i < timelineRef.get().getWindowCount(); i++) {
      MediaItem mediaItem = timelineRef.get().getWindow(i, new Window()).mediaItem;
      MediaItem expectedMediaItem =
          (i == testMediaItemIndex) ? testCurrentMediaItem : testMediaItems.get(i);
      if (Util.SDK_INT < 21) {
        // Bitmap conversion and back gives not exactly the same byte array below API 21
        MediaMetadata mediaMetadata =
            mediaItem
                .mediaMetadata
                .buildUpon()
                .setArtworkData(/* artworkData= */ null, /* artworkDataType= */ null)
                .build();
        MediaMetadata expectedMediaMetadata =
            expectedMediaItem
                .mediaMetadata
                .buildUpon()
                .setArtworkData(/* artworkData= */ null, /* artworkDataType= */ null)
                .build();
        mediaItem = mediaItem.buildUpon().setMediaMetadata(mediaMetadata).build();
        expectedMediaItem =
            expectedMediaItem.buildUpon().setMediaMetadata(expectedMediaMetadata).build();
      }
      assertThat(mediaItem).isEqualTo(expectedMediaItem);
    }
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(mediaItemTransitionReasonRef.get())
        .isEqualTo(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
    assertThat(positionInfoRef.get().mediaItemIndex).isEqualTo(testMediaItemIndex);
    assertThat(discontinuityReasonRef.get()).isEqualTo(DISCONTINUITY_REASON_AUTO_TRANSITION);
    assertThat(metadataRef.get()).isEqualTo(testMediaMetadata);
    assertThat(eventsRef.get()).isEqualTo(testEvents);

    Timeline currentTimeline =
        threadTestRule.getHandler().postAndSync(controller::getCurrentTimeline);
    assertThat(currentTimeline).isEqualTo(timelineRef.get());
    int currentMediaItemIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItemIndex);
    assertThat(currentMediaItemIndex).isEqualTo(testMediaItemIndex);
    MediaItem currentMediaItem =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItem);
    assertThat(currentMediaItem).isEqualTo(testCurrentMediaItem);
    MediaMetadata currentMediaMetadata =
        threadTestRule.getHandler().postAndSync(controller::getMediaMetadata);
    assertThat(currentMediaMetadata).isEqualTo(currentMediaItem.mediaMetadata);
  }

  @Test
  public void getters_withValidQueueAndMetadataButWithInvalidQueueId() throws Exception {
    int testSize = 3;
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItems(testSize);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testMediaItems);
    MediaMetadataCompat testMediaMetadataCompat = createMediaMetadataCompat();
    @RatingCompat.Style int testRatingType = RatingCompat.RATING_HEART;
    MediaMetadata testMediaMetadata =
        LegacyConversions.convertToMediaMetadata(testMediaMetadataCompat, testRatingType);
    Events testEvents =
        new Events(
            new FlagSet.Builder()
                .addAll(
                    EVENT_MEDIA_ITEM_TRANSITION,
                    EVENT_MEDIA_METADATA_CHANGED,
                    EVENT_POSITION_DISCONTINUITY,
                    EVENT_TIMELINE_CHANGED)
                .build());
    int testMediaItemIndex = testSize; // Index of fake item.
    testMediaItems.add(
        LegacyConversions.convertToMediaItem(testMediaMetadataCompat, testRatingType));

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(5);
    AtomicReference<Timeline> timelineRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<MediaItem> mediaItemRef = new AtomicReference<>();
    AtomicInteger mediaItemTransitionReasonRef = new AtomicInteger();
    AtomicReference<PositionInfo> positionInfoRef = new AtomicReference<>();
    AtomicInteger discontinuityReasonRef = new AtomicInteger();
    AtomicReference<MediaMetadata> metadataRef = new AtomicReference<>();
    AtomicReference<Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineRef.set(timeline);
            timelineChangeReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
            mediaItemRef.set(mediaItem);
            mediaItemTransitionReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition,
              PositionInfo newPosition,
              @Player.DiscontinuityReason int reason) {
            positionInfoRef.set(newPosition);
            discontinuityReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            metadataRef.set(mediaMetadata);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Events events) {
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setQueue(testQueue);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder().setActiveQueueItemId(/* id= */ -1).build());
    session.setMetadata(testMediaMetadataCompat);
    session.setRatingType(testRatingType);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertTimelineEqualsToMediaItems(timelineRef.get(), testMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(mediaItemRef.get()).isEqualTo(testMediaItems.get(testMediaItemIndex));
    assertThat(mediaItemTransitionReasonRef.get())
        .isEqualTo(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
    assertThat(positionInfoRef.get().mediaItemIndex).isEqualTo(testMediaItemIndex);
    assertThat(discontinuityReasonRef.get()).isEqualTo(DISCONTINUITY_REASON_AUTO_TRANSITION);
    assertThat(metadataRef.get()).isEqualTo(testMediaMetadata);
    assertThat(eventsRef.get()).isEqualTo(testEvents);

    Timeline currentTimeline =
        threadTestRule.getHandler().postAndSync(controller::getCurrentTimeline);
    assertThat(currentTimeline).isEqualTo(timelineRef.get());
    int currentMediaItemIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItemIndex);
    assertThat(currentMediaItemIndex).isEqualTo(testMediaItemIndex);
    MediaItem currentMediaItem =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItem);
    assertThat(currentMediaItem).isEqualTo(testMediaItems.get(testMediaItemIndex));
    MediaMetadata currentMediaMetadata =
        threadTestRule.getHandler().postAndSync(controller::getMediaMetadata);
    assertThat(currentMediaMetadata).isEqualTo(currentMediaItem.mediaMetadata);
  }

  @Test
  public void getters_withValidQueueAndQueueIdWithoutMetadata() throws Exception {
    int testSize = 3;
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItems(testSize);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testMediaItems);
    @RatingCompat.Style int testRatingType = RatingCompat.RATING_HEART;
    Events testEvents =
        new Events(
            new FlagSet.Builder()
                .addAll(
                    EVENT_MEDIA_ITEM_TRANSITION,
                    EVENT_POSITION_DISCONTINUITY,
                    EVENT_TIMELINE_CHANGED)
                .build());
    int testMediaItemIndex = 1;

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(4);
    AtomicReference<Timeline> timelineRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<MediaItem> mediaItemRef = new AtomicReference<>();
    AtomicInteger mediaItemTransitionReasonRef = new AtomicInteger();
    AtomicReference<PositionInfo> positionInfoRef = new AtomicReference<>();
    AtomicInteger discontinuityReasonRef = new AtomicInteger();
    AtomicReference<Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineRef.set(timeline);
            timelineChangeReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
            mediaItemRef.set(mediaItem);
            mediaItemTransitionReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition,
              PositionInfo newPosition,
              @Player.DiscontinuityReason int reason) {
            positionInfoRef.set(newPosition);
            discontinuityReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Events events) {
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setQueue(testQueue);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setActiveQueueItemId(testQueue.get(testMediaItemIndex).getQueueId())
            .build());
    session.setRatingType(testRatingType);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertTimelineEqualsToMediaItems(timelineRef.get(), testMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(mediaItemRef.get()).isEqualTo(testMediaItems.get(testMediaItemIndex));
    assertThat(mediaItemTransitionReasonRef.get())
        .isEqualTo(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
    assertThat(positionInfoRef.get().mediaItemIndex).isEqualTo(testMediaItemIndex);
    assertThat(discontinuityReasonRef.get()).isEqualTo(DISCONTINUITY_REASON_AUTO_TRANSITION);
    assertThat(eventsRef.get()).isEqualTo(testEvents);

    Timeline currentTimeline =
        threadTestRule.getHandler().postAndSync(controller::getCurrentTimeline);
    assertThat(currentTimeline).isEqualTo(timelineRef.get());
    int currentMediaItemIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItemIndex);
    assertThat(currentMediaItemIndex).isEqualTo(testMediaItemIndex);
    MediaItem currentMediaItem =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItem);
    assertThat(currentMediaItem).isEqualTo(testMediaItems.get(testMediaItemIndex));
    MediaMetadata currentMediaMetadata =
        threadTestRule.getHandler().postAndSync(controller::getMediaMetadata);
    assertThat(currentMediaMetadata).isEqualTo(currentMediaItem.mediaMetadata);
  }

  @Test
  public void getters_withMetadata() throws Exception {
    MediaMetadataCompat testMediaMetadataCompat = createMediaMetadataCompat();
    @RatingCompat.Style int testRatingType = RatingCompat.RATING_HEART;
    MediaMetadata testMediaMetadata =
        LegacyConversions.convertToMediaMetadata(testMediaMetadataCompat, testRatingType);
    Events testEvents =
        new Events(
            new FlagSet.Builder()
                .addAll(
                    EVENT_MEDIA_ITEM_TRANSITION,
                    EVENT_MEDIA_METADATA_CHANGED,
                    EVENT_POSITION_DISCONTINUITY,
                    EVENT_TIMELINE_CHANGED)
                .build());
    int testMediaItemIndex = 0;
    List<MediaItem> testMediaItems = new ArrayList<>();
    testMediaItems.add(
        LegacyConversions.convertToMediaItem(testMediaMetadataCompat, testRatingType));

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(5);
    AtomicReference<Timeline> timelineRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<MediaItem> mediaItemRef = new AtomicReference<>();
    AtomicInteger mediaItemTransitionReasonRef = new AtomicInteger();
    AtomicReference<PositionInfo> positionInfoRef = new AtomicReference<>();
    AtomicInteger discontinuityReasonRef = new AtomicInteger();
    AtomicReference<MediaMetadata> metadataRef = new AtomicReference<>();
    AtomicReference<Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineRef.set(timeline);
            timelineChangeReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
            mediaItemRef.set(mediaItem);
            mediaItemTransitionReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition,
              PositionInfo newPosition,
              @Player.DiscontinuityReason int reason) {
            positionInfoRef.set(newPosition);
            discontinuityReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            metadataRef.set(mediaMetadata);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Events events) {
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setMetadata(testMediaMetadataCompat);
    session.setRatingType(testRatingType);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertTimelineEqualsToMediaItems(timelineRef.get(), testMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(mediaItemRef.get()).isEqualTo(testMediaItems.get(testMediaItemIndex));
    assertThat(mediaItemTransitionReasonRef.get())
        .isEqualTo(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
    assertThat(positionInfoRef.get().mediaItemIndex).isEqualTo(testMediaItemIndex);
    assertThat(discontinuityReasonRef.get()).isEqualTo(DISCONTINUITY_REASON_AUTO_TRANSITION);
    assertThat(metadataRef.get()).isEqualTo(testMediaMetadata);
    assertThat(eventsRef.get()).isEqualTo(testEvents);

    Timeline currentTimeline =
        threadTestRule.getHandler().postAndSync(controller::getCurrentTimeline);
    assertThat(currentTimeline).isEqualTo(timelineRef.get());
    int currentMediaItemIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItemIndex);
    assertThat(currentMediaItemIndex).isEqualTo(testMediaItemIndex);
    MediaItem currentMediaItem =
        threadTestRule.getHandler().postAndSync(controller::getCurrentMediaItem);
    assertThat(currentMediaItem).isEqualTo(testMediaItems.get(testMediaItemIndex));
    MediaMetadata currentMediaMetadata =
        threadTestRule.getHandler().postAndSync(controller::getMediaMetadata);
    assertThat(currentMediaMetadata).isEqualTo(currentMediaItem.mediaMetadata);
  }

  private static MediaMetadataCompat createMediaMetadataCompat() {
    return new MediaMetadataCompat.Builder()
        .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, "artist")
        .putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "title")
        .build();
  }

  private static void assertTimelineEqualsToMediaItems(
      Timeline currentTimeline, List<MediaItem> mediaItems) {
    assertThat(currentTimeline.getWindowCount()).isEqualTo(mediaItems.size());
    Window window = new Window();
    for (int i = 0; i < currentTimeline.getWindowCount(); i++) {
      MediaItem mediaItem = currentTimeline.getWindow(i, window).mediaItem;
      assertWithMessage("Expected " + mediaItems.get(i) + " at " + i + ", but was " + mediaItem)
          .that(mediaItem)
          .isEqualTo(mediaItems.get(i));
    }
  }

  private List<MediaSessionCompat.QueueItem> convertToQueueItems(List<MediaItem> mediaItems)
      throws Exception {
    List<MediaSessionCompat.QueueItem> list = new ArrayList<>();
    for (int i = 0; i < mediaItems.size(); i++) {
      MediaItem item = mediaItems.get(i);
      @Nullable
      Bitmap bitmap = bitmapLoader.decodeBitmap(item.mediaMetadata.artworkData).get(10, SECONDS);
      MediaDescriptionCompat description =
          LegacyConversions.convertToMediaDescriptionCompat(item, bitmap);
      long id = LegacyConversions.convertToQueueItemId(i);
      list.add(new MediaSessionCompat.QueueItem(description, id));
    }
    return list;
  }
}
