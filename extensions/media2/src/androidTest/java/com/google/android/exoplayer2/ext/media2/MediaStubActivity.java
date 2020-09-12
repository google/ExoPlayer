/*
 * Copyright 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.media2;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import com.google.android.exoplayer2.ext.media2.test.R;
import com.google.android.exoplayer2.util.Util;

/** Stub activity to play media contents on. */
public final class MediaStubActivity extends Activity {

  private static final String TAG = "MediaStubActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.mediaplayer);

    // disable enter animation.
    overridePendingTransition(0, 0);

    if (Util.SDK_INT >= 27) {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      setTurnScreenOn(true);
      setShowWhenLocked(true);
      KeyguardManager keyguardManager =
          (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
      keyguardManager.requestDismissKeyguard(this, null);
    } else {
      getWindow()
          .addFlags(
              WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                  | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                  | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                  | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }
  }

  @Override
  public void finish() {
    super.finish();

    // disable exit animation.
    overridePendingTransition(0, 0);
  }

  @Override
  protected void onResume() {
    Log.i(TAG, "onResume");
    super.onResume();
  }

  @Override
  protected void onPause() {
    Log.i(TAG, "onPause");
    super.onPause();
  }

  public SurfaceHolder getSurfaceHolder() {
    SurfaceView surface = findViewById(R.id.surface);
    return surface.getHolder();
  }
}
