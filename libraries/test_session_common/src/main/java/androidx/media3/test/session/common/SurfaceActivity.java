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
package androidx.media3.test.session.common;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.ViewGroup;

/** An activity used for surface test */
public class SurfaceActivity extends Activity {
  private ViewGroup rootViewGroup;

  private SurfaceView firstSurfaceView;
  private SurfaceHolder firstSurfaceHolder;

  private SurfaceView secondSurfaceView;
  private SurfaceHolder secondSurfaceHolder;

  private TextureView firstTextureView;

  private TextureView secondTextureView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    TestUtils.setKeepScreenOn(this);
    setContentView(R.layout.activity_surface);

    rootViewGroup = findViewById(R.id.root_view_group);

    firstSurfaceView = findViewById(R.id.surface_view_first);
    firstSurfaceHolder = firstSurfaceView.getHolder();

    secondSurfaceView = findViewById(R.id.surface_view_second);
    secondSurfaceHolder = secondSurfaceView.getHolder();

    firstTextureView = findViewById(R.id.texture_view_first);

    secondTextureView = findViewById(R.id.texture_view_second);
  }

  public ViewGroup getRootViewGroup() {
    return rootViewGroup;
  }

  public SurfaceView getFirstSurfaceView() {
    return firstSurfaceView;
  }

  public SurfaceHolder getFirstSurfaceHolder() {
    return firstSurfaceHolder;
  }

  public SurfaceView getSecondSurfaceView() {
    return secondSurfaceView;
  }

  public SurfaceHolder getSecondSurfaceHolder() {
    return secondSurfaceHolder;
  }

  public TextureView getFirstTextureView() {
    return firstTextureView;
  }

  public TextureView getSecondTextureView() {
    return secondTextureView;
  }
}
