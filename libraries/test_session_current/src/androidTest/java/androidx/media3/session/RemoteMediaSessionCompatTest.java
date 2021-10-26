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

import static androidx.media3.test.session.common.CommonConstants.DEFAULT_TEST_NAME;
import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link RemoteMediaSessionCompat}. */
@RunWith(AndroidJUnit4.class)
public class RemoteMediaSessionCompatTest {

  private Context context;
  private RemoteMediaSessionCompat remoteSessionCompat;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    remoteSessionCompat = new RemoteMediaSessionCompat(DEFAULT_TEST_NAME, context);
  }

  @After
  public void cleanUp() throws Exception {
    remoteSessionCompat.cleanUp();
  }

  @Test
  @SmallTest
  public void gettingToken() throws Exception {
    MediaSessionCompat.Token token = remoteSessionCompat.getSessionToken();
    assertThat(token).isNotNull();
  }

  @Test
  @SmallTest
  public void creatingControllerCompat() throws Exception {
    MediaSessionCompat.Token token = remoteSessionCompat.getSessionToken();
    assertThat(token).isNotNull();
    MediaControllerCompat controller = new MediaControllerCompat(context, token);
    assertThat(controller.getPackageName()).isEqualTo(SUPPORT_APP_PACKAGE_NAME);
  }
}
