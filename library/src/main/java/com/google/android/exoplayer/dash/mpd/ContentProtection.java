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

import com.google.android.exoplayer.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

/**
 * Represents a ContentProtection tag in an AdaptationSet.
 */
public class ContentProtection {

  /**
   * Identifies the content protection scheme.
   */
  public final String schemeUriId;

  /**
   * Protection scheme specific initialization data. May be null.
   */
  public final SchemeData schemeData;

  /**
   * @param schemeUriId Identifies the content protection scheme.
   * @param schemeData Protection scheme specific initialization data. May be null.
   */
  public ContentProtection(String schemeUriId, SchemeData schemeData) {
    this.schemeUriId = Assertions.checkNotNull(schemeUriId);
    this.schemeData = schemeData;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ContentProtection)) {
      return false;
    }
    if (obj == this) {
      return true;
    }

    ContentProtection other = (ContentProtection) obj;
    return schemeUriId.equals(other.schemeUriId) && Util.areEqual(schemeData, other.schemeData);
  }

  @Override
  public int hashCode() {
    return (31 * schemeUriId.hashCode()) + (schemeData != null ? schemeData.hashCode() : 0);
  }

}
