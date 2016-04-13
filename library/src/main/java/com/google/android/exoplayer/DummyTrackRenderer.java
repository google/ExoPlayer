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
 */
public final class DummyTrackRenderer extends TrackRenderer {

  @Override
  protected int supportsFormat(Format format) throws ExoPlaybackException {
    return TrackRenderer.FORMAT_UNSUPPORTED_TYPE;
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
  protected void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    throw new IllegalStateException();
  }

  @Override
  protected void reset(long positionUs) throws ExoPlaybackException {
    throw new IllegalStateException();
  }

}
