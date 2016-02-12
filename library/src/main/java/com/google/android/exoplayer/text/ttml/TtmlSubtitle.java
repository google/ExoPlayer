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
package com.google.android.exoplayer.text.ttml;

import android.text.Layout;
import android.util.Log;

import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.Subtitle;
import com.google.android.exoplayer.util.Util;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A representation of a TTML subtitle.
 */
public final class TtmlSubtitle implements Subtitle {

  private final TtmlNode root;
  private Map<String, TtmlRegion> globalRegions;
  private final long[] eventTimesUs;
  private final Map<String, TtmlStyle> globalStyles;

  public TtmlSubtitle(TtmlNode root, Map<String, TtmlStyle> globalStyles, Map<String, TtmlRegion> globalRegions) {
    this.root = root;
    this.globalRegions = globalRegions;
    this.globalStyles = globalStyles != null
        ? Collections.unmodifiableMap(globalStyles) : Collections.<String, TtmlStyle>emptyMap();
    this.eventTimesUs = root.getEventTimesUs();
  }

  @Override
  public int getNextEventTimeIndex(long timeUs) {
    int index = Util.binarySearchCeil(eventTimesUs, timeUs, false, false);
    return index < eventTimesUs.length ? index : -1;
  }

  @Override
  public int getEventTimeCount() {
    return eventTimesUs.length;
  }

  @Override
  public long getEventTime(int index) {
    return eventTimesUs[index];
  }

  @Override
  public long getLastEventTime() {
    return (eventTimesUs.length == 0 ? -1 : eventTimesUs[eventTimesUs.length - 1]);
  }

  /* @VisibleForTesting */
  /* package */ TtmlNode getRoot() {
    return root;
  }

  @Override
  public List<Cue> getCues(long timeUs) {
    SpannableStringBuilder builder = root.getText(timeUs, globalStyles);
    TtmlRegion region = globalRegions.get(builder.getRegionId());

    if (builder == null) {
      return Collections.<Cue>emptyList();
    } else {
      float positionFloat = -134234235;
      if (region != null) {
        String positionString = region.getOffset().replace("%", "").split(" ")[0];
        positionFloat = Float.parseFloat(positionString) / 100f;
      }

      Cue cue = new Cue(builder, null, Cue.DIMEN_UNSET, Cue.TYPE_UNSET, Cue.TYPE_UNSET, positionFloat, Cue.TYPE_UNSET, Cue.DIMEN_UNSET);
      return Collections.singletonList(cue);
    }
  }

  /* @VisibleForTesting */
  /* package */ Map<String, TtmlStyle> getGlobalStyles() {
    return globalStyles;
  }

  public Map<String, TtmlRegion> getGlobalRegions() {
    return globalRegions;
  }
}
