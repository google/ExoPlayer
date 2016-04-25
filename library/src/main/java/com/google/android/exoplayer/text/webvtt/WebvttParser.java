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

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.text.SimpleSubtitleParser;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple WebVTT parser.
 * <p>
 * @see <a href="http://dev.w3.org/html5/webvtt">WebVTT specification</a>
 */
public final class WebvttParser extends SimpleSubtitleParser {

  private static final int NO_EVENT_FOUND = -1;
  private static final int END_OF_FILE_FOUND = 0;
  private static final int COMMENT_FOUND = 1;
  private static final int STYLE_BLOCK_FOUND = 2;
  private static final int CUE_FOUND = 3;
  private static final String COMMENT_START = "NOTE";
  private static final String STYLE_START = "STYLE";

  private final WebvttCueParser cueParser;
  private final ParsableByteArray parsableWebvttData;
  private final WebvttCue.Builder webvttCueBuilder;
  private final CssParser cssParser;
  private final List<WebvttCssStyle> definedStyles;

  public WebvttParser() {
    cueParser = new WebvttCueParser();
    parsableWebvttData = new ParsableByteArray();
    webvttCueBuilder = new WebvttCue.Builder();
    cssParser = new CssParser();
    definedStyles = new ArrayList<>();
  }

  @Override
  protected WebvttSubtitle decode(byte[] bytes, int length) throws ParserException {
    parsableWebvttData.reset(bytes, length);
    // Initialization for consistent starting state.
    webvttCueBuilder.reset();
    definedStyles.clear();

    // Validate the first line of the header, and skip the remainder.
    WebvttParserUtil.validateWebvttHeaderLine(parsableWebvttData);
    while (!TextUtils.isEmpty(parsableWebvttData.readLine())) {}

    int eventFound;
    ArrayList<WebvttCue> subtitles = new ArrayList<>();
    while ((eventFound = getNextEvent(parsableWebvttData)) != END_OF_FILE_FOUND) {
      if (eventFound == COMMENT_FOUND) {
        skipComment(parsableWebvttData);
      } else if (eventFound == STYLE_BLOCK_FOUND) {
        if (!subtitles.isEmpty()) {
          throw new ParserException("A style block was found after the first cue.");
        }
        parsableWebvttData.readLine(); // Consume the "STYLE" header.
        WebvttCssStyle styleBlock = cssParser.parseBlock(parsableWebvttData);
        if (styleBlock != null) {
          definedStyles.add(styleBlock);
        }
      } else if (eventFound == CUE_FOUND) {
        if (cueParser.parseCue(parsableWebvttData, webvttCueBuilder, definedStyles)) {
          subtitles.add(webvttCueBuilder.build());
          webvttCueBuilder.reset();
        }
      }
    }
    return new WebvttSubtitle(subtitles);
  }

  /**
   * Positions the input right before the next event, and returns the kind of event found. Does not
   * consume any data from such event, if any.
   *
   * @return The kind of event found.
   */
  private static int getNextEvent(ParsableByteArray parsableWebvttData) {
    int foundEvent = NO_EVENT_FOUND;
    int currentInputPosition = 0;
    while (foundEvent == NO_EVENT_FOUND) {
      currentInputPosition = parsableWebvttData.getPosition();
      String line = parsableWebvttData.readLine();
      if (line == null) {
        foundEvent = END_OF_FILE_FOUND;
      } else if (STYLE_START.equals(line)) {
        foundEvent = STYLE_BLOCK_FOUND;
      } else if (COMMENT_START.startsWith(line)) {
        foundEvent = COMMENT_FOUND;
      } else {
        foundEvent = CUE_FOUND;
      }
    }
    parsableWebvttData.setPosition(currentInputPosition);
    return foundEvent;
  }

  private static void skipComment(ParsableByteArray parsableWebvttData) {
    while (!TextUtils.isEmpty(parsableWebvttData.readLine())) {}
  }

}
