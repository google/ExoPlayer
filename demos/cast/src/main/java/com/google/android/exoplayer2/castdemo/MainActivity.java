/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.castdemo;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ui.PlaybackControlView;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.util.Util;
import com.google.android.gms.cast.framework.CastButtonFactory;

/**
 * An activity that plays video using {@link SimpleExoPlayer} and {@link CastPlayer}.
 */
public class MainActivity extends AppCompatActivity {

  private SimpleExoPlayerView simpleExoPlayerView;
  private PlaybackControlView castControlView;
  private PlayerManager playerManager;

  // Activity lifecycle methods.

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.main_activity);

    simpleExoPlayerView = findViewById(R.id.player_view);
    simpleExoPlayerView.requestFocus();

    castControlView = findViewById(R.id.cast_control_view);

    ListView sampleList = findViewById(R.id.sample_list);
    sampleList.setAdapter(new SampleListAdapter());
    sampleList.setOnItemClickListener(new SampleClickListener());
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    getMenuInflater().inflate(R.menu.menu, menu);
    CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu,
        R.id.media_route_menu_item);
    return true;
  }

  @Override
  public void onStart() {
    super.onStart();
    if (Util.SDK_INT > 23) {
      setupPlayerManager();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if ((Util.SDK_INT <= 23)) {
      setupPlayerManager();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (Util.SDK_INT <= 23) {
      releasePlayerManager();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (Util.SDK_INT > 23) {
      releasePlayerManager();
    }
  }

  // Activity input.

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // If the event was not handled then see if the player view can handle it.
    return super.dispatchKeyEvent(event) || playerManager.dispatchKeyEvent(event);
  }

  // Internal methods.

  private void setupPlayerManager() {
    playerManager = new PlayerManager(simpleExoPlayerView, castControlView,
        getApplicationContext());
  }

  private void releasePlayerManager() {
    playerManager.release();
    playerManager = null;
  }

  // User controls.

  private final class SampleListAdapter extends ArrayAdapter<CastDemoUtil.Sample> {

    public SampleListAdapter() {
      super(getApplicationContext(), android.R.layout.simple_list_item_1, CastDemoUtil.SAMPLES);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = super.getView(position, convertView, parent);
      view.setBackgroundColor(Color.WHITE);
      return view;
    }

  }

  private class SampleClickListener implements AdapterView.OnItemClickListener {

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
      if (parent.getSelectedItemPosition() != position) {
        CastDemoUtil.Sample currentSample = CastDemoUtil.SAMPLES.get(position);
        playerManager.setCurrentSample(currentSample, 0, true);
      }
    }

  }

}
