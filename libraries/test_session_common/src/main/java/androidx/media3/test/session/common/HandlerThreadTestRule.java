/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.media3.test.session.common;

import android.os.HandlerThread;
import org.junit.rules.ExternalResource;

/** TestRule for providing a handler and an executor for {@link HandlerThread}. */
public final class HandlerThreadTestRule extends ExternalResource {

  private final String threadName;
  private TestHandler handler;

  public HandlerThreadTestRule(String threadName) {
    this.threadName = threadName;
  }

  @Override
  protected void before() {
    HandlerThread handlerThread = new HandlerThread(threadName);
    handlerThread.start();

    handler = new TestHandler(handlerThread.getLooper());
  }

  @Override
  protected void after() {
    try {
      handler.getLooper().quitSafely();
    } finally {
      handler = null;
    }
  }

  /** Gets the handler for the thread. */
  public TestHandler getHandler() {
    if (handler == null) {
      throw new IllegalStateException("It should be called between before() and after()");
    }
    return handler;
  }
}
