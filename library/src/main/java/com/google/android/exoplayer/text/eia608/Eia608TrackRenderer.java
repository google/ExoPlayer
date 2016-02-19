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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
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

  // The default number of rows to display in ROLL-UP captions mode.
  private static final int DEFAULT_CAPTIONS_ROW_COUNT = 4;
  // The maximum duration that captions are parsed ahead of the current position.
  private static final int MAX_SAMPLE_READAHEAD_US = 5000000;

  private final Eia608Parser eia608Parser;
  private final TextRenderer textRenderer;
  private final Handler textRendererHandler;
  private final MediaFormatHolder formatHolder;
  private final SampleHolder sampleHolder;
  private final SpannableStringBuilder incomingStringBuilder;
  private final TreeSet<ClosedCaptionList> pendingCaptionLists;
  private List<Cue> cueList;

  private boolean inputStreamEnded;
  private int captionMode;
  private int captionRowCount;
  private ClosedCaptionCtrl repeatableControl;

  private final boolean DEBUG = false;

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
  private int styleSwitchUnderlineStartPos = -1;
  private int styleSwitchItalicStartPos = -1;
  private int styleSwitchColorStartPos = -1;
  private int styleSwitchColor;

  // The safe area of the screen is divided into 15 rows, 32 columns and each column has 4 Tabs
  // Note: Row and Column indices are 1 based!
  private int positionBaseRow;
  private int positionBaseColumn;
  private int positionTabCount;

  private final Eia608CueBuilder cueBuilder = new Eia608CueBuilder();
  private final List<Eia608CueBuilder> rollingQues = new LinkedList<>();

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
    incomingStringBuilder = new SpannableStringBuilder();
    pendingCaptionLists = new TreeSet<>();
    cueList = new ArrayList<>();
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
    clearCaptionsBuffer();
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
      consumeCaptionList(pendingCaptionLists.pollFirst());
    }
  }

  private void sendCaptionToRenderer(boolean decodeOnly) {
    if (DEBUG) {
      printCurrentCueList();
    }

    if (isModeRollUp()) {
      for (Eia608CueBuilder oneBuilder : rollingQues) {
        // empty rows are rolling to handle line count limit correctly, but should not be sent to
        // the renderer
        if (!oneBuilder.isEmpty()) {
          cueList.add(oneBuilder.build());
        }
      }
    } else {
      rollingQues.clear();
    }

    if (!decodeOnly) {
      invokeRenderer(cueList);
    }
    cueList = new ArrayList<>();
  }

  // for debug use only
  private void printCurrentCueList() {
    Log.d(TAG, "+ + + + + + + + + + + + +");
    for (Cue oneCue :cueList) {
      Log.d(TAG, "Cue @" + oneCue.line + " " + oneCue.text);
    }
    Log.d(TAG, "-------------------------");
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

  /**
   * Update captions on the screen
   * @param cues (list) to render. Call with null parameter to clear screen
     */
  private void invokeRenderer(List<Cue> cues) {
    if (textRendererHandler != null) {
      textRendererHandler.obtainMessage(MSG_INVOKE_RENDERER, cues).sendToTarget();
    } else {
      invokeRendererInternal(cues);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_INVOKE_RENDERER:
        invokeRendererInternal((List<Cue>) msg.obj);
        return true;
    }
    return false;
  }

  private void invokeRendererInternal(List<Cue> cues) {
    if (cues == null) {
      textRenderer.onCues(Collections.<Cue>emptyList());
    } else {
      textRenderer.onCues(cues);
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

  // for debug use only
  private void printIncomingCaptionData(ClosedCaptionList captionList) {
    Log.i(TAG, "****************************");
    for (ClosedCaption oneCaption : captionList.captions) {
      Log.i(TAG, oneCaption.toString());
    }
    Log.i(TAG, "-*-*-*-*-*-*-*-*-*-*-*-*-*-*-");
  }

  private void consumeCaptionList(ClosedCaptionList captionList) {
    int captionBufferSize = captionList.captions.length;
    if (captionBufferSize == 0) {
      return;
    }

    if (DEBUG) {
      printIncomingCaptionData(captionList);
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
          handleMiscCode(captionCtrl, captionList.decodeOnly);
        } else if (captionCtrl.isPreambleAddressCode()) {
          // In ROLL-UP mode, an incoming PAC just moves the entire window (with captions intact)
          // to the now place, but does not clear the previous captions. When there are no empty
          // rows left, than the next Carriage Return will remove the top row from the memory, so
          // clearing is implemented there.
          createCueFromCurrentBuffers(!isModeRollUp());
          handlePreambleAddressCode(captionCtrl);
        } else if (captionCtrl.isMidRowCode()) {
          handleMidRowCode(captionCtrl);
        } else if (captionCtrl.isTabOffsetCode()) {
          handleTabOffsetCode(captionCtrl);
        }
      } else {
        handleText((ClosedCaptionText) caption);
      }
    }
    if (!isRepeatableControl) {
      repeatableControl = null;
    }

    // Pop-up mode needs to wait for the command END-OF-CAPTION to update the screen, but other
    // modes show incoming characters immediately.
    if (isModePaintOn() || isModeRollUp()) {
      // We need to keep the old buffers intact, as new characters will be concatenated to the
      // current content.
      final boolean clearOldBuffers = false;
      createCueFromCurrentBuffers(clearOldBuffers);

      sendCaptionToRenderer(captionList.decodeOnly);
    }
  }

  private void handleTabOffsetCode(ClosedCaptionCtrl captionCtrl) {
    // CC1: 0x17 and 0x1F are the 2 channels.
    // CC2: 0x21 means 1, 0x22 means 2 tab, 0x23 means 3 tabs.
    positionTabCount = captionCtrl.cc2 - 0x20;

    if (DEBUG) {
      Log.i(TAG, "TAB: " + positionTabCount);
    }
  }

  private void handleText(ClosedCaptionText captionText) {
    if (DEBUG) {
      Log.i(TAG, "TEXT: " + captionText.text);
    }

    if (!isModeUnknown()) {
      incomingStringBuilder.append(captionText.text);
    }
  }

  /**
   * This function should be called only to handle the Carriage Return Misc Code command in ROLL-UP
   * Mode. When called, the current and previous (up to the number of rolling lines) rows are rolled
   * up by one line. The "base line" (the one showing new incoming characters) will be empty.
   */
  private void rollUpQues() {
    // add latest line with currently incoming characters to the rolling list first
    setupCueBuilder(true);

    // Note: there can be multiple CARRIAGE-RETURN commands coming in without character in between
    // we need to roll up for each, so we put empty lines into the list to correctly handle the
    // line count limit.
    rollingQues.add(new Eia608CueBuilder(cueBuilder));

    // base row is rolled up, incoming characters will be added to a blank line:
    incomingStringBuilder.clear();

    dropOldRollingQues();

    for (Eia608CueBuilder oneBuilder : rollingQues) {
      oneBuilder.rollUp();
    }
  }

  // call this when the rolling cue list changes (resize or roll) to remove old cues
  private void dropOldRollingQues() {
    // we keep one less row than the allowed one, as the base row is getting the incoming characters
    while (rollingQues.size() >= captionRowCount) {
      rollingQues.remove(0);
    }
  }

  private void handleMidRowCode(ClosedCaptionCtrl captionCtrl) {
    if (DEBUG) {
      Log.i(TAG, "MRC: " + captionCtrl.getMidRowCodeMeaning());
    }

    // TODO: possibly add space character as:
    //"All Mid-Row Codes and the Flash On command are spacing attributes which appear in the display
    // just as if a standard space (20h) had been received"
    // https://www.law.cornell.edu/cfr/text/47/79.101
    // This is currently skipped, as all content I met added spaces correctly

    int newColor = captionCtrl.getMidRowColorValue();
    int captionLength = incomingStringBuilder.length();

    // The last color described in the standard is "transparent" meaning "Keep Previous Color" and
    // is used to turn on Italics with the current color unchanged. So for example a command to
    // switch to RED then TRANSPARENT would mean RED ITALIC. The first color command (RED) disables
    // all previous color settings while the second (TRANSPARENT) does not.
    if (newColor == Color.TRANSPARENT) {
      closeOpenSpans(STYLE_PRIORITY_ITALIC, true); // close already open spans
      styleSwitchItalicStartPos = captionLength;
    } else {
      // Not transparent: apply new color
      closeOpenSpans(STYLE_PRIORITY_COLOR, true); // close already open spans
      styleSwitchColor = newColor;
      styleSwitchColorStartPos = captionLength;
    }

    // the last bit is the "Underline switch", it does not interfere with any other settings
    if (captionCtrl.isUnderline()) {
      closeOpenSpans(STYLE_PRIORITY_UNDERLINE, true); // close already open spans
      styleSwitchUnderlineStartPos = captionLength;
    }
  }

  private void handleMiscCode(ClosedCaptionCtrl captionCtrl, boolean decodeOnly) {
    if (DEBUG) {
      Log.i(TAG, "MISC: " + captionCtrl.getMiscControlCodeMeaning());
    }

    // the first ones are the commands setting the captioning mode. Some other commands also imply
    // one of the modes already set, but mode setting might have been dropped (parity check) or
    // just skipped by the provider. So we might update the mode for those (like END-OF-CAPTION is
    // only expected in POP-ON mode), but their original purpose is not mode change, they will be
    // handled later
    switch (captionCtrl.cc2) {
      case ClosedCaptionCtrl.ROLL_UP_CAPTIONS_2_ROWS:
        captionRowCount = 2;
        setCaptionMode(CC_MODE_ROLL_UP);
        dropOldRollingQues(); // if row count is decreased we need to adjust
        return;
      case ClosedCaptionCtrl.ROLL_UP_CAPTIONS_3_ROWS:
        captionRowCount = 3;
        setCaptionMode(CC_MODE_ROLL_UP);
        dropOldRollingQues(); // if row count is decreased we need to adjust
        return;
      case ClosedCaptionCtrl.ROLL_UP_CAPTIONS_4_ROWS:
        captionRowCount = 4; // if row count could not be more, no need to adjust the rolling list
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case ClosedCaptionCtrl.RESUME_CAPTION_LOADING:
        setCaptionMode(CC_MODE_POP_ON);
        return;
      case ClosedCaptionCtrl.RESUME_DIRECT_CAPTIONING:
        setCaptionMode(CC_MODE_PAINT_ON);
        return;
    }

    if (isModeUnknown()) {
      // mode is not initialized yet, but the next codes would depend on the current mode. So we
      // skip everything till the next command setting the mode correctly
      return;
    }

    switch (captionCtrl.cc2) {
      case ClosedCaptionCtrl.ERASE_DISPLAYED_MEMORY:
        invokeRenderer(null); // clear screen immediately.

        // When POP UP mode is used, we need to handle the screen and the off-screen buffer
        // separately. In ROLL-UP mode and PAINT-ON mode the screen is immediately updated from the
        // buffer. So, for those modes, we clear all buffers to avoid redrawing old stuff.
        if (isModeRollUp() || isModePaintOn()) {
          incomingStringBuilder.clear();
        }
        rollingQues.clear();
        return;
      case ClosedCaptionCtrl.ERASE_NON_DISPLAYED_MEMORY: // clear everything not displayed
        clearCaptionsBuffer();
        return;
      case ClosedCaptionCtrl.END_OF_CAPTION:
        // in case the command RESUME_CAPTION_LOADING was skipped, this command needs to change
        // captioning mode as well!
        setCaptionMode(CC_MODE_POP_ON);
        createCueFromCurrentBuffers(true);
        sendCaptionToRenderer(decodeOnly);
        return;
      case ClosedCaptionCtrl.CARRIAGE_RETURN:
        // CARRIAGE_RETURN should not affect POP-UP and PAINT-ON captions, only ROLL-UP ones as
        // during the analog era there were no "line break" characters sent, but the next captions
        // were positioned to the row below the current one. So this current implementation does
        // not really expect "new line" characters for formatting
        if (isModeRollUp()) {
          rollUpQues();
        }
        return;
      case ClosedCaptionCtrl.BACKSPACE:
        int length = incomingStringBuilder.length();
        if (length > 0) {
          incomingStringBuilder.delete(length - 1, length);
        }
        return;
    }
  }

  /**
   * For some operations we handle the screen (already shown memory) and incoming buffer
   * separately. This function can be used to clear every internal buffers, but the currently shown
   * captions.
   */
  private void clearCaptionsBuffer() {
    incomingStringBuilder.clear();
    cueList.clear();
    rollingQues.clear();
  }

  /**
   * Put the received characters with the received formatting information into a cue. This should
   * be called just before rendering a cue. Might be mid-line, if we do not need to wait for a
   * command to show the captions, but incoming characters should be shown immediately.
   * @param clearStringBuilder clear previous formatting and text
   */
  private void createCueFromCurrentBuffers(boolean clearStringBuilder) {
    setupCueBuilder(clearStringBuilder);

    if (cueBuilder.isEmpty()) {
      return;
    }

    cueList.add(cueBuilder.build());

    // Not all modes need to clear the buffers when the captions got sent to the display.
    // For ROLL-UP and PAINT-ON mode the old content is kept in the buffer as new characters are
    // concatenated and sent to display immediately
    if (clearStringBuilder) {
      incomingStringBuilder.clear();
    }
  }

  /**
   * Spans cannot partially overlap each other, so the ordering (priority) is important
   * This function sets up every spans that are marked as currently open (has start position set)
   * with equal or lower priority than the incoming parameter. We only need to clear span opening
   * data if the content currently processed is final, and the next incoming characters wont be
   * appended to the text any more.
   * @param spanPriority limit of priority of the spans to close
   * @param clearSpanOpenings true if the spans currently closed should be removed from next cues
   */
  private void closeOpenSpans(int spanPriority, boolean clearSpanOpenings) {
    int textLength = incomingStringBuilder.length();

    if (styleSwitchUnderlineStartPos != -1) {
      if (styleSwitchUnderlineStartPos < textLength) {
        incomingStringBuilder.setSpan(new UnderlineSpan(), styleSwitchUnderlineStartPos,
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
        incomingStringBuilder.setSpan(new StyleSpan(Typeface.ITALIC),
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
        incomingStringBuilder.setSpan(new ForegroundColorSpan(styleSwitchColor),
                styleSwitchColorStartPos, textLength, SPAN_EXCLUSIVE_EXCLUSIVE);
      }

      if (clearSpanOpenings) {
        styleSwitchColorStartPos = -1;
      }
    }
  }

  // as there are no multi-line cues allowed, every PAC should be the first command of the line
  private void handlePreambleAddressCode(ClosedCaptionCtrl pacCommand) {
    if (DEBUG) {
      Log.i(TAG, "PAC: " + pacCommand.getPreambleAddressCodeMeaning());
    }

    positionBaseRow = pacCommand.getPreambleAddressCodePositionRow();
    positionBaseColumn = pacCommand.getPreambleAddressCodePositionColumn();

    // new row should receive new tab commands after this PAC, so we can reset old values here
    positionTabCount = 0;

    int preambleColor = pacCommand.getPreambleColor();
    if (preambleColor != Color.WHITE) {
      styleSwitchColor = preambleColor;
      styleSwitchColorStartPos = 0;
    }

    if (pacCommand.isPreambleItalic()) {
      styleSwitchItalicStartPos = 0;
    }

    if (pacCommand.isUnderline()) {
      styleSwitchUnderlineStartPos = 0;
    }
  }

  private boolean isModePopOn() {
    return captionMode == CC_MODE_POP_ON;
  }

  private boolean isModeRollUp() {
    return captionMode == CC_MODE_ROLL_UP;
  }

  private boolean isModePaintOn() {
    return captionMode == CC_MODE_PAINT_ON;
  }

  // CC_MODE_UNKNOWN means uninitialized mode, as we can switch to the live steam any time not
  // knowing what the previous commands were. Captions will be dropped till the next command
  // correctly setting up the state arrives
  private boolean isModeUnknown() {
    return captionMode == CC_MODE_UNKNOWN;
  }

  private void setCaptionMode(int captionMode) {
    if (this.captionMode == captionMode) {
      return;
    }

    this.captionMode = captionMode;

    incomingStringBuilder.clear();

    if (isModeRollUp() || isModeUnknown()) {
      // When switching to ROLL-UP or unknown, we also need to clear the captions.
      invokeRenderer(null);
    }

    if (isModeRollUp()) {
      // reset position to defaults. Incoming PAC will overwrite these values if necessary
      positionBaseColumn = 1;
      positionBaseRow = 15;

      // clear rolling window as well
      rollingQues.clear();
      clearCaptionsBuffer();
    }
  }

  private void setupCueBuilder(boolean clearSpanOpenings) {
    cueBuilder.reset();

    int buildLength = incomingStringBuilder.length();
    if (buildLength == 0) {
      return;
    }

    closeOpenSpans(STYLE_PRIORITY_ALL, clearSpanOpenings);

    cueBuilder.setRow(positionBaseRow);
    cueBuilder.setColumn(positionBaseColumn, positionTabCount);

    cueBuilder.setText(new SpannableStringBuilder(incomingStringBuilder, 0, buildLength));
  }

  private void clearPendingSample() {
    sampleHolder.timeUs = C.UNKNOWN_TIME_US;
    sampleHolder.clearData();
  }

  private boolean isSamplePending() {
    return sampleHolder.timeUs != C.UNKNOWN_TIME_US;
  }

}
