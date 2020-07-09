/*
 * Copyright 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.media2;

import android.os.Handler;
import android.os.Looper;

/** A {@link Handler} that provides {@link #postOrRun(Runnable)}. */
/* package */ final class PlayerHandler extends Handler {
  public PlayerHandler(Looper looper) {
    super(looper);
  }

  /**
   * Posts the {@link Runnable} if the calling thread differs with the {@link Looper} of this
   * handler. Otherwise, runs the runnable directly.
   *
   * @param r A runnable to either post or run.
   * @return {@code true} if it's successfully run. {@code false} otherwise.
   */
  public boolean postOrRun(Runnable r) {
    if (Thread.currentThread() != getLooper().getThread()) {
      return post(r);
    }
    r.run();
    return true;
  }
}
