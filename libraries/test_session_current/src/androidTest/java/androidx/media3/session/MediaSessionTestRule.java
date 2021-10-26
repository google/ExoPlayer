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

import android.support.v4.media.session.MediaSessionCompat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** TestRule for releasing {@link MediaSession} instances after use. */
public class MediaSessionTestRule implements TestRule {
  private final List<MediaSession> sessions;
  private final List<MediaSessionCompat> sessionCompats;

  MediaSessionTestRule() {
    sessions = new CopyOnWriteArrayList<>();
    sessionCompats = new CopyOnWriteArrayList<>();
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          base.evaluate();
        } finally {
          cleanUpSessions();
        }
      }
    };
  }

  /** Ensures that release() is called after the test. */
  public <T extends MediaSession> T ensureReleaseAfterTest(T session) {
    sessions.add(session);
    return session;
  }

  /** Ensures that release() is called after the test. */
  public MediaSessionCompat ensureReleaseAfterTest(MediaSessionCompat session) {
    sessionCompats.add(session);
    return session;
  }

  private void cleanUpSessions() {
    for (int i = 0; i < sessions.size(); i++) {
      sessions.get(i).release();
    }
    sessions.clear();

    for (int i = 0; i < sessionCompats.size(); i++) {
      sessionCompats.get(i).release();
    }
    sessionCompats.clear();
  }
}
