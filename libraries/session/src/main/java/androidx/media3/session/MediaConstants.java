/*
 * Copyright 2019 The Android Open Source Project
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

import android.net.Uri;

/** Constants that can be shared between media session and controller. */
public final class MediaConstants {

  /**
   * A {@link Uri} scheme used in a media uri.
   *
   * @see MediaController#setMediaUri
   * @see MediaSession.SessionCallback#onSetMediaUri
   */
  public static final String MEDIA_URI_SCHEME = "androidx";

  /**
   * A {@link Uri} authority used in a media uri.
   *
   * @see MediaController#setMediaUri
   * @see MediaSession.SessionCallback#onSetMediaUri
   */
  public static final String MEDIA_URI_AUTHORITY = "media3-session";

  /**
   * A {@link Uri} path used by {@code
   * android.support.v4.media.session.MediaControllerCompat.TransportControls#playFromMediaId}.
   *
   * @see MediaController#setMediaUri
   * @see MediaSession.SessionCallback#onSetMediaUri
   */
  public static final String MEDIA_URI_PATH_PLAY_FROM_MEDIA_ID = "playFromMediaId";

  /**
   * A {@link Uri} path used by {@code
   * android.support.v4.media.session.MediaControllerCompat.TransportControls#playFromSearch}.
   *
   * @see MediaController#setMediaUri
   * @see MediaSession.SessionCallback#onSetMediaUri
   */
  public static final String MEDIA_URI_PATH_PLAY_FROM_SEARCH = "playFromSearch";

  /**
   * A {@link Uri} path used by {@link
   * android.support.v4.media.session.MediaControllerCompat.TransportControls#prepareFromMediaId}.
   *
   * @see MediaController#setMediaUri
   * @see MediaSession.SessionCallback#onSetMediaUri
   */
  public static final String MEDIA_URI_PATH_PREPARE_FROM_MEDIA_ID = "prepareFromMediaId";

  /**
   * A {@link Uri} path used by {@link
   * android.support.v4.media.session.MediaControllerCompat.TransportControls#prepareFromSearch}.
   *
   * @see MediaController#setMediaUri
   * @see MediaSession.SessionCallback#onSetMediaUri
   */
  public static final String MEDIA_URI_PATH_PREPARE_FROM_SEARCH = "prepareFromSearch";

  /**
   * A {@link Uri} path for encoding how the uri will be translated when connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}.
   *
   * @see MediaController#setMediaUri
   */
  public static final String MEDIA_URI_PATH_SET_MEDIA_URI = "setMediaUri";

  // From scheme to path, plus path delimiter
  /* package */ static final String MEDIA_URI_SET_MEDIA_URI_PREFIX =
      new Uri.Builder()
              .scheme(MEDIA_URI_SCHEME)
              .authority(MEDIA_URI_AUTHORITY)
              .path(MEDIA_URI_PATH_SET_MEDIA_URI)
              .build()
              .toString()
          + "?";

  /**
   * A {@link Uri} query for media id.
   *
   * @see MediaSession.SessionCallback#onSetMediaUri
   * @see MediaController#setMediaUri
   */
  public static final String MEDIA_URI_QUERY_ID = "id";

  /**
   * A {@link Uri} query for search query.
   *
   * @see MediaSession.SessionCallback#onSetMediaUri
   * @see MediaController#setMediaUri
   */
  public static final String MEDIA_URI_QUERY_QUERY = "query";

  /**
   * A {@link Uri} query for media uri.
   *
   * @see MediaController#setMediaUri
   */
  public static final String MEDIA_URI_QUERY_URI = "uri";

  /* package */ static final String SESSION_COMMAND_ON_EXTRAS_CHANGED =
      "androidx.media3.session.SESSION_COMMAND_ON_EXTRAS_CHANGED";
  /* package */ static final String SESSION_COMMAND_ON_CAPTIONING_ENABLED_CHANGED =
      "androidx.media3.session.SESSION_COMMAND_ON_CAPTIONING_ENABLED_CHANGED";
  /* package */ static final String SESSION_COMMAND_REQUEST_SESSION3_TOKEN =
      "androidx.media3.session.SESSION_COMMAND_REQUEST_SESSION3_TOKEN";

  /* package */ static final String ARGUMENT_CAPTIONING_ENABLED =
      "androidx.media3.session.ARGUMENT_CAPTIONING_ENABLED";

  private MediaConstants() {}
}
