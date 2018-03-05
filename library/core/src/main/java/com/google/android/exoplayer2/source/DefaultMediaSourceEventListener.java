/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import java.io.IOException;

/**
 * A {@link MediaSourceEventListener} allowing selective overrides. All methods are implemented as
 * no-ops.
 */
public abstract class DefaultMediaSourceEventListener implements MediaSourceEventListener {

  @Override
  public void onLoadStarted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  @Override
  public void onLoadCompleted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  @Override
  public void onLoadCanceled(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  @Override
  public void onLoadError(
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled) {
    // Do nothing.
  }

  @Override
  public void onUpstreamDiscarded(MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  @Override
  public void onDownstreamFormatChanged(MediaLoadData mediaLoadData) {
    // Do nothing.
  }
}
