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

import static com.google.android.exoplayer2.session.vct.common.CommonConstants.DEFAULT_TEST_NAME;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link RemoteMediaSession}. */
@RunWith(AndroidJUnit4.class)
public class RemoteMediaSessionTest {

  private Context context;
  private RemoteMediaSession remoteSession;
  private Bundle tokenExtras;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    tokenExtras = TestUtils.createTestBundle();
    remoteSession = new RemoteMediaSession(DEFAULT_TEST_NAME, context, tokenExtras);
  }

  @After
  public void cleanUp() throws Exception {
    if (remoteSession != null) {
      remoteSession.cleanUp();
    }
  }

  @Test
  @SmallTest
  public void gettingToken() throws Exception {
    SessionToken token = remoteSession.getToken();
    assertThat(token).isNotNull();
    assertThat(token.getPackageName()).isEqualTo(SUPPORT_APP_PACKAGE_NAME);
    assertThat(TestUtils.equals(tokenExtras, token.getExtras())).isTrue();
  }

  @Test
  @SmallTest
  public void creatingController() throws Exception {
    SessionToken token = remoteSession.getToken();
    assertThat(token).isNotNull();
    MediaController controller =
        new MediaController.Builder(context)
            .setSessionToken(token)
            .setControllerCallback(new MediaController.ControllerCallback() {})
            .build();
    assertThat(controller).isNotNull();
  }
}
