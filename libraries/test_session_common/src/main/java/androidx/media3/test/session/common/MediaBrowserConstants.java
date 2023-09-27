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
package androidx.media3.test.session.common;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

/** Constants for calling MediaBrowser methods. */
public class MediaBrowserConstants {

  public static final String ROOT_ID = "rootId";
  public static final String ROOT_ID_SUPPORTS_BROWSABLE_CHILDREN_ONLY =
      "root_id_supports_browsable_children_only";
  public static final Bundle ROOT_EXTRAS = new Bundle();
  public static final String ROOT_EXTRAS_KEY = "root_extras_key";
  public static final int ROOT_EXTRAS_VALUE = 4321;

  public static final String MEDIA_ID_GET_BROWSABLE_ITEM = "media_id_get_browsable_item";
  public static final String MEDIA_ID_GET_PLAYABLE_ITEM = "media_id_get_playable_item";
  public static final String MEDIA_ID_GET_ITEM_WITH_METADATA = "media_id_get_item_with_metadata";

  public static final String PARENT_ID = "parent_id";
  public static final String PARENT_ID_LONG_LIST = "parent_id_long_list";
  public static final String PARENT_ID_NO_CHILDREN = "parent_id_no_children";
  public static final String PARENT_ID_ERROR = "parent_id_error";
  public static final String PARENT_ID_AUTH_EXPIRED_ERROR = "parent_auth_expired_error";
  public static final String PARENT_ID_AUTH_EXPIRED_ERROR_KEY_ERROR_RESOLUTION_ACTION_LABEL =
      "parent_auth_expired_error_label";

  public static final List<String> GET_CHILDREN_RESULT = new ArrayList<>();
  public static final int CHILDREN_COUNT = 100;

  public static final int LONG_LIST_COUNT = 5_000;

  public static final String SEARCH_QUERY = "search_query";
  public static final String SEARCH_QUERY_LONG_LIST = "search_query_long_list";
  public static final String SEARCH_QUERY_TAKES_TIME = "search_query_takes_time";
  public static final long SEARCH_TIME_IN_MS = 5_000;
  public static final String SEARCH_QUERY_EMPTY_RESULT = "search_query_empty_result";
  public static final String SEARCH_QUERY_ERROR = "search_query_error";

  public static final List<String> SEARCH_RESULT = new ArrayList<>();
  public static final int SEARCH_RESULT_COUNT = 50;

  public static final String SUBSCRIBE_PARENT_ID_1 = "subscribe_parent_id_1";
  public static final String SUBSCRIBE_PARENT_ID_2 = "subscribe_parent_id_2";
  public static final String EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_MEDIA_ID =
      "notify_children_changed_media_id";
  public static final String EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_ITEM_COUNT =
      "notify_children_changed_item_count";
  public static final String EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_DELAY_MS =
      "notify_children_changed_delay";
  public static final String EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_BROADCAST =
      "notify_children_changed_broadcast";

  public static final String CUSTOM_ACTION = "customAction";
  public static final Bundle CUSTOM_ACTION_EXTRAS = new Bundle();

  public static final String CUSTOM_ACTION_ASSERT_PARAMS = "assertParams";

  static {
    ROOT_EXTRAS.putInt(ROOT_EXTRAS_KEY, ROOT_EXTRAS_VALUE);

    CUSTOM_ACTION_EXTRAS.putString(CUSTOM_ACTION, CUSTOM_ACTION);

    GET_CHILDREN_RESULT.clear();
    String getChildrenMediaIdPrefix = "get_children_media_id_";
    for (int i = 0; i < CHILDREN_COUNT; i++) {
      GET_CHILDREN_RESULT.add(getChildrenMediaIdPrefix + i);
    }

    SEARCH_RESULT.clear();
    String getSearchResultMediaIdPrefix = "get_search_result_media_id_";
    for (int i = 0; i < SEARCH_RESULT_COUNT; i++) {
      SEARCH_RESULT.add(getSearchResultMediaIdPrefix + i);
    }
  }

  private MediaBrowserConstants() {}
}
