/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.dash.mpd;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a ContentProtection tag in an AdaptationSet. Holds arbitrary data for various DRM
 * schemes.
 */
public final class ContentProtection {

  /**
   * Identifies the content protection scheme.
   */
  public final String schemeUriId;
  /**
   * Protection scheme specific data.
   */
  public final Map<String, String> keyedData;

  /**
   * @param schemeUriId Identifies the content protection scheme.
   * @param keyedData Data specific to the scheme.
   */
  public ContentProtection(String schemeUriId, Map<String, String> keyedData) {
    this.schemeUriId = schemeUriId;
    if (keyedData != null) {
      this.keyedData = Collections.unmodifiableMap(keyedData);
    } else {
      this.keyedData = Collections.emptyMap();
    }

  }

}
