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

import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.util.Assertions;

import android.media.MediaCodec;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Wraps a {@link SubtitleParser}, exposing an interface similar to {@link MediaCodec} for
 * asynchronous parsing of subtitles.
 */
public class SubtitleParserHelper implements Handler.Callback {

  private final SubtitleParser parser;

  private final Handler handler;
  private SampleHolder sampleHolder;
  private boolean parsing;
  private Subtitle result;
  private IOException error;

  /**
   * @param looper The {@link Looper} associated with the thread on which parsing should occur.
   * @param parser The parser that should be used to parse the raw data.
   */
  public SubtitleParserHelper(Looper looper, SubtitleParser parser) {
    this.handler = new Handler(looper, this);
    this.parser = parser;
    flush();
  }

  /**
   * Flushes the helper, canceling the current parsing operation, if there is one.
   */
  public synchronized void flush() {
    sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
    parsing = false;
    result = null;
    error = null;
  }

  /**
   * Whether the helper is currently performing a parsing operation.
   *
   * @return True if the helper is currently performing a parsing operation. False otherwise.
   */
  public synchronized boolean isParsing() {
    return parsing;
  }

  /**
   * Gets the holder that should be populated with data to be parsed.
   * <p>
   * The returned holder will remain valid unless {@link #flush()} is called. If {@link #flush()}
   * is called the holder is replaced, and this method should be called again to obtain the new
   * holder.
   *
   * @return The holder that should be populated with data to be parsed.
   */
  public synchronized SampleHolder getSampleHolder() {
    return sampleHolder;
  }

  /**
   * Start a parsing operation.
   * <p>
   * The holder returned by {@link #getSampleHolder()} should be populated with the data to be
   * parsed prior to calling this method.
   */
  public synchronized void startParseOperation() {
    Assertions.checkState(!parsing);
    parsing = true;
    result = null;
    error = null;
    handler.obtainMessage(0, sampleHolder).sendToTarget();
  }

  /**
   * Gets the result of the most recent parsing operation.
   * <p>
   * The result is cleared as a result of calling this method, and so subsequent calls will return
   * null until a subsequent parsing operation has finished.
   *
   * @return The result of the parsing operation, or null.
   * @throws IOException If the parsing operation failed.
   */
  public synchronized Subtitle getAndClearResult() throws IOException {
    try {
      if (error != null) {
        throw error;
      }
      return result;
    } finally {
      error = null;
      result = null;
    }
  }

  @Override
  public boolean handleMessage(Message msg) {
    Subtitle result;
    IOException error;
    SampleHolder holder = (SampleHolder) msg.obj;
    try {
      InputStream inputStream = new ByteArrayInputStream(holder.data.array(), 0, holder.size);
      result = parser.parse(inputStream, null, sampleHolder.timeUs);
      error = null;
    } catch (IOException e) {
      result = null;
      error = e;
    }
    synchronized (this) {
      if (sampleHolder != holder) {
        // A flush has occurred since this holder was posted. Do nothing.
      } else {
        holder.data.position(0);
        this.result = result;
        this.error = error;
        this.parsing = false;
      }
    }
    return true;
  }

}
