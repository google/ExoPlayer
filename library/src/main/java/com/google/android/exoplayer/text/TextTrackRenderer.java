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
package com.google.android.exoplayer.text;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.FormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.dash.mpd.AdaptationSet;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.VerboseLogUtil;

import android.annotation.TargetApi;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link TrackRenderer} for textual subtitles. The actual rendering of each line of text to a
 * suitable output (e.g. the display) is delegated to a {@link TextRenderer}.
 */
@TargetApi(16)
public class TextTrackRenderer extends TrackRenderer implements Callback {

  /**
   * An interface for components that render text.
   */
  public interface TextRenderer {

    /**
     * Invoked each time there is a change in the text to be rendered.
     *
     * @param text The text to render, or null if no text is to be rendered.
     */
    void onText(String text);

  }

  private static final String TAG = "TextTrackRenderer";

  private static final int MSG_UPDATE_OVERLAY = 0;

  private final Handler textRendererHandler;
  private final TextRenderer textRenderer;
  private final SampleSource source;
  private final SampleHolder sampleHolder;
  private final FormatHolder formatHolder;
  private final SubtitleParser subtitleParser;

  private int trackIndex;

  private long currentPositionUs;
  private boolean inputStreamEnded;

  private Subtitle subtitle;
  private int nextSubtitleEventIndex;
  private boolean textRendererNeedsUpdate;

  /**
   * @param source A source from which samples containing subtitle data can be read.
   * @param subtitleParser A subtitle parser that will parse Subtitle objects from the source.
   * @param textRenderer The text renderer.
   * @param textRendererLooper The looper associated with the thread on which textRenderer should be
   *     invoked. If the renderer makes use of standard Android UI components, then this should
   *     normally be the looper associated with the applications' main thread, which can be
   *     obtained using {@link android.app.Activity#getMainLooper()}. Null may be passed if the
   *     renderer should be invoked directly on the player's internal rendering thread.
   */
  public TextTrackRenderer(SampleSource source, SubtitleParser subtitleParser,
      TextRenderer textRenderer, Looper textRendererLooper) {
    this.source = Assertions.checkNotNull(source);
    this.subtitleParser = Assertions.checkNotNull(subtitleParser);
    this.textRenderer = Assertions.checkNotNull(textRenderer);
    this.textRendererHandler = textRendererLooper == null ? null : new Handler(textRendererLooper,
        this);
    formatHolder = new FormatHolder();
    sampleHolder = new SampleHolder(true);
  }

  @Override
  protected int doPrepare() throws ExoPlaybackException {
    try {
      boolean sourcePrepared = source.prepare();
      if (!sourcePrepared) {
        return TrackRenderer.STATE_UNPREPARED;
      }
    } catch (IOException e) {
      throw new ExoPlaybackException(e);
    }
    for (int i = 0; i < source.getTrackCount(); i++) {
      if (subtitleParser.canParse(source.getTrackInfo(i).mimeType)) {
        trackIndex = i;
        return TrackRenderer.STATE_PREPARED;
      }
    }
    return TrackRenderer.STATE_IGNORE;
  }

  @Override
  protected void onEnabled(long timeUs, boolean joining) {
    source.enable(trackIndex, timeUs);
    seekToInternal(timeUs);
  }

  @Override
  protected void seekTo(long timeUs) {
    source.seekToUs(timeUs);
    seekToInternal(timeUs);
  }

  private void seekToInternal(long timeUs) {
    inputStreamEnded = false;
    currentPositionUs = timeUs;
    source.seekToUs(timeUs);
    if (subtitle != null && (timeUs < subtitle.getStartTime()
        || subtitle.getLastEventTime() <= timeUs)) {
      subtitle = null;
    }
    resetSampleData();
    clearTextRenderer();
    syncNextEventIndex(timeUs);
    textRendererNeedsUpdate = subtitle != null;
  }

  @Override
  protected void doSomeWork(long timeUs) throws ExoPlaybackException {
    source.continueBuffering(timeUs);
    currentPositionUs = timeUs;

    // We're iterating through the events in a subtitle. Set textRendererNeedsUpdate if we advance
    // to the next event.
    if (subtitle != null) {
      long nextEventTimeUs = getNextEventTime();
      while (nextEventTimeUs <= timeUs) {
        nextSubtitleEventIndex++;
        nextEventTimeUs = getNextEventTime();
        textRendererNeedsUpdate = true;
      }
      if (nextEventTimeUs == Long.MAX_VALUE) {
        // We've finished processing the subtitle.
        subtitle = null;
      }
    }

    // We don't have a subtitle. Try and read the next one from the source, and if we succeed then
    // sync and set textRendererNeedsUpdate.
    if (subtitle == null) {
      boolean resetSampleHolder = false;
      try {
        int result = source.readData(trackIndex, timeUs, formatHolder, sampleHolder, false);
        if (result == SampleSource.SAMPLE_READ) {
          resetSampleHolder = true;
          InputStream subtitleInputStream =
              new ByteArrayInputStream(sampleHolder.data.array(), 0, sampleHolder.size);
          subtitle = subtitleParser.parse(subtitleInputStream, "UTF-8", sampleHolder.timeUs);
          syncNextEventIndex(timeUs);
          textRendererNeedsUpdate = true;
        } else if (result == SampleSource.END_OF_STREAM) {
          inputStreamEnded = true;
        }
      } catch (IOException e) {
        resetSampleHolder = true;
        throw new ExoPlaybackException(e);
      } finally {
        if (resetSampleHolder) {
          resetSampleData();
        }
      }
    }

    // Update the text renderer if we're both playing and textRendererNeedsUpdate is set.
    if (textRendererNeedsUpdate && getState() == TrackRenderer.STATE_STARTED) {
      textRendererNeedsUpdate = false;
      if (subtitle == null) {
        clearTextRenderer();
      } else {
        updateTextRenderer(timeUs);
      }
    }
  }

  @Override
  protected void onDisabled() {
    source.disable(trackIndex);
    subtitle = null;
    resetSampleData();
    clearTextRenderer();
  }

  @Override
  protected void onReleased() {
    source.release();
  }

  @Override
  protected long getCurrentPositionUs() {
    return currentPositionUs;
  }

  @Override
  protected long getDurationUs() {
    return source.getTrackInfo(trackIndex).durationUs;
  }

  @Override
  protected long getBufferedPositionUs() {
    // Don't block playback whilst subtitles are loading.
    return END_OF_TRACK;
  }

  @Override
  protected boolean isEnded() {
    return inputStreamEnded && subtitle == null;
  }

  @Override
  protected boolean isReady() {
    // Don't block playback whilst subtitles are loading.
    // Note: To change this behavior, it will be necessary to consider [redacted].
    return true;
  }

  private void syncNextEventIndex(long timeUs) {
    nextSubtitleEventIndex = subtitle == null ? -1 : subtitle.getNextEventTimeIndex(timeUs);
  }

  private long getNextEventTime() {
    return ((nextSubtitleEventIndex == -1)
        || (nextSubtitleEventIndex >= subtitle.getEventTimeCount())) ? Long.MAX_VALUE
        : (subtitle.getEventTime(nextSubtitleEventIndex));
  }

  private void resetSampleData() {
    if (sampleHolder.data != null) {
      sampleHolder.data.position(0);
    }
  }

  private void updateTextRenderer(long timeUs) {
    String text = subtitle.getText(timeUs);
    log("updateTextRenderer; text=: " + text);
    if (textRendererHandler != null) {
      textRendererHandler.obtainMessage(MSG_UPDATE_OVERLAY, text).sendToTarget();
    } else {
      invokeTextRenderer(text);
    }
  }

  private void clearTextRenderer() {
    log("clearTextRenderer");
    if (textRendererHandler != null) {
      textRendererHandler.obtainMessage(MSG_UPDATE_OVERLAY, null).sendToTarget();
    } else {
      invokeTextRenderer(null);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_UPDATE_OVERLAY:
        invokeTextRenderer((String) msg.obj);
        return true;
    }
    return false;
  }

  private void invokeTextRenderer(String text) {
    textRenderer.onText(text);
  }

  private void log(String logMessage) {
    if (VerboseLogUtil.isTagEnabled(TAG)) {
      Log.v(TAG, "type=" + AdaptationSet.TYPE_TEXT + ", " + logMessage);
    }
  }

}
