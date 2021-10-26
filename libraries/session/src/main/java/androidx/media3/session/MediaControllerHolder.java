/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.session;

import static androidx.media3.common.util.Util.postOrRun;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.common.util.concurrent.AbstractFuture;

/* package */ class MediaControllerHolder<T extends MediaController> extends AbstractFuture<T>
    implements MediaController.ConnectionCallback {

  private final Handler handler;
  @Nullable private T controller;
  private boolean accepted;

  public MediaControllerHolder(Looper looper) {
    handler = new Handler(looper);
  }

  public void setController(T controller) {
    this.controller = controller;
    maybeSetFutureResult();

    addListener(
        () -> {
          if (isCancelled()) {
            controller.release();
          }
        },
        runnable -> postOrRun(handler, runnable));
  }

  @Override
  public void onAccepted() {
    accepted = true;
    maybeSetFutureResult();
  }

  @Override
  public void onRejected() {
    maybeSetException();
  }

  private void maybeSetFutureResult() {
    if (controller != null && accepted) {
      set(controller);
    }
  }

  private void maybeSetException() {
    setException(new SecurityException("Session rejected the connection request."));
  }
}
