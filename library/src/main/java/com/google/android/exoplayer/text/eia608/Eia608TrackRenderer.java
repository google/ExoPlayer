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

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.text.TextRenderer;
import com.google.android.exoplayer.util.Assertions;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * A {@link TrackRenderer} for EIA-608 closed captions in a media stream.
 */
public class Eia608TrackRenderer extends TrackRenderer implements Callback {

  private static final int MSG_INVOKE_RENDERER = 0;
  // The Number of closed captions text line to keep in memory.
  private static final int ALLOWED_CAPTIONS_TEXT_LINES_COUNT = 4;

  private final SampleSource source;
  private final Eia608Parser eia608Parser;
  private final TextRenderer textRenderer;
  private final Handler metadataHandler;
  private final MediaFormatHolder formatHolder;
  private final SampleHolder sampleHolder;
  private final StringBuilder closedCaptionStringBuilder;
  //Currently displayed captions.
  private final List<ClosedCaption> currentCaptions;
  private final Queue<Integer> newLineIndexes;

  private int trackIndex;
  private long currentPositionUs;
  private boolean inputStreamEnded;

  private long pendingCaptionsTimestamp;
  private List<ClosedCaption> pendingCaptions;

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
    this.source = Assertions.checkNotNull(source);
    this.textRenderer = Assertions.checkNotNull(textRenderer);
    this.metadataHandler = textRendererLooper == null ? null
        : new Handler(textRendererLooper, this);
    eia608Parser = new Eia608Parser();
    formatHolder = new MediaFormatHolder();
    sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
    closedCaptionStringBuilder = new StringBuilder();
    currentCaptions = new LinkedList<ClosedCaption>();
    newLineIndexes = new LinkedList<Integer>();
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
      if (eia608Parser.canParse(source.getTrackInfo(i).mimeType)) {
        trackIndex = i;
        return TrackRenderer.STATE_PREPARED;
      }
    }
    return TrackRenderer.STATE_IGNORE;
  }

  @Override
  protected void onEnabled(long positionUs, boolean joining) {
    source.enable(trackIndex, positionUs);
    seekToInternal(positionUs);
  }

  @Override
  protected void seekTo(long positionUs) throws ExoPlaybackException {
    source.seekToUs(positionUs);
    seekToInternal(positionUs);
  }

  private void seekToInternal(long positionUs) {
    currentPositionUs = positionUs;
    pendingCaptions = null;
    inputStreamEnded = false;
    // Clear displayed captions.
    currentCaptions.clear();
  }

  @Override
  protected void doSomeWork(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException {
    currentPositionUs = positionUs;
    try {
      source.continueBuffering(positionUs);
    } catch (IOException e) {
      throw new ExoPlaybackException(e);
    }

    if (!inputStreamEnded && pendingCaptions == null) {
      try {
        int result = source.readData(trackIndex, positionUs, formatHolder, sampleHolder, false);
        if (result == SampleSource.SAMPLE_READ) {
          pendingCaptionsTimestamp = sampleHolder.timeUs;
          pendingCaptions = eia608Parser.parse(sampleHolder.data.array(), sampleHolder.size,
              sampleHolder.timeUs);
          sampleHolder.data.clear();
        } else if (result == SampleSource.END_OF_STREAM) {
          inputStreamEnded = true;
        }
      } catch (IOException e) {
        throw new ExoPlaybackException(e);
      }
    }

    if (pendingCaptions != null && pendingCaptionsTimestamp <= currentPositionUs) {
      invokeRenderer(pendingCaptions);
      pendingCaptions = null;
    }
  }

  @Override
  protected void onDisabled() {
    pendingCaptions = null;
    source.disable(trackIndex);
  }

  @Override
  protected long getDurationUs() {
    return source.getTrackInfo(trackIndex).durationUs;
  }

  @Override
  protected long getCurrentPositionUs() {
    return currentPositionUs;
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

  private void invokeRenderer(List<ClosedCaption> metadata) {
    if (metadataHandler != null) {
      metadataHandler.obtainMessage(MSG_INVOKE_RENDERER, metadata).sendToTarget();
    } else {
      invokeRendererInternal(metadata);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_INVOKE_RENDERER:
        invokeRendererInternal((List<ClosedCaption>) msg.obj);
        return true;
    }
    return false;
  }

  private void invokeRendererInternal(List<ClosedCaption> metadata) {
    currentCaptions.addAll(metadata);
    // Sort captions by the timestamp.
    Collections.sort(currentCaptions);
    closedCaptionStringBuilder.setLength(0);

    // After processing keep only captions after cutIndex.
    int cutIndex = 0;
    newLineIndexes.clear();
    for (int i = 0; i < currentCaptions.size(); i++) {
      ClosedCaption caption = currentCaptions.get(i);

      if (caption.type == ClosedCaption.TYPE_CTRL) {
        int cc2 = caption.text.codePointAt(1);
        switch (cc2) {
          case 0x2C: // Erase Displayed Memory.
            closedCaptionStringBuilder.setLength(0);
            cutIndex = i;
            newLineIndexes.clear();
            break;
          case 0x25: //  Roll-Up.
          case 0x26:
          case 0x27:
          default:
            if (cc2 >= 0x20 && cc2 < 0x40) {
              break;
            }
            if (closedCaptionStringBuilder.length() > 0
                && closedCaptionStringBuilder.charAt(closedCaptionStringBuilder.length() - 1)
                != '\n') {
              closedCaptionStringBuilder.append('\n');
              newLineIndexes.add(i);
              if (newLineIndexes.size() >= ALLOWED_CAPTIONS_TEXT_LINES_COUNT) {
                cutIndex = newLineIndexes.poll();
              }
            }
            break;
        }
      } else {
        closedCaptionStringBuilder.append(caption.text);
      }
    }

    if (cutIndex > 0 && cutIndex < currentCaptions.size() - 1) {
      for (int i = 0; i <= cutIndex; i++) {
        currentCaptions.remove(0);
      }
    }

    textRenderer.onText(closedCaptionStringBuilder.toString());
  }

}
