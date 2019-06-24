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

import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DEFAULT_START_POSITION_US;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DISABLED_TEXT_TRACK_SELECTION_FLAGS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DISCONTINUITY_REASON;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DURATION_US;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_ERROR_MESSAGE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_ID;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_IS_DYNAMIC;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_IS_LOADING;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_IS_SEEKABLE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_MEDIA_ITEMS_INFO;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_MEDIA_QUEUE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PERIODS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PERIOD_ID;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PITCH;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PLAYBACK_PARAMETERS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PLAYBACK_POSITION;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PLAYBACK_STATE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PLAY_WHEN_READY;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_POSITION_IN_FIRST_PERIOD_US;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_POSITION_MS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PREFERRED_AUDIO_LANGUAGE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PREFERRED_TEXT_LANGUAGE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_REPEAT_MODE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SELECT_UNDETERMINED_TEXT_LANGUAGE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SEQUENCE_NUMBER;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SHUFFLE_MODE_ENABLED;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SHUFFLE_ORDER;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SKIP_SILENCE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SPEED;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_TRACK_SELECTION_PARAMETERS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_UUID;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_WINDOW_DURATION_US;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_DISCONTINUITY_REASON_SEEK;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_REPEAT_MODE_OFF;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_SELECTION_FLAG_FORCED;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_STATE_BUFFERING;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ReceiverAppStateUpdate}. */
@RunWith(AndroidJUnit4.class)
public class ReceiverAppStateUpdateTest {

  private static final long MOCK_SEQUENCE_NUMBER = 1;

  @Test
  public void statusUpdate_withPlayWhenReady_producesExpectedUpdate() throws JSONException {
    ReceiverAppStateUpdate stateUpdate =
        ReceiverAppStateUpdate.builder(MOCK_SEQUENCE_NUMBER).setPlayWhenReady(true).build();
    JSONObject stateMessage = createStateMessage().put(KEY_PLAY_WHEN_READY, true);

    assertThat(ReceiverAppStateUpdate.fromJsonMessage(stateMessage.toString()))
        .isEqualTo(stateUpdate);
  }

  @Test
  public void statusUpdate_withPlaybackState_producesExpectedUpdate() throws JSONException {
    ReceiverAppStateUpdate stateUpdate =
        ReceiverAppStateUpdate.builder(MOCK_SEQUENCE_NUMBER)
            .setPlaybackState(Player.STATE_BUFFERING)
            .build();
    JSONObject stateMessage = createStateMessage().put(KEY_PLAYBACK_STATE, STR_STATE_BUFFERING);

    assertThat(ReceiverAppStateUpdate.fromJsonMessage(stateMessage.toString()))
        .isEqualTo(stateUpdate);
  }

  @Test
  public void statusUpdate_withMediaQueue_producesExpectedUpdate() throws JSONException {
    HashMap<String, String> requestHeaders = new HashMap<>();
    requestHeaders.put("key", "value");
    MediaItem.UriBundle media = new MediaItem.UriBundle(Uri.parse("www.media.com"), requestHeaders);
    MediaItem.DrmScheme drmScheme1 =
        new MediaItem.DrmScheme(
            C.WIDEVINE_UUID,
            new MediaItem.UriBundle(Uri.parse("www.widevine.com"), requestHeaders));
    MediaItem.DrmScheme drmScheme2 =
        new MediaItem.DrmScheme(
            C.PLAYREADY_UUID,
            new MediaItem.UriBundle(Uri.parse("www.playready.com"), requestHeaders));
    MediaItem item =
        new MediaItem.Builder()
            .setTitle("title")
            .setDescription("description")
            .setMedia(media)
            .setDrmSchemes(Arrays.asList(drmScheme1, drmScheme2))
            .setStartPositionUs(10)
            .setEndPositionUs(20)
            .build();
    ReceiverAppStateUpdate stateUpdate =
        ReceiverAppStateUpdate.builder(MOCK_SEQUENCE_NUMBER)
            .setItems(Collections.singletonList(item))
            .build();
    JSONObject object =
        createStateMessage()
            .put(KEY_MEDIA_QUEUE, new JSONArray().put(ExoCastMessage.mediaItemAsJsonObject(item)));

    assertThat(ReceiverAppStateUpdate.fromJsonMessage(object.toString())).isEqualTo(stateUpdate);
  }

  @Test
  public void statusUpdate_withRepeatMode_producesExpectedUpdate() throws JSONException {
    ReceiverAppStateUpdate stateUpdate =
        ReceiverAppStateUpdate.builder(MOCK_SEQUENCE_NUMBER)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .build();
    JSONObject stateMessage = createStateMessage().put(KEY_REPEAT_MODE, STR_REPEAT_MODE_OFF);

    assertThat(ReceiverAppStateUpdate.fromJsonMessage(stateMessage.toString()))
        .isEqualTo(stateUpdate);
  }

  @Test
  public void statusUpdate_withShuffleModeEnabled_producesExpectedUpdate() throws JSONException {
    ReceiverAppStateUpdate stateUpdate =
        ReceiverAppStateUpdate.builder(MOCK_SEQUENCE_NUMBER).setShuffleModeEnabled(false).build();
    JSONObject stateMessage = createStateMessage().put(KEY_SHUFFLE_MODE_ENABLED, false);

    assertThat(ReceiverAppStateUpdate.fromJsonMessage(stateMessage.toString()))
        .isEqualTo(stateUpdate);
  }

  @Test
  public void statusUpdate_withIsLoading_producesExpectedUpdate() throws JSONException {
    ReceiverAppStateUpdate stateUpdate =
        ReceiverAppStateUpdate.builder(MOCK_SEQUENCE_NUMBER).setIsLoading(true).build();
    JSONObject stateMessage = createStateMessage().put(KEY_IS_LOADING, true);

    assertThat(ReceiverAppStateUpdate.fromJsonMessage(stateMessage.toString()))
        .isEqualTo(stateUpdate);
  }

  @Test
  public void statusUpdate_withPlaybackParameters_producesExpectedUpdate() throws JSONException {
    ReceiverAppStateUpdate stateUpdate =
        ReceiverAppStateUpdate.builder(MOCK_SEQUENCE_NUMBER)
            .setPlaybackParameters(
                new PlaybackParameters(
                    /* speed= */ .5f, /* pitch= */ .25f, /* skipSilence= */ false))
            .build();
    JSONObject playbackParamsJson =
        new JSONObject().put(KEY_SPEED, .5).put(KEY_PITCH, .25).put(KEY_SKIP_SILENCE, false);
    JSONObject stateMessage = createStateMessage().put(KEY_PLAYBACK_PARAMETERS, playbackParamsJson);

    assertThat(ReceiverAppStateUpdate.fromJsonMessage(stateMessage.toString()))
        .isEqualTo(stateUpdate);
  }

  @Test
  public void statusUpdate_withTrackSelectionParameters_producesExpectedUpdate()
      throws JSONException {
    ReceiverAppStateUpdate stateUpdate =
        ReceiverAppStateUpdate.builder(MOCK_SEQUENCE_NUMBER)
            .setTrackSelectionParameters(
                TrackSelectionParameters.DEFAULT
                    .buildUpon()
                    .setDisabledTextTrackSelectionFlags(
                        C.SELECTION_FLAG_FORCED | C.SELECTION_FLAG_DEFAULT)
                    .setPreferredAudioLanguage("esp")
                    .setPreferredTextLanguage("deu")
                    .setSelectUndeterminedTextLanguage(true)
                    .build())
            .build();

    JSONArray selectionFlagsJson =
        new JSONArray()
            .put(ExoCastConstants.STR_SELECTION_FLAG_DEFAULT)
            .put(STR_SELECTION_FLAG_FORCED);
    JSONObject playbackParamsJson =
        new JSONObject()
            .put(KEY_DISABLED_TEXT_TRACK_SELECTION_FLAGS, selectionFlagsJson)
            .put(KEY_PREFERRED_AUDIO_LANGUAGE, "esp")
            .put(KEY_PREFERRED_TEXT_LANGUAGE, "deu")
            .put(KEY_SELECT_UNDETERMINED_TEXT_LANGUAGE, true);
    JSONObject object =
        createStateMessage().put(KEY_TRACK_SELECTION_PARAMETERS, playbackParamsJson);

    assertThat(ReceiverAppStateUpdate.fromJsonMessage(object.toString())).isEqualTo(stateUpdate);
  }

  @Test
  public void statusUpdate_withError_producesExpectedUpdate() throws JSONException {
    ReceiverAppStateUpdate stateUpdate =
        ReceiverAppStateUpdate.builder(MOCK_SEQUENCE_NUMBER)
            .setErrorMessage("error message")
            .build();
    JSONObject stateMessage = createStateMessage().put(KEY_ERROR_MESSAGE, "error message");

    assertThat(ReceiverAppStateUpdate.fromJsonMessage(stateMessage.toString()))
        .isEqualTo(stateUpdate);
  }

  @Test
  public void statusUpdate_withPlaybackPosition_producesExpectedUpdate() throws JSONException {
    ReceiverAppStateUpdate stateUpdate =
        ReceiverAppStateUpdate.builder(MOCK_SEQUENCE_NUMBER)
            .setPlaybackPosition(
                new UUID(/* mostSigBits= */ 0, /* leastSigBits= */ 1), "period", 10L)
            .build();
    JSONObject positionJson =
        new JSONObject()
            .put(KEY_UUID, new UUID(0, 1))
            .put(KEY_POSITION_MS, 10)
            .put(KEY_PERIOD_ID, "period");
    JSONObject stateMessage = createStateMessage().put(KEY_PLAYBACK_POSITION, positionJson);

    assertThat(ReceiverAppStateUpdate.fromJsonMessage(stateMessage.toString()))
        .isEqualTo(stateUpdate);
  }

  @Test
  public void statusUpdate_withDiscontinuity_producesExpectedUpdate() throws JSONException {
    ReceiverAppStateUpdate stateUpdate =
        ReceiverAppStateUpdate.builder(MOCK_SEQUENCE_NUMBER)
            .setPlaybackPosition(
                new UUID(/* mostSigBits= */ 0, /* leastSigBits= */ 1), "period", 10L)
            .setDiscontinuityReason(Player.DISCONTINUITY_REASON_SEEK)
            .build();
    JSONObject positionJson =
        new JSONObject()
            .put(KEY_UUID, new UUID(0, 1))
            .put(KEY_POSITION_MS, 10)
            .put(KEY_PERIOD_ID, "period")
            .put(KEY_DISCONTINUITY_REASON, STR_DISCONTINUITY_REASON_SEEK);
    JSONObject stateMessage = createStateMessage().put(KEY_PLAYBACK_POSITION, positionJson);

    assertThat(ReceiverAppStateUpdate.fromJsonMessage(stateMessage.toString()))
        .isEqualTo(stateUpdate);
  }

  @Test
  public void statusUpdate_withMediaItemInfo_producesExpectedTimeline() throws JSONException {
    MediaItem.Builder builder = new MediaItem.Builder();
    MediaItem item1 = builder.setUuid(new UUID(0, 1)).build();
    MediaItem item2 = builder.setUuid(new UUID(0, 2)).build();

    JSONArray periodsJson = new JSONArray();
    periodsJson
        .put(new JSONObject().put(KEY_ID, "id1").put(KEY_DURATION_US, 5000000L))
        .put(new JSONObject().put(KEY_ID, "id2").put(KEY_DURATION_US, 7000000L))
        .put(new JSONObject().put(KEY_ID, "id3").put(KEY_DURATION_US, 6000000L));
    JSONObject mediaItemInfoForUuid1 = new JSONObject();
    mediaItemInfoForUuid1
        .put(KEY_WINDOW_DURATION_US, 10000000L)
        .put(KEY_DEFAULT_START_POSITION_US, 1000000L)
        .put(KEY_PERIODS, periodsJson)
        .put(KEY_POSITION_IN_FIRST_PERIOD_US, 2000000L)
        .put(KEY_IS_DYNAMIC, false)
        .put(KEY_IS_SEEKABLE, true);
    JSONObject mediaItemInfoMapJson =
        new JSONObject().put(new UUID(0, 1).toString(), mediaItemInfoForUuid1);

    JSONObject receiverAppStateUpdateJson =
        createStateMessage().put(KEY_MEDIA_ITEMS_INFO, mediaItemInfoMapJson);
    ReceiverAppStateUpdate receiverAppStateUpdate =
        ReceiverAppStateUpdate.fromJsonMessage(receiverAppStateUpdateJson.toString());
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(item1, item2),
            receiverAppStateUpdate.mediaItemsInformation,
            new ShuffleOrder.DefaultShuffleOrder(
                /* shuffledIndices= */ new int[] {1, 0}, /* randomSeed= */ 0));
    Timeline.Window window0 =
        timeline.getWindow(/* windowIndex= */ 0, new Timeline.Window(), /* setTag= */ true);
    Timeline.Window window1 =
        timeline.getWindow(/* windowIndex= */ 1, new Timeline.Window(), /* setTag= */ true);
    Timeline.Period[] periods = new Timeline.Period[4];
    for (int i = 0; i < 4; i++) {
      periods[i] =
          timeline.getPeriod(/* periodIndex= */ i, new Timeline.Period(), /* setIds= */ true);
    }

    assertThat(timeline.getWindowCount()).isEqualTo(2);
    assertThat(window0.positionInFirstPeriodUs).isEqualTo(2000000L);
    assertThat(window0.durationUs).isEqualTo(10000000L);
    assertThat(window0.isDynamic).isFalse();
    assertThat(window0.isSeekable).isTrue();
    assertThat(window0.defaultPositionUs).isEqualTo(1000000L);
    assertThat(window1.positionInFirstPeriodUs).isEqualTo(0L);
    assertThat(window1.durationUs).isEqualTo(C.TIME_UNSET);
    assertThat(window1.isDynamic).isTrue();
    assertThat(window1.isSeekable).isFalse();
    assertThat(window1.defaultPositionUs).isEqualTo(0L);

    assertThat(timeline.getPeriodCount()).isEqualTo(4);
    assertThat(periods[0].id).isEqualTo("id1");
    assertThat(periods[0].getPositionInWindowUs()).isEqualTo(-2000000L);
    assertThat(periods[0].durationUs).isEqualTo(5000000L);
    assertThat(periods[1].id).isEqualTo("id2");
    assertThat(periods[1].durationUs).isEqualTo(7000000L);
    assertThat(periods[1].getPositionInWindowUs()).isEqualTo(3000000L);
    assertThat(periods[2].id).isEqualTo("id3");
    assertThat(periods[2].durationUs).isEqualTo(6000000L);
    assertThat(periods[2].getPositionInWindowUs()).isEqualTo(10000000L);
    assertThat(periods[3].durationUs).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void statusUpdate_withShuffleOrder_producesExpectedTimeline() throws JSONException {
    MediaItem.Builder builder = new MediaItem.Builder();
    JSONObject receiverAppStateUpdateJson =
        createStateMessage().put(KEY_SHUFFLE_ORDER, new JSONArray(Arrays.asList(2, 3, 1, 0)));
    ReceiverAppStateUpdate receiverAppStateUpdate =
        ReceiverAppStateUpdate.fromJsonMessage(receiverAppStateUpdateJson.toString());
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            /* mediaItems= */ Arrays.asList(
                builder.build(), builder.build(), builder.build(), builder.build()),
            /* mediaItemInfoMap= */ Collections.emptyMap(),
            /* shuffleOrder= */ new ShuffleOrder.DefaultShuffleOrder(
                Util.toArray(receiverAppStateUpdate.shuffleOrder), /* randomSeed= */ 0));

    assertThat(timeline.getFirstWindowIndex(/* shuffleModeEnabled= */ true)).isEqualTo(2);
    assertThat(
            timeline.getNextWindowIndex(
                /* windowIndex= */ 2,
                /* repeatMode= */ Player.REPEAT_MODE_OFF,
                /* shuffleModeEnabled= */ true))
        .isEqualTo(3);
    assertThat(
            timeline.getNextWindowIndex(
                /* windowIndex= */ 3,
                /* repeatMode= */ Player.REPEAT_MODE_OFF,
                /* shuffleModeEnabled= */ true))
        .isEqualTo(1);
    assertThat(
            timeline.getNextWindowIndex(
                /* windowIndex= */ 1,
                /* repeatMode= */ Player.REPEAT_MODE_OFF,
                /* shuffleModeEnabled= */ true))
        .isEqualTo(0);
    assertThat(
            timeline.getNextWindowIndex(
                /* windowIndex= */ 0,
                /* repeatMode= */ Player.REPEAT_MODE_OFF,
                /* shuffleModeEnabled= */ true))
        .isEqualTo(C.INDEX_UNSET);
  }

  private static JSONObject createStateMessage() throws JSONException {
    return new JSONObject().put(KEY_SEQUENCE_NUMBER, MOCK_SEQUENCE_NUMBER);
  }
}
