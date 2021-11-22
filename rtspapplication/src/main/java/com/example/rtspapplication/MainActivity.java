package com.example.rtspapplication;


import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Bundle;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;


public class MainActivity extends AppCompatActivity {
  String url = "rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mov";
  String TAG = "MainActivity.java";
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    Log.i(TAG,"Started App");
    PlayerView playerView = findViewById(R.id.player_view);
    Log.i(TAG,"Created PlayerView");
    playerView.requestFocus();

    ExoPlayer player = new ExoPlayer.Builder(this).build();
    Log.i(TAG,"Finished creation of new Exoplayer");
    MediaSource mediaSource =
        new RtspMediaSource.Factory()
            .createMediaSource(MediaItem.fromUri(Uri.parse(url))); // UDPdatsource Directory
    Log.i(TAG,"Finished creation of mediaSource");

    player.setMediaSource(mediaSource);
    Log.i(TAG,"Set Media Source");
    playerView.setUseController(false);
    playerView.setPlayer(player);
    player.prepare();
    Log.i(TAG,"Player Prepared");
    player.setPlayWhenReady(true);
    Log.i(TAG,"Set to play when the player is ready");
  }
}