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
package com.google.android.exoplayer2.imademo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;

/**
 * Main Activity for the ExoPlayer IMA plugin example. ExoPlayer objects are created by DemoPlayer,
 * which this class instantiates.
 */
public class MainActivity extends AppCompatActivity {

    private DemoPlayer mPlayer;
    private SimpleExoPlayerView mView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mView = (SimpleExoPlayerView) findViewById(R.id.simpleExoPlayerView);
        mPlayer = new DemoPlayer(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mPlayer.init(this, mView);
    }

    @Override
    public void onPause() {
        super.onPause();
        mPlayer.reset();
    }

    @Override
    public void onDestroy() {
        mPlayer.release();
        super.onDestroy();
    }
}
