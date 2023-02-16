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
package androidx.media3.session;

import static android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_BROWSABLE;
import static android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static androidx.media.utils.MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS;
import static androidx.media3.common.MimeTypes.AUDIO_AAC;
import static androidx.media3.common.MimeTypes.VIDEO_H264;
import static androidx.media3.common.MimeTypes.VIDEO_H265;
import static androidx.media3.session.MediaConstants.EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY;
import static androidx.media3.test.session.common.TestUtils.getCommandsAsList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
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
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media.VolumeProviderCompat;
import androidx.media.utils.MediaConstants;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.HeartRating;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PercentageRating;
import androidx.media3.common.Player;
import androidx.media3.common.Rating;
import androidx.media3.common.StarRating;
import androidx.media3.common.ThumbRating;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.session.PlayerInfo.BundlingExclusions;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
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
  private BitmapLoader bitmapLoader;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    bitmapLoader = new CacheBitmapLoader(new SimpleBitmapLoader());
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
  public void convertToQueueItem_withArtworkData() throws Exception {
    MediaItem mediaItem = MediaTestUtils.createMediaItemWithArtworkData("testId");
    MediaMetadata mediaMetadata = mediaItem.mediaMetadata;
    ListenableFuture<Bitmap> bitmapFuture = bitmapLoader.decodeBitmap(mediaMetadata.artworkData);
    @Nullable Bitmap bitmap = bitmapFuture.get(10, SECONDS);

    MediaSessionCompat.QueueItem queueItem =
        MediaUtils.convertToQueueItem(
            mediaItem,
            /** mediaItemIndex= */
            100,
            bitmap);

    assertThat(queueItem.getQueueId()).isEqualTo(100);
    assertThat(queueItem.getDescription().getIconBitmap()).isNotNull();
  }

  @Test
  public void convertToMediaDescriptionCompat_setsExpectedValues() {
    String mediaId = "testId";
    String title = "testTitle";
    String description = "testDesc";
    MediaMetadata metadata =
        new MediaMetadata.Builder()
            .setTitle(title)
            .setDescription(description)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build();
    MediaItem mediaItem =
        new MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(metadata).build();
    MediaDescriptionCompat descriptionCompat =
        MediaUtils.convertToMediaDescriptionCompat(mediaItem, /* artworkBitmap= */ null);

    assertThat(descriptionCompat.getMediaId()).isEqualTo(mediaId);
    assertThat(descriptionCompat.getTitle()).isEqualTo(title);
    assertThat(descriptionCompat.getDescription()).isEqualTo(description);
    assertThat(
            descriptionCompat
                .getExtras()
                .getLong(androidx.media3.session.MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT))
        .isEqualTo(MediaMetadata.MEDIA_TYPE_MUSIC);
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

    for (int i = 0; i < 5; i++) {
      assertThat(MediaUtils.truncateListBySize(bundleList, bundleSize * i + 1)).hasSize(i);
    }
  }

  @Test
  public void convertToMediaMetadata_withoutTitle() {
    assertThat(MediaUtils.convertToMediaMetadata((CharSequence) null))
        .isEqualTo(MediaMetadata.EMPTY);
  }

  @Test
  public void convertToMediaMetadata_withTitle() {
    CharSequence title = "title";
    assertThat(MediaUtils.convertToMediaMetadata(title).title).isEqualTo(title);
  }

  @Test
  public void convertToMediaMetadata_roundTripViaMediaMetadataCompat_returnsEqualMediaItemMetadata()
      throws Exception {
    MediaItem testMediaItem = MediaTestUtils.createMediaItemWithArtworkData("testZZZ");
    MediaMetadata testMediaMetadata = testMediaItem.mediaMetadata;
    @Nullable Bitmap testArtworkBitmap = null;
    @Nullable
    ListenableFuture<Bitmap> bitmapFuture = bitmapLoader.loadBitmapFromMetadata(testMediaMetadata);
    if (bitmapFuture != null) {
      testArtworkBitmap = bitmapFuture.get(10, SECONDS);
    }
    MediaMetadataCompat testMediaMetadataCompat =
        MediaUtils.convertToMediaMetadataCompat(
            testMediaMetadata,
            "mediaId",
            Uri.parse("http://example.com"),
            /* durationMs= */ 100L,
            testArtworkBitmap);

    MediaMetadata mediaMetadata =
        MediaUtils.convertToMediaMetadata(testMediaMetadataCompat, RatingCompat.RATING_NONE);

    assertThat(mediaMetadata).isEqualTo(testMediaMetadata);
    assertThat(mediaMetadata.artworkData).isNotNull();
  }

  @Test
  public void
      convertToMediaMetadata_roundTripViaMediaDescriptionCompat_returnsEqualMediaItemMetadata()
          throws Exception {
    MediaItem testMediaItem = MediaTestUtils.createMediaItemWithArtworkData("testZZZ");
    MediaMetadata testMediaMetadata = testMediaItem.mediaMetadata;
    @Nullable Bitmap testArtworkBitmap = null;
    @Nullable
    ListenableFuture<Bitmap> bitmapFuture = bitmapLoader.loadBitmapFromMetadata(testMediaMetadata);
    if (bitmapFuture != null) {
      testArtworkBitmap = bitmapFuture.get(10, SECONDS);
    }
    MediaDescriptionCompat mediaDescriptionCompat =
        MediaUtils.convertToMediaDescriptionCompat(testMediaItem, testArtworkBitmap);

    MediaMetadata mediaMetadata =
        MediaUtils.convertToMediaMetadata(mediaDescriptionCompat, RatingCompat.RATING_NONE);

    assertThat(mediaMetadata).isEqualTo(testMediaMetadata);
    assertThat(mediaMetadata.artworkData).isNotNull();
  }

  @Test
  public void convertToMediaMetadataCompat_withMediaType_setsMediaType() {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setMediaMetadata(
                new MediaMetadata.Builder().setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build())
            .build();

    MediaMetadataCompat mediaMetadataCompat =
        MediaUtils.convertToMediaMetadataCompat(
            mediaItem.mediaMetadata,
            "mediaId",
            Uri.parse("http://www.example.com"),
            /* durotionsMs= */ C.TIME_UNSET,
            /* artworkBitmap= */ null);

    assertThat(
            mediaMetadataCompat.getLong(
                androidx.media3.session.MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT))
        .isEqualTo(MediaMetadata.MEDIA_TYPE_MUSIC);
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
    rootHints.putInt(
        MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS, FLAG_BROWSABLE);
    rootHints.putBoolean(MediaBrowserService.BrowserRoot.EXTRA_OFFLINE, true);
    rootHints.putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true);
    rootHints.putBoolean(MediaBrowserService.BrowserRoot.EXTRA_SUGGESTED, true);

    MediaLibraryService.LibraryParams params =
        MediaUtils.convertToLibraryParams(context, rootHints);

    assertThat(params.extras.getString("key")).isEqualTo("value");
    assertThat(params.extras.getBoolean(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY)).isTrue();
    assertThat(params.extras.containsKey(BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS))
        .isFalse();
    assertThat(params.isOffline).isTrue();
    assertThat(params.isRecent).isTrue();
    assertThat(params.isSuggested).isTrue();
  }

  @Test
  public void convertToLibraryParams_rootHintsBrowsableNoFlagSet_browsableOnlyFalse() {
    Bundle rootHints = new Bundle();
    rootHints.putInt(MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS, 0);

    MediaLibraryService.LibraryParams params =
        MediaUtils.convertToLibraryParams(context, rootHints);

    assertThat(params.extras.getBoolean(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY)).isFalse();
  }

  @Test
  public void convertToLibraryParams_rootHintsPlayableFlagSet_browsableOnlyFalse() {
    Bundle rootHints = new Bundle();
    rootHints.putInt(
        MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS,
        FLAG_PLAYABLE | FLAG_BROWSABLE);

    MediaLibraryService.LibraryParams params =
        MediaUtils.convertToLibraryParams(context, rootHints);

    assertThat(params.extras.getBoolean(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY)).isFalse();
  }

  @Test
  public void convertToLibraryParams_rootHintsBrowsableAbsentKey_browsableOnlyFalse() {
    MediaLibraryService.LibraryParams params =
        MediaUtils.convertToLibraryParams(context, /* legacyBundle= */ Bundle.EMPTY);

    assertThat(params.extras.getBoolean(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY)).isFalse();
  }

  @Test
  public void convertToRootHints() {
    assertThat(MediaUtils.convertToRootHints(null)).isNull();
    Bundle extras = new Bundle();
    extras.putString("key", "value");
    extras.putBoolean(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY, true);
    MediaLibraryService.LibraryParams param =
        new MediaLibraryService.LibraryParams.Builder()
            .setOffline(true)
            .setRecent(true)
            .setSuggested(true)
            .setExtras(extras)
            .build();

    Bundle rootHints = MediaUtils.convertToRootHints(param);

    assertThat(
            rootHints.getInt(
                BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS, /* defaultValue= */ 0))
        .isEqualTo(FLAG_BROWSABLE);
    assertThat(rootHints.getString("key")).isEqualTo("value");
    assertThat(rootHints.get(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY)).isNull();
    assertThat(rootHints.getBoolean(MediaBrowserService.BrowserRoot.EXTRA_OFFLINE)).isTrue();
    assertThat(rootHints.getBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT)).isTrue();
    assertThat(rootHints.getBoolean(MediaBrowserService.BrowserRoot.EXTRA_SUGGESTED)).isTrue();
  }

  @Test
  public void convertToRootHints_browsableOnlyFalse_correctLegacyBrowsableFlags() {
    Bundle extras = new Bundle();
    extras.putBoolean(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY, false);
    MediaLibraryService.LibraryParams param =
        new MediaLibraryService.LibraryParams.Builder().setExtras(extras).build();

    Bundle rootHints = MediaUtils.convertToRootHints(param);

    assertThat(
            rootHints.getInt(
                BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS, /* defaultValue= */ -1))
        .isEqualTo(FLAG_BROWSABLE | FLAG_PLAYABLE);
    assertThat(rootHints.get(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY)).isNull();
  }

  @Test
  public void convertToRootHints_browsableAbsentKey_noLegacyKeyAdded() {
    MediaLibraryService.LibraryParams param =
        new MediaLibraryService.LibraryParams.Builder().build();

    Bundle rootHints = MediaUtils.convertToRootHints(param);

    assertThat(rootHints.get(BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS)).isNull();
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
  public void convertToSessionCommands_withCustomAction_containsCustomAction() {
    PlaybackStateCompat playbackState =
        new PlaybackStateCompat.Builder()
            .addCustomAction("action", "name", /* icon= */ 100)
            .build();
    SessionCommands sessionCommands =
        MediaUtils.convertToSessionCommands(playbackState, /* isSessionReady= */ true);
    assertThat(sessionCommands.contains(new SessionCommand("action", /* extras= */ Bundle.EMPTY)))
        .isTrue();
  }

  @SdkSuppress(minSdkVersion = 21)
  @Test
  public void convertToSessionCommands_whenSessionIsNotReadyOnSdk21_disallowsRating() {
    SessionCommands sessionCommands =
        MediaUtils.convertToSessionCommands(/* playbackState= */ null, /* isSessionReady= */ false);
    assertThat(sessionCommands.contains(SessionCommand.COMMAND_CODE_SESSION_SET_RATING)).isFalse();
  }

  @Test
  public void convertToPlayerCommands_withNoActions_onlyDefaultCommandsAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(/* capabilities= */ 0).build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsExactly(
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_DEVICE_VOLUME,
            Player.COMMAND_GET_MEDIA_ITEMS_METADATA,
            Player.COMMAND_GET_AUDIO_ATTRIBUTES);
  }

  @Test
  public void convertToPlayerCommands_withJustPlayAction_playPauseCommandNotAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY).build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).doesNotContain(Player.COMMAND_PLAY_PAUSE);
  }

  @Test
  public void convertToPlayerCommands_withJustPauseAction_playPauseCommandNotAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PAUSE).build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).doesNotContain(Player.COMMAND_PLAY_PAUSE);
  }

  @Test
  public void convertToPlayerCommands_withPlayAndPauseAction_playPauseCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE)
            .build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_PLAY_PAUSE);
  }

  @Test
  public void convertToPlayerCommands_withPlayPauseAction_playPauseCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE).build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_PLAY_PAUSE);
  }

  @Test
  public void convertToPlayerCommands_withPrepareAction_prepareCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PREPARE).build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_PREPARE);
  }

  @Test
  public void convertToPlayerCommands_withRewindAction_seekBackCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_REWIND).build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_SEEK_BACK);
  }

  @Test
  public void convertToPlayerCommands_withFastForwardAction_seekForwardCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_FAST_FORWARD)
            .build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_SEEK_FORWARD);
  }

  @Test
  public void convertToPlayerCommands_withSeekToAction_seekInCurrentMediaItemCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_SEEK_TO).build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
  }

  @Test
  public void convertToPlayerCommands_withSkipToNextAction_seekToNextCommandsAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            .build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsAtLeast(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);
  }

  @Test
  public void convertToPlayerCommands_withSkipToPreviousAction_seekToPreviousCommandsAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsAtLeast(
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
  }

  @Test
  public void
      convertToPlayerCommands_withPlayFromActionsWithoutPrepareFromAction_setMediaItemCommandNotAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                    | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                    | PlaybackStateCompat.ACTION_PLAY_FROM_URI)
            .build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsNoneOf(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_PREPARE);
  }

  @Test
  public void
      convertToPlayerCommands_withPrepareFromActionsWithoutPlayFromAction_setMediaItemCommandNotAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_URI)
            .build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsNoneOf(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_PREPARE);
  }

  @Test
  public void
      convertToPlayerCommands_withPlayFromAndPrepareFromMediaId_setMediaItemPrepareAndPlayAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID)
            .build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsAtLeast(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_PREPARE);
  }

  @Test
  public void
      convertToPlayerCommands_withPlayFromAndPrepareFromSearch_setMediaItemPrepareAndPlayAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH)
            .build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsAtLeast(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_PREPARE);
  }

  @Test
  public void
      convertToPlayerCommands_withPlayFromAndPrepareFromUri_setMediaItemPrepareAndPlayAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_FROM_URI
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_URI)
            .build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsAtLeast(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_PREPARE);
  }

  @Test
  public void convertToPlayerCommands_withSetPlaybackSpeedAction_setSpeedCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED)
            .build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_SET_SPEED_AND_PITCH);
  }

  @Test
  public void convertToPlayerCommands_withStopAction_stopCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_STOP).build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_STOP);
  }

  @Test
  public void convertToPlayerCommands_withRelativeVolumeControl_adjustVolumeCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(/* capabilities= */ 0).build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_RELATIVE,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_ADJUST_DEVICE_VOLUME);
    assertThat(getCommandsAsList(playerCommands)).doesNotContain(Player.COMMAND_SET_DEVICE_VOLUME);
  }

  @Test
  public void convertToPlayerCommands_withAbsoluteVolumeControl_adjustVolumeCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(/* capabilities= */ 0).build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsAtLeast(Player.COMMAND_ADJUST_DEVICE_VOLUME, Player.COMMAND_SET_DEVICE_VOLUME);
  }

  @Test
  public void
      convertToPlayerCommands_withShuffleRepeatActionsAndSessionReady_shuffleAndRepeatCommandsAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                    | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)
            .build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsAtLeast(Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_SHUFFLE_MODE);
  }

  @Test
  public void
      convertToPlayerCommands_withShuffleRepeatActionsAndSessionNotReady_shuffleAndRepeatCommandsNotAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                    | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)
            .build();

    Player.Commands playerCommands =
        MediaUtils.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ false);

    assertThat(getCommandsAsList(playerCommands))
        .containsNoneOf(Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_SHUFFLE_MODE);
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
    assertThat(button.isEnabled).isTrue();
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

  @Test
  public void convertToCurrentPosition_byDefault_returnsZero() {
    long currentPositionMs =
        MediaUtils.convertToCurrentPositionMs(
            /* playbackStateCompat= */ null,
            /* currentMediaMetadata= */ null,
            /* timeDiffMs= */ C.TIME_UNSET);
    assertThat(currentPositionMs).isEqualTo(0);
  }

  @Test
  public void convertToCurrentPositionMs_withNegativePosition_adjustsToZero() {
    long testPositionMs = -100L;
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPositionMs, /* playbackSpeed= */ 1.0f)
            .build();
    long currentPositionMs =
        MediaUtils.convertToCurrentPositionMs(
            state, /* metadataCompat= */ null, /* timeDiffMs= */ C.TIME_UNSET);
    assertThat(currentPositionMs).isEqualTo(0);
  }

  @Test
  public void convertToCurrentPositionMs_withGreaterThanDuration_adjustsToDuration() {
    long testDurationMs = 100L;
    long testPositionMs = 200L;
    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_DURATION, testDurationMs).build();
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPositionMs, /* playbackSpeed= */ 1.0f)
            .build();
    long currentPositionMs =
        MediaUtils.convertToCurrentPositionMs(state, metadata, /* timeDiffMs= */ C.TIME_UNSET);
    assertThat(currentPositionMs).isEqualTo(testDurationMs);
  }

  @Test
  public void convertToDurationMs() {
    long testDurationMs = 100L;
    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_DURATION, testDurationMs).build();
    long currentPositionMs = MediaUtils.convertToDurationMs(metadata);
    assertThat(currentPositionMs).isEqualTo(testDurationMs);
  }

  @Test
  public void convertToDurationMs_withNegativeDuration_returnsTimeUnset() {
    long testDurationMs = -100L;
    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_DURATION, testDurationMs).build();
    long currentPositionMs = MediaUtils.convertToDurationMs(metadata);
    assertThat(currentPositionMs).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void convertToBufferedPositionMs() {
    long testPositionMs = 300L;
    long testBufferedPositionMs = 331L;
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPositionMs, /* playbackSpeed= */ 1.0f)
            .setBufferedPosition(testBufferedPositionMs)
            .build();

    long bufferedPositionMs =
        MediaUtils.convertToBufferedPositionMs(
            state, /* metadataCompat= */ null, /* timeDiffMs= */ C.TIME_UNSET);
    assertThat(bufferedPositionMs).isEqualTo(testBufferedPositionMs);
  }

  @Test
  public void convertToBufferedPositionMs_withLessThanPosition_adjustsToPosition() {
    long testPositionMs = 300L;
    long testBufferedPositionMs = 100L;
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPositionMs, /* playbackSpeed= */ 1.0f)
            .setBufferedPosition(testBufferedPositionMs)
            .build();
    long bufferedPositionMs =
        MediaUtils.convertToBufferedPositionMs(
            state, /* metadataCompat= */ null, /* timeDiffMs= */ C.TIME_UNSET);
    assertThat(bufferedPositionMs).isEqualTo(testPositionMs);
  }

  @Test
  public void convertToBufferedPositionMs_withLessThanPositionAndWithTimeDiff_adjustsToPosition() {
    long testPositionMs = 200L;
    long testBufferedPositionMs = 100L;
    long testTimeDiffMs = 100;
    float testPlaybackSpeed = 1.0f;
    long expectedPositionMs = testPositionMs + (long) (testPlaybackSpeed * testTimeDiffMs);
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PLAYING, testPositionMs, testPlaybackSpeed)
            .setBufferedPosition(testBufferedPositionMs)
            .build();
    long bufferedPositionMs =
        MediaUtils.convertToBufferedPositionMs(state, /* metadataCompat= */ null, testTimeDiffMs);
    assertThat(bufferedPositionMs).isEqualTo(expectedPositionMs);
  }

  @Test
  public void convertToBufferedPositionMs_withGreaterThanDuration_adjustsToDuration() {
    long testDurationMs = 100L;
    long testBufferedPositionMs = 200L;
    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_DURATION, testDurationMs).build();
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder().setBufferedPosition(testBufferedPositionMs).build();
    long bufferedPositionMs =
        MediaUtils.convertToBufferedPositionMs(state, metadata, /* timeDiffMs= */ C.TIME_UNSET);
    assertThat(bufferedPositionMs).isEqualTo(testDurationMs);
  }

  @Test
  public void convertToTotalBufferedDurationMs() {
    long testCurrentPositionMs = 224L;
    long testBufferedPositionMs = 331L;
    long testTotalBufferedDurationMs = testBufferedPositionMs - testCurrentPositionMs;
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PAUSED, testCurrentPositionMs, /* playbackSpeed= */ 1.0f)
            .setBufferedPosition(testBufferedPositionMs)
            .build();

    long totalBufferedDurationMs =
        MediaUtils.convertToTotalBufferedDurationMs(
            state, /* metadataCompat= */ null, /* timeDiffMs= */ C.INDEX_UNSET);
    assertThat(totalBufferedDurationMs).isEqualTo(testTotalBufferedDurationMs);
  }

  @Test
  public void mergePlayerInfo_timelineAndTracksExcluded_correctMerge() {
    Timeline timeline =
        new Timeline.RemotableTimeline(
            ImmutableList.of(new Timeline.Window()),
            ImmutableList.of(new Timeline.Period()),
            /* shuffledWindowIndices= */ new int[] {0});
    Tracks tracks =
        new Tracks(
            ImmutableList.of(
                new Tracks.Group(
                    new TrackGroup(new Format.Builder().setSampleMimeType(AUDIO_AAC).build()),
                    /* adaptiveSupported= */ false,
                    new int[] {C.FORMAT_EXCEEDS_CAPABILITIES},
                    /* trackSelected= */ new boolean[] {true}),
                new Tracks.Group(
                    new TrackGroup(
                        new Format.Builder().setSampleMimeType(VIDEO_H264).build(),
                        new Format.Builder().setSampleMimeType(VIDEO_H265).build()),
                    /* adaptiveSupported= */ true,
                    new int[] {C.FORMAT_HANDLED, C.FORMAT_UNSUPPORTED_TYPE},
                    /* trackSelected= */ new boolean[] {false, true})));
    PlayerInfo oldPlayerInfo =
        PlayerInfo.DEFAULT.copyWithCurrentTracks(tracks).copyWithTimeline(timeline);
    PlayerInfo newPlayerInfo = PlayerInfo.DEFAULT;
    Player.Commands availableCommands =
        Player.Commands.EMPTY
            .buildUpon()
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_TRACKS)
            .build();

    Pair<PlayerInfo, BundlingExclusions> mergeResult =
        MediaUtils.mergePlayerInfo(
            oldPlayerInfo,
            BundlingExclusions.NONE,
            newPlayerInfo,
            new BundlingExclusions(/* isTimelineExcluded= */ true, /* areTracksExcluded= */ true),
            availableCommands);

    assertThat(mergeResult.first.timeline).isSameInstanceAs(oldPlayerInfo.timeline);
    assertThat(mergeResult.first.currentTracks).isSameInstanceAs(oldPlayerInfo.currentTracks);
    assertThat(mergeResult.second.isTimelineExcluded).isFalse();
    assertThat(mergeResult.second.areCurrentTracksExcluded).isFalse();
  }

  @Test
  public void mergePlayerInfo_getTimelineCommandNotAvailable_emptyTimeline() {
    Timeline timeline =
        new Timeline.RemotableTimeline(
            ImmutableList.of(new Timeline.Window()),
            ImmutableList.of(new Timeline.Period()),
            /* shuffledWindowIndices= */ new int[] {0});
    Tracks tracks =
        new Tracks(
            ImmutableList.of(
                new Tracks.Group(
                    new TrackGroup(new Format.Builder().setSampleMimeType(AUDIO_AAC).build()),
                    /* adaptiveSupported= */ false,
                    new int[] {C.FORMAT_EXCEEDS_CAPABILITIES},
                    /* trackSelected= */ new boolean[] {true}),
                new Tracks.Group(
                    new TrackGroup(
                        new Format.Builder().setSampleMimeType(VIDEO_H264).build(),
                        new Format.Builder().setSampleMimeType(VIDEO_H265).build()),
                    /* adaptiveSupported= */ true,
                    new int[] {C.FORMAT_HANDLED, C.FORMAT_UNSUPPORTED_TYPE},
                    /* trackSelected= */ new boolean[] {false, true})));
    PlayerInfo oldPlayerInfo =
        PlayerInfo.DEFAULT.copyWithCurrentTracks(tracks).copyWithTimeline(timeline);
    PlayerInfo newPlayerInfo = PlayerInfo.DEFAULT;
    Player.Commands availableCommands =
        Player.Commands.EMPTY.buildUpon().add(Player.COMMAND_GET_TRACKS).build();

    Pair<PlayerInfo, BundlingExclusions> mergeResult =
        MediaUtils.mergePlayerInfo(
            oldPlayerInfo,
            BundlingExclusions.NONE,
            newPlayerInfo,
            new BundlingExclusions(/* isTimelineExcluded= */ true, /* areTracksExcluded= */ true),
            availableCommands);

    assertThat(mergeResult.first.timeline).isSameInstanceAs(Timeline.EMPTY);
    assertThat(mergeResult.first.currentTracks).isSameInstanceAs(oldPlayerInfo.currentTracks);
    assertThat(mergeResult.second.isTimelineExcluded).isTrue();
    assertThat(mergeResult.second.areCurrentTracksExcluded).isFalse();
  }

  @Test
  public void mergePlayerInfo_getTracksCommandNotAvailable_emptyTracks() {
    Timeline timeline =
        new Timeline.RemotableTimeline(
            ImmutableList.of(new Timeline.Window()),
            ImmutableList.of(new Timeline.Period()),
            /* shuffledWindowIndices= */ new int[] {0});
    Tracks tracks =
        new Tracks(
            ImmutableList.of(
                new Tracks.Group(
                    new TrackGroup(new Format.Builder().setSampleMimeType(AUDIO_AAC).build()),
                    /* adaptiveSupported= */ false,
                    new int[] {C.FORMAT_EXCEEDS_CAPABILITIES},
                    /* trackSelected= */ new boolean[] {true}),
                new Tracks.Group(
                    new TrackGroup(
                        new Format.Builder().setSampleMimeType(VIDEO_H264).build(),
                        new Format.Builder().setSampleMimeType(VIDEO_H265).build()),
                    /* adaptiveSupported= */ true,
                    new int[] {C.FORMAT_HANDLED, C.FORMAT_UNSUPPORTED_TYPE},
                    /* trackSelected= */ new boolean[] {false, true})));
    PlayerInfo oldPlayerInfo =
        PlayerInfo.DEFAULT.copyWithCurrentTracks(tracks).copyWithTimeline(timeline);
    PlayerInfo newPlayerInfo = PlayerInfo.DEFAULT;
    Player.Commands availableCommands =
        Player.Commands.EMPTY.buildUpon().add(Player.COMMAND_GET_TIMELINE).build();

    Pair<PlayerInfo, BundlingExclusions> mergeResult =
        MediaUtils.mergePlayerInfo(
            oldPlayerInfo,
            BundlingExclusions.NONE,
            newPlayerInfo,
            new BundlingExclusions(/* isTimelineExcluded= */ true, /* areTracksExcluded= */ true),
            availableCommands);

    assertThat(mergeResult.first.timeline).isSameInstanceAs(oldPlayerInfo.timeline);
    assertThat(mergeResult.first.currentTracks).isSameInstanceAs(Tracks.EMPTY);
    assertThat(mergeResult.second.isTimelineExcluded).isFalse();
    assertThat(mergeResult.second.areCurrentTracksExcluded).isTrue();
  }
}
