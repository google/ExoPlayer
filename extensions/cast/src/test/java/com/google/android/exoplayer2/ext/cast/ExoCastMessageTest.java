/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cast;

import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_ARGS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DESCRIPTION;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DISABLED_TEXT_TRACK_SELECTION_FLAGS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DRM_SCHEMES;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_END_POSITION_US;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_INDEX;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_ITEMS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_LICENSE_SERVER;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_MEDIA;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_METHOD;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_MIME_TYPE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PITCH;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PLAY_WHEN_READY;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_POSITION_MS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PREFERRED_AUDIO_LANGUAGE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PREFERRED_TEXT_LANGUAGE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_REPEAT_MODE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_REQUEST_HEADERS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SELECT_UNDETERMINED_TEXT_LANGUAGE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SEQUENCE_NUMBER;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SHUFFLE_MODE_ENABLED;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SHUFFLE_ORDER;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SKIP_SILENCE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SPEED;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_START_POSITION_US;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_TITLE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_URI;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_UUID;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_UUIDS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_ADD_ITEMS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_MOVE_ITEM;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_REMOVE_ITEMS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_SEEK_TO;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_SET_PLAYBACK_PARAMETERS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_SET_PLAY_WHEN_READY;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_SET_REPEAT_MODE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_SET_SHUFFLE_MODE_ENABLED;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_SET_TRACK_SELECTION_PARAMETERS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_REPEAT_MODE_OFF;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_REPEAT_MODE_ONE;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.cast.MediaItem.DrmScheme;
import com.google.android.exoplayer2.ext.cast.MediaItem.UriBundle;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ExoCastMessage}. */
@RunWith(AndroidJUnit4.class)
public class ExoCastMessageTest {

  @Test
  public void addItems_withUnsetIndex_doesNotAddIndexToJson() throws JSONException {
    MediaItem sampleItem = new MediaItem.Builder().build();
    ExoCastMessage message =
        new ExoCastMessage.AddItems(
            C.INDEX_UNSET,
            Collections.singletonList(sampleItem),
            new ShuffleOrder.UnshuffledShuffleOrder(1));
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 0));
    JSONObject arguments = messageAsJson.getJSONObject(KEY_ARGS);
    JSONArray items = arguments.getJSONArray(KEY_ITEMS);

    assertThat(messageAsJson.getLong(KEY_SEQUENCE_NUMBER)).isEqualTo(0);
    assertThat(messageAsJson.getString(KEY_METHOD)).isEqualTo(METHOD_ADD_ITEMS);
    assertThat(arguments.has(KEY_INDEX)).isFalse();
    assertThat(items.length()).isEqualTo(1);
  }

  @Test
  public void addItems_withMultipleItems_producesExpectedJsonList() throws JSONException {
    MediaItem sampleItem1 = new MediaItem.Builder().build();
    MediaItem sampleItem2 = new MediaItem.Builder().build();
    ExoCastMessage message =
        new ExoCastMessage.AddItems(
            1, Arrays.asList(sampleItem2, sampleItem1), new ShuffleOrder.UnshuffledShuffleOrder(2));
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 1));
    JSONObject arguments = messageAsJson.getJSONObject(KEY_ARGS);
    JSONArray items = arguments.getJSONArray(KEY_ITEMS);

    assertThat(messageAsJson.getLong(KEY_SEQUENCE_NUMBER)).isEqualTo(1);
    assertThat(messageAsJson.getString(KEY_METHOD)).isEqualTo(METHOD_ADD_ITEMS);
    assertThat(arguments.getInt(KEY_INDEX)).isEqualTo(1);
    assertThat(items.length()).isEqualTo(2);
  }

  @Test
  public void addItems_withoutItemOptionalFields_doesNotAddFieldsToJson() throws JSONException {
    MediaItem itemWithoutOptionalFields =
        new MediaItem.Builder()
            .setTitle("title")
            .setMimeType(MimeTypes.AUDIO_MP4)
            .setDescription("desc")
            .setDrmSchemes(Collections.singletonList(new DrmScheme(C.WIDEVINE_UUID, null)))
            .setMedia("www.google.com")
            .build();
    ExoCastMessage message =
        new ExoCastMessage.AddItems(
            C.INDEX_UNSET,
            Collections.singletonList(itemWithoutOptionalFields),
            new ShuffleOrder.UnshuffledShuffleOrder(1));
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 0));
    JSONObject arguments = messageAsJson.getJSONObject(KEY_ARGS);
    JSONArray items = arguments.getJSONArray(KEY_ITEMS);

    assertJsonEqualsMediaItem(items.getJSONObject(/* index= */ 0), itemWithoutOptionalFields);
  }

  @Test
  public void addItems_withAllItemFields_addsFieldsToJson() throws JSONException {
    HashMap<String, String> headersMedia = new HashMap<>();
    headersMedia.put("header1", "value1");
    headersMedia.put("header2", "value2");
    UriBundle media = new UriBundle(Uri.parse("www.google.com"), headersMedia);

    HashMap<String, String> headersWidevine = new HashMap<>();
    headersWidevine.put("widevine", "value");
    UriBundle widevingUriBundle = new UriBundle(Uri.parse("www.widevine.com"), headersWidevine);

    HashMap<String, String> headersPlayready = new HashMap<>();
    headersPlayready.put("playready", "value");
    UriBundle playreadyUriBundle = new UriBundle(Uri.parse("www.playready.com"), headersPlayready);

    DrmScheme[] drmSchemes =
        new DrmScheme[] {
          new DrmScheme(C.WIDEVINE_UUID, widevingUriBundle),
          new DrmScheme(C.PLAYREADY_UUID, playreadyUriBundle)
        };
    MediaItem itemWithAllFields =
        new MediaItem.Builder()
            .setTitle("title")
            .setMimeType(MimeTypes.VIDEO_MP4)
            .setDescription("desc")
            .setStartPositionUs(3)
            .setEndPositionUs(10)
            .setDrmSchemes(Arrays.asList(drmSchemes))
            .setMedia(media)
            .build();
    ExoCastMessage message =
        new ExoCastMessage.AddItems(
            C.INDEX_UNSET,
            Collections.singletonList(itemWithAllFields),
            new ShuffleOrder.UnshuffledShuffleOrder(1));
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 0));
    JSONObject arguments = messageAsJson.getJSONObject(KEY_ARGS);
    JSONArray items = arguments.getJSONArray(KEY_ITEMS);

    assertJsonEqualsMediaItem(items.getJSONObject(/* index= */ 0), itemWithAllFields);
  }

  @Test
  public void addItems_withShuffleOrder_producesExpectedJson() throws JSONException {
    MediaItem.Builder builder = new MediaItem.Builder();
    MediaItem sampleItem1 = builder.build();
    MediaItem sampleItem2 = builder.build();
    MediaItem sampleItem3 = builder.build();
    MediaItem sampleItem4 = builder.build();

    ExoCastMessage message =
        new ExoCastMessage.AddItems(
            C.INDEX_UNSET,
            Arrays.asList(sampleItem1, sampleItem2, sampleItem3, sampleItem4),
            new ShuffleOrder.DefaultShuffleOrder(new int[] {2, 1, 3, 0}, /* randomSeed= */ 0));
    JSONObject arguments =
        new JSONObject(message.toJsonString(/* sequenceNumber= */ 0)).getJSONObject(KEY_ARGS);
    JSONArray shuffledIndices = arguments.getJSONArray(KEY_SHUFFLE_ORDER);
    assertThat(shuffledIndices.getInt(0)).isEqualTo(2);
    assertThat(shuffledIndices.getInt(1)).isEqualTo(1);
    assertThat(shuffledIndices.getInt(2)).isEqualTo(3);
    assertThat(shuffledIndices.getInt(3)).isEqualTo(0);
  }

  @Test
  public void moveItem_producesExpectedJson() throws JSONException {
    ExoCastMessage message =
        new ExoCastMessage.MoveItem(
            new UUID(0, 1),
            /* index= */ 3,
            new ShuffleOrder.DefaultShuffleOrder(new int[] {2, 1, 3, 0}, /* randomSeed= */ 0));
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 1));
    JSONObject arguments = messageAsJson.getJSONObject(KEY_ARGS);

    assertThat(messageAsJson.getLong(KEY_SEQUENCE_NUMBER)).isEqualTo(1);
    assertThat(messageAsJson.getString(KEY_METHOD)).isEqualTo(METHOD_MOVE_ITEM);
    assertThat(arguments.getString(KEY_UUID)).isEqualTo(new UUID(0, 1).toString());
    assertThat(arguments.getInt(KEY_INDEX)).isEqualTo(3);
    JSONArray shuffledIndices = arguments.getJSONArray(KEY_SHUFFLE_ORDER);
    assertThat(shuffledIndices.getInt(0)).isEqualTo(2);
    assertThat(shuffledIndices.getInt(1)).isEqualTo(1);
    assertThat(shuffledIndices.getInt(2)).isEqualTo(3);
    assertThat(shuffledIndices.getInt(3)).isEqualTo(0);
  }

  @Test
  public void removeItems_withSingleItem_producesExpectedJson() throws JSONException {
    ExoCastMessage message =
        new ExoCastMessage.RemoveItems(Collections.singletonList(new UUID(0, 1)));
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 0));
    JSONArray uuids = messageAsJson.getJSONObject(KEY_ARGS).getJSONArray(KEY_UUIDS);

    assertThat(messageAsJson.getLong(KEY_SEQUENCE_NUMBER)).isEqualTo(0);
    assertThat(messageAsJson.getString(KEY_METHOD)).isEqualTo(METHOD_REMOVE_ITEMS);
    assertThat(uuids.length()).isEqualTo(1);
    assertThat(uuids.getString(0)).isEqualTo(new UUID(0, 1).toString());
  }

  @Test
  public void removeItems_withMultipleItems_producesExpectedJson() throws JSONException {
    ExoCastMessage message =
        new ExoCastMessage.RemoveItems(
            Arrays.asList(new UUID(0, 1), new UUID(0, 2), new UUID(0, 3)));
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 0));
    JSONArray uuids = messageAsJson.getJSONObject(KEY_ARGS).getJSONArray(KEY_UUIDS);

    assertThat(messageAsJson.getLong(KEY_SEQUENCE_NUMBER)).isEqualTo(0);
    assertThat(messageAsJson.getString(KEY_METHOD)).isEqualTo(METHOD_REMOVE_ITEMS);
    assertThat(uuids.length()).isEqualTo(3);
    assertThat(uuids.getString(0)).isEqualTo(new UUID(0, 1).toString());
    assertThat(uuids.getString(1)).isEqualTo(new UUID(0, 2).toString());
    assertThat(uuids.getString(2)).isEqualTo(new UUID(0, 3).toString());
  }

  @Test
  public void setPlayWhenReady_producesExpectedJson() throws JSONException {
    ExoCastMessage message = new ExoCastMessage.SetPlayWhenReady(true);
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 0));

    assertThat(messageAsJson.getLong(KEY_SEQUENCE_NUMBER)).isEqualTo(0);
    assertThat(messageAsJson.getString(KEY_METHOD)).isEqualTo(METHOD_SET_PLAY_WHEN_READY);
    assertThat(messageAsJson.getJSONObject(KEY_ARGS).getBoolean(KEY_PLAY_WHEN_READY)).isTrue();
  }

  @Test
  public void setRepeatMode_withRepeatModeOff_producesExpectedJson() throws JSONException {
    ExoCastMessage message = new ExoCastMessage.SetRepeatMode(Player.REPEAT_MODE_OFF);
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 0));

    assertThat(messageAsJson.getLong(KEY_SEQUENCE_NUMBER)).isEqualTo(0);
    assertThat(messageAsJson.getString(KEY_METHOD)).isEqualTo(METHOD_SET_REPEAT_MODE);
    assertThat(messageAsJson.getJSONObject(KEY_ARGS).getString(KEY_REPEAT_MODE))
        .isEqualTo(STR_REPEAT_MODE_OFF);
  }

  @Test
  public void setRepeatMode_withRepeatModeOne_producesExpectedJson() throws JSONException {
    ExoCastMessage message = new ExoCastMessage.SetRepeatMode(Player.REPEAT_MODE_ONE);
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 0));

    assertThat(messageAsJson.getLong(KEY_SEQUENCE_NUMBER)).isEqualTo(0);
    assertThat(messageAsJson.getString(KEY_METHOD)).isEqualTo(METHOD_SET_REPEAT_MODE);
    assertThat(messageAsJson.getJSONObject(KEY_ARGS).getString(KEY_REPEAT_MODE))
        .isEqualTo(STR_REPEAT_MODE_ONE);
  }

  @Test
  public void setRepeatMode_withRepeatModeAll_producesExpectedJson() throws JSONException {
    ExoCastMessage message = new ExoCastMessage.SetRepeatMode(Player.REPEAT_MODE_ALL);
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 0));

    assertThat(messageAsJson.getLong(KEY_SEQUENCE_NUMBER)).isEqualTo(0);
    assertThat(messageAsJson.getString(KEY_METHOD)).isEqualTo(METHOD_SET_REPEAT_MODE);
    assertThat(messageAsJson.getJSONObject(KEY_ARGS).getString(KEY_REPEAT_MODE))
        .isEqualTo(STR_REPEAT_MODE_ALL);
  }

  @Test
  public void setShuffleModeEnabled_producesExpectedJson() throws JSONException {
    ExoCastMessage message =
        new ExoCastMessage.SetShuffleModeEnabled(/* shuffleModeEnabled= */ false);
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 0));

    assertThat(messageAsJson.getLong(KEY_SEQUENCE_NUMBER)).isEqualTo(0);
    assertThat(messageAsJson.getString(KEY_METHOD)).isEqualTo(METHOD_SET_SHUFFLE_MODE_ENABLED);
    assertThat(messageAsJson.getJSONObject(KEY_ARGS).getBoolean(KEY_SHUFFLE_MODE_ENABLED))
        .isFalse();
  }

  @Test
  public void seekTo_withPositionInItem_addsPositionField() throws JSONException {
    ExoCastMessage message = new ExoCastMessage.SeekTo(new UUID(0, 1), /* positionMs= */ 10);
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 0));
    JSONObject arguments = messageAsJson.getJSONObject(KEY_ARGS);

    assertThat(messageAsJson.getLong(KEY_SEQUENCE_NUMBER)).isEqualTo(0);
    assertThat(messageAsJson.getString(KEY_METHOD)).isEqualTo(METHOD_SEEK_TO);
    assertThat(arguments.getString(KEY_UUID)).isEqualTo(new UUID(0, 1).toString());
    assertThat(arguments.getLong(KEY_POSITION_MS)).isEqualTo(10);
  }

  @Test
  public void seekTo_withUnsetPosition_doesNotAddPositionField() throws JSONException {
    ExoCastMessage message =
        new ExoCastMessage.SeekTo(new UUID(0, 1), /* positionMs= */ C.TIME_UNSET);
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 0));
    JSONObject arguments = messageAsJson.getJSONObject(KEY_ARGS);

    assertThat(messageAsJson.getLong(KEY_SEQUENCE_NUMBER)).isEqualTo(0);
    assertThat(messageAsJson.getString(KEY_METHOD)).isEqualTo(METHOD_SEEK_TO);
    assertThat(arguments.getString(KEY_UUID)).isEqualTo(new UUID(0, 1).toString());
    assertThat(arguments.has(KEY_POSITION_MS)).isFalse();
  }

  @Test
  public void setPlaybackParameters_producesExpectedJson() throws JSONException {
    ExoCastMessage message =
        new ExoCastMessage.SetPlaybackParameters(
            new PlaybackParameters(/* speed= */ 0.5f, /* pitch= */ 2, /* skipSilence= */ false));
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 0));
    JSONObject arguments = messageAsJson.getJSONObject(KEY_ARGS);

    assertThat(messageAsJson.getLong(KEY_SEQUENCE_NUMBER)).isEqualTo(0);
    assertThat(messageAsJson.getString(KEY_METHOD)).isEqualTo(METHOD_SET_PLAYBACK_PARAMETERS);
    assertThat(arguments.getDouble(KEY_SPEED)).isEqualTo(0.5);
    assertThat(arguments.getDouble(KEY_PITCH)).isEqualTo(2.0);
    assertThat(arguments.getBoolean(KEY_SKIP_SILENCE)).isFalse();
  }

  @Test
  public void setSelectionParameters_producesExpectedJson() throws JSONException {
    ExoCastMessage message =
        new ExoCastMessage.SetTrackSelectionParameters(
            TrackSelectionParameters.DEFAULT
                .buildUpon()
                .setDisabledTextTrackSelectionFlags(
                    C.SELECTION_FLAG_AUTOSELECT | C.SELECTION_FLAG_DEFAULT)
                .setSelectUndeterminedTextLanguage(true)
                .setPreferredAudioLanguage("esp")
                .setPreferredTextLanguage("deu")
                .build());
    JSONObject messageAsJson = new JSONObject(message.toJsonString(/* sequenceNumber= */ 0));
    JSONObject arguments = messageAsJson.getJSONObject(KEY_ARGS);

    assertThat(messageAsJson.getLong(KEY_SEQUENCE_NUMBER)).isEqualTo(0);
    assertThat(messageAsJson.getString(KEY_METHOD))
        .isEqualTo(METHOD_SET_TRACK_SELECTION_PARAMETERS);
    assertThat(arguments.getBoolean(KEY_SELECT_UNDETERMINED_TEXT_LANGUAGE)).isTrue();
    assertThat(arguments.getString(KEY_PREFERRED_AUDIO_LANGUAGE)).isEqualTo("esp");
    assertThat(arguments.getString(KEY_PREFERRED_TEXT_LANGUAGE)).isEqualTo("deu");
    ArrayList<String> selectionFlagStrings = new ArrayList<>();
    JSONArray selectionFlagsJson = arguments.getJSONArray(KEY_DISABLED_TEXT_TRACK_SELECTION_FLAGS);
    for (int i = 0; i < selectionFlagsJson.length(); i++) {
      selectionFlagStrings.add(selectionFlagsJson.getString(i));
    }
    assertThat(selectionFlagStrings).contains(ExoCastConstants.STR_SELECTION_FLAG_AUTOSELECT);
    assertThat(selectionFlagStrings).doesNotContain(ExoCastConstants.STR_SELECTION_FLAG_FORCED);
    assertThat(selectionFlagStrings).contains(ExoCastConstants.STR_SELECTION_FLAG_DEFAULT);
  }

  private static void assertJsonEqualsMediaItem(JSONObject itemAsJson, MediaItem mediaItem)
      throws JSONException {
    assertThat(itemAsJson.getString(KEY_UUID)).isEqualTo(mediaItem.uuid.toString());
    assertThat(itemAsJson.getString(KEY_TITLE)).isEqualTo(mediaItem.title);
    assertThat(itemAsJson.getString(KEY_MIME_TYPE)).isEqualTo(mediaItem.mimeType);
    assertThat(itemAsJson.getString(KEY_DESCRIPTION)).isEqualTo(mediaItem.description);
    assertJsonMatchesTimestamp(itemAsJson, KEY_START_POSITION_US, mediaItem.startPositionUs);
    assertJsonMatchesTimestamp(itemAsJson, KEY_END_POSITION_US, mediaItem.endPositionUs);
    assertJsonMatchesUriBundle(itemAsJson, KEY_MEDIA, mediaItem.media);

    List<DrmScheme> drmSchemes = mediaItem.drmSchemes;
    int drmSchemesLength = drmSchemes.size();
    JSONArray drmSchemesAsJson = itemAsJson.getJSONArray(KEY_DRM_SCHEMES);

    assertThat(drmSchemesAsJson.length()).isEqualTo(drmSchemesLength);
    for (int i = 0; i < drmSchemesLength; i++) {
      DrmScheme drmScheme = drmSchemes.get(i);
      JSONObject drmSchemeAsJson = drmSchemesAsJson.getJSONObject(i);

      assertThat(drmSchemeAsJson.getString(KEY_UUID)).isEqualTo(drmScheme.uuid.toString());
      assertJsonMatchesUriBundle(drmSchemeAsJson, KEY_LICENSE_SERVER, drmScheme.licenseServer);
    }
  }

  private static void assertJsonMatchesUriBundle(
      JSONObject jsonObject, String key, @Nullable UriBundle uriBundle) throws JSONException {
    if (uriBundle == null) {
      assertThat(jsonObject.has(key)).isFalse();
      return;
    }
    JSONObject uriBundleAsJson = jsonObject.getJSONObject(key);
    assertThat(uriBundleAsJson.getString(KEY_URI)).isEqualTo(uriBundle.uri.toString());
    Map<String, String> requestHeaders = uriBundle.requestHeaders;
    JSONObject requestHeadersAsJson = uriBundleAsJson.getJSONObject(KEY_REQUEST_HEADERS);

    assertThat(requestHeadersAsJson.length()).isEqualTo(requestHeaders.size());
    for (String headerKey : requestHeaders.keySet()) {
      assertThat(requestHeadersAsJson.getString(headerKey))
          .isEqualTo(requestHeaders.get(headerKey));
    }
  }

  private static void assertJsonMatchesTimestamp(JSONObject object, String key, long timestamp)
      throws JSONException {
    if (timestamp == C.TIME_UNSET) {
      assertThat(object.has(key)).isFalse();
    } else {
      assertThat(object.getLong(key)).isEqualTo(timestamp);
    }
  }
}
