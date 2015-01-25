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
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.util.Assertions;

import android.annotation.TargetApi;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import java.io.IOException;

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

  private static final int MSG_UPDATE_OVERLAY = 0;

  private final Handler textRendererHandler;
  private final TextRenderer textRenderer;
  private final SampleSource source;
  private final MediaFormatHolder formatHolder;
  private final SubtitleParser subtitleParser;

  private int trackIndex;

  private long currentPositionUs;
  private boolean inputStreamEnded;

  private Subtitle subtitle;
  private SubtitleParserHelper parserHelper;
  private HandlerThread parserThread;
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
    formatHolder = new MediaFormatHolder();
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
  protected void onEnabled(long positionUs, boolean joining) {
    source.enable(trackIndex, positionUs);
    parserThread = new HandlerThread("textParser");
    parserThread.start();
    parserHelper = new SubtitleParserHelper(parserThread.getLooper(), subtitleParser);
    seekToInternal(positionUs);
  }

  @Override
  protected void seekTo(long positionUs) {
    source.seekToUs(positionUs);
    seekToInternal(positionUs);
  }

  private void seekToInternal(long positionUs) {
    inputStreamEnded = false;
    currentPositionUs = positionUs;
    source.seekToUs(positionUs);
    if (subtitle != null && (positionUs < subtitle.getStartTime()
        || subtitle.getLastEventTime() <= positionUs)) {
      subtitle = null;
    }
    parserHelper.flush();
    clearTextRenderer();
    syncNextEventIndex(positionUs);
    textRendererNeedsUpdate = subtitle != null;
  }

  @Override
  protected void doSomeWork(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    currentPositionUs = positionUs;
    try {
      source.continueBuffering(positionUs);
    } catch (IOException e) {
      throw new ExoPlaybackException(e);
    }

    if (parserHelper.isParsing()) {
      return;
    }

    Subtitle dequeuedSubtitle = null;
    if (subtitle == null) {
      try {
        dequeuedSubtitle = parserHelper.getAndClearResult();
      } catch (IOException e) {
        throw new ExoPlaybackException(e);
      }
    }

    if (subtitle == null && dequeuedSubtitle != null) {
      // We've dequeued a new subtitle. Sync the event index and update the subtitle.
      subtitle = dequeuedSubtitle;
      syncNextEventIndex(positionUs);
      textRendererNeedsUpdate = true;
    } else if (subtitle != null) {
      // We're iterating through the events in a subtitle. Set textRendererNeedsUpdate if we
      // advance to the next event.
      long nextEventTimeUs = getNextEventTime();
      while (nextEventTimeUs <= positionUs) {
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
    if (!inputStreamEnded && subtitle == null) {
      try {
        SampleHolder sampleHolder = parserHelper.getSampleHolder();
        int result = source.readData(trackIndex, positionUs, formatHolder, sampleHolder, false);
        if (result == SampleSource.SAMPLE_READ) {
          parserHelper.startParseOperation();
        } else if (result == SampleSource.END_OF_STREAM) {
          inputStreamEnded = true;
        }
      } catch (IOException e) {
        throw new ExoPlaybackException(e);
      }
    }

    // Update the text renderer if we're both playing and textRendererNeedsUpdate is set.
    if (textRendererNeedsUpdate && getState() == TrackRenderer.STATE_STARTED) {
      textRendererNeedsUpdate = false;
      if (subtitle == null) {
        clearTextRenderer();
      } else {
        updateTextRenderer(positionUs);
      }
    }
  }

  @Override
  protected void onDisabled() {
    subtitle = null;
    parserThread.quit();
    parserThread = null;
    parserHelper = null;
    clearTextRenderer();
    source.disable(trackIndex);
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
    return END_OF_TRACK_US;
  }

  @Override
  protected boolean isEnded() {
    return inputStreamEnded && subtitle == null;
  }

  @Override
  protected boolean isReady() {
    // Don't block playback whilst subtitles are loading.
    // Note: To change this behavior, it will be necessary to consider [Internal: b/12949941].
    return true;
  }

  private void syncNextEventIndex(long positionUs) {
    nextSubtitleEventIndex = subtitle == null ? -1 : subtitle.getNextEventTimeIndex(positionUs);
  }

  private long getNextEventTime() {
    return ((nextSubtitleEventIndex == -1)
        || (nextSubtitleEventIndex >= subtitle.getEventTimeCount())) ? Long.MAX_VALUE
        : (subtitle.getEventTime(nextSubtitleEventIndex));
  }

  private void updateTextRenderer(long positionUs) {
    String text = subtitle.getText(positionUs);
    if (textRendererHandler != null) {
      textRendererHandler.obtainMessage(MSG_UPDATE_OVERLAY, text).sendToTarget();
    } else {
      invokeRendererInternal(text);
    }
  }

  private void clearTextRenderer() {
    if (textRendererHandler != null) {
      textRendererHandler.obtainMessage(MSG_UPDATE_OVERLAY, null).sendToTarget();
    } else {
      invokeRendererInternal(null);
    }
  }

  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_UPDATE_OVERLAY:
        invokeRendererInternal((String) msg.obj);
        return true;
    }
    return false;
  }

  private void invokeRendererInternal(String text) {
    textRenderer.onText(text);
  }

}
