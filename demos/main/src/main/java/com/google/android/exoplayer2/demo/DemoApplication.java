/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.demo;

import android.app.Application;
import android.util.Log;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.RtpDataSource;
import com.google.android.exoplayer2.upstream.RtpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.util.rtp.AluRtpDistributionFeedback;
import com.google.android.exoplayer2.util.rtp.RtpDistributionFeedback;

/**
 * Placeholder application to facilitate overriding Application methods for debugging and testing.
 */
public class DemoApplication extends Application {

  protected String userAgent;

  protected RtpDistributionFeedback.RtpFeedbackEventListener eventListener =
          new RtpDistributionFeedback.RtpFeedbackEventListener () {

            @Override
            public void onRtpFeedbackEvent(
                    RtpDistributionFeedback.RtpFeedbackEvent event) {

              if (event instanceof AluRtpDistributionFeedback.AluRtpFeedbackConfigDiscoveryStarted) {

                Log.v("AluRtpFeedback", "ALU RTP Feedback Configuration Discovery Started");
              }
              else if (event instanceof AluRtpDistributionFeedback.AluRtpFeedbackConfigDiscoveryEnded) {

                Log.v("AluRtpFeedback", "ALU RTP Feedback Configuration Discovery Ended");
              }
              else if (event instanceof AluRtpDistributionFeedback.AluDefaultRtpBurstServerEvent) {

                AluRtpDistributionFeedback.AluDefaultRtpBurstServerEvent serverEvent =
                        (AluRtpDistributionFeedback.AluDefaultRtpBurstServerEvent) event;

                Log.v("AluRtpFeedback", "default burst=[" + serverEvent.getBurstServer() + "]");
              }
              else if (event instanceof AluRtpDistributionFeedback.AluDefaultRtpRetransmissionServerEvent) {

                AluRtpDistributionFeedback.AluDefaultRtpRetransmissionServerEvent serverEvent =
                        (AluRtpDistributionFeedback.AluDefaultRtpRetransmissionServerEvent) event;

                Log.v("AluRtpFeedback", "default retransmission=[" + serverEvent.getRetransmissionServer() + "]");
              }
              else if (event instanceof AluRtpDistributionFeedback.AluRtpMulticastGroupInfoEvent) {

                AluRtpDistributionFeedback.AluRtpMulticastGroupInfoEvent groupInfo =
                        (AluRtpDistributionFeedback.AluRtpMulticastGroupInfoEvent) event;

                Log.v("AluRtpFeedback", "mcast=[" + groupInfo.getMulticastGroup() + "], " +
                        "first burst=[" + groupInfo.getFirstBurstServer() + "], " +
                        "second burst=[" + groupInfo.getSecondBurstServer() + "], " +
                        "first retrans=[" + groupInfo.getFirstRetransmissionServer() + "], " +
                        "second retrans=[" + groupInfo.getSecondRetransmissionServer() + "]");
              }
            }
          };


  @Override
  public void onCreate() {
    super.onCreate();
    userAgent = Util.getUserAgent(this, "ExoPlayerDemo");
  }

  public DataSource.Factory buildDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
    return new DefaultDataSourceFactory(this, bandwidthMeter,
        buildHttpDataSourceFactory(bandwidthMeter));
  }

  public HttpDataSource.Factory buildHttpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
    return new DefaultHttpDataSourceFactory(userAgent, bandwidthMeter);
  }


  public RtpDataSource.Factory buildRtpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter,
                                                         String vendor, boolean feedback_events,
                                                         String burst_uri,
                                                         String retransmission_uri) {

    RtpDataSourceFactory dataSourceFactory = new RtpDataSourceFactory(bandwidthMeter);

    if ((vendor != null) && ("alu".equalsIgnoreCase(vendor))) {

      dataSourceFactory.setFeedbackProperty(RtpDistributionFeedback.Properties.FB_VENDOR,
              RtpDistributionFeedback.Providers.ALU);

      if (feedback_events) {
        dataSourceFactory.setFeedbackProperty(RtpDistributionFeedback.Properties.FB_EVENTS_CALLBACK,
                eventListener);
      }

      int flagsScheme = 0;

      if (burst_uri != null) {
        dataSourceFactory.setFeedbackProperty(RtpDistributionFeedback.Properties.FB_RAMS_URI,
                burst_uri);

        flagsScheme |= RtpDistributionFeedback.Schemes.FB_RAMS;
      }

      if (retransmission_uri != null) {
        dataSourceFactory.setFeedbackProperty(
                RtpDistributionFeedback.Properties.FB_CONGESTION_CONTROL_URI,
                retransmission_uri);

        flagsScheme |= RtpDistributionFeedback.Schemes.FB_CONGESTION_CONTROL;
      }

      dataSourceFactory.setFeedbackProperty(RtpDistributionFeedback.Properties.FB_SCHEME,
              flagsScheme);

    }

    return dataSourceFactory;
  }

  public boolean useExtensionRenderers() {
    return BuildConfig.FLAVOR.equals("withExtensions");
  }

}
