/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.transformer;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.WindowManager;
import androidx.media3.common.util.Util;
import androidx.media3.transformer.test.R;

/** An activity with surfaces for testing purposes. */
public final class SurfaceTestActivity extends Activity {

  private SurfaceView surfaceView;
  private TextureView textureView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setKeepScreenOn(this);
    setContentView(R.layout.surface_test_activity);
    surfaceView = findViewById(R.id.surface_view);
    textureView = findViewById(R.id.texture_view);
  }

  /** Gets this activity's {@link SurfaceView}. */
  public SurfaceView getSurfaceView() {
    return surfaceView;
  }

  /** Gets this activity's {@link TextureView}. */
  public TextureView getTextureView() {
    return textureView;
  }

  private static void setKeepScreenOn(Activity activity) {
    if (Util.SDK_INT >= 27) {
      activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      activity.setTurnScreenOn(true);
      activity.setShowWhenLocked(true);
      KeyguardManager keyguardManager =
          (KeyguardManager) activity.getSystemService(KEYGUARD_SERVICE);
      keyguardManager.requestDismissKeyguard(activity, /* callback= */ null);
    } else {
      activity
          .getWindow()
          .addFlags(
              WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                  | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                  | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                  | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }
  }
}
