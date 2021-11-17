package com.example.rtspapplication;


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
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    PlayerView playerView = findViewById(R.id.player_view);
    playerView.requestFocus();

    ExoPlayer player = new ExoPlayer.Builder(this).build();

    MediaSource mediaSource =
        new RtspMediaSource.Factory()
            .createMediaSource(MediaItem.fromUri(Uri.parse(url))); // UDPdatsource Directory

    player.setMediaSource(mediaSource);
    playerView.setUseController(false);
    playerView.setPlayer(player);
    player.prepare();
    player.setPlayWhenReady(true);
  }
}