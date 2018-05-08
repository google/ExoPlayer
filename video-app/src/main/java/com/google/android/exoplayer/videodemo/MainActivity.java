/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer.videodemo;

import static com.google.android.exoplayer.videodemo.Samples.MP4_URI;

import android.app.Activity;
import android.os.Bundle;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

public class MainActivity extends Activity {

  private PlayerView playerView;
  private SimpleExoPlayer player;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main_activity);

    playerView = findViewById(R.id.player_view);
  }

  @Override
  protected void onStart() {
    super.onStart();

    player = ExoPlayerFactory.newSimpleInstance(this, new DefaultTrackSelector());
    playerView.setPlayer(player);

    DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(
        this,
        Util.getUserAgent(this, getString(R.string.application_name)));
    ExtractorMediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory)
        .createMediaSource(MP4_URI);
    player.prepare(mediaSource);

    player.setPlayWhenReady(true);
  }

  @Override
  protected void onStop() {
    playerView.setPlayer(null);
    player.release();
    player = null;

    super.onStop();
  }

}
