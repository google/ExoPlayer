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

import android.text.SpannableStringBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * A package internal representation of TTML node.
 */
/* package */ final class TtmlNode {

  public static final long UNDEFINED_TIME = -1;
  public static final String TAG_TT = "tt";
  public static final String TAG_HEAD = "head";
  public static final String TAG_BODY = "body";
  public static final String TAG_DIV = "div";
  public static final String TAG_P = "p";
  public static final String TAG_SPAN = "span";
  public static final String TAG_BR = "br";
  public static final String TAG_STYLE = "style";
  public static final String TAG_STYLING = "styling";
  public static final String TAG_LAYOUT = "layout";
  public static final String TAG_REGION = "region";
  public static final String TAG_METADATA = "metadata";
  public static final String TAG_SMPTE_IMAGE = "smpte:image";
  public static final String TAG_SMPTE_DATA = "smpte:data";
  public static final String TAG_SMPTE_INFORMATION = "smpte:information";

  public static final String ATTR_ID = "id";
  public static final String ATTR_TTS_BACKGROUND_COLOR = "backgroundColor";
  public static final String ATTR_TTS_FONT_STYLE = "fontStyle";
  public static final String ATTR_TTS_FONT_SIZE = "fontSize";
  public static final String ATTR_TTS_FONT_FAMILY = "fontFamily";
  public static final String ATTR_TTS_FONT_WEIGHT = "fontWeight";
  public static final String ATTR_TTS_COLOR = "color";
  public static final String ATTR_TTS_TEXT_DECORATION = "textDecoration";
  public static final String ATTR_TTS_TEXT_ALIGN = "textAlign";

  public static final String ATTR_REGION_ORIGIN = "origin";

  public static final String LINETHROUGH = "linethrough";
  public static final String NO_LINETHROUGH = "nolinethrough";
  public static final String UNDERLINE = "underline";
  public static final String NO_UNDERLINE = "nounderline";
  public static final String ITALIC = "italic";
  public static final String BOLD = "bold";

  public static final String LEFT = "left";
  public static final String CENTER = "center";
  public static final String RIGHT = "right";
  public static final String START = "start";
  public static final String END = "end";

  public final String regionId;
  public final String tag;
  public final String text;
  public final boolean isTextNode;
  public final long startTimeUs;
  public final long endTimeUs;
  public final TtmlStyle style;
  private String[] styleIds;

  private List<TtmlNode> children;
  private int start;
  private int end;

  public static TtmlNode buildTextNode(String text) {
    return new TtmlNode(null, TtmlRenderUtil.applyTextElementSpacePolicy(text), UNDEFINED_TIME,
        UNDEFINED_TIME, null, null, null);
  }

  public static TtmlNode buildNode(String tag, long startTimeUs, long endTimeUs,
      TtmlStyle style, String[] styleIds, String regionId) {
    return new TtmlNode(tag, null, startTimeUs, endTimeUs, style, styleIds, regionId);
  }

  private TtmlNode(String tag, String text, long startTimeUs, long endTimeUs,
                   TtmlStyle style, String[] styleIds, String regionId) {
    this.tag = tag;
    this.text = text;
    this.style = style;
    this.styleIds = styleIds;
    this.regionId = regionId;
    this.isTextNode = text != null;
    this.startTimeUs = startTimeUs;
    this.endTimeUs = endTimeUs;
  }

  public boolean isActive(long timeUs) {
    return (startTimeUs == UNDEFINED_TIME && endTimeUs == UNDEFINED_TIME)
        || (startTimeUs <= timeUs && endTimeUs == UNDEFINED_TIME)
        || (startTimeUs == UNDEFINED_TIME && timeUs < endTimeUs)
        || (startTimeUs <= timeUs && timeUs < endTimeUs);
  }

  public void addChild(TtmlNode child) {
    if (children == null) {
      children = new ArrayList<>();
    }
    children.add(child);
  }

  public TtmlNode getChild(int index) {
    if (children == null) {
      throw new IndexOutOfBoundsException();
    }
    return children.get(index);
  }

  public int getChildCount() {
    return children == null ? 0 : children.size();
  }

  public long[] getEventTimesUs() {
    TreeSet<Long> eventTimeSet = new TreeSet<>();
    getEventTimes(eventTimeSet, false);
    long[] eventTimes = new long[eventTimeSet.size()];
    Iterator<Long> eventTimeIterator = eventTimeSet.iterator();
    int i = 0;
    while (eventTimeIterator.hasNext()) {
      long eventTimeUs = eventTimeIterator.next();
      eventTimes[i++] = eventTimeUs;
    }
    return eventTimes;
  }

  private void getEventTimes(TreeSet<Long> out, boolean descendsPNode) {
    boolean isPNode = TAG_P.equals(tag);
    if (descendsPNode || isPNode) {
      if (startTimeUs != UNDEFINED_TIME) {
        out.add(startTimeUs);
      }
      if (endTimeUs != UNDEFINED_TIME) {
        out.add(endTimeUs);
      }
    }
    if (children == null) {
      return;
    }
    for (int i = 0; i < children.size(); i++) {
      children.get(i).getEventTimes(out, descendsPNode || isPNode);
    }
  }

  public String[] getStyleIds() {
    return styleIds;
  }

  public RegionTrackingFormattedTextBuilder getText(long timeUs, Map<String, TtmlStyle> globalStyles, Map<String, TtmlRegion> globalRegions) {
    RegionTrackingFormattedTextBuilder formattedTextBuilder = new RegionTrackingFormattedTextBuilder(globalRegions);
    traverseForText(timeUs, formattedTextBuilder, false);
    traverseForStyle(formattedTextBuilder, globalStyles);
    return formattedTextBuilder;
  }

  private void traverseForText(long timeUs, RegionTrackingFormattedTextBuilder builder,
                                                 boolean descendsPNode) {
    start = builder.getBuilder().length();
    end = start;
    if (isTextNode && descendsPNode) {
      builder.getBuilder().append(text);
    } else if (TAG_BR.equals(tag) && descendsPNode) {
      builder.getBuilder().append('\n');
    } else if (TAG_METADATA.equals(tag)) {
      // Do nothing.
    } else if (isActive(timeUs)) {
      boolean isPNode = TAG_P.equals(tag);
      builder.setRegionId(regionId);

      for (int i = 0; i < getChildCount(); ++i) {
        getChild(i).traverseForText(timeUs, builder, descendsPNode || isPNode);
      }
      if (isPNode) {
        TtmlRenderUtil.endParagraph(builder.getBuilder());
      }
      end = builder.getBuilder().length();
    }
  }

  private void traverseForStyle(RegionTrackingFormattedTextBuilder builder,
      Map<String, TtmlStyle> globalStyles) {
    if (start != end) {
      TtmlStyle resolvedStyle = TtmlRenderUtil.resolveStyle(style, styleIds, globalStyles);
      if (resolvedStyle != null) {
        TtmlRenderUtil.applyStylesToSpan(builder.getBuilder(), start, end, resolvedStyle);
      }
      for (int i = 0; i < getChildCount(); ++i) {
        getChild(i).traverseForStyle(builder, globalStyles);
      }
    }
  }

}

