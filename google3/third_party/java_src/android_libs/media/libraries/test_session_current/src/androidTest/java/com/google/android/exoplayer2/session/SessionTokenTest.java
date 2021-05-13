/*
 * Copyright 2018 The Android Open Source Project
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

package com.google.android.exoplayer2.session;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import com.google.android.exoplayer2.session.vct.common.MainLooperTestRule;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link SessionToken}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SessionTokenTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private Context context;
  private List<MediaSession> sessions = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
  }

  @After
  public void cleanUp() throws Exception {
    for (MediaSession session : sessions) {
      if (session != null) {
        session.release();
      }
    }
  }

  @Test
  public void constructor_sessionService() {
    SessionToken token =
        new SessionToken(
            context,
            new ComponentName(
                context.getPackageName(), MockMediaSessionService.class.getCanonicalName()));
    assertThat(token.getPackageName()).isEqualTo(context.getPackageName());
    assertThat(token.getUid()).isEqualTo(Process.myUid());
    assertThat(token.getType()).isEqualTo(SessionToken.TYPE_SESSION_SERVICE);
  }

  @Test
  public void constructor_libraryService() {
    ComponentName testComponentName =
        new ComponentName(
            context.getPackageName(), MockMediaLibraryService.class.getCanonicalName());
    SessionToken token = new SessionToken(context, testComponentName);

    assertThat(token.getPackageName()).isEqualTo(context.getPackageName());
    assertThat(token.getUid()).isEqualTo(Process.myUid());
    assertThat(token.getType()).isEqualTo(SessionToken.TYPE_LIBRARY_SERVICE);
    assertThat(token.getServiceName()).isEqualTo(testComponentName.getClassName());
  }

  @Test
  public void getters_whenCreatedBySession() {
    Bundle testTokenExtras = TestUtils.createTestBundle();
    MediaSession session =
        new MediaSession.Builder(context, new MockPlayer.Builder().build())
            .setId("testGetters_whenCreatedBySession")
            .setExtras(testTokenExtras)
            .build();
    sessions.add(session);
    SessionToken token = session.getToken();

    assertThat(token.getPackageName()).isEqualTo(context.getPackageName());
    assertThat(token.getUid()).isEqualTo(Process.myUid());
    assertThat(token.getType()).isEqualTo(SessionToken.TYPE_SESSION);
    assertThat(TestUtils.equals(testTokenExtras, token.getExtras())).isTrue();
    assertThat(token.getServiceName()).isNull();
  }
}
