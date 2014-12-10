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
package com.google.android.exoplayer;

/**
 * A {@link TrackRenderer} that does nothing.
 * <p>
 * This renderer returns {@link TrackRenderer#STATE_IGNORE} from {@link #doPrepare()} in order to
 * request that it should be ignored. {@link IllegalStateException} is thrown from all methods that
 * are documented to indicate that they should not be invoked unless the renderer is prepared.
 */
public class DummyTrackRenderer extends TrackRenderer {

  @Override
  protected int doPrepare() throws ExoPlaybackException {
    return STATE_IGNORE;
  }

  @Override
  protected boolean isEnded() {
    throw new IllegalStateException();
  }

  @Override
  protected boolean isReady() {
    throw new IllegalStateException();
  }

  @Override
  protected void seekTo(long positionUs) {
    throw new IllegalStateException();
  }

  @Override
  protected void doSomeWork(long positionUs, long elapsedRealtimeUs) {
    throw new IllegalStateException();
  }

  @Override
  protected long getDurationUs() {
    throw new IllegalStateException();
  }

  @Override
  protected long getBufferedPositionUs() {
    throw new IllegalStateException();
  }

  @Override
  protected long getCurrentPositionUs() {
    throw new IllegalStateException();
  }

}
