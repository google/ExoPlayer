/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2;

import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MediaClock;

import java.io.IOException;

/**
 * An abstract base class suitable for most {@link Renderer} implementations.
 */
public abstract class BaseRenderer implements Renderer, RendererCapabilities {

  private int index;
  private int state;
  private SampleStream stream;
  private long streamOffsetUs;
  private boolean readEndOfStream;
  private boolean streamIsFinal;

  public BaseRenderer() {
    readEndOfStream = true;
  }

  @Override
  public final RendererCapabilities getCapabilities() {
    return this;
  }

  @Override
  public final void setIndex(int index) {
    this.index = index;
  }

  @Override
  public final int getIndex() {
    return index;
  }

  @Override
  public MediaClock getMediaClock() {
    return null;
  }

  @Override
  public final int getState() {
    return state;
  }

  @Override
  public final void enable(Format[] formats, SampleStream stream, long positionUs,
      boolean joining, long offsetUs) throws ExoPlaybackException {
    Assertions.checkState(state == STATE_DISABLED);
    state = STATE_ENABLED;
    onEnabled(joining);
    replaceStream(formats, stream, offsetUs);
    onReset(positionUs, joining);
  }

  /**
   * Called when the renderer is enabled.
   * <p>
   * The default implementation is a no-op.
   *
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onEnabled(boolean joining) throws ExoPlaybackException {
    // Do nothing.
  }

  @Override
  public final void replaceStream(Format[] formats, SampleStream stream, long offsetUs)
      throws ExoPlaybackException {
    Assertions.checkState(!streamIsFinal);
    this.stream = stream;
    readEndOfStream = false;
    streamOffsetUs = offsetUs;
    onStreamChanged(formats);
  }

  /**
   * Called when the renderer's stream has changed.
   * <p>
   * The default implementation is a no-op.
   *
   * @param formats The enabled formats.
   * @throws ExoPlaybackException Thrown if an error occurs.
   */
  protected void onStreamChanged(Format[] formats) throws ExoPlaybackException {
    // Do nothing.
  }

  @Override
  public final void reset(long positionUs) throws ExoPlaybackException {
    streamIsFinal = false;
    onReset(positionUs, false);
  }

  /**
   * Invoked when a reset is encountered, and also when the renderer is enabled.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @param positionUs The playback position in microseconds.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @throws ExoPlaybackException If an error occurs handling the reset.
   */
  protected void onReset(long positionUs, boolean joining) throws ExoPlaybackException {
    // Do nothing.
  }

  @Override
  public final boolean hasReadStreamToEnd() {
    return readEndOfStream;
  }

  @Override
  public final void setCurrentStreamIsFinal() {
    streamIsFinal = true;
  }

  @Override
  public final void start() throws ExoPlaybackException {
    Assertions.checkState(state == STATE_ENABLED);
    state = STATE_STARTED;
    onStarted();
  }

  /**
   * Called when the renderer is started.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onStarted() throws ExoPlaybackException {
    // Do nothing.
  }

  @Override
  public final void stop() throws ExoPlaybackException {
    Assertions.checkState(state == STATE_STARTED);
    state = STATE_ENABLED;
    onStopped();
  }

  /**
   * Called when the renderer is stopped.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onStopped() throws ExoPlaybackException {
    // Do nothing.
  }

  @Override
  public final void disable() {
    Assertions.checkState(state == STATE_ENABLED);
    state = STATE_DISABLED;
    onDisabled();
    stream = null;
    streamIsFinal = false;
  }

  /**
   * Called when the renderer is disabled.
   * <p>
   * The default implementation is a no-op.
   */
  protected void onDisabled() {
    // Do nothing.
  }

  @Override
  public final void maybeThrowStreamError() throws IOException {
    stream.maybeThrowError();
  }

  // RendererCapabilities implementation.

  @Override
  public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
    return ADAPTIVE_NOT_SUPPORTED;
  }

  // ExoPlayerComponent implementation.

  @Override
  public void handleMessage(int what, Object object) throws ExoPlaybackException {
    // Do nothing.
  }

  // Methods to be called by subclasses.

  /**
   * Reads from the enabled upstream source.
   *
   * @see SampleStream#readData(FormatHolder, DecoderInputBuffer)
   */
  protected final int readSource(FormatHolder formatHolder, DecoderInputBuffer buffer) {
    int result = stream.readData(formatHolder, buffer);
    if (result == C.RESULT_BUFFER_READ) {
      if (buffer.isEndOfStream()) {
        readEndOfStream = true;
        return streamIsFinal ? C.RESULT_BUFFER_READ : C.RESULT_NOTHING_READ;
      }
      buffer.timeUs += streamOffsetUs;
    }
    return result;
  }

  /**
   * Returns whether the upstream source is ready.
   *
   * @return True if the source is ready. False otherwise.
   */
  protected final boolean isSourceReady() {
    return readEndOfStream ? streamIsFinal : stream.isReady();
  }

}
