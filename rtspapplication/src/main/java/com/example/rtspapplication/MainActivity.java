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
  //String url = "rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mov";
  String url ="rtsp://demo:demo@ipvmdemo.dyndns.org:5541/onvif-media/media.amp?profile=profile_1_h264&sessiontimeout=60&streamtype=unicast"; // This is a LIVE feed!
  String TAG =  Constants.TAG + " MainActivity.java";
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

    MediaItem item = MediaItem.fromUri(Uri.parse(url));
    Log.i(TAG,"START : Create a new MediaSource using RtspMediaSource.Factory()");
    MediaSource mediaSource = new RtspMediaSource.Factory().createMediaSource(item); // UDPdatsource Directory
    Log.i(TAG,"Finished creation of mediaSource");

    player.setMediaSource(mediaSource);
    Log.i(TAG,"Set Media Source DONE");
    //playerView.setUseController(true);
    Log.i(TAG,"setUseController() DONE");
    playerView.setPlayer(player);
    Log.i(TAG,"setPlayer() DONE");
    player.prepare();
    Log.i(TAG,"Player Prepared DONE");
    player.setPlayWhenReady(true);
    Log.i(TAG,"Set to play when the player is ready");
  }
}