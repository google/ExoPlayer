package com.sample.myapplication;

import android.os.Bundle;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.rtsp.MediaDescription;
import com.google.android.exoplayer2.source.rtsp.MediaDescription.*;
import com.google.android.exoplayer2.source.rtsp.MediaDescription.RtpMapAttribute;
import static com.google.android.exoplayer2.source.rtsp.MediaDescription.MEDIA_TYPE_VIDEO;
import static com.google.android.exoplayer2.source.rtsp.MediaDescription.RTP_AVP_PROFILE;
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_CONTROL;
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_FMTP;
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_RTPMAP;

import com.google.android.exoplayer2.source.rtsp.RtspMediaSource;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource.Factory;
import com.google.android.exoplayer2.source.rtsp.RtspMediaTrack;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.MimeTypes;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    //make sure you are using even number port for RTP comm.
    Uri url = Uri.parse("rtp://10.2.0.19:50008");
    public static String TAG = "ExoSample";
    MediaItem item = null;
    RtspMediaSource mediaSource = null;
    ExoPlayer player = null;
    Factory mFactory = null;
    private boolean isRtpOnly = true;
    RtspMediaTrack rtspMediaTrack = null;
    MediaDescription mediadescription = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PlayerView playerView = findViewById(R.id.player_view);
        Log.i(TAG,"Created PlayerView");
        playerView.requestFocus();
        player = new ExoPlayer.Builder(this).build();
        //this confirms that we can use url to get distinct IP address & port number
        Log.d(TAG,"Uri details "+url.getHost()+" "+url.getPort()); //prints 10.2.0.19 & 50008
        Log.i(TAG,"Creating MediaItem from url : " + url);
        item = new MediaItem.Builder().setUri(url).build();
        //Below line not working as it is setting MediaItem#LocalConfiguration to null & RtspMediaSource is yelling
        //item = new MediaItem.Builder().setMimeType(MimeTypes.BASE_TYPE_VIDEO).build();

        //creating mediasource
        mFactory = new RtspMediaSource.Factory(isRtpOnly);
        mFactory.setTimeoutMs(10000); //10sec timeout
        mFactory.setForceUseRtpTcp(false); //advising SDK to only use UDP
        mediaSource = mFactory.createMediaSource(item);


            //Emulating parsing of SDP file info of SA
            mediadescription = new MediaDescription.Builder(MEDIA_TYPE_VIDEO, 50008, RTP_AVP_PROFILE, 96) //m=video 50008 RTP/AVP 96
                    .setConnection("IN IP4 10.2.0.19") /* c=IN IP4 10.2.0.19*/
                    .setBitrate(589000) //bitrate for various Argo collected videos vary from 589kbps to 1649 kbps. Revisit this later
                    .addAttribute(ATTR_RTPMAP, "96 H264/90000") //a=rtpmap:96 H264/90000
                    .addAttribute(ATTR_FMTP, "96 media=video; clock-rate=90000; encoding-name=H264; sprop-parameter-sets=Z2QAKKy0A8ARPy4C1AQEBQAAAwABAAADADCPGDKg,aO88sA==")
                    .build(); //you omitted control attribute specifyng track ID, revisit that later. Above fmtp string is copied from A9 mainline SA.


        //There is no session involved with plain RTP connection. Setting SessionUrl to null
        rtspMediaTrack = new RtspMediaTrack(mediadescription,null);

        mediaSource.setRtspMediaTrack(rtspMediaTrack);
        player.setMediaSource(mediaSource);
        //playerView.setUseController(true);

        playerView.setPlayer(player);
        player.prepare();
        player.setPlayWhenReady(true);

    }

    @Override
    protected void onDestroy() {
        player.stop();
        player.release();
        super.onDestroy();
    }
}