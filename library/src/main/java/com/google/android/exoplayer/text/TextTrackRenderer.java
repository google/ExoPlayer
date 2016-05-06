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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.FormatHolder;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.TrackStream;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.extensions.Decoder;

import android.annotation.TargetApi;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * A {@link TrackRenderer} for subtitles.
 * <p>
 * Text is parsed from sample data using {@link Decoder} instances obtained from a
 * {@link SubtitleParserFactory}. The actual rendering of each line of text is delegated to a
 * {@link TextRenderer}.
 */
@TargetApi(16)
public final class TextTrackRenderer extends TrackRenderer implements Callback {

  private static final int MSG_UPDATE_OVERLAY = 0;

  private final Handler textRendererHandler;
  private final TextRenderer textRenderer;
  private final SubtitleParserFactory parserFactory;
  private final FormatHolder formatHolder;

  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private SubtitleParser parser;
  private SubtitleInputBuffer nextInputBuffer;
  private SubtitleOutputBuffer subtitle;
  private SubtitleOutputBuffer nextSubtitle;
  private int nextSubtitleEventIndex;

  /**
   * @param textRenderer The text renderer.
   * @param textRendererLooper The looper associated with the thread on which textRenderer should be
   *     invoked. If the renderer makes use of standard Android UI components, then this should
   *     normally be the looper associated with the application's main thread, which can be obtained
   *     using {@link android.app.Activity#getMainLooper()}. Null may be passed if the renderer
   *     should be invoked directly on the player's internal rendering thread.
   */
  public TextTrackRenderer(TextRenderer textRenderer, Looper textRendererLooper) {
    this(textRenderer, textRendererLooper, SubtitleParserFactory.DEFAULT);
  }

  /**
   * @param textRenderer The text renderer.
   * @param textRendererLooper The looper associated with the thread on which textRenderer should be
   *     invoked. If the renderer makes use of standard Android UI components, then this should
   *     normally be the looper associated with the application's main thread, which can be obtained
   *     using {@link android.app.Activity#getMainLooper()}. Null may be passed if the renderer
   *     should be invoked directly on the player's internal rendering thread.
   * @param parserFactory A factory from which to obtain {@link Decoder} instances.
   */
  public TextTrackRenderer(TextRenderer textRenderer, Looper textRendererLooper,
      SubtitleParserFactory parserFactory) {
    this.textRenderer = Assertions.checkNotNull(textRenderer);
    this.textRendererHandler = textRendererLooper == null ? null
        : new Handler(textRendererLooper, this);
    this.parserFactory = parserFactory;
    formatHolder = new FormatHolder();
  }

  @Override
  public int getTrackType() {
    return C.TRACK_TYPE_TEXT;
  }
  
  @Override
  protected int supportsFormat(Format format) {
    return parserFactory.supportsFormat(format) ? TrackRenderer.FORMAT_HANDLED
        : (MimeTypes.isText(format.sampleMimeType) ? FORMAT_UNSUPPORTED_SUBTYPE
        : FORMAT_UNSUPPORTED_TYPE);
  }

  @Override
  protected void onEnabled(Format[] formats, boolean joining) throws ExoPlaybackException {
    super.onEnabled(formats, joining);
    parser = parserFactory.createParser(formats[0]);
  }

  @Override
  protected void reset(long positionUs) {
    inputStreamEnded = false;
    outputStreamEnded = false;
    if (subtitle != null) {
      subtitle.release();
      subtitle = null;
    }
    if (nextSubtitle != null) {
      nextSubtitle.release();
      nextSubtitle = null;
    }
    nextInputBuffer = null;
    clearTextRenderer();
    parser.flush();
  }

  @Override
  protected void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      return;
    }

    if (nextSubtitle == null) {
      parser.setPositionUs(positionUs);
      try {
        nextSubtitle = parser.dequeueOutputBuffer();
      } catch (IOException e) {
        throw ExoPlaybackException.createForRenderer(e, getIndex());
      }
    }

    if (getState() != TrackRenderer.STATE_STARTED) {
      return;
    }

    boolean textRendererNeedsUpdate = false;
    if (subtitle != null) {
      // We're iterating through the events in a subtitle. Set textRendererNeedsUpdate if we
      // advance to the next event.
      long subtitleNextEventTimeUs = getNextEventTime();
      while (subtitleNextEventTimeUs <= positionUs) {
        nextSubtitleEventIndex++;
        subtitleNextEventTimeUs = getNextEventTime();
        textRendererNeedsUpdate = true;
      }
    }

    if (nextSubtitle != null && nextSubtitle.timestampUs <= positionUs) {
      // Advance to the next subtitle. Sync the next event index and trigger an update.
      if (subtitle != null) {
        subtitle.release();
      }
      subtitle = nextSubtitle;
      nextSubtitle = null;
      if (subtitle.isEndOfStream()) {
        outputStreamEnded = true;
        subtitle.release();
        subtitle = null;
        return;
      }
      nextSubtitleEventIndex = subtitle.getNextEventTimeIndex(positionUs);
      textRendererNeedsUpdate = true;
    }

    if (textRendererNeedsUpdate) {
      // textRendererNeedsUpdate is set and we're playing. Update the renderer.
      updateTextRenderer(subtitle.getCues(positionUs));
    }

    try {
      while (!inputStreamEnded) {
        if (nextInputBuffer == null) {
          nextInputBuffer = parser.dequeueInputBuffer();
          if (nextInputBuffer == null) {
            return;
          }
        }
        // Try and read the next subtitle from the source.
        int result = readSource(formatHolder, nextInputBuffer);
        if (result == TrackStream.BUFFER_READ) {
          // Clear BUFFER_FLAG_DECODE_ONLY (see [Internal: b/27893809]) and queue the buffer.
          nextInputBuffer.clearFlag(C.BUFFER_FLAG_DECODE_ONLY);
          if (nextInputBuffer.isEndOfStream()) {
            inputStreamEnded = true;
          } else {
            nextInputBuffer.subsampleOffsetUs = formatHolder.format.subsampleOffsetUs;
          }
          parser.queueInputBuffer(nextInputBuffer);
          nextInputBuffer = null;
        } else if (result == TrackStream.NOTHING_READ) {
          break;
        }
      }
    } catch (ParserException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
  }

  @Override
  protected void onDisabled() {
    if (subtitle != null) {
      subtitle.release();
      subtitle = null;
    }
    if (nextSubtitle != null) {
      nextSubtitle.release();
      nextSubtitle = null;
    }
    parser.release();
    parser = null;
    nextInputBuffer = null;
    clearTextRenderer();
    super.onDisabled();
  }

  @Override
  protected boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  protected boolean isReady() {
    // Don't block playback whilst subtitles are loading.
    // Note: To change this behavior, it will be necessary to consider [Internal: b/12949941].
    return true;
  }

  private long getNextEventTime() {
    return ((nextSubtitleEventIndex == -1)
        || (nextSubtitleEventIndex >= subtitle.getEventTimeCount())) ? Long.MAX_VALUE
        : (subtitle.getEventTime(nextSubtitleEventIndex));
  }

  private void updateTextRenderer(List<Cue> cues) {
    if (textRendererHandler != null) {
      textRendererHandler.obtainMessage(MSG_UPDATE_OVERLAY, cues).sendToTarget();
    } else {
      invokeRendererInternalCues(cues);
    }
  }

  private void clearTextRenderer() {
    updateTextRenderer(Collections.<Cue>emptyList());
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_UPDATE_OVERLAY:
        invokeRendererInternalCues((List<Cue>) msg.obj);
        return true;
    }
    return false;
  }

  private void invokeRendererInternalCues(List<Cue> cues) {
    textRenderer.onCues(cues);
  }

}
