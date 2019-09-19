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

import static com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.AD_PROGRESS;
import static com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.LOADED;
import static com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.STARTED;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdError;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Main Activity for the IMA plugin demo. {@link ExoPlayer} objects are created by
 * {@link PlayerManager}, which this class instantiates.
 */
public final class MainActivity extends Activity {

  private PlayerView playerView;

  private EditText contentPositionText;
  private EditText continueWatchingPositionText;

  private Spinner adTagSpinner;
  private String[] adTagNames;
  private String[] adTagUrls;

  private PlayerManager player;
  private Button startButton;
  private Button pauseButton;

  private SimpleDateFormat tsFormat = new SimpleDateFormat("HH:MM:ss,SSS", Locale.US);
  private TextView logView;
  private AdEvent.AdEventType lastAdEventType;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main_activity);
    playerView = findViewById(R.id.player_view);
    playerView.setControllerAutoShow(true);
    playerView.setUseController(true);

    logView = findViewById(R.id.log_view);

    contentPositionText = findViewById(R.id.content_position);
    continueWatchingPositionText= findViewById(R.id.continue_watching_position);

    adTagSpinner = findViewById(R.id.ad_tag_spinner);
    String[] adTagItems = getResources().getStringArray(R.array.ad_tag_items);
    adTagNames = new String[adTagItems.length];
    adTagUrls = new String[adTagItems.length];
    for (int i = 0; i < adTagItems.length; i++) {
      String[] values = adTagItems[i].split("#", 2);
      adTagNames[i] = values[0];
      adTagUrls[i] = values[1];
    }
    ArrayAdapter<String> adTagAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, adTagNames);
    adTagSpinner.setAdapter(adTagAdapter);
    adTagSpinner.setSelection(0);

    startButton = findViewById(R.id.start_btn);
    startButton.setOnClickListener(view -> play());

    pauseButton = findViewById(R.id.pause_btn);
    pauseButton.setOnClickListener(view -> pause());
  }

  private void pause() {
    if (player == null) {
      return;
    }
    if (pauseButton.getText().toString().equals("Pause")) {
      player.pause();
      pauseButton.setText("Resume");
    } else {
      player.resume();
      pauseButton.setText("Pause");
    }
  }

  private void play() {
    if (player != null) {
      player.release();
    }

    logView.setText("");
    lastAdEventType = null;

    String adTagUrl = adTagUrls[adTagSpinner.getSelectedItemPosition()];
    long contentPosition = parsePosition(contentPositionText.getText().toString());
    long continueWatchingPosition = parsePosition(continueWatchingPositionText.getText().toString());
    player = new PlayerManager(MainActivity.this, adTagUrl, contentPosition,
        continueWatchingPosition, this::onAdEvent);
    AdsLoader adsLoader = player.getAdsLoader().getAdsLoader();
    adsLoader.addAdErrorListener(this::onAdError);
    adsLoader.addAdsLoadedListener(this::onAdsManagerLoaded);
    player.init(MainActivity.this, playerView);

    pauseButton.setText("Pause");
    pauseButton.setEnabled(true);
  }

  private long parsePosition(String value) {
    if (value == null || value.isEmpty()) {
      return 0;
    }
    return (long) (Double.parseDouble(value) * 1000);
  }

  private void onAdsManagerLoaded(AdsManagerLoadedEvent event) {
    AdsManager adsManager = event.getAdsManager();
    List<Float> cuePoints = adsManager.getAdCuePoints();
    String timestamp = getTimestamp();
    String msg = "[" + timestamp + "] AdsManagerLoaded, cuePoints=" + cuePoints;
    logView.setText(msg + "\n" + logView.getText());
  }

  private void onAdError(AdErrorEvent adErrorEvent) {
    AdError error = adErrorEvent.getError();
    String timestamp = getTimestamp();
    String msg = "[" + timestamp + "] Error, code=" + error.getErrorCodeNumber() + ", message=" + error.getMessage();
    logView.setText(msg + "\n" + logView.getText());
  }

  private void onAdEvent(AdEvent adEvent) {
    String timestamp = getTimestamp();
    AdEvent.AdEventType adEventType = adEvent.getType();
    String msg = "[" + timestamp + "] AdEvent: " + adEventType;
    if (adEvent.getAd() != null) {
      msg += ", adId=" + adEvent.getAd().getAdId();
    }
    if (adEventType == LOADED) {
      Ad ad = adEvent.getAd();
      AdPodInfo adPodInfo = ad.getAdPodInfo();
      int adPosition = adPodInfo.getAdPosition();
      int adCount = adPodInfo.getTotalAds();
      int podIndex = adPodInfo.getPodIndex();
      String creativeId = ad.getCreativeId();
      double timeOffset = adPodInfo.getTimeOffset();
      msg += ", timeOffset=" + timeOffset + ", podIndex=" + podIndex + ", adPosition=" + adPosition +
          ", adCount=" + adCount + ", creativeId=" + creativeId;
    }
    if (adEventType == STARTED) {
      Ad ad = adEvent.getAd();
      String creativeId = ad.getCreativeId();
      String title = ad.getTitle();
      msg += ", creativeId=" + creativeId + ", title=" + title;
    }
    if (adEvent.getAdData() != null && !adEvent.getAdData().isEmpty()) {
      msg += ", data=" + adEvent.getAdData();
    }

    String history = logView.getText().toString();
    if (adEventType == AD_PROGRESS && lastAdEventType == AD_PROGRESS) {
      int idx = history.indexOf("\n");
      if (idx != -1) {
        history = history.substring(idx + 1);
      }
    }

    logView.setText(msg + "\n" + history);
    lastAdEventType = adEvent.getType();
  }

  private String getTimestamp() {
    Date now = new Date();
    return tsFormat.format(now);
  }

  @Override
  public void onResume() {
    super.onResume();

  }

  @Override
  public void onPause() {
    super.onPause();
    if (player != null) {
      player.reset();
    }
  }

  @Override
  public void onDestroy() {
    if (player != null) {
      player.release();
    }
    super.onDestroy();
  }

}
