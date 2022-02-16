/*
 * Copyright 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.PendingIntent;
import android.content.Intent;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowPendingIntent;

/** Tests for {@link DefaultActionFactory}. */
@RunWith(AndroidJUnit4.class)
public class DefaultActionFactoryTest {

  @Test
  public void createMediaPendingIntent_intentIsMediaAction() {
    DefaultActionFactory actionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));

    PendingIntent pendingIntent =
        actionFactory.createMediaActionPendingIntent(MediaNotification.ActionFactory.COMMAND_PLAY);

    ShadowPendingIntent shadowPendingIntent = shadowOf(pendingIntent);
    assertThat(actionFactory.isMediaAction(shadowPendingIntent.getSavedIntent())).isTrue();
  }

  @Test
  public void isMediaAction_withNonMediaIntent_returnsFalse() {
    DefaultActionFactory actionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));

    Intent intent = new Intent("invalid_action");

    assertThat(actionFactory.isMediaAction(intent)).isFalse();
  }

  @Test
  public void isCustomAction_withNonCustomActionIntent_returnsFalse() {
    DefaultActionFactory actionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));

    Intent intent = new Intent("invalid_action");

    assertThat(actionFactory.isCustomAction(intent)).isFalse();
  }

  /** A test service for unit tests. */
  public static final class TestService extends MediaLibraryService {
    @Nullable
    @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
      return null;
    }
  }
}
