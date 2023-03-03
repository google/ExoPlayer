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

import android.content.Context;
import android.media.AudioManager;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** TestRule for preparing main looper. */
public final class MainLooperTestRule implements TestRule {

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        prepare();
        base.evaluate();
      }
    };
  }

  private void prepare() {
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              // Prepare the main looper if it hasn't.
              // Some framework APIs always run on the main looper.
              if (Looper.getMainLooper() == null) {
                Looper.prepareMainLooper();
              }

              // Initialize AudioManager on the main thread to workaround b/78617702 that
              // audio focus listener is called on the thread where the AudioManager was
              // originally initialized.
              // Without posting this, audio focus listeners wouldn't be called because the
              // listeners would be posted to the test thread (here) where it waits until the
              // tests are finished.
              Context context = ApplicationProvider.getApplicationContext();
              AudioManager unusedManager =
                  (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            });
  }

  public static void runOnMainSync(Runnable runnable) throws Exception {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
  }
}
