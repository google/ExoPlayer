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

import static com.google.android.exoplayer2.session.vct.common.CommonConstants.MOCK_MEDIA2_LIBRARY_SERVICE;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.MOCK_MEDIA2_SESSION_SERVICE;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import com.google.android.exoplayer2.session.vct.common.MainLooperTestRule;
import java.util.Set;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaSessionManager}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaSessionManagerTest {

  private static final ComponentName MOCK_BROWSER_SERVICE_COMPAT_NAME =
      new ComponentName(
          SUPPORT_APP_PACKAGE_NAME, MockMediaBrowserServiceCompat.class.getCanonicalName());

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private Context context;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
  }

  @Test
  public void getSessionServiceTokens() {
    boolean hasMockBrowserServiceCompat = false;
    boolean hasMockSessionService2 = false;
    boolean hasMockLibraryService2 = false;
    MediaSessionManager sessionManager = MediaSessionManager.getInstance(context);
    Set<SessionToken> serviceTokens = sessionManager.getSessionServiceTokens();
    for (SessionToken token : serviceTokens) {
      ComponentName componentName = token.getComponentName();
      if (MOCK_BROWSER_SERVICE_COMPAT_NAME.equals(componentName)) {
        hasMockBrowserServiceCompat = true;
      } else if (MOCK_MEDIA2_SESSION_SERVICE.equals(componentName)) {
        hasMockSessionService2 = true;
      } else if (MOCK_MEDIA2_LIBRARY_SERVICE.equals(componentName)) {
        hasMockLibraryService2 = true;
      }
    }
    assertThat(hasMockBrowserServiceCompat).isTrue();
    assertThat(hasMockSessionService2).isTrue();
    assertThat(hasMockLibraryService2).isTrue();
  }
}
