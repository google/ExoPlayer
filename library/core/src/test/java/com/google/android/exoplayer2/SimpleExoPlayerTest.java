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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Unit test for {@link SimpleExoPlayer}. */
@RunWith(AndroidJUnit4.class)
public class SimpleExoPlayerTest {

  // TODO(b/143232359): Revert to @Config(sdk = Config.ALL_SDKS) once b/143232359 is resolved
  @Test
  @Config(minSdk = Config.OLDEST_SDK, maxSdk = Config.TARGET_SDK)
  public void builder_inBackgroundThread_doesNotThrow() throws Exception {
    Thread builderThread =
        new Thread(
            () -> new SimpleExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build());
    AtomicReference<Throwable> builderThrow = new AtomicReference<>();
    builderThread.setUncaughtExceptionHandler((thread, throwable) -> builderThrow.set(throwable));

    builderThread.start();
    builderThread.join();

    assertThat(builderThrow.get()).isNull();
  }
}
