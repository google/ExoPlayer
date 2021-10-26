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
package androidx.media3.session;

import static androidx.media3.test.session.common.CommonConstants.MOCK_MEDIA3_LIBRARY_SERVICE;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link RemoteMediaBrowserCompat}. */
@RunWith(AndroidJUnit4.class)
public class RemoteMediaBrowserCompatTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private RemoteMediaBrowserCompat remoteBrowserCompat;

  @Before
  public void setUp() throws Exception {
    remoteBrowserCompat =
        new RemoteMediaBrowserCompat(
            ApplicationProvider.getApplicationContext(), MOCK_MEDIA3_LIBRARY_SERVICE);
  }

  @After
  public void cleanUp() throws Exception {
    if (remoteBrowserCompat != null) {
      remoteBrowserCompat.cleanUp();
    }
  }

  @Test
  @SmallTest
  public void connect() throws Exception {
    remoteBrowserCompat.connect(/* waitForConnection= */ true);
    assertThat(remoteBrowserCompat.isConnected()).isTrue();
  }
}
