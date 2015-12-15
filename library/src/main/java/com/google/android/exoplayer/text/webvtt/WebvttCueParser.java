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
package com.google.android.exoplayer.text.webvtt;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;

import java.util.Stack;

/**
 * Parser for webvtt cue text. (https://w3c.github.io/webvtt/#cue-text)
 */
/* package */ final class WebvttCueParser {

  private static final char CHAR_LESS_THAN = '<';
  private static final char CHAR_GREATER_THAN = '>';
  private static final char CHAR_SLASH = '/';
  private static final char CHAR_AMPERSAND = '&';
  private static final char CHAR_SEMI_COLON = ';';
  private static final char CHAR_SPACE = ' ';
  private static final String SPACE = " ";

  private static final String ENTITY_LESS_THAN = "lt";
  private static final String ENTITY_GREATER_THAN = "gt";
  private static final String ENTITY_AMPERSAND = "amp";
  private static final String ENTITY_NON_BREAK_SPACE = "nbsp";

  private static final String TAG_BOLD = "b";
  private static final String TAG_ITALIC = "i";
  private static final String TAG_UNDERLINE = "u";
  private static final String TAG_CLASS = "c";
  private static final String TAG_VOICE = "v";
  private static final String TAG_LANG = "lang";

  private static final int STYLE_BOLD = Typeface.BOLD;
  private static final int STYLE_ITALIC = Typeface.ITALIC;

  private static final String TAG = "WebvttCueParser";

  public Spanned parse(String markup) {
    SpannableStringBuilder spannedText = new SpannableStringBuilder();
    Stack<StartTag> startTagStack = new Stack<>();
    String[] tagTokens;
    int pos = 0;
    while (pos < markup.length()) {
      char curr = markup.charAt(pos);
      switch (curr) {
        case CHAR_LESS_THAN:
          if (pos + 1 >= markup.length()) {
            pos++;
            break; // avoid ArrayOutOfBoundsException
          }
          int ltPos = pos;
          boolean isClosingTag = markup.charAt(ltPos + 1) == CHAR_SLASH;
          pos = findEndOfTag(markup, ltPos + 1);
          boolean isVoidTag = markup.charAt(pos - 2) == CHAR_SLASH;

          tagTokens = tokenizeTag(markup.substring(
              ltPos + (isClosingTag ? 2 : 1), isVoidTag ? pos - 2 : pos - 1));
          if (tagTokens == null || !isSupportedTag(tagTokens[0])) {
            continue;
          }
          if (isClosingTag) {
            StartTag startTag;
            do {
              if (startTagStack.isEmpty()) {
                break;
              }
              startTag = startTagStack.pop();
              applySpansForTag(startTag, spannedText);
            } while(!startTag.name.equals(tagTokens[0]));
          } else if (!isVoidTag) {
            startTagStack.push(new StartTag(tagTokens[0], spannedText.length()));
          }
          break;
        case CHAR_AMPERSAND:
          int semiColonEnd = markup.indexOf(CHAR_SEMI_COLON, pos + 1);
          int spaceEnd = markup.indexOf(CHAR_SPACE, pos + 1);
          int entityEnd = semiColonEnd == -1 ? spaceEnd
              : spaceEnd == -1 ? semiColonEnd : Math.min(semiColonEnd, spaceEnd);
          if (entityEnd != -1) {
            applyEntity(markup.substring(pos + 1, entityEnd), spannedText);
            if (entityEnd == spaceEnd) {
              spannedText.append(" ");
            }
            pos = entityEnd + 1;
          } else {
            spannedText.append(curr);
            pos++;
          }
          break;
        default:
          spannedText.append(curr);
          pos++;
          break;
      }
    }
    // apply unclosed tags
    while (!startTagStack.isEmpty()) {
      applySpansForTag(startTagStack.pop(), spannedText);
    }
    return spannedText;
  }

  /**
   * Find end of tag (&gt;). The position returned is the position of the &gt; plus one (exclusive).
   *
   * @param markup The webvtt cue markup to be parsed.
   * @param startPos the position from where to start searching for the end of tag.
   * @return the position of the end of tag plus 1 (one).
   */
  private int findEndOfTag(String markup, int startPos) {
    int idx = markup.indexOf(CHAR_GREATER_THAN, startPos);
    return idx == -1 ? markup.length() : idx + 1;
  }

  private void applyEntity(String entity, SpannableStringBuilder spannedText) {
    switch (entity) {
      case ENTITY_LESS_THAN:
        spannedText.append('<');
        break;
      case ENTITY_GREATER_THAN:
        spannedText.append('>');
        break;
      case ENTITY_NON_BREAK_SPACE:
        spannedText.append(' ');
        break;
      case ENTITY_AMPERSAND:
        spannedText.append('&');
        break;
      default:
        Log.w(TAG, "ignoring unsupported entity: '&" + entity + ";'");
        break;
    }
  }

  private boolean isSupportedTag(String tagName) {
    switch (tagName) {
      case TAG_BOLD:
      case TAG_CLASS:
      case TAG_ITALIC:
      case TAG_LANG:
      case TAG_UNDERLINE:
      case TAG_VOICE:
        return true;
      default:
        return false;
    }
  }

  private void applySpansForTag(StartTag startTag, SpannableStringBuilder spannedText) {
    switch(startTag.name) {
      case TAG_BOLD:
        spannedText.setSpan(new StyleSpan(STYLE_BOLD), startTag.position,
            spannedText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return;
      case TAG_ITALIC:
        spannedText.setSpan(new StyleSpan(STYLE_ITALIC), startTag.position,
            spannedText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return;
      case TAG_UNDERLINE:
        spannedText.setSpan(new UnderlineSpan(), startTag.position,
            spannedText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return;
      default:
        break;
    }
  }

  /**
   * Tokenizes a tag expression into tag name (pos 0) and classes (pos 1..n).
   *
   * @param fullTagExpression characters between &amp;lt: and &amp;gt; of a start or end tag
   * @return an array of <code>String</code>s with the tag name at pos 0 followed by style classes
   *    or null if it's an empty tag: '&lt;&gt;'
   */
  private String[] tokenizeTag(String fullTagExpression) {
    fullTagExpression = fullTagExpression.replace("\\s+", " ").trim();
    if (fullTagExpression.length() == 0) {
      return null;
    }
    if (fullTagExpression.contains(SPACE)) {
      fullTagExpression = fullTagExpression.substring(0, fullTagExpression.indexOf(SPACE));
    }
    return fullTagExpression.split("\\.");
  }

  private static final class StartTag {

    public final String name;
    public final int position;

    public StartTag(String name, int position) {
      this.position = position;
      this.name = name;
    }

  }

}
