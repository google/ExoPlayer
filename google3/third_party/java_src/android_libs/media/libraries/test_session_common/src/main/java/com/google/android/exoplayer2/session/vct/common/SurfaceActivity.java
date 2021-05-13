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

package com.google.android.exoplayer2.session.vct.common;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** An activity used for surface test */
public class SurfaceActivity extends Activity {
  private SurfaceHolder firstSurfaceHolder;
  private SurfaceHolder secondSurfaceHolder;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    TestUtils.setKeepScreenOn(this);
    setContentView(R.layout.activity_surface);

    SurfaceView firstSurfaceView = findViewById(R.id.surface_view_first);
    firstSurfaceHolder = firstSurfaceView.getHolder();

    SurfaceView secondSurfaceView = findViewById(R.id.surface_view_second);
    secondSurfaceHolder = secondSurfaceView.getHolder();
  }

  public SurfaceHolder getFirstSurfaceHolder() {
    return firstSurfaceHolder;
  }

  public SurfaceHolder getSecondSurfaceHolder() {
    return secondSurfaceHolder;
  }
}
