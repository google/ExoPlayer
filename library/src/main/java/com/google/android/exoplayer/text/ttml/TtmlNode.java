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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

  public final String tag;
  public final boolean isTextNode;
  public final String text;
  public final long startTimeUs;
  public final long endTimeUs;

  private List<TtmlNode> children;

  public static TtmlNode buildTextNode(String text) {
    return new TtmlNode(null, applySpacePolicy(text, true), UNDEFINED_TIME, UNDEFINED_TIME);
  }

  public static TtmlNode buildNode(String tag, long startTimeUs, long endTimeUs) {
    return new TtmlNode(tag, null, startTimeUs, endTimeUs);
  }

  private TtmlNode(String tag, String text, long startTimeUs, long endTimeUs) {
    this.tag = tag;
    this.text = text;
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
      children = new ArrayList<TtmlNode>();
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
    TreeSet<Long> eventTimeSet = new TreeSet<Long>();
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

  public String getText(long timeUs) {
    StringBuilder builder = new StringBuilder();
    getText(timeUs, builder, false);
    return applySpacePolicy(builder.toString().replaceAll("\n$", ""), false);
  }

  private void getText(long timeUs, StringBuilder builder, boolean descendsPNode) {
    if (isTextNode && descendsPNode) {
      builder.append(text);
    } else if (TAG_BR.equals(tag) && descendsPNode) {
      builder.append("\n");
    } else if (TAG_METADATA.equals(tag)) {
      // Do nothing.
    } else if (isActive(timeUs)) {
      boolean isPNode = TAG_P.equals(tag);
      int length = builder.length();
      for (int i = 0; i < getChildCount(); ++i) {
        getChild(i).getText(timeUs, builder, descendsPNode || isPNode);
      }
      if (isPNode && length != builder.length()) {
        builder.append("\n");
      }
    }
  }

  /**
   * Applies the space policy to the given string. See:
   * <a href src="http://www.w3.org/TR/ttaf1-dfxp/#content-attribute-space">The default space
   * policy</a>
   *
   * @param in A string to apply the policy.
   * @param treatLineFeedAsSpace Whether to convert line feeds to spaces.
   */
  private static String applySpacePolicy(String in, boolean treatLineFeedAsSpace) {
    // Removes carriage return followed by line feed. See: http://www.w3.org/TR/xml/#sec-line-ends
    String out = in.replaceAll("\r\n", "\n");
    // Apply suppress-at-line-break="auto" and
    // white-space-treatment="ignore-if-surrounding-linefeed"
    out = out.replaceAll(" *\n *", "\n");
    // Apply linefeed-treatment="treat-as-space"
    out = treatLineFeedAsSpace ? out.replaceAll("\n", " ") : out;
    // Apply white-space-collapse="true"
    out = out.replaceAll("[ \t\\x0B\f\r]+", " ");
    return out;
  }

}
