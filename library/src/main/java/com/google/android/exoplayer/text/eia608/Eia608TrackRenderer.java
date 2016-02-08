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
package com.google.android.exoplayer.text.eia608;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.SampleSourceTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.TextRenderer;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;

import java.util.Collections;
import java.util.TreeSet;

import static android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;

/**
 * A {@link TrackRenderer} for EIA-608 closed captions in a media stream.
 */
public final class Eia608TrackRenderer extends SampleSourceTrackRenderer implements Callback {

  private static final int MSG_INVOKE_RENDERER = 0;

  private static final int CC_MODE_UNKNOWN = 0;
  private static final int CC_MODE_ROLL_UP = 1;
  private static final int CC_MODE_POP_ON = 2;
  private static final int CC_MODE_PAINT_ON = 3;

  // The default number of rows to display in roll-up captions mode.
  private static final int DEFAULT_CAPTIONS_ROW_COUNT = 4;
  // The maximum duration that captions are parsed ahead of the current position.
  private static final int MAX_SAMPLE_READAHEAD_US = 5000000;

  private final Eia608Parser eia608Parser;
  private final TextRenderer textRenderer;
  private final Handler textRendererHandler;
  private final MediaFormatHolder formatHolder;
  private final SampleHolder sampleHolder;
  private final SpannableStringBuilder captionStringBuilder;
  private final TreeSet<ClosedCaptionList> pendingCaptionLists;

  private boolean inputStreamEnded;
  private int captionMode;
  private int captionRowCount;
  private SpannableStringBuilder caption;
  private SpannableStringBuilder lastRenderedCaption;
  private ClosedCaptionCtrl repeatableControl;

  private static final String TAG = "Eia608Captions";

  // Incoming styling commands are ordered, a higher priority command clears any previous lower
  // priority commands. Similarly, output SPANs cannot partially overlap, so we need to close all
  // lower priority SPANs in case of opening a higher priority one. Having these preconditions we
  // do not need stack or more complex tracking of styles
  private static final int STYLE_PRIORITY_UNDERLINE = 1; // lowest priority
  private static final int STYLE_PRIORITY_ITALIC = 2;
  private static final int STYLE_PRIORITY_COLOR = 3;

  // defined only to give a name to the highest priority (means "every spans").
  private static final int STYLE_PRIORITY_ALL = 4;

  // Characters (text to display) and control codes (formatting instructions) are arriving
  // one-by-one, and we want to translate this incoming byte stream into a character sequence with
  // spans. We need the start and the end position to correctly set up a span, so we will use the
  // following variables to store information about previously received formatting instructions.
  // When we get to a point where we should end or change formatting, we can set up the spans
  // between the stored start position and the current position (new control code, end of line, etc)
  private int styleSwitchUnderlineStartPos;
  private int styleSwitchItalicStartPos;
  private int styleSwitchColorStartPos;
  private int styleSwitchColor;

  /**
   * @param source A source from which samples containing EIA-608 closed captions can be read.
   * @param textRenderer The text renderer.
   * @param textRendererLooper The looper associated with the thread on which textRenderer should be
   *     invoked. If the renderer makes use of standard Android UI components, then this should
   *     normally be the looper associated with the applications' main thread, which can be
   *     obtained using {@link android.app.Activity#getMainLooper()}. Null may be passed if the
   *     renderer should be invoked directly on the player's internal rendering thread.
   */
  public Eia608TrackRenderer(SampleSource source, TextRenderer textRenderer,
      Looper textRendererLooper) {
    super(source);
    this.textRenderer = Assertions.checkNotNull(textRenderer);
    textRendererHandler = textRendererLooper == null ? null : new Handler(textRendererLooper, this);
    eia608Parser = new Eia608Parser();
    formatHolder = new MediaFormatHolder();
    sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
    captionStringBuilder = new SpannableStringBuilder();
    pendingCaptionLists = new TreeSet<>();
  }

  @Override
  protected boolean handlesTrack(MediaFormat mediaFormat) {
    return eia608Parser.canParse(mediaFormat.mimeType);
  }

  @Override
  protected void onEnabled(int track, long positionUs, boolean joining)
      throws ExoPlaybackException {
    super.onEnabled(track, positionUs, joining);
  }

  @Override
  protected void onDiscontinuity(long positionUs) {
    inputStreamEnded = false;
    repeatableControl = null;
    pendingCaptionLists.clear();
    clearPendingSample();
    captionRowCount = DEFAULT_CAPTIONS_ROW_COUNT;
    setCaptionMode(CC_MODE_UNKNOWN);
    invokeRenderer(null);
  }

  @Override
  protected void doSomeWork(long positionUs, long elapsedRealtimeUs, boolean sourceIsReady)
      throws ExoPlaybackException {
    if (isSamplePending()) {
      maybeParsePendingSample(positionUs);
    }

    int result = inputStreamEnded ? SampleSource.END_OF_STREAM : SampleSource.SAMPLE_READ;
    while (!isSamplePending() && result == SampleSource.SAMPLE_READ) {
      result = readSource(positionUs, formatHolder, sampleHolder);
      if (result == SampleSource.SAMPLE_READ) {
        maybeParsePendingSample(positionUs);
      } else if (result == SampleSource.END_OF_STREAM) {
        inputStreamEnded = true;
      }
    }

    while (!pendingCaptionLists.isEmpty()) {
      if (pendingCaptionLists.first().timeUs > positionUs) {
        // We're too early to render any of the pending caption lists.
        return;
      }
      // Remove and consume the next caption list.
      ClosedCaptionList nextCaptionList = pendingCaptionLists.pollFirst();
      consumeCaptionList(nextCaptionList);
      // Update the renderer, unless the caption list was marked for decoding only.
      if (!nextCaptionList.decodeOnly) {
        invokeRenderer(caption);
      }
    }
  }

  @Override
  protected long getBufferedPositionUs() {
    return TrackRenderer.END_OF_TRACK_US;
  }

  @Override
  protected boolean isEnded() {
    return inputStreamEnded;
  }

  @Override
  protected boolean isReady() {
    return true;
  }

  private void invokeRenderer(SpannableStringBuilder text) {
    if (Util.areEqual(lastRenderedCaption, text)) {
      // No change.
      return;
    }
    final SpannableStringBuilder textToRender = text == null ? null : new SpannableStringBuilder(text);
    this.lastRenderedCaption = textToRender;
    if (textRendererHandler != null) {
      textRendererHandler.obtainMessage(MSG_INVOKE_RENDERER, textToRender).sendToTarget();
    } else {
      invokeRendererInternal(textToRender);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_INVOKE_RENDERER:
        invokeRendererInternal((SpannableStringBuilder) msg.obj);
        return true;
    }
    return false;
  }

  private void invokeRendererInternal(CharSequence cueText) {
    if (cueText == null) {
      textRenderer.onCues(Collections.<Cue>emptyList());
    } else {
      textRenderer.onCues(Collections.singletonList(new Cue(cueText)));
    }
  }

  private void maybeParsePendingSample(long positionUs) {
    if (sampleHolder.timeUs > positionUs + MAX_SAMPLE_READAHEAD_US) {
      // We're too early to parse the sample.
      return;
    }
    ClosedCaptionList holder = eia608Parser.parse(sampleHolder);
    clearPendingSample();
    if (holder != null) {
      pendingCaptionLists.add(holder);
    }
  }

  private void consumeCaptionList(ClosedCaptionList captionList) {
    int captionBufferSize = captionList.captions.length;
    if (captionBufferSize == 0) {
      return;
    }

    boolean isRepeatableControl = false;
    for (int i = 0; i < captionBufferSize; i++) {
      ClosedCaption caption = captionList.captions[i];
      if (caption.type == ClosedCaption.TYPE_CTRL) {
        ClosedCaptionCtrl captionCtrl = (ClosedCaptionCtrl) caption;
        isRepeatableControl = captionBufferSize == 1 && captionCtrl.isRepeatable();
        if (isRepeatableControl && repeatableControl != null
            && repeatableControl.cc1 == captionCtrl.cc1
            && repeatableControl.cc2 == captionCtrl.cc2) {
          repeatableControl = null;
          continue;
        } else if (isRepeatableControl) {
          repeatableControl = captionCtrl;
        }
        if (captionCtrl.isMiscCode()) {
          handleMiscCode(captionCtrl);
        } else if (captionCtrl.isPreambleAddressCode()) {
          handlePreambleAddressCode(captionCtrl);
        } else if (captionCtrl.isMidRowCode()) {
          handleMidRowCode(captionCtrl);
        }
      } else {
        handleText((ClosedCaptionText) caption);
      }
    }

    if (!isRepeatableControl) {
      repeatableControl = null;
    }
    if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_PAINT_ON) {
      caption = getDisplayCaption(false);
    }
  }

  private void handleText(ClosedCaptionText captionText) {
    if (captionMode != CC_MODE_UNKNOWN) {
      captionStringBuilder.append(captionText.text);
    }
  }

  private void handleMidRowCode(ClosedCaptionCtrl captionCtrl) {
    //Log.i(TAG, "Mid line Command code: " + captionCtrl.getMidRowCodeMeaning());

    //TODO: possibly add space character as:
    //"All Mid-Row Codes and the Flash On command are spacing attributes which appear in the display
    // just as if a standard space (20h) had been received"
    // https://www.law.cornell.edu/cfr/text/47/79.101

    int newColor = captionCtrl.getMidRowColorValue();
    int captionLength = captionStringBuilder.length();

    // The last color described in the standard is "transparent" meaning "Keep Previous Color" and
    // is used to turn on Italics with the current color unchanged. So for example a command to
    // switch to RED then TRANSPARENT would mean RED ITALIC. The first color command (RED) disables
    // all previous color settings while the second (TRANSPARENT) does not.
    if (newColor == Color.TRANSPARENT) {
      closeOpenSpans(STYLE_PRIORITY_ITALIC, captionStringBuilder, true); // close already open spans
      styleSwitchItalicStartPos = captionLength;
    } else {
      // Not transparent: apply new color
      closeOpenSpans(STYLE_PRIORITY_COLOR, captionStringBuilder, true); // close already open spans
      styleSwitchColor = newColor;
      styleSwitchColorStartPos = captionLength;
    }

    // the last bit is the "Underline switch", it does not interfere with any other settings
    if (captionCtrl.isUnderline()) {
      closeOpenSpans(STYLE_PRIORITY_UNDERLINE, captionStringBuilder, true); // close already open spans
      styleSwitchUnderlineStartPos = captionLength;
    }

    // Any color or italics Mid-Row Code should turn off flashing, but let's declare that deprecated
  }

  private void handleMiscCode(ClosedCaptionCtrl captionCtrl) {

    // TODO: when switching to "ROLL UP" mode, the default ROW value is 15, and
    // The Roll-Up command, in normal practice, will be followed (not necessarily immediately) by a
    // Preamble Address Code indicating the base row and the horizontal indent position. If no
    // Preamble Address Code is received, the base row will default to Row 15 or, if a roll-up
    // caption is currently displayed, to the same base row last received, and the cursor will be
    // placed at Column 1. See 47 CFR Ch1 (10-1-13) 1 / ii

    switch (captionCtrl.cc2) {
      case ClosedCaptionCtrl.ROLL_UP_CAPTIONS_2_ROWS:
        captionRowCount = 2;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case ClosedCaptionCtrl.ROLL_UP_CAPTIONS_3_ROWS:
        captionRowCount = 3;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case ClosedCaptionCtrl.ROLL_UP_CAPTIONS_4_ROWS:
        captionRowCount = 4;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case ClosedCaptionCtrl.RESUME_CAPTION_LOADING:
        setCaptionMode(CC_MODE_POP_ON);
        return;
      case ClosedCaptionCtrl.RESUME_DIRECT_CAPTIONING:
        setCaptionMode(CC_MODE_PAINT_ON);
        return;
    }

    if (captionMode == CC_MODE_UNKNOWN) {
      return;
    }

    switch (captionCtrl.cc2) {
      case ClosedCaptionCtrl.ERASE_DISPLAYED_MEMORY:
        caption = null;
        if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_PAINT_ON) {
          captionStringBuilder.clear();
        }
        return;
      case ClosedCaptionCtrl.ERASE_NON_DISPLAYED_MEMORY:
        captionStringBuilder.clear();
        return;
      case ClosedCaptionCtrl.END_OF_CAPTION:
        caption = getDisplayCaption(true);
        captionStringBuilder.clear();
        return;
      case ClosedCaptionCtrl.CARRIAGE_RETURN:
        maybeAppendNewline();
        return;
      case ClosedCaptionCtrl.BACKSPACE:
        int length = captionStringBuilder.length();
        if (length > 0) {
          captionStringBuilder.delete(length - 1, length);
        }
        return;
    }
  }


  /**
   * Spans cannot partially overlap each other, so the ordering (priority) is important
   * This function sets up every spans that are marked as currently open (has start position set)
   * with equal or lower priority than the incoming parameter.
   * @param spanPriority limit of priority of the spans to close
   * @param stringBuilder the new spans should be inserted into this stringBuilder
   * @param clearSpanOpenings true if the spans currently closed should be removed
   */
  private void closeOpenSpans(int spanPriority, SpannableStringBuilder stringBuilder,
                              boolean clearSpanOpenings) {

    // TODO: should check if closing before rather than after a new line character makes any
    // difference. We might want to exclude the new line characters from the spans

    int textLength = stringBuilder.length();

    if (styleSwitchUnderlineStartPos != -1) {
      if (styleSwitchUnderlineStartPos < textLength) {
        stringBuilder.setSpan(new UnderlineSpan(), styleSwitchUnderlineStartPos,
                textLength, SPAN_EXCLUSIVE_EXCLUSIVE);
      }

      if (clearSpanOpenings) {
        styleSwitchUnderlineStartPos = -1;
      }
    }

    if (spanPriority < STYLE_PRIORITY_ITALIC)
      return;

    if (styleSwitchItalicStartPos != -1) {
      if (styleSwitchItalicStartPos < textLength) {
        stringBuilder.setSpan(new StyleSpan(Typeface.ITALIC),
                styleSwitchItalicStartPos, textLength, SPAN_EXCLUSIVE_EXCLUSIVE);
      }

      if (clearSpanOpenings) {
        styleSwitchItalicStartPos = -1;
      }
    }

    if (spanPriority < STYLE_PRIORITY_COLOR)
      return;

    if (styleSwitchColorStartPos != -1) {
      if (styleSwitchColorStartPos < textLength) {
        stringBuilder.setSpan(new ForegroundColorSpan(styleSwitchColor),
                styleSwitchColorStartPos, textLength, SPAN_EXCLUSIVE_EXCLUSIVE);
      }

      if (clearSpanOpenings) {
        styleSwitchColorStartPos = -1;
      }
    }
  }

  private void handlePreambleAddressCode(ClosedCaptionCtrl captionCtrl) {
    // PAC might be skipped, but it should be the beginning of every line. So we expect a line break
    // before every PAC.
    maybeAppendNewline();

    //Log.d(TAG , captionCtrl.getPreambleAddressCodeMeaning());

    // TODO: Add better handling of this with specific positioning.
    // TODO: Read CC1 for vertical positioning information

    int currentLength = captionStringBuilder.length();

    int preambleColor = captionCtrl.getPreambleColor();
    if (preambleColor != Color.WHITE) {
      styleSwitchColor = preambleColor;
      styleSwitchColorStartPos = currentLength;
    }

    if (captionCtrl.isPreambleItalic()) {
      styleSwitchItalicStartPos = currentLength;
    }

    if (captionCtrl.isUnderline()) {
      styleSwitchUnderlineStartPos = currentLength;
    }
  }

  private void setCaptionMode(int captionMode) {
    if (this.captionMode == captionMode) {
      return;
    }

    this.captionMode = captionMode;
    // Clear the working memory.
    captionStringBuilder.clear();
    if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_UNKNOWN) {
      // When switching to roll-up or unknown, we also need to clear the caption.
      caption = null;
    }
  }

  private void maybeAppendNewline() {
    closeOpenSpans(STYLE_PRIORITY_ALL, captionStringBuilder, true); //end of line should close all tags

    int buildLength = captionStringBuilder.length();
    if (buildLength > 0 && captionStringBuilder.charAt(buildLength - 1) != '\n') {
      captionStringBuilder.append('\n');
    }
  }

  private SpannableStringBuilder getDisplayCaption(boolean clearSpans) {
    int buildLength = captionStringBuilder.length();
    if (buildLength == 0) {
      return null;
    }

    boolean endsWithNewline = captionStringBuilder.charAt(buildLength - 1) == '\n';
    if (buildLength == 1 && endsWithNewline) {
      return null;
    }

    int endIndex = endsWithNewline ? buildLength - 1 : buildLength;
    if (captionMode != CC_MODE_ROLL_UP) {
      SpannableStringBuilder result = new SpannableStringBuilder(captionStringBuilder, 0, endIndex);
      closeOpenSpans(STYLE_PRIORITY_ALL, result, clearSpans);
      return result;
    }

    // Show only the last X rows by searching backwards the last X line breaks in the builder and
    // returning only what is after them.

    int startIndex = 0;
    int newLineCount = 0;
    for (int charIdx = endIndex - 1; charIdx >= 0; --charIdx) {
      if (captionStringBuilder.charAt(charIdx) == '\n') {
        newLineCount++;
      }

      if (newLineCount >= captionRowCount) {
        startIndex = charIdx + 1;  // +1 to skip the newline char
        break;
      }
    }

    captionStringBuilder.delete(0, startIndex);
    SpannableStringBuilder result = new SpannableStringBuilder(captionStringBuilder);
    closeOpenSpans(STYLE_PRIORITY_ALL, result, clearSpans);
    return result;
  }

  private void clearPendingSample() {
    sampleHolder.timeUs = C.UNKNOWN_TIME_US;
    sampleHolder.clearData();
  }

  private boolean isSamplePending() {
    return sampleHolder.timeUs != C.UNKNOWN_TIME_US;
  }
}
