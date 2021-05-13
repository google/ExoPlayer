/*
 * Copyright 2020 The Android Open Source Project
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

import static android.support.v4.media.session.MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.service.media.MediaBrowserService;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import androidx.media.AudioAttributesCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.HeartRating;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PercentageRating;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Rating;
import com.google.android.exoplayer2.StarRating;
import com.google.android.exoplayer2.ThumbRating;
import com.google.android.exoplayer2.audio.AudioAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaUtils}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public final class MediaUtilsTest {

  private Context context;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
  }

  @Test
  public void convertToBrowserItem() {
    String mediaId = "testId";
    CharSequence trackTitle = "testTitle";
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(trackTitle).build())
            .build();

    MediaBrowserCompat.MediaItem browserItem = MediaUtils.convertToBrowserItem(mediaItem);

    assertThat(browserItem.getDescription()).isNotNull();
    assertThat(browserItem.getDescription().getMediaId()).isEqualTo(mediaId);
    assertThat(TextUtils.equals(browserItem.getDescription().getTitle(), trackTitle)).isTrue();
  }

  @Test
  public void convertToMediaItem_browserItemToMediaItem() {
    String mediaId = "testId";
    String title = "testTitle";
    MediaDescriptionCompat descriptionCompat =
        new MediaDescriptionCompat.Builder().setMediaId(mediaId).setTitle(title).build();
    MediaBrowserCompat.MediaItem browserItem =
        new MediaBrowserCompat.MediaItem(descriptionCompat, /* flags= */ 0);

    MediaItem mediaItem = MediaUtils.convertToMediaItem(browserItem);
    assertThat(mediaItem.mediaId).isEqualTo(mediaId);
    assertThat(mediaItem.mediaMetadata.title).isEqualTo(title);
  }

  @Test
  public void convertToMediaItem_queueItemToMediaItem() {
    String mediaId = "testMediaId";
    String title = "testTitle";
    MediaDescriptionCompat descriptionCompat =
        new MediaDescriptionCompat.Builder().setMediaId(mediaId).setTitle(title).build();
    MediaSessionCompat.QueueItem queueItem =
        new MediaSessionCompat.QueueItem(descriptionCompat, /* id= */ 1);
    MediaItem mediaItem = MediaUtils.convertToMediaItem(queueItem);
    assertThat(mediaItem.mediaId).isEqualTo(mediaId);
    assertThat(mediaItem.mediaMetadata.title.toString()).isEqualTo(title);
  }

  @Test
  public void convertToMediaItem_metadataCompatToMediaItem() {
    MediaMetadataCompat metadataCompat = null;
    LegacyMediaItem mediaItem =
        MediaUtils.convertToLegacyMediaItem(metadataCompat, RatingCompat.RATING_3_STARS);
    assertThat(mediaItem).isNull();

    String mediaId = "testId";
    String displaySubtitle = "testDisplaySubtitle";
    String displayDescription = "testDisplayDescription";
    String title = "testTitle";
    String iconUri = "testIconUri";
    String mediaUri = "testMediaUri";

    metadataCompat =
        new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, displayDescription)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, iconUri)
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, mediaUri)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, displaySubtitle)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .build();
    mediaItem = MediaUtils.convertToLegacyMediaItem(metadataCompat, RatingCompat.RATING_3_STARS);
    assertThat(mediaItem).isNotNull();
    assertThat(mediaItem.getMediaId()).isEqualTo(mediaId);
    LegacyMediaMetadata metadata = mediaItem.getMetadata();
    assertThat(metadata).isNotNull();
    assertThat(metadata.getString(LegacyMediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION))
        .isEqualTo(displayDescription);
    assertThat(metadata.getString(LegacyMediaMetadata.METADATA_KEY_DISPLAY_ICON_URI))
        .isEqualTo(iconUri);
    assertThat(metadata.getString(LegacyMediaMetadata.METADATA_KEY_MEDIA_URI)).isEqualTo(mediaUri);
    assertThat(metadata.getString(LegacyMediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))
        .isEqualTo(displaySubtitle);
    assertThat(metadata.getString(LegacyMediaMetadata.METADATA_KEY_TITLE)).isEqualTo(title);
  }

  @Test
  public void convertToBrowserItemList() {
    int size = 3;
    List<MediaItem> mediaItems = MediaTestUtils.createConvergedMediaItems(size);
    List<MediaBrowserCompat.MediaItem> browserItems =
        MediaUtils.convertToBrowserItemList(mediaItems);
    assertThat(browserItems).hasSize(size);
    for (int i = 0; i < size; ++i) {
      assertThat(browserItems.get(i).getMediaId()).isEqualTo(mediaItems.get(i).mediaId);
    }
  }

  @Test
  public void convertBrowserItemListToMediaItemList() {
    int size = 3;
    List<MediaBrowserCompat.MediaItem> browserItems = MediaTestUtils.createBrowserItems(size);
    List<MediaItem> mediaItems = MediaUtils.convertBrowserItemListToMediaItemList(browserItems);
    assertThat(mediaItems).hasSize(size);
    for (int i = 0; i < size; ++i) {
      assertThat(mediaItems.get(i).mediaId).isEqualTo(browserItems.get(i).getMediaId());
    }
  }

  @Test
  public void convertToMediaItemList_queueItemToMediaItem() {
    int size = 3;
    List<MediaSessionCompat.QueueItem> queueItems = MediaTestUtils.createQueueItems(size);
    List<MediaItem> mediaItems = MediaUtils.convertQueueItemListToMediaItemList(queueItems);
    assertThat(mediaItems).hasSize(queueItems.size());
    for (int i = 0; i < size; ++i) {
      assertThat(mediaItems.get(i).mediaId)
          .isEqualTo(queueItems.get(i).getDescription().getMediaId());
    }
  }

  @Test
  public void convertToQueueItemList() {
    int size = 3;
    List<MediaItem> mediaItems = MediaTestUtils.createConvergedMediaItems(size);
    List<MediaSessionCompat.QueueItem> queueItems = MediaUtils.convertToQueueItemList(mediaItems);
    assertThat(queueItems).hasSize(mediaItems.size());
    for (int i = 0; i < size; ++i) {
      assertThat(queueItems.get(i).getDescription().getMediaId())
          .isEqualTo(mediaItems.get(i).mediaId);
    }
  }

  @Test
  public void createMediaDescriptionCompat() {
    String mediaId = "testId";
    MediaDescriptionCompat descriptionCompat = MediaUtils.createMediaDescriptionCompat(null);
    assertThat(descriptionCompat).isNull();

    descriptionCompat = MediaUtils.createMediaDescriptionCompat(mediaId);
    assertThat(descriptionCompat.getMediaId()).isEqualTo(mediaId);
  }

  @Test
  public void convertToQueueItemId() {
    assertThat(MediaUtils.convertToQueueItemId(C.INDEX_UNSET))
        .isEqualTo(MediaSessionCompat.QueueItem.UNKNOWN_ID);
    assertThat(MediaUtils.convertToQueueItemId(100)).isEqualTo(100);
  }

  @Test
  public void truncateListBySize() {
    List<Bundle> bundleList = new ArrayList<>();
    Bundle testBundle = new Bundle();
    testBundle.putString("key", "value");

    Parcel p = Parcel.obtain();
    p.writeParcelable(testBundle, 0);
    int bundleSize = p.dataSize();
    p.recycle();

    bundleList.addAll(Collections.nCopies(10, testBundle));

    assertThat(MediaUtils.truncateListBySize(null, 1)).isNull();
    for (int i = 0; i < 5; i++) {
      assertThat(MediaUtils.truncateListBySize(bundleList, bundleSize * i + 1)).hasSize(i);
    }
  }

  @Test
  public void convertToMediaMetadata_withoutTitle() {
    assertThat(MediaUtils.convertToMediaMetadata(null)).isEqualTo(MediaMetadata.EMPTY);
  }

  @Test
  public void convertToMediaMetadata_withTitle() {
    CharSequence title = "title";
    assertThat(MediaUtils.convertToMediaMetadata(title).title).isEqualTo(title);
  }

  @Test
  public void convertBetweenRatingAndRatingCompat() {
    assertRatingEquals(MediaUtils.convertToRating(null), MediaUtils.convertToRatingCompat(null));
    assertRatingEquals(
        MediaUtils.convertToRating(RatingCompat.newUnratedRating(RatingCompat.RATING_NONE)),
        MediaUtils.convertToRatingCompat(null));
    assertRatingEquals(
        MediaUtils.convertToRating(RatingCompat.newUnratedRating(RatingCompat.RATING_HEART)),
        MediaUtils.convertToRatingCompat(new HeartRating()));
    assertRatingEquals(
        MediaUtils.convertToRating(RatingCompat.newHeartRating(true)),
        MediaUtils.convertToRatingCompat(new HeartRating(true)));
    assertRatingEquals(
        MediaUtils.convertToRating(RatingCompat.newThumbRating(false)),
        MediaUtils.convertToRatingCompat(new ThumbRating(false)));
    assertRatingEquals(
        MediaUtils.convertToRating(RatingCompat.newThumbRating(false)),
        MediaUtils.convertToRatingCompat(new ThumbRating(false)));
    assertRatingEquals(
        MediaUtils.convertToRating(RatingCompat.newStarRating(RatingCompat.RATING_3_STARS, 1f)),
        MediaUtils.convertToRatingCompat(new StarRating(3, 1f)));
    assertRatingEquals(
        MediaUtils.convertToRating(RatingCompat.newStarRating(RatingCompat.RATING_4_STARS, 0f)),
        MediaUtils.convertToRatingCompat(new StarRating(4, 0f)));
    assertRatingEquals(
        MediaUtils.convertToRating(RatingCompat.newStarRating(RatingCompat.RATING_5_STARS, 5f)),
        MediaUtils.convertToRatingCompat(new StarRating(5, 5f)));
    assertRatingEquals(
        MediaUtils.convertToRating(RatingCompat.newPercentageRating(80f)),
        MediaUtils.convertToRatingCompat(new PercentageRating(80f)));
  }

  void assertRatingEquals(Rating rating, RatingCompat ratingCompat) {
    if (rating == null && ratingCompat == null) {
      return;
    }
    assertThat(rating.isRated()).isEqualTo(ratingCompat.isRated());
    if (rating instanceof HeartRating) {
      assertThat(ratingCompat.getRatingStyle()).isEqualTo(RatingCompat.RATING_HEART);
      assertThat(((HeartRating) rating).isHeart()).isEqualTo(ratingCompat.hasHeart());
    } else if (rating instanceof ThumbRating) {
      assertThat(ratingCompat.getRatingStyle()).isEqualTo(RatingCompat.RATING_THUMB_UP_DOWN);
      assertThat(((ThumbRating) rating).isThumbsUp()).isEqualTo(ratingCompat.isThumbUp());
    } else if (rating instanceof StarRating) {
      StarRating starRating = (StarRating) rating;
      switch (starRating.getMaxStars()) {
        case 3:
          assertThat(ratingCompat.getRatingStyle()).isEqualTo(RatingCompat.RATING_3_STARS);
          break;
        case 4:
          assertThat(ratingCompat.getRatingStyle()).isEqualTo(RatingCompat.RATING_4_STARS);
          break;
        case 5:
          assertThat(ratingCompat.getRatingStyle()).isEqualTo(RatingCompat.RATING_5_STARS);
          break;
        default: // fall out
      }
      assertThat(starRating.getStarRating()).isEqualTo(ratingCompat.getStarRating());
    } else if (rating instanceof PercentageRating) {
      assertThat(ratingCompat.getRatingStyle()).isEqualTo(RatingCompat.RATING_PERCENTAGE);
      assertThat(((PercentageRating) rating).getPercent())
          .isEqualTo(ratingCompat.getPercentRating());
    }
  }

  @Test
  public void convertToLibraryParams() {
    assertThat(MediaUtils.convertToLibraryParams(context, null)).isNull();
    Bundle rootHints = new Bundle();
    rootHints.putString("key", "value");
    rootHints.putBoolean(MediaBrowserService.BrowserRoot.EXTRA_OFFLINE, true);
    rootHints.putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true);
    rootHints.putBoolean(MediaBrowserService.BrowserRoot.EXTRA_SUGGESTED, true);

    MediaLibraryService.LibraryParams params =
        MediaUtils.convertToLibraryParams(context, rootHints);
    assertThat(params.offline).isTrue();
    assertThat(params.recent).isTrue();
    assertThat(params.suggested).isTrue();
    assertThat(params.extras.getString("key")).isEqualTo("value");
  }

  @Test
  public void convertToRootHints() {
    assertThat(MediaUtils.convertToRootHints(null)).isNull();
    Bundle extras = new Bundle();
    extras.putString("key", "value");
    MediaLibraryService.LibraryParams param =
        new MediaLibraryService.LibraryParams.Builder()
            .setOffline(true)
            .setRecent(true)
            .setSuggested(true)
            .setExtras(extras)
            .build();
    Bundle rootHints = MediaUtils.convertToRootHints(param);
    assertThat(rootHints.getBoolean(MediaBrowserService.BrowserRoot.EXTRA_OFFLINE)).isTrue();
    assertThat(rootHints.getBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT)).isTrue();
    assertThat(rootHints.getBoolean(MediaBrowserService.BrowserRoot.EXTRA_SUGGESTED)).isTrue();
    assertThat(rootHints.getString("key")).isEqualTo("value");
  }

  @Test
  public void removeNullElements() {
    List<String> strings = new ArrayList<>();
    strings.add("str1");
    strings.add(null);
    strings.add("str2");
    strings.add(null);
    assertThat(MediaUtils.removeNullElements(strings)).containsExactly("str1", "str2");
  }

  @Test
  public void convertToSessionCommands() {
    PlaybackStateCompat playbackState =
        new PlaybackStateCompat.Builder()
            .addCustomAction("action", "name", /* icon= */ 100)
            .build();
    SessionCommands sessionCommands = MediaUtils.convertToSessionCommands(playbackState);
    assertThat(sessionCommands.contains(new SessionCommand("action", /* extras= */ null))).isTrue();
  }

  @Test
  public void convertToPlayerCommands() {
    long sessionFlags = FLAG_HANDLES_QUEUE_COMMANDS;
    Player.Commands playerCommands = MediaUtils.convertToPlayerCommands(sessionFlags);
    assertThat(playerCommands.contains(Player.COMMAND_GET_MEDIA_ITEMS)).isTrue();
  }

  @Test
  public void convertToCustomLayout() {
    assertThat(MediaUtils.convertToCustomLayout(null)).isEmpty();

    String extraKey = "key";
    String extraValue = "value";
    String actionStr = "action";
    String displayName = "display_name";
    int iconRes = 21;

    Bundle extras = new Bundle();
    extras.putString(extraKey, extraValue);

    PlaybackStateCompat.CustomAction action =
        new PlaybackStateCompat.CustomAction.Builder(actionStr, displayName, iconRes)
            .setExtras(extras)
            .build();

    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_NONE,
                /* position= */ 0,
                /* playbackSpeed= */ 1,
                /* updateTime= */ 100)
            .addCustomAction(action)
            .build();

    List<CommandButton> buttons = MediaUtils.convertToCustomLayout(state);
    assertThat(buttons).hasSize(1);
    CommandButton button = buttons.get(0);
    assertThat(button.displayName.toString()).isEqualTo(displayName);
    assertThat(button.enabled).isTrue();
    assertThat(button.iconResId).isEqualTo(iconRes);
    assertThat(button.sessionCommand.customAction).isEqualTo(actionStr);
    assertThat(button.sessionCommand.customExtras.getString(extraKey)).isEqualTo(extraValue);
  }

  @Test
  public void convertToAudioAttributes() {
    assertThat(MediaUtils.convertToAudioAttributes((AudioAttributesCompat) null))
        .isSameInstanceAs(AudioAttributes.DEFAULT);
    assertThat(MediaUtils.convertToAudioAttributes((MediaControllerCompat.PlaybackInfo) null))
        .isSameInstanceAs(AudioAttributes.DEFAULT);

    int contentType = AudioAttributesCompat.CONTENT_TYPE_MUSIC;
    int flags = AudioAttributesCompat.FLAG_AUDIBILITY_ENFORCED;
    int usage = AudioAttributesCompat.USAGE_MEDIA;
    AudioAttributesCompat aaCompat =
        new AudioAttributesCompat.Builder()
            .setContentType(contentType)
            .setFlags(flags)
            .setUsage(usage)
            .build();
    AudioAttributes aa =
        new AudioAttributes.Builder()
            .setContentType(contentType)
            .setFlags(flags)
            .setUsage(usage)
            .build();
    assertThat(MediaUtils.convertToAudioAttributes(aaCompat)).isEqualTo(aa);
    assertThat(MediaUtils.convertToAudioAttributesCompat(aa)).isEqualTo(aaCompat);
  }
}
