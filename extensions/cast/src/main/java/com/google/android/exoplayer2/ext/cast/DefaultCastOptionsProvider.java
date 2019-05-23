/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.ext.cast;

import android.content.Context;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import java.util.List;

/**
 * A convenience {@link OptionsProvider} to target the default cast receiver app.
 */
public final class DefaultCastOptionsProvider implements OptionsProvider {

  /**
   * App id of the Default Media Receiver app. Apps that do not require DRM support may use this
   * receiver receiver app ID.
   *
   * <p>See https://developers.google.com/cast/docs/caf_receiver/#default_media_receiver.
   */
  public static final String APP_ID_DEFAULT_RECEIVER =
      CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID;

  /**
   * App id for receiver app with rudimentary support for DRM.
   *
   * <p>This app id is only suitable for ExoPlayer's Cast Demo app, and it is not intended for
   * production use. In order to use DRM, custom receiver apps should be used. For environments that
   * do not require DRM, the default receiver app should be used (see {@link
   * #APP_ID_DEFAULT_RECEIVER}).
   */
  //  TODO: Add a documentation resource link for DRM support in the receiver app [Internal ref:
  // b/128603245].
  public static final String APP_ID_DEFAULT_RECEIVER_WITH_DRM = "A12D4273";

  @Override
  public CastOptions getCastOptions(Context context) {
    return new CastOptions.Builder()
        .setReceiverApplicationId(APP_ID_DEFAULT_RECEIVER_WITH_DRM)
        .setStopReceiverApplicationWhenEndingSession(true)
        .build();
  }

  @Override
  public List<SessionProvider> getAdditionalSessionProviders(Context context) {
    return null;
  }

}
